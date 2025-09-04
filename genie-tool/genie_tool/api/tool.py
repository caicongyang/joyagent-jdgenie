"""
工具与服务的 API 聚合路由

暴露与工具相关的 HTTP 接口，例如：代码解释器、报告生成、深度搜索等。
"""
# 导入标准库：json 用于序列化 SSE 数据负载
import json
# 导入标准库：os 用于读取环境变量（如文件服务地址）
import os
# 导入标准库：time 用于流式模式中的时间窗口控制
import time

# 导入 FastAPI 路由与 SSE 支持
from fastapi import APIRouter
from sse_starlette import ServerSentEvent, EventSourceResponse

# 导入模型：行动/代码输出（用于区分不同流式事件）
from genie_tool.model.code import ActionOutput, CodeOuput
# 导入协议模型：请求体定义
from genie_tool.model.protocal import CIRequest, ReportRequest, DeepSearchRequest
# 导入工具：文件上传（将文本/报告保存到文件服务）
from genie_tool.util.file_util import upload_file
# 导入业务能力：报告生成（markdown/html/ppt）
from genie_tool.tool.report import report
# 导入业务能力：代码解释器代理（流式执行与产出）
from genie_tool.tool.code_interpreter import code_interpreter_agent
# 导入中间件路由：统一日志与异常处理
from genie_tool.util.middleware_util import RequestHandlerRoute
# 导入业务能力：深度搜索工具
from genie_tool.tool.deepsearch import DeepSearch

# 创建路由对象，指定自定义路由类以接入日志打印与异常兜底
router = APIRouter(route_class=RequestHandlerRoute)


# 定义代码解释器接口：POST /code_interpreter
@router.post("/code_interpreter")
async def post_code_interpreter(
    body: CIRequest,
):
    """代码解释器：支持 SSE 流式返回或一次性返回"""
     # 处理文件路径：将非绝对/非 http 链接的文件名补齐为文件服务可预览 URL
    if body.file_names:
        for idx, f_name in enumerate(body.file_names):
            if not f_name.startswith("/") and not f_name.startswith("http"):
                body.file_names[idx] = f"{os.getenv('FILE_SERVER_URL')}/preview/{body.request_id}/{f_name}"

    # 定义内部异步生成器：按 SSE 事件流式推送执行过程
    async def _stream():
        """SSE 流式推送执行过程与最终结果"""
        # 累积内容缓冲区（用于 token/time 两种节流模式）
        acc_content = ""
        # token 计数器（按 N 个 token 输出一次）
        acc_token = 0
        # 最近一次输出的时间（按时间间隔输出）
        acc_time = time.time()
        # 调用代码解释器代理，开启流式执行，逐步产出步骤对象或文本增量
        async for chunk in code_interpreter_agent(
            task=body.task,
            file_names=body.file_names,
            request_id=body.request_id,
            stream=True,
        ):


            # 若产出为代码对象（例如解析出的可执行 Python 代码）
            if isinstance(chunk, CodeOuput):
                # 推送代码块事件（携带可能的文件信息）
                yield ServerSentEvent(
                    data=json.dumps(
                        {
                            "requestId": body.request_id,
                            "code": chunk.code,
                            "fileInfo": chunk.file_list,
                            "isFinal": False,
                        },
                        ensure_ascii=False,
                    )
                )
            # 若产出为最终答案（ActionOutput），通常携带文件列表（报告/结果）
            elif isinstance(chunk, ActionOutput):
                # 推送最终答案事件
                yield ServerSentEvent(
                    data=json.dumps(
                        {
                            "requestId": body.request_id,
                            "codeOutput": chunk.content,
                            "fileInfo": chunk.file_list,
                            "isFinal": True,
                        },
                        ensure_ascii=False,
                    )
                )
                # 发送流结束标记，便于前端关闭事件源
                yield ServerSentEvent(data="[DONE]")
            else:
                # 文本增量：累积到缓冲区，供不同流式模式统一控制
                acc_content += chunk
                acc_token += 1
                # 通用模式：每个增量都直接下发
                if body.stream_mode.mode == "general":
                    yield ServerSentEvent(
                        data=json.dumps(
                            {"requestId": body.request_id, "data": chunk, "isFinal": False},
                            ensure_ascii=False,
                        )
                    )
                # Token 模式：累计到 N 个 token 后再输出
                elif body.stream_mode.mode == "token":
                    if acc_token >= body.stream_mode.token:
                        yield ServerSentEvent(
                            data=json.dumps(
                                {
                                    "requestId": body.request_id,
                                    "data": acc_content,
                                    "isFinal": False,
                                },
                                ensure_ascii=False,
                            )
                        )
                        acc_token = 0
                        acc_content = ""
                # 时间模式：超过指定时间间隔后输出一次
                elif body.stream_mode.mode == "time":
                    if time.time() - acc_time > body.stream_mode.time:
                        yield ServerSentEvent(
                            data=json.dumps(
                                {
                                    "requestId": body.request_id,
                                    "data": acc_content,
                                    "isFinal": False,
                                },
                                ensure_ascii=False,
                            )
                        )
                        acc_time = time.time()
                        acc_content = ""
                # 若为 token/time 模式，循环末尾兜底下发剩余内容（避免遗漏）
                if body.stream_mode.mode in ["time", "token"] and acc_content:
                    yield ServerSentEvent(
                        data=json.dumps(
                            {
                                "requestId": body.request_id,
                                "data": acc_content,
                                "isFinal": False,
                            },
                            ensure_ascii=False,
                        )
                    )
            

    # 若客户端请求流式
    if body.stream:
        # 返回 SSE 响应，附带心跳机制维持连接
        return EventSourceResponse(
            _stream(),
            ping_message_factory=lambda: ServerSentEvent(data="heartbeat"),
            ping=15,
        )
    else:
        # 非流式：直接聚合完整结果后返回
        content = ""
        async for chunk in code_interpreter_agent(
            task=body.task,
            file_names=body.file_names,
            stream=body.stream,
        ):
            content += chunk
        # 完整结果上传为文件，得到可下载/预览链接
        file_info = [
            await upload_file(
                content=content,
                file_name=body.file_name,
                request_id=body.request_id,
                file_type="html" if body.file_type == "ppt" else body.file_type,
            )
        ]
        # 返回结构化响应
        return {
            "code": 200,
            "data": content,
            "fileInfo": file_info,
            "requestId": body.request_id,
        }


# 定义报告生成接口：POST /report
@router.post("/report")
async def post_report(
    body: ReportRequest,
):
    """报告生成：按类型生成对应格式内容，支持 SSE 流式"""
    # 处理文件路径：与代码解释器一致，补齐文件服务可预览 URL
    if body.file_names:
        for idx, f_name in enumerate(body.file_names):
            if not f_name.startswith("/") and not f_name.startswith("http"):
                body.file_names[idx] = f"{os.getenv('FILE_SERVER_URL')}/preview/{body.request_id}/{f_name}"
    
    # 内部函数：去除围栏代码块包装（```html 等），保留纯 HTML 内容
    def _parser_html_content(content: str):
        if content.startswith("```\nhtml"):
            content = content[len("```\nhtml"): ]
        if content.startswith("```html"):
            content = content[len("```html"): ]
        if content.endswith("```"):
            content = content[: -3]
        return content

    # 定义内部异步生成器：按 SSE 推送报告内容
    async def _stream():
        """SSE 流式推送报告内容，结束后保存文件并返回链接"""
        # 总内容（用于最终存储），与分片缓冲（用于节流推送）
        content = ""
        acc_content = ""
        acc_token = 0
        acc_time = time.time()
        # 调用报告生成器，按所选类型（markdown/html/ppt）流式生成
        async for chunk in report(
            task=body.task,
            file_names=body.file_names,
            file_type=body.file_type,
        ):
            content += chunk
            acc_content += chunk
            acc_token += 1
            # 通用模式：直接透传分片
            if body.stream_mode.mode == "general":
                yield ServerSentEvent(
                    data=json.dumps(
                        {"requestId": body.request_id, "data": chunk, "isFinal": False},
                        ensure_ascii=False,
                    )
                )
            # Token 模式：累计达到阈值时输出
            elif body.stream_mode.mode == "token":
                if acc_token >= body.stream_mode.token:
                    yield ServerSentEvent(
                        data=json.dumps(
                            {
                                "requestId": body.request_id,
                                "data": acc_content,
                                "isFinal": False,
                            },
                            ensure_ascii=False,
                        )
                    )
                    acc_token = 0
                    acc_content = ""
            # 时间模式：达到时间间隔时输出
            elif body.stream_mode.mode == "time":
                if time.time() - acc_time > body.stream_mode.time:
                    yield ServerSentEvent(
                        data=json.dumps(
                            {
                                "requestId": body.request_id,
                                "data": acc_content,
                                "isFinal": False,
                            },
                            ensure_ascii=False,
                        )
                    )
                    acc_time = time.time()
                    acc_content = ""
        # 兜底：若还有残留分片，在 token/time 模式下补发
        if body.stream_mode.mode in ["time", "token"] and acc_content:
            yield ServerSentEvent(
                data=json.dumps({"requestId": body.request_id, "data": acc_content, "isFinal": False},
                                ensure_ascii=False))
        # 若为 HTML/PPT，需要剥离围栏，得到纯 HTML
        if body.file_type in ["ppt", "html"]:
            content = _parser_html_content(content)
        # 将完整报告上传为文件，得到链接
        file_info = [await upload_file(content=content, file_name=body.file_name, request_id=body.request_id,
                                 file_type="html" if body.file_type == "ppt" else body.file_type)]
        # 推送最终事件（包含文件信息）并结束
        yield ServerSentEvent(data=json.dumps(
            {"requestId": body.request_id, "data": content, "fileInfo": file_info,
             "isFinal": True}, ensure_ascii=False))
        yield ServerSentEvent(data="[DONE]")

    # 若客户端请求流式
    if body.stream:
        # 返回 SSE 响应（附心跳）
        return EventSourceResponse(
            _stream(),
            ping_message_factory=lambda: ServerSentEvent(data="heartbeat"),
            ping=15,
        )
    else:
        # 非流式：聚合完整报告并返回
        content = ""
        async for chunk in report(
            task=body.task,
            file_names=body.file_names,
            file_type=body.file_type,
        ):
            content += chunk
        # HTML/PPT 类型需剥离围栏代码块
        if body.file_type in ["ppt", "html"]:
            content = _parser_html_content(content)
        # 上传并返回链接
        file_info = [await upload_file(content=content, file_name=body.file_name, request_id=body.request_id,
                                 file_type="html" if body.file_type == "ppt" else body.file_type)]
        return {"code": 200, "data": content, "fileInfo": file_info, "requestId": body.request_id}


# 定义深度搜索接口：POST /deepsearch
@router.post("/deepsearch")
async def post_deepsearch(
    body: DeepSearchRequest,
):
    """深度搜索端点"""
    # 根据请求参数初始化搜索工具（可指定使用的引擎集合）
    deepsearch = DeepSearch(engines=body.search_engines)
    # 定义内部流式生成器：将深搜过程的阶段性结果逐步推送给客户端
    async def _stream():
        # 运行深度搜索，内部包含查询分解、并行检索、推理判断与报告生成
        async for chunk in deepsearch.run(
                query=body.query,
                request_id=body.request_id,
                max_loop=body.max_loop,
                stream=True,
                stream_mode=body.stream_mode,
        ):
            # 搜索模块已经产出 JSON 字符串，直接透传
            yield ServerSentEvent(data=chunk)
        # 搜索完成，发送结束标记
        yield ServerSentEvent(data="[DONE]")

    # 返回 SSE 响应（附心跳），由前端消费分阶段 JSON
    return EventSourceResponse(_stream(), ping_message_factory=lambda: ServerSentEvent(data="heartbeat"), ping=15)


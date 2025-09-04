"""
深度搜索（DeepSearch）工具

能力：
- 多引擎并行检索（bing/jina/sogou/serp），去重合并
- 循环推理：按轮次进行"分解查询 -> 检索 -> 评估是否继续"，直到达到上限或模型判定可以作答
- 生成最终报告：支持流式返回 answer 片段
"""
# 异步编程支持，用于并行执行多个搜索请求
import asyncio
# JSON序列化/反序列化，用于构造API响应
import json
# 操作系统接口，用于获取环境变量配置
import os
# 并发执行工具，用于在线程池中执行异步任务
from concurrent.futures import ThreadPoolExecutor, as_completed
# 函数工具，用于创建带有预设参数的函数
from functools import partial
# 类型注解，增强代码可读性和IDE支持
from typing import List, AsyncGenerator, Tuple

# 日志工具，记录搜索过程中的关键信息
from genie_tool.util.log_util import logger
# LLM调用工具，用于查询分解、推理判断和答案生成
from genie_tool.util.llm_util import ask_llm
# 文档模型，表示搜索结果的标准格式
from genie_tool.model.document import Doc
# 计时装饰器，用于监控函数执行耗时
from genie_tool.util.log_util import timer
# 查询分解组件，将复杂查询拆分为多个子查询
from genie_tool.tool.search_component.query_process import query_decompose
# 答案生成组件，基于检索结果生成最终回答
from genie_tool.tool.search_component.answer import answer_question
# 推理组件，判断当前检索结果是否足够回答问题
from genie_tool.tool.search_component.reasoning import search_reasoning
# 搜索引擎组件，封装多个搜索引擎的调用
from genie_tool.tool.search_component.search_engine import MixSearch
# 流式输出模式定义，控制分片返回的策略
from genie_tool.model.protocal import StreamMode
# 文件工具，用于裁剪过长的文档内容
from genie_tool.util.file_util import truncate_files
# 模型上下文工厂，获取不同模型的上下文长度限制
from genie_tool.model.context import LLMModelInfoFactory


class DeepSearch:
    """深度搜索工具：封装检索、推理与报告生成全流程"""

    def __init__(self, engines: List[str] = []):
        """
        初始化深度搜索工具
        
        参数:
        - engines: 要使用的搜索引擎列表，如 ["bing", "jina", "sogou", "serp"]
                  若为空，则从环境变量 USE_SEARCH_ENGINE 获取（默认为 "bing"）
        """
        # 如果没有指定搜索引擎，则从环境变量获取（默认使用bing）
        if not engines:
            engines = os.getenv("USE_SEARCH_ENGINE", "bing").split(",")
        
        # 根据传入的引擎列表，确定启用哪些搜索引擎
        use_bing = "bing" in engines  # 是否使用必应搜索
        use_jina = "jina" in engines  # 是否使用Jina搜索
        use_sogou = "sogou" in engines  # 是否使用搜狗搜索
        use_serp = "serp" in engines  # 是否使用Serper搜索API
        
        # 创建一个预设参数的搜索函数，便于后续调用
        self._search_single_query = partial(
            MixSearch().search_and_dedup, use_bing=use_bing, use_jina=use_jina, use_sogou=use_sogou, use_serp=use_serp)
        
        # 初始化已搜索查询列表，用于避免重复搜索
        self.searched_queries = []
        # 初始化当前文档集合，存储所有搜索结果
        self.current_docs = []

    def search_docs_str(self, model: str = None) -> str:
        """
        将收集的文档转换为格式化的字符串，用于传递给LLM
        
        参数:
        - model: 模型名称，用于获取上下文长度限制并裁剪文档
        
        返回:
        - 格式化后的文档字符串，每个文档带有编号和HTML格式
        """
        # 初始化空字符串，用于累积文档内容
        current_docs_str = ""
        
        # 获取指定模型的上下文长度限制
        max_tokens = LLMModelInfoFactory.get_context_length(model)
        
        # 如果指定了模型，则按模型上下文长度的80%裁剪文档，否则使用全部文档
        truncate_docs = truncate_files(self.current_docs, max_tokens=int(max_tokens * 0.8)) if model else self.current_docs
        
        # 遍历裁剪后的文档，为每个文档添加编号和格式化输出
        for i, doc in enumerate(truncate_docs, start=1):
            current_docs_str += f"文档编号〔{i}〕. \n{doc.to_html()}\n"
            
        return current_docs_str

    @timer()  # 添加计时装饰器，用于监控函数执行时间
    async def run(
            self,
            query: str,  # 用户原始查询
            request_id: str = None,  # 请求ID，用于日志追踪和关联
            max_loop: int = 1,  # 最大搜索循环次数，默认只执行一轮
            stream: bool = False,  # 是否启用流式输出
            stream_mode: StreamMode = StreamMode(),  # 流式输出模式配置
            *args,  # 额外位置参数
            **kwargs  # 额外关键字参数
    ) -> AsyncGenerator[str, None]:
        """
        深度搜索主流程（支持流式输出）
        
        执行流程：
        1. 查询分解：将用户查询拆分为多个子查询
        2. 并行搜索：对每个子查询执行搜索并去重
        3. 推理判断：评估当前结果是否足够回答问题
        4. 循环迭代：根据推理结果决定是否继续搜索
        5. 生成答案：基于所有搜索结果生成最终回答

        产出消息类型：
        - extend: 查询分解阶段，返回分解查询占位结果
        - search: 检索阶段，返回分解查询对应的搜索结果列表
        - report: 生成报告阶段，分片或最终完整答案
        
        返回:
        - 异步生成器，产出JSON字符串，包含各阶段结果
        """

        # 初始化循环计数器
        current_loop = 1
        
        # 执行深度搜索循环，最多执行max_loop轮
        while current_loop <= max_loop:
            # 记录当前轮次信息
            logger.info(f"{request_id} 第 {current_loop} 轮深度搜索...")
            
            # 第一步：查询分解 - 使用LLM将复杂查询拆分为多个子查询
            sub_queries = await query_decompose(query=query)  # 分解主查询为多个子查询

            # 向客户端发送查询分解结果（占位，此时还没有搜索结果）
            yield json.dumps({
                "requestId": request_id,  # 请求ID
                "query": query,  # 原始查询
                "searchResult": {"query": sub_queries, "docs": [[]] * len(sub_queries)},  # 子查询列表和空的文档结果占位
                "isFinal": False,  # 非最终结果
                "messageType": "extend"  # 消息类型：查询扩展
            }, ensure_ascii=False)  # 确保中文正确编码

            # 短暂等待，避免前端渲染压力
            await asyncio.sleep(0.1)

            # 过滤掉已经搜索过的查询，避免重复请求
            sub_queries = [sub_query for sub_query in sub_queries
                           if sub_query not in self.searched_queries]
                           
            # 第二步：并行搜索 - 对每个子查询执行搜索并去重
            searched_docs, docs_list = await self._search_queries_and_dedup(
                queries=sub_queries,  # 过滤后的子查询列表
                request_id=request_id,  # 请求ID，用于日志跟踪
            )

            # 获取单页最大显示长度（用于前端展示时裁剪文档内容）
            truncate_len = int(os.getenv("SINGLE_PAGE_MAX_SIZE", 200))
            
            # 向客户端发送搜索结果
            yield json.dumps(
                {
                    "requestId": request_id,  # 请求ID
                    "query": query,  # 原始查询
                    "searchResult": {
                        "query": sub_queries,  # 子查询列表
                        # 二维数组：每个子查询对应一组文档，每个文档转为字典并裁剪内容
                        "docs": [[d.to_dict(truncate_len=truncate_len) for d in docs_l] for docs_l in docs_list]
                    },
                    "isFinal": False,  # 非最终结果
                    "messageType": "search"  # 消息类型：搜索结果
                }, ensure_ascii=False)  # 确保中文正确编码

            # 更新内部状态：累积已检索文档与查询，用于后续去重和答案生成
            self.current_docs.extend(searched_docs)  # 添加本轮搜索到的文档
            self.searched_queries.extend(sub_queries)  # 添加本轮的子查询

            # 如果已经达到最大循环次数，直接结束循环
            if current_loop == max_loop:
                break

            # 第三步：推理验证 - 判断当前结果是否足够回答问题
            reasoning_result = search_reasoning(
                request_id=request_id,  # 请求ID
                query=query,  # 原始查询
                # 将当前所有文档转为字符串，传递给推理模型
                content=self.search_docs_str(os.getenv("SEARCH_REASONING_MODEL")),
            )

            # 如果推理判断已经可以回答问题，则提前结束循环
            if reasoning_result.get("is_verify", "1") in ["1", 1]:
                logger.info(f"{request_id} reasoning 判断没有得到新的查询，流程结束")
                break

            # 增加循环计数，准备下一轮搜索
            current_loop += 1

        # 第四步：生成最终答案（报告）- 支持流式分片返回
        answer = ""  # 完整答案，用于非流式模式
        acc_content = ""  # 累积内容，用于流式分片
        acc_token = 0  # 累积token计数，用于控制分片频率
        
        # 调用答案生成组件，基于所有搜索结果生成最终回答
        async for chunk in answer_question(
                query=query,  # 原始查询
                # 将所有文档转为字符串，传递给答案生成模型
                search_content=self.search_docs_str(os.getenv("SEARCH_ANSWER_MODEL"))
        ):
            # 流式模式：按token数分片返回
            if stream:
                # 当累积token达到阈值时，发送一个分片
                if acc_token >= stream_mode.token:
                    yield json.dumps({
                        "requestId": request_id,  # 请求ID
                        "query": query,  # 原始查询
                        "searchResult": {  # 空的搜索结果（报告阶段不需要）
                            "query": [],
                            "docs": [],
                        },
                        "answer": acc_content,  # 当前累积的答案片段
                        "isFinal": False,  # 非最终结果
                        "messageType": "report"  # 消息类型：报告
                    }, ensure_ascii=False)
                    # 重置累积变量
                    acc_content = ""
                    acc_token = 0
                # 累积当前片段
                acc_content += chunk
                acc_token += 1
            # 无论流式与否，都累积完整答案
            answer += chunk
            
        # 流式模式：发送最后一个未满阈值的分片（如果有）
        if stream and acc_content:
            yield json.dumps({
                "requestId": request_id,
                "query": query,
                "searchResult": {
                    "query": [],
                    "docs": [],
                },
                "answer": acc_content,  # 剩余的答案片段
                "isFinal": False,
                "messageType": "report"
            }, ensure_ascii=False)
            
        # 发送最终标记，表示搜索和答案生成完成
        yield json.dumps({
                "requestId": request_id,
                "query": query,
                "searchResult": {
                    "query": [],
                    "docs": [],
                },
                # 流式模式下已经分片发送过答案，这里留空；非流式模式下发送完整答案
                "answer": "" if stream else answer,
                "isFinal": True,  # 标记为最终结果
                "messageType": "report"  # 消息类型：报告
            }, ensure_ascii=False)

    async def _search_queries_and_dedup(
            self,
            queries: List[str],  # 要搜索的查询列表
            request_id: str,  # 请求ID，用于日志跟踪
    ) -> Tuple[List[Doc], List[List[Doc]]]:
        """
        异步并行搜索多个查询并去重
        
        实现方式：
        1. 使用线程池并行执行异步搜索
        2. 收集所有搜索结果
        3. 合并并去除重复文档
        
        返回：
        - 去重后的文档列表
        - 原始搜索结果（二维数组，每个查询对应一组文档）
        """
        # 内部函数：在新的事件循环中运行异步搜索
        def _run_async(*args, **kwargs):
            # 创建新的事件循环，避免与主事件循环冲突
            loop = asyncio.new_event_loop()
            # 设置当前线程的事件循环
            asyncio.set_event_loop(loop)
            # 在新循环中执行搜索并等待结果
            s_result = loop.run_until_complete(self._search_single_query(*args, **kwargs))
            # 关闭事件循环
            loop.close()
            # 返回搜索结果
            return s_result

        # 创建任务列表，准备并行执行
        process_list = []
        # 使用线程池并行执行搜索任务，线程数从环境变量获取（默认5）
        with ThreadPoolExecutor(max_workers=int(os.getenv("SEARCH_THREAD_NUM", 5))) as executor:
            # 为每个查询创建一个搜索任务
            for query in queries:
                # 提交任务到线程池
                process = executor.submit(_run_async, query, request_id)
                # 将任务添加到列表中
                process_list.append(process)
                
        # 收集所有已完成任务的结果，按完成顺序（不保证与提交顺序相同）
        results = [process.result() for process in as_completed(process_list)]
        
        # 将所有搜索结果扁平化为单一列表
        all_docs = [doc for docs in results for doc in docs]
        
        # 去重：使用集合记录已见过的文档内容
        seen_content = set()
        deduped_docs = []
        
        # 遍历所有文档，只保留内容不重复的文档
        for doc in all_docs:
            # 只有非空文档且内容未见过的才保留
            if doc.content and doc.content not in seen_content:
                deduped_docs.append(doc)
                seen_content.add(doc.content)
                
        # 返回去重后的文档列表和原始搜索结果
        return deduped_docs, results

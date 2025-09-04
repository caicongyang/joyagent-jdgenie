# -*- coding: utf-8 -*-  # 文件编码声明
# =====================  # 分隔线（信息块）
# 
# 
# Author: liumin.423     # 作者信息
# Date:   2025/7/7       # 创建日期
# =====================  # 分隔线（信息块）
import os  # 操作系统接口，用于读取环境变量
from datetime import datetime  # 日期时间，用于Prompt渲染当前时间/日期
from typing import Optional, List, Literal, AsyncGenerator  # 类型注解，提升可读性

from dotenv import load_dotenv  # 加载 .env 环境变量
from jinja2 import Template  # 模板引擎，用于渲染Prompt
from loguru import logger  # 日志库，用于警告/错误输出

from genie_tool.util.file_util import download_all_files, truncate_files, flatten_search_file  # 文件工具：下载/裁剪/拍平
from genie_tool.util.prompt_util import get_prompt  # 读取提示模板
from genie_tool.util.llm_util import ask_llm  # 调用LLM（流式/非流式）
from genie_tool.util.log_util import timer  # 计时装饰器
from genie_tool.model.context import LLMModelInfoFactory  # 模型上下文长度与最大输出配置

load_dotenv()  # 初始化加载环境变量（如模型名等）


@timer(key="enter")  # 记录进入与耗时
async def report(
        task: str,
        file_names: Optional[List[str]] = tuple(),
        model: str = "gpt-4.1",
        file_type: Literal["markdown", "html", "ppt"] = "markdown",
) -> AsyncGenerator:
    # 报告类型到具体处理函数的映射
    report_factory = {
        "ppt": ppt_report,
        "markdown": markdown_report,
        "html": html_report,
    }
    model = os.getenv("REPORT_MODEL", "gpt-4.1")  # 从环境变量覆盖模型名（若存在）
    # 将生成内容以流式片段形式 yield 出去
    async for chunk in report_factory[file_type](task, file_names, model):
        yield chunk


@timer(key="enter")  # 记录进入与耗时
async def ppt_report(
        task: str,
        file_names: Optional[List[str]] = tuple(),
        model: str = "gpt-4.1",
        temperature: float = None,
        top_p: float = 0.6,
) -> AsyncGenerator:
    files = await download_all_files(file_names)  # 下载输入文件内容
    flat_files = []  # 汇总用于渲染的文件列表

    # 1. 首先解析 md html 文件，没有这部分文件则使用全部
    filtered_files = [f for f in files if f["file_name"].split(".")[-1] in ["md", "html"]
                      and not f["file_name"].endswith("_搜索结果.md")] or files  # 优先使用md/html
    for f in filtered_files:
        # 对于搜索文件有结构，需要重新解析
        if f["file_name"].endswith("_search_result.txt"):
            flat_files.extend(flatten_search_file(f))  # 搜索结果拍平
        else:
            flat_files.append(f)  # 直接加入

    # 按模型上下文长度的80%进行裁剪
    truncate_flat_files = truncate_files(flat_files, max_tokens=int(LLMModelInfoFactory.get_context_length(model) * 0.8))
    prompt = Template(get_prompt("report")["ppt_prompt"]) \
        .render(task=task, files=truncate_flat_files, date=datetime.now().strftime("%Y-%m-%d"))  # 渲染PPT任务提示

    async for chunk in ask_llm(messages=prompt, model=model, stream=True,
                                temperature=temperature, top_p=top_p, only_content=True):  # 流式请求LLM
        yield chunk  # 逐片段返回


@timer(key="enter")  # 记录进入与耗时
async def markdown_report(
        task,
        file_names: Optional[List[str]] = tuple(),
        model: str = "gpt-4.1",
        temperature: float = 0,
        top_p: float = 0.9,
) -> AsyncGenerator:
    files = await download_all_files(file_names)  # 下载输入文件内容
    flat_files = []  # 格式化后的文件列表
    for f in files:
        # 对于搜索文件有结构，需要重新解析
        if f["file_name"].endswith("_search_result.txt"):
            flat_files.extend(flatten_search_file(f))  # 拍平
        else:
            flat_files.append(f)  # 直接加入

    # 裁剪上下文
    truncate_flat_files = truncate_files(flat_files, max_tokens=int(LLMModelInfoFactory.get_context_length(model) * 0.8))
    prompt = Template(get_prompt("report")["markdown_prompt"]) \
        .render(task=task, files=truncate_flat_files, current_time=datetime.now().strftime("%Y-%m-%d %H:%M:%S"))  # 渲染Markdown任务

    async for chunk in ask_llm(messages=prompt, model=model, stream=True,
                                temperature=temperature, top_p=top_p, only_content=True):  # 流式请求LLM
        yield chunk  # 逐片段返回


@timer(key="enter")  # 记录进入与耗时
async def html_report(
        task,
        file_names: Optional[List[str]] = tuple(),
        model: str = "gpt-4.1",
        temperature: float = 0,
        top_p: float = 0.9,
) -> AsyncGenerator:
    files = await download_all_files(file_names)  # 下载输入文件内容
    key_files = []  # 关键文件（优先保留）
    flat_files = []  # 普通文件
    # 对于搜索文件有结构，需要重新解析
    for f in files:
        fpath = f["file_name"]
        fname = os.path.basename(fpath)
        if fname.split(".")[-1] in ["md", "txt", "csv"]:
            # CI 输出结果
            if "代码输出" in fname:
                key_files.append({"content": f["content"], "description": fname, "type": "txt", "link": fpath})  # 加入关键文件
            # 搜索文件
            elif fname.endswith("_search_result.txt"):
                try:
                    flat_files.extend([{
                            "content": tf["content"],
                            "description": tf.get("title") or tf["content"][:20],
                            "type": "txt",
                            "link": tf.get("link"),
                        } for tf in flatten_search_file(f)
                    ])
                except Exception as e:
                    logger.warning(f"html_report parser file [{fpath}] error: {e}")  # 解析失败仅告警
            # 其他文件
            else:
                flat_files.append({
                    "content": f["content"],
                    "description": fname,
                    "type": "txt",
                    "link": fpath
                })
    # 控制上下文预算：关键文件优先占位，其余文件在剩余预算内裁剪
    discount = int(LLMModelInfoFactory.get_context_length(model) * 0.8)  # 可用预算
    key_files = truncate_files(key_files, max_tokens=discount)  # 先裁剪关键文件
    flat_files = truncate_files(flat_files, max_tokens=discount - sum([len(f["content"]) for f in key_files]))  # 再裁剪普通文件

    report_prompts = get_prompt("report")  # 读取报告模板
    prompt = Template(report_prompts["html_task"]) \
        .render(task=task, key_files=key_files, files=flat_files, date=datetime.now().strftime('%Y年%m月%d日'))  # 渲染HTML任务

    async for chunk in ask_llm(
            messages=[{"role": "system", "content": report_prompts["html_prompt"]},
                      {"role": "user", "content": prompt}],
            model=model, stream=True, temperature=temperature, top_p=top_p, only_content=True):  # 流式请求LLM
        yield chunk  # 逐片段返回


if __name__ == "__main__":
    pass

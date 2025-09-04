"""
查询分解模块

将复杂查询拆分为多个子查询，便于后续多引擎检索与聚合。
流程：先思考（think），再根据思考结果进行正式的 queries 生成。
"""
import os
import re
import time

from loguru import logger

from genie_tool.util.llm_util import ask_llm
from genie_tool.util.prompt_util import get_prompt
from genie_tool.model.context import RequestIdCtx
from genie_tool.util.log_util import timer


@timer()
async def query_decompose(
        query: str,
        **kwargs
):
    """
    将单个复杂问题拆分为子查询列表

    步骤：
    1) 使用 think_model 先行“思考”，得到隐式分析文本
    2) 将思考结果作为输入，调用 model 生成子查询（Markdown 列表格式）
    3) 解析列表项（- item）为纯文本数组
    """
    model = os.getenv("QUERY_DECOMPOSE_MODEL", "gpt-4.1")
    think_model = os.getenv("QUERY_DECOMPOSE_THINK_MODEL", "gpt-4.1")
    current_date = time.strftime("%Y-%m-%d", time.localtime())
    decompose_prompt = get_prompt("deepsearch")
    # think 阶段：生成隐式思考文本（流式累加）
    think_content = ""
    async for chunk in ask_llm(
            messages=decompose_prompt["query_decompose_think_prompt"].format(task=query, retrieval_str=""),
            model=think_model,
            stream=True,
            only_content=True,  # 只返回内容
    ):
        if chunk:
            think_content += chunk

    logger.info(f"{RequestIdCtx.request_id} query_decompose think: {think_content}")

    # decompose 阶段：根据思考结果正式生成子查询
    messages = [
        {
            "role": "system",
            "content": decompose_prompt["query_decompose_prompt"].format(
                current_date=current_date, max_queries=os.getenv("QUERY_DECOMPOSE_MAX_SIZE", 5))},
        {"role": "user", "content": f"思考结果：{think_content}"},
    ]
    extend_queries = ""
    async for chunk in ask_llm(
            messages=messages,
            model=model,
            stream=True,
            only_content=True,  # 只返回内容
    ):
        if chunk:
            extend_queries += chunk

    logger.info(f"{RequestIdCtx.request_id} query_decompose queries: {extend_queries}")

    # 解析 Markdown 列表为纯文本数组
    queries = re.findall(r"^- (.+)$", extend_queries, re.MULTILINE)
    return [match.strip() for match in queries]


if __name__ == "__main__":
    pass

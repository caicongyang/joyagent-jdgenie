"""
搜索问答生成模块

根据已检索与整理的内容，调用 LLM 生成最终答案（流式）。
"""
import os
import time

from genie_tool.util.llm_util import ask_llm
from genie_tool.util.log_util import timer
from genie_tool.util.prompt_util import get_prompt


@timer()
async def answer_question(query: str, search_content: str):
    """
    基于检索内容生成答案（支持流式返回）

    参数：
    - query: 用户原始问题
    - search_content: 已结构化/拼接的检索内容

    行为：
    - 从 prompt 配置读取模板，注入 query、检索内容、当前时间、目标长度
    - 调用 LLM（流式），逐片段 yield 内容
    """
    prompt_template = get_prompt("deepsearch")["answer_prompt"]

    model = os.getenv("SEARCH_ANSWER_MODEL", "gpt-4.1")
    answer_length = os.getenv("SEARCH_ANSWER_LENGTH", "10000")

    prompt = prompt_template.format(
        query=query,
        sub_qa=search_content,
        current_time=time.strftime("%Y-%m-%d %H:%M:%S", time.localtime()),
        response_length=answer_length
    )
    async for chunk in ask_llm(
            messages=prompt,
            model=model,
            stream=True,
            only_content=True,  # 只返回内容
    ):
        if chunk:
            yield chunk


if __name__ == "__main__":
    pass

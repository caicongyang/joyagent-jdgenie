"""
搜索循环推理模块

用于判断是否需要继续扩展搜索：
- 输入：当前 query、已收集内容、历史查询列表
- 输出：是否可直接回答（is_verify）、若需要可给出重写后的 query
"""
import json
import os
import time
from json_repair import repair_json

from genie_tool.util.llm_util import ask_llm
from genie_tool.util.prompt_util import get_prompt
from genie_tool.util.log_util import timer


@timer()
async def search_reasoning(
        request_id: str, query: str, content: str, history_query_list: list = [],
):
    """
    基于当前检索内容进行推理，判断是否继续下一轮搜索

    返回：形如 {is_verify: "1"/"0", rewrite_query: str, reason: str}
    """
    if not request_id or not query or not content:
        return {}

    model = os.getenv("SEARCH_REASONING_MODEL", "gpt-4.1")
    prompt = get_prompt("deepsearch")["reasoning_prompt"]
    prompt_content = prompt.format(
        query=query,
        sub_queries=history_query_list,
        content=content,
        date=time.strftime("%Y年%m月%d日 %H时%M分%S秒", time.localtime()),
    )
    content = ""
    async for chunk in ask_llm(
            messages=prompt_content,
            model=model,
            stream=True,
            only_content=True,  # 只返回内容
    ):
        if chunk:
            content += chunk
    content_clean = json.loads(repair_json(content, ensure_ascii=False))
    return _parser(request_id, content_clean)


def _parser(request_id, reasoning: dict) -> dict:
    """规范化模型的推理输出，填充缺省字段"""
    reasoning_dict = {
        "request_id": request_id,
        "rewrite_query": reasoning.get("rewrite_query", ""),
        "reason": reasoning.get("reason", ""),
    }
    if reasoning.get("is_answer", "") in [1, "1"]:
        reasoning_dict["is_verify"] = "1"
    else:
        reasoning_dict["is_verify"] = "0"
    return reasoning_dict


if __name__ == "__main__":
    pass

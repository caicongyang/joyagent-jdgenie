"""
LLM 调用工具

封装对 litellm 的异步调用，提供：
- 统一的消息格式适配（字符串或 OpenAI 样式的 messages 列表）
- 可选的敏感词替换开关（通过环境变量 SENSITIVE_WORD_REPLACE）
- 流式/非流式两种返回方式，并支持仅返回内容（only_content）
"""
import json
import os
from typing import List, Any, Optional

from litellm import acompletion

from genie_tool.util.log_util import timer, AsyncTimer
from genie_tool.util.sensitive_detection import SensitiveWordsReplace


@timer(key="enter")
async def ask_llm(
        messages: str | List[Any],
        model: str,
        temperature: float = None,
        top_p: float = None,
        stream: bool = False,

        # 自定义字段
        only_content: bool = False,     # 只返回内容

        extra_headers: Optional[dict] = None,
        **kwargs,
):
    """
    调用底层 LLM 接口（litellm）

    参数：
    - messages: 可以是字符串（会自动包装为单轮 user）或 OpenAI 风格的 messages 列表
    - model: 模型标识
    - temperature/top_p: 采样控制
    - stream: 是否以流式返回
    - only_content: 当为 True 时，返回值仅包含文本内容
    - extra_headers/kwargs: 透传给底层的额外参数

    返回：
    - 异步生成器：根据 stream 决定产出的对象（增量 chunk 或完整响应/文本）
    """
    # 统一 messages 格式：字符串 -> 单条 user 消息
    if isinstance(messages, str):
        messages = [{"role": "user", "content": messages}]

    # 可选的敏感词替换（开关：SENSITIVE_WORD_REPLACE）
    if os.getenv("SENSITIVE_WORD_REPLACE", "false") == "true":
        for message in messages:
            if isinstance(message.get("content"), str):
                message["content"] = SensitiveWordsReplace.replace(message["content"])
            else:
                message["content"] = json.loads(
                    SensitiveWordsReplace.replace(json.dumps(message["content"], ensure_ascii=False)))

    # 发起异步补全请求
    response = await acompletion(
        messages=messages,
        model=model,
        temperature=temperature,
        top_p=top_p,
        stream=stream,
        extra_headers=extra_headers,
        **kwargs
    )

    # 统一计时：不同返回模式下都统计耗时
    async with AsyncTimer(key=f"exec ask_llm"):
        if stream:
            # 流式：逐增量返回
            async for chunk in response:
                if only_content:
                    if chunk.choices and chunk.choices[0] and chunk.choices[0].delta and chunk.choices[0].delta.content:
                        yield chunk.choices[0].delta.content
                else:
                    yield chunk
        else:
            # 非流式：返回完整对象或仅内容
            yield response.choices[0].message.content if only_content else response


if __name__ == "__main__":
    pass

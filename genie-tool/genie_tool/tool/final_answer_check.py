"""
最终答案检查器（FinalAnswerCheck）

用途：
- 在智能体执行一步代码后，基于当前对话记忆与执行日志，询问模型判断是否已经得到可以输出的最终答案
- 通过 prompt 模板拼接上下文，调用模型生成 JSON 判定结果（容错解析）
"""

from openai import AsyncOpenAI

from smolagents import ChatMessage
from smolagents import ActionStep
from typing import Callable, List, Dict, Optional
from smolagents import MessageRole
import json_repair
from loguru import logger as lg


class FinalAnswerCheck(object):
    """
    最终答案检查器

    通过预设的提示模板 `final_answer` 构造 system/user 指令，并将已有记忆拼接后询问模型：
    - 若回复 JSON 对象/数组中含有 `is_final` 为 True，则认为当前可返回最终答案
    - 否则继续后续步骤
    """
    def __init__(
        self,
        input_messages,
        execution_logs,
        model: Callable[[List[Dict[str, str]]], ChatMessage],
        task: str,
        request_id,
        prompt_temps,
        memory_step: ActionStep,
        grammar: Optional[Dict[str, str]] = None,
    ):
        self.model = model
        additional_args = {"grammar": grammar} if grammar is not None else {}
        self.additional_args = additional_args
        self.memory_step = memory_step
        # copy
        self.input_messages = input_messages
        self.prompt_templates = prompt_temps
        self.task = task
        self.execution_logs = execution_logs
        self.request_id = request_id

    def check_is_final_answer(self):
        """
        判断当前轮是否可输出最终答案

        步骤：
        1) 组装上下文消息：过滤掉前两条（通常是系统预设），合并当前步骤消息
        2) 根据模板构造新的 system 与 user 指令
        3) 询问模型并解析 JSON（容错）
        4) 判断 is_final 字段
        """
        # 去掉最前面的系统消息，仅保留有效上下文
        memory_lines = self.input_messages[2:]
        memory_lines.extend(self.memory_step.to_messages())
        final_answer = self.prompt_templates["final_answer"]

        # 组装 system 与 user 行
        system_line = ChatMessage.from_dict({
            "role": MessageRole.SYSTEM,
            "content": final_answer["pre_messages"].replace("{{task}}", self.task),
        })
        user_line = ChatMessage.from_dict({
            "role": MessageRole.USER,
            "content": [
                {
                    "type": "text",
                    "text": final_answer["post_messages"].replace(
                        "{{task}}", self.task
                    ),
                }
            ],
        })

        # 汇总输入：system + 历史 + 当前步 + user
        inputs = [system_line]
        for input_ in memory_lines:
            inputs.append(input_)
        inputs.append(user_line)

        # 调用模型进行最终判断
        chat_message: ChatMessage = self.model.generate(
            inputs, extra_headers={"x-ms-client-request-id": self.request_id},
        )
        obj = json_repair.loads(chat_message.content)

        # 容错：空对象视为未完成
        if obj == None or obj == "":
            return False, None

        # 支持两种形态：单对象或数组
        if isinstance(obj, dict) and obj.get("is_final") is True:
            return True, self.execution_logs
        if isinstance(obj, list):
            for o in obj:
                if hasattr(o, "is_final") and o["is_final"] is True:
                    return True, self.execution_logs
        return False, None

    def __name__(self):
        return "FinalAnswerCheck"

"""
代码解释器智能体（CIAgent）模块

本模块基于 smolagents 的 CodeAgent 扩展，实现了带有流式输出能力的 ReAct 风格智能体：
- 支持将大模型的输出按增量流式返回（便于前端实时渲染）
- 解析大模型输出中的代码片段，并交由本地 Python 执行器运行
- 汇总执行日志，并结合自定义的最终答案检查器（FinalAnswerCheck）判断是否产出最终答案
- 当检测到代码片段时，会将其作为一个独立的步骤产出，便于外部进行文件保存或展示

关于 smolagents：
- smolagents 是一个轻量级智能体框架，专注于构建基于大语言模型的自主代理
- 其 CodeAgent 类提供了代码生成、解析和执行的核心能力，遵循 ReAct（思考-行动-观察）范式
- 主要组件：
  * Model：抽象大语言模型接口，支持不同提供商（如 LiteLLMModel 适配器）
  * Tool：工具抽象，如 PythonInterpreterTool 提供安全的代码执行环境
  * ActionStep：记忆步骤，存储每轮交互的输入、输出、工具调用和观察结果
  * ChatMessageStreamDelta：流式输出的增量单元，支持实时渲染

主要入口：
- 类 CIAgent：继承自 CodeAgent，扩展了 _step_stream 以支持流式推理与产出

注意：
- 本文件仅包含智能体行为逻辑，不直接处理网络/存储等副作用；文件保存、上传等由调用方完成
"""

import json  # 处理 JSON 序列化/反序列化
import os
import re
import time
from collections.abc import Callable, Generator
from typing import Any, Optional
import uuid
from smolagents import (
    CodeAgent,
    ChatMessage,
    MessageRole,
    AgentGenerationError,
    BASE_BUILTIN_MODULES,
    LogLevel,
    AgentParsingError,
    fix_final_answer_code,
    parse_code_blobs,
    AgentExecutionError,
    ToolCall,
    truncate_content,
    YELLOW_HEX,
    ActionOutput,
    Model,
    Tool,
    PromptTemplates,
    ActionStep,
    ChatMessageStreamDelta,
    agglomerate_stream_deltas,
    ToolOutput,
)
from loguru import logger as lg  # 日志库，用于结构化/彩色日志输出
from rich.text import Text  # rich 文本对象，用于美化输出
from rich.console import Group  # rich 组合组件
from rich.live import Live  # rich 实时刷新组件
from rich.markdown import Markdown  # rich Markdown 渲染
import json_repair  # 容错 JSON 解析（可修复部分不规范 JSON）

from genie_tool.model.code import CodeOuput  # 代码产出数据结构（用于向上游回传新代码文件）
from genie_tool.tool.final_answer_check import FinalAnswerCheck  # 最终答案判断器
from genie_tool.util.file_util import generate_data_id  # 生成唯一文件名/ID
from genie_tool.util.log_util import timer  # 计时装饰器，记录函数耗时


class CIAgent(CodeAgent):
    """
    代码解释器智能体（Code Interpreter Agent）

    继承自 smolagents.CodeAgent，专注于代码生成与执行的智能体基类。
    
    作用：
    - 基于 ReAct 思想（思考-行动-观察），在每个步骤中驱动大模型产出代码或答案
    - 将大模型的中间输出以流式增量（ChatMessageStreamDelta）方式抛出，便于前端实时渲染
    - 当解析到可执行的 Python 代码块时，调用本地执行器运行，并把执行日志反馈给模型
    - 集成最终答案校验逻辑，在合适时机返回最终答案（ActionOutput，携带 is_final_answer）

    重要属性：
    - output_dir: 可选的输出目录，供上层在需要时将执行产物落盘
    
    smolagents 提供的核心能力：
    - 安全的代码执行环境：限制导入模块、文件系统访问和网络请求等
    - 记忆管理：跟踪对话历史和执行结果
    - 代码解析：从自由文本中提取代码块
    - 流式输出：支持增量生成和实时反馈
    """
    def __init__(
        self,
        tools: list[Tool],
        model: Model,
        prompt_templates: PromptTemplates | None = None,
        additional_authorized_imports: list[str] | None = None,
        planning_interval: int | None = None,
        executor_type: str | None = "local",
        executor_kwargs: dict[str, Any] | None = None,
        grammar: dict[str, str] | None = None,
        output_dir: Optional[str] = None,
        *args,
        **kwargs,
    ):
        # 输出目录（可选）：用于保存执行产生的产物（例如图表、数据文件）
        self.output_dir = output_dir
        super().__init__(
            tools=tools,
            model=model,
            prompt_templates=prompt_templates,
            grammar=grammar,
            planning_interval=planning_interval,
            additional_authorized_imports=additional_authorized_imports,
            executor_type=executor_type,
            executor_kwargs=executor_kwargs,
            **kwargs,
        )

    @timer()
    def _step_stream(
        self, memory_step: ActionStep
    ) -> Generator[
        ChatMessageStreamDelta | ToolCall | ToolOutput | ActionOutput | CodeOuput
    ]:
        """
        执行 ReAct 框架中的单个步骤（支持流式输出）

        流程概述：
        1) 读取记忆，构造模型输入消息
        2) 调用模型的流式生成接口，边生成边产出 ChatMessageStreamDelta 事件
        3) 聚合增量，得到完整的模型输出文本 output_text
        4) 解析模型输出中的代码块，形成 ToolCall 调用（python_interpreter）
        5) 使用本地 Python 执行器执行代码，收集执行日志（observation）
        6) 通过 FinalAnswerCheck 判断是否可以作为最终答案
        7) 产出：
           - ChatMessageStreamDelta（若干个）：模型的流式增量
           - CodeOuput（0或1个）：解析出的代码片段，供上游保存为文件
           - ActionOutput（1个）：本步动作的结果；若 is_final_answer=True，代表已得到最终答案

        参数：
        - memory_step: 当前记忆步，用于记录输入/输出/观察等中间信息

        生成器返回：
        - ChatMessageStreamDelta | ToolCall | ToolOutput | ActionOutput | CodeOuput
        """
        # 1) 将当前记忆写回消息列表，作为模型的输入
        memory_messages = self.write_memory_to_messages()

        # 保存输入消息，供最终答案检查等逻辑使用
        self.input_messages = memory_messages.copy()

        # Add new step in logs
        memory_step.model_input_messages = memory_messages.copy()
        try:
            input_messages = memory_messages.copy()  # 模型输入（深拷贝避免后续副作用）

            # 为本次模型调用生成唯一请求ID，便于链路追踪
            model_request_id = str(uuid.uuid4())

            output_stream = self.model.generate_stream(
                    input_messages,
                    extra_headers={"x-ms-client-request-id": model_request_id},
                )  # 调用大模型的流式接口
            chat_message_stream_deltas: list[ChatMessageStreamDelta] = []
            # 使用 rich.Live 实时在终端刷新渲染模型输出
            with Live("", console=self.logger.console, vertical_overflow="visible") as live:
                for event in output_stream:
                    # 收集增量事件，便于后续聚合为完整消息
                    chat_message_stream_deltas.append(event)
                    live.update(
                        Markdown(agglomerate_stream_deltas(chat_message_stream_deltas).render_as_markdown())
                    )
                    # 向外部流式产出增量事件，便于前端即时渲染
                    yield event
            # 将所有增量聚合为最终一条 ChatMessage
            chat_message = agglomerate_stream_deltas(chat_message_stream_deltas)
            memory_step.model_output_message = chat_message  # 记录到记忆步
            output_text = chat_message.content  # 完整文本
            self.logger.log_markdown(
                content=output_text,
                title="Output message of the LLM:",
                level=LogLevel.DEBUG,
            )
            memory_step.model_output_message = chat_message
            output_text = chat_message.content

            # This adds <end_code> sequence to the history.
            # This will nudge ulterior LLM calls to finish with <end_code>, thus efficiently stopping generation.
            if output_text and output_text.strip().endswith("```"):
                output_text += "<end_code>"
                memory_step.model_output_message.content = output_text

            # 记录模型输出文本，供后续解析、执行
            memory_step.model_output = output_text
            # 注：历史注释提示 put 缺少 await，这里无异步调用，不需要 await

        except Exception as e:
            raise AgentGenerationError(
                f"Error in generating model output:\n{e}", self.logger
            ) from e

        self.logger.log_markdown(
            content=output_text,
            title="Output message of the LLM:",
            level=LogLevel.DEBUG,
        )

        # 2) 解析模型输出中的代码片段（```python ... ```），并修复可能的尾部语法
        try:
            # smolagents 提供的代码解析工具，识别 markdown 代码块并提取 Python 代码
            code_action = fix_final_answer_code(parse_code_blobs(output_text))
        except Exception as e:
            error_msg = (
                f"Error in code parsing:\n{e}\nMake sure to provide correct code blobs."
            )
            raise AgentParsingError(error_msg, self.logger)

        # 在记忆中记录一次将要执行的工具调用（python_interpreter）
        memory_step.tool_calls = [
            ToolCall(
                name="python_interpreter",
                arguments=code_action,
                id=f"call_{len(self.memory.steps)}",
            )
        ]

        # 3) 执行阶段：将解析到的代码交给 Python 执行器运行
        self.logger.log_code(
            title="Executing parsed code:", content=code_action, level=LogLevel.INFO
        )

        try:
            # 执行器返回（stdout/stderr 等日志由执行器内部聚合），此处我们只使用日志文本
            # smolagents 的 python_executor 提供安全的沙箱执行环境
            _, execution_logs, _ = self.python_executor(code_action)

            execution_outputs_console = []
            if len(execution_logs) > 0:
                execution_outputs_console += [
                    Text("Execution logs:", style="bold"),
                    Text(execution_logs),
                ]

            # 观察值（observation）反馈给模型：这里主要是执行日志
            observation = "Execution logs:\n" + execution_logs
            # 尝试从输出文本中提取任务标题作为代码文件名
            if matcher := re.search(r"Task:\s?(.*)", output_text):
                file_name = f"{matcher.group(1).replace(' ', '')}.py"
            else:
                file_name = f'{generate_data_id("index")}.py'
            # 产出代码对象，便于上层将其保存到文件系统或对象存储
            yield CodeOuput(code=code_action, file_name=file_name)
        except Exception as e:
            if (
                hasattr(self.python_executor, "state")
                and "_print_outputs" in self.python_executor.state
            ):
                execution_logs = str(self.python_executor.state["_print_outputs"])
                if len(execution_logs) > 0:
                    execution_outputs_console = [
                        Text("Execution logs:", style="bold"),
                        Text(execution_logs),
                    ]
                    memory_step.observations = "Execution logs:\n" + execution_logs
                    self.logger.log(
                        Group(*execution_outputs_console), level=LogLevel.INFO
                    )
            error_msg = str(e)

            if "Import of " in error_msg and " is not allowed" in error_msg:
                self.logger.log(
                    "[bold red]Warning to user: Code execution failed due to an unauthorized import - Consider passing said import under `additional_authorized_imports` when initializing your CodeAgent.",
                    level=LogLevel.INFO,
                )
            raise AgentExecutionError(error_msg, self.logger)

        # 将执行日志作为观察值写入记忆
        memory_step.observations = observation

        # 4) 使用最终答案检查器，判断是否满足返回最终答案的条件
        finalObj = FinalAnswerCheck(
            input_messages=self.input_messages,
            execution_logs=execution_logs,
            model=self.model,
            task=self.task,
            prompt_temps=self.prompt_templates,
            memory_step=memory_step,
            grammar=self.grammar,
            request_id=f"{model_request_id}-final",
        )
        finalFlag, exeLog = finalObj.check_is_final_answer()  # 返回：是否为最终答案、以及可展示的执行日志
        self.logger.log(Group(*execution_outputs_console), level=LogLevel.INFO)
        # 记录本步的动作输出（供记忆与后续步骤引用）
        memory_step.action_output = exeLog

        # 产出动作结果。若 is_final_answer=True，表示上游可以结束本轮对话/流程
        yield ActionOutput(output=exeLog, is_final_answer=finalFlag)


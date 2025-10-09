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

import json  # 处理 JSON 序列化/反序列化，用于数据交换和格式化
import os    # 操作系统相关功能，主要用于文件路径和环境变量处理
import re    # 正则表达式模块，用于解析模型输出中的代码块和任务标题
import time  # 时间相关功能，主要用于性能监控和计时
from collections.abc import Callable, Generator  # 抽象基类：可调用对象和生成器类型提示
from typing import Any, Optional                 # 类型注解：任意类型和可选类型
import uuid  # 生成唯一标识符，用于模型请求 ID 和链路追踪
from smolagents import (
    CodeAgent,                 # 代码智能体基类：提供基础的代码生成、解析和执行框架
    ChatMessage,               # 聊天消息对象：封装对话中的单条消息内容和角色信息
    MessageRole,               # 消息角色枚举：SYSTEM(系统)、USER(用户)、ASSISTANT(助手)等
    AgentGenerationError,      # 智能体生成错误：模型输出生成阶段的异常类
    BASE_BUILTIN_MODULES,      # 基础内置模块列表：Python 执行环境中允许导入的安全模块
    LogLevel,                  # 日志级别枚举：DEBUG、INFO、WARNING、ERROR 等
    AgentParsingError,         # 智能体解析错误：代码块解析阶段的异常类
    fix_final_answer_code,     # 代码修复函数：修复可能存在语法问题的代码片段
    parse_code_blobs,          # 代码解析函数：从文本中提取 ```python 代码块
    AgentExecutionError,       # 智能体执行错误：代码执行阶段的异常类
    ToolCall,                  # 工具调用对象：封装工具名称、参数和调用 ID
    truncate_content,          # 内容截断函数：限制长文本的显示长度
    YELLOW_HEX,                # 黄色十六进制码：用于终端彩色输出的颜色常量
    ActionOutput,              # 动作输出对象：封装单步操作的结果和是否为最终答案的标志
    Model,                     # 模型抽象接口：定义大语言模型的统一调用规范
    Tool,                      # 工具抽象接口：定义智能体可使用的工具规范
    PromptTemplates,           # 提示模板对象：存储各种场景下的 prompt 模板
    ActionStep,                # 动作步骤对象：记录智能体单步执行的完整信息
    ChatMessageStreamDelta,    # 流式消息增量：流式输出中的单个增量片段
    agglomerate_stream_deltas, # 增量聚合函数：将多个流式增量合并为完整消息
    ToolOutput,                # 工具输出对象：封装工具执行的返回结果
)
from loguru import logger as lg              # Loguru 日志库：提供结构化、彩色和高性能的日志输出
from rich.text import Text                   # Rich 文本对象：用于创建带样式的控制台文本显示
from rich.console import Group               # Rich 组合组件：将多个显示元素组合为一个显示单元
from rich.live import Live                   # Rich 实时刷新组件：支持动态更新控制台内容的实时显示
from rich.markdown import Markdown           # Rich Markdown 渲染器：将 Markdown 文本渲染为格式化的控制台输出
import json_repair                           # JSON 修复库：提供容错的 JSON 解析，可修复格式不规范的 JSON

from genie_tool.model.code import CodeOuput                     # 代码输出数据模型：封装生成的代码内容和文件信息
from genie_tool.tool.final_answer_check import FinalAnswerCheck # 最终答案检查器：判断当前执行步骤是否已产出最终答案
from genie_tool.util.file_util import generate_data_id          # 文件工具：生成唯一的数据 ID 和文件名
from genie_tool.util.log_util import timer                      # 日志工具：提供函数执行计时装饰器


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
        tools: list[Tool],                                      # 工具列表：智能体可调用的工具集合（如搜索、文件操作等）
        model: Model,                                           # 大语言模型实例：用于生成代码和推理的核心模型
        prompt_templates: PromptTemplates | None = None,        # 提示模板：包含系统提示、用户提示等预定义模板
        additional_authorized_imports: list[str] | None = None, # 额外授权导入：Python执行环境中允许的额外模块列表
        planning_interval: int | None = None,                   # 规划间隔：多少步后重新进行任务规划（可选）
        executor_type: str | None = "local",                    # 执行器类型：代码执行环境类型（local/remote等）
        executor_kwargs: dict[str, Any] | None = None,          # 执行器参数：传递给代码执行器的额外配置参数
        grammar: dict[str, str] | None = None,                  # 语法配置：控制模型输出格式的语法约束（可选）
        output_dir: Optional[str] = None,                       # 输出目录：用于保存代码执行产生的文件和图表（可选）
        *args,                                                  # 可变位置参数：传递给父类的额外位置参数
        **kwargs,                                               # 可变关键字参数：传递给父类的额外关键字参数
    ):
        # 保存输出目录路径，用于后续保存执行产生的产物（例如图表、数据文件、分析结果等）
        self.output_dir = output_dir

        # 调用父类 CodeAgent 的构造函数，初始化基础的代码智能体功能
        # 包括：工具注册、模型配置、提示模板设置、执行环境初始化等
        super().__init__(
            tools=tools,                                    # 注册可用工具到智能体环境
            model=model,                                    # 设置核心推理模型
            prompt_templates=prompt_templates,              # 配置各场景下的提示模板
            grammar=grammar,                                # 设置输出格式约束
            planning_interval=planning_interval,            # 配置任务重新规划的间隔
            additional_authorized_imports=additional_authorized_imports,  # 配置Python执行环境的安全导入白名单
            executor_type=executor_type,                    # 设置代码执行器的类型
            executor_kwargs=executor_kwargs,                # 传递执行器的详细配置参数
            **kwargs,                                       # 传递其他配置参数到父类
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
        # ===== 第一阶段：构建模型输入并获取流式输出 =====

        # 1) 将智能体的对话记忆转换为模型可理解的消息格式
        # 包括：系统提示、历史对话、工具调用记录、执行结果等
        memory_messages = self.write_memory_to_messages()

        # 保存输入消息的副本，供后续的最终答案检查逻辑使用
        # 最终答案检查器需要分析完整的对话上下文来判断是否已达到任务目标
        self.input_messages = memory_messages.copy()

        # 将输入消息记录到当前动作步骤中，用于调试和日志追踪
        memory_step.model_input_messages = memory_messages.copy()

        try:
            # 创建模型输入的深拷贝，避免后续操作对原始消息产生副作用
            input_messages = memory_messages.copy()

            # 为本次模型调用生成唯一的请求ID，用于：
            # - 链路追踪和日志关联
            # - 区分不同的模型调用请求
            # - 便于问题定位和性能分析
            model_request_id = str(uuid.uuid4())

            # 调用大语言模型的流式生成接口
            # 流式接口的优势：可以实时获取生成内容，提升用户体验
            output_stream = self.model.generate_stream(
                input_messages,                                           # 输入消息列表
                extra_headers={"x-ms-client-request-id": model_request_id}, # 请求ID用于追踪
            )

            # 初始化流式增量收集器，用于聚合所有的增量片段
            chat_message_stream_deltas: list[ChatMessageStreamDelta] = []

            # 使用 Rich 的 Live 组件实现终端实时刷新显示
            # vertical_overflow="visible" 允许内容超出终端高度时继续显示
            with Live("", console=self.logger.console, vertical_overflow="visible") as live:
                # 遍历模型的流式输出，每个 event 都是一个增量片段
                for event in output_stream:
                    # 收集每个增量事件，用于后续聚合为完整消息
                    chat_message_stream_deltas.append(event)

                    # 实时更新终端显示：将当前收集到的所有增量聚合并渲染为 Markdown
                    live.update(
                        Markdown(agglomerate_stream_deltas(chat_message_stream_deltas).render_as_markdown())
                    )

                    # 向外部调用方流式产出增量事件
                    # 这使得前端可以实时接收并显示模型的生成过程
                    yield event

            # ===== 第二阶段：处理模型输出和格式化 =====

            # 将所有增量片段聚合为一条完整的 ChatMessage
            chat_message = agglomerate_stream_deltas(chat_message_stream_deltas)

            # 将完整的模型输出消息记录到当前动作步骤中
            memory_step.model_output_message = chat_message

            # 提取消息的文本内容，用于后续的代码解析和执行
            output_text = chat_message.content

            # 记录模型输出到日志，便于调试和问题排查
            self.logger.log_markdown(
                content=output_text,
                title="Output message of the LLM:",
                level=LogLevel.DEBUG,
            )

            # 确保记忆步骤中保存了完整的模型输出消息
            memory_step.model_output_message = chat_message
            output_text = chat_message.content

            # 代码结束标记处理：如果模型输出以代码块结尾，添加结束标记
            # 这个标记可以引导后续的模型调用更高效地结束代码生成
            if output_text and output_text.strip().endswith("```"):
                output_text += "<end_code>"  # 添加结束标记
                memory_step.model_output_message.content = output_text  # 更新记忆中的内容

            # 将最终的输出文本保存到记忆步骤中，供后续的代码解析和执行使用
            memory_step.model_output = output_text

        except Exception as e:
            # 捕获模型输出生成过程中的任何异常，包括：
            # - 网络连接错误
            # - 模型服务不可用
            # - 请求超时或参数错误
            # - 流式输出处理异常
            raise AgentGenerationError(
                f"Error in generating model output:\n{e}", self.logger
            ) from e

        # 再次记录完整的模型输出到日志（用于调试，虽然上面已记录过）
        self.logger.log_markdown(
            content=output_text,
            title="Output message of the LLM:",
            level=LogLevel.DEBUG,
        )

        # ===== 第三阶段：解析模型输出中的代码片段 =====

        try:
            # 从模型输出文本中解析代码块
            # 1. parse_code_blobs(): 识别并提取 ```python ... ``` 格式的代码块
            # 2. fix_final_answer_code(): 修复代码块中可能的语法问题，如：
            #    - 缺少的缩进
            #    - 不完整的语句
            #    - 多余的结束标记
            code_action = fix_final_answer_code(parse_code_blobs(output_text))
        except Exception as e:
            # 代码解析失败时的错误处理
            # 可能的失败原因：
            # - 模型输出格式不规范（没有正确的代码块标记）
            # - 代码语法错误无法修复
            # - 代码块嵌套或格式复杂
            error_msg = (
                f"Error in code parsing:\n{e}\nMake sure to provide correct code blobs."
            )
            raise AgentParsingError(error_msg, self.logger)

        # ===== 第四阶段：记录工具调用并准备执行 =====

        # 在当前动作步骤的记忆中记录即将执行的工具调用
        # 这个记录包含：工具名称、调用参数（代码内容）、唯一调用ID
        memory_step.tool_calls = [
            ToolCall(
                name="python_interpreter",                    # 工具名称：Python 解释器
                arguments=code_action,                        # 调用参数：解析出的 Python 代码
                id=f"call_{len(self.memory.steps)}",          # 唯一ID：基于当前记忆步数生成
            )
        ]

        # ===== 第五阶段：执行解析出的代码 =====

        # 记录即将执行的代码到日志，便于调试和审计
        self.logger.log_code(
            title="Executing parsed code:",
            content=code_action,
            level=LogLevel.INFO
        )

        try:
            # 调用 Python 执行器运行解析出的代码
            # python_executor 提供的功能：
            # - 安全沙箱环境：限制文件系统访问、网络请求等危险操作
            # - 模块导入控制：只允许预定义的安全模块
            # - 执行日志收集：捕获 stdout、stderr 和执行结果
            # 返回值：(result, execution_logs, error_info)
            _, execution_logs, _ = self.python_executor(code_action)

            # 准备控制台输出格式化组件
            execution_outputs_console = []
            if len(execution_logs) > 0:
                # 使用 Rich 组件格式化执行日志的显示
                execution_outputs_console += [
                    Text("Execution logs:", style="bold"),  # 加粗的标题
                    Text(execution_logs),                    # 执行日志内容
                ]

            # 构建观察值（observation），这将作为下一轮模型输入的一部分
            # 观察值帮助模型了解代码执行的结果，以便做出后续决策
            observation = "Execution logs:\n" + execution_logs

            # ===== 第六阶段：生成代码文件名并输出代码对象 =====

            # 尝试从模型输出中提取任务描述作为文件名
            # 正则表达式匹配 "Task: 任务描述" 格式
            if matcher := re.search(r"Task:\s?(.*)", output_text):
                # 提取任务描述并清理空格，生成 Python 文件名
                file_name = f"{matcher.group(1).replace(' ', '')}.py"
            else:
                # 如果没有找到任务描述，生成一个基于 ID 的默认文件名
                file_name = f'{generate_data_id("index")}.py'

            # 产出 CodeOutput 对象，包含：
            # - code: 执行的 Python 代码内容
            # - file_name: 建议的文件名
            # 这个对象可以被上层调用者用于：
            # - 保存代码到文件系统
            # - 上传到对象存储
            # - 展示给用户查看
            yield CodeOuput(code=code_action, file_name=file_name)
        except Exception as e:
            # ===== 代码执行异常处理 =====

            # 尝试从执行器状态中获取部分执行日志（即使执行失败）
            if (
                hasattr(self.python_executor, "state")                # 检查执行器是否有状态对象
                and "_print_outputs" in self.python_executor.state    # 检查是否有输出缓存
            ):
                # 提取已有的输出日志（执行过程中的 print 语句输出等）
                execution_logs = str(self.python_executor.state["_print_outputs"])
                if len(execution_logs) > 0:
                    # 格式化部分执行日志用于显示
                    execution_outputs_console = [
                        Text("Execution logs:", style="bold"),
                        Text(execution_logs),
                    ]
                    # 将部分日志保存到记忆步骤中
                    memory_step.observations = "Execution logs:\n" + execution_logs
                    # 显示部分执行结果（有助于调试）
                    self.logger.log(
                        Group(*execution_outputs_console), level=LogLevel.INFO
                    )

            # 获取异常信息的字符串表示
            error_msg = str(e)

            # 特殊处理：模块导入权限错误
            # 当代码尝试导入未授权的模块时，给出友好的提示信息
            if "Import of " in error_msg and " is not allowed" in error_msg:
                self.logger.log(
                    "[bold red]Warning to user: Code execution failed due to an unauthorized import - "
                    "Consider passing said import under `additional_authorized_imports` when initializing your CodeAgent.",
                    level=LogLevel.INFO,
                )

            # 抛出代码执行异常，包含详细的错误信息
            raise AgentExecutionError(error_msg, self.logger)

        # ===== 第七阶段：保存执行结果到记忆 =====

        # 将代码执行的观察结果（日志）保存到当前动作步骤的记忆中
        # 这些观察结果将在下一轮推理中作为上下文提供给模型
        memory_step.observations = observation

        # ===== 第八阶段：最终答案检查 =====

        # 创建最终答案检查器实例，用于判断当前步骤是否已经产出了满足用户需求的最终答案
        finalObj = FinalAnswerCheck(
            input_messages=self.input_messages,      # 完整的输入消息历史
            execution_logs=execution_logs,           # 当前步骤的执行日志
            model=self.model,                        # 用于判断的语言模型
            task=self.task,                          # 用户的原始任务描述
            prompt_temps=self.prompt_templates,      # 最终答案检查的提示模板
            memory_step=memory_step,                 # 当前动作步骤的完整信息
            grammar=self.grammar,                    # 输出格式约束
            request_id=f"{model_request_id}-final",  # 最终答案检查的请求ID
        )

        # 调用检查器判断是否为最终答案
        # 返回值：
        # - finalFlag: 布尔值，表示是否为最终答案
        # - exeLog: 格式化的执行日志，用于展示给用户
        finalFlag, exeLog = finalObj.check_is_final_answer()

        # 显示执行日志到控制台（用于实时反馈）
        self.logger.log(Group(*execution_outputs_console), level=LogLevel.INFO)

        # 将格式化的执行日志保存为当前步骤的动作输出
        # 这个输出将被记录到记忆中，供后续步骤参考
        memory_step.action_output = exeLog

        # ===== 第九阶段：产出最终的动作结果 =====

        # 产出 ActionOutput 对象，包含：
        # - output: 当前步骤的执行结果（格式化的日志）
        # - is_final_answer: 是否为最终答案的标志
        #
        # 如果 is_final_answer=True，表示：
        # - 任务已经完成，可以结束当前的推理循环
        # - 上层调用者应该停止继续调用 _step_stream
        # - 用户已经得到了满意的答案
        yield ActionOutput(output=exeLog, is_final_answer=finalFlag)


# -*- coding: utf-8 -*-
# =====================
# 代码解释器工具模块
# 
# 功能说明：
# 1. 提供基于AI的代码解释和执行功能
# 2. 支持多种文件格式的处理（Excel、CSV、文本文件等）
# 3. 集成Python代码执行环境
# 4. 支持流式输出和文件上传下载
#
# Author: liumin.423
# Date:   2025/7/7
# =====================

# 导入异步编程相关模块
import asyncio  # 用于异步操作支持
import importlib  # 动态导入模块（当前未使用）
import os  # 操作系统接口，用于文件路径操作
import shutil  # 高级文件操作，用于删除临时目录
import tempfile  # 创建临时文件和目录
from typing import List, Optional  # 类型提示支持

# 导入数据处理相关模块
import pandas as pd  # 数据分析库，用于处理Excel和CSV文件
import yaml  # YAML文件解析（当前未使用）
from jinja2 import Template  # 模板引擎，用于动态生成提示词

# 导入AI代理相关模块
from smolagents import LiteLLMModel, FinalAnswerStep, PythonInterpreterTool, ChatMessageStreamDelta

# 导入项目内部模块
from genie_tool.tool.ci_agent import CIAgent  # 自定义的代码解释器代理
from genie_tool.util.file_util import download_all_files_in_path, upload_file, upload_file_by_path  # 文件操作工具
from genie_tool.util.log_util import timer  # 日志和计时装饰器
from genie_tool.util.prompt_util import get_prompt  # 提示词获取工具
import requests  # HTTP请求库（当前未使用）
from genie_tool.model.code import ActionOutput, CodeOuput  # 代码输出相关数据模型

@timer()  # 添加计时装饰器，用于监控函数执行时间
async def code_interpreter_agent(
    task: str,  # 用户输入的任务描述，告诉AI需要执行什么操作
    file_names: Optional[List[str]] = None,  # 可选的文件名列表，指定需要处理的文件
    max_file_abstract_size: int = 2000,  # 文件摘要的最大字符数，用于限制文本文件的预览长度
    max_tokens: int = 32000,  # AI模型的最大token限制
    request_id: str = "",  # 请求ID，用于追踪和关联请求
    stream: bool = True,  # 是否启用流式输出，True时会实时返回执行过程
):
    """
    代码解释器主函数
    
    功能：
    1. 创建临时工作目录
    2. 下载并处理用户指定的文件
    3. 根据文件类型生成摘要信息
    4. 使用AI代理执行代码解释任务
    5. 处理输出结果并上传文件
    
    参数说明：
    - task: 用户的任务描述
    - file_names: 需要处理的文件列表
    - max_file_abstract_size: 文件摘要最大长度
    - max_tokens: AI模型token限制
    - request_id: 请求唯一标识
    - stream: 是否流式输出
    
    返回值：
    - 生成器，逐步返回执行结果
    """
    work_dir = ""  # 初始化工作目录变量
    try:
        # 创建临时工作目录，用于存放下载的文件和输出结果
        work_dir = tempfile.mkdtemp()
        
        # 在工作目录下创建output子目录，用于存放代码执行的输出文件
        output_dir = os.path.join(work_dir, "output")
        os.makedirs(output_dir, exist_ok=True)  # exist_ok=True表示目录已存在时不报错
        
        # 从指定路径下载所有相关文件到工作目录
        import_files = await download_all_files_in_path(file_names=file_names, work_dir=work_dir)

        # ========== 第一步：文件处理和摘要生成 ==========
        files = []  # 存储处理后的文件信息列表
        if import_files:
            # 遍历所有下载的文件
            for import_file in import_files:

                file_name = import_file["file_name"]  # 获取文件名
                file_path = import_file["file_path"]  # 获取文件路径
                
                # 跳过无效的文件信息
                if not file_name or not file_path:
                    continue

                # 处理表格文件（Excel和CSV）
                if file_name.split(".")[-1] in ["xlsx", "xls", "csv"]:
                    # 设置pandas显示选项，显示所有列
                    pd.set_option("display.max_columns", None)
                    
                    # 根据文件类型选择合适的读取方法
                    df = (
                        pd.read_csv(file_path)  # CSV文件使用read_csv
                        if file_name.endswith(".csv")
                        else pd.read_excel(file_path)  # Excel文件使用read_excel
                    )
                    
                    # 将文件路径和前10行数据作为摘要添加到文件列表
                    files.append({"path": file_path, "abstract": f"{df.head(10)}"})
                    
                # 处理文本文件（txt、markdown、html）
                elif file_name.split(".")[-1] in ["txt", "md", "html"]:
                    # 读取文本文件内容
                    with open(file_path, "r", encoding="utf-8") as rf:
                        files.append(
                            {
                                "path": file_path,  # 文件路径
                                "abstract": "".join(rf.readlines())[
                                    :max_file_abstract_size  # 截取指定长度的文本作为摘要
                                ],
                            }
                        )

        # ========== 第二步：构建AI提示词模板 ==========
        # 获取代码解释器的提示词模板配置
        ci_prompt_template = get_prompt("code_interpreter")

        # ========== 第三步：创建代码解释器AI代理 ==========
        # 创建具有Python执行能力的AI代理
        agent = create_ci_agent(
            prompt_templates=ci_prompt_template,  # 提示词模板
            max_tokens=max_tokens,  # token限制
            return_full_result=True,  # 返回完整结果
            output_dir=output_dir,  # 输出目录
        )

        # 使用Jinja2模板引擎渲染任务描述
        # 将文件信息、用户任务和输出目录注入到模板中
        template_task = Template(ci_prompt_template["task_template"]).render(
            files=files,  # 处理后的文件列表
            task=task,  # 用户任务描述
            output_dir=output_dir  # 输出目录路径
        )

        # ========== 第四步：执行AI代理任务并处理输出 ==========
        if stream:
            # 流式模式：逐步返回执行结果
            for step in agent.run(task=str(template_task), stream=True, max_steps=10):
                
                # 处理代码输出步骤
                if isinstance(step, CodeOuput):
                    # 将生成的代码上传为文件
                    file_info = await upload_file(
                        content=step.code,  # 代码内容
                        file_name=step.file_name,  # 文件名
                        file_type="py",  # 文件类型为Python
                        request_id=request_id,  # 请求ID
                    )
                    step.file_list = [file_info]  # 将文件信息添加到步骤中
                    yield step  # 返回代码输出步骤
                
                # 处理最终答案步骤
                elif isinstance(step, FinalAnswerStep):
                    file_list = []  # 初始化文件列表
                    
                    # 检查输出目录中是否有新生成的文件（如Excel、CSV等）
                    file_path = get_new_file_by_path(output_dir=output_dir)
                    if file_path:
                        # 上传新生成的文件
                        file_info = await upload_file_by_path(
                            file_path=file_path, request_id=request_id
                        )
                        if file_info:
                            file_list.append(file_info)
                    
                    # 创建代码输出的Markdown文件
                    code_name = f"{task[:20]}_代码输出.md"  # 使用任务前20个字符作为文件名前缀
                    file_list.append(
                        await upload_file(
                            content=step.output,  # 最终输出内容
                            file_name=code_name,  # 文件名
                            file_type="md",  # Markdown格式
                            request_id=request_id,  # 请求ID
                        )
                    )

                    # 创建动作输出对象并返回
                    output = ActionOutput(content=step.output, file_list=file_list)
                    yield output
                    
                # 处理聊天消息流增量（当前被注释掉，不处理）
                elif isinstance(step, ChatMessageStreamDelta):
                    #yield step.content  # 可以选择返回流式聊天内容
                    pass  # 暂时跳过处理
                    
                # 异步让出控制权，避免阻塞事件循环
                await asyncio.sleep(0)
                
        else:
            # 非流式模式：直接返回完整结果
            output = agent.run(task=task)
            yield output
            
    except Exception as e:
        # 异常处理：重新抛出异常，让调用方处理
        raise e

    finally:
        # 清理工作：删除临时工作目录及其所有内容
        if work_dir:
            shutil.rmtree(work_dir, ignore_errors=True)  # ignore_errors=True避免删除失败时报错


def get_new_file_by_path(output_dir):
    """
    获取输出目录中最新生成的表格文件
    
    功能：
    - 扫描输出目录中的所有文件
    - 筛选出Excel和CSV格式的文件
    - 返回最新修改的文件路径
    
    参数：
    - output_dir: 输出目录路径
    
    返回值：
    - 最新文件的完整路径，如果没有找到则返回空字符串
    """
    temp_file = ""  # 存储最新文件路径
    latest_time = 0  # 存储最新修改时间
    
    # 遍历输出目录中的所有文件
    for item in os.listdir(output_dir):
        # 只处理Excel和CSV文件
        if item.endswith(".xlsx") or item.endswith(".csv") or item.endswith(".xls"):
            item_path = os.path.join(output_dir, item)  # 构建完整文件路径
            
            # 确保是文件而不是目录
            if os.path.isfile(item_path):
                # 获取文件的最后修改时间戳
                mod_time = os.path.getmtime(item_path)
                
                # 如果当前文件比之前记录的文件更新，则更新记录
                if mod_time > latest_time:
                    latest_time = mod_time  # 更新最新时间
                    temp_file = item_path   # 更新最新文件路径
                    
    return temp_file  # 返回最新文件路径


def create_ci_agent(
    prompt_templates=None,  # 提示词模板配置
    max_tokens: int = 16000,  # AI模型的最大token限制
    return_full_result: bool = True,  # 是否返回完整执行结果
    output_dir: str = "",  # 代码执行的输出目录
) -> CIAgent:
    """
    创建代码解释器AI代理
    
    功能：
    - 初始化LLM模型
    - 配置Python解释器工具
    - 设置允许导入的Python库
    - 创建具有代码执行能力的AI代理
    
    参数：
    - prompt_templates: 提示词模板配置
    - max_tokens: 模型最大token数
    - return_full_result: 是否返回完整结果
    - output_dir: 输出目录路径
    
    返回值：
    - 配置好的CIAgent实例
    """
    # 创建LiteLLM模型实例
    model = LiteLLMModel(
        max_tokens=max_tokens,  # 设置token限制
        model_id=os.getenv("CODE_INTEPRETER_MODEL", "gpt-4.1")  # 从环境变量获取模型ID，默认为gpt-4.1
    )

    # 创建并返回代码解释器代理
    return CIAgent(
        model=model,  # 使用的AI模型
        prompt_templates=prompt_templates,  # 提示词模板
        tools=[PythonInterpreterTool()],  # 工具列表：Python解释器
        return_full_result=return_full_result,  # 是否返回完整结果
        additional_authorized_imports=[  # 额外允许导入的Python库
            "pandas",     # 数据分析库
            "openpyxl",   # Excel文件操作库
            "numpy",      # 数值计算库
            "matplotlib", # 绘图库
            "seaborn",    # 统计绘图库
        ],
        output_dir=output_dir,  # 输出目录
    )


if __name__ == "__main__":
    """
    主程序入口
    
    当前为空实现，可以在这里添加测试代码或示例用法
    """
    pass

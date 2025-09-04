"""
代码执行/动作输出数据模型

用于在智能体流程中向上游传递“新代码片段文件”与“动作结果及附件清单”。
"""

from dataclasses import dataclass
from typing import Any

@dataclass
class CodeOuput:
    """新生成的代码产出（用于文件存储/下载）"""
    code: Any
    file_name: str
    file_list: list = None

@dataclass
class ActionOutput:
    """动作（一步执行）的输出内容与附件列表"""
    content: str
    file_list: list
"""
提示模板工具

通过 importlib.resources 从包内 `genie_tool/prompt/*.yaml` 读取提示词配置
"""
import importlib

import yaml


def get_prompt(prompt_file):
    """加载指定名称的 YAML 提示模板并解析为字典"""
    return yaml.safe_load(importlib.resources.files("genie_tool.prompt").joinpath(f"{prompt_file}.yaml").read_text())


#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
简化的 SmolaAgents E2B 演示

基于官方 SmolaAgents 文档的最小化 E2B sandbox 演示。
专注于核心功能，易于理解和快速上手。

官方文档: https://github.com/huggingface/smolagents
"""

import os
from typing import Generator, Any

# 导入 SmolaAgents 核心组件
try:
    from smolagents import CodeAgent, LiteLLMModel, WebSearchTool
    print("✅ SmolaAgents 核心组件导入成功")
except ImportError as e:
    print(f"❌ SmolaAgents 导入失败: {e}")
    print("请安装: pip install smolagents")
    exit(1)

# 尝试导入 E2B 工具
try:
    from smolagents.tools import E2BPythonInterpreterTool
    E2B_AVAILABLE = True
    print("✅ E2B Python 解释器工具可用")
except ImportError:
    print("⚠️  E2B 工具未找到，使用标准 Python 解释器")
    from smolagents import PythonInterpreterTool as E2BPythonInterpreterTool
    E2B_AVAILABLE = False


class SimpleE2BAgent:
    """简化的 E2B 代码智能体"""
    
    def __init__(self, model_id: str = "gpt-4o-mini"):
        """初始化智能体"""
        
        # 创建 LLM 模型
        self.model = LiteLLMModel(
            model_id=model_id,
            max_tokens=3000,
            temperature=0.1
        )
        
        # 创建工具列表
        tools = []
        
        # 添加 Python 解释器工具
        if E2B_AVAILABLE and os.getenv("E2B_API_KEY"):
            # 使用 E2B 沙箱
            python_tool = E2BPythonInterpreterTool(
                api_key=os.getenv("E2B_API_KEY")
            )
            print("🔒 使用 E2B 安全沙箱")
        else:
            # 使用标准解释器
            python_tool = E2BPythonInterpreterTool()
            print("⚠️  使用标准 Python 解释器")
        
        tools.append(python_tool)
        
        # 添加网络搜索工具（可选）
        try:
            web_tool = WebSearchTool()
            tools.append(web_tool)
            print("🌐 网络搜索工具已启用")
        except:
            print("⚠️  网络搜索工具不可用")
        
        # 创建代码智能体
        self.agent = CodeAgent(
            model=self.model,
            tools=tools,
            max_steps=8,
            stream_outputs=True
        )
        
        print(f"🤖 智能体初始化完成 (模型: {model_id})")
    
    def run(self, task: str) -> Generator[Any, None, None]:
        """执行任务"""
        print(f"🚀 执行任务: {task}")
        print("=" * 50)
        
        try:
            for step in self.agent.run(task, stream=True):
                yield step
        except Exception as e:
            print(f"❌ 执行失败: {e}")
            raise


def demo_data_analysis():
    """数据分析演示"""
    print("\n📊 演示: 数据分析")
    print("-" * 30)
    
    agent = SimpleE2BAgent()
    task = """
    创建一个数据分析演示：
    1. 生成包含100个样本的随机数据集（年龄、收入、教育年限）
    2. 计算基础统计信息（均值、标准差、相关性）
    3. 创建散点图显示年龄与收入的关系
    4. 输出分析结论
    """
    
    for step in agent.run(task):
        if hasattr(step, 'output') and step.output:
            print(f"📝 {step.output}")


def demo_machine_learning():
    """机器学习演示"""
    print("\n🤖 演示: 机器学习")
    print("-" * 30)
    
    agent = SimpleE2BAgent()
    task = """
    创建一个机器学习分类演示：
    1. 使用 sklearn 生成二分类数据集
    2. 训练逻辑回归模型
    3. 计算模型准确率和混淆矩阵
    4. 可视化决策边界
    5. 输出模型评估结果
    """
    
    for step in agent.run(task):
        if hasattr(step, 'output') and step.output:
            print(f"📝 {step.output}")


def demo_web_analysis():
    """网络数据分析演示"""
    print("\n🌐 演示: 网络数据分析")
    print("-" * 30)
    
    agent = SimpleE2BAgent()
    task = """
    进行网络数据分析：
    1. 搜索 "Python 数据科学 2024 趋势"
    2. 分析搜索结果中的关键信息
    3. 提取主要技术趋势
    4. 创建简单的词频统计
    5. 总结发现的趋势
    """
    
    for step in agent.run(task):
        if hasattr(step, 'output') and step.output:
            print(f"📝 {step.output}")


def interactive_mode():
    """交互模式"""
    print("\n🎮 交互模式")
    print("输入任务描述，或输入 'quit' 退出")
    print("-" * 30)
    
    agent = SimpleE2BAgent()
    
    while True:
        task = input("\n💬 任务: ").strip()
        
        if task.lower() in ['quit', 'exit', 'q']:
            break
        
        if not task:
            continue
        
        try:
            for step in agent.run(task):
                if hasattr(step, 'output') and step.output:
                    print(f"📝 {step.output}")
        except Exception as e:
            print(f"❌ 错误: {e}")


def check_environment():
    """检查环境配置"""
    print("🔍 环境检查:")
    
    # 检查 LLM API 密钥
    llm_keys = ["OPENAI_API_KEY", "DEEPSEEK_API_KEY", "ANTHROPIC_API_KEY"]
    llm_configured = any(os.getenv(key) for key in llm_keys)
    
    if llm_configured:
        print("✅ LLM API 密钥已配置")
    else:
        print("❌ 未找到 LLM API 密钥")
        print("   请设置: export OPENAI_API_KEY='your-key'")
        return False
    
    # 检查 E2B API 密钥
    if os.getenv("E2B_API_KEY"):
        print("✅ E2B API 密钥已配置")
    else:
        print("⚠️  E2B API 密钥未配置")
        print("   获取免费密钥: https://e2b.dev/")
        print("   设置: export E2B_API_KEY='your-e2b-key'")
    
    return True


def main():
    """主函数"""
    print("🎯 简化的 SmolaAgents E2B 演示")
    print("基于官方文档: https://github.com/huggingface/smolagents")
    print("=" * 50)
    
    if not check_environment():
        return
    
    print("\n选择演示模式:")
    print("1. 数据分析")
    print("2. 机器学习")
    print("3. 网络数据分析")
    print("4. 交互模式")
    print("0. 全部演示")
    
    try:
        choice = input("\n请选择 (0-4): ").strip()
        
        if choice == "1":
            demo_data_analysis()
        elif choice == "2":
            demo_machine_learning()
        elif choice == "3":
            demo_web_analysis()
        elif choice == "4":
            interactive_mode()
        elif choice == "0":
            demo_data_analysis()
            demo_machine_learning() 
            demo_web_analysis()
        else:
            print("❌ 无效选择")
    
    except KeyboardInterrupt:
        print("\n⏹️  程序中断")
    except Exception as e:
        print(f"❌ 执行出错: {e}")


if __name__ == "__main__":
    main()

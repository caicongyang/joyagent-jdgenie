#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
最小化的 SmolaAgents 演示

基于 jdgenie code_interpreter.py 的核心功能，创建一个最简单的Python代码执行演示。
"""

import os
import asyncio
from typing import Generator, Any

try:
    from smolagents import LiteLLMModel, PythonInterpreterTool, CodeAgent
    print("✅ smolagents 导入成功")
except ImportError:
    print("❌ 请安装 smolagents: pip install smolagents")
    exit(1)


class MinimalPythonAgent:
    """最小化的Python代码执行智能体"""
    
    def __init__(self, model_name: str = "gpt-4o-mini"):
        """初始化智能体"""
        self.model = LiteLLMModel(
            model_id=model_name,
            max_tokens=2000
        )
        
        self.agent = CodeAgent(
            model=self.model,
            tools=[PythonInterpreterTool()],
            max_steps=5
        )
        
        print(f"🤖 智能体初始化完成，模型: {model_name}")
    
    def run(self, task: str, stream: bool = True) -> Generator[Any, None, None]:
        """执行任务"""
        print(f"🚀 执行任务: {task}")
        print("=" * 50)
        
        try:
            if stream:
                # 流式执行
                for step in self.agent.run(task, stream=True):
                    yield step
            else:
                # 非流式执行
                result = self.agent.run(task)
                yield result
                
        except Exception as e:
            print(f"❌ 执行出错: {e}")
            raise


def demo_basic_math():
    """演示基础数学计算"""
    print("\n📊 演示1: 基础数学计算")
    print("-" * 30)
    
    agent = MinimalPythonAgent()
    task = "计算1到10的平方和，并打印每个数的平方值"
    
    for step in agent.run(task):
        print(f"📝 步骤: {type(step).__name__}")
        if hasattr(step, 'output'):
            print(f"💬 输出: {step.output}")


def demo_data_analysis():
    """演示数据分析"""
    print("\n📈 演示2: 数据分析")
    print("-" * 30)
    
    agent = MinimalPythonAgent()
    task = """
    创建一个简单的数据集：
    - 包含5个学生的姓名、数学成绩、英语成绩
    - 计算每个学生的平均分
    - 找出总分最高的学生
    - 打印结果
    """
    
    for step in agent.run(task):
        print(f"📝 步骤: {type(step).__name__}")
        if hasattr(step, 'output'):
            print(f"💬 输出: {step.output}")


def demo_visualization():
    """演示数据可视化"""
    print("\n📊 演示3: 数据可视化")
    print("-" * 30)
    
    agent = MinimalPythonAgent()
    task = """
    使用matplotlib创建一个简单的图表：
    - 生成10个随机数
    - 创建柱状图
    - 添加标题和标签
    - 显示图表（如果可能的话）
    """
    
    for step in agent.run(task):
        print(f"📝 步骤: {type(step).__name__}")
        if hasattr(step, 'output'):
            print(f"💬 输出: {step.output}")


async def interactive_mode():
    """交互模式"""
    print("\n🎮 交互模式")
    print("输入Python任务，输入'quit'退出")
    print("-" * 30)
    
    agent = MinimalPythonAgent()
    
    while True:
        task = input("\n💬 任务: ").strip()
        
        if task.lower() in ['quit', 'exit', 'q']:
            break
            
        if not task:
            continue
            
        try:
            for step in agent.run(task):
                print(f"📝 {type(step).__name__}")
                if hasattr(step, 'output'):
                    print(f"💬 {step.output}")
        except Exception as e:
            print(f"❌ 错误: {e}")


def check_environment():
    """检查环境"""
    print("🔍 检查环境...")
    
    # 检查API密钥
    if not os.getenv("OPENAI_API_KEY") and not os.getenv("DEEPSEEK_API_KEY"):
        print("⚠️  未检测到API密钥")
        print("请设置: export OPENAI_API_KEY='your-key'")
        return False
    
    print("✅ 环境检查通过")
    return True


def main():
    """主函数"""
    print("🎯 最小化 SmolaAgents 演示")
    print("基于 jdgenie code_interpreter")
    print("=" * 40)
    
    if not check_environment():
        return
    
    print("\n选择演示模式:")
    print("1. 基础数学计算")
    print("2. 数据分析") 
    print("3. 数据可视化")
    print("4. 交互模式")
    print("0. 全部演示")
    
    try:
        choice = input("\n请选择 (0-4): ").strip()
        
        if choice == "1":
            demo_basic_math()
        elif choice == "2":
            demo_data_analysis()
        elif choice == "3":
            demo_visualization()
        elif choice == "4":
            asyncio.run(interactive_mode())
        elif choice == "0":
            demo_basic_math()
            demo_data_analysis()
            demo_visualization()
        else:
            print("❌ 无效选择")
            
    except KeyboardInterrupt:
        print("\n⏹️  程序中断")
    except Exception as e:
        print(f"❌ 执行出错: {e}")


if __name__ == "__main__":
    main()

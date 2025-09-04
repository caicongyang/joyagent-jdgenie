#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SmolaAgents 最小化演示

基于 jdgenie 的 code_interpreter.py，创建一个简化的 Python 代码执行演示。
使用 smolagents 框架执行 Python 代码并展示结果。
"""

import os
import asyncio
import tempfile
import shutil
from typing import Optional, List

try:
    from smolagents import LiteLLMModel, PythonInterpreterTool, CodeAgent
    from smolagents import FinalAnswerStep, ChatMessageStreamDelta
    print("✅ smolagents 导入成功")
except ImportError:
    print("❌ 请安装 smolagents: pip install smolagents")
    exit(1)


class SimplePythonAgent:
    """简化的Python代码执行智能体"""
    
    def __init__(self, model_name: str = "gpt-4o-mini"):
        """
        初始化智能体
        
        Args:
            model_name: 使用的LLM模型名称
        """
        self.model_name = model_name
        self.work_dir = None
        
        # 创建LLM模型
        self.model = LiteLLMModel(
            model_id=model_name,
            max_tokens=4000
        )
        
        # 创建Python解释器工具
        self.python_tool = PythonInterpreterTool()
        
        # 创建代码智能体
        self.agent = CodeAgent(
            model=self.model,
            tools=[self.python_tool],
            max_steps=10
        )
        
        print(f"🤖 Python智能体初始化完成，模型: {model_name}")
    
    def setup_workspace(self):
        """设置工作空间"""
        if not self.work_dir:
            self.work_dir = tempfile.mkdtemp()
            print(f"📁 工作目录: {self.work_dir}")
        return self.work_dir
    
    def cleanup_workspace(self):
        """清理工作空间"""
        if self.work_dir and os.path.exists(self.work_dir):
            shutil.rmtree(self.work_dir, ignore_errors=True)
            print("🧹 工作目录已清理")
    
    def run_task(self, task: str, stream: bool = True):
        """
        执行任务
        
        Args:
            task: 任务描述
            stream: 是否流式输出
        
        Returns:
            生成器或结果
        """
        print(f"🚀 开始执行任务: {task}")
        print("=" * 50)
        
        try:
            if stream:
                # 流式执行
                for step in self.agent.run(task, stream=True):
                    yield step
            else:
                # 非流式执行
                result = self.agent.run(task)
                return result
                
        except Exception as e:
            print(f"❌ 执行出错: {e}")
            raise e


class DemoRunner:
    """演示运行器"""
    
    def __init__(self):
        self.agent = None
    
    async def run_demo(self):
        """运行演示"""
        print("🎯 SmolaAgents Python代码执行演示")
        print("=" * 60)
        
        # 检查环境
        if not self._check_environment():
            return
        
        # 创建智能体
        self.agent = SimplePythonAgent()
        self.agent.setup_workspace()
        
        try:
            # 运行演示任务
            await self._run_demo_tasks()
            
        finally:
            # 清理资源
            if self.agent:
                self.agent.cleanup_workspace()
    
    def _check_environment(self) -> bool:
        """检查环境配置"""
        print("🔍 检查环境配置...")
        
        # 检查API密钥
        if not os.getenv("OPENAI_API_KEY") and not os.getenv("DEEPSEEK_API_KEY"):
            print("⚠️  警告：未检测到API密钥")
            print("   请设置环境变量: export OPENAI_API_KEY='your-key-here'")
            print("   或者: export DEEPSEEK_API_KEY='your-key-here'")
            
            # 询问是否继续
            choice = input("是否继续演示？(y/n): ").strip().lower()
            if choice not in ['y', 'yes']:
                return False
        
        print("✅ 环境检查完成")
        return True
    
    async def _run_demo_tasks(self):
        """运行演示任务"""
        
        # 演示任务列表
        demo_tasks = [
            {
                "name": "数学计算演示",
                "task": "计算1到100的平方和，并绘制一个简单的柱状图展示前10个数的平方值"
            },
            {
                "name": "数据分析演示", 
                "task": "创建一个包含姓名、年龄、城市的示例数据集，进行简单的数据分析，包括平均年龄、城市分布等"
            },
            {
                "name": "文件操作演示",
                "task": "创建一个简单的CSV文件，包含产品名称和价格，然后读取并计算总价值"
            }
        ]
        
        for i, demo in enumerate(demo_tasks, 1):
            print(f"\n📋 演示 {i}: {demo['name']}")
            print("-" * 40)
            
            try:
                # 流式执行任务
                for step in self.agent.run_task(demo['task'], stream=True):
                    self._handle_step_output(step)
                    
            except Exception as e:
                print(f"❌ 演示 {i} 执行失败: {e}")
            
            # 询问是否继续下一个演示
            if i < len(demo_tasks):
                print("\n" + "="*60)
                choice = input("按回车继续下一个演示，或输入 'q' 退出: ").strip()
                if choice.lower() == 'q':
                    break
    
    def _handle_step_output(self, step):
        """处理步骤输出"""
        if isinstance(step, FinalAnswerStep):
            print(f"🎉 最终答案: {step.output}")
        elif isinstance(step, ChatMessageStreamDelta):
            if step.content:
                print(step.content, end='', flush=True)
        else:
            # 其他类型的步骤
            print(f"📝 步骤: {type(step).__name__}")


async def interactive_mode():
    """交互式模式"""
    print("\n🎮 进入交互式模式")
    print("输入Python任务描述，智能体将为您执行代码")
    print("输入 'quit' 退出")
    print("-" * 40)
    
    agent = SimplePythonAgent()
    agent.setup_workspace()
    
    try:
        while True:
            task = input("\n💬 请输入任务: ").strip()
            
            if task.lower() in ['quit', 'exit', 'q']:
                print("👋 退出交互模式")
                break
            
            if not task:
                continue
            
            try:
                print(f"\n🚀 执行任务: {task}")
                print("-" * 30)
                
                for step in agent.run_task(task, stream=True):
                    if isinstance(step, FinalAnswerStep):
                        print(f"\n✅ 结果: {step.output}")
                    elif isinstance(step, ChatMessageStreamDelta):
                        if step.content:
                            print(step.content, end='', flush=True)
                            
            except Exception as e:
                print(f"❌ 执行出错: {e}")
                
    finally:
        agent.cleanup_workspace()


async def simple_demo():
    """简单演示"""
    print("🎯 简单Python代码执行演示")
    print("=" * 40)
    
    # 创建智能体
    agent = SimplePythonAgent()
    agent.setup_workspace()
    
    try:
        # 简单的数学计算任务
        task = "计算斐波那契数列的前20项，并打印结果"
        
        print(f"📝 任务: {task}")
        print("-" * 30)
        
        for step in agent.run_task(task, stream=True):
            if isinstance(step, FinalAnswerStep):
                print(f"\n✅ 最终结果:\n{step.output}")
            elif isinstance(step, ChatMessageStreamDelta):
                if step.content:
                    print(step.content, end='', flush=True)
                    
    except Exception as e:
        print(f"❌ 演示失败: {e}")
        
    finally:
        agent.cleanup_workspace()


def print_usage():
    """打印使用说明"""
    print("""
🎯 SmolaAgents Python代码执行演示

使用方法:
    python smolagents_demo.py [模式]

模式选项:
    simple      - 简单演示（默认）
    demo        - 完整演示
    interactive - 交互式模式

环境要求:
    - Python 3.8+
    - smolagents 包
    - OpenAI API密钥或DeepSeek API密钥

环境变量:
    export OPENAI_API_KEY='your-openai-key'
    或
    export DEEPSEEK_API_KEY='your-deepseek-key'

示例:
    python smolagents_demo.py simple
    python smolagents_demo.py demo
    python smolagents_demo.py interactive
    """)


async def main():
    """主函数"""
    import sys
    
    # 解析命令行参数
    mode = sys.argv[1] if len(sys.argv) > 1 else "simple"
    
    if mode == "help" or mode == "-h" or mode == "--help":
        print_usage()
        return
    
    print("🚀 启动 SmolaAgents 演示程序...")
    
    try:
        if mode == "simple":
            await simple_demo()
        elif mode == "demo":
            runner = DemoRunner()
            await runner.run_demo()
        elif mode == "interactive":
            await interactive_mode()
        else:
            print(f"❌ 未知模式: {mode}")
            print_usage()
            
    except KeyboardInterrupt:
        print("\n⏹️  程序被用户中断")
    except Exception as e:
        print(f"❌ 程序执行出错: {e}")


if __name__ == "__main__":
    print("🎮 SmolaAgents Python代码执行演示")
    print("基于 jdgenie code_interpreter 的最小化实现")
    print("=" * 60)
    
    try:
        asyncio.run(main())
    except Exception as e:
        print(f"❌ 启动失败: {e}")
        print("💡 请检查依赖安装和环境配置")

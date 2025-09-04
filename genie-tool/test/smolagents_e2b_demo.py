#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SmolaAgents E2B Sandbox 演示

基于官方 SmolaAgents 文档，使用 E2B sandbox 进行安全的代码执行。
E2B 提供了一个隔离的执行环境，确保代码执行的安全性。

官方文档: https://github.com/huggingface/smolagents
E2B 文档: https://e2b.dev/
"""

import os
import asyncio
from typing import Optional, List, Generator, Any

# 检查必要的依赖
try:
    from smolagents import CodeAgent, LiteLLMModel, InferenceClientModel
    from smolagents import WebSearchTool, FinalAnswerStep, ChatMessageStreamDelta
    print("✅ smolagents 核心组件导入成功")
except ImportError as e:
    print(f"❌ smolagents 导入失败: {e}")
    print("请安装: pip install smolagents")
    exit(1)

try:
    from smolagents.tools import E2BPythonInterpreterTool
    print("✅ E2B Python 解释器工具导入成功")
except ImportError:
    print("⚠️  E2B Python 解释器工具未找到，将使用标准 PythonInterpreterTool")
    try:
        from smolagents import PythonInterpreterTool as E2BPythonInterpreterTool
        print("✅ 使用标准 PythonInterpreterTool 作为替代")
    except ImportError:
        print("❌ 无法导入 Python 解释器工具")
        exit(1)


class E2BCodeAgent:
    """使用 E2B Sandbox 的安全代码执行智能体"""
    
    def __init__(self, 
                 model_type: str = "LiteLLM",
                 model_id: str = "gpt-4o-mini",
                 e2b_api_key: Optional[str] = None,
                 use_web_search: bool = False):
        """
        初始化 E2B 代码智能体
        
        Args:
            model_type: 模型类型 ("LiteLLM", "InferenceClient", "OpenAI")
            model_id: 模型ID
            e2b_api_key: E2B API密钥
            use_web_search: 是否启用网络搜索功能
        """
        self.model_type = model_type
        self.model_id = model_id
        self.e2b_api_key = e2b_api_key or os.getenv("E2B_API_KEY")
        
        # 初始化模型
        self.model = self._create_model()
        
        # 初始化工具
        self.tools = self._create_tools(use_web_search)
        
        # 创建代码智能体
        self.agent = CodeAgent(
            model=self.model,
            tools=self.tools,
            max_steps=10,
            stream_outputs=True  # 启用流式输出
        )
        
        print(f"🤖 E2B 代码智能体初始化完成")
        print(f"   模型: {model_type}/{model_id}")
        print(f"   E2B Sandbox: {'✅ 已配置' if self.e2b_api_key else '⚠️  未配置API密钥'}")
        print(f"   工具数量: {len(self.tools)}")
    
    def _create_model(self):
        """创建LLM模型"""
        if self.model_type.lower() == "litellm":
            return LiteLLMModel(
                model_id=self.model_id,
                max_tokens=4000,
                temperature=0.1
            )
        elif self.model_type.lower() == "inferenceclient":
            return InferenceClientModel(
                model_id=self.model_id,
                max_tokens=4000,
                temperature=0.1
            )
        else:
            # 默认使用 LiteLLM
            return LiteLLMModel(
                model_id=self.model_id,
                max_tokens=4000,
                temperature=0.1
            )
    
    def _create_tools(self, use_web_search: bool = False) -> List:
        """创建工具列表"""
        tools = []
        
        # 添加 E2B Python 解释器工具
        try:
            if self.e2b_api_key:
                # 使用 E2B sandbox
                python_tool = E2BPythonInterpreterTool(
                    api_key=self.e2b_api_key
                )
                print("✅ 使用 E2B Sandbox Python 解释器")
            else:
                # 使用标准 Python 解释器
                python_tool = E2BPythonInterpreterTool()
                print("⚠️  使用标准 Python 解释器 (建议配置 E2B API Key)")
            
            tools.append(python_tool)
        except Exception as e:
            print(f"❌ Python 解释器工具初始化失败: {e}")
        
        # 添加网络搜索工具
        if use_web_search:
            try:
                web_tool = WebSearchTool()
                tools.append(web_tool)
                print("✅ 网络搜索工具已添加")
            except Exception as e:
                print(f"⚠️  网络搜索工具初始化失败: {e}")
        
        return tools
    
    def run_task(self, task: str, stream: bool = True) -> Generator[Any, None, None]:
        """
        执行任务
        
        Args:
            task: 任务描述
            stream: 是否流式输出
        
        Returns:
            生成器，逐步返回执行结果
        """
        print(f"🚀 开始执行任务: {task}")
        print("=" * 60)
        
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
            print(f"❌ 任务执行失败: {e}")
            raise


class E2BDemoRunner:
    """E2B 演示运行器"""
    
    def __init__(self):
        self.agent = None
    
    def check_environment(self) -> bool:
        """检查环境配置"""
        print("🔍 检查环境配置...")
        
        # 检查 LLM API 密钥
        llm_key_found = False
        if os.getenv("OPENAI_API_KEY"):
            print("✅ OPENAI_API_KEY 已配置")
            llm_key_found = True
        if os.getenv("DEEPSEEK_API_KEY"):
            print("✅ DEEPSEEK_API_KEY 已配置")
            llm_key_found = True
        if os.getenv("ANTHROPIC_API_KEY"):
            print("✅ ANTHROPIC_API_KEY 已配置")
            llm_key_found = True
            
        if not llm_key_found:
            print("⚠️  未检测到 LLM API 密钥")
            print("   请设置以下环境变量之一:")
            print("   export OPENAI_API_KEY='your-key'")
            print("   export DEEPSEEK_API_KEY='your-key'")
            print("   export ANTHROPIC_API_KEY='your-key'")
        
        # 检查 E2B API 密钥
        if os.getenv("E2B_API_KEY"):
            print("✅ E2B_API_KEY 已配置")
        else:
            print("⚠️  E2B_API_KEY 未配置")
            print("   访问 https://e2b.dev/ 获取免费API密钥")
            print("   export E2B_API_KEY='your-e2b-key'")
        
        print("✅ 环境检查完成")
        return True
    
    async def run_demo(self):
        """运行演示"""
        print("🎯 SmolaAgents E2B Sandbox 演示")
        print("基于官方文档: https://github.com/huggingface/smolagents")
        print("=" * 60)
        
        if not self.check_environment():
            return
        
        # 创建智能体
        print("\n🤖 创建 E2B 代码智能体...")
        self.agent = E2BCodeAgent(
            model_type="LiteLLM",
            model_id="gpt-4o-mini",
            use_web_search=True
        )
        
        # 运行演示任务
        await self._run_demo_tasks()
    
    async def _run_demo_tasks(self):
        """运行演示任务"""
        
        demo_tasks = [
            {
                "name": "数据科学分析",
                "task": """
                创建一个数据科学分析：
                1. 生成一个包含100个数据点的随机数据集（包含年龄、收入、教育水平）
                2. 进行基础统计分析
                3. 创建相关性分析
                4. 绘制数据可视化图表
                5. 总结分析结果
                """
            },
            {
                "name": "机器学习演示",
                "task": """
                创建一个简单的机器学习演示：
                1. 使用 sklearn 生成分类数据集
                2. 训练一个简单的分类模型
                3. 评估模型性能
                4. 绘制决策边界
                5. 解释结果
                """
            },
            {
                "name": "网络数据分析",
                "task": """
                进行网络数据分析：
                1. 搜索最新的 Python 编程趋势
                2. 分析搜索结果
                3. 创建一个简单的数据可视化
                4. 总结发现的趋势
                """
            }
        ]
        
        for i, demo in enumerate(demo_tasks, 1):
            print(f"\n📋 演示 {i}: {demo['name']}")
            print("-" * 50)
            
            try:
                step_count = 0
                for step in self.agent.run_task(demo['task'], stream=True):
                    step_count += 1
                    self._handle_step_output(step, step_count)
                    
                    # 异步让出控制权
                    await asyncio.sleep(0.1)
                    
            except Exception as e:
                print(f"❌ 演示 {i} 执行失败: {e}")
            
            # 询问是否继续
            if i < len(demo_tasks):
                print("\n" + "="*60)
                choice = input("按回车继续下一个演示，或输入 'q' 退出: ").strip()
                if choice.lower() == 'q':
                    break
    
    def _handle_step_output(self, step, step_num: int):
        """处理步骤输出"""
        if isinstance(step, FinalAnswerStep):
            print(f"\n🎉 最终答案:")
            print("-" * 30)
            print(step.output)
        elif isinstance(step, ChatMessageStreamDelta):
            if step.content:
                print(step.content, end='', flush=True)
        else:
            # 其他类型的步骤
            print(f"\n📝 步骤 {step_num}: {type(step).__name__}")
            if hasattr(step, 'output') and step.output:
                print(f"   输出: {step.output[:100]}...")


async def interactive_mode():
    """交互模式"""
    print("\n🎮 E2B Sandbox 交互模式")
    print("输入任务描述，智能体将在安全的 E2B 环境中执行代码")
    print("输入 'quit' 退出")
    print("-" * 50)
    
    # 创建智能体
    agent = E2BCodeAgent(
        model_type="LiteLLM",
        model_id="gpt-4o-mini",
        use_web_search=True
    )
    
    while True:
        task = input("\n💬 请输入任务: ").strip()
        
        if task.lower() in ['quit', 'exit', 'q']:
            print("👋 退出交互模式")
            break
        
        if not task:
            continue
        
        try:
            print(f"\n🚀 执行任务: {task}")
            print("-" * 40)
            
            step_count = 0
            for step in agent.run_task(task, stream=True):
                step_count += 1
                
                if isinstance(step, FinalAnswerStep):
                    print(f"\n✅ 最终结果:")
                    print(step.output)
                elif isinstance(step, ChatMessageStreamDelta):
                    if step.content:
                        print(step.content, end='', flush=True)
                else:
                    print(f"\n📝 步骤 {step_count}: {type(step).__name__}")
                
                # 异步让出控制权
                await asyncio.sleep(0.05)
                
        except Exception as e:
            print(f"❌ 执行出错: {e}")


def print_usage():
    """打印使用说明"""
    print("""
🎯 SmolaAgents E2B Sandbox 演示

基于官方文档: https://github.com/huggingface/smolagents

使用方法:
    python smolagents_e2b_demo.py [模式]

模式选项:
    demo        - 完整演示（默认）
    interactive - 交互模式
    help        - 显示帮助

环境要求:
    - Python 3.8+
    - smolagents 包
    - LLM API密钥 (OpenAI/DeepSeek/Anthropic)
    - E2B API密钥 (可选，用于安全沙箱)

环境变量:
    # LLM API (选择其一)
    export OPENAI_API_KEY='your-openai-key'
    export DEEPSEEK_API_KEY='your-deepseek-key'
    export ANTHROPIC_API_KEY='your-anthropic-key'
    
    # E2B Sandbox (可选)
    export E2B_API_KEY='your-e2b-key'

E2B 安全特性:
    - 完全隔离的执行环境
    - 防止恶意代码影响主机系统
    - 支持网络访问和文件操作
    - 自动资源清理

示例:
    python smolagents_e2b_demo.py demo
    python smolagents_e2b_demo.py interactive
    """)


async def main():
    """主函数"""
    import sys
    
    # 解析命令行参数
    mode = sys.argv[1] if len(sys.argv) > 1 else "demo"
    
    if mode in ["help", "-h", "--help"]:
        print_usage()
        return
    
    print("🚀 启动 SmolaAgents E2B Sandbox 演示程序...")
    print("基于官方文档: https://github.com/huggingface/smolagents")
    
    try:
        if mode == "demo":
            runner = E2BDemoRunner()
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
        import traceback
        traceback.print_exc()


if __name__ == "__main__":
    print("🎮 SmolaAgents E2B Sandbox 演示")
    print("安全的代码执行环境 + 强大的AI智能体")
    print("=" * 60)
    
    try:
        asyncio.run(main())
    except Exception as e:
        print(f"❌ 启动失败: {e}")
        print("💡 请检查依赖安装和环境配置")

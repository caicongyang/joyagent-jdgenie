#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
安装 E2B 相关依赖的脚本

自动检查和安装运行 SmolaAgents E2B 演示所需的依赖包。
"""

import subprocess
import sys
import os
from typing import List, Tuple


def run_command(command: str) -> Tuple[bool, str]:
    """执行命令并返回结果"""
    try:
        result = subprocess.run(
            command.split(),
            capture_output=True,
            text=True,
            check=True
        )
        return True, result.stdout
    except subprocess.CalledProcessError as e:
        return False, e.stderr


def check_package_installed(package: str) -> bool:
    """检查包是否已安装"""
    try:
        __import__(package.replace('-', '_'))
        return True
    except ImportError:
        return False


def install_package(package: str) -> bool:
    """安装包"""
    print(f"📦 安装 {package}...")
    success, output = run_command(f"pip install {package}")
    
    if success:
        print(f"✅ {package} 安装成功")
        return True
    else:
        print(f"❌ {package} 安装失败: {output}")
        return False


def check_smolagents_version():
    """检查 smolagents 版本"""
    try:
        import smolagents
        version = getattr(smolagents, '__version__', '未知')
        print(f"✅ smolagents 版本: {version}")
        
        # 检查是否有 E2B 支持
        try:
            from smolagents.tools import E2BPythonInterpreterTool
            print("✅ E2B 工具支持可用")
            return True
        except ImportError:
            print("⚠️  当前版本不支持 E2B 工具")
            return False
            
    except ImportError:
        print("❌ smolagents 未安装")
        return False


def install_dependencies():
    """安装所有必要依赖"""
    print("🚀 开始安装 E2B 演示依赖...")
    print("=" * 50)
    
    # 核心依赖
    core_packages = [
        "smolagents",
        "litellm",
        "requests",
    ]
    
    # 可选依赖（用于增强功能）
    optional_packages = [
        "pandas",
        "numpy", 
        "matplotlib",
        "seaborn",
        "scikit-learn",
        "plotly",
    ]
    
    # E2B 相关依赖
    e2b_packages = [
        "e2b",
    ]
    
    failed_packages = []
    
    # 安装核心依赖
    print("\n📦 安装核心依赖...")
    for package in core_packages:
        if not install_package(package):
            failed_packages.append(package)
    
    # 安装 E2B 依赖
    print("\n🔒 安装 E2B 依赖...")
    for package in e2b_packages:
        if not install_package(package):
            failed_packages.append(package)
    
    # 安装可选依赖
    print("\n📊 安装可选依赖（数据科学相关）...")
    for package in optional_packages:
        if not check_package_installed(package.replace('-', '_')):
            if not install_package(package):
                print(f"⚠️  {package} 安装失败，但不影响核心功能")
        else:
            print(f"✅ {package} 已安装")
    
    # 检查安装结果
    print("\n" + "=" * 50)
    print("📋 安装结果:")
    
    if failed_packages:
        print(f"❌ 以下包安装失败: {', '.join(failed_packages)}")
        return False
    else:
        print("✅ 所有核心依赖安装成功!")
        return True


def check_environment():
    """检查环境配置"""
    print("\n🔍 检查环境配置...")
    
    # 检查 Python 版本
    python_version = sys.version_info
    print(f"🐍 Python 版本: {python_version.major}.{python_version.minor}.{python_version.micro}")
    
    if python_version < (3, 8):
        print("❌ 需要 Python 3.8 或更高版本")
        return False
    
    # 检查 pip 版本
    success, pip_output = run_command("pip --version")
    if success:
        print(f"📦 {pip_output.strip()}")
    
    # 检查环境变量
    env_vars = {
        "OPENAI_API_KEY": "OpenAI API 密钥",
        "DEEPSEEK_API_KEY": "DeepSeek API 密钥", 
        "ANTHROPIC_API_KEY": "Anthropic API 密钥",
        "E2B_API_KEY": "E2B API 密钥",
    }
    
    print("\n🔑 环境变量检查:")
    llm_key_found = False
    
    for var, desc in env_vars.items():
        if os.getenv(var):
            print(f"✅ {var} 已配置")
            if var in ["OPENAI_API_KEY", "DEEPSEEK_API_KEY", "ANTHROPIC_API_KEY"]:
                llm_key_found = True
        else:
            print(f"⚠️  {var} 未配置 ({desc})")
    
    if not llm_key_found:
        print("\n❌ 需要至少配置一个 LLM API 密钥")
        print("   例如: export OPENAI_API_KEY='your-key-here'")
        return False
    
    return True


def test_installation():
    """测试安装是否成功"""
    print("\n🧪 测试安装...")
    
    try:
        # 测试核心组件
        from smolagents import CodeAgent, LiteLLMModel
        print("✅ SmolaAgents 核心组件可用")
        
        # 测试 E2B 工具
        try:
            from smolagents.tools import E2BPythonInterpreterTool
            print("✅ E2B Python 解释器工具可用")
        except ImportError:
            print("⚠️  E2B 工具不可用，将使用标准工具")
        
        # 测试可选组件
        optional_imports = [
            ("pandas", "数据处理"),
            ("numpy", "数值计算"),
            ("matplotlib", "数据可视化"),
            ("sklearn", "机器学习"),
        ]
        
        for module, desc in optional_imports:
            try:
                __import__(module)
                print(f"✅ {module} 可用 ({desc})")
            except ImportError:
                print(f"⚠️  {module} 不可用 ({desc})")
        
        return True
        
    except ImportError as e:
        print(f"❌ 安装测试失败: {e}")
        return False


def print_next_steps():
    """打印后续步骤"""
    print("\n" + "=" * 50)
    print("🎯 后续步骤:")
    print()
    print("1. 配置 API 密钥:")
    print("   export OPENAI_API_KEY='your-openai-key'")
    print("   export E2B_API_KEY='your-e2b-key'")
    print()
    print("2. 获取 E2B API 密钥:")
    print("   • 访问 https://e2b.dev/")
    print("   • 注册免费账户")
    print("   • 创建 API 密钥")
    print()
    print("3. 运行演示程序:")
    print("   python simple_e2b_demo.py")
    print("   python smolagents_e2b_demo.py demo")
    print()
    print("4. 查看详细文档:")
    print("   cat e2b_setup_guide.md")


def main():
    """主函数"""
    print("🎯 SmolaAgents E2B 依赖安装脚本")
    print("=" * 50)
    
    # 检查环境
    if not check_environment():
        print("\n❌ 环境检查失败，请修复后重试")
        return
    
    # 检查现有安装
    if check_smolagents_version():
        print("✅ SmolaAgents 已安装")
        
        choice = input("\n是否要重新安装/更新依赖? (y/N): ").strip().lower()
        if choice not in ['y', 'yes']:
            print("跳过安装，进行测试...")
            if test_installation():
                print_next_steps()
            return
    
    # 安装依赖
    if install_dependencies():
        print("\n✅ 依赖安装完成!")
        
        # 测试安装
        if test_installation():
            print("\n🎉 所有组件测试通过!")
            print_next_steps()
        else:
            print("\n❌ 安装测试失败，请检查错误信息")
    else:
        print("\n❌ 依赖安装失败")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n⏹️  安装被用户中断")
    except Exception as e:
        print(f"\n❌ 安装过程出错: {e}")
        import traceback
        traceback.print_exc()

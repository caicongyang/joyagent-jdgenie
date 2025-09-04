#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PPT生成器演示程序启动脚本

简化的启动脚本，方便快速运行PPT生成演示。
"""

import asyncio
import sys
import os

# 确保可以导入demo模块
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from ppt_generator_demo import PPTGeneratorDemo


async def quick_demo():
    """快速演示PPT生成功能"""
    print("🚀 启动PPT生成器快速演示...")
    
    demo = PPTGeneratorDemo()
    
    # 检查环境配置
    if not os.getenv("OPENAI_API_KEY") and not os.getenv("DEEPSEEK_API_KEY"):
        print("⚠️  注意：未检测到API密钥")
        print("   请设置环境变量: export OPENAI_API_KEY='your-key-here'")
        print("   或者: export DEEPSEEK_API_KEY='your-key-here'")
        print("   继续运行演示（可能会失败）...")
        print()
    
    # 运行简单的PPT演示
    print("📝 开始生成AI技术趋势PPT演示...")
    await demo.demo_simple_ppt()


if __name__ == "__main__":
    try:
        asyncio.run(quick_demo())
    except KeyboardInterrupt:
        print("\n⏹️  用户中断操作")
    except Exception as e:
        print(f"❌ 运行出错: {e}")
        print("\n💡 提示:")
        print("   1. 确保已设置API密钥")
        print("   2. 检查网络连接")
        print("   3. 运行完整版演示: python ppt_generator_demo.py")

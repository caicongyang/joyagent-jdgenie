#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PPT转换功能依赖安装脚本
"""

import subprocess
import sys
import os


def install_package(package_name):
    """安装Python包"""
    try:
        print(f"📦 正在安装 {package_name}...")
        subprocess.check_call([sys.executable, "-m", "pip", "install", package_name])
        print(f"✅ {package_name} 安装成功")
        return True
    except subprocess.CalledProcessError as e:
        print(f"❌ {package_name} 安装失败: {e}")
        return False


def check_package(package_name, import_name=None):
    """检查包是否已安装"""
    if import_name is None:
        import_name = package_name
    
    try:
        __import__(import_name)
        print(f"✅ {package_name} 已安装")
        return True
    except ImportError:
        print(f"⚠️  {package_name} 未安装")
        return False


def main():
    """主安装流程"""
    print("🚀 开始安装PPT转换功能所需依赖包...")
    print("=" * 50)
    
    # 必需的依赖包
    required_packages = [
        ("python-pptx", "pptx"),
        ("beautifulsoup4", "bs4"),
        ("lxml", "lxml"),  # beautifulsoup4的解析器
    ]
    
    # 可选的依赖包
    optional_packages = [
        ("selenium", "selenium"),
        ("Pillow", "PIL"),  # 图像处理
        ("webdriver-manager", "webdriver_manager"),  # 自动管理webdriver
    ]
    
    print("📋 检查必需依赖包...")
    missing_required = []
    for package_name, import_name in required_packages:
        if not check_package(package_name, import_name):
            missing_required.append(package_name)
    
    print("\n📋 检查可选依赖包...")
    missing_optional = []
    for package_name, import_name in optional_packages:
        if not check_package(package_name, import_name):
            missing_optional.append(package_name)
    
    # 安装缺失的必需包
    if missing_required:
        print(f"\n🔧 安装缺失的必需依赖包: {', '.join(missing_required)}")
        for package in missing_required:
            if not install_package(package):
                print(f"❌ 必需依赖包 {package} 安装失败，程序可能无法正常工作")
                return False
    
    # 询问是否安装可选包
    if missing_optional:
        print(f"\n🤔 发现缺失的可选依赖包: {', '.join(missing_optional)}")
        print("这些包提供额外功能：")
        print("  - selenium: 支持浏览器截图转换（推荐）")
        print("  - Pillow: 图像处理支持")
        print("  - webdriver-manager: 自动管理Chrome驱动")
        
        install_optional = input("\n是否安装可选依赖包？(y/n): ").strip().lower()
        if install_optional in ['y', 'yes']:
            for package in missing_optional:
                install_package(package)
    
    print("\n🎯 功能说明:")
    print("现在您可以使用以下功能:")
    print("1. 基础功能: HTML内容解析转PPT (仅需python-pptx)")
    print("2. 高级功能: 浏览器截图转PPT (需要selenium)")
    print("3. 混合功能: 结合两种方案的最佳效果")
    
    print("\n🚀 使用方法:")
    print("python html_to_ppt_converter.py")
    
    print("\n✅ 依赖安装完成！")
    return True


if __name__ == "__main__":
    try:
        success = main()
        if success:
            print("\n🎉 所有依赖安装完成，可以开始使用PPT转换功能了！")
        else:
            print("\n❌ 依赖安装过程中出现问题")
            sys.exit(1)
    except KeyboardInterrupt:
        print("\n⏹️  安装被用户中断")
        sys.exit(1)
    except Exception as e:
        print(f"\n❌ 安装过程中出现错误: {e}")
        sys.exit(1)

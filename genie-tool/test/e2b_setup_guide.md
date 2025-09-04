# SmolaAgents E2B Sandbox 配置指南

## 概述

E2B (Environment to Build) 是一个云端代码执行平台，为 AI 智能体提供安全的沙箱环境。本指南将帮助您配置和使用 E2B 与 SmolaAgents。

## E2B 的优势

### 🔒 安全性
- **完全隔离**: 代码在云端沙箱中执行，与您的本地系统完全隔离
- **恶意代码防护**: 防止恶意代码影响主机系统
- **资源限制**: 自动限制CPU、内存和存储使用

### ⚡ 性能
- **快速启动**: 沙箱环境秒级启动
- **高性能**: 云端高性能计算资源
- **自动扩展**: 根据需求自动分配资源

### 🛠️ 功能
- **完整 Python 环境**: 预装常用科学计算库
- **网络访问**: 支持网络请求和API调用
- **文件系统**: 完整的文件读写能力
- **可视化**: 支持图表生成和显示

## 安装和配置

### 1. 注册 E2B 账户

访问 [E2B 官网](https://e2b.dev/) 并注册免费账户：

```bash
# 1. 访问 https://e2b.dev/
# 2. 点击 "Sign up for free"
# 3. 使用 GitHub 或 Google 账户注册
# 4. 完成邮箱验证
```

### 2. 获取 API 密钥

在 E2B 控制台获取 API 密钥：

```bash
# 1. 登录 E2B 控制台
# 2. 进入 "API Keys" 页面
# 3. 点击 "Create API Key"
# 4. 复制生成的密钥
```

### 3. 配置环境变量

```bash
# 设置 E2B API 密钥
export E2B_API_KEY='your-e2b-api-key-here'

# 同时需要设置 LLM API 密钥（选择其一）
export OPENAI_API_KEY='your-openai-key'
export DEEPSEEK_API_KEY='your-deepseek-key'
export ANTHROPIC_API_KEY='your-anthropic-key'
```

### 4. 安装依赖

```bash
# 安装 smolagents（包含 E2B 支持）
pip install smolagents

# 可选：安装额外的科学计算库
pip install pandas numpy matplotlib seaborn scikit-learn
```

## E2B 沙箱特性

### 预装环境

E2B 沙箱预装了以下常用库：

```python
# 数据科学
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns

# 机器学习
import sklearn
from sklearn.model_selection import train_test_split
from sklearn.linear_model import LogisticRegression

# 网络请求
import requests
import json

# 文件处理
import os
import csv
import json
```

### 文件系统

```python
# 创建文件
with open('/tmp/data.txt', 'w') as f:
    f.write('Hello E2B!')

# 读取文件
with open('/tmp/data.txt', 'r') as f:
    content = f.read()
    print(content)

# 创建目录
os.makedirs('/tmp/my_project', exist_ok=True)
```

### 网络访问

```python
# HTTP 请求
import requests

response = requests.get('https://api.github.com/repos/huggingface/smolagents')
data = response.json()
print(f"Stars: {data['stargazers_count']}")

# API 调用
import openai
# 可以在沙箱中安全调用外部 API
```

## 使用示例

### 基本使用

```python
from smolagents import CodeAgent, LiteLLMModel
from smolagents.tools import E2BPythonInterpreterTool

# 创建模型
model = LiteLLMModel(model_id="gpt-4o-mini")

# 创建 E2B 工具
e2b_tool = E2BPythonInterpreterTool(api_key="your-e2b-key")

# 创建智能体
agent = CodeAgent(
    model=model,
    tools=[e2b_tool],
    stream_outputs=True
)

# 执行任务
for step in agent.run("分析一个数据集并创建可视化图表", stream=True):
    print(step)
```

### 高级配置

```python
# 自定义沙箱配置
e2b_tool = E2BPythonInterpreterTool(
    api_key="your-e2b-key",
    template_id="custom-template",  # 自定义模板
    timeout=300,  # 超时时间（秒）
    max_runtime=600  # 最大运行时间
)
```

## 与标准 Python 解释器对比

| 特性 | 标准 PythonInterpreterTool | E2B PythonInterpreterTool |
|------|---------------------------|---------------------------|
| **安全性** | ⚠️ 本地执行，存在风险 | ✅ 云端沙箱，完全隔离 |
| **性能** | ✅ 本地执行，速度快 | ⚡ 云端执行，启动略慢 |
| **网络访问** | ✅ 完整网络权限 | ✅ 受控网络访问 |
| **文件系统** | ⚠️ 完整文件系统访问 | ✅ 隔离文件系统 |
| **资源限制** | ❌ 无限制，可能消耗大量资源 | ✅ 自动资源限制 |
| **恶意代码防护** | ❌ 无防护 | ✅ 完全防护 |

## 演示程序使用

### 运行完整演示

```bash
python smolagents_e2b_demo.py demo
```

演示包含：
1. 数据科学分析
2. 机器学习演示  
3. 网络数据分析

### 交互模式

```bash
python smolagents_e2b_demo.py interactive
```

在交互模式中，您可以：
- 输入任意 Python 任务
- 实时查看执行过程
- 安全执行复杂代码

### 示例任务

```python
# 数据分析任务
"创建一个销售数据分析，包含趋势图和统计摘要"

# 机器学习任务  
"训练一个简单的分类模型，并评估其性能"

# 网络数据任务
"爬取网站数据并进行情感分析"

# 可视化任务
"创建一个交互式数据仪表板"
```

## 定价和限制

### 免费套餐
- **执行时间**: 每月 100 小时
- **并发数**: 1 个沙箱
- **存储**: 1GB 临时存储
- **网络**: 基本网络访问

### 付费套餐
- **Pro**: $20/月，500 小时执行时间
- **Team**: $100/月，2500 小时执行时间
- **Enterprise**: 定制价格

详细定价请访问：https://e2b.dev/pricing

## 故障排除

### 常见问题

#### 1. API 密钥错误
```
❌ E2B API authentication failed
```

**解决方案**：
- 检查 API 密钥是否正确
- 确认环境变量设置正确
- 验证账户状态和余额

#### 2. 沙箱启动失败
```
❌ Failed to create E2B sandbox
```

**解决方案**：
- 检查网络连接
- 确认账户未超出限制
- 等待几分钟后重试

#### 3. 代码执行超时
```
❌ Code execution timeout
```

**解决方案**：
- 优化代码性能
- 增加超时时间
- 分解复杂任务

### 调试技巧

1. **启用详细日志**
```python
import logging
logging.basicConfig(level=logging.DEBUG)
```

2. **检查沙箱状态**
```python
# 在代码中添加状态检查
print("Sandbox status:", os.getenv("E2B_SANDBOX_ID"))
```

3. **资源监控**
```python
import psutil
print(f"CPU: {psutil.cpu_percent()}%")
print(f"Memory: {psutil.virtual_memory().percent}%")
```

## 最佳实践

### 1. 安全性
- 始终使用 E2B 沙箱执行不受信任的代码
- 定期轮换 API 密钥
- 监控沙箱使用情况

### 2. 性能优化
- 重用沙箱实例减少启动时间
- 优化代码减少执行时间
- 使用异步操作提高效率

### 3. 成本控制
- 监控执行时间使用量
- 及时清理不需要的沙箱
- 使用合适的定价套餐

## 总结

E2B 为 SmolaAgents 提供了强大的安全代码执行能力，是生产环境中运行 AI 代码智能体的理想选择。通过本指南，您可以：

✅ **安全执行**: 在隔离环境中运行代码
✅ **高性能**: 利用云端计算资源  
✅ **易于使用**: 简单的 API 接口
✅ **功能完整**: 支持完整的 Python 生态系统

开始使用 E2B，让您的 AI 智能体更加安全和强大！

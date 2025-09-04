# PPT生成器演示程序

## 概述

本目录包含了从 `joyagent-jdgenie` 项目中提取并整合的PPT生成功能演示程序。该程序整合了原项目中的核心功能，提供了一个独立可执行的PPT生成器。

## 功能特性

### 🎯 核心功能
- **智能PPT生成**: 基于任务描述和输入文件生成专业的HTML格式PPT
- **多文件支持**: 支持本地文件和远程URL作为输入源
- **流式生成**: 实时显示生成进度，提供良好的用户体验
- **模板渲染**: 使用Jinja2模板引擎，支持灵活的内容渲染

### 🎨 PPT特性
- **专业设计**: 高级感、科技感的扁平化卡片样式
- **数据可视化**: 集成ECharts图表库，支持多种图表类型
- **响应式布局**: 16:9宽高比，适配不同屏幕尺寸
- **交互功能**: 支持幻灯片切换、进度条、播放控制

### 🔧 技术特性
- **异步处理**: 全异步架构，高性能文件处理和LLM调用
- **智能裁剪**: 根据模型上下文长度智能裁剪输入内容
- **容错处理**: 优雅处理文件下载失败、解析错误等异常
- **日志监控**: 企业级日志记录和性能监控

## 安装依赖

```bash
# 进入genie-tool目录
cd genie-tool

# 安装必要依赖
pip install jinja2 loguru python-dotenv litellm aiohttp
```

## 环境配置

1. 复制配置文件模板：
```bash
cp test/env_example.txt test/.env
```

2. 编辑 `.env` 文件，填入你的API密钥：
```bash
# 使用OpenAI API（推荐）
OPENAI_API_KEY=your-openai-api-key-here

# 或者使用DeepSeek API
DEEPSEEK_API_KEY=your-deepseek-api-key-here

# 可选：指定使用的模型
REPORT_MODEL=gpt-4o-mini
```

## 使用方法

### 直接运行演示程序

```bash
cd genie-tool/test
python ppt_generator_demo.py
```

### 演示模式

程序提供三种演示模式：

#### 1. AI技术趋势PPT演示
- 基于内置的AI技术发展内容
- 展示基础的PPT生成能力
- 适合快速体验功能

#### 2. 销售数据分析PPT演示
- 包含丰富的销售数据和图表
- 展示数据可视化能力
- 演示ECharts图表集成

#### 3. 自定义PPT生成
- 支持自定义任务描述
- 支持多个输入文件
- 完全灵活的内容定制

### 编程方式调用

```python
import asyncio
from ppt_generator_demo import PPTGeneratorDemo

async def generate_custom_ppt():
    demo = PPTGeneratorDemo()
    
    # 生成PPT
    content = ""
    async for chunk in demo.generate_ppt(
        task="生成一份关于机器学习的技术报告PPT",
        file_names=["report.md", "data.txt"],
        stream=True
    ):
        content += chunk
    
    # 保存文件
    file_path = await demo.save_ppt_file(content, "ml_report.html")
    print(f"PPT已保存: {file_path}")

# 运行
asyncio.run(generate_custom_ppt())
```

## 输出文件

- 生成的PPT文件保存在 `output/` 目录下
- 文件格式为HTML，可直接在浏览器中打开
- 支持完整的PPT功能：切换、播放、进度条等

## 项目结构

```
test/
├── ppt_generator_demo.py    # 主程序文件
├── env_example.txt          # 环境配置模板
├── README.md               # 使用说明
└── output/                 # 生成的PPT文件目录
    ├── ai_trends_ppt.html
    ├── sales_analysis_ppt.html
    └── custom_ppt_*.html
```

## 核心类说明

### PPTGeneratorDemo 类

主要的PPT生成器类，整合了以下功能模块：

#### 文件处理模块
- `get_file_content()`: 获取文件内容（支持本地/远程）
- `download_all_files()`: 批量下载文件
- `truncate_files()`: 智能内容裁剪
- `flatten_search_file()`: 搜索结果文件处理

#### LLM调用模块
- `ask_llm()`: 统一的LLM调用接口
- `get_context_length()`: 模型上下文长度管理

#### PPT生成模块
- `generate_ppt()`: 核心PPT生成方法
- `save_ppt_file()`: 文件保存和清理
- `clean_html_content()`: HTML内容清理

#### 演示模块
- `demo_simple_ppt()`: 简单PPT演示
- `demo_with_data_ppt()`: 数据图表PPT演示
- `demo_custom_ppt()`: 自定义PPT生成

## 技术架构

本演示程序基于原项目的以下核心模块：

1. **报告生成层** (`report.py`)
   - PPT生成的核心逻辑
   - 工厂模式的报告类型分发

2. **文件处理层** (`file_util.py`)
   - 异步文件下载和处理
   - 智能内容裁剪机制

3. **LLM调用层** (`llm_util.py`)
   - 统一的LLM接口封装
   - 流式/非流式调用支持

4. **模板系统** (`prompt_util.py` + `report.yaml`)
   - Jinja2模板渲染
   - 专业的PPT生成提示词

5. **基础设施层**
   - 日志和性能监控
   - 上下文管理和配置

## 注意事项

1. **API密钥**: 确保设置了有效的OpenAI或DeepSeek API密钥
2. **网络连接**: 远程文件下载需要稳定的网络连接
3. **内存使用**: 大文件处理时注意内存使用情况
4. **输出质量**: 生成质量取决于输入内容的质量和任务描述的清晰度

## 故障排除

### 常见问题

1. **ModuleNotFoundError**: 检查是否安装了所有必要依赖
2. **API调用失败**: 检查API密钥是否正确设置
3. **文件下载失败**: 检查文件路径或URL是否有效
4. **生成内容为空**: 检查模型配置和网络连接

### 调试方法

程序提供详细的日志输出，日志文件保存在 `logs/` 目录下：

```bash
# 查看最新日志
tail -f logs/ppt_demo_*.log
```

## 扩展开发

可以基于此演示程序进行进一步开发：

1. **自定义模板**: 修改 `_get_ppt_template()` 方法
2. **新增模型**: 在 `model_context_lengths` 中添加新模型
3. **增强功能**: 扩展文件处理或输出格式
4. **集成API**: 将功能封装为Web API服务

## HTML转真正的PPT格式

### 问题说明

原项目生成的是HTML格式的"PPT"，虽然具有PPT的交互功能，但不是真正的PowerPoint文件(.pptx)。为了满足需要真正PPT格式的场景，我们提供了转换解决方案。

### 转换方案

#### 方案1：HTML内容解析转换 (推荐)
- **优点**: 纯Python实现，无需浏览器环境
- **缺点**: 无法完美还原复杂样式和图表
- **适用**: 文本内容为主的PPT

```bash
# 安装依赖
python install_ppt_dependencies.py

# 运行转换
python html_to_ppt_converter.py
```

#### 方案2：浏览器截图转换
- **优点**: 完美还原HTML样式和图表
- **缺点**: 需要Chrome浏览器，生成的是图片PPT
- **适用**: 包含复杂图表的PPT

#### 方案3：混合方案
- **优点**: 结合两种方案优势
- **缺点**: 需要更多依赖
- **适用**: 追求最佳效果

### 直接生成PPTX

```python
from html_to_ppt_converter import EnhancedPPTGenerator

generator = EnhancedPPTGenerator()
pptx_file = await generator.generate_pptx_directly(
    task="生成技术报告PPT",
    file_names=["data.md"],
    method="parse"  # 或 "screenshot", "hybrid"
)
```

### 转换工具使用

```bash
# 1. 安装转换依赖
python install_ppt_dependencies.py

# 2. 运行转换器
python html_to_ppt_converter.py

# 3. 选择转换方案
# 方案1: 内容解析 (快速)
# 方案2: 浏览器截图 (高质量)
# 方案3: 混合方案 (最佳效果)
```

### 文件结构

```
test/
├── ppt_generator_demo.py           # HTML格式PPT生成器
├── html_to_ppt_converter.py        # HTML转PPTX转换器
├── install_ppt_dependencies.py     # 依赖安装脚本
├── output/                         # 输出目录
│   ├── *.html                     # HTML格式PPT
│   └── *.pptx                     # 转换后的PowerPoint文件
```

## 许可证

本演示程序基于 `joyagent-jdgenie` 项目，遵循原项目的许可证条款。

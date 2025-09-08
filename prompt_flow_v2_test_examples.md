# PromptFlow Agent v2.0 测试示例

## 新架构特点

✅ **AI 驱动任务规划** - 用户只需描述目标，AI 自动生成执行计划
✅ **自然语言交互** - 无需学习复杂语法，直接用中文描述需求
✅ **智能工具选择** - AI 了解所有可用工具能力，自动选择最优组合
✅ **动态适应执行** - 执行过程中遇到问题会自动调整和重新规划
✅ **完整错误恢复** - 多种恢复策略确保任务尽可能完成

## API 调用示例

### 1. 数据分析任务 (推荐方式)

```json
{
  "query": "分析销售数据文件，生成包含趋势分析、产品排行和改进建议的专业报告",
  "agentType": 6,
  "params": {
    "variables": {
      "author": "数据分析师"
    }
  }
}
```

### 2. 客服对话处理

```json
{
  "query": "客户说产品有质量问题要退款，帮我专业地处理这个客服场景",
  "agentType": 6,
  "params": {
    "variables": {
      "company": "JoyAgent"
    }
  }
}
```

### 3. 内容创作任务

```json
{
  "query": "为我们的新AI产品写一份完整的营销方案，包括目标受众分析、竞争对手分析和推广策略",
  "agentType": 6,
  "params": {}
}
```

### 4. 使用 PromptFlowTool (工具调用方式)

```json
{
  "tool_name": "prompt_flow",
  "parameters": {
    "command": "execute_goal",
    "goal": "帮我研究竞争对手的产品功能，并制定我们的差异化策略",
    "variables": {
      "industry": "AI工具",
      "our_product": "智能代理平台"
    }
  }
}
```

### 5. 兼容模式 (传统 Markdown 语法)

```json
{
  "tool_name": "prompt_flow", 
  "parameters": {
    "command": "execute_markdown",
    "markdown_content": "# 简单任务流程\n\n1. **数据处理** [tool:file_tool]\n   - 操作: read\n   - 文件: {{user_file}}\n\n2. **分析** [tool:llm_call]\n   - prompt: 分析数据: {{step_1.result}}\n\n3. **报告** [tool:llm_call]\n   - prompt: 生成报告: {{step_2.result}}",
    "variables": {
      "user_file": "data.csv" 
    }
  }
}
```

## 执行流程对比

### v1.0 (旧版本)
```
用户写Markdown → 解析语法 → 按步骤执行 → 返回结果
```

### v2.0 (新版本)  
```
用户描述目标 → AI分析目标 → 生成执行计划 → 智能执行 → 自动恢复 → 返回结果
```

## 模板和示例

### 创建目标示例模板

```json
{
  "tool_name": "prompt_flow",
  "parameters": {
    "command": "create_template",
    "template_name": "goal_examples_guide",
    "template_type": "goal_examples"
  }
}
```

### 列出可用模板

```json
{
  "tool_name": "prompt_flow", 
  "parameters": {
    "command": "list_templates"
  }
}
```

## 流式输出事件

新架构支持以下 SSE 事件：

- `prompt_flow_plan_generated` - 计划生成完成
- `prompt_flow_step` - 步骤执行状态  
- `prompt_flow_execution_complete` - 执行完成
- `prompt_flow_error` - 执行错误

## 核心优势总结

1. **零学习成本** - 会说中文就能使用
2. **智能化程度高** - AI 参与整个任务规划过程
3. **容错能力强** - 自动处理执行失败和重新规划
4. **扩展性好** - 自动发现和利用新增工具
5. **向后兼容** - 仍支持传统 Markdown 语法

PromptFlow v2.0 真正实现了"人人都能创建 AI 工作流"的愿景！
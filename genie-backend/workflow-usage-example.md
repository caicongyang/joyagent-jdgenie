# WORKFLOW Agent 使用示例

本文档展示了如何使用新实现的 WORKFLOW Agent 来执行复杂的多步骤任务。

## 1. 基本使用

### 1.1 通过API请求使用WORKFLOW Agent（自动匹配模板）

```json
{
  "requestId": "req-12345",
  "erp": "user123",
  "query": "请帮我分析最近的销售数据趋势",
  "agentType": 2,
  "isStream": true,
  "executionMode": "batch"
}
```

### 1.2 使用配置文件中的工作流模板

```json
{
  "requestId": "req-12345",
  "erp": "user123", 
  "query": "分析用户行为数据",
  "agentType": 2,
  "isStream": true,
  "workflowTemplate": "data-analysis"
}
```

### 1.3 使用模板并自定义变量

```json
{
  "requestId": "req-12345",
  "erp": "user123", 
  "query": "分析产品销售数据",
  "agentType": 2,
  "isStream": true,
  "workflowTemplate": "data-analysis",
  "workflowVariables": "{\"analysis_scope\":\"深度分析\",\"report_format\":\"图表报告\"}"
}
```

### 1.4 使用内联JSON定义工作流

```json
{
  "requestId": "req-12345",
  "erp": "user123", 
  "query": "分析用户行为数据",
  "agentType": 2,
  "isStream": true,
  "workflowDefinition": "{\"id\":\"custom-analysis\",\"name\":\"自定义分析工作流\",\"steps\":[{\"id\":\"step1\",\"name\":\"数据收集\",\"type\":\"llm_call\",\"prompt\":\"收集用户行为数据\"},{\"id\":\"step2\",\"name\":\"数据分析\",\"type\":\"llm_call\",\"prompt\":\"分析收集到的数据\"}]}"
}
```

## 2. 工作流模板使用

### 2.1 数据分析模板
适用于需要对数据进行收集、处理、分析和报告的场景。

**触发示例：**
- "请分析我们的销售数据"
- "帮我做一个用户行为分析"
- "分析产品性能数据"

**执行步骤：**
1. 数据收集 - 确定数据来源和类型
2. 数据处理 - 清洗和预处理数据
3. 数据分析 - 发现趋势和模式
4. 结果报告 - 生成完整分析报告

### 2.2 研究模板
适用于需要深入研究某个主题的场景。

**触发示例：**
- "研究人工智能在金融行业的应用"
- "调研竞争对手的产品策略"
- "查找最新的技术趋势"

**执行步骤：**
1. 制定研究计划 - 确定研究范围和方法
2. 信息搜索 - 使用深度搜索工具
3. 信息分析 - 分析搜索结果
4. 研究报告 - 生成研究报告

### 2.3 问题解决模板
适用于需要系统性解决复杂问题的场景。

**触发示例：**
- "如何提高客户满意度"
- "解决产品质量问题"
- "优化业务流程"

**执行步骤：**
1. 问题分析 - 深入分析问题本质
2. 方案设计 - 设计解决方案
3. 方案评估 - 评估可行性和风险
4. 实施建议 - 提供具体实施计划

## 3. 自定义工作流

### 3.1 完整的工作流定义示例

```json
{
  "id": "custom-workflow-001",
  "name": "产品分析工作流",
  "description": "分析产品市场表现和用户反馈",
  "maxSteps": 10,
  "timeoutSeconds": 3600,
  "variables": {
    "product_name": "智能助手",
    "analysis_period": "最近3个月"
  },
  "steps": [
    {
      "id": "market-research",
      "name": "市场调研",
      "type": "llm_call",
      "prompt": "分析${product_name}在${analysis_period}的市场表现",
      "systemPrompt": "你是一个市场分析专家"
    },
    {
      "id": "user-feedback-analysis", 
      "name": "用户反馈分析",
      "type": "tool_call",
      "toolName": "deep_search",
      "toolArgs": {
        "query": "${product_name} 用户评价 反馈",
        "time_range": "${analysis_period}"
      }
    },
    {
      "id": "competitive-analysis",
      "name": "竞品分析", 
      "type": "llm_call",
      "prompt": "基于市场研究${step.market-research}和用户反馈${step.user-feedback-analysis}，分析竞争态势",
      "dependencies": ["market-research", "user-feedback-analysis"]
    },
    {
      "id": "recommendations",
      "name": "改进建议",
      "type": "llm_call",
      "prompt": "基于分析结果，提出产品改进建议",
      "dependencies": ["competitive-analysis"]
    }
  ]
}
```

### 3.2 并行执行步骤示例

```json
{
  "id": "parallel-analysis",
  "name": "并行分析工作流",
  "steps": [
    {
      "id": "parallel-tasks",
      "name": "并行任务执行",
      "type": "parallel",
      "maxConcurrency": 3,
      "waitForAll": true,
      "subSteps": [
        {
          "id": "task1",
          "type": "llm_call", 
          "prompt": "分析技术指标"
        },
        {
          "id": "task2",
          "type": "tool_call",
          "toolName": "deep_search",
          "toolArgs": {"query": "行业数据"}
        },
        {
          "id": "task3",
          "type": "llm_call",
          "prompt": "评估用户体验"
        }
      ]
    }
  ]
}
```

### 3.3 如何定义一个工具流（Tool Flow）

工具流是以一个或多个 `tool_call` 步骤为核心，通过依赖与变量把工具输入输出串联起来的工作流。底层由 `ToolCallStep` 执行，字段对接如下：
- `type`: 固定为 `"tool_call"`，实际映射为 `StepType.TOOL_CALL`
- `toolName`: 工具名称（必填），将传给 `ToolCollection.execute(toolName, toolArgs)`
- `toolArgs`: 工具入参（对象/字典），支持变量插值
- 可选通用控制：`dependencies`、`condition`、`retryPolicy`、`timeoutSeconds`、`skippable`

#### 最小可运行示例
```json
{
  "id": "tool-flow-min",
  "name": "最小工具流",
  "steps": [
    {
      "id": "call-deepsearch",
      "type": "tool_call",
      "toolName": "deep_search",
      "toolArgs": {"query": "行业趋势"}
    }
  ]
}
```

#### 进阶示例：串联多个工具并复用上一步结果
```json
{
  "id": "tool-flow-advanced",
  "name": "工具流-串联",
  "variables": {"topic": "AIGC"},
  "steps": [
    {
      "id": "search",
      "name": "搜索",
      "type": "tool_call",
      "toolName": "deep_search",
      "toolArgs": {"query": "${topic} 行业报告 2024"}
    },
    {
      "id": "summarize",
      "name": "摘要整理",
      "type": "tool_call",
      "dependencies": ["search"],
      "toolName": "report",
      "toolArgs": {"content": "${step.search}"}
    }
  ]
}
```

#### 字段说明与建议
- `toolName`（必填）: 对应后端已注册到 `ToolCollection` 的工具名，否则会执行失败
- `toolArgs`: 按工具定义传入结构即可；推荐与 `variables` 配合使用，便于复用
- `dependencies`: 保证顺序/数据依赖，引用上一步输出用 `${step.<id>}`
- `retryPolicy`: 例如
  ```json
  {
    "maxRetries": 2,
    "retryDelayMs": 1000,
    "retryType": "FIXED_DELAY"
  }
  ```
- `timeoutSeconds`: 超时上限（秒）；默认 300（见 `WorkflowStep`）
- `skippable`: 可跳过非关键步骤以提升鲁棒性

#### 并行工具流
- 用 `type: "parallel"` + `subSteps` 组织多个 `tool_call` 并行执行
- 可设置 `maxConcurrency` 限制并发数，`waitForAll` 控制是否等待全部完成再继续

#### 常见错误
- 漏填 `toolName` → 将触发校验错误或运行错误
- `toolArgs` 结构与工具不匹配 → 工具抛出 `IllegalArgumentException/IllegalStateException` 时不会重试
- 引用 `${step.xxx}` 时 `dependencies` 未声明 → 可能在运行期找不到前置结果

## 4. 单步执行模式

适用于需要逐步控制执行进度的场景。

```json
{
  "requestId": "req-12345",
  "erp": "user123",
  "query": "逐步分析销售数据",
  "agentType": 2,
  "executionMode": "step",
  "isStream": true
}
```

在单步执行模式下，系统会逐个执行工作流步骤，用户可以看到每一步的详细执行过程。

## 5. 工作流工具使用

### 5.1 创建工作流

```json
{
  "command": "create",
  "template_name": "data_analysis"
}
```

### 5.2 查看工作流状态

```json
{
  "command": "status",
  "workflow_id": "workflow-12345"
}
```

### 5.3 控制工作流执行

```json
// 暂停
{
  "command": "pause",
  "workflow_id": "workflow-12345"
}

// 恢复
{
  "command": "resume", 
  "workflow_id": "workflow-12345"
}

// 取消
{
  "command": "cancel",
  "workflow_id": "workflow-12345"
}
```

### 5.4 列出可用模板

```json
{
  "command": "list_templates"
}
```

## 6. 响应格式

### 6.1 工作流开始响应

```json
{
  "status": "success",
  "finished": false,
  "responseType": "text",
  "packageType": "workflow_start",
  "response": "🚀 工作流已开始执行\n\n📋 工作流名称: 数据分析工作流\n📝 描述: 完整的数据分析流程\n🔢 总步骤数: 4\n⏱️ 预估时间: 约2-3分钟\n\n正在执行中，请稍候...",
  "resultMap": {
    "name": "数据分析工作流",
    "description": "完整的数据分析流程", 
    "totalSteps": 4,
    "estimatedTime": "约2-3分钟"
  }
}
```

### 6.2 步骤完成响应

```json
{
  "status": "success",
  "finished": false,
  "responseType": "text", 
  "packageType": "workflow_step",
  "response": "✅ 步骤完成: 数据收集\n\n📋 执行结果:\n已确定需要收集的数据类型包括销售数据、用户行为数据和市场趋势数据...\n\n---"
}
```

### 6.3 工作流完成响应

```json
{
  "status": "success",
  "finished": true,
  "responseType": "text",
  "packageType": "workflow_complete", 
  "response": "🎉 工作流执行成功!\n\n📊 执行统计:\n• 总步骤数: 4\n• 执行时间: 125.30 秒\n\n📋 最终结果:\n数据分析已完成，发现以下关键洞察...",
  "resultMap": {
    "success": true,
    "totalSteps": 4,
    "executionTime": 125300
  }
}
```

## 7. 最佳实践

### 7.1 工作流设计原则
1. **模块化设计** - 将复杂任务分解为独立的步骤
2. **合理依赖** - 明确步骤间的依赖关系
3. **错误处理** - 为关键步骤设置重试策略
4. **参数化** - 使用变量使工作流更灵活

### 7.2 性能优化
1. **并行执行** - 对独立任务使用并行步骤
2. **超时控制** - 合理设置步骤和工作流超时时间
3. **资源管理** - 控制并发数避免资源竞争

### 7.3 监控和调试
1. **日志记录** - 每个步骤都有详细的执行日志
2. **进度跟踪** - 实时监控工作流执行进度
3. **错误诊断** - 提供详细的错误信息和建议

## 8. 常见问题

### 8.1 如何处理步骤失败？
系统会根据步骤的 `skippable` 属性决定是否继续执行。关键步骤失败会终止工作流，可跳过的步骤失败会继续执行。

### 8.2 如何传递步骤间的数据？
使用变量语法 `${step.stepId}` 来引用前面步骤的结果。

### 8.3 如何实现条件执行？
使用步骤条件 `StepCondition` 来控制步骤是否执行。

### 8.4 工作流执行时间过长怎么办？
可以设置合理的超时时间，或者使用单步执行模式进行调试。

## 9. 扩展开发

如需添加新的步骤类型或工作流功能，可以：

1. **扩展步骤类型** - 继承 `WorkflowStep` 基类
2. **添加新工具** - 在 `WorkflowTool` 中添加新命令
3. **自定义响应处理** - 在 `WorkflowAgentResponseHandler` 中处理新的响应类型
4. **创建新模板** - 在系统中注册新的工作流模板

通过这种模块化的设计，WORKFLOW Agent 具有良好的扩展性，可以适应各种复杂的业务场景。

## 10. 配置文件定义工作流

### 10.1 配置文件位置

工作流模板定义在 `src/main/resources/workflows.yml` 文件中。

### 10.2 配置文件结构

```yaml
# 工作流配置文件
workflows:
  # 模板名称
  template-name:
    id: "template-id"
    name: "模板显示名称"
    description: "模板描述"
    maxSteps: 10
    timeoutSeconds: 3600
    variables:
      var1: "默认值1"
      var2: "默认值2"
    steps:
      - id: "step-id"
        name: "步骤名称"
        type: "步骤类型"
        # 其他步骤配置...

# 全局设置
settings:
  defaultConfig:
    maxSteps: 50
    timeoutSeconds: 1800
  enableCache: true
  defaultExecutionMode: "batch"
```

### 10.3 预定义模板

系统提供以下预定义模板：

#### 10.3.1 数据分析模板 (`data-analysis`)
- **用途**: 完整的数据分析流程
- **步骤**: 数据收集 → 数据搜索 → 数据处理 → 数据分析 → 结果报告
- **变量**: `analysis_scope`、`report_format`

#### 10.3.2 研究模板 (`research`)
- **用途**: 深度研究流程
- **步骤**: 研究计划 → 信息搜索 → 信息分析 → 研究报告
- **变量**: `research_depth`、`search_scope`

#### 10.3.3 并行分析模板 (`parallel-analysis`)
- **用途**: 多维度并行分析
- **步骤**: 并行任务（技术分析+市场搜索+用户体验） → 综合分析

#### 10.3.4 问题解决模板 (`problem-solving`)
- **用途**: 系统性问题解决
- **步骤**: 问题分析 → 背景研究 → 方案设计 → 方案评估 → 实施建议
- **变量**: `solution_type`、`evaluation_criteria`

#### 10.3.5 简单工具流模板 (`simple-tool-flow`)
- **用途**: 演示工具调用流程
- **步骤**: 搜索信息 → 分析结果 → 生成报告
- **变量**: `search_query`

### 10.4 使用配置文件模板

#### 10.4.1 直接指定模板名称

```json
{
  "requestId": "req-12345",
  "erp": "user123",
  "query": "分析市场趋势",
  "agentType": 2,
  "workflowTemplate": "data-analysis"
}
```

#### 10.4.2 使用模板并覆盖变量

```json
{
  "requestId": "req-12345",
  "erp": "user123",
  "query": "研究AI发展趋势",
  "agentType": 2,
  "workflowTemplate": "research",
  "workflowVariables": "{\"research_depth\":\"深度研究\",\"search_scope\":\"全球范围\"}"
}
```

### 10.5 自定义配置文件模板

#### 10.5.1 添加新模板

在 `workflows.yml` 中添加新的模板定义：

```yaml
workflows:
  my-custom-template:
    id: "my-custom-template"
    name: "我的自定义模板"
    description: "自定义工作流模板"
    maxSteps: 5
    timeoutSeconds: 1200
    variables:
      custom_var: "默认值"
    steps:
      - id: "custom-step-1"
        name: "自定义步骤1"
        type: "llm_call"
        prompt: "执行自定义任务：${query}"
        systemPrompt: "你是一个专业助手"
      - id: "custom-step-2"
        name: "自定义步骤2"
        type: "tool_call"
        toolName: "deep_search"
        toolArgs:
          query: "${custom_var}"
        dependencies: ["custom-step-1"]
```

#### 10.5.2 步骤类型支持

- `llm_call`: LLM调用步骤
  - `prompt`: 提示词（支持变量插值）
  - `systemPrompt`: 系统提示词
- `tool_call`: 工具调用步骤
  - `toolName`: 工具名称
  - `toolArgs`: 工具参数（支持变量插值）
- `sequential`: 顺序执行步骤
  - `subSteps`: 子步骤列表
- `parallel`: 并行执行步骤
  - `subSteps`: 子步骤列表
  - `maxConcurrency`: 最大并发数
  - `waitForAll`: 是否等待所有子步骤完成

#### 10.5.3 步骤配置选项

每个步骤都支持以下通用配置：

```yaml
- id: "step-id"
  name: "步骤名称"
  description: "步骤描述"
  type: "步骤类型"
  dependencies: ["前置步骤ID"]
  condition: "执行条件表达式"
  timeoutSeconds: 300
  skippable: false
  retryPolicy:
    maxRetries: 2
    retryDelayMs: 1000
    retryType: "FIXED_DELAY"
    backoffMultiplier: 2.0
    maxRetryDelayMs: 10000
```

### 10.6 工作流管理工具

可以使用 `workflow` 工具来管理配置文件中的模板：

#### 10.6.1 列出所有模板

```json
{
  "command": "list_templates"
}
```

#### 10.6.2 获取模板信息

```json
{
  "command": "template_info",
  "template_name": "data-analysis"
}
```

#### 10.6.3 获取完整模板定义

```json
{
  "command": "get_template",
  "template_name": "research"
}
```

#### 10.6.4 重新加载配置

```json
{
  "command": "reload"
}
```

### 10.7 工作流选择优先级

系统按以下优先级选择工作流：

1. **指定模板名称** (`workflowTemplate`)
2. **内联JSON定义** (`workflowDefinition`)
3. **智能模板匹配** (根据查询内容自动匹配)
4. **默认智能生成** (动态创建工作流)

### 10.8 变量插值

配置文件中支持以下变量插值：

- `${query}`: 用户查询内容
- `${requestId}`: 请求ID
- `${erp}`: 用户ERP
- `${variable_name}`: 自定义变量
- `${step.step_id}`: 引用前置步骤结果

### 10.9 配置文件优势

使用配置文件定义工作流的优势：

1. **可维护性**: 集中管理，易于修改和版本控制
2. **可重用性**: 一次定义，多次使用
3. **标准化**: 统一的工作流规范和最佳实践
4. **灵活性**: 支持变量插值和动态配置
5. **性能**: 预编译模板，执行效率高
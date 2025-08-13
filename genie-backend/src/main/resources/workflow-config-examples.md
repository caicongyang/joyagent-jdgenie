# 工作流配置文件使用示例

## 1. 基本使用示例

### 1.1 使用数据分析模板

**请求示例：**
```json
{
  "requestId": "req-001",
  "erp": "user123",
  "query": "分析最近一个月的销售数据",
  "agentType": 2,
  "isStream": true,
  "workflowTemplate": "data-analysis"
}
```

**执行流程：**
1. 系统从 `workflows.yml` 加载 `data-analysis` 模板
2. 自动注入变量：`query = "分析最近一个月的销售数据"`
3. 按模板定义的步骤执行：数据收集 → 数据搜索 → 数据处理 → 数据分析 → 结果报告

### 1.2 使用模板并自定义变量

**请求示例：**
```json
{
  "requestId": "req-002",
  "erp": "user123",
  "query": "研究人工智能在金融领域的应用",
  "agentType": 2,
  "isStream": true,
  "workflowTemplate": "research",
  "workflowVariables": "{\"research_depth\":\"深度研究\",\"search_scope\":\"全球范围\"}"
}
```

**变量应用：**
- 模板默认变量被覆盖
- `research_depth`: "深度研究"
- `search_scope`: "全球范围"
- 系统自动变量：`query`, `requestId`, `erp`

### 1.3 智能模板匹配

**请求示例：**
```json
{
  "requestId": "req-003",
  "erp": "user123",
  "query": "如何解决客户满意度下降的问题",
  "agentType": 2,
  "isStream": true
}
```

**匹配逻辑：**
- 系统检测到查询包含"解决"、"问题"等关键词
- 自动匹配到 `problem-solving` 模板
- 执行问题解决工作流

## 2. 自定义模板示例

### 2.1 创建简单的工具流模板

在 `workflows.yml` 中添加：

```yaml
workflows:
  my-simple-flow:
    id: "my-simple-flow"
    name: "我的简单工具流"
    description: "演示简单工具调用流程"
    maxSteps: 3
    timeoutSeconds: 600
    variables:
      search_topic: "${query}"
    steps:
      - id: "search"
        name: "搜索信息"
        type: "tool_call"
        toolName: "deep_search"
        toolArgs:
          query: "${search_topic}"
        retryPolicy:
          maxRetries: 2
          retryDelayMs: 1000
          
      - id: "analyze"
        name: "分析结果"
        type: "llm_call"
        prompt: "请分析以下搜索结果：${step.search}"
        systemPrompt: "你是一个专业的信息分析师"
        dependencies: ["search"]
```

**使用示例：**
```json
{
  "requestId": "req-004",
  "erp": "user123",
  "query": "区块链技术发展趋势",
  "agentType": 2,
  "workflowTemplate": "my-simple-flow"
}
```

### 2.2 创建并行处理模板

```yaml
workflows:
  parallel-research:
    id: "parallel-research"
    name: "并行研究工作流"
    description: "同时进行多个维度的研究"
    maxSteps: 4
    timeoutSeconds: 1800
    steps:
      - id: "parallel-search"
        name: "并行搜索任务"
        type: "parallel"
        maxConcurrency: 3
        waitForAll: true
        subSteps:
          - id: "tech-search"
            name: "技术搜索"
            type: "tool_call"
            toolName: "deep_search"
            toolArgs:
              query: "${query} 技术 technology"
              
          - id: "market-search"
            name: "市场搜索"
            type: "tool_call"
            toolName: "deep_search"
            toolArgs:
              query: "${query} 市场 market"
              
          - id: "trend-search"
            name: "趋势搜索"
            type: "tool_call"
            toolName: "deep_search"
            toolArgs:
              query: "${query} 趋势 trend"
              
      - id: "synthesis"
        name: "综合分析"
        type: "llm_call"
        prompt: "综合分析技术、市场和趋势三个维度的搜索结果：技术维度${step.tech-search}，市场维度${step.market-search}，趋势维度${step.trend-search}"
        systemPrompt: "你是一个综合分析专家"
        dependencies: ["parallel-search"]
```

## 3. 工作流管理工具使用

### 3.1 列出所有模板

```json
{
  "requestId": "req-005",
  "erp": "user123",
  "query": "列出所有工作流模板",
  "agentType": 2,
  "workflowDefinition": "{\"id\":\"tool-demo\",\"steps\":[{\"id\":\"list\",\"type\":\"tool_call\",\"toolName\":\"workflow\",\"toolArgs\":{\"command\":\"list_templates\"}}]}"
}
```

### 3.2 查看模板详情

```json
{
  "requestId": "req-006",
  "erp": "user123",
  "query": "查看数据分析模板详情",
  "agentType": 2,
  "workflowDefinition": "{\"id\":\"tool-demo\",\"steps\":[{\"id\":\"info\",\"type\":\"tool_call\",\"toolName\":\"workflow\",\"toolArgs\":{\"command\":\"template_info\",\"template_name\":\"data-analysis\"}}]}"
}
```

## 4. 高级配置示例

### 4.1 带条件执行的工作流

```yaml
workflows:
  conditional-flow:
    id: "conditional-flow"
    name: "条件执行工作流"
    description: "根据条件执行不同步骤"
    steps:
      - id: "initial-analysis"
        name: "初始分析"
        type: "llm_call"
        prompt: "对以下内容进行初始分析：${query}"
        
      - id: "detailed-search"
        name: "详细搜索"
        type: "tool_call"
        toolName: "deep_search"
        toolArgs:
          query: "${query} 详细信息"
        condition: "length(step.initial-analysis) < 100"
        dependencies: ["initial-analysis"]
        
      - id: "final-report"
        name: "最终报告"
        type: "llm_call"
        prompt: "生成最终报告，基于初始分析：${step.initial-analysis}${step.detailed-search ? '和详细搜索：' + step.detailed-search : ''}"
        dependencies: ["initial-analysis"]
```

### 4.2 带重试策略的工作流

```yaml
workflows:
  robust-flow:
    id: "robust-flow"
    name: "健壮工作流"
    description: "带有完善错误处理的工作流"
    steps:
      - id: "critical-search"
        name: "关键搜索"
        type: "tool_call"
        toolName: "deep_search"
        toolArgs:
          query: "${query}"
        retryPolicy:
          maxRetries: 3
          retryDelayMs: 2000
          retryType: "EXPONENTIAL"
          backoffMultiplier: 2.0
          maxRetryDelayMs: 10000
        timeoutSeconds: 300
        
      - id: "backup-analysis"
        name: "备用分析"
        type: "llm_call"
        prompt: "基于有限信息进行分析：${query}"
        skippable: true
        dependencies: ["critical-search"]
```

## 5. 最佳实践

### 5.1 模板命名规范
- 使用小写字母和连字符
- 名称要描述性强：`data-analysis`、`problem-solving`
- 避免特殊字符和空格

### 5.2 变量设计原则
- 使用描述性变量名
- 提供合理的默认值
- 支持用户覆盖关键变量

### 5.3 步骤设计建议
- 步骤ID要唯一且有意义
- 合理设置依赖关系
- 为关键步骤配置重试策略
- 非关键步骤可设为可跳过

### 5.4 性能优化
- 合理使用并行步骤
- 设置适当的超时时间
- 避免不必要的工具调用

### 5.5 错误处理
- 为网络相关操作配置重试
- 使用指数退避策略
- 设置合理的最大重试次数

## 6. 故障排除

### 6.1 常见问题

**问题1：模板未找到**
- 检查模板名称是否正确
- 确认 `workflows.yml` 文件格式正确
- 使用 `workflow` 工具的 `list_templates` 命令检查

**问题2：变量插值失败**
- 检查变量名拼写
- 确认变量已定义
- 使用正确的插值语法：`${variable_name}`

**问题3：步骤依赖错误**
- 检查依赖的步骤ID是否存在
- 确认依赖关系没有循环
- 验证依赖步骤在当前步骤之前定义

### 6.2 调试技巧

1. **使用单步执行模式**
   ```json
   {
     "executionMode": "step"
   }
   ```

2. **查看详细日志**
   - 检查应用日志中的工作流执行信息
   - 关注步骤执行状态和错误信息

3. **使用工作流管理工具**
   - 使用 `template_info` 命令检查模板配置
   - 使用 `reload` 命令重新加载配置

通过以上示例和最佳实践，你可以充分利用配置文件定义工作流的功能，创建高效、可维护的工作流模板。
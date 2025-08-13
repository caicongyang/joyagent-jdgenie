# 前端 WORKFLOW 智能体集成说明

## 概述

本文档说明了为支持新的 WORKFLOW 智能体类型而对前端代码进行的修改。这些修改确保用户可以通过前端界面选择和使用 WORKFLOW 智能体，并正确显示工作流执行状态。

## 主要修改文件

### 1. 常量和配置文件 (`src/utils/constants.ts`)

#### 新增内容：
- **智能体类型枚举** (`AgentTypeEnum`)
- **智能体模式配置** (`agentModeList`) 
- **工作流模板配置** (`workflowTemplates`)

```typescript
// 智能体类型枚举
export enum AgentTypeEnum {
  COMPREHENSIVE = 1,
  WORKFLOW = 2,
  PLAN_SOLVE = 3,
  ROUTER = 4,
  REACT = 5
}

// 智能体模式配置 - 包含三种模式
export const agentModeList = [
  {
    name: '快速响应',
    key: 'react',
    agentType: AgentTypeEnum.REACT,
    description: '快速响应模式，适合简单问答和基础任务',
    icon: 'icon-kuaisuchuli',
    color: 'text-[#29CC29]'
  },
  {
    name: '深度研究',
    key: 'plan_solve', 
    agentType: AgentTypeEnum.PLAN_SOLVE,
    description: '深度研究模式，适合复杂分析和研究任务',
    icon: 'icon-shendusikao',
    color: 'text-[#4040FF]'
  },
  {
    name: '工作流',
    key: 'workflow',
    agentType: AgentTypeEnum.WORKFLOW,
    description: '工作流模式，适合多步骤复杂业务流程处理',
    icon: 'icon-gongzuoliu',
    color: 'text-[#FF860D]'
  }
];

// 工作流模板配置
export const workflowTemplates = [
  {
    id: 'data_analysis',
    name: '数据分析工作流',
    description: '包含数据收集、处理、分析和报告生成的完整流程',
    steps: ['数据收集', '数据处理', '数据分析', '结果报告'],
    icon: 'icon-shujufenxi',
    color: 'text-[#4040FF]'
  },
  // ... 其他模板
];
```

### 2. 类型定义更新 (`src/types/chat.ts`)

#### 扩展 `TInputInfo` 类型：
```typescript
export type TInputInfo = {
  files?: TFile[];
  message: string;
  outputStyle?: string;
  deepThink: boolean;
  agentMode?: string;           // 新增：智能体模式
  workflowTemplate?: string;    // 新增：工作流模板
  executionMode?: string;       // 新增：执行模式
}
```

### 3. 智能体模式选择器组件 (`src/components/AgentModeSelector/index.tsx`)

#### 新建组件特性：
- 支持三种智能体模式选择：快速响应、深度研究、工作流
- 工作流模式下可选择预定义模板
- 响应式设计，支持不同尺寸
- 使用 Popover 实现友好的交互体验

#### 核心功能：
```typescript
interface AgentModeSelectorProps {
  value?: string;
  onChange?: (mode: string, template?: string) => void;
  size?: 'small' | 'medium' | 'large';
}
```

### 4. 输入组件更新 (`src/components/GeneralInput/index.tsx`)

#### 主要修改：
- 集成 `AgentModeSelector` 组件
- 添加智能体模式和工作流模板状态管理
- 更新发送消息逻辑，包含新参数
- 保持向后兼容性

#### 关键代码：
```typescript
const [agentMode, setAgentMode] = useState<string>('react');
const [workflowTemplate, setWorkflowTemplate] = useState<string>('');

const handleAgentModeChange = (mode: string, template?: string) => {
  setAgentMode(mode);
  setWorkflowTemplate(template || '');
  // 同步更新deepThink状态以保持向后兼容
  if (mode === 'plan_solve') {
    setDeepThink(true);
  } else {
    setDeepThink(false);
  }
};

// 发送消息时包含新参数
send({
  message: question,
  outputStyle: product?.type,
  deepThink,
  agentMode,
  workflowTemplate,
  executionMode: 'batch'
});
```

### 5. 聊天视图更新 (`src/components/ChatView/index.tsx`)

#### 主要修改：
- 解构新的输入参数
- 根据 `agentMode` 确定后端智能体类型
- 向后端传递完整的请求参数

#### 智能体类型映射：
```typescript
// 根据agentMode确定智能体类型
let agentType = 5; // 默认REACT
if (agentMode === 'plan_solve') {
  agentType = 3; // PLAN_SOLVE
} else if (agentMode === 'workflow') {
  agentType = 2; // WORKFLOW
}

const params = {
  sessionId,
  requestId,
  query: message,
  deepThink: deepThink ? 1 : 0,
  outputStyle,
  agentType,
  workflowTemplate,
  executionMode: executionMode || 'batch'
};
```

### 6. 消息处理逻辑更新 (`src/utils/chat.ts`)

#### 新增工作流消息类型处理：
```typescript
// 在combineData函数中新增工作流消息类型处理
case "workflow_start":
case "workflow_progress": 
case "workflow_step":
case "workflow_complete":
case "workflow_error": {
  handleWorkflowMessage(eventData, currentChat);
  break;
}

// 新增工作流消息处理函数
function handleWorkflowMessage(
  eventData: MESSAGE.EventData,
  currentChat: CHAT.ChatItem
) {
  // 工作流消息处理逻辑
}
```

#### 更新动作构建逻辑：
- 新增工作流相关的消息类型常量
- 为每种工作流状态定义对应的动作信息
- 更新图标映射表

### 7. 工作流状态显示组件 (`src/components/WorkflowStatus/index.tsx`)

#### 新建组件特性：
- 支持所有工作流状态的可视化显示
- 不同状态使用不同颜色和图标
- 显示进度条、步骤信息、执行时间等详细信息
- 响应式布局设计

#### 支持的状态类型：
- `workflow_start` - 工作流启动
- `workflow_progress` - 进度更新
- `workflow_step` - 步骤完成
- `workflow_complete` - 执行完成
- `workflow_error` - 执行错误

## 用户界面改进

### 1. 智能体模式选择
- 用户可以在发送消息前选择三种智能体模式
- 工作流模式下可选择预定义的工作流模板
- 界面清晰显示每种模式的适用场景

### 2. 工作流执行状态显示
- 实时显示工作流执行进度
- 步骤完成情况的详细反馈
- 执行时间和结果的可视化展示

### 3. 向后兼容性
- 保持原有的 `deepThink` 参数逻辑
- 现有功能不受影响
- 平滑的用户体验升级

## 技术实现要点

### 1. 状态管理
- 使用 React Hooks 管理智能体模式状态
- 合理的状态同步机制
- 避免不必要的重渲染

### 2. 类型安全
- 完整的 TypeScript 类型定义
- 严格的类型检查
- 良好的代码提示

### 3. 组件设计
- 模块化的组件架构
- 可复用的工作流状态显示组件
- 响应式设计适配不同屏幕

### 4. 用户体验
- 直观的界面设计
- 清晰的状态反馈
- 流畅的交互体验

## 使用方式

### 1. 选择智能体模式
用户在输入框下方可以看到智能体模式选择器，点击可切换：
- **快速响应**：适合简单问答
- **深度研究**：适合复杂分析（原deepThink模式）
- **工作流**：适合多步骤复杂流程

### 2. 选择工作流模板（工作流模式下）
选择工作流模式后，可以进一步选择预定义模板：
- **数据分析工作流**：数据收集→处理→分析→报告
- **研究工作流**：研究计划→信息搜索→分析→报告  
- **问题解决工作流**：问题分析→方案设计→评估→建议

### 3. 查看执行状态
工作流执行过程中，界面会实时显示：
- 执行进度百分比
- 当前执行步骤
- 步骤完成详情
- 最终执行结果

## 后续扩展建议

1. **更多工作流模板**：根据用户需求添加更多预定义模板
2. **自定义工作流**：允许用户创建自定义工作流
3. **执行历史**：保存和查看工作流执行历史
4. **性能监控**：添加工作流执行性能统计
5. **错误处理增强**：更详细的错误信息和处理建议

## 总结

这次前端集成修改实现了对 WORKFLOW 智能体的完整支持，包括：
- 用户友好的智能体模式选择界面
- 工作流模板选择功能  
- 实时的执行状态显示
- 完整的消息处理逻辑
- 良好的向后兼容性

用户现在可以轻松选择适合的智能体模式，享受结构化的工作流体验，同时系统保持了良好的稳定性和扩展性。
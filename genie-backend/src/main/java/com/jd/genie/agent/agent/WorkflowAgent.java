package com.jd.genie.agent.agent;

import com.jd.genie.agent.enums.AgentState;
import com.jd.genie.agent.enums.RoleType;
import com.jd.genie.agent.workflow.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * 工作流智能体
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkflowAgent extends BaseAgent {
    
    /**
     * 工作流定义
     */
    private WorkflowDefinition workflowDefinition;
    
    /**
     * 工作流执行引擎
     */
    private WorkflowEngine workflowEngine;
    
    /**
     * 工作流执行上下文
     */
    private WorkflowContext workflowContext;
    
    /**
     * 是否单步执行模式
     */
    private boolean stepByStepMode = false;
    
    public WorkflowAgent(AgentContext agentContext) {
        super();
        this.context = agentContext;
        this.workflowEngine = new WorkflowEngine();
        this.workflowContext = new WorkflowContext("workflow-" + System.currentTimeMillis(), agentContext);
        
        // 设置默认属性
        setName("WorkflowAgent");
        setDescription("Intelligent agent for workflow execution and management");
        setSystemPrompt("I am a workflow execution agent that can manage and execute complex multi-step processes.");
        
        // 初始化可用工具
        initializeTools();
    }
    
    public WorkflowAgent(AgentContext agentContext, WorkflowDefinition definition) {
        this(agentContext);
        this.workflowDefinition = definition;
        this.workflowContext = new WorkflowContext(definition.getId(), agentContext);
        
        // 更新Agent名称和描述
        if (definition.getName() != null) {
            setName("WorkflowAgent-" + definition.getName());
        }
        if (definition.getDescription() != null) {
            setDescription(definition.getDescription());
        }
    }
    
    @Override
    public String step() {
        if (workflowDefinition == null) {
            setState(AgentState.ERROR);
            return "Error: No workflow definition provided";
        }
        
        try {
            if (stepByStepMode) {
                return executeSingleStep();
            } else {
                return executeCompleteWorkflow();
            }
        } catch (Exception e) {
            log.error("Error in workflow execution: {}", e.getMessage(), e);
            setState(AgentState.ERROR);
            workflowContext.fail("Workflow execution error: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
    
    /**
     * 执行单个步骤
     */
    private String executeSingleStep() {
        setState(AgentState.RUNNING);
        
        StepResult result = workflowEngine.executeNextStep(workflowDefinition, workflowContext);
        
        if (result.isSuccess()) {
            if (workflowDefinition.hasNextStep(workflowContext)) {
                // 还有步骤要执行
                String message = String.format("Step completed successfully. Result: %s", 
                        result.getDataAsString());
                updateMemory(RoleType.ASSISTANT, message, null);
                return message;
            } else {
                // 工作流完成
                setState(AgentState.FINISHED);
                workflowContext.complete();
                String message = "Workflow completed successfully";
                updateMemory(RoleType.ASSISTANT, message, null);
                return message;
            }
        } else {
            // 步骤失败
            setState(AgentState.ERROR);
            workflowContext.fail(result.getErrorMessage());
            String message = String.format("Step failed: %s", result.getErrorMessage());
            updateMemory(RoleType.ASSISTANT, message, null);
            return message;
        }
    }
    
    /**
     * 执行完整工作流
     */
    private String executeCompleteWorkflow() {
        setState(AgentState.RUNNING);
        
        WorkflowResult result = workflowEngine.executeWorkflow(workflowDefinition, workflowContext);
        
        if (result.isSuccess()) {
            setState(AgentState.FINISHED);
            String message = generateSuccessMessage(result);
            updateMemory(RoleType.ASSISTANT, message, null);
            return message;
        } else {
            setState(AgentState.ERROR);
            String message = generateFailureMessage(result);
            updateMemory(RoleType.ASSISTANT, message, null);
            return message;
        }
    }
    
    /**
     * 生成成功消息
     */
    private String generateSuccessMessage(WorkflowResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ Workflow completed successfully!\n\n");
        sb.append(String.format("📊 Execution Summary:\n"));
        sb.append(String.format("• Total time: %.2f seconds\n", result.getExecutionTimeSeconds()));
        sb.append(String.format("• Steps completed: %d\n", result.getCompletedSteps()));
        sb.append(String.format("• Steps skipped: %d\n", result.getSkippedSteps()));
        sb.append(String.format("• Success rate: %.1f%%\n", result.getSuccessRate() * 100));
        
        // 添加输出数据
        if (result.getOutputData() != null) {
            sb.append(String.format("\n📋 Output:\n%s\n", result.getOutputData()));
        }
        
        // 添加关键步骤结果
        if (result.getStepResults() != null && !result.getStepResults().isEmpty()) {
            sb.append("\n🔍 Key Results:\n");
            result.getStepResults().forEach((stepId, stepResult) -> {
                if (stepResult != null) {
                    String resultStr = stepResult.toString();
                    if (resultStr.length() > 100) {
                        resultStr = resultStr.substring(0, 100) + "...";
                    }
                    sb.append(String.format("• %s: %s\n", stepId, resultStr));
                }
            });
        }
        
        return sb.toString();
    }
    
    /**
     * 生成失败消息
     */
    private String generateFailureMessage(WorkflowResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("❌ Workflow execution failed\n\n");
        sb.append(String.format("📊 Execution Summary:\n"));
        sb.append(String.format("• Total time: %.2f seconds\n", result.getExecutionTimeSeconds()));
        sb.append(String.format("• Steps completed: %d\n", result.getCompletedSteps()));
        sb.append(String.format("• Steps failed: %d\n", result.getFailedSteps()));
        sb.append(String.format("• Steps skipped: %d\n", result.getSkippedSteps()));
        
        if (result.getErrorMessage() != null) {
            sb.append(String.format("\n❗ Error: %s\n", result.getErrorMessage()));
        }
        
        // 添加部分成功的结果
        if (result.isPartialSuccess()) {
            sb.append("\n⚠️ Partial results available:\n");
            result.getStepResults().forEach((stepId, stepResult) -> {
                if (stepResult != null) {
                    sb.append(String.format("• %s: %s\n", stepId, stepResult.toString()));
                }
            });
        }
        
        return sb.toString();
    }
    
    /**
     * 设置工作流定义
     */
    public void setWorkflowDefinition(WorkflowDefinition definition) {
        this.workflowDefinition = definition;
        this.workflowContext = new WorkflowContext(definition.getId(), this.context);
        
        // 更新Agent属性
        if (definition.getName() != null) {
            setName("WorkflowAgent-" + definition.getName());
        }
        if (definition.getDescription() != null) {
            setDescription(definition.getDescription());
        }
    }
    
    /**
     * 暂停工作流执行
     */
    public void pauseWorkflow() {
        if (workflowContext != null) {
            workflowContext.setWorkflowStatus(WorkflowStatus.SUSPENDED);
            workflowContext.addEvent("WORKFLOW_PAUSED", "Workflow paused by user request");
        }
    }
    
    /**
     * 恢复工作流执行
     */
    public void resumeWorkflow() {
        if (workflowContext != null && workflowContext.getWorkflowStatus() == WorkflowStatus.SUSPENDED) {
            workflowContext.setWorkflowStatus(WorkflowStatus.RUNNING);
            workflowContext.addEvent("WORKFLOW_RESUMED", "Workflow resumed by user request");
        }
    }
    
    /**
     * 取消工作流执行
     */
    public void cancelWorkflow() {
        if (workflowContext != null) {
            workflowContext.setWorkflowStatus(WorkflowStatus.CANCELLED);
            workflowContext.addEvent("WORKFLOW_CANCELLED", "Workflow cancelled by user request");
            setState(AgentState.FINISHED);
        }
    }
    
    /**
     * 获取工作流执行进度
     */
    public double getProgress() {
        if (workflowDefinition == null || workflowContext == null) {
            return 0.0;
        }
        
        long totalSteps = workflowDefinition.getSteps().size();
        long completedSteps = workflowContext.getStepStatuses().values().stream()
                .filter(status -> status == StepStatus.COMPLETED || status == StepStatus.SKIPPED)
                .count();
        
        return totalSteps > 0 ? (double) completedSteps / totalSteps : 0.0;
    }
    
    /**
     * 获取当前执行状态描述
     */
    public String getStatusDescription() {
        if (workflowContext == null) {
            return "Not initialized";
        }
        
        WorkflowStatus status = workflowContext.getWorkflowStatus();
        String currentStepId = workflowContext.getCurrentStepId();
        double progress = getProgress();
        
        return String.format("%s (%.1f%% complete)%s", 
                status.getDescription(), 
                progress * 100,
                currentStepId != null ? " - Current step: " + currentStepId : "");
    }
    
    /**
     * 初始化工具
     */
    private void initializeTools() {
        // 这里可以添加工作流相关的专用工具
        // 例如：工作流控制工具、状态查询工具等
        log.debug("Initializing workflow agent tools...");
    }
    
    /**
     * 启用单步执行模式
     */
    public WorkflowAgent enableStepByStepMode() {
        this.stepByStepMode = true;
        return this;
    }
    
    /**
     * 禁用单步执行模式
     */
    public WorkflowAgent disableStepByStepMode() {
        this.stepByStepMode = false;
        return this;
    }
}
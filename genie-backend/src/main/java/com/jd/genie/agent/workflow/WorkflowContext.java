package com.jd.genie.agent.workflow;

import com.jd.genie.agent.agent.AgentContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作流执行上下文
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowContext {
    
    /**
     * 工作流ID
     */
    private String workflowId;
    
    /**
     * 工作流实例ID
     */
    private String instanceId;
    
    /**
     * 全局变量
     */
    private Map<String, Object> variables = new ConcurrentHashMap<>();
    
    /**
     * 步骤执行结果
     */
    private Map<String, Object> stepResults = new ConcurrentHashMap<>();
    
    /**
     * 步骤状态映射
     */
    private Map<String, StepStatus> stepStatuses = new ConcurrentHashMap<>();
    
    /**
     * Agent上下文
     */
    private AgentContext agentContext;
    
    /**
     * 工作流状态
     */
    private WorkflowStatus workflowStatus = WorkflowStatus.CREATED;
    
    /**
     * 当前执行步骤ID
     */
    private String currentStepId;
    
    /**
     * 执行开始时间
     */
    private Date startTime;
    
    /**
     * 执行结束时间
     */
    private Date endTime;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 事件日志
     */
    private List<WorkflowEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
    
    public WorkflowContext(String workflowId, AgentContext agentContext) {
        this.workflowId = workflowId;
        this.instanceId = workflowId + "-" + System.currentTimeMillis();
        this.agentContext = agentContext;
        this.startTime = new Date();
    }
    
    /**
     * 获取变量值
     */
    public Object getVariable(String key) {
        return variables.get(key);
    }
    
    /**
     * 设置变量值
     */
    public void setVariable(String key, Object value) {
        variables.put(key, value);
        addEvent("VARIABLE_SET", key + " = " + value);
    }
    
    /**
     * 获取步骤结果
     */
    public Object getStepResult(String stepId) {
        return stepResults.get(stepId);
    }
    
    /**
     * 设置步骤结果
     */
    public void setStepResult(String stepId, Object result) {
        stepResults.put(stepId, result);
    }
    
    /**
     * 获取步骤状态
     */
    public StepStatus getStepStatus(String stepId) {
        return stepStatuses.getOrDefault(stepId, StepStatus.PENDING);
    }
    
    /**
     * 设置步骤状态
     */
    public void setStepStatus(String stepId, StepStatus status) {
        StepStatus oldStatus = stepStatuses.put(stepId, status);
        addEvent("STEP_STATUS_CHANGED", 
                String.format("Step %s: %s -> %s", stepId, oldStatus, status));
    }
    
    /**
     * 添加事件
     */
    public void addEvent(String eventType, Object eventData) {
        WorkflowEvent event = WorkflowEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .workflowId(workflowId)
                .instanceId(instanceId)
                .stepId(currentStepId)
                .eventType(eventType)
                .eventData(eventData)
                .timestamp(new Date())
                .build();
        events.add(event);
    }
    
    /**
     * 完成工作流
     */
    public void complete() {
        this.workflowStatus = WorkflowStatus.COMPLETED;
        this.endTime = new Date();
        addEvent("WORKFLOW_COMPLETED", "Workflow completed successfully");
    }
    
    /**
     * 失败工作流
     */
    public void fail(String errorMessage) {
        this.workflowStatus = WorkflowStatus.FAILED;
        this.errorMessage = errorMessage;
        this.endTime = new Date();
        addEvent("WORKFLOW_FAILED", errorMessage);
    }
    
    /**
     * 获取执行耗时（毫秒）
     */
    public long getExecutionTime() {
        Date end = endTime != null ? endTime : new Date();
        return end.getTime() - startTime.getTime();
    }
}
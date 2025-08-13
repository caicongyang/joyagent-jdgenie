package com.jd.genie.agent.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 工作流执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowResult {
    
    /**
     * 是否执行成功
     */
    private boolean success;
    
    /**
     * 工作流状态
     */
    private WorkflowStatus status;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 异常对象
     */
    private Throwable exception;
    
    /**
     * 执行开始时间
     */
    private Date startTime;
    
    /**
     * 执行结束时间
     */
    private Date endTime;
    
    /**
     * 执行耗时（毫秒）
     */
    private long executionTimeMs;
    
    /**
     * 已完成的步骤数
     */
    private int completedSteps;
    
    /**
     * 失败的步骤数
     */
    private int failedSteps;
    
    /**
     * 跳过的步骤数
     */
    private int skippedSteps;
    
    /**
     * 总步骤数
     */
    private int totalSteps;
    
    /**
     * 步骤执行结果映射
     */
    @Builder.Default
    private Map<String, Object> stepResults = new HashMap<>();
    
    /**
     * 工作流上下文
     */
    private WorkflowContext context;
    
    /**
     * 附加属性
     */
    @Builder.Default
    private Map<String, Object> properties = new HashMap<>();
    
    /**
     * 创建成功结果
     */
    public static WorkflowResult success(WorkflowContext context) {
        return builder()
                .success(true)
                .status(WorkflowStatus.COMPLETED)
                .context(context)
                .endTime(new Date())
                .build()
                .calculateMetrics();
    }
    
    /**
     * 创建失败结果
     */
    public static WorkflowResult failure(WorkflowContext context, String errorMessage) {
        return failure(context, errorMessage, null);
    }
    
    /**
     * 创建失败结果
     */
    public static WorkflowResult failure(WorkflowContext context, String errorMessage, Throwable exception) {
        return builder()
                .success(false)
                .status(WorkflowStatus.FAILED)
                .errorMessage(errorMessage)
                .exception(exception)
                .context(context)
                .endTime(new Date())
                .build()
                .calculateMetrics();
    }
    
    /**
     * 是否部分成功（有些步骤成功，有些失败）
     */
    public boolean isPartialSuccess() {
        return completedSteps > 0 && failedSteps > 0;
    }
    
    /**
     * 获取执行时间（秒）
     */
    public double getExecutionTimeSeconds() {
        return executionTimeMs / 1000.0;
    }
    
    /**
     * 获取成功率
     */
    public double getSuccessRate() {
        if (totalSteps == 0) {
            return 1.0;
        }
        return (double) completedSteps / totalSteps;
    }
    
    /**
     * 获取失败率
     */
    public double getFailureRate() {
        if (totalSteps == 0) {
            return 0.0;
        }
        return (double) failedSteps / totalSteps;
    }
    
    /**
     * 添加属性
     */
    public WorkflowResult withProperty(String key, Object value) {
        properties.put(key, value);
        return this;
    }
    
    /**
     * 获取属性
     */
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key, Class<T> type) {
        Object value = properties.get(key);
        if (value == null) {
            return null;
        }
        return type.cast(value);
    }
    
    /**
     * 计算执行指标
     */
    private WorkflowResult calculateMetrics() {
        if (context != null) {
            // 计算执行时间
            if (context.getStartTime() != null) {
                this.startTime = context.getStartTime();
                if (endTime != null) {
                    this.executionTimeMs = endTime.getTime() - startTime.getTime();
                }
            }
            
            // 统计步骤状态
            Map<String, StepStatus> stepStatuses = context.getStepStatuses();
            this.totalSteps = stepStatuses.size();
            this.completedSteps = 0;
            this.failedSteps = 0;
            this.skippedSteps = 0;
            
            for (StepStatus status : stepStatuses.values()) {
                switch (status) {
                    case COMPLETED:
                        completedSteps++;
                        break;
                    case FAILED:
                        failedSteps++;
                        break;
                    case SKIPPED:
                        skippedSteps++;
                        break;
                    default:
                        break;
                }
            }
            
            // 复制步骤结果
            this.stepResults = new HashMap<>(context.getStepResults());
        }
        
        return this;
    }
    
    /**
     * 获取输出数据
     */
    public Object getOutputData() {
        if (stepResults.isEmpty()) {
            return null;
        }
        
        // 如果只有一个结果，直接返回
        if (stepResults.size() == 1) {
            return stepResults.values().iterator().next();
        }
        
        // 多个结果时返回映射
        return stepResults;
    }
    
    /**
     * 获取执行摘要
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Workflow %s", success ? "SUCCESS" : "FAILED"));
        sb.append(String.format(" - %d/%d steps completed", completedSteps, totalSteps));
        if (failedSteps > 0) {
            sb.append(String.format(", %d failed", failedSteps));
        }
        if (skippedSteps > 0) {
            sb.append(String.format(", %d skipped", skippedSteps));
        }
        sb.append(String.format(" in %.2fs", getExecutionTimeSeconds()));
        
        if (!success && errorMessage != null) {
            sb.append(String.format(" - %s", errorMessage));
        }
        
        return sb.toString();
    }
}
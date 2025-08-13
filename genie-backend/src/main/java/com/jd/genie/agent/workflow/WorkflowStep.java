package com.jd.genie.agent.workflow;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流步骤抽象基类
 */
@Data
@Accessors(chain = true)
public abstract class WorkflowStep {
    
    /**
     * 步骤ID
     */
    private String id;
    
    /**
     * 步骤名称
     */
    private String name;
    
    /**
     * 步骤描述
     */
    private String description;
    
    /**
     * 步骤类型
     */
    private StepType type;
    
    /**
     * 依赖的步骤ID列表
     */
    private List<String> dependencies = new ArrayList<>();
    
    /**
     * 步骤条件
     */
    private StepCondition condition;
    
    /**
     * 重试策略
     */
    private RetryPolicy retryPolicy;
    
    /**
     * 步骤参数
     */
    private Map<String, Object> parameters = new HashMap<>();
    
    /**
     * 超时时间（秒）
     */
    private long timeoutSeconds = 300;
    
    /**
     * 是否可以跳过
     */
    private boolean skippable = false;
    
    /**
     * 执行步骤
     */
    public abstract StepResult execute(WorkflowContext context);
    
    /**
     * 验证步骤配置
     */
    public void validate() {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Step ID cannot be null or empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Step name cannot be null or empty");
        }
        if (type == null) {
            throw new IllegalArgumentException("Step type cannot be null");
        }
    }
    
    /**
     * 检查前置条件是否满足
     */
    public boolean canExecute(WorkflowContext context) {
        // 检查依赖步骤是否完成
        for (String dependency : dependencies) {
            StepStatus status = context.getStepStatus(dependency);
            if (status != StepStatus.COMPLETED) {
                return false;
            }
        }
        
        // 检查步骤条件
        if (condition != null) {
            return condition.evaluate(context);
        }
        
        return true;
    }
    
    /**
     * 获取参数值
     */
    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key, Class<T> type) {
        Object value = parameters.get(key);
        if (value == null) {
            return null;
        }
        return type.cast(value);
    }
    
    /**
     * 设置参数
     */
    public WorkflowStep setParameter(String key, Object value) {
        parameters.put(key, value);
        return this;
    }
    
    /**
     * 执行步骤（带重试）
     */
    public StepResult executeWithRetry(WorkflowContext context) {
        int maxRetries = retryPolicy != null ? retryPolicy.getMaxRetries() : 0;
        int attempt = 0;
        
        while (attempt <= maxRetries) {
            try {
                context.addEvent("STEP_EXECUTION_START", 
                        String.format("Executing step %s (attempt %d)", id, attempt + 1));
                
                StepResult result = execute(context);
                
                if (result.isSuccess()) {
                    context.addEvent("STEP_EXECUTION_SUCCESS", 
                            String.format("Step %s completed successfully", id));
                    return result;
                }
                
                if (attempt < maxRetries && retryPolicy.shouldRetry(result)) {
                    attempt++;
                    context.addEvent("STEP_EXECUTION_RETRY", 
                            String.format("Step %s failed, retrying (attempt %d/%d)", 
                                    id, attempt + 1, maxRetries + 1));
                    
                    // 等待重试间隔
                    if (retryPolicy.getRetryDelayMs() > 0) {
                        try {
                            Thread.sleep(retryPolicy.getRetryDelayMs());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                } else {
                    context.addEvent("STEP_EXECUTION_FAILED", 
                            String.format("Step %s failed after %d attempts", id, attempt + 1));
                    return result;
                }
            } catch (Exception e) {
                StepResult errorResult = StepResult.failure("Step execution error: " + e.getMessage(), e);
                
                if (attempt < maxRetries && (retryPolicy == null || retryPolicy.shouldRetry(errorResult))) {
                    attempt++;
                    context.addEvent("STEP_EXECUTION_ERROR", 
                            String.format("Step %s error, retrying (attempt %d/%d): %s", 
                                    id, attempt + 1, maxRetries + 1, e.getMessage()));
                    
                    // 等待重试间隔
                    if (retryPolicy != null && retryPolicy.getRetryDelayMs() > 0) {
                        try {
                            Thread.sleep(retryPolicy.getRetryDelayMs());
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                } else {
                    context.addEvent("STEP_EXECUTION_ERROR", 
                            String.format("Step %s failed with error after %d attempts: %s", 
                                    id, attempt + 1, e.getMessage()));
                    return errorResult;
                }
            }
        }
        
        return StepResult.failure("Max retries exceeded for step: " + id);
    }
}
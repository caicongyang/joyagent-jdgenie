package com.jd.genie.agent.promptflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 执行上下文
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionContext {
    
    /**
     * 执行计划
     */
    private ExecutionPlan plan;
    
    /**
     * 步骤执行结果映射
     */
    @Builder.Default
    private Map<String, StepResult> stepResults = new ConcurrentHashMap<>();
    
    /**
     * 全局变量
     */
    @Builder.Default
    private Map<String, Object> globalVariables = new HashMap<>();
    
    /**
     * 运行时变量
     */
    @Builder.Default
    private Map<String, Object> runtimeVariables = new HashMap<>();
    
    /**
     * 执行开始时间
     */
    private long startTime;
    
    /**
     * 当前执行的步骤ID
     */
    private String currentStepId;
    
    /**
     * 添加步骤结果
     */
    public void addStepResult(String stepId, StepResult result) {
        stepResults.put(stepId, result);
        
        // 将步骤结果添加到运行时变量中，便于后续步骤引用
        runtimeVariables.put(stepId + ".result", result.getOutput());
        runtimeVariables.put(stepId + ".success", result.isSuccess());
        runtimeVariables.put(stepId + ".error", result.getError());
    }
    
    /**
     * 获取步骤结果
     */
    public StepResult getStepResult(String stepId) {
        return stepResults.get(stepId);
    }
    
    /**
     * 检查步骤是否已执行
     */
    public boolean isStepExecuted(String stepId) {
        return stepResults.containsKey(stepId);
    }
    
    /**
     * 检查步骤是否执行成功
     */
    public boolean isStepSuccessful(String stepId) {
        StepResult result = stepResults.get(stepId);
        return result != null && result.isSuccess();
    }
    
    /**
     * 获取变量值
     */
    public Object getVariable(String varName) {
        // 优先从运行时变量获取
        if (runtimeVariables.containsKey(varName)) {
            return runtimeVariables.get(varName);
        }
        
        // 然后从全局变量获取
        return globalVariables.get(varName);
    }
    
    /**
     * 设置运行时变量
     */
    public void setRuntimeVariable(String name, Object value) {
        runtimeVariables.put(name, value);
    }
    
    /**
     * 合并变量映射
     */
    public void mergeVariables(Map<String, Object> variables) {
        if (variables != null) {
            runtimeVariables.putAll(variables);
        }
    }
}
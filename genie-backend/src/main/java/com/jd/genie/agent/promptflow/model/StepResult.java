package com.jd.genie.agent.promptflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 步骤执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepResult {
    
    /**
     * 步骤ID
     */
    private String stepId;
    
    /**
     * 执行是否成功
     */
    private boolean success;
    
    /**
     * 执行输出结果
     */
    private Object output;
    
    /**
     * 错误信息
     */
    private String error;
    
    /**
     * 执行时间(毫秒)
     */
    private long executionTime;
    
    /**
     * 重试次数
     */
    private int retryCount;
    
    /**
     * 创建成功结果
     */
    public static StepResult success(Object output) {
        return StepResult.builder()
                .success(true)
                .output(output)
                .build();
    }
    
    /**
     * 创建成功结果(带步骤ID)
     */
    public static StepResult success(String stepId, Object output) {
        return StepResult.builder()
                .stepId(stepId)
                .success(true)
                .output(output)
                .build();
    }
    
    /**
     * 创建失败结果
     */
    public static StepResult failure(String error) {
        return StepResult.builder()
                .success(false)
                .error(error)
                .build();
    }
    
    /**
     * 创建失败结果(带步骤ID)
     */
    public static StepResult failure(String stepId, String error) {
        return StepResult.builder()
                .stepId(stepId)
                .success(false)
                .error(error)
                .build();
    }
}
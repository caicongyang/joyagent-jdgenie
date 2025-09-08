package com.jd.genie.agent.promptflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 执行结果模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResult {
    
    /**
     * 执行是否成功
     */
    private boolean success;
    
    /**
     * 最终输出结果
     */
    private String finalOutput;
    
    /**
     * 失败的步骤ID
     */
    private String failedStepId;
    
    /**
     * 错误信息
     */
    private String error;
    
    /**
     * 执行上下文数据
     */
    private Map<String, Object> contextData;
    
    /**
     * 执行统计信息
     */
    private ExecutionStats stats;
    
    /**
     * 创建成功结果
     */
    public static ExecutionResult success(String finalOutput) {
        return ExecutionResult.builder()
                .success(true)
                .finalOutput(finalOutput)
                .build();
    }
    
    /**
     * 创建失败结果
     */
    public static ExecutionResult failure(String failedStepId, String error) {
        return ExecutionResult.builder()
                .success(false)
                .failedStepId(failedStepId)
                .error(error)
                .build();
    }
    
    /**
     * 执行统计信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionStats {
        
        /**
         * 总执行时间(毫秒)
         */
        private long totalExecutionTime;
        
        /**
         * 成功步骤数
         */
        private int successfulSteps;
        
        /**
         * 失败步骤数
         */
        private int failedSteps;
        
        /**
         * 跳过步骤数
         */
        private int skippedSteps;
        
        /**
         * 重试次数
         */
        private int totalRetries;
    }
}
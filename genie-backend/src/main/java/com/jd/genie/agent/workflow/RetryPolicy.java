package com.jd.genie.agent.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 重试策略
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryPolicy {
    /**
     * Get max retries
     */
    public int getMaxRetries() {
        return maxRetries;
    }
    
    /**
     * Get retry delay ms
     */
    public long getRetryDelayMs() {
        return retryDelayMs;
    }
    
    /**
     * 最大重试次数
     */
    @Builder.Default
    private int maxRetries = 0;
    
    /**
     * 重试间隔（毫秒）
     */
    @Builder.Default
    private long retryDelayMs = 1000;
    
    /**
     * 重试策略类型
     */
    @Builder.Default
    private RetryType retryType = RetryType.FIXED_DELAY;
    
    /**
     * 指数退避的基数
     */
    @Builder.Default
    private double backoffMultiplier = 2.0;
    
    /**
     * 最大重试间隔（毫秒）
     */
    @Builder.Default
    private long maxRetryDelayMs = 30000;
    
    /**
     * 重试类型
     */
    public enum RetryType {
        FIXED_DELAY,     // 固定延迟
        EXPONENTIAL,     // 指数退避
        LINEAR           // 线性增长
    }
    
    /**
     * 创建不重试策略
     */
    public static RetryPolicy noRetry() {
        return builder()
                .maxRetries(0)
                .build();
    }
    
    /**
     * 创建固定延迟重试策略
     */
    public static RetryPolicy fixedDelay(int maxRetries, long delayMs) {
        return builder()
                .maxRetries(maxRetries)
                .retryDelayMs(delayMs)
                .retryType(RetryType.FIXED_DELAY)
                .build();
    }
    
    /**
     * 创建指数退避重试策略
     */
    public static RetryPolicy exponentialBackoff(int maxRetries, long initialDelayMs, 
                                                double multiplier, long maxDelayMs) {
        return builder()
                .maxRetries(maxRetries)
                .retryDelayMs(initialDelayMs)
                .retryType(RetryType.EXPONENTIAL)
                .backoffMultiplier(multiplier)
                .maxRetryDelayMs(maxDelayMs)
                .build();
    }
    
    /**
     * 创建线性增长重试策略
     */
    public static RetryPolicy linearBackoff(int maxRetries, long initialDelayMs, long increment) {
        return builder()
                .maxRetries(maxRetries)
                .retryDelayMs(initialDelayMs)
                .retryType(RetryType.LINEAR)
                .backoffMultiplier(increment)
                .build();
    }
    
    /**
     * 判断是否应该重试
     */
    public boolean shouldRetry(StepResult result) {
        if (maxRetries <= 0) {
            return false;
        }
        
        // 如果步骤成功，不需要重试
        if (result.isSuccess()) {
            return false;
        }
        
        // 检查异常类型，某些异常不应该重试
        if (result.getException() != null) {
            Throwable exception = result.getException();
            
            // 中断异常不重试
            if (exception instanceof InterruptedException) {
                return false;
            }
            
            // 参数错误等不重试
            if (exception instanceof IllegalArgumentException || 
                exception instanceof IllegalStateException) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 计算重试延迟时间
     */
    public long calculateRetryDelay(int attemptNumber) {
        switch (retryType) {
            case FIXED_DELAY:
                return retryDelayMs;
                
            case EXPONENTIAL:
                long delay = (long) (retryDelayMs * Math.pow(backoffMultiplier, attemptNumber));
                return Math.min(delay, maxRetryDelayMs);
                
            case LINEAR:
                long linearDelay = retryDelayMs + (long) (backoffMultiplier * attemptNumber);
                return Math.min(linearDelay, maxRetryDelayMs);
                
            default:
                return retryDelayMs;
        }
    }
    
    /**
     * 获取总的最大等待时间
     */
    public long getTotalMaxWaitTime() {
        long totalTime = 0;
        for (int i = 0; i < maxRetries; i++) {
            totalTime += calculateRetryDelay(i);
        }
        return totalTime;
    }
}
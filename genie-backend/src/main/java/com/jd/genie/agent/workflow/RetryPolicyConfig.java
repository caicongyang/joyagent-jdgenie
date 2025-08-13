package com.jd.genie.agent.workflow;

import lombok.Data;

/**
 * 重试策略配置
 */
@Data
public class RetryPolicyConfig {
    
    /**
     * 最大重试次数
     */
    private Integer maxRetries;
    
    /**
     * 重试间隔（毫秒）
     */
    private Long retryDelayMs;
    
    /**
     * 重试策略类型
     */
    private String retryType;
    
    /**
     * 指数退避的基数
     */
    private Double backoffMultiplier;
    
    /**
     * 最大重试间隔（毫秒）
     */
    private Long maxRetryDelayMs;
}
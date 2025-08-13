package com.jd.genie.agent.workflow;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 工作流步骤配置
 */
@Data
public class WorkflowStepConfig {
    
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
    private String type;
    
    /**
     * 依赖步骤
     */
    private List<String> dependencies;
    
    /**
     * 步骤条件
     */
    private String condition;
    
    /**
     * 重试策略
     */
    private RetryPolicyConfig retryPolicy;
    
    /**
     * 超时时间（秒）
     */
    private Long timeoutSeconds;
    
    /**
     * 是否可跳过
     */
    private Boolean skippable;
    
    // LLM调用步骤专用字段
    /**
     * 提示词
     */
    private String prompt;
    
    /**
     * 系统提示词
     */
    private String systemPrompt;
    
    // 工具调用步骤专用字段
    /**
     * 工具名称
     */
    private String toolName;
    
    /**
     * 工具参数
     */
    private Map<String, Object> toolArgs;
    
    // 并行/顺序步骤专用字段
    /**
     * 子步骤
     */
    private List<WorkflowStepConfig> subSteps;
    
    /**
     * 最大并发数（并行步骤专用）
     */
    private Integer maxConcurrency;
    
    /**
     * 是否等待所有子步骤完成（并行步骤专用）
     */
    private Boolean waitForAll;
}
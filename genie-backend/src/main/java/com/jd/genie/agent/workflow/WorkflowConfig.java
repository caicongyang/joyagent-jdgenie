package com.jd.genie.agent.workflow;

import lombok.Data;

import java.util.Map;

/**
 * 工作流配置数据模型
 */
@Data
public class WorkflowConfig {
    
    /**
     * 工作流模板定义
     */
    private Map<String, WorkflowTemplateConfig> workflows;
    
    /**
     * 工作流设置
     */
    private WorkflowSettings settings;
    
    /**
     * 工作流模板配置
     */
    @Data
    public static class WorkflowTemplateConfig {
        private String id;
        private String name;
        private String description;
        private Integer maxSteps;
        private Long timeoutSeconds;
        private Map<String, Object> variables;
        private java.util.List<WorkflowStepConfig> steps;
    }
    
    /**
     * 工作流设置
     */
    @Data
    public static class WorkflowSettings {
        private DefaultConfig defaultConfig;
        private String workflowDirectory;
        private Boolean enableCache;
        private String defaultExecutionMode;
    }
    
    /**
     * 默认配置
     */
    @Data
    public static class DefaultConfig {
        private Integer maxSteps;
        private Long timeoutSeconds;
        private RetryPolicyConfig retryPolicy;
    }
}
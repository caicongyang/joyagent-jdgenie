package com.jd.genie.agent.promptflow.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 执行计划模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExecutionPlan {
    
    /**
     * 用户目标描述
     */
    private String goal;
    
    /**
     * 计划唯一标识
     */
    private String planId;
    
    /**
     * 执行步骤列表
     */
    private List<ExecutionStep> steps;
    
    /**
     * 全局变量
     */
    private Map<String, Object> globalVariables;
    
    /**
     * 计划元数据
     */
    private PlanMetadata metadata;
    
    /**
     * 执行步骤
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionStep {
        
        /**
         * 步骤唯一标识
         */
        private String id;
        
        /**
         * 步骤描述
         */
        private String description;
        
        /**
         * 使用的工具名称
         */
        private String tool;
        
        /**
         * 工具参数
         */
        private Map<String, Object> params;
        
        /**
         * 依赖的步骤ID列表
         */
        private List<String> dependencies;
        
        /**
         * 预期输出描述
         */
        private String expectedOutput;
        
        /**
         * 重试次数
         */
        @Builder.Default
        private int retryCount = 0;
        
        /**
         * 是否为可选步骤
         */
        @Builder.Default
        private boolean optional = false;
        
        /**
         * 超时时间(秒)
         */
        @Builder.Default
        private int timeoutSeconds = 300;
    }
    
    /**
     * 计划元数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanMetadata {
        
        /**
         * 创建者
         */
        private String createdBy;
        
        /**
         * 创建时间
         */
        private long createdTime;
        
        /**
         * 预估步骤数
         */
        private int estimatedSteps;
        
        /**
         * 复杂度评估
         */
        private String complexity;
        
        /**
         * 预估执行时间(秒)
         */
        private int estimatedDuration;
    }
}
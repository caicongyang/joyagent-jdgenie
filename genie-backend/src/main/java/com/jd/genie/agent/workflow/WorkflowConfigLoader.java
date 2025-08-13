package com.jd.genie.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作流配置加载器
 * 负责从配置文件加载工作流定义
 */
@Slf4j
@Component
public class WorkflowConfigLoader {
    
    private static final String DEFAULT_CONFIG_PATH = "workflows.yml";
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    
    /**
     * 工作流配置缓存
     */
    private final Map<String, WorkflowDefinition> workflowCache = new ConcurrentHashMap<>();
    
    /**
     * 工作流配置数据
     */
    private WorkflowConfig workflowConfig;
    
    /**
     * 初始化加载配置
     */
    @PostConstruct
    public void init() {
        loadWorkflowConfig();
    }
    
    /**
     * 加载工作流配置
     */
    public void loadWorkflowConfig() {
        loadWorkflowConfig(DEFAULT_CONFIG_PATH);
    }
    
    /**
     * 从指定路径加载工作流配置
     */
    public void loadWorkflowConfig(String configPath) {
        try {
            log.info("Loading workflow config from: {}", configPath);
            
            Resource resource = new ClassPathResource(configPath);
            if (!resource.exists()) {
                log.warn("Workflow config file not found: {}, using empty config", configPath);
                workflowConfig = new WorkflowConfig();
                return;
            }
            
            try (InputStream inputStream = resource.getInputStream()) {
                workflowConfig = yamlMapper.readValue(inputStream, WorkflowConfig.class);
                
                // 构建工作流定义缓存
                buildWorkflowCache();
                
                log.info("Successfully loaded {} workflow templates", workflowCache.size());
                workflowCache.keySet().forEach(key -> 
                    log.debug("Loaded workflow template: {}", key));
                
            }
        } catch (IOException e) {
            log.error("Error loading workflow config from {}: {}", configPath, e.getMessage(), e);
            workflowConfig = new WorkflowConfig();
        }
    }
    
    /**
     * 构建工作流定义缓存
     */
    private void buildWorkflowCache() {
        workflowCache.clear();
        
        if (workflowConfig.getWorkflows() == null) {
            return;
        }
        
        workflowConfig.getWorkflows().forEach((templateName, templateConfig) -> {
            try {
                WorkflowDefinition definition = buildWorkflowDefinition(templateName, templateConfig);
                workflowCache.put(templateName, definition);
                
                // 同时以ID为key缓存
                if (definition.getId() != null && !definition.getId().equals(templateName)) {
                    workflowCache.put(definition.getId(), definition);
                }
                
            } catch (Exception e) {
                log.error("Error building workflow definition for template {}: {}", 
                        templateName, e.getMessage(), e);
            }
        });
    }
    
    /**
     * 构建工作流定义
     */
    private WorkflowDefinition buildWorkflowDefinition(String templateName, WorkflowConfig.WorkflowTemplateConfig templateConfig) {
        WorkflowDefinition.WorkflowDefinitionBuilder builder = WorkflowDefinition.builder()
                .id(templateConfig.getId() != null ? templateConfig.getId() : templateName)
                .name(templateConfig.getName() != null ? templateConfig.getName() : templateName)
                .description(templateConfig.getDescription())
                .maxSteps(templateConfig.getMaxSteps() != null ? templateConfig.getMaxSteps() : getDefaultMaxSteps())
                .timeoutSeconds(templateConfig.getTimeoutSeconds() != null ? templateConfig.getTimeoutSeconds() : getDefaultTimeoutSeconds())
                .trigger(WorkflowTrigger.manual());
        
        // 设置变量
        if (templateConfig.getVariables() != null) {
            builder.variables(new HashMap<>(templateConfig.getVariables()));
        }
        
        // 构建步骤
        List<WorkflowStep> steps = new ArrayList<>();
        if (templateConfig.getSteps() != null) {
            for (WorkflowStepConfig stepConfig : templateConfig.getSteps()) {
                WorkflowStep step = buildWorkflowStep(stepConfig);
                if (step != null) {
                    steps.add(step);
                }
            }
        }
        builder.steps(steps);
        
        return builder.build();
    }
    
    /**
     * 构建工作流步骤
     */
    private WorkflowStep buildWorkflowStep(WorkflowStepConfig stepConfig) {
        try {
            WorkflowStep step = createStepByType(stepConfig);
            if (step == null) {
                return null;
            }
            
            // 设置基本属性
            step.setId(stepConfig.getId());
            step.setName(stepConfig.getName());
            step.setDescription(stepConfig.getDescription());
            
            // 设置依赖
            if (stepConfig.getDependencies() != null) {
                step.setDependencies(new ArrayList<>(stepConfig.getDependencies()));
            }
            
            // 设置超时
            if (stepConfig.getTimeoutSeconds() != null) {
                step.setTimeoutSeconds(stepConfig.getTimeoutSeconds());
            }
            
            // 设置是否可跳过
            if (stepConfig.getSkippable() != null) {
                step.setSkippable(stepConfig.getSkippable());
            }
            
            // 设置重试策略
            if (stepConfig.getRetryPolicy() != null) {
                step.setRetryPolicy(buildRetryPolicy(stepConfig.getRetryPolicy()));
            }
            
            // 设置条件
            if (stepConfig.getCondition() != null) {
                step.setCondition(StepCondition.expression(stepConfig.getCondition()));
            }
            
            return step;
            
        } catch (Exception e) {
            log.error("Error building workflow step {}: {}", stepConfig.getId(), e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 根据类型创建步骤
     */
    private WorkflowStep createStepByType(WorkflowStepConfig stepConfig) {
        String type = stepConfig.getType();
        if (type == null) {
            log.warn("Step type is null for step: {}", stepConfig.getId());
            return null;
        }
        
        switch (type.toLowerCase()) {
            case "tool_call":
                return createToolCallStep(stepConfig);
            case "llm_call":
                return createLlmCallStep(stepConfig);
            case "sequential":
                return createSequentialStep(stepConfig);
            case "parallel":
                return createParallelStep(stepConfig);
            default:
                log.warn("Unsupported step type: {} for step: {}", type, stepConfig.getId());
                return null;
        }
    }
    
    /**
     * 创建工具调用步骤
     */
    private WorkflowStep createToolCallStep(WorkflowStepConfig stepConfig) {
        String toolName = stepConfig.getToolName();
        if (toolName == null) {
            log.error("Tool name is required for tool_call step: {}", stepConfig.getId());
            return null;
        }
        
        Map<String, Object> toolArgs = stepConfig.getToolArgs() != null ? 
                new HashMap<>(stepConfig.getToolArgs()) : new HashMap<>();
        
        return new com.jd.genie.agent.workflow.steps.ToolCallStep(
                stepConfig.getId(), stepConfig.getName(), toolName, toolArgs);
    }
    
    /**
     * 创建LLM调用步骤
     */
    private WorkflowStep createLlmCallStep(WorkflowStepConfig stepConfig) {
        String prompt = stepConfig.getPrompt();
        if (prompt == null) {
            log.error("Prompt is required for llm_call step: {}", stepConfig.getId());
            return null;
        }
        
        return new com.jd.genie.agent.workflow.steps.LlmCallStep(
                stepConfig.getId(), stepConfig.getName(), prompt, stepConfig.getSystemPrompt());
    }
    
    /**
     * 创建顺序步骤
     */
    private WorkflowStep createSequentialStep(WorkflowStepConfig stepConfig) {
        List<WorkflowStep> subSteps = new ArrayList<>();
        if (stepConfig.getSubSteps() != null) {
            for (WorkflowStepConfig subStepConfig : stepConfig.getSubSteps()) {
                WorkflowStep subStep = buildWorkflowStep(subStepConfig);
                if (subStep != null) {
                    subSteps.add(subStep);
                }
            }
        }
        
        return new com.jd.genie.agent.workflow.steps.SequentialStep(
                stepConfig.getId(), stepConfig.getName(), subSteps);
    }
    
    /**
     * 创建并行步骤
     */
    private WorkflowStep createParallelStep(WorkflowStepConfig stepConfig) {
        List<WorkflowStep> subSteps = new ArrayList<>();
        if (stepConfig.getSubSteps() != null) {
            for (WorkflowStepConfig subStepConfig : stepConfig.getSubSteps()) {
                WorkflowStep subStep = buildWorkflowStep(subStepConfig);
                if (subStep != null) {
                    subSteps.add(subStep);
                }
            }
        }
        
        com.jd.genie.agent.workflow.steps.ParallelStep parallelStep = 
                new com.jd.genie.agent.workflow.steps.ParallelStep(
                        stepConfig.getId(), stepConfig.getName(), subSteps);
        
        // 设置并行参数
        if (stepConfig.getMaxConcurrency() != null) {
            parallelStep.setMaxConcurrency(stepConfig.getMaxConcurrency());
        }
        if (stepConfig.getWaitForAll() != null) {
            parallelStep.setWaitForAll(stepConfig.getWaitForAll());
        }
        
        return parallelStep;
    }
    
    /**
     * 构建重试策略
     */
    private RetryPolicy buildRetryPolicy(RetryPolicyConfig retryConfig) {
        RetryPolicy.RetryPolicyBuilder builder = RetryPolicy.builder()
                .maxRetries(retryConfig.getMaxRetries() != null ? retryConfig.getMaxRetries() : 0)
                .retryDelayMs(retryConfig.getRetryDelayMs() != null ? retryConfig.getRetryDelayMs() : 1000);
        
        if (retryConfig.getRetryType() != null) {
            try {
                RetryPolicy.RetryType retryType = RetryPolicy.RetryType.valueOf(retryConfig.getRetryType().toUpperCase());
                builder.retryType(retryType);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid retry type: {}, using default FIXED_DELAY", retryConfig.getRetryType());
                builder.retryType(RetryPolicy.RetryType.FIXED_DELAY);
            }
        }
        
        if (retryConfig.getBackoffMultiplier() != null) {
            builder.backoffMultiplier(retryConfig.getBackoffMultiplier());
        }
        if (retryConfig.getMaxRetryDelayMs() != null) {
            builder.maxRetryDelayMs(retryConfig.getMaxRetryDelayMs());
        }
        
        return builder.build();
    }
    
    /**
     * 根据模板名称获取工作流定义
     */
    public WorkflowDefinition getWorkflowByTemplate(String templateName) {
        return workflowCache.get(templateName);
    }
    
    /**
     * 根据ID获取工作流定义
     */
    public WorkflowDefinition getWorkflowById(String workflowId) {
        return workflowCache.get(workflowId);
    }
    
    /**
     * 获取所有可用的工作流模板名称
     */
    public Set<String> getAvailableTemplates() {
        return new HashSet<>(workflowCache.keySet());
    }
    
    /**
     * 检查模板是否存在
     */
    public boolean hasTemplate(String templateName) {
        return workflowCache.containsKey(templateName);
    }
    
    /**
     * 重新加载配置
     */
    public void reload() {
        loadWorkflowConfig();
    }
    
    /**
     * 获取默认最大步数
     */
    private int getDefaultMaxSteps() {
        if (workflowConfig != null && workflowConfig.getSettings() != null && 
            workflowConfig.getSettings().getDefaultConfig() != null) {
            Integer maxSteps = workflowConfig.getSettings().getDefaultConfig().getMaxSteps();
            if (maxSteps != null) {
                return maxSteps;
            }
        }
        return 50;
    }
    
    /**
     * 获取默认超时时间
     */
    private long getDefaultTimeoutSeconds() {
        if (workflowConfig != null && workflowConfig.getSettings() != null && 
            workflowConfig.getSettings().getDefaultConfig() != null) {
            Long timeoutSeconds = workflowConfig.getSettings().getDefaultConfig().getTimeoutSeconds();
            if (timeoutSeconds != null) {
                return timeoutSeconds;
            }
        }
        return 1800;
    }
    
    /**
     * 获取配置信息
     */
    public WorkflowConfig getWorkflowConfig() {
        return workflowConfig;
    }
}
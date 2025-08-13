package com.jd.genie.agent.workflow;

import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 工作流执行引擎
 */
@Slf4j
public class WorkflowEngine {
    
    /**
     * 执行工作流
     */
    public WorkflowResult executeWorkflow(WorkflowDefinition definition, WorkflowContext context) {
        Date startTime = new Date();
        
        try {
            // 验证工作流定义
            validateWorkflow(definition);
            
            // 初始化工作流状态
            context.setWorkflowStatus(WorkflowStatus.RUNNING);
            context.addEvent("WORKFLOW_STARTED", 
                    String.format("Starting workflow: %s", definition.getName()));
            
            // 初始化所有步骤状态为PENDING
            initializeStepStatuses(definition, context);
            
            // 执行工作流步骤
            int executedSteps = 0;
            int maxSteps = definition.getMaxSteps();
            
            while (executedSteps < maxSteps && hasExecutableSteps(definition, context)) {
                WorkflowStep nextStep = getNextExecutableStep(definition, context);
                
                if (nextStep == null) {
                    log.warn("No executable step found but hasExecutableSteps returned true");
                    break;
                }
                
                // 执行步骤
                StepResult stepResult = executeStep(nextStep, context);
                executedSteps++;
                
                // 更新步骤状态
                updateStepStatus(nextStep, stepResult, context);
                
                // 检查是否需要终止
                if (!stepResult.isSuccess() && !nextStep.isSkippable()) {
                    context.fail(String.format("Workflow failed at step %s: %s", 
                            nextStep.getId(), stepResult.getErrorMessage()));
                    return WorkflowResult.failure(context, stepResult.getErrorMessage());
                }
            }
            
            // 检查工作流完成状态
            if (executedSteps >= maxSteps) {
                context.fail("Workflow terminated: Maximum steps exceeded (" + maxSteps + ")");
                return WorkflowResult.failure(context, "Maximum steps exceeded");
            }
            
            // 工作流完成
            context.complete();
            context.addEvent("WORKFLOW_COMPLETED", 
                    String.format("Workflow completed successfully in %d steps", executedSteps));
            
            return WorkflowResult.success(context);
            
        } catch (Exception e) {
            log.error("Error executing workflow {}: {}", definition.getId(), e.getMessage(), e);
            context.fail("Workflow execution error: " + e.getMessage());
            return WorkflowResult.failure(context, e.getMessage());
            
        } finally {
            Date endTime = new Date();
            long executionTime = endTime.getTime() - startTime.getTime();
            log.info("Workflow {} execution completed in {}ms", 
                    definition.getId(), executionTime);
        }
    }
    
    /**
     * 执行下一个步骤
     */
    public StepResult executeNextStep(WorkflowDefinition definition, WorkflowContext context) {
        WorkflowStep nextStep = getNextExecutableStep(definition, context);
        
        if (nextStep == null) {
            return StepResult.success("No more executable steps");
        }
        
        return executeStep(nextStep, context);
    }
    
    /**
     * 执行单个步骤
     */
    private StepResult executeStep(WorkflowStep step, WorkflowContext context) {
        Date startTime = new Date();
        
        try {
            context.setCurrentStepId(step.getId());
            context.setStepStatus(step.getId(), StepStatus.RUNNING);
            
            log.info("Executing workflow step: {} ({})", step.getId(), step.getType());
            
            context.addEvent("STEP_EXECUTION_START", 
                    String.format("Starting step: %s [%s]", step.getId(), step.getType()));
            
            // 执行步骤（带重试）
            StepResult result = step.executeWithRetry(context);
            
            // 记录执行结果
            if (result.isSuccess()) {
                context.addEvent("STEP_EXECUTION_SUCCESS", 
                        String.format("Step %s completed successfully", step.getId()));
                log.info("Step {} completed successfully", step.getId());
            } else {
                context.addEvent("STEP_EXECUTION_FAILED", 
                        String.format("Step %s failed: %s", step.getId(), result.getErrorMessage()));
                log.warn("Step {} failed: {}", step.getId(), result.getErrorMessage());
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("Error executing step {}: {}", step.getId(), e.getMessage(), e);
            
            context.addEvent("STEP_EXECUTION_ERROR", 
                    String.format("Step %s error: %s", step.getId(), e.getMessage()));
            
            return StepResult.failure("Step execution error: " + e.getMessage(), e)
                    .withExecutionTime(startTime, new Date());
        }
    }
    
    /**
     * 获取下一个可执行的步骤
     */
    private WorkflowStep getNextExecutableStep(WorkflowDefinition definition, WorkflowContext context) {
        return definition.getSteps().stream()
                .filter(step -> context.getStepStatus(step.getId()) == StepStatus.PENDING)
                .filter(step -> step.canExecute(context))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 检查是否还有可执行的步骤
     */
    private boolean hasExecutableSteps(WorkflowDefinition definition, WorkflowContext context) {
        return definition.getSteps().stream()
                .anyMatch(step -> context.getStepStatus(step.getId()) == StepStatus.PENDING 
                        && step.canExecute(context));
    }
    
    /**
     * 初始化所有步骤状态
     */
    private void initializeStepStatuses(WorkflowDefinition definition, WorkflowContext context) {
        for (WorkflowStep step : definition.getSteps()) {
            context.setStepStatus(step.getId(), StepStatus.PENDING);
        }
    }
    
    /**
     * 更新步骤状态
     */
    private void updateStepStatus(WorkflowStep step, StepResult result, WorkflowContext context) {
        StepStatus status;
        if (result.isSuccess()) {
            status = StepStatus.COMPLETED;
        } else if (step.isSkippable()) {
            status = StepStatus.SKIPPED;
        } else {
            status = StepStatus.FAILED;
        }
        
        context.setStepStatus(step.getId(), status);
        
        // 保存步骤结果
        if (result.getData() != null) {
            context.setStepResult(step.getId(), result.getData());
        }
    }
    
    /**
     * 验证工作流定义
     */
    private void validateWorkflow(WorkflowDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("Workflow definition cannot be null");
        }
        
        if (definition.getSteps() == null || definition.getSteps().isEmpty()) {
            throw new IllegalArgumentException("Workflow must have at least one step");
        }
        
        // 验证步骤ID唯一性
        List<String> stepIds = definition.getSteps().stream()
                .map(WorkflowStep::getId)
                .collect(Collectors.toList());
        
        long uniqueCount = stepIds.stream().distinct().count();
        if (uniqueCount != stepIds.size()) {
            throw new IllegalArgumentException("Workflow steps must have unique IDs");
        }
        
        // 验证每个步骤
        for (WorkflowStep step : definition.getSteps()) {
            step.validate();
        }
        
        // 验证依赖关系
        validateDependencies(definition);
    }
    
    /**
     * 验证步骤依赖关系
     */
    private void validateDependencies(WorkflowDefinition definition) {
        List<String> stepIds = definition.getSteps().stream()
                .map(WorkflowStep::getId)
                .collect(Collectors.toList());
        
        for (WorkflowStep step : definition.getSteps()) {
            for (String dependency : step.getDependencies()) {
                if (!stepIds.contains(dependency)) {
                    throw new IllegalArgumentException(
                            String.format("Step %s depends on non-existent step: %s", 
                                    step.getId(), dependency));
                }
            }
        }
        
        // 检查循环依赖（简单实现）
        checkCircularDependencies(definition);
    }
    
    /**
     * 检查循环依赖
     */
    private void checkCircularDependencies(WorkflowDefinition definition) {
        // 使用深度优先搜索检查循环依赖
        for (WorkflowStep step : definition.getSteps()) {
            java.util.Set<String> visited = new java.util.HashSet<>();
            java.util.Set<String> recursionStack = new java.util.HashSet<>();
            
            if (hasCycle(step.getId(), definition, visited, recursionStack)) {
                throw new IllegalArgumentException(
                        String.format("Circular dependency detected involving step: %s", step.getId()));
            }
        }
    }
    
    /**
     * 递归检查是否存在循环
     */
    private boolean hasCycle(String stepId, WorkflowDefinition definition,
                           java.util.Set<String> visited, java.util.Set<String> recursionStack) {
        if (recursionStack.contains(stepId)) {
            return true; // 发现循环
        }
        
        if (visited.contains(stepId)) {
            return false; // 已经访问过，且不在递归栈中
        }
        
        visited.add(stepId);
        recursionStack.add(stepId);
        
        // 获取当前步骤的依赖
        WorkflowStep currentStep = definition.getStepById(stepId);
        if (currentStep != null) {
            for (String dependency : currentStep.getDependencies()) {
                if (hasCycle(dependency, definition, visited, recursionStack)) {
                    return true;
                }
            }
        }
        
        recursionStack.remove(stepId);
        return false;
    }
}
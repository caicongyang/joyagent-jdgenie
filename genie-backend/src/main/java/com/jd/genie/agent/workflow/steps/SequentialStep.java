package com.jd.genie.agent.workflow.steps;

import com.jd.genie.agent.workflow.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 顺序执行步骤
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
public class SequentialStep extends WorkflowStep {
    
    /**
     * 子步骤列表
     */
    private List<WorkflowStep> subSteps = new ArrayList<>();
    
    public SequentialStep() {
        setType(StepType.SEQUENTIAL);
    }
    
    public SequentialStep(String id, String name, List<WorkflowStep> subSteps) {
        this();
        setId(id);
        setName(name);
        this.subSteps = subSteps != null ? subSteps : new ArrayList<>();
    }
    
    @Override
    public StepResult execute(WorkflowContext context) {
        Date startTime = new Date();
        List<StepResult> subResults = new ArrayList<>();
        
        try {
            context.addEvent("SEQUENTIAL_STEP_START", 
                    String.format("Starting sequential execution of %d sub-steps", subSteps.size()));
            
            // 顺序执行每个子步骤
            for (int i = 0; i < subSteps.size(); i++) {
                WorkflowStep subStep = subSteps.get(i);
                
                context.addEvent("SUB_STEP_START", 
                        String.format("Executing sub-step %d/%d: %s", i + 1, subSteps.size(), subStep.getId()));
                
                // 检查子步骤是否可以执行
                if (!subStep.canExecute(context)) {
                    context.addEvent("SUB_STEP_SKIPPED", 
                            String.format("Sub-step %s skipped due to conditions", subStep.getId()));
                    context.setStepStatus(subStep.getId(), StepStatus.SKIPPED);
                    continue;
                }
                
                // 设置子步骤状态为执行中
                context.setStepStatus(subStep.getId(), StepStatus.RUNNING);
                context.setCurrentStepId(subStep.getId());
                
                // 执行子步骤
                StepResult subResult = subStep.executeWithRetry(context);
                subResults.add(subResult);
                
                // 更新子步骤状态
                if (subResult.isSuccess()) {
                    context.setStepStatus(subStep.getId(), StepStatus.COMPLETED);
                    context.addEvent("SUB_STEP_SUCCESS", 
                            String.format("Sub-step %s completed successfully", subStep.getId()));
                } else {
                    context.setStepStatus(subStep.getId(), StepStatus.FAILED);
                    context.addEvent("SUB_STEP_FAILED", 
                            String.format("Sub-step %s failed: %s", subStep.getId(), subResult.getErrorMessage()));
                    
                    // 检查是否是关键步骤，如果是则中断执行
                    if (!subStep.isSkippable()) {
                        context.addEvent("SEQUENTIAL_STEP_FAILED", 
                                String.format("Sequential step failed at sub-step %s", subStep.getId()));
                        
                        return StepResult.failure(
                                String.format("Sequential step failed at sub-step %s: %s", 
                                        subStep.getId(), subResult.getErrorMessage()),
                                subResult.getException())
                                .withExecutionTime(startTime, new Date())
                                .withProperty("failedSubStep", subStep.getId())
                                .withProperty("subResults", subResults);
                    }
                }
            }
            
            context.addEvent("SEQUENTIAL_STEP_SUCCESS", 
                    String.format("Sequential step completed with %d sub-steps", subSteps.size()));
            
            // 所有子步骤执行完成，返回成功结果
            return StepResult.success(subResults)
                    .withExecutionTime(startTime, new Date())
                    .withProperty("subStepCount", subSteps.size())
                    .withProperty("subResults", subResults);
            
        } catch (Exception e) {
            log.error("Error in sequential step execution {}: {}", getId(), e.getMessage(), e);
            
            context.addEvent("SEQUENTIAL_STEP_ERROR", 
                    String.format("Sequential step error: %s", e.getMessage()));
            
            return StepResult.failure("Sequential step execution error: " + e.getMessage(), e)
                    .withExecutionTime(startTime, new Date())
                    .withProperty("subResults", subResults);
        } finally {
            context.setCurrentStepId(getId());
        }
    }
    
    /**
     * 添加子步骤
     */
    public SequentialStep addSubStep(WorkflowStep subStep) {
        if (subStep != null) {
            this.subSteps.add(subStep);
        }
        return this;
    }
    
    /**
     * 添加多个子步骤
     */
    public SequentialStep addSubSteps(List<WorkflowStep> subSteps) {
        if (subSteps != null) {
            this.subSteps.addAll(subSteps);
        }
        return this;
    }
    
    @Override
    public void validate() {
        super.validate();
        
        if (subSteps.isEmpty()) {
            throw new IllegalArgumentException("Sequential step must have at least one sub-step: " + getId());
        }
        
        // 验证子步骤
        for (WorkflowStep subStep : subSteps) {
            if (subStep == null) {
                throw new IllegalArgumentException("Sub-step cannot be null in sequential step: " + getId());
            }
            subStep.validate();
        }
    }
    
    @Override
    public boolean canExecute(WorkflowContext context) {
        if (!super.canExecute(context)) {
            return false;
        }
        
        // 检查是否有任何子步骤可以执行
        return subSteps.stream().anyMatch(subStep -> subStep.canExecute(context));
    }
    
    /**
     * 创建顺序步骤的便捷方法
     */
    public static SequentialStep create(String id, String name, List<WorkflowStep> subSteps) {
        SequentialStep step = new SequentialStep(id, name, subSteps);
        step.validate();
        return step;
    }
}
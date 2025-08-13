package com.jd.genie.agent.workflow.steps;

import com.jd.genie.agent.util.ThreadUtil;
import com.jd.genie.agent.workflow.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 并行执行步骤
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
public class ParallelStep extends WorkflowStep {
    
    /**
     * 子步骤列表
     */
    private List<WorkflowStep> subSteps = new ArrayList<>();
    
    /**
     * 最大并发数，0表示无限制
     */
    private int maxConcurrency = 0;
    
    /**
     * 是否等待所有子步骤完成，false表示只要有一个成功就返回
     */
    private boolean waitForAll = true;
    
    /**
     * 失败策略：遇到失败是否立即停止
     */
    private boolean failFast = false;
    
    public ParallelStep() {
        setType(StepType.PARALLEL);
    }
    
    public ParallelStep(String id, String name, List<WorkflowStep> subSteps) {
        this();
        setId(id);
        setName(name);
        this.subSteps = subSteps != null ? subSteps : new ArrayList<>();
    }
    
    @Override
    public StepResult execute(WorkflowContext context) {
        Date startTime = new Date();
        
        try {
            List<WorkflowStep> executableSteps = getExecutableSteps(context);
            if (executableSteps.isEmpty()) {
                context.addEvent("PARALLEL_STEP_SKIPPED", "No executable sub-steps found");
                return StepResult.success(new ArrayList<>())
                        .withExecutionTime(startTime, new Date());
            }
            
            context.addEvent("PARALLEL_STEP_START", 
                    String.format("Starting parallel execution of %d sub-steps (max concurrency: %d)", 
                            executableSteps.size(), maxConcurrency > 0 ? maxConcurrency : executableSteps.size()));
            
            // 使用并发执行
            ConcurrentHashMap<String, StepResult> results = new ConcurrentHashMap<>();
            CountDownLatch latch = ThreadUtil.getCountDownLatch(executableSteps.size());
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);
            
            // 执行所有子步骤
            for (WorkflowStep subStep : executableSteps) {
                ThreadUtil.execute(() -> {
                    try {
                        executeSubStep(subStep, context, results, successCount, failureCount);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            // 等待所有子步骤完成
            ThreadUtil.await(latch);
            
            // 分析执行结果
            return analyzeResults(context, results, successCount.get(), 
                    failureCount.get(), startTime);
            
        } catch (Exception e) {
            log.error("Error in parallel step execution {}: {}", getId(), e.getMessage(), e);
            
            context.addEvent("PARALLEL_STEP_ERROR", 
                    String.format("Parallel step error: %s", e.getMessage()));
            
            return StepResult.failure("Parallel step execution error: " + e.getMessage(), e)
                    .withExecutionTime(startTime, new Date());
        }
    }
    
    /**
     * 获取可执行的子步骤
     */
    private List<WorkflowStep> getExecutableSteps(WorkflowContext context) {
        List<WorkflowStep> executable = new ArrayList<>();
        for (WorkflowStep subStep : subSteps) {
            if (subStep.canExecute(context)) {
                executable.add(subStep);
            } else {
                context.setStepStatus(subStep.getId(), StepStatus.SKIPPED);
                context.addEvent("SUB_STEP_SKIPPED", 
                        String.format("Sub-step %s skipped due to conditions", subStep.getId()));
            }
        }
        return executable;
    }
    
    /**
     * 执行单个子步骤
     */
    private void executeSubStep(WorkflowStep subStep, WorkflowContext context,
                               ConcurrentHashMap<String, StepResult> results,
                               AtomicInteger successCount, AtomicInteger failureCount) {
        try {
            context.addEvent("SUB_STEP_START", 
                    String.format("Starting parallel sub-step: %s", subStep.getId()));
            
            // 设置步骤状态
            context.setStepStatus(subStep.getId(), StepStatus.RUNNING);
            
            // 执行子步骤
            StepResult result = subStep.executeWithRetry(context);
            results.put(subStep.getId(), result);
            
            // 更新状态和计数
            if (result.isSuccess()) {
                context.setStepStatus(subStep.getId(), StepStatus.COMPLETED);
                successCount.incrementAndGet();
                context.addEvent("SUB_STEP_SUCCESS", 
                        String.format("Sub-step %s completed successfully", subStep.getId()));
            } else {
                context.setStepStatus(subStep.getId(), StepStatus.FAILED);
                failureCount.incrementAndGet();
                context.addEvent("SUB_STEP_FAILED", 
                        String.format("Sub-step %s failed: %s", subStep.getId(), result.getErrorMessage()));
            }
            
        } catch (Exception e) {
            log.error("Error executing parallel sub-step {}: {}", subStep.getId(), e.getMessage(), e);
            
            StepResult errorResult = StepResult.failure("Sub-step execution error: " + e.getMessage(), e);
            results.put(subStep.getId(), errorResult);
            context.setStepStatus(subStep.getId(), StepStatus.FAILED);
            failureCount.incrementAndGet();
            
            context.addEvent("SUB_STEP_ERROR", 
                    String.format("Sub-step %s error: %s", subStep.getId(), e.getMessage()));
        }
    }
    
    /**
     * 分析执行结果
     */
    private StepResult analyzeResults(WorkflowContext context, 
                                    ConcurrentHashMap<String, StepResult> results,
                                    int successCount, int failureCount, Date startTime) {
        
        int totalCount = successCount + failureCount;
        
        context.addEvent("PARALLEL_STEP_COMPLETED", 
                String.format("Parallel step completed: %d success, %d failed, %d total", 
                        successCount, failureCount, totalCount));
        
        // 根据等待策略决定成功条件
        boolean isSuccess;
        String message;
        
        if (waitForAll) {
            // 等待所有步骤完成，只有全部成功才算成功
            isSuccess = failureCount == 0;
            message = isSuccess ? 
                    String.format("All %d parallel sub-steps completed successfully", totalCount) :
                    String.format("Parallel step failed: %d of %d sub-steps failed", failureCount, totalCount);
        } else {
            // 只要有一个成功就算成功
            isSuccess = successCount > 0;
            message = isSuccess ?
                    String.format("Parallel step succeeded: %d of %d sub-steps completed", successCount, totalCount) :
                    String.format("All %d parallel sub-steps failed", totalCount);
        }
        
        StepResult result = isSuccess ? 
                StepResult.success(new ArrayList<>(results.values())) :
                StepResult.failure(message);
        
        return result
                .withExecutionTime(startTime, new Date())
                .withProperty("totalSteps", totalCount)
                .withProperty("successCount", successCount)
                .withProperty("failureCount", failureCount)
                .withProperty("stepResults", results);
    }
    
    /**
     * 添加子步骤
     */
    public ParallelStep addSubStep(WorkflowStep subStep) {
        if (subStep != null) {
            this.subSteps.add(subStep);
        }
        return this;
    }
    
    /**
     * 添加多个子步骤
     */
    public ParallelStep addSubSteps(List<WorkflowStep> subSteps) {
        if (subSteps != null) {
            this.subSteps.addAll(subSteps);
        }
        return this;
    }
    
    /**
     * 设置最大并发数
     */
    public ParallelStep maxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
        return this;
    }
    
    /**
     * 设置等待策略
     */
    public ParallelStep waitForAll(boolean waitForAll) {
        this.waitForAll = waitForAll;
        return this;
    }
    
    /**
     * 设置失败策略
     */
    public ParallelStep failFast(boolean failFast) {
        this.failFast = failFast;
        return this;
    }
    
    @Override
    public void validate() {
        super.validate();
        
        if (subSteps.isEmpty()) {
            throw new IllegalArgumentException("Parallel step must have at least one sub-step: " + getId());
        }
        
        if (maxConcurrency < 0) {
            throw new IllegalArgumentException("Max concurrency cannot be negative: " + getId());
        }
        
        // 验证子步骤
        for (WorkflowStep subStep : subSteps) {
            if (subStep == null) {
                throw new IllegalArgumentException("Sub-step cannot be null in parallel step: " + getId());
            }
            subStep.validate();
        }
    }
    
    /**
     * 创建并行步骤的便捷方法
     */
    public static ParallelStep create(String id, String name, List<WorkflowStep> subSteps) {
        ParallelStep step = new ParallelStep(id, name, subSteps);
        step.validate();
        return step;
    }
}
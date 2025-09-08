package com.jd.genie.agent.promptflow.adaptive;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.promptflow.engine.ExecutionEngine;
import com.jd.genie.agent.promptflow.model.ExecutionPlan;
import com.jd.genie.agent.promptflow.model.ExecutionResult;
import com.jd.genie.agent.promptflow.planner.TaskPlanner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 自适应控制器
 * 处理执行失败并尝试自动恢复
 */
@Slf4j
@Component
public class AdaptiveController {
    
    private final TaskPlanner taskPlanner;
    private final ExecutionEngine executionEngine;
    private final ErrorAnalyzer errorAnalyzer;
    
    public AdaptiveController() {
        this.taskPlanner = new TaskPlanner();
        this.executionEngine = new ExecutionEngine();
        this.errorAnalyzer = new ErrorAnalyzer();
    }
    
    /**
     * 处理执行失败
     */
    public String handleFailure(ExecutionPlan originalPlan, ExecutionResult result, AgentContext context) {
        log.info("开始处理执行失败，原计划: {}, 失败步骤: {}", 
                originalPlan.getPlanId(), result.getFailedStepId());
        
        try {
            // 1. 分析失败原因
            FailureAnalysis analysis = errorAnalyzer.analyze(result, originalPlan);
            log.info("失败分析完成: {}", analysis.getSummary());
            
            // 2. 决定恢复策略
            RecoveryStrategy strategy = determineRecoveryStrategy(analysis);
            log.info("选择恢复策略: {}", strategy);
            
            // 3. 执行恢复策略
            switch (strategy) {
                case RETRY_FAILED_STEP:
                    return retryFailedStep(originalPlan, result, context);
                    
                case SKIP_AND_CONTINUE:
                    return skipAndContinue(originalPlan, result, context);
                    
                case REPLAN_FROM_FAILURE:
                    return replanFromFailurePoint(originalPlan, analysis, context);
                    
                case COMPLETE_REPLAN:
                    return completeReplan(originalPlan, analysis, context);
                    
                case NO_RECOVERY:
                default:
                    return formatFailureMessage(originalPlan, result, analysis);
            }
            
        } catch (Exception e) {
            log.error("自适应恢复过程中发生异常", e);
            return "执行失败，且自动恢复也失败了：" + e.getMessage();
        }
    }
    
    /**
     * 决定恢复策略
     */
    private RecoveryStrategy determineRecoveryStrategy(FailureAnalysis analysis) {
        // 根据失败类型和严重程度决定策略
        switch (analysis.getFailureType()) {
            case TOOL_NOT_FOUND:
                return RecoveryStrategy.COMPLETE_REPLAN;
                
            case PARAMETER_ERROR:
                return RecoveryStrategy.REPLAN_FROM_FAILURE;
                
            case TIMEOUT:
                return RecoveryStrategy.RETRY_FAILED_STEP;
                
            case TOOL_EXECUTION_ERROR:
                if (analysis.getSeverity() == FailureSeverity.LOW) {
                    return RecoveryStrategy.SKIP_AND_CONTINUE;
                } else {
                    return RecoveryStrategy.REPLAN_FROM_FAILURE;
                }
                
            case DEPENDENCY_FAILURE:
                return RecoveryStrategy.REPLAN_FROM_FAILURE;
                
            default:
                return RecoveryStrategy.NO_RECOVERY;
        }
    }
    
    /**
     * 重试失败的步骤
     */
    private String retryFailedStep(ExecutionPlan originalPlan, ExecutionResult result, AgentContext context) {
        log.info("尝试重试失败的步骤: {}", result.getFailedStepId());
        
        // 创建只包含失败步骤及其后续步骤的新计划
        ExecutionPlan retryPlan = createRetryPlan(originalPlan, result.getFailedStepId());
        
        ExecutionResult retryResult = executionEngine.execute(retryPlan, context);
        
        if (retryResult.isSuccess()) {
            return "重试成功：\n" + retryResult.getFinalOutput();
        } else {
            return "重试失败：" + retryResult.getError();
        }
    }
    
    /**
     * 跳过失败步骤继续执行
     */
    private String skipAndContinue(ExecutionPlan originalPlan, ExecutionResult result, AgentContext context) {
        log.info("跳过失败步骤继续执行: {}", result.getFailedStepId());
        
        // 创建跳过失败步骤的新计划
        ExecutionPlan continuePlan = createContinuePlan(originalPlan, result.getFailedStepId());
        
        ExecutionResult continueResult = executionEngine.execute(continuePlan, context);
        
        if (continueResult.isSuccess()) {
            return String.format("已跳过失败步骤 '%s' 并继续执行：\n%s", 
                    result.getFailedStepId(), continueResult.getFinalOutput());
        } else {
            return "跳过后继续执行也失败了：" + continueResult.getError();
        }
    }
    
    /**
     * 从失败点重新规划
     */
    private String replanFromFailurePoint(ExecutionPlan originalPlan, FailureAnalysis analysis, AgentContext context) {
        log.info("从失败点重新规划: {}", analysis.getFailedStepId());
        
        try {
            // 构建重新规划的目标描述
            String replanGoal = buildReplanGoal(originalPlan, analysis);
            
            // 生成新的执行计划
            ExecutionPlan newPlan = taskPlanner.planTasks(replanGoal, context);
            
            // 执行新计划
            ExecutionResult newResult = executionEngine.execute(newPlan, context);
            
            if (newResult.isSuccess()) {
                return "重新规划执行成功：\n" + newResult.getFinalOutput();
            } else {
                return "重新规划后仍然失败：" + newResult.getError();
            }
            
        } catch (Exception e) {
            log.error("重新规划失败", e);
            return "重新规划失败：" + e.getMessage();
        }
    }
    
    /**
     * 完全重新规划
     */
    private String completeReplan(ExecutionPlan originalPlan, FailureAnalysis analysis, AgentContext context) {
        log.info("完全重新规划原目标: {}", originalPlan.getGoal());
        
        try {
            // 在原目标基础上添加失败经验
            String adjustedGoal = originalPlan.getGoal() + 
                "\n\n注意：之前的执行失败了，失败原因是：" + analysis.getRootCause() + 
                "\n请调整执行计划避免类似问题。";
            
            // 生成新的执行计划
            ExecutionPlan newPlan = taskPlanner.planTasks(adjustedGoal, context);
            
            // 执行新计划
            ExecutionResult newResult = executionEngine.execute(newPlan, context);
            
            if (newResult.isSuccess()) {
                return "完全重新规划执行成功：\n" + newResult.getFinalOutput();
            } else {
                return "完全重新规划后仍然失败：" + newResult.getError();
            }
            
        } catch (Exception e) {
            log.error("完全重新规划失败", e);
            return "完全重新规划失败：" + e.getMessage();
        }
    }
    
    /**
     * 创建重试计划
     */
    private ExecutionPlan createRetryPlan(ExecutionPlan originalPlan, String failedStepId) {
        // TODO: 实现创建重试计划的逻辑
        // 这里简化实现，返回原计划
        return originalPlan;
    }
    
    /**
     * 创建继续执行计划
     */
    private ExecutionPlan createContinuePlan(ExecutionPlan originalPlan, String failedStepId) {
        // TODO: 实现创建继续执行计划的逻辑
        // 这里简化实现，返回原计划
        return originalPlan;
    }
    
    /**
     * 构建重新规划的目标描述
     */
    private String buildReplanGoal(ExecutionPlan originalPlan, FailureAnalysis analysis) {
        return String.format("%s\n\n重要提示：\n- 之前在步骤 '%s' 失败了\n- 失败原因：%s\n- 请选择不同的方法或工具来达成目标",
                originalPlan.getGoal(),
                analysis.getFailedStepId(),
                analysis.getRootCause());
    }
    
    /**
     * 格式化失败消息
     */
    private String formatFailureMessage(ExecutionPlan originalPlan, ExecutionResult result, FailureAnalysis analysis) {
        return String.format("""
                ## 执行失败 ❌
                
                **目标**: %s
                **失败步骤**: %s
                **失败原因**: %s
                **详细信息**: %s
                
                **建议**: %s
                """,
                originalPlan.getGoal(),
                result.getFailedStepId(),
                analysis.getFailureType(),
                result.getError(),
                analysis.getSuggestion()
        );
    }
}
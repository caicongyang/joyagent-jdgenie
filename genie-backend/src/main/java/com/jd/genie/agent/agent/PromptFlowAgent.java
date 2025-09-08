package com.jd.genie.agent.agent;

import com.jd.genie.agent.enums.AgentState;
import com.jd.genie.agent.promptflow.adaptive.AdaptiveController;
import com.jd.genie.agent.promptflow.engine.ExecutionEngine;
import com.jd.genie.agent.promptflow.model.ExecutionPlan;
import com.jd.genie.agent.promptflow.model.ExecutionResult;
import com.jd.genie.agent.promptflow.planner.TaskPlanner;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * PromptFlow 智能体 v2.0 - AI驱动的任务规划和执行
 */
@Slf4j
public class PromptFlowAgent extends BaseAgent {
    
    private final TaskPlanner taskPlanner;
    private final ExecutionEngine executionEngine;
    private final AdaptiveController adaptiveController;
    
    public PromptFlowAgent(AgentContext context) {
        this.context = context;
        this.taskPlanner = new TaskPlanner();
        this.executionEngine = new ExecutionEngine();
        this.adaptiveController = new AdaptiveController();
        
        // 设置基本信息
        setName("PromptFlow Agent v2");
        setDescription("AI驱动的智能任务规划和执行代理");
        setMaxSteps(1); // 单步执行完成所有任务
    }
    
    @Override
    public String step() {
        try {
            log.info("PromptFlow v2 开始执行，目标: {}", context.getQuery());
            
            // 1. AI 分析用户目标并生成计划
            String userGoal = context.getQuery();
            if (userGoal == null || userGoal.trim().isEmpty()) {
                setState(AgentState.ERROR);
                return "错误：请提供要执行的目标描述";
            }
            
            ExecutionPlan plan = taskPlanner.planTasks(userGoal, context);
            log.info("生成执行计划: {}, 包含 {} 个步骤", plan.getPlanId(), plan.getSteps().size());
            
            // 发送计划生成完成事件
            sendPlanGeneratedEvent(plan);
            
            // 2. 执行计划
            ExecutionResult result = executionEngine.execute(plan, context);
            
            // 3. 处理执行结果
            if (result.isSuccess()) {
                setState(AgentState.FINISHED);
                sendExecutionCompleteEvent(result, true);
                return result.getFinalOutput();
            } else {
                log.warn("计划执行失败，尝试自适应恢复: {}", result.getError());
                
                // 4. 失败时尝试自适应调整
                String recoveryResult = adaptiveController.handleFailure(plan, result, context);
                setState(AgentState.FINISHED);
                sendExecutionCompleteEvent(result, false);
                return recoveryResult;
            }
            
        } catch (Exception e) {
            log.error("PromptFlow v2 执行失败", e);
            setState(AgentState.ERROR);
            return handleError(e);
        }
    }
    
    /**
     * 发送计划生成完成事件
     */
    private void sendPlanGeneratedEvent(ExecutionPlan plan) {
        if (context.getIsStream() != null && context.getIsStream() && context.getPrinter() != null) {
            try {
                Map<String, Object> eventData = new HashMap<>();
                eventData.put("plan_id", plan.getPlanId());
                eventData.put("goal", plan.getGoal());
                eventData.put("steps_count", plan.getSteps().size());
                eventData.put("estimated_duration", plan.getMetadata() != null ? plan.getMetadata().getEstimatedDuration() : 0);
                
                // 添加步骤概览
                if (plan.getSteps() != null) {
                    java.util.List<String> stepDescriptions = plan.getSteps().stream()
                            .map(ExecutionPlan.ExecutionStep::getDescription)
                            .collect(java.util.stream.Collectors.toList());
                    eventData.put("steps_overview", stepDescriptions);
                }
                
                context.getPrinter().send("prompt_flow_plan_generated", eventData);
            } catch (Exception e) {
                log.warn("发送计划生成事件失败", e);
            }
        }
    }
    
    /**
     * 发送执行完成事件
     */
    private void sendExecutionCompleteEvent(ExecutionResult result, boolean success) {
        if (context.getIsStream() != null && context.getIsStream() && context.getPrinter() != null) {
            try {
                Map<String, Object> eventData = new HashMap<>();
                eventData.put("success", success);
                eventData.put("execution_time", result.getStats() != null ? result.getStats().getTotalExecutionTime() : 0);
                
                if (success) {
                    eventData.put("message", "执行成功完成");
                    if (result.getStats() != null) {
                        eventData.put("successful_steps", result.getStats().getSuccessfulSteps());
                        eventData.put("total_retries", result.getStats().getTotalRetries());
                    }
                } else {
                    eventData.put("message", "执行失败，正在尝试恢复");
                    eventData.put("failed_step", result.getFailedStepId());
                    eventData.put("error", result.getError());
                }
                
                context.getPrinter().send("prompt_flow_execution_complete", eventData);
            } catch (Exception e) {
                log.warn("发送执行完成事件失败", e);
            }
        }
    }
    
    /**
     * 处理错误
     */
    private String handleError(Exception e) {
        String errorMessage = String.format("PromptFlow v2 执行失败: %s", e.getMessage());
        log.error(errorMessage, e);
        
        // 发送错误事件
        if (context.getIsStream() != null && context.getIsStream() && context.getPrinter() != null) {
            try {
                Map<String, Object> errorData = new HashMap<>();
                errorData.put("error_type", e.getClass().getSimpleName());
                errorData.put("error_message", e.getMessage());
                errorData.put("timestamp", System.currentTimeMillis());
                
                context.getPrinter().send("prompt_flow_error", errorData);
            } catch (Exception printError) {
                log.warn("发送错误事件失败", printError);
            }
        }
        
        return String.format("""
                ## 执行失败 ❌
                
                **错误类型**: %s
                **错误信息**: %s
                
                **建议**: 请检查您的目标描述是否清晰，或稍后重试。如果问题持续存在，请联系管理员。
                """, 
                e.getClass().getSimpleName(), 
                e.getMessage());
    }
    
    /**
     * 添加导入语句的辅助方法
     */
    private void addImports() {
        // 这个方法用于确保必要的导入
        java.util.HashMap.class.getName();
        java.util.stream.Collectors.class.getName();
    }
}
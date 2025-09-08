package com.jd.genie.agent.promptflow.engine;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.promptflow.model.*;
import com.jd.genie.agent.tool.BaseTool;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.agent.llm.LLM;
import com.jd.genie.agent.dto.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 执行引擎
 */
@Slf4j
@Component
public class ExecutionEngine {
    
    private final ContextManager contextManager;
    private final VariableResolver variableResolver;
    private final ResultProcessor resultProcessor;
    
    public ExecutionEngine() {
        this.contextManager = new ContextManager();
        this.variableResolver = new VariableResolver();
        this.resultProcessor = new ResultProcessor();
    }
    
    /**
     * 执行计划
     */
    public ExecutionResult execute(ExecutionPlan plan, AgentContext agentContext) {
        log.info("开始执行计划: {}, 包含 {} 个步骤", plan.getPlanId(), plan.getSteps().size());
        
        ExecutionContext execContext = contextManager.createExecutionContext(plan, agentContext);
        ExecutionResult.ExecutionStats.ExecutionStatsBuilder statsBuilder = ExecutionResult.ExecutionStats.builder();
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. 按依赖关系排序步骤
            List<ExecutionPlan.ExecutionStep> sortedSteps = sortStepsByDependencies(plan.getSteps());
            
            // 2. 逐步执行
            for (ExecutionPlan.ExecutionStep step : sortedSteps) {
                execContext.setCurrentStepId(step.getId());
                
                // 检查依赖是否满足
                if (!areDependenciesSatisfied(step, execContext)) {
                    log.warn("步骤 {} 的依赖未满足，跳过执行", step.getId());
                    statsBuilder.skippedSteps(statsBuilder.build().getSkippedSteps() + 1);
                    continue;
                }
                
                // 发送步骤开始事件
                sendStepStartEvent(step, agentContext);
                
                // 执行步骤
                StepResult stepResult = executeStepWithRetry(step, execContext, agentContext);
                
                // 更新统计信息
                if (stepResult.isSuccess()) {
                    statsBuilder.successfulSteps(statsBuilder.build().getSuccessfulSteps() + 1);
                } else {
                    statsBuilder.failedSteps(statsBuilder.build().getFailedSteps() + 1);
                    statsBuilder.totalRetries(statsBuilder.build().getTotalRetries() + stepResult.getRetryCount());
                }
                
                // 更新执行上下文
                execContext.addStepResult(step.getId(), stepResult);
                
                // 发送步骤完成事件
                sendStepCompleteEvent(step, stepResult, agentContext);
                
                // 如果非可选步骤失败，终止执行
                if (!stepResult.isSuccess() && !step.isOptional()) {
                    log.error("关键步骤 {} 执行失败: {}", step.getId(), stepResult.getError());
                    return createFailureResult(step.getId(), stepResult.getError(), statsBuilder, startTime);
                }
            }
            
            // 3. 生成最终结果
            String finalOutput = resultProcessor.generateFinalOutput(execContext);
            
            long totalTime = System.currentTimeMillis() - startTime;
            ExecutionResult.ExecutionStats stats = statsBuilder
                    .totalExecutionTime(totalTime)
                    .build();
            
            log.info("计划执行成功，耗时 {}ms", totalTime);
            
            return ExecutionResult.builder()
                    .success(true)
                    .finalOutput(finalOutput)
                    .contextData(execContext.getRuntimeVariables())
                    .stats(stats)
                    .build();
            
        } catch (Exception e) {
            log.error("执行计划时发生异常", e);
            return createFailureResult("execution_error", e.getMessage(), statsBuilder, startTime);
        }
    }
    
    /**
     * 按依赖关系排序步骤
     */
    private List<ExecutionPlan.ExecutionStep> sortStepsByDependencies(List<ExecutionPlan.ExecutionStep> steps) {
        Map<String, ExecutionPlan.ExecutionStep> stepMap = steps.stream()
                .collect(Collectors.toMap(ExecutionPlan.ExecutionStep::getId, step -> step));
        
        List<ExecutionPlan.ExecutionStep> sortedSteps = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        
        for (ExecutionPlan.ExecutionStep step : steps) {
            if (!visited.contains(step.getId())) {
                topologicalSort(step, stepMap, visited, visiting, sortedSteps);
            }
        }
        
        return sortedSteps;
    }
    
    /**
     * 拓扑排序
     */
    private void topologicalSort(ExecutionPlan.ExecutionStep step,
                                Map<String, ExecutionPlan.ExecutionStep> stepMap,
                                Set<String> visited,
                                Set<String> visiting,
                                List<ExecutionPlan.ExecutionStep> result) {
        
        if (visiting.contains(step.getId())) {
            throw new RuntimeException("检测到循环依赖: " + step.getId());
        }
        
        if (visited.contains(step.getId())) {
            return;
        }
        
        visiting.add(step.getId());
        
        // 先处理依赖
        if (step.getDependencies() != null) {
            for (String depId : step.getDependencies()) {
                ExecutionPlan.ExecutionStep depStep = stepMap.get(depId);
                if (depStep != null) {
                    topologicalSort(depStep, stepMap, visited, visiting, result);
                }
            }
        }
        
        visiting.remove(step.getId());
        visited.add(step.getId());
        result.add(step);
    }
    
    /**
     * 检查依赖是否满足
     */
    private boolean areDependenciesSatisfied(ExecutionPlan.ExecutionStep step, ExecutionContext context) {
        if (step.getDependencies() == null || step.getDependencies().isEmpty()) {
            return true;
        }
        
        for (String depId : step.getDependencies()) {
            if (!context.isStepSuccessful(depId)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 带重试的步骤执行
     */
    private StepResult executeStepWithRetry(ExecutionPlan.ExecutionStep step, 
                                          ExecutionContext context, 
                                          AgentContext agentContext) {
        int maxRetries = Math.max(step.getRetryCount(), 0);
        int attempts = 0;
        
        while (attempts <= maxRetries) {
            try {
                StepResult result = executeStep(step, context, agentContext);
                if (result.isSuccess()) {
                    result.setRetryCount(attempts);
                    return result;
                }
                
                // 失败时检查是否需要重试
                if (attempts < maxRetries) {
                    log.warn("步骤 {} 执行失败，进行第 {} 次重试: {}", 
                            step.getId(), attempts + 1, result.getError());
                    attempts++;
                    
                    // 重试前等待一段时间
                    Thread.sleep(1000 * attempts);
                } else {
                    result.setRetryCount(attempts);
                    return result;
                }
                
            } catch (Exception e) {
                log.error("步骤 {} 执行异常", step.getId(), e);
                if (attempts >= maxRetries) {
                    return StepResult.failure(step.getId(), "执行异常: " + e.getMessage());
                }
                attempts++;
            }
        }
        
        return StepResult.failure(step.getId(), "重试次数耗尽");
    }
    
    /**
     * 执行单个步骤
     */
    private StepResult executeStep(ExecutionPlan.ExecutionStep step, 
                                 ExecutionContext context, 
                                 AgentContext agentContext) {
        long stepStartTime = System.currentTimeMillis();
        
        try {
            log.info("执行步骤: {} - {}", step.getId(), step.getDescription());
            
            // 解析参数中的变量引用
            Map<String, Object> resolvedParams = variableResolver.resolveParameters(step.getParams(), context);
            
            Object result;
            
            // 特殊处理 llm_call 工具
            if ("llm_call".equals(step.getTool())) {
                result = executeLLMCall(resolvedParams, agentContext);
            } else {
                // 执行普通工具
                result = executeToolCall(step.getTool(), resolvedParams, agentContext);
            }
            
            long executionTime = System.currentTimeMillis() - stepStartTime;
            
            return StepResult.builder()
                    .stepId(step.getId())
                    .success(true)
                    .output(result)
                    .executionTime(executionTime)
                    .build();
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - stepStartTime;
            log.error("步骤 {} 执行失败", step.getId(), e);
            
            return StepResult.builder()
                    .stepId(step.getId())
                    .success(false)
                    .error(e.getMessage())
                    .executionTime(executionTime)
                    .build();
        }
    }
    
    /**
     * 执行LLM调用
     */
    private Object executeLLMCall(Map<String, Object> params, AgentContext agentContext) throws Exception {
        String prompt = (String) params.get("prompt");
        if (prompt == null) {
            throw new Exception("LLM调用缺少prompt参数");
        }
        
        String model = (String) params.getOrDefault("model", "gpt-4");
        Integer maxTokens = (Integer) params.getOrDefault("max_tokens", 4000);
        
        LLM llm = new LLM(model, "");
        
        CompletableFuture<String> future = llm.ask(
                agentContext,
                Arrays.asList(Message.userMessage(prompt, null)),
                null,
                agentContext.getIsStream(),
                maxTokens.doubleValue()
        );
        
        return future.get();
    }
    
    /**
     * 执行工具调用
     */
    private Object executeToolCall(String toolName, Map<String, Object> params, AgentContext agentContext) throws Exception {
        ToolCollection toolCollection = agentContext.getToolCollection();
        if (toolCollection == null) {
            throw new Exception("工具集合未初始化");
        }
        
        BaseTool tool = toolCollection.getTool(toolName);
        if (tool == null) {
            throw new Exception("工具不存在: " + toolName);
        }
        
        return tool.execute(params);
    }
    
    /**
     * 发送步骤开始事件
     */
    private void sendStepStartEvent(ExecutionPlan.ExecutionStep step, AgentContext agentContext) {
        if (agentContext.getIsStream() && agentContext.getPrinter() != null) {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("step_id", step.getId());
            eventData.put("description", step.getDescription());
            eventData.put("tool", step.getTool());
            eventData.put("status", "started");
            
            agentContext.getPrinter().send("prompt_flow_step", eventData);
        }
    }
    
    /**
     * 发送步骤完成事件
     */
    private void sendStepCompleteEvent(ExecutionPlan.ExecutionStep step, StepResult result, AgentContext agentContext) {
        if (agentContext.getIsStream() && agentContext.getPrinter() != null) {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("step_id", step.getId());
            eventData.put("description", step.getDescription());
            eventData.put("tool", step.getTool());
            eventData.put("status", result.isSuccess() ? "completed" : "failed");
            eventData.put("success", result.isSuccess());
            eventData.put("execution_time", result.getExecutionTime());
            
            if (result.isSuccess()) {
                eventData.put("output_preview", truncateOutput(String.valueOf(result.getOutput())));
            } else {
                eventData.put("error", result.getError());
            }
            
            agentContext.getPrinter().send("prompt_flow_step", eventData);
        }
    }
    
    /**
     * 截断输出预览
     */
    private String truncateOutput(String output) {
        if (output == null) return "";
        if (output.length() <= 200) return output;
        return output.substring(0, 200) + "...";
    }
    
    /**
     * 创建失败结果
     */
    private ExecutionResult createFailureResult(String failedStepId, String error, 
                                              ExecutionResult.ExecutionStats.ExecutionStatsBuilder statsBuilder,
                                              long startTime) {
        long totalTime = System.currentTimeMillis() - startTime;
        ExecutionResult.ExecutionStats stats = statsBuilder
                .totalExecutionTime(totalTime)
                .build();
        
        return ExecutionResult.builder()
                .success(false)
                .failedStepId(failedStepId)
                .error(error)
                .stats(stats)
                .build();
    }
}
package com.jd.genie.agent.promptflow.planner;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.promptflow.model.ExecutionPlan;
import com.jd.genie.agent.promptflow.planner.analyzer.ToolAnalyzer;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.agent.llm.LLM;
import com.jd.genie.agent.dto.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * AI驱动的任务规划器
 */
@Slf4j
@Component
public class TaskPlanner {
    
    private final ToolAnalyzer toolAnalyzer;
    private final ObjectMapper objectMapper;
    
    public TaskPlanner() {
        this.toolAnalyzer = new ToolAnalyzer();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 根据用户目标生成执行计划
     */
    public ExecutionPlan planTasks(String userGoal, AgentContext context) throws Exception {
        log.info("开始为目标生成执行计划: {}", userGoal);
        
        // 1. 分析可用工具能力
        ToolCapabilities toolCapabilities = toolAnalyzer.analyzeAvailableTools(context.getToolCollection());
        
        // 2. 构建规划 prompt
        String planningPrompt = buildPlanningPrompt(userGoal, toolCapabilities);
        
        // 3. 调用 LLM 生成计划
        String planJson = generatePlanWithLLM(planningPrompt, context);
        
        // 4. 解析并验证计划
        ExecutionPlan plan = parsePlan(planJson);
        validatePlan(plan, toolCapabilities);
        
        // 5. 设置计划元数据
        enrichPlanMetadata(plan, userGoal);
        
        log.info("成功生成执行计划，包含 {} 个步骤", plan.getSteps().size());
        return plan;
    }
    
    /**
     * 构建规划提示词
     */
    private String buildPlanningPrompt(String userGoal, ToolCapabilities capabilities) {
        return String.format("""
            你是一个专业的任务规划AI助手。你的职责是根据用户目标生成详细的执行计划。
            
            **用户目标**: %s
            
            **可用工具及其能力**:
            %s
            
            **规划要求**:
            1. 将用户目标分解为具体可执行的步骤
            2. 为每个步骤选择最合适的工具
            3. 明确步骤间的依赖关系和数据传递
            4. 考虑错误处理和重试机制
            5. 优化执行效率和用户体验
            
            **输出格式**: 严格按照以下JSON格式输出，不要包含任何其他文本：
            ```json
            {
              "goal": "用户目标的清晰描述",
              "steps": [
                {
                  "id": "step_1",
                  "description": "步骤的详细描述",
                  "tool": "tool_name",
                  "params": {
                    "param1": "value1",
                    "param2": "{{variable_reference}}"
                  },
                  "dependencies": [],
                  "expectedOutput": "预期输出的描述",
                  "optional": false
                }
              ]
            }
            ```
            
            **变量引用规则**:
            - 使用 {{step_id.result}} 引用前序步骤的输出结果
            - 使用 {{user_input}} 引用用户输入
            - 使用 {{current_date}} 等系统变量
            
            **注意事项**:
            - 步骤ID必须唯一且有意义
            - 工具名称必须在可用工具列表中
            - 依赖关系要合理，避免循环依赖
            - 参数要符合工具的要求
            """, userGoal, capabilities.getDescription());
    }
    
    /**
     * 使用LLM生成计划
     */
    private String generatePlanWithLLM(String planningPrompt, AgentContext context) throws Exception {
        LLM llm = new LLM("gpt-4", "");
        
        CompletableFuture<String> future = llm.ask(
            context,
            Arrays.asList(Message.userMessage(planningPrompt, null)),
            null,
            false,  // 规划阶段不需要流式输出
            4000.0
        );
        
        String response = future.get();
        
        // 提取JSON部分
        String jsonContent = extractJsonFromResponse(response);
        log.debug("LLM生成的计划JSON: {}", jsonContent);
        
        return jsonContent;
    }
    
    /**
     * 从LLM响应中提取JSON内容
     */
    private String extractJsonFromResponse(String response) {
        // 查找JSON代码块
        if (response.contains("```json")) {
            int startIndex = response.indexOf("```json") + 7;
            int endIndex = response.indexOf("```", startIndex);
            if (endIndex > startIndex) {
                return response.substring(startIndex, endIndex).trim();
            }
        }
        
        // 查找JSON对象
        int startIndex = response.indexOf("{");
        int endIndex = response.lastIndexOf("}");
        if (startIndex >= 0 && endIndex > startIndex) {
            return response.substring(startIndex, endIndex + 1);
        }
        
        // 如果都找不到，返回原始响应
        return response;
    }
    
    /**
     * 解析计划JSON
     */
    private ExecutionPlan parsePlan(String planJson) throws Exception {
        try {
            return objectMapper.readValue(planJson, ExecutionPlan.class);
        } catch (Exception e) {
            log.error("解析执行计划JSON失败: {}", planJson, e);
            throw new Exception("解析执行计划失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 验证计划有效性
     */
    private void validatePlan(ExecutionPlan plan, ToolCapabilities capabilities) throws Exception {
        if (plan.getSteps() == null || plan.getSteps().isEmpty()) {
            throw new Exception("执行计划不能为空");
        }
        
        // 验证工具可用性
        for (ExecutionPlan.ExecutionStep step : plan.getSteps()) {
            if (!capabilities.hasToolHasTool(step.getTool())) {
                throw new Exception("工具不可用: " + step.getTool());
            }
        }
        
        // 验证依赖关系
        validateDependencies(plan);
        
        log.info("执行计划验证通过");
    }
    
    /**
     * 验证步骤依赖关系
     */
    private void validateDependencies(ExecutionPlan plan) throws Exception {
        for (ExecutionPlan.ExecutionStep step : plan.getSteps()) {
            if (step.getDependencies() != null) {
                for (String depId : step.getDependencies()) {
                    boolean found = plan.getSteps().stream()
                            .anyMatch(s -> depId.equals(s.getId()));
                    if (!found) {
                        throw new Exception("步骤 " + step.getId() + " 依赖的步骤不存在: " + depId);
                    }
                }
            }
        }
    }
    
    /**
     * 丰富计划元数据
     */
    private void enrichPlanMetadata(ExecutionPlan plan, String userGoal) {
        ExecutionPlan.PlanMetadata metadata = ExecutionPlan.PlanMetadata.builder()
                .createdBy("TaskPlanner")
                .createdTime(System.currentTimeMillis())
                .estimatedSteps(plan.getSteps().size())
                .complexity(estimateComplexity(plan))
                .estimatedDuration(estimateDuration(plan))
                .build();
        
        plan.setMetadata(metadata);
        plan.setPlanId(UUID.randomUUID().toString());
        
        if (plan.getGoal() == null) {
            plan.setGoal(userGoal);
        }
    }
    
    /**
     * 估算计划复杂度
     */
    private String estimateComplexity(ExecutionPlan plan) {
        int stepCount = plan.getSteps().size();
        if (stepCount <= 3) {
            return "Simple";
        } else if (stepCount <= 7) {
            return "Medium";
        } else {
            return "Complex";
        }
    }
    
    /**
     * 估算执行时间(秒)
     */
    private int estimateDuration(ExecutionPlan plan) {
        // 简单估算：每个步骤平均30秒
        return plan.getSteps().size() * 30;
    }
}
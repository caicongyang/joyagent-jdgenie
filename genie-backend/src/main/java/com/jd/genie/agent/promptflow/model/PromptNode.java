package com.jd.genie.agent.promptflow.model;

import com.jd.genie.agent.dto.Message;
import com.jd.genie.agent.llm.LLM;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prompt 节点实现
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Slf4j
public class PromptNode extends FlowNode {
    private String promptContent;
    private String model;
    private Integer maxTokens;
    private Double temperature;
    
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(.*?)\\}\\}");
    
    @Override
    public NodeExecutionResult execute(FlowContext context) {
        try {
            long startTime = System.currentTimeMillis();
            
            // 渲染 prompt 内容
            String renderedPrompt = renderPrompt(promptContent, context);
            
            // 调用 LLM
            LLM llm = new LLM(model != null ? model : "gpt-4", "");
            CompletableFuture<String> future = llm.ask(
                context.getAgentContext(),
                Arrays.asList(Message.userMessage(renderedPrompt, null)),
                null,
                context.getAgentContext().getIsStream(),
                maxTokens != null ? maxTokens.doubleValue() : 4000.0
            );
            
            String response = future.get();
            long executionTime = System.currentTimeMillis() - startTime;
            
            // 更新上下文中的步骤结果
            context.setStepResult(getId(), response);
            context.setStepResult(getId() + "_result", response);
            
            // 构建执行结果
            return NodeExecutionResult.builder()
                .nodeId(getId())
                .success(true)
                .output(response)
                .context(createOutputContext(response, context))
                .nextNodeId(getNextNode())
                .executionTime(executionTime)
                .build();
                
        } catch (Exception e) {
            log.error("PromptNode execution failed for node: {}", getId(), e);
            return NodeExecutionResult.builder()
                .nodeId(getId())
                .success(false)
                .error(e.getMessage())
                .executionTime(0)
                .build();
        }
    }
    
    /**
     * 渲染 prompt 内容，替换变量
     */
    private String renderPrompt(String prompt, FlowContext context) {
        if (prompt == null) {
            return "";
        }
        
        String rendered = prompt;
        
        // 替换变量
        Matcher matcher = VARIABLE_PATTERN.matcher(rendered);
        while (matcher.find()) {
            String varName = matcher.group(1).trim();
            String varValue = resolveVariable(varName, context);
            rendered = rendered.replace("{{" + varName + "}}", varValue);
        }
        
        return rendered;
    }
    
    /**
     * 解析变量值
     */
    private String resolveVariable(String varName, FlowContext context) {
        // 尝试从步骤结果中获取
        if (varName.endsWith("_result")) {
            String stepResult = context.getStepResult(varName);
            if (stepResult != null) {
                return stepResult;
            }
        }
        
        // 尝试从上下文变量中获取
        String value = context.getVariable(varName, null);
        if (value != null) {
            return value;
        }
        
        // 内置变量
        switch (varName) {
            case "current_date":
                return java.time.LocalDate.now().toString();
            case "current_time":
                return java.time.LocalDateTime.now().toString();
            case "user_input":
                return context.getAgentContext().getQuery();
            default:
                return "{{" + varName + "}}"; // 保持原样，表示未找到变量
        }
    }
    
    /**
     * 创建输出上下文
     */
    private FlowContext createOutputContext(String result, FlowContext inputContext) {
        FlowContext outputContext = new FlowContext();
        outputContext.setAgentContext(inputContext.getAgentContext());
        outputContext.setGlobalVariables(inputContext.getGlobalVariables());
        outputContext.setVariables(new HashMap<>(inputContext.getVariables()));
        outputContext.setStepResults(new HashMap<>(inputContext.getStepResults()));
        
        // 添加当前步骤的结果
        outputContext.setStepResult(getId(), result);
        outputContext.setStepResult(getId() + "_result", result);
        
        return outputContext;
    }
}
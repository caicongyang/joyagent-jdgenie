package com.jd.genie.agent.promptflow.model;

import com.jd.genie.agent.tool.BaseTool;
import com.jd.genie.agent.tool.ToolCollection;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工具节点实现
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Slf4j
public class ToolNode extends FlowNode {
    private String toolName;
    private Map<String, Object> toolParameters;
    private boolean async;
    private Integer timeout;
    
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(.*?)\\}\\}");
    
    @Override
    public NodeExecutionResult execute(FlowContext context) {
        try {
            long startTime = System.currentTimeMillis();
            
            // 获取工具
            ToolCollection toolCollection = context.getAgentContext().getToolCollection();
            BaseTool tool = toolCollection.getTool(toolName);
            
            if (tool == null) {
                throw new RuntimeException("Tool not found: " + toolName);
            }
            
            // 准备工具参数
            Map<String, Object> resolvedParams = resolveParameters(toolParameters, context);
            
            // 执行工具
            Object result = tool.execute(resolvedParams);
            String resultStr = result != null ? String.valueOf(result) : "";
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            // 更新上下文中的步骤结果
            context.setStepResult(getId(), resultStr);
            context.setStepResult(getId() + "_result", resultStr);
            
            return NodeExecutionResult.builder()
                .nodeId(getId())
                .success(true)
                .output(resultStr)
                .context(createOutputContext(resultStr, context))
                .nextNodeId(getNextNode())
                .executionTime(executionTime)
                .build();
                
        } catch (Exception e) {
            log.error("ToolNode execution failed for node: {} with tool: {}", getId(), toolName, e);
            return NodeExecutionResult.builder()
                .nodeId(getId())
                .success(false)
                .error(e.getMessage())
                .executionTime(0)
                .build();
        }
    }
    
    /**
     * 解析参数，替换变量引用
     */
    private Map<String, Object> resolveParameters(Map<String, Object> params, FlowContext context) {
        if (params == null) {
            return new HashMap<>();
        }
        
        Map<String, Object> resolved = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                String strValue = (String) value;
                // 解析变量引用
                value = resolveVariableInString(strValue, context);
            }
            resolved.put(entry.getKey(), value);
        }
        
        return resolved;
    }
    
    /**
     * 解析字符串中的变量引用
     */
    private String resolveVariableInString(String input, FlowContext context) {
        if (input == null) {
            return "";
        }
        
        String result = input;
        Matcher matcher = VARIABLE_PATTERN.matcher(result);
        
        while (matcher.find()) {
            String varName = matcher.group(1).trim();
            String varValue = resolveVariable(varName, context);
            result = result.replace("{{" + varName + "}}", varValue);
        }
        
        return result;
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
                return "{{" + varName + "}}"; // 保持原样
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
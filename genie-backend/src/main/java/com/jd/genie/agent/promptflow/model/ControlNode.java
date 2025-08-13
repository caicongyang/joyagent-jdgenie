package com.jd.genie.agent.promptflow.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 控制节点实现（条件分支等）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Slf4j
public class ControlNode extends FlowNode {
    private String condition;
    private String trueNode;
    private String falseNode;
    private ControlType controlType = ControlType.CONDITION;
    
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(.*?)\\}\\}");
    
    @Override
    public NodeExecutionResult execute(FlowContext context) {
        try {
            long startTime = System.currentTimeMillis();
            
            boolean conditionResult = evaluateCondition(condition, context);
            String nextNodeId = conditionResult ? trueNode : falseNode;
            
            // 如果没有指定分支节点，使用默认的下一个节点
            if (nextNodeId == null) {
                nextNodeId = getNextNode();
            }
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            String result = String.format("Condition '%s' evaluated to: %s, next node: %s", 
                                        condition, conditionResult, nextNodeId);
            
            // 更新上下文中的步骤结果
            context.setStepResult(getId(), result);
            context.setStepResult(getId() + "_result", String.valueOf(conditionResult));
            
            return NodeExecutionResult.builder()
                .nodeId(getId())
                .success(true)
                .output(result)
                .context(createOutputContext(result, context))
                .nextNodeId(nextNodeId)
                .executionTime(executionTime)
                .build();
                
        } catch (Exception e) {
            log.error("ControlNode execution failed for node: {}", getId(), e);
            return NodeExecutionResult.builder()
                .nodeId(getId())
                .success(false)
                .error(e.getMessage())
                .nextNodeId(getNextNode())
                .executionTime(0)
                .build();
        }
    }
    
    /**
     * 评估条件表达式
     */
    private boolean evaluateCondition(String conditionExpr, FlowContext context) {
        if (conditionExpr == null || conditionExpr.trim().isEmpty()) {
            return true; // 空条件默认为 true
        }
        
        // 解析条件中的变量
        String resolvedCondition = resolveVariables(conditionExpr, context);
        
        // 简单的条件评估逻辑
        try {
            // 支持基本的比较操作
            if (resolvedCondition.contains("==")) {
                String[] parts = resolvedCondition.split("==");
                if (parts.length == 2) {
                    String left = parts[0].trim().replaceAll("\"", "");
                    String right = parts[1].trim().replaceAll("\"", "");
                    return left.equals(right);
                }
            } else if (resolvedCondition.contains("!=")) {
                String[] parts = resolvedCondition.split("!=");
                if (parts.length == 2) {
                    String left = parts[0].trim().replaceAll("\"", "");
                    String right = parts[1].trim().replaceAll("\"", "");
                    return !left.equals(right);
                }
            } else if (resolvedCondition.contains(">")) {
                String[] parts = resolvedCondition.split(">");
                if (parts.length == 2) {
                    try {
                        double left = Double.parseDouble(parts[0].trim());
                        double right = Double.parseDouble(parts[1].trim());
                        return left > right;
                    } catch (NumberFormatException e) {
                        log.warn("Failed to parse numeric comparison: {}", resolvedCondition);
                        return false;
                    }
                }
            } else if (resolvedCondition.contains("<")) {
                String[] parts = resolvedCondition.split("<");
                if (parts.length == 2) {
                    try {
                        double left = Double.parseDouble(parts[0].trim());
                        double right = Double.parseDouble(parts[1].trim());
                        return left < right;
                    } catch (NumberFormatException e) {
                        log.warn("Failed to parse numeric comparison: {}", resolvedCondition);
                        return false;
                    }
                }
            }
            
            // 简单的布尔值判断
            String booleanValue = resolvedCondition.trim().toLowerCase();
            if ("true".equals(booleanValue)) {
                return true;
            } else if ("false".equals(booleanValue)) {
                return false;
            }
            
            // 检查变量是否存在且不为空
            return !resolvedCondition.trim().isEmpty() && 
                   !resolvedCondition.contains("{{") && 
                   !resolvedCondition.contains("}}");
            
        } catch (Exception e) {
            log.warn("Failed to evaluate condition: {}, error: {}", resolvedCondition, e.getMessage());
            return false;
        }
    }
    
    /**
     * 解析变量引用
     */
    private String resolveVariables(String content, FlowContext context) {
        if (content == null) {
            return "";
        }
        
        String result = content;
        Matcher matcher = VARIABLE_PATTERN.matcher(result);
        
        while (matcher.find()) {
            String varName = matcher.group(1).trim();
            String varValue = resolveVariable(varName, context);
            result = result.replace("{{" + varName + "}}", varValue);
        }
        
        return result;
    }
    
    /**
     * 解析单个变量
     */
    private String resolveVariable(String varName, FlowContext context) {
        // 尝试从步骤结果中获取
        String stepResult = context.getStepResult(varName);
        if (stepResult != null) {
            return stepResult;
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
                return ""; // 条件评估时，未知变量返回空字符串
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


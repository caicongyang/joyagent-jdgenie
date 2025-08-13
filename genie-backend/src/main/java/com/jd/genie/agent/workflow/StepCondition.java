package com.jd.genie.agent.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.function.Function;

/**
 * 步骤执行条件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepCondition {
    
    /**
     * 条件类型
     */
    private ConditionType type;
    
    /**
     * 条件表达式
     */
    private String expression;
    
    /**
     * 条件参数
     */
    private Map<String, Object> parameters;
    
    /**
     * 自定义条件函数
     */
    private Function<WorkflowContext, Boolean> customCondition;
    
    /**
     * 条件类型枚举
     */
    public enum ConditionType {
        ALWAYS,        // 总是执行
        NEVER,         // 永不执行
        VARIABLE,      // 基于变量
        STEP_RESULT,   // 基于步骤结果
        EXPRESSION,    // 表达式
        CUSTOM         // 自定义函数
    }
    
    /**
     * 创建总是执行的条件
     */
    public static StepCondition always() {
        return builder()
                .type(ConditionType.ALWAYS)
                .build();
    }
    
    /**
     * 创建永不执行的条件
     */
    public static StepCondition never() {
        return builder()
                .type(ConditionType.NEVER)
                .build();
    }
    
    /**
     * 创建基于变量的条件
     */
    public static StepCondition variable(String variableName, Object expectedValue) {
        return builder()
                .type(ConditionType.VARIABLE)
                .expression(variableName)
                .parameters(Map.of("expected", expectedValue))
                .build();
    }
    
    /**
     * 创建基于步骤结果的条件
     */
    public static StepCondition stepResult(String stepId, boolean expectedSuccess) {
        return builder()
                .type(ConditionType.STEP_RESULT)
                .expression(stepId)
                .parameters(Map.of("expectedSuccess", expectedSuccess))
                .build();
    }
    
    /**
     * 创建表达式条件
     */
    public static StepCondition expression(String expression) {
        return builder()
                .type(ConditionType.EXPRESSION)
                .expression(expression)
                .build();
    }
    
    /**
     * 创建自定义条件
     */
    public static StepCondition custom(Function<WorkflowContext, Boolean> condition) {
        return builder()
                .type(ConditionType.CUSTOM)
                .customCondition(condition)
                .build();
    }
    
    /**
     * 评估条件
     */
    public boolean evaluate(WorkflowContext context) {
        switch (type) {
            case ALWAYS:
                return true;
            case NEVER:
                return false;
            case VARIABLE:
                return evaluateVariableCondition(context);
            case STEP_RESULT:
                return evaluateStepResultCondition(context);
            case EXPRESSION:
                return evaluateExpression(context);
            case CUSTOM:
                return customCondition != null ? customCondition.apply(context) : true;
            default:
                return true;
        }
    }
    
    /**
     * 评估变量条件
     */
    private boolean evaluateVariableCondition(WorkflowContext context) {
        if (expression == null || parameters == null) {
            return true;
        }
        
        Object actualValue = context.getVariable(expression);
        Object expectedValue = parameters.get("expected");
        
        if (actualValue == null && expectedValue == null) {
            return true;
        }
        
        return actualValue != null && actualValue.equals(expectedValue);
    }
    
    /**
     * 评估步骤结果条件
     */
    private boolean evaluateStepResultCondition(WorkflowContext context) {
        if (expression == null || parameters == null) {
            return true;
        }
        
        StepStatus stepStatus = context.getStepStatus(expression);
        Boolean expectedSuccess = (Boolean) parameters.get("expectedSuccess");
        
        if (expectedSuccess == null) {
            return stepStatus == StepStatus.COMPLETED;
        }
        
        if (expectedSuccess) {
            return stepStatus == StepStatus.COMPLETED;
        } else {
            return stepStatus == StepStatus.FAILED;
        }
    }
    
    /**
     * 评估表达式条件
     */
    private boolean evaluateExpression(WorkflowContext context) {
        if (expression == null || expression.trim().isEmpty()) {
            return true;
        }
        
        // 简单的表达式评估实现
        // 实际应用中可以集成更强大的表达式引擎，如SpEL、MVEL等
        
        // 支持简单的变量比较
        if (expression.contains("==")) {
            String[] parts = expression.split("==");
            if (parts.length == 2) {
                String varName = parts[0].trim();
                String expectedValue = parts[1].trim().replace("'", "").replace("\"", "");
                Object actualValue = context.getVariable(varName);
                return actualValue != null && actualValue.toString().equals(expectedValue);
            }
        }
        
        // 支持简单的布尔变量
        Object value = context.getVariable(expression);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        
        // 默认返回true
        return true;
    }
}
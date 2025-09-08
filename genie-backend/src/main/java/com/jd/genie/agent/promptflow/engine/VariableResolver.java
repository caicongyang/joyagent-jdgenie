package com.jd.genie.agent.promptflow.engine;

import com.jd.genie.agent.promptflow.model.ExecutionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 变量解析器
 */
@Slf4j
@Component
public class VariableResolver {
    
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");
    
    /**
     * 解析参数中的变量引用
     */
    public Map<String, Object> resolveParameters(Map<String, Object> params, ExecutionContext context) {
        if (params == null) {
            return new HashMap<>();
        }
        
        Map<String, Object> resolved = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            Object value = entry.getValue();
            Object resolvedValue = resolveValue(value, context);
            resolved.put(entry.getKey(), resolvedValue);
        }
        
        return resolved;
    }
    
    /**
     * 解析单个值
     */
    @SuppressWarnings("unchecked")
    private Object resolveValue(Object value, ExecutionContext context) {
        if (value instanceof String) {
            return resolveVariables((String) value, context);
        } else if (value instanceof Map) {
            return resolveParameters((Map<String, Object>) value, context);
        } else {
            return value;
        }
    }
    
    /**
     * 解析字符串中的变量引用
     */
    private String resolveVariables(String template, ExecutionContext context) {
        if (template == null) {
            return null;
        }
        
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String varRef = matcher.group(1).trim();
            String value = getVariableValue(varRef, context);
            
            // 替换变量引用，如果值为null则保持原样
            if (value != null) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(value));
            } else {
                log.warn("变量引用未找到对应值: {}", varRef);
                matcher.appendReplacement(result, Matcher.quoteReplacement("{{" + varRef + "}}"));
            }
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * 获取变量值
     */
    private String getVariableValue(String varRef, ExecutionContext context) {
        // 处理点号分隔的复合引用，如 step_1.result
        if (varRef.contains(".")) {
            String[] parts = varRef.split("\\.", 2);
            String baseName = parts[0];
            String fieldName = parts[1];
            
            // 检查是否是步骤结果引用
            if (context.isStepExecuted(baseName)) {
                return getStepFieldValue(baseName, fieldName, context);
            }
        }
        
        // 直接变量引用
        Object value = context.getVariable(varRef);
        return value != null ? String.valueOf(value) : null;
    }
    
    /**
     * 获取步骤字段值
     */
    private String getStepFieldValue(String stepId, String fieldName, ExecutionContext context) {
        switch (fieldName) {
            case "result":
                Object result = context.getStepResult(stepId).getOutput();
                return result != null ? String.valueOf(result) : "";
                
            case "success":
                return String.valueOf(context.isStepSuccessful(stepId));
                
            case "error":
                String error = context.getStepResult(stepId).getError();
                return error != null ? error : "";
                
            case "execution_time":
                long execTime = context.getStepResult(stepId).getExecutionTime();
                return String.valueOf(execTime);
                
            default:
                log.warn("未知的步骤字段: {}.{}", stepId, fieldName);
                return null;
        }
    }
}
package com.jd.genie.agent.promptflow.model;

import com.jd.genie.agent.agent.AgentContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 流程上下文
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowContext {
    private AgentContext agentContext;
    @Builder.Default
    private Map<String, Object> variables = new HashMap<>();
    @Builder.Default
    private Map<String, Object> globalVariables = new HashMap<>();
    @Builder.Default
    private Map<String, String> stepResults = new HashMap<>();
    private String currentUser;
    private String currentDate;
    
    /**
     * 获取变量值
     */
    public String getVariable(String key, String defaultValue) {
        Object value = variables.get(key);
        if (value != null) {
            return String.valueOf(value);
        }
        
        value = globalVariables.get(key);
        if (value != null) {
            return String.valueOf(value);
        }
        
        return defaultValue;
    }
    
    /**
     * 设置变量
     */
    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }
    
    /**
     * 获取步骤结果
     */
    public String getStepResult(String stepKey) {
        return stepResults.get(stepKey);
    }
    
    /**
     * 设置步骤结果
     */
    public void setStepResult(String stepKey, String result) {
        stepResults.put(stepKey, result);
    }
    
    /**
     * 合并上下文
     */
    public void merge(FlowContext other) {
        if (other != null) {
            if (other.variables != null) {
                this.variables.putAll(other.variables);
            }
            if (other.stepResults != null) {
                this.stepResults.putAll(other.stepResults);
            }
        }
    }
}
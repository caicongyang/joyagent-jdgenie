package com.jd.genie.agent.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工作流触发器
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowTrigger {
    
    /**
     * 触发器类型
     */
    private TriggerType type;
    
    /**
     * 触发条件
     */
    private String condition;
    
    /**
     * 触发参数
     */
    private Map<String, Object> parameters;
    
    /**
     * 触发器类型枚举
     */
    public enum TriggerType {
        MANUAL,      // 手动触发
        SCHEDULED,   // 定时触发
        EVENT,       // 事件触发
        API,         // API触发
        CONDITION    // 条件触发
    }
    
    /**
     * 创建手动触发器
     */
    public static WorkflowTrigger manual() {
        return builder()
                .type(TriggerType.MANUAL)
                .build();
    }
    
    /**
     * 创建事件触发器
     */
    public static WorkflowTrigger event(String eventCondition) {
        return builder()
                .type(TriggerType.EVENT)
                .condition(eventCondition)
                .build();
    }
    
    /**
     * 创建条件触发器
     */
    public static WorkflowTrigger condition(String conditionExpression) {
        return builder()
                .type(TriggerType.CONDITION)
                .condition(conditionExpression)
                .build();
    }
    
    /**
     * 检查是否应该触发
     */
    public boolean shouldTrigger(WorkflowContext context) {
        switch (type) {
            case MANUAL:
                return true;
            case CONDITION:
                return evaluateCondition(context);
            default:
                return false;
        }
    }
    
    /**
     * 评估触发条件
     */
    private boolean evaluateCondition(WorkflowContext context) {
        if (condition == null || condition.isEmpty()) {
            return true;
        }
        // 简单的条件评估，实际应用中可以使用更复杂的表达式引擎
        // 这里只做示例实现
        return true;
    }
}
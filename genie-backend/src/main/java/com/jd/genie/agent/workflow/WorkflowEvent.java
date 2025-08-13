package com.jd.genie.agent.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 工作流事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowEvent {
    
    /**
     * 事件ID
     */
    private String eventId;
    
    /**
     * 工作流ID
     */
    private String workflowId;
    
    /**
     * 工作流实例ID
     */
    private String instanceId;
    
    /**
     * 步骤ID
     */
    private String stepId;
    
    /**
     * 事件类型
     */
    private String eventType;
    
    /**
     * 事件数据
     */
    private Object eventData;
    
    /**
     * 时间戳
     */
    private Date timestamp;
    
    /**
     * 事件级别
     */
    @Builder.Default
    private EventLevel level = EventLevel.INFO;
    
    /**
     * 事件级别枚举
     */
    public enum EventLevel {
        DEBUG, INFO, WARN, ERROR
    }
    
    /**
     * 创建调试事件
     */
    public static WorkflowEvent debug(String workflowId, String stepId, String eventType, Object data) {
        return builder()
                .workflowId(workflowId)
                .stepId(stepId)
                .eventType(eventType)
                .eventData(data)
                .level(EventLevel.DEBUG)
                .timestamp(new Date())
                .build();
    }
    
    /**
     * 创建信息事件
     */
    public static WorkflowEvent info(String workflowId, String stepId, String eventType, Object data) {
        return builder()
                .workflowId(workflowId)
                .stepId(stepId)
                .eventType(eventType)
                .eventData(data)
                .level(EventLevel.INFO)
                .timestamp(new Date())
                .build();
    }
    
    /**
     * 创建警告事件
     */
    public static WorkflowEvent warn(String workflowId, String stepId, String eventType, Object data) {
        return builder()
                .workflowId(workflowId)
                .stepId(stepId)
                .eventType(eventType)
                .eventData(data)
                .level(EventLevel.WARN)
                .timestamp(new Date())
                .build();
    }
    
    /**
     * 创建错误事件
     */
    public static WorkflowEvent error(String workflowId, String stepId, String eventType, Object data) {
        return builder()
                .workflowId(workflowId)
                .stepId(stepId)
                .eventType(eventType)
                .eventData(data)
                .level(EventLevel.ERROR)
                .timestamp(new Date())
                .build();
    }
}
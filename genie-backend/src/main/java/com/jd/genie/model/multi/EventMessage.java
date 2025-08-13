package com.jd.genie.model.multi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventMessage implements Serializable {
    /**
     * Set task ID
     */
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }
    
    /**
     * Set message type
     */
    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }
    
    /**
     * Set message order
     */
    public void setMessageOrder(int messageOrder) {
        this.messageOrder = messageOrder;
    }
    
    /**
     * Set message order with Integer
     */
    public void setMessageOrder(Integer messageOrder) {
        this.messageOrder = messageOrder;
    }
    
    /**
     * Set result map
     */
    public void setResultMap(Object resultMap) {
        this.resultMap = resultMap;
    }
    
    /**
     * Get result map
     */
    public Object getResultMap() {
        return resultMap;
    }
    private static final long serialVersionUID = 1L;

    private String taskId;
    private Integer taskOrder;
    private String messageId;
    private String messageType;// task、tool、html、file、
    private Integer messageOrder;
    private Object resultMap;
}

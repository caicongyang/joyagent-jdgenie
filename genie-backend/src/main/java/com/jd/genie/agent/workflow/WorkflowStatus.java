package com.jd.genie.agent.workflow;

/**
 * 工作流状态枚举
 */
public enum WorkflowStatus {
    
    /**
     * 已创建
     */
    CREATED("created", "已创建"),
    
    /**
     * 执行中
     */
    RUNNING("running", "执行中"),
    
    /**
     * 暂停
     */
    SUSPENDED("suspended", "暂停"),
    
    /**
     * 已完成
     */
    COMPLETED("completed", "已完成"),
    
    /**
     * 失败
     */
    FAILED("failed", "失败"),
    
    /**
     * 已取消
     */
    CANCELLED("cancelled", "已取消");
    
    private final String code;
    private final String description;
    
    WorkflowStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 根据代码获取状态
     */
    public static WorkflowStatus fromCode(String code) {
        for (WorkflowStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid workflow status code: " + code);
    }
    
    /**
     * 是否为终态
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
    
    /**
     * 是否为活跃状态
     */
    public boolean isActive() {
        return this == RUNNING;
    }
}
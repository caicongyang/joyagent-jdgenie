package com.jd.genie.agent.workflow;

/**
 * 步骤状态枚举
 */
public enum StepStatus {
    
    /**
     * 等待执行
     */
    PENDING("pending", "等待执行"),
    
    /**
     * 执行中
     */
    RUNNING("running", "执行中"),
    
    /**
     * 已完成
     */
    COMPLETED("completed", "已完成"),
    
    /**
     * 执行失败
     */
    FAILED("failed", "执行失败"),
    
    /**
     * 已跳过
     */
    SKIPPED("skipped", "已跳过"),
    
    /**
     * 被阻塞
     */
    BLOCKED("blocked", "被阻塞");
    
    private final String code;
    private final String description;
    
    StepStatus(String code, String description) {
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
    public static StepStatus fromCode(String code) {
        for (StepStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid step status code: " + code);
    }
    
    /**
     * 是否为终态
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == SKIPPED;
    }
    
    /**
     * 是否为活跃状态
     */
    public boolean isActive() {
        return this == RUNNING;
    }
    
    /**
     * 是否可以重试
     */
    public boolean canRetry() {
        return this == FAILED;
    }
}
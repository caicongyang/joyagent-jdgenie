package com.jd.genie.agent.promptflow.adaptive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 失败分析结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailureAnalysis {
    
    /**
     * 失败的步骤ID
     */
    private String failedStepId;
    
    /**
     * 失败类型
     */
    private FailureType failureType;
    
    /**
     * 失败严重程度
     */
    private FailureSeverity severity;
    
    /**
     * 根本原因
     */
    private String rootCause;
    
    /**
     * 改进建议
     */
    private String suggestion;
    
    /**
     * 原始错误信息
     */
    private String originalError;
    
    /**
     * 获取分析摘要
     */
    public String getSummary() {
        return String.format("步骤 %s 发生 %s 级别的 %s", 
                failedStepId, severity, failureType);
    }
}

/**
 * 失败类型枚举
 */
enum FailureType {
    TOOL_NOT_FOUND("工具未找到"),
    PARAMETER_ERROR("参数错误"),
    TIMEOUT("执行超时"),
    DEPENDENCY_FAILURE("依赖失败"),
    PERMISSION_ERROR("权限错误"),
    NETWORK_ERROR("网络错误"),
    TOOL_EXECUTION_ERROR("工具执行错误"),
    UNKNOWN("未知错误");
    
    private final String description;
    
    FailureType(String description) {
        this.description = description;
    }
    
    @Override
    public String toString() {
        return description;
    }
}

/**
 * 失败严重程度枚举
 */
enum FailureSeverity {
    LOW("低"),
    MEDIUM("中"),
    HIGH("高");
    
    private final String description;
    
    FailureSeverity(String description) {
        this.description = description;
    }
    
    @Override
    public String toString() {
        return description;
    }
}

/**
 * 恢复策略枚举
 */
enum RecoveryStrategy {
    RETRY_FAILED_STEP("重试失败步骤"),
    SKIP_AND_CONTINUE("跳过并继续"),
    REPLAN_FROM_FAILURE("从失败点重新规划"),
    COMPLETE_REPLAN("完全重新规划"),
    NO_RECOVERY("无法恢复");
    
    private final String description;
    
    RecoveryStrategy(String description) {
        this.description = description;
    }
    
    @Override
    public String toString() {
        return description;
    }
}
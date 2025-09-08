package com.jd.genie.agent.promptflow.adaptive;

import com.jd.genie.agent.promptflow.model.ExecutionPlan;
import com.jd.genie.agent.promptflow.model.ExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 错误分析器
 */
@Slf4j
@Component
public class ErrorAnalyzer {
    
    /**
     * 分析执行失败
     */
    public FailureAnalysis analyze(ExecutionResult result, ExecutionPlan plan) {
        String error = result.getError();
        String failedStepId = result.getFailedStepId();
        
        FailureType failureType = classifyFailureType(error);
        FailureSeverity severity = assessSeverity(failureType, error);
        String rootCause = identifyRootCause(error, failureType);
        String suggestion = generateSuggestion(failureType, error);
        
        return FailureAnalysis.builder()
                .failedStepId(failedStepId)
                .failureType(failureType)
                .severity(severity)
                .rootCause(rootCause)
                .suggestion(suggestion)
                .originalError(error)
                .build();
    }
    
    /**
     * 分类失败类型
     */
    private FailureType classifyFailureType(String error) {
        if (error == null) {
            return FailureType.UNKNOWN;
        }
        
        String lowerError = error.toLowerCase();
        
        if (lowerError.contains("tool not found") || lowerError.contains("工具不存在")) {
            return FailureType.TOOL_NOT_FOUND;
        } else if (lowerError.contains("timeout") || lowerError.contains("超时")) {
            return FailureType.TIMEOUT;
        } else if (lowerError.contains("parameter") || lowerError.contains("参数")) {
            return FailureType.PARAMETER_ERROR;
        } else if (lowerError.contains("dependency") || lowerError.contains("依赖")) {
            return FailureType.DEPENDENCY_FAILURE;
        } else if (lowerError.contains("permission") || lowerError.contains("权限")) {
            return FailureType.PERMISSION_ERROR;
        } else if (lowerError.contains("network") || lowerError.contains("网络")) {
            return FailureType.NETWORK_ERROR;
        } else {
            return FailureType.TOOL_EXECUTION_ERROR;
        }
    }
    
    /**
     * 评估失败严重程度
     */
    private FailureSeverity assessSeverity(FailureType failureType, String error) {
        switch (failureType) {
            case TOOL_NOT_FOUND:
            case PERMISSION_ERROR:
                return FailureSeverity.HIGH;
                
            case DEPENDENCY_FAILURE:
            case PARAMETER_ERROR:
                return FailureSeverity.MEDIUM;
                
            case TIMEOUT:
            case NETWORK_ERROR:
            case TOOL_EXECUTION_ERROR:
                return FailureSeverity.LOW;
                
            default:
                return FailureSeverity.MEDIUM;
        }
    }
    
    /**
     * 识别根本原因
     */
    private String identifyRootCause(String error, FailureType failureType) {
        switch (failureType) {
            case TOOL_NOT_FOUND:
                return "指定的工具在当前环境中不可用";
                
            case PARAMETER_ERROR:
                return "工具调用参数不正确或缺失必要参数";
                
            case TIMEOUT:
                return "操作执行时间超出了预设的超时限制";
                
            case DEPENDENCY_FAILURE:
                return "前置步骤执行失败，导致当前步骤无法正常执行";
                
            case PERMISSION_ERROR:
                return "缺少执行该操作所需的权限";
                
            case NETWORK_ERROR:
                return "网络连接问题导致操作失败";
                
            case TOOL_EXECUTION_ERROR:
                return "工具执行过程中遇到内部错误";
                
            default:
                return error != null ? error : "未知错误";
        }
    }
    
    /**
     * 生成改进建议
     */
    private String generateSuggestion(FailureType failureType, String error) {
        switch (failureType) {
            case TOOL_NOT_FOUND:
                return "请检查工具名称是否正确，或选择其他可用的工具";
                
            case PARAMETER_ERROR:
                return "请检查工具参数是否正确，确保所有必需参数都已提供";
                
            case TIMEOUT:
                return "可以尝试增加超时时间，或将任务分解为更小的步骤";
                
            case DEPENDENCY_FAILURE:
                return "请检查前置步骤的执行状态，修复依赖问题后重试";
                
            case PERMISSION_ERROR:
                return "请确保有足够的权限执行该操作";
                
            case NETWORK_ERROR:
                return "请检查网络连接，稍后重试";
                
            case TOOL_EXECUTION_ERROR:
                return "可以尝试使用不同的参数或替代工具";
                
            default:
                return "请检查错误信息并尝试调整执行策略";
        }
    }
}
package com.jd.genie.handler;

import com.jd.genie.agent.enums.ResponseTypeEnum;
import com.jd.genie.model.multi.EventResult;
import com.jd.genie.model.req.AgentRequest;
import com.jd.genie.model.response.AgentResponse;
import com.jd.genie.model.response.GptProcessResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流智能体响应处理器
 */
@Slf4j
@Component
public class WorkflowAgentResponseHandler extends BaseAgentResponseHandler {
    
    @Override
    public GptProcessResult handle(AgentRequest request, AgentResponse response,
                                 List<AgentResponse> responseList, EventResult eventResult) {
        
        try {
            log.info("{} Processing workflow agent response", request.getRequestId());
            
            GptProcessResult result = new GptProcessResult();
            result.setReqId(request.getRequestId());
            
            // 处理不同类型的响应
            switch (response.getType()) {
                case "workflow_start":
                    return handleWorkflowStart(request, response, result);
                    
                case "workflow_progress":
                    return handleWorkflowProgress(request, response, result, responseList);
                    
                case "workflow_step":
                    return handleWorkflowStep(request, response, result, responseList);
                    
                case "workflow_complete":
                    return handleWorkflowComplete(request, response, result, responseList);
                    
                case "workflow_error":
                    return handleWorkflowError(request, response, result);
                    
                case "text":
                default:
                    return handleTextResponse(request, response, result, responseList);
            }
            
        } catch (Exception e) {
            log.error("{} Error handling workflow response: {}", request.getRequestId(), e.getMessage(), e);
            return createErrorResult(request, "Response handling error: " + e.getMessage());
        }
    }
    
    /**
     * 处理工作流开始响应
     */
    private GptProcessResult handleWorkflowStart(AgentRequest request, AgentResponse response, 
                                               GptProcessResult result) {
        log.info("{} Workflow started", request.getRequestId());
        
        result.setStatus("success");
        result.setFinished(false);
        result.setResponseType(ResponseTypeEnum.text.name());
        result.setPackageType("workflow_start");
        
        // 解析工作流信息
        Map<String, Object> workflowInfo = parseWorkflowInfo(response);
        result.setResultMap(workflowInfo);
        
        String message = String.format("🚀 工作流已开始执行\n\n" +
                "📋 工作流名称: %s\n" +
                "📝 描述: %s\n" +
                "🔢 总步骤数: %d\n" +
                "⏱️ 预估时间: %s\n\n" +
                "正在执行中，请稍候...",
                workflowInfo.get("name"),
                workflowInfo.get("description"),
                workflowInfo.get("totalSteps"),
                workflowInfo.get("estimatedTime"));
        
        result.setResponse(message);
        result.setResponseAll(message);
        
        return result;
    }
    
    /**
     * 处理工作流进度响应
     */
    private GptProcessResult handleWorkflowProgress(AgentRequest request, AgentResponse response, 
                                                  GptProcessResult result, List<AgentResponse> responseList) {
        log.info("{} Workflow progress update", request.getRequestId());
        
        result.setStatus("success");
        result.setFinished(false);
        result.setResponseType(ResponseTypeEnum.text.name());
        result.setPackageType("workflow_progress");
        
        // 解析进度信息
        Map<String, Object> progressInfo = parseProgressInfo(response);
        result.setResultMap(progressInfo);
        
        double progress = (Double) progressInfo.getOrDefault("progress", 0.0);
        String currentStep = (String) progressInfo.getOrDefault("currentStep", "未知");
        int completedSteps = (Integer) progressInfo.getOrDefault("completedSteps", 0);
        int totalSteps = (Integer) progressInfo.getOrDefault("totalSteps", 0);
        
        String message = String.format("⏳ 执行进度: %.1f%%\n" +
                "📍 当前步骤: %s\n" +
                "✅ 已完成: %d/%d 步骤",
                progress * 100, currentStep, completedSteps, totalSteps);
        
        result.setResponse(message);
        result.setResponseAll(buildProgressiveResponse(responseList, message));
        
        return result;
    }
    
    /**
     * 处理工作流步骤响应
     */
    private GptProcessResult handleWorkflowStep(AgentRequest request, AgentResponse response, 
                                              GptProcessResult result, List<AgentResponse> responseList) {
        log.info("{} Workflow step completed", request.getRequestId());
        
        result.setStatus("success");
        result.setFinished(false);
        result.setResponseType(ResponseTypeEnum.text.name());
        result.setPackageType("workflow_step");
        
        // 解析步骤信息
        Map<String, Object> stepInfo = parseStepInfo(response);
        result.setResultMap(stepInfo);
        
        String stepName = (String) stepInfo.getOrDefault("stepName", "未知步骤");
        String stepResult = (String) stepInfo.getOrDefault("stepResult", "");
        boolean success = (Boolean) stepInfo.getOrDefault("success", true);
        
        String statusIcon = success ? "✅" : "❌";
        String message = String.format("%s 步骤完成: %s\n\n📋 执行结果:\n%s\n\n---",
                statusIcon, stepName, stepResult);
        
        result.setResponse(message);
        result.setResponseAll(buildProgressiveResponse(responseList, message));
        
        return result;
    }
    
    /**
     * 处理工作流完成响应
     */
    private GptProcessResult handleWorkflowComplete(AgentRequest request, AgentResponse response, 
                                                  GptProcessResult result, List<AgentResponse> responseList) {
        log.info("{} Workflow completed", request.getRequestId());
        
        result.setStatus("success");
        result.setFinished(true);
        result.setResponseType(ResponseTypeEnum.text.name());
        result.setPackageType("workflow_complete");
        
        // 解析完成信息
        Map<String, Object> completionInfo = parseCompletionInfo(response);
        result.setResultMap(completionInfo);
        
        boolean success = (Boolean) completionInfo.getOrDefault("success", true);
        String finalResult = (String) completionInfo.getOrDefault("result", "");
        int totalSteps = (Integer) completionInfo.getOrDefault("totalSteps", 0);
        long executionTime = (Long) completionInfo.getOrDefault("executionTime", 0L);
        
        String statusIcon = success ? "🎉" : "⚠️";
        String statusText = success ? "工作流执行成功!" : "工作流执行完成（部分成功）";
        
        String message = String.format("%s %s\n\n" +
                "📊 执行统计:\n" +
                "• 总步骤数: %d\n" +
                "• 执行时间: %.2f 秒\n\n" +
                "📋 最终结果:\n%s",
                statusIcon, statusText, totalSteps, executionTime / 1000.0, finalResult);
        
        result.setResponse(message);
        result.setResponseAll(buildProgressiveResponse(responseList, message));
        
        return result;
    }
    
    /**
     * 处理工作流错误响应
     */
    private GptProcessResult handleWorkflowError(AgentRequest request, AgentResponse response, 
                                               GptProcessResult result) {
        log.error("{} Workflow error occurred", request.getRequestId());
        
        result.setStatus("failed");
        result.setFinished(true);
        result.setResponseType(ResponseTypeEnum.text.name());
        result.setPackageType("workflow_error");
        
        String errorMessage = response.getContent() != null ? response.getContent() : "未知错误";
        String message = String.format("❌ 工作流执行失败\n\n🔍 错误信息:\n%s\n\n💡 请检查输入或联系管理员",
                errorMessage);
        
        result.setResponse(message);
        result.setResponseAll(message);
        result.setErrorMsg(errorMessage);
        
        return result;
    }
    
    /**
     * 处理文本响应
     */
    private GptProcessResult handleTextResponse(AgentRequest request, AgentResponse response, 
                                              GptProcessResult result, List<AgentResponse> responseList) {
        
        result.setStatus("success");
        result.setResponseType(ResponseTypeEnum.text.name());
        result.setPackageType("text");
        
        String content = response.getContent() != null ? response.getContent() : "";
        
        // 判断是否为最终响应
        boolean isFinished = isWorkflowFinished(content, responseList);
        result.setFinished(isFinished);
        
        result.setResponse(content);
        result.setResponseAll(buildProgressiveResponse(responseList, content));
        
        return result;
    }
    
    /**
     * 解析工作流信息
     */
    private Map<String, Object> parseWorkflowInfo(AgentResponse response) {
        Map<String, Object> info = new HashMap<>();
        
        try {
            if (response.getData() != null && response.getData() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.getData();
                
                info.put("name", data.getOrDefault("workflowName", "未命名工作流"));
                info.put("description", data.getOrDefault("description", "暂无描述"));
                info.put("totalSteps", data.getOrDefault("totalSteps", 0));
                info.put("estimatedTime", data.getOrDefault("estimatedTime", "未知"));
            } else {
                // 默认值
                info.put("name", "智能工作流");
                info.put("description", "系统自动生成的工作流");
                info.put("totalSteps", 3);
                info.put("estimatedTime", "约1-2分钟");
            }
        } catch (Exception e) {
            log.warn("Error parsing workflow info: {}", e.getMessage());
            // 使用默认值
            info.put("name", "智能工作流");
            info.put("description", "系统自动生成的工作流");
            info.put("totalSteps", 3);
            info.put("estimatedTime", "约1-2分钟");
        }
        
        return info;
    }
    
    /**
     * 解析进度信息
     */
    private Map<String, Object> parseProgressInfo(AgentResponse response) {
        Map<String, Object> info = new HashMap<>();
        
        try {
            if (response.getData() != null && response.getData() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.getData();
                
                info.put("progress", data.getOrDefault("progress", 0.0));
                info.put("currentStep", data.getOrDefault("currentStep", "执行中"));
                info.put("completedSteps", data.getOrDefault("completedSteps", 0));
                info.put("totalSteps", data.getOrDefault("totalSteps", 0));
            }
        } catch (Exception e) {
            log.warn("Error parsing progress info: {}", e.getMessage());
        }
        
        return info;
    }
    
    /**
     * 解析步骤信息
     */
    private Map<String, Object> parseStepInfo(AgentResponse response) {
        Map<String, Object> info = new HashMap<>();
        
        try {
            if (response.getData() != null && response.getData() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.getData();
                
                info.put("stepName", data.getOrDefault("stepName", "未知步骤"));
                info.put("stepResult", data.getOrDefault("stepResult", ""));
                info.put("success", data.getOrDefault("success", true));
            }
        } catch (Exception e) {
            log.warn("Error parsing step info: {}", e.getMessage());
        }
        
        return info;
    }
    
    /**
     * 解析完成信息
     */
    private Map<String, Object> parseCompletionInfo(AgentResponse response) {
        Map<String, Object> info = new HashMap<>();
        
        try {
            if (response.getData() != null && response.getData() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.getData();
                
                info.put("success", data.getOrDefault("success", true));
                info.put("result", data.getOrDefault("result", ""));
                info.put("totalSteps", data.getOrDefault("totalSteps", 0));
                info.put("executionTime", data.getOrDefault("executionTime", 0L));
            } else {
                // 从响应内容中提取信息
                String content = response.getContent() != null ? response.getContent() : "";
                info.put("success", !content.contains("失败") && !content.contains("错误"));
                info.put("result", content);
                info.put("totalSteps", 0);
                info.put("executionTime", 0L);
            }
        } catch (Exception e) {
            log.warn("Error parsing completion info: {}", e.getMessage());
        }
        
        return info;
    }
    
    /**
     * 判断工作流是否完成
     */
    private boolean isWorkflowFinished(String content, List<AgentResponse> responseList) {
        if (content == null) return false;
        
        // 检查完成标志
        String lowerContent = content.toLowerCase();
        if (lowerContent.contains("workflow completed") || 
            lowerContent.contains("工作流完成") ||
            lowerContent.contains("execution completed") ||
            lowerContent.contains("✅") && lowerContent.contains("成功")) {
            return true;
        }
        
        // 检查错误标志
        if (lowerContent.contains("workflow failed") || 
            lowerContent.contains("工作流失败") ||
            lowerContent.contains("execution failed") ||
            lowerContent.contains("❌")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 构建渐进式响应
     */
    private String buildProgressiveResponse(List<AgentResponse> responseList, String currentResponse) {
        if (responseList == null || responseList.isEmpty()) {
            return currentResponse;
        }
        
        StringBuilder fullResponse = new StringBuilder();
        for (AgentResponse response : responseList) {
            if (response.getContent() != null && !response.getContent().trim().isEmpty()) {
                fullResponse.append(response.getContent()).append("\n");
            }
        }
        
        // 添加当前响应
        if (currentResponse != null && !currentResponse.trim().isEmpty()) {
            fullResponse.append(currentResponse);
        }
        
        return fullResponse.toString();
    }
    
    /**
     * 创建错误结果
     */
    private GptProcessResult createErrorResult(AgentRequest request, String errorMessage) {
        GptProcessResult result = new GptProcessResult();
        result.setReqId(request.getRequestId());
        result.setStatus("failed");
        result.setFinished(true);
        result.setResponseType(ResponseTypeEnum.text.name());
        result.setErrorMsg(errorMessage);
        result.setResponse("❌ 处理请求时发生错误: " + errorMessage);
        result.setResponseAll(result.getResponse());
        
        return result;
    }
}
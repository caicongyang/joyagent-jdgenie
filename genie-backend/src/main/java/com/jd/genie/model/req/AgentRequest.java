package com.jd.genie.model.req;

import com.jd.genie.model.dto.FileInformation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Assistant请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRequest {
    /**
     * Get request ID
     */
    public String getRequestId() {
        return requestId;
    }
    private String requestId;
    private String erp;
    private String query;
    private Integer agentType;
    private String basePrompt;
    private String sopPrompt;
    private Boolean isStream;
    private List<Message> messages;
    private String outputStyle; // 交付物产出格式：html(网页模式）， docs(文档模式）， table(表格模式）
    private String workflowDefinition; // 工作流定义JSON字符串，用于WORKFLOW类型的Agent
    private String workflowTemplate; // 工作流模板名称，从配置文件中加载预定义工作流
    private String workflowVariables; // 工作流变量JSON字符串，用于自定义模板变量
    private String executionMode; // 执行模式：batch(批量执行)，step(单步执行)

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
        private String commandCode;
        private List<FileInformation> uploadFile;
        private List<FileInformation> files;

    }
}

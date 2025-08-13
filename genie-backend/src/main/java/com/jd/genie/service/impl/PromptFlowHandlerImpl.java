package com.jd.genie.service.impl;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.agent.PromptFlowAgent;
import com.jd.genie.agent.enums.AgentType;
import com.jd.genie.model.req.AgentRequest;
import com.jd.genie.service.AgentHandlerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * PromptFlow 智能体处理器
 */
@Slf4j
@Component
public class PromptFlowHandlerImpl implements AgentHandlerService {
    
    @Override
    public String handle(AgentContext agentContext, AgentRequest request) {
        try {
            log.info("Starting PromptFlow agent handling for request: {}", request.getQuery());
            
            // 设置 PromptFlow 相关的上下文
            setupPromptFlowContext(agentContext, request);
            
            // 创建并运行 PromptFlow Agent
            PromptFlowAgent agent = new PromptFlowAgent(agentContext);
            String result = agent.run(request.getQuery());
            
            log.info("PromptFlow agent completed successfully");
            return result;
            
        } catch (Exception e) {
            log.error("PromptFlow agent execution failed", e);
            return "PromptFlow执行失败: " + e.getMessage();
        }
    }
    
    @Override
    public Boolean support(AgentContext agentContext, AgentRequest request) {
        return AgentType.PROMPT_FLOW.getValue().equals(request.getAgentType());
    }
    
    /**
     * 设置 PromptFlow 上下文
     */
    private void setupPromptFlowContext(AgentContext agentContext, AgentRequest request) {
        // 使用工作流定义字段作为 PromptFlow 的 Markdown 内容
        if (request.getWorkflowDefinition() != null && !request.getWorkflowDefinition().trim().isEmpty()) {
            agentContext.setInlineMarkdown(request.getWorkflowDefinition());
            log.info("Set inline markdown from workflow definition");
        }
        
        // 处理工作流变量
        if (request.getWorkflowVariables() != null && !request.getWorkflowVariables().trim().isEmpty()) {
            try {
                // 这里应该解析 JSON 字符串为 Map，但现在先用简单方式
                Map<String, Object> variables = new HashMap<>();
                variables.put("raw_variables", request.getWorkflowVariables());
                agentContext.setFlowVariables(variables);
                log.info("Set flow variables from workflow variables");
            } catch (Exception e) {
                log.warn("Failed to parse workflow variables: {}", e.getMessage());
            }
        }
        
        // 如果没有提供任何 Markdown 内容，尝试从 query 中解析
        if (agentContext.getInlineMarkdown() == null && agentContext.getMarkdownFlow() == null) {
            String query = request.getQuery();
            if (query != null && isMarkdownContent(query)) {
                agentContext.setInlineMarkdown(query);
                log.info("Using query as inline markdown content");
            }
        }
    }
    
    /**
     * 检查内容是否为 Markdown 流程定义
     */
    private boolean isMarkdownContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        
        // 检查是否包含流程步骤的标识符
        return content.matches(".*\\d+\\..*\\[\\w+.*\\].*");
    }
}
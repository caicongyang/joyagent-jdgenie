package com.jd.genie.agent.promptflow.engine;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.promptflow.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 流程执行引擎
 */
@Slf4j
@Component
public class FlowEngine {
    private final AgentContext agentContext;
    private PromptFlowConfig config;
    
    public FlowEngine(AgentContext agentContext) {
        this.agentContext = agentContext;
    }
    
    /**
     * 初始化引擎
     */
    public void initialize(PromptFlowConfig config) {
        this.config = config;
    }
    
    /**
     * 执行流程
     */
    public String execute() throws FlowExecutionException {
        if (config == null || config.getFlowDefinition() == null) {
            throw new FlowExecutionException("Flow configuration not initialized");
        }
        
        FlowContext flowContext = createInitialContext();
        StringBuilder result = new StringBuilder();
        
        String currentNodeId = config.getFlowDefinition().getStartNode();
        int executedSteps = 0;
        int maxSteps = 50; // 防止无限循环
        
        while (currentNodeId != null && executedSteps < maxSteps) {
            FlowNode currentNode = findNodeById(currentNodeId);
            if (currentNode == null) {
                throw new FlowExecutionException("Node not found: " + currentNodeId);
            }
            
            log.info("Executing node: {} ({})", currentNodeId, currentNode.getName());
            
            // 执行当前节点
            NodeExecutionResult nodeResult = executeNode(currentNode, flowContext);
            
            if (!nodeResult.isSuccess()) {
                throw new FlowExecutionException("Node execution failed: " + nodeResult.getError());
            }
            
            // 累加输出结果
            if (nodeResult.getOutput() != null && !nodeResult.getOutput().trim().isEmpty()) {
                if (result.length() > 0) {
                    result.append("\n\n");
                }
                result.append(nodeResult.getOutput());
            }
            
            // 更新流程上下文
            if (nodeResult.getContext() != null) {
                flowContext.merge(nodeResult.getContext());
            }
            
            // 确定下一个节点
            currentNodeId = nodeResult.getNextNodeId();
            executedSteps++;
            
            // 发送流式输出（如果启用）
            sendStreamOutput(nodeResult);
        }
        
        if (executedSteps >= maxSteps) {
            log.warn("Flow execution terminated due to max steps limit: {}", maxSteps);
        }
        
        return result.toString();
    }
    
    /**
     * 创建初始上下文
     */
    private FlowContext createInitialContext() {
        FlowContext context = new FlowContext();
        context.setAgentContext(agentContext);
        
        // 设置全局变量
        Map<String, Object> globalVars = new HashMap<>();
        if (config.getVariables() != null) {
            globalVars.putAll(config.getVariables());
        }
        if (config.getFlowDefinition().getGlobalVariables() != null) {
            globalVars.putAll(config.getFlowDefinition().getGlobalVariables());
        }
        if (agentContext.getFlowVariables() != null) {
            globalVars.putAll(agentContext.getFlowVariables());
        }
        context.setGlobalVariables(globalVars);
        
        // 设置内置变量
        context.setVariable("user_input", agentContext.getQuery());
        context.setVariable("current_date", java.time.LocalDate.now().toString());
        context.setVariable("current_time", java.time.LocalDateTime.now().toString());
        
        return context;
    }
    
    /**
     * 查找节点
     */
    private FlowNode findNodeById(String nodeId) {
        if (config.getFlowDefinition().getNodes() == null) {
            return null;
        }
        
        return config.getFlowDefinition().getNodes().stream()
            .filter(node -> nodeId.equals(node.getId()))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 执行节点
     */
    private NodeExecutionResult executeNode(FlowNode node, FlowContext context) {
        try {
            return node.execute(context);
        } catch (Exception e) {
            log.error("Failed to execute node: {}", node.getId(), e);
            return NodeExecutionResult.builder()
                .nodeId(node.getId())
                .success(false)
                .error(e.getMessage())
                .build();
        }
    }
    
    /**
     * 发送流式输出
     */
    private void sendStreamOutput(NodeExecutionResult nodeResult) {
        if (agentContext.getIsStream() != null && agentContext.getIsStream() && 
            agentContext.getPrinter() != null) {
            try {
                // 发送节点执行结果
                String streamMessage = String.format("[%s] %s", 
                    nodeResult.getNodeId(), 
                    nodeResult.getOutput());
                agentContext.getPrinter().send("prompt_flow_step", streamMessage);
            } catch (Exception e) {
                log.warn("Failed to send stream output", e);
            }
        }
    }
    
    /**
     * 流程执行异常
     */
    public static class FlowExecutionException extends Exception {
        public FlowExecutionException(String message) {
            super(message);
        }
        
        public FlowExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
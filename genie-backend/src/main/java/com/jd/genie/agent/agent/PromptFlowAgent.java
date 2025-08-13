package com.jd.genie.agent.agent;

import com.jd.genie.agent.enums.AgentState;
import com.jd.genie.agent.promptflow.model.*;
import com.jd.genie.agent.promptflow.parser.MarkdownFlowParser;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * PromptFlow 智能体 - 重构为多步骤执行模式
 */
@Slf4j
public class PromptFlowAgent extends BaseAgent {
    private PromptFlowConfig config;
    private MarkdownFlowParser markdownParser;
    
    // 执行状态管理
    private FlowContext flowContext;
    private String currentNodeId;
    private int executedNodes = 0;
    private StringBuilder flowResult;
    private boolean initialized = false;
    
    public PromptFlowAgent(AgentContext context) {
        this.context = context;
        this.markdownParser = new MarkdownFlowParser();
        this.flowResult = new StringBuilder();
        
        // 设置基本信息
        setName("PromptFlow Agent");
        setDescription("基于 Markdown 的流程化智能体");
        // 移除单步限制，使用动态步数计算
        setMaxSteps(50); // 防止无限循环的保护措施
    }
    
    @Override
    public String step() {
        try {
            // 首次执行时进行初始化
            if (!initialized) {
                initializeFlow();
                initialized = true;
            }
            
            // 检查是否还有节点需要执行
            if (currentNodeId == null) {
                setState(AgentState.FINISHED);
                return flowResult.toString();
            }
            
            // 执行当前节点
            return executeCurrentNode();
            
        } catch (Exception e) {
            log.error("PromptFlow step execution failed", e);
            setState(AgentState.ERROR);
            return handleError(e);
        }
    }
    
    /**
     * 初始化流程
     */
    private void initializeFlow() throws Exception {
        // 解析配置
        parseFlowConfiguration();
        
        // 创建流程上下文
        flowContext = createInitialContext();
        
        // 设置起始节点
        currentNodeId = config.getFlowDefinition().getStartNode();
        
        // 动态计算最大步数
        int nodeCount = config.getFlowDefinition().getNodes() != null ? 
            config.getFlowDefinition().getNodes().size() : 10;
        setMaxSteps(Math.max(nodeCount * 2, 50)); // 允许节点重复执行或条件跳转
        
        log.info("PromptFlow initialized with {} nodes, max steps: {}", nodeCount, getMaxSteps());
    }
    
    /**
     * 执行当前节点
     */
    private String executeCurrentNode() {
        FlowNode currentNode = findNodeById(currentNodeId);
        if (currentNode == null) {
            setState(AgentState.ERROR);
            return "Error: Node not found: " + currentNodeId;
        }
        
        log.info("Executing PromptFlow node: {} ({})", currentNodeId, currentNode.getName());
        
        // 执行节点
        NodeExecutionResult nodeResult = executeNode(currentNode);
        
        if (!nodeResult.isSuccess()) {
            setState(AgentState.ERROR);
            return "Node execution failed: " + nodeResult.getError();
        }
        
        // 累加输出结果
        if (nodeResult.getOutput() != null && !nodeResult.getOutput().trim().isEmpty()) {
            if (flowResult.length() > 0) {
                flowResult.append("\n\n");
            }
            flowResult.append(nodeResult.getOutput());
        }
        
        // 更新流程上下文
        if (nodeResult.getContext() != null) {
            flowContext.merge(nodeResult.getContext());
        }
        
        // 发送流式输出
        sendStreamOutput(nodeResult);
        
        // 更新到下一个节点
        currentNodeId = nodeResult.getNextNodeId();
        executedNodes++;
        
        // 如果没有下一个节点，标记完成
        if (currentNodeId == null) {
            setState(AgentState.FINISHED);
            return "Node completed: " + currentNode.getName() + " (Flow finished)";
        }
        
        return "Node completed: " + currentNode.getName();
    }
    
    /**
     * 解析流程配置
     */
    private void parseFlowConfiguration() throws Exception {
        String markdownContent = getMarkdownContent();
        
        if (markdownContent == null || markdownContent.trim().isEmpty()) {
            throw new IllegalArgumentException("Markdown content is required for PromptFlow");
        }
        
        log.info("Parsing markdown content for flow: {}", 
                markdownContent.length() > 100 ? 
                markdownContent.substring(0, 100) + "..." : 
                markdownContent);
        
        this.config = markdownParser.parseMarkdown(markdownContent);
        
        log.info("Successfully parsed flow configuration: {}", config.getName());
    }
    
    /**
     * 获取 Markdown 内容
     */
    private String getMarkdownContent() throws IOException {
        // 优先使用内联 Markdown 内容
        if (context.getInlineMarkdown() != null) {
            log.info("Using inline markdown content");
            return context.getInlineMarkdown();
        }
        
        // 其次使用 Markdown 文件路径
        if (context.getMarkdownFlow() != null) {
            String filePath = context.getMarkdownFlow();
            log.info("Loading markdown from file: {}", filePath);
            
            try {
                return Files.readString(Paths.get(filePath));
            } catch (IOException e) {
                log.error("Failed to read markdown file: {}", filePath, e);
                throw new IOException("Failed to read markdown file: " + filePath, e);
            }
        }
        
        // 最后尝试从查询中提取 Markdown 内容
        String query = context.getQuery();
        if (query != null && isMarkdownLikeContent(query)) {
            log.info("Using query as markdown content");
            return query;
        }
        
        throw new IllegalArgumentException("No markdown content provided. Please specify either inlineMarkdown or markdownFlow in the request.");
    }
    
    /**
     * 检查内容是否像 Markdown 流程定义
     */
    private boolean isMarkdownLikeContent(String content) {
        if (content == null) {
            return false;
        }
        
        // 简单检查是否包含流程步骤的模式
        return content.contains("1.") && 
               (content.contains("[prompt]") || 
                content.contains("[tool") || 
                content.contains("[markdown]"));
    }
    
    /**
     * 创建初始上下文
     */
    private FlowContext createInitialContext() {
        FlowContext context = new FlowContext();
        context.setAgentContext(this.context);
        
        // 设置全局变量
        Map<String, Object> globalVars = new HashMap<>();
        if (config.getVariables() != null) {
            globalVars.putAll(config.getVariables());
        }
        if (config.getFlowDefinition().getGlobalVariables() != null) {
            globalVars.putAll(config.getFlowDefinition().getGlobalVariables());
        }
        if (this.context.getFlowVariables() != null) {
            globalVars.putAll(this.context.getFlowVariables());
        }
        context.setGlobalVariables(globalVars);
        
        // 设置内置变量
        context.setVariable("user_input", this.context.getQuery());
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
    private NodeExecutionResult executeNode(FlowNode node) {
        try {
            return node.execute(flowContext);
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
        if (context.getIsStream() != null && context.getIsStream() && 
            context.getPrinter() != null) {
            try {
                // 发送节点执行结果
                String streamMessage = String.format("[%s] %s", 
                    nodeResult.getNodeId(), 
                    nodeResult.getOutput());
                context.getPrinter().send("prompt_flow_step", streamMessage);
                
                // 发送进度信息
                Map<String, Object> progressInfo = new HashMap<>();
                progressInfo.put("executedNodes", executedNodes);
                progressInfo.put("currentNode", nodeResult.getNodeId());
                progressInfo.put("hasNext", nodeResult.getNextNodeId() != null);
                context.getPrinter().send("prompt_flow_progress", progressInfo);
            } catch (Exception e) {
                log.warn("Failed to send stream output", e);
            }
        }
    }
    
    /**
     * 处理错误
     */
    private String handleError(Exception e) {
        String errorMessage = String.format("PromptFlow执行失败: %s", e.getMessage());
        log.error(errorMessage, e);
        
        // 如果启用了流式输出，发送错误信息
        if (context.getPrinter() != null) {
            try {
                context.getPrinter().send("prompt_flow_error", "ERROR: " + errorMessage);
            } catch (Exception printError) {
                log.warn("Failed to print error message", printError);
            }
        }
        
        return errorMessage;
    }
    
    /**
     * 重写run方法，确保在完成时返回完整结果
     */
    @Override
    public String run(String query) {
        String result = super.run(query);
        
        // 如果流程正常完成，返回累积的完整结果
        if (getState() == AgentState.FINISHED && flowResult.length() > 0) {
            return flowResult.toString();
        }
        
        return result;
    }
    
    /**
     * 获取默认的 Markdown 文件路径
     */
    private String getDefaultMarkdownPath() {
        return "flows/default_flow.md";
    }
}
package com.jd.genie.agent.promptflow.parser;

import com.jd.genie.agent.promptflow.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 流程解析器
 */
@Slf4j
@Component
public class MarkdownFlowParser {
    
    private static final Pattern STEP_PATTERN = Pattern.compile("^(\\d+)\\.(.*?)\\[(.*?)\\]");
    private static final Pattern CONFIG_PATTERN = Pattern.compile("^-\\s+(.*?):\\s+(.*)");
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(.*?)\\}\\}");
    
    /**
     * 解析状态枚举
     */
    private enum ParseState {
        HEADER,       // 解析标题
        CONFIG,       // 解析配置
        STEPS,        // 解析步骤列表
        STEP_CONTENT  // 解析步骤内容
    }
    
    /**
     * 解析 Markdown 内容为 PromptFlow 配置
     */
    public PromptFlowConfig parseMarkdown(String markdownContent) throws ParseException {
        if (markdownContent == null || markdownContent.trim().isEmpty()) {
            throw new ParseException("Markdown content cannot be empty");
        }
        
        String[] lines = markdownContent.split("\n");
        
        PromptFlowConfig.PromptFlowConfigBuilder configBuilder = PromptFlowConfig.builder();
        List<FlowStep> steps = new ArrayList<>();
        Map<String, Object> globalConfig = new HashMap<>();
        
        ParseState state = ParseState.HEADER;
        FlowStep currentStep = null;
        StringBuilder contentBuffer = new StringBuilder();
        
        for (String line : lines) {
            line = line.trim();
            
            switch (state) {
                case HEADER:
                    if (line.startsWith("# ")) {
                        configBuilder.name(line.substring(2).trim());
                    } else if (line.equals("## 配置")) {
                        state = ParseState.CONFIG;
                    } else if (line.equals("## 流程步骤")) {
                        state = ParseState.STEPS;
                    }
                    break;
                    
                case CONFIG:
                    if (line.equals("## 流程步骤")) {
                        state = ParseState.STEPS;
                    } else if (line.startsWith("- ")) {
                        parseConfigLine(line, globalConfig);
                    }
                    break;
                    
                case STEPS:
                    if (isStepLine(line)) {
                        // 保存前一个步骤
                        if (currentStep != null) {
                            currentStep.setContent(contentBuffer.toString().trim());
                            steps.add(currentStep);
                        }
                        
                        // 解析新步骤
                        currentStep = parseStepLine(line);
                        contentBuffer = new StringBuilder();
                        state = ParseState.STEP_CONTENT;
                    }
                    break;
                    
                case STEP_CONTENT:
                    if (isStepLine(line)) {
                        // 保存当前步骤并开始新步骤
                        currentStep.setContent(contentBuffer.toString().trim());
                        steps.add(currentStep);
                        
                        currentStep = parseStepLine(line);
                        contentBuffer = new StringBuilder();
                    } else {
                        if (contentBuffer.length() > 0) {
                            contentBuffer.append("\n");
                        }
                        contentBuffer.append(line);
                    }
                    break;
            }
        }
        
        // 保存最后一个步骤
        if (currentStep != null) {
            currentStep.setContent(contentBuffer.toString().trim());
            steps.add(currentStep);
        }
        
        return configBuilder
            .variables(globalConfig)
            .flowDefinition(convertToFlowDefinition(steps))
            .build();
    }
    
    /**
     * 解析配置行
     */
    private void parseConfigLine(String line, Map<String, Object> config) {
        Matcher matcher = CONFIG_PATTERN.matcher(line);
        if (matcher.find()) {
            String key = matcher.group(1).trim();
            String value = matcher.group(2).trim();
            config.put(key, value);
        }
    }
    
    /**
     * 检查是否是步骤行
     */
    private boolean isStepLine(String line) {
        return STEP_PATTERN.matcher(line).find();
    }
    
    /**
     * 解析步骤行
     */
    private FlowStep parseStepLine(String line) throws ParseException {
        Matcher matcher = STEP_PATTERN.matcher(line);
        if (matcher.find()) {
            int stepNumber = Integer.parseInt(matcher.group(1));
            String stepName = matcher.group(2).trim();
            String stepType = matcher.group(3).trim();
            
            // 清理步骤名称中的markdown格式
            stepName = stepName.replaceAll("\\*\\*(.*?)\\*\\*", "$1");
            
            FlowStep.FlowStepBuilder builder = FlowStep.builder()
                .stepNumber(stepNumber)
                .name(stepName)
                .originalType(stepType);
                
            // 解析步骤类型和工具名
            if (stepType.startsWith("tool:")) {
                builder.type(StepType.TOOL)
                       .toolName(stepType.substring(5));
            } else if (stepType.startsWith("if:")) {
                builder.type(StepType.CONDITION)
                       .condition(stepType.substring(3));
            } else {
                builder.type(parseStepType(stepType));
            }
            
            return builder.build();
        }
        throw new ParseException("Invalid step format: " + line);
    }
    
    /**
     * 解析步骤类型
     */
    private StepType parseStepType(String typeStr) {
        switch (typeStr.toLowerCase()) {
            case "prompt":
                return StepType.PROMPT;
            case "markdown":
                return StepType.MARKDOWN;
            case "parallel":
                return StepType.PARALLEL;
            case "loop":
                return StepType.LOOP;
            default:
                if (typeStr.startsWith("tool")) {
                    return StepType.TOOL;
                }
                return StepType.UNKNOWN;
        }
    }
    
    /**
     * 转换为流程定义
     */
    private FlowDefinition convertToFlowDefinition(List<FlowStep> steps) {
        List<FlowNode> nodes = new ArrayList<>();
        
        for (int i = 0; i < steps.size(); i++) {
            FlowStep step = steps.get(i);
            FlowNode node = createFlowNode(step, i);
            
            // 设置下一个节点
            if (i < steps.size() - 1) {
                node.setNextNode("step_" + (i + 1));
            }
            
            nodes.add(node);
        }
        
        return FlowDefinition.builder()
            .startNode(nodes.isEmpty() ? null : "step_0")
            .nodes(nodes)
            .globalVariables(new HashMap<>())
            .build();
    }
    
    /**
     * 创建流程节点
     */
    private FlowNode createFlowNode(FlowStep step, int index) {
        String nodeId = "step_" + index;
        
        switch (step.getType()) {
            case PROMPT:
                return createPromptNode(nodeId, step);
            case TOOL:
                return createToolNode(nodeId, step);
            case MARKDOWN:
                return createMarkdownNode(nodeId, step);
            case CONDITION:
                return createConditionNode(nodeId, step);
            default:
                // 创建默认的 Prompt 节点
                return createPromptNode(nodeId, step);
        }
    }
    
    /**
     * 创建 Prompt 节点
     */
    private FlowNode createPromptNode(String nodeId, FlowStep step) {
        PromptNode node = new PromptNode();
        node.setId(nodeId);
        node.setName(step.getName());
        node.setType(NodeType.PROMPT);
        node.setPromptContent(step.getContent());
        return node;
    }
    
    /**
     * 创建工具节点
     */
    private FlowNode createToolNode(String nodeId, FlowStep step) {
        ToolNode node = new ToolNode();
        node.setId(nodeId);
        node.setName(step.getName());
        node.setType(NodeType.TOOL);
        node.setToolName(step.getToolName());
        node.setToolParameters(parseToolParameters(step.getContent()));
        return node;
    }
    
    /**
     * 创建 Markdown 节点
     */
    private FlowNode createMarkdownNode(String nodeId, FlowStep step) {
        MarkdownNode node = new MarkdownNode();
        node.setId(nodeId);
        node.setName(step.getName());
        node.setType(NodeType.MARKDOWN);
        node.setMarkdownContent(step.getContent());
        return node;
    }
    
    /**
     * 创建条件节点
     */
    private FlowNode createConditionNode(String nodeId, FlowStep step) {
        ControlNode node = new ControlNode();
        node.setId(nodeId);
        node.setName(step.getName());
        node.setType(NodeType.CONDITION);
        node.setCondition(step.getCondition());
        return node;
    }
    
    /**
     * 解析工具参数
     */
    private Map<String, Object> parseToolParameters(String content) {
        Map<String, Object> params = new HashMap<>();
        
        if (content == null || content.trim().isEmpty()) {
            return params;
        }
        
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("- ")) {
                Matcher matcher = CONFIG_PATTERN.matcher(line);
                if (matcher.find()) {
                    String key = matcher.group(1).trim();
                    String value = matcher.group(2).trim();
                    params.put(key, value);
                }
            }
        }
        
        return params;
    }
    
    /**
     * 解析异常类
     */
    public static class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }
        
        public ParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
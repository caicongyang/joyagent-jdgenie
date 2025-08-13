package com.jd.genie.agent.promptflow.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 节点实现
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Slf4j
public class MarkdownNode extends FlowNode {
    private String markdownContent;
    private String templatePath;
    private MarkdownRenderType renderType = MarkdownRenderType.PLAIN;
    
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(.*?)\\}\\}");
    
    @Override
    public NodeExecutionResult execute(FlowContext context) {
        try {
            long startTime = System.currentTimeMillis();
            
            String content = getMarkdownContent(context);
            String renderedContent = renderMarkdown(content, context);
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            // 更新上下文中的步骤结果
            context.setStepResult(getId(), renderedContent);
            context.setStepResult(getId() + "_result", renderedContent);
            
            return NodeExecutionResult.builder()
                .nodeId(getId())
                .success(true)
                .output(renderedContent)
                .context(createOutputContext(renderedContent, context))
                .nextNodeId(getNextNode())
                .executionTime(executionTime)
                .build();
                
        } catch (Exception e) {
            log.error("MarkdownNode execution failed for node: {}", getId(), e);
            return NodeExecutionResult.builder()
                .nodeId(getId())
                .success(false)
                .error(e.getMessage())
                .executionTime(0)
                .build();
        }
    }
    
    /**
     * 获取 Markdown 内容
     */
    private String getMarkdownContent(FlowContext context) {
        if (markdownContent != null) {
            return resolveVariables(markdownContent, context);
        } else if (templatePath != null) {
            // 从文件加载 markdown 模板
            try {
                String template = Files.readString(Paths.get(templatePath));
                return resolveVariables(template, context);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load markdown template: " + templatePath, e);
            }
        } else {
            throw new RuntimeException("No markdown content or template specified for node: " + getId());
        }
    }
    
    /**
     * 渲染 Markdown 内容
     */
    private String renderMarkdown(String content, FlowContext context) {
        switch (renderType) {
            case HTML:
                return markdownToHtml(content);
            case PLAIN:
                return content;
            case FORMATTED:
                return formatMarkdown(content);
            default:
                return content;
        }
    }
    
    /**
     * 解析变量引用
     */
    private String resolveVariables(String content, FlowContext context) {
        if (content == null) {
            return "";
        }
        
        String result = content;
        Matcher matcher = VARIABLE_PATTERN.matcher(result);
        
        while (matcher.find()) {
            String varName = matcher.group(1).trim();
            String varValue = resolveVariable(varName, context);
            result = result.replace("{{" + varName + "}}", varValue);
        }
        
        return result;
    }
    
    /**
     * 解析单个变量
     */
    private String resolveVariable(String varName, FlowContext context) {
        // 尝试从步骤结果中获取
        if (varName.endsWith("_result")) {
            String stepResult = context.getStepResult(varName);
            if (stepResult != null) {
                return stepResult;
            }
        }
        
        // 尝试从上下文变量中获取
        String value = context.getVariable(varName, null);
        if (value != null) {
            return value;
        }
        
        // 内置变量
        switch (varName) {
            case "current_date":
                return java.time.LocalDate.now().toString();
            case "current_time":
                return java.time.LocalDateTime.now().toString();
            case "user_input":
                return context.getAgentContext().getQuery();
            default:
                return "{{" + varName + "}}"; // 保持原样
        }
    }
    
    /**
     * 简单的 Markdown 转 HTML
     */
    private String markdownToHtml(String markdown) {
        String html = markdown;
        
        // 简单的转换规则
        html = html.replaceAll("^# (.*)", "<h1>$1</h1>");
        html = html.replaceAll("^## (.*)", "<h2>$1</h2>");
        html = html.replaceAll("^### (.*)", "<h3>$1</h3>");
        html = html.replaceAll("\\*\\*(.*?)\\*\\*", "<strong>$1</strong>");
        html = html.replaceAll("\\*(.*?)\\*", "<em>$1</em>");
        html = html.replaceAll("\\n", "<br>");
        
        return html;
    }
    
    /**
     * 格式化 Markdown
     */
    private String formatMarkdown(String markdown) {
        // 简单的格式化处理
        return markdown.trim();
    }
    
    /**
     * 创建输出上下文
     */
    private FlowContext createOutputContext(String result, FlowContext inputContext) {
        FlowContext outputContext = new FlowContext();
        outputContext.setAgentContext(inputContext.getAgentContext());
        outputContext.setGlobalVariables(inputContext.getGlobalVariables());
        outputContext.setVariables(new HashMap<>(inputContext.getVariables()));
        outputContext.setStepResults(new HashMap<>(inputContext.getStepResults()));
        
        // 添加当前步骤的结果
        outputContext.setStepResult(getId(), result);
        outputContext.setStepResult(getId() + "_result", result);
        
        return outputContext;
    }
}


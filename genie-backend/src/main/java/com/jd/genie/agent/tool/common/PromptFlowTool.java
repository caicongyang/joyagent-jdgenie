package com.jd.genie.agent.tool.common;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.agent.PromptFlowAgent;
import com.jd.genie.agent.promptflow.parser.MarkdownFlowParser;
import com.jd.genie.agent.promptflow.model.PromptFlowConfig;
import com.jd.genie.agent.tool.BaseTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * PromptFlow 工具
 */
@Slf4j
@Component
public class PromptFlowTool implements BaseTool {
    
    private AgentContext agentContext;
    
    public void setAgentContext(AgentContext agentContext) {
        this.agentContext = agentContext;
    }
    
    @Override
    public String getName() {
        return "prompt_flow";
    }
    
    @Override
    public String getDescription() {
        return "PromptFlow 流程管理工具，支持加载、执行、管理 prompt 流程。支持从 Markdown 内容或文件执行流程，验证格式，列出模板等功能。";
    }
    
    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        
        // command 参数
        Map<String, Object> command = new HashMap<>();
        command.put("type", "string");
        command.put("enum", Arrays.asList(
            "execute_markdown", 
            "execute_file", 
            "validate", 
            "list_templates", 
            "create_template"
        ));
        command.put("description", "操作命令：execute_markdown-执行Markdown内容, execute_file-执行Markdown文件, validate-验证格式, list_templates-列出模板, create_template-创建模板");
        properties.put("command", command);
        
        // markdown_content 参数
        Map<String, Object> markdownContent = new HashMap<>();
        markdownContent.put("type", "string");
        markdownContent.put("description", "Markdown格式的流程内容");
        properties.put("markdown_content", markdownContent);
        
        // markdown_file 参数
        Map<String, Object> markdownFile = new HashMap<>();
        markdownFile.put("type", "string");
        markdownFile.put("description", "Markdown文件路径");
        properties.put("markdown_file", markdownFile);
        
        // variables 参数
        Map<String, Object> variables = new HashMap<>();
        variables.put("type", "object");
        variables.put("description", "流程变量映射");
        properties.put("variables", variables);
        
        // template_name 参数
        Map<String, Object> templateName = new HashMap<>();
        templateName.put("type", "string");
        templateName.put("description", "模板名称");
        properties.put("template_name", templateName);
        
        // template_type 参数
        Map<String, Object> templateType = new HashMap<>();
        templateType.put("type", "string");
        templateType.put("enum", Arrays.asList("basic", "data_analysis", "customer_service"));
        templateType.put("description", "模板类型");
        properties.put("template_type", templateType);
        
        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("command"));
        
        return parameters;
    }
    
    @Override
    public Object execute(Object input) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) input;
            String command = (String) params.get("command");
            
            switch (command) {
                case "execute_markdown":
                    return executeMarkdownContent(params);
                case "execute_file":
                    return executeMarkdownFile(params);
                case "validate":
                    return validateMarkdown(params);
                case "list_templates":
                    return listAvailableTemplates();
                case "create_template":
                    return createTemplate(params);
                default:
                    return "不支持的命令: " + command;
            }
            
        } catch (Exception e) {
            log.error("PromptFlowTool execution failed", e);
            return "工具执行失败: " + e.getMessage();
        }
    }
    
    /**
     * 执行 Markdown 内容
     */
    private Object executeMarkdownContent(Map<String, Object> params) {
        String markdownContent = (String) params.get("markdown_content");
        if (markdownContent == null || markdownContent.trim().isEmpty()) {
            return "错误: 缺少 Markdown 内容";
        }
        
        try {
            // 创建临时的 AgentContext
            AgentContext tempContext = createTempContext(params);
            tempContext.setInlineMarkdown(markdownContent);
            
            // 执行 PromptFlow
            PromptFlowAgent agent = new PromptFlowAgent(tempContext);
            String result = agent.step();
            
            return "Markdown 流程执行完成:\n" + result;
            
        } catch (Exception e) {
            log.error("Failed to execute markdown content", e);
            return "Markdown 流程执行失败: " + e.getMessage();
        }
    }
    
    /**
     * 执行 Markdown 文件
     */
    private Object executeMarkdownFile(Map<String, Object> params) {
        String markdownFile = (String) params.get("markdown_file");
        if (markdownFile == null || markdownFile.trim().isEmpty()) {
            return "错误: 缺少 Markdown 文件路径";
        }
        
        try {
            // 创建临时的 AgentContext
            AgentContext tempContext = createTempContext(params);
            tempContext.setMarkdownFlow(markdownFile);
            
            // 执行 PromptFlow
            PromptFlowAgent agent = new PromptFlowAgent(tempContext);
            String result = agent.step();
            
            return "Markdown 文件执行完成:\n" + result;
            
        } catch (Exception e) {
            log.error("Failed to execute markdown file: {}", markdownFile, e);
            return "Markdown 文件执行失败: " + e.getMessage();
        }
    }
    
    /**
     * 验证 Markdown 格式
     */
    private Object validateMarkdown(Map<String, Object> params) {
        String markdownContent = (String) params.get("markdown_content");
        if (markdownContent == null || markdownContent.trim().isEmpty()) {
            return "错误: 缺少要验证的 Markdown 内容";
        }
        
        try {
            MarkdownFlowParser parser = new MarkdownFlowParser();
            PromptFlowConfig config = parser.parseMarkdown(markdownContent);
            
            int stepCount = config.getFlowDefinition() != null && config.getFlowDefinition().getNodes() != null 
                ? config.getFlowDefinition().getNodes().size() : 0;
                
            return String.format("Markdown 格式验证通过:\n- 流程名称: %s\n- 步骤数量: %d", 
                                config.getName() != null ? config.getName() : "未命名", 
                                stepCount);
        } catch (Exception e) {
            return "Markdown 格式验证失败: " + e.getMessage();
        }
    }
    
    /**
     * 列出可用模板
     */
    private Object listAvailableTemplates() {
        try {
            String templateDirPath = "flows/templates";
            File templateDir = new File(templateDirPath);
            
            if (!templateDir.exists()) {
                return "模板目录不存在: " + templateDirPath + "\n建议创建该目录并放置模板文件";
            }
            
            File[] templateFiles = templateDir.listFiles((dir, name) -> name.endsWith(".md"));
            
            if (templateFiles == null || templateFiles.length == 0) {
                return "未找到可用的 Markdown 模板文件";
            }
            
            StringBuilder result = new StringBuilder("可用的 Markdown 模板:\n");
            for (File file : templateFiles) {
                result.append("- ").append(file.getName());
                
                // 尝试读取文件的第一行作为描述
                try {
                    String firstLine = Files.lines(file.toPath()).findFirst().orElse("");
                    if (firstLine.startsWith("# ")) {
                        result.append(" (").append(firstLine.substring(2)).append(")");
                    }
                } catch (Exception e) {
                    // 忽略读取错误
                }
                
                result.append("\n");
            }
            
            return result.toString();
            
        } catch (Exception e) {
            log.error("Failed to list templates", e);
            return "列出模板失败: " + e.getMessage();
        }
    }
    
    /**
     * 创建模板
     */
    private Object createTemplate(Map<String, Object> params) {
        String templateName = (String) params.get("template_name");
        String templateType = (String) params.get("template_type");
        
        if (templateName == null || templateName.trim().isEmpty()) {
            return "错误: 缺少模板名称";
        }
        
        try {
            String templateContent = generateTemplate(templateType != null ? templateType : "basic");
            
            // 确保模板目录存在
            File templateDir = new File("flows/templates");
            if (!templateDir.exists()) {
                templateDir.mkdirs();
            }
            
            String filePath = "flows/templates/" + templateName + ".md";
            Files.write(Paths.get(filePath), templateContent.getBytes());
            
            return "成功创建模板: " + filePath;
            
        } catch (Exception e) {
            log.error("Failed to create template: {}", templateName, e);
            return "创建模板失败: " + e.getMessage();
        }
    }
    
    /**
     * 生成模板内容
     */
    private String generateTemplate(String type) {
        switch (type) {
            case "data_analysis":
                return "# 数据分析模板\n\n" +
                       "## 配置\n" +
                       "- 作者: {{author}}\n" +
                       "- 模型: gpt-4\n\n" +
                       "## 流程步骤\n\n" +
                       "1. **读取数据** [tool:file_tool]\n" +
                       "   - 操作: get\n" +
                       "   - 文件名: {{input_file}}\n\n" +
                       "2. **数据分析** [tool:code_interpreter]\n" +
                       "   ```python\n" +
                       "   import pandas as pd\n" +
                       "   df = pd.read_csv('{{input_file}}')\n" +
                       "   print('数据形状:', df.shape)\n" +
                       "   print('数据描述:')\n" +
                       "   print(df.describe())\n" +
                       "   ```\n\n" +
                       "3. **生成报告** [prompt]\n" +
                       "   > 基于以下数据分析结果生成专业报告:\n" +
                       "   > \n" +
                       "   > {{step_1_result}}\n" +
                       "   > \n" +
                       "   > 请包含数据概览、关键发现和建议。";
                       
            case "customer_service":
                return "# 客服模板\n\n" +
                       "## 配置\n" +
                       "- 公司: {{company}}\n" +
                       "- 模型: gpt-3.5-turbo\n\n" +
                       "## 流程步骤\n\n" +
                       "1. **问候客户** [prompt]\n" +
                       "   > 你是{{company}}的智能客服，请专业礼貌地问候客户：\n" +
                       "   > \n" +
                       "   > 客户消息: {{user_input}}\n\n" +
                       "2. **意图识别** [tool:deep_search]\n" +
                       "   - query: 客服意图分类 {{user_input}}\n\n" +
                       "3. **智能回复** [prompt]\n" +
                       "   > 根据客户意图提供专业回复：\n" +
                       "   > \n" +
                       "   > 客户问题: {{user_input}}\n" +
                       "   > 意图分析: {{step_1_result}}\n" +
                       "   > \n" +
                       "   > 要求：专业、友好、准确";
                       
            default:
                return "# 基础模板\n\n" +
                       "## 配置\n" +
                       "- 作者: AI助手\n" +
                       "- 模型: gpt-4\n\n" +
                       "## 流程步骤\n\n" +
                       "1. **处理请求** [prompt]\n" +
                       "   > 请处理用户请求: {{user_input}}\n" +
                       "   > \n" +
                       "   > 要求：准确理解需求并提供有帮助的回复。";
        }
    }
    
    /**
     * 创建临时上下文
     */
    private AgentContext createTempContext(Map<String, Object> params) {
        AgentContext context = new AgentContext();
        
        // 从当前 agent context 复制必要信息
        if (agentContext != null) {
            context.setToolCollection(agentContext.getToolCollection());
            context.setPrinter(agentContext.getPrinter());
            context.setIsStream(agentContext.getIsStream());
        }
        
        // 设置流程变量
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) params.get("variables");
        if (variables != null) {
            context.setFlowVariables(variables);
        }
        
        // 设置默认查询
        context.setQuery("PromptFlow工具执行");
        
        return context;
    }
}
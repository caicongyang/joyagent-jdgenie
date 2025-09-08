package com.jd.genie.agent.tool.common;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.agent.PromptFlowAgent;
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
        return "PromptFlow v2.0 智能任务规划和执行工具。用户描述目标，AI自动规划任务并执行，无需学习特定语法。支持自然语言输入、智能规划、自动恢复等功能。";
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
            "execute_goal", 
            "execute_markdown", 
            "execute_file", 
            "list_templates", 
            "create_template"
        ));
        command.put("description", "操作命令：execute_goal-AI规划并执行目标(推荐), execute_markdown-执行Markdown内容(兼容模式), execute_file-执行Markdown文件(兼容模式), list_templates-列出模板, create_template-创建模板");
        properties.put("command", command);
        
        // goal 参数 (新增)
        Map<String, Object> goal = new HashMap<>();
        goal.put("type", "string");
        goal.put("description", "用自然语言描述要达成的目标，AI会自动规划执行步骤");
        properties.put("goal", goal);
        
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
        templateType.put("enum", Arrays.asList("basic", "data_analysis", "customer_service", "goal_examples"));
        templateType.put("description", "模板类型：basic-基础模板, data_analysis-数据分析, customer_service-客服对话, goal_examples-目标示例");
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
                case "execute_goal":
                    return executeGoal(params);
                case "execute_markdown":
                    return executeMarkdownContent(params);
                case "execute_file":
                    return executeMarkdownFile(params);
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
     * 执行目标（v2.0 推荐方式）
     */
    private Object executeGoal(Map<String, Object> params) {
        String goal = (String) params.get("goal");
        if (goal == null || goal.trim().isEmpty()) {
            return "错误: 请提供要达成的目标描述";
        }
        
        try {
            // 创建临时的 AgentContext，使用目标作为查询
            AgentContext tempContext = createTempContext(params);
            tempContext.setQuery(goal);
            
            // 使用 PromptFlowAgent v2 执行
            PromptFlowAgent agent = new PromptFlowAgent(tempContext);
            String result = agent.step();
            
            return "🎯 目标执行完成:\n\n" + result;
            
        } catch (Exception e) {
            log.error("Failed to execute goal: {}", goal, e);
            return "目标执行失败: " + e.getMessage();
        }
    }
    
    /**
     * 执行 Markdown 内容（兼容模式）
     */
    private Object executeMarkdownContent(Map<String, Object> params) {
        String markdownContent = (String) params.get("markdown_content");
        if (markdownContent == null || markdownContent.trim().isEmpty()) {
            return "错误: 缺少 Markdown 内容";
        }
        
        try {
            // v2.0 兼容模式：将 Markdown 内容转换为自然语言目标
            String naturalGoal = convertMarkdownToGoal(markdownContent);
            
            // 创建临时的 AgentContext，使用转换后的目标
            AgentContext tempContext = createTempContext(params);
            tempContext.setQuery(naturalGoal);
            
            // 使用 v2.0 引擎执行
            PromptFlowAgent agent = new PromptFlowAgent(tempContext);
            String result = agent.step();
            
            return "兼容模式执行完成 (已转换为AI规划模式):\n" + result;
            
        } catch (Exception e) {
            log.error("Failed to execute markdown content in compatibility mode", e);
            return "兼容模式执行失败: " + e.getMessage();
        }
    }
    
    /**
     * 执行 Markdown 文件（兼容模式）
     */
    private Object executeMarkdownFile(Map<String, Object> params) {
        return "兼容模式已弃用，请使用 execute_goal 命令和自然语言描述目标。\n" +
               "示例：{\"command\": \"execute_goal\", \"goal\": \"您的目标描述\"}";
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
            case "goal_examples":
                return "# PromptFlow v2.0 目标示例\n\n" +
                       "PromptFlow v2.0 使用自然语言描述目标，AI自动规划执行。以下是一些示例：\n\n" +
                       "## 数据分析类目标示例\n\n" +
                       "```\n" +
                       "分析这个销售数据文件，生成包含趋势分析和改进建议的专业报告\n" +
                       "```\n\n" +
                       "```\n" +
                       "对用户行为数据进行深度分析，找出用户流失的主要原因并提出解决方案\n" +
                       "```\n\n" +
                       "## 内容生成类目标示例\n\n" +
                       "```\n" +
                       "为我们的新产品写一份完整的营销方案，包括目标受众分析和推广策略\n" +
                       "```\n\n" +
                       "```\n" +
                       "创建一个技术博客文章，解释机器学习在电商中的应用，要求通俗易懂\n" +
                       "```\n\n" +
                       "## 使用方法\n\n" +
                       "1. 用自然语言清晰描述你的目标\n" +
                       "2. AI会自动分析并生成执行计划\n" +
                       "3. 系统按计划逐步执行，实时显示进度\n" +
                       "4. 如遇问题会自动尝试调整和恢复\n\n" +
                       "**提示**: 目标描述越具体，AI生成的计划越精准！";
                       
            case "data_analysis":
                return "# 数据分析目标示例\n\n" +
                       "**v2.0 推荐方式**（自然语言描述）：\n" +
                       "```\n" +
                       "分析销售数据文件，生成包含以下内容的专业报告：\n" +
                       "1. 数据概览和质量检查\n" +
                       "2. 销售趋势分析\n" +
                       "3. 产品性能排行\n" +
                       "4. 关键发现和改进建议\n" +
                       "```\n\n" +
                       "**兼容模式**（传统 Markdown 语法）：\n" +
                       "# 数据分析流程\n\n" +
                       "1. **读取数据** [tool:file_tool]\n" +
                       "   - 操作: get\n" +
                       "   - 文件名: {{input_file}}\n\n" +
                       "2. **数据分析** [tool:code_interpreter]\n" +
                       "   ```python\n" +
                       "   import pandas as pd\n" +
                       "   df = pd.read_csv('{{input_file}}')\n" +
                       "   print('数据概览:', df.shape)\n" +
                       "   print(df.describe())\n" +
                       "   ```\n\n" +
                       "3. **生成报告** [tool:llm_call]\n" +
                       "   - prompt: 基于数据分析结果生成专业报告";
                       
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
                return "# PromptFlow v2.0 基础使用指南\n\n" +
                       "## 推荐使用方式\n\n" +
                       "直接用自然语言描述你的目标，例如：\n\n" +
                       "```\n" +
                       "帮我处理这个任务：[具体描述你想要完成的事情]\n" +
                       "```\n\n" +
                       "AI会自动：\n" +
                       "1. 分析你的目标\n" +
                       "2. 选择合适的工具\n" +
                       "3. 生成执行计划\n" +
                       "4. 逐步执行任务\n" +
                       "5. 如遇问题自动调整\n\n" +
                       "## 兼容模式\n\n" +
                       "如果需要，仍可使用传统 Markdown 语法：\n\n" +
                       "1. **处理请求** [tool:llm_call]\n" +
                       "   - prompt: 请处理用户请求: {{user_input}}";
        }
    }
    
    /**
     * 将 Markdown 内容转换为自然语言目标（兼容模式）
     */
    private String convertMarkdownToGoal(String markdownContent) {
        // 简单的转换逻辑：提取主要步骤和意图
        StringBuilder goal = new StringBuilder("请帮我执行以下任务：");
        
        // 提取标题作为主要目标
        String[] lines = markdownContent.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("# ")) {
                goal.append(line.substring(2)).append("。");
                break;
            }
        }
        
        // 提取步骤描述
        goal.append("具体要求：");
        for (String line : lines) {
            line = line.trim();
            if (line.matches("^\\d+\\.\\s+\\*\\*.*\\*\\*.*")) {
                // 提取步骤描述，移除markdown格式
                String step = line.replaceAll("^\\d+\\.\\s+\\*\\*(.*?)\\*\\*.*", "$1");
                goal.append(step).append("，");
            }
        }
        
        // 如果没有提取到具体内容，使用原始内容的摘要
        if (goal.length() < 50) {
            goal = new StringBuilder("请根据以下描述执行相应任务：");
            goal.append(markdownContent.substring(0, Math.min(markdownContent.length(), 200)));
            if (markdownContent.length() > 200) {
                goal.append("...");
            }
        }
        
        return goal.toString();
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
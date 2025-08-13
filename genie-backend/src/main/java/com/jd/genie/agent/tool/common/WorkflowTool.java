package com.jd.genie.agent.tool.common;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.jd.genie.agent.tool.BaseTool;
import com.jd.genie.agent.workflow.WorkflowConfigLoader;
import com.jd.genie.agent.workflow.WorkflowDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 工作流管理工具
 * 提供工作流模板查询、管理等功能
 */
@Slf4j
@Component
public class WorkflowTool implements BaseTool {
    
    @Autowired
    private WorkflowConfigLoader workflowConfigLoader;
    
    private final String name = "workflow";
    private final String description = "工作流管理工具，用于查询和管理工作流模板";
    private final String parametersJsonSchema = buildParametersJsonSchema();
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getDescription() {
        return description;
    }
    
    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", name);
        params.put("description", description);
        params.put("parameters", JSON.parseObject(parametersJsonSchema));
        return params;
    }
    
    @Override
    public Object execute(Object args) {
        try {
            JSONObject jsonArgs = parseArgs(args);
            String command = jsonArgs.getString("command");
            
            if (command == null) {
                return createErrorResult("Missing required parameter: command");
            }
            
            switch (command.toLowerCase()) {
                case "list_templates":
                    return listTemplates();
                case "get_template":
                    return getTemplate(jsonArgs.getString("template_name"));
                case "template_info":
                    return getTemplateInfo(jsonArgs.getString("template_name"));
                case "reload":
                    return reloadConfig();
                default:
                    return createErrorResult("Unknown command: " + command);
            }
            
        } catch (Exception e) {
            log.error("Error executing workflow tool: {}", e.getMessage(), e);
            return createErrorResult("Workflow tool execution error: " + e.getMessage());
        }
    }
    
    /**
     * 列出所有可用的工作流模板
     */
    private Object listTemplates() {
        try {
            Set<String> templates = workflowConfigLoader.getAvailableTemplates();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("templates", templates);
            result.put("count", templates.size());
            result.put("message", String.format("Found %d workflow templates", templates.size()));
            
            return result;
            
        } catch (Exception e) {
            log.error("Error listing workflow templates: {}", e.getMessage(), e);
            return createErrorResult("Failed to list templates: " + e.getMessage());
        }
    }
    
    /**
     * 获取指定模板的完整定义
     */
    private Object getTemplate(String templateName) {
        if (templateName == null || templateName.trim().isEmpty()) {
            return createErrorResult("Template name is required");
        }
        
        try {
            WorkflowDefinition definition = workflowConfigLoader.getWorkflowByTemplate(templateName);
            if (definition == null) {
                return createErrorResult("Template not found: " + templateName);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("template_name", templateName);
            result.put("definition", definition);
            result.put("message", "Template retrieved successfully");
            
            return result;
            
        } catch (Exception e) {
            log.error("Error getting workflow template {}: {}", templateName, e.getMessage(), e);
            return createErrorResult("Failed to get template: " + e.getMessage());
        }
    }
    
    /**
     * 获取模板基本信息（不包含完整定义）
     */
    private Object getTemplateInfo(String templateName) {
        if (templateName == null || templateName.trim().isEmpty()) {
            return createErrorResult("Template name is required");
        }
        
        try {
            WorkflowDefinition definition = workflowConfigLoader.getWorkflowByTemplate(templateName);
            if (definition == null) {
                return createErrorResult("Template not found: " + templateName);
            }
            
            Map<String, Object> info = new HashMap<>();
            info.put("id", definition.getId());
            info.put("name", definition.getName());
            info.put("description", definition.getDescription());
            info.put("max_steps", definition.getMaxSteps());
            info.put("timeout_seconds", definition.getTimeoutSeconds());
            info.put("step_count", definition.getSteps() != null ? definition.getSteps().size() : 0);
            info.put("has_variables", definition.getVariables() != null && !definition.getVariables().isEmpty());
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("template_name", templateName);
            result.put("info", info);
            result.put("message", "Template info retrieved successfully");
            
            return result;
            
        } catch (Exception e) {
            log.error("Error getting workflow template info {}: {}", templateName, e.getMessage(), e);
            return createErrorResult("Failed to get template info: " + e.getMessage());
        }
    }
    
    /**
     * 重新加载工作流配置
     */
    private Object reloadConfig() {
        try {
            workflowConfigLoader.reload();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Workflow configuration reloaded successfully");
            result.put("template_count", workflowConfigLoader.getAvailableTemplates().size());
            
            return result;
            
        } catch (Exception e) {
            log.error("Error reloading workflow config: {}", e.getMessage(), e);
            return createErrorResult("Failed to reload config: " + e.getMessage());
        }
    }
    
    /**
     * 创建错误结果
     */
    private Map<String, Object> createErrorResult(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("error", message);
        return result;
    }
    
    /**
     * 解析参数
     */
    private JSONObject parseArgs(Object args) {
        if (args instanceof String) {
            return JSON.parseObject((String) args);
        } else if (args instanceof JSONObject) {
            return (JSONObject) args;
        } else if (args instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> argsMap = (Map<String, Object>) args;
            return new JSONObject(argsMap);
        } else {
            return JSON.parseObject(JSON.toJSONString(args));
        }
    }
    
    /**
     * 构建参数JSON Schema
     */
    private String buildParametersJsonSchema() {
        return "{\n" +
                "  \"type\": \"object\",\n" +
                "  \"properties\": {\n" +
                "    \"command\": {\n" +
                "      \"type\": \"string\",\n" +
                "      \"description\": \"操作命令\",\n" +
                "      \"enum\": [\"list_templates\", \"get_template\", \"template_info\", \"reload\"]\n" +
                "    },\n" +
                "    \"template_name\": {\n" +
                "      \"type\": \"string\",\n" +
                "      \"description\": \"工作流模板名称（get_template和template_info命令需要）\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"required\": [\"command\"]\n" +
                "}";
    }
}
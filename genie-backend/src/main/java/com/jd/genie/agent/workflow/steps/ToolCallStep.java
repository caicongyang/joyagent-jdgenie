package com.jd.genie.agent.workflow.steps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.agent.workflow.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.Map;

/**
 * 工具调用步骤
 */
@Slf4j
public class ToolCallStep extends WorkflowStep {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public ToolCallStep() {
        setType(StepType.TOOL_CALL);
    }
    
    public ToolCallStep(String id, String name, String toolName, Map<String, Object> toolArgs) {
        this();
        setId(id);
        setName(name);
        setParameter("toolName", toolName);
        setParameter("toolArgs", toolArgs);
    }
    
    @Override
    public StepResult execute(WorkflowContext context) {
        Date startTime = new Date();
        
        try {
            String toolName = getParameter("toolName", String.class);
            Object toolArgs = getParameters().get("toolArgs");
            
            if (toolName == null || toolName.trim().isEmpty()) {
                return StepResult.failure("Tool name is required for ToolCallStep")
                        .withExecutionTime(startTime, new Date());
            }
            
            // 获取工具集合
            ToolCollection toolCollection = getToolCollection(context);
            if (toolCollection == null) {
                return StepResult.failure("ToolCollection not available in context")
                        .withExecutionTime(startTime, new Date());
            }
            
            // 记录工具调用开始
            context.addEvent("TOOL_CALL_START", 
                    String.format("Calling tool: %s with args: %s", toolName, toolArgs));
            
            // 执行工具
            Object result = toolCollection.execute(toolName, toolArgs);
            
            // 记录工具调用成功
            context.addEvent("TOOL_CALL_SUCCESS", 
                    String.format("Tool %s executed successfully", toolName));
            
            // 保存结果到上下文
            context.setStepResult(getId(), result);
            
            return StepResult.success(result)
                    .withExecutionTime(startTime, new Date())
                    .withProperty("toolName", toolName)
                    .withProperty("toolArgs", toolArgs);
            
        } catch (Exception e) {
            log.error("Error executing tool call step {}: {}", getId(), e.getMessage(), e);
            
            context.addEvent("TOOL_CALL_ERROR", 
                    String.format("Tool call failed: %s", e.getMessage()));
            
            return StepResult.failure("Tool call failed: " + e.getMessage(), e)
                    .withExecutionTime(startTime, new Date());
        }
    }
    
    /**
     * 从上下文获取工具集合
     */
    private ToolCollection getToolCollection(WorkflowContext context) {
        if (context.getAgentContext() != null && 
            context.getAgentContext().getAvailableTools() != null) {
            return context.getAgentContext().getAvailableTools();
        }
        return null;
    }
    
    @Override
    public void validate() {
        super.validate();
        
        String toolName = getParameter("toolName", String.class);
        if (toolName == null || toolName.trim().isEmpty()) {
            throw new IllegalArgumentException("Tool name is required for ToolCallStep: " + getId());
        }
    }
    
    /**
     * 创建工具调用步骤的便捷方法
     */
    public static ToolCallStep create(String id, String name, String toolName, Map<String, Object> args) {
        ToolCallStep step = new ToolCallStep(id, name, toolName, args);
        step.validate();
        return step;
    }
    
    /**
     * 创建简单的工具调用步骤
     */
    public static ToolCallStep simple(String id, String toolName) {
        return create(id, "Call " + toolName, toolName, Map.of());
    }
}
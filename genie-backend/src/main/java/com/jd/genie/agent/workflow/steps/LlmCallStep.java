package com.jd.genie.agent.workflow.steps;

import com.jd.genie.agent.dto.Message;
import com.jd.genie.agent.enums.RoleType;
import com.jd.genie.agent.llm.LLM;
import com.jd.genie.agent.workflow.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.Map;

/**
 * LLM调用步骤
 */
@Slf4j
public class LlmCallStep extends WorkflowStep {
    
    public LlmCallStep() {
        setType(StepType.LLM_CALL);
    }
    
    public LlmCallStep(String id, String name, String prompt) {
        this();
        setId(id);
        setName(name);
        setParameter("prompt", prompt);
    }
    
    public LlmCallStep(String id, String name, String prompt, String systemPrompt) {
        this(id, name, prompt);
        setParameter("systemPrompt", systemPrompt);
    }
    
    @Override
    public StepResult execute(WorkflowContext context) {
        Date startTime = new Date();
        
        try {
            String prompt = getParameter("prompt", String.class);
            String systemPrompt = getParameter("systemPrompt", String.class);
            
            if (prompt == null || prompt.trim().isEmpty()) {
                return StepResult.failure("Prompt is required for LlmCallStep")
                        .withExecutionTime(startTime, new Date());
            }
            
            // 获取LLM实例
            LLM llm = getLLM(context);
            if (llm == null) {
                return StepResult.failure("LLM not available in context")
                        .withExecutionTime(startTime, new Date());
            }
            
            // 处理prompt中的变量替换
            String processedPrompt = processVariables(prompt, context);
            String processedSystemPrompt = systemPrompt != null ? 
                    processVariables(systemPrompt, context) : null;
            
            context.addEvent("LLM_CALL_START", 
                    String.format("Calling LLM with prompt length: %d", processedPrompt.length()));
            
            // 调用LLM
            String response = callLLM(llm, processedPrompt, processedSystemPrompt, context);
            
            context.addEvent("LLM_CALL_SUCCESS", 
                    String.format("LLM call completed, response length: %d", 
                            response != null ? response.length() : 0));
            
            // 保存结果到上下文
            context.setStepResult(getId(), response);
            context.setVariable(getId() + "_response", response);
            
            return StepResult.success(response)
                    .withExecutionTime(startTime, new Date())
                    .withProperty("promptLength", processedPrompt.length())
                    .withProperty("responseLength", response != null ? response.length() : 0);
            
        } catch (Exception e) {
            log.error("Error executing LLM call step {}: {}", getId(), e.getMessage(), e);
            
            context.addEvent("LLM_CALL_ERROR", 
                    String.format("LLM call failed: %s", e.getMessage()));
            
            return StepResult.failure("LLM call failed: " + e.getMessage(), e)
                    .withExecutionTime(startTime, new Date());
        }
    }
    
    /**
     * 处理prompt中的变量替换
     */
    private String processVariables(String text, WorkflowContext context) {
        if (text == null) {
            return null;
        }
        
        String result = text;
        
        // 替换工作流变量 ${variableName}
        for (Map.Entry<String, Object> entry : context.getVariables().entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            if (result.contains(placeholder)) {
                String value = entry.getValue() != null ? entry.getValue().toString() : "";
                result = result.replace(placeholder, value);
            }
        }
        
        // 替换步骤结果 ${step.stepId}
        for (Map.Entry<String, Object> entry : context.getStepResults().entrySet()) {
            String placeholder = "${step." + entry.getKey() + "}";
            if (result.contains(placeholder)) {
                String value = entry.getValue() != null ? entry.getValue().toString() : "";
                result = result.replace(placeholder, value);
            }
        }
        
        return result;
    }
    
    /**
     * 调用LLM
     */
    private String callLLM(LLM llm, String prompt, String systemPrompt, WorkflowContext context) {
        try {
            // 创建消息列表
            java.util.List<Message> messages = new java.util.ArrayList<>();
            
            // 添加系统提示词
            if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
                messages.add(Message.systemMessage(systemPrompt, null));
            }
            
            // 添加用户提示词
            messages.add(Message.userMessage(prompt, null));
            
            // 调用LLM
            return llm.generate(messages, null, null);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to call LLM", e);
        }
    }
    
    /**
     * 从上下文获取LLM实例
     */
    private LLM getLLM(WorkflowContext context) {
        if (context.getAgentContext() != null && 
            context.getAgentContext().getLlm() != null) {
            return context.getAgentContext().getLlm();
        }
        return null;
    }
    
    @Override
    public void validate() {
        super.validate();
        
        String prompt = getParameter("prompt", String.class);
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt is required for LlmCallStep: " + getId());
        }
    }
    
    /**
     * 创建LLM调用步骤的便捷方法
     */
    public static LlmCallStep create(String id, String name, String prompt) {
        LlmCallStep step = new LlmCallStep(id, name, prompt);
        step.validate();
        return step;
    }
    
    /**
     * 创建带系统提示词的LLM调用步骤
     */
    public static LlmCallStep create(String id, String name, String prompt, String systemPrompt) {
        LlmCallStep step = new LlmCallStep(id, name, prompt, systemPrompt);
        step.validate();
        return step;
    }
    
    /**
     * 设置温度参数
     */
    public LlmCallStep temperature(double temperature) {
        setParameter("temperature", temperature);
        return this;
    }
    
    /**
     * 设置最大token数
     */
    public LlmCallStep maxTokens(int maxTokens) {
        setParameter("maxTokens", maxTokens);
        return this;
    }
}
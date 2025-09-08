package com.jd.genie.agent.promptflow.engine;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.promptflow.model.ExecutionContext;
import com.jd.genie.agent.promptflow.model.ExecutionPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 上下文管理器
 */
@Slf4j
@Component
public class ContextManager {
    
    /**
     * 创建执行上下文
     */
    public ExecutionContext createExecutionContext(ExecutionPlan plan, AgentContext agentContext) {
        ExecutionContext context = ExecutionContext.builder()
                .plan(plan)
                .startTime(System.currentTimeMillis())
                .build();
        
        // 设置全局变量
        if (plan.getGlobalVariables() != null) {
            context.setGlobalVariables(new HashMap<>(plan.getGlobalVariables()));
        }
        
        // 设置系统内置变量
        setupBuiltinVariables(context, agentContext);
        
        log.debug("创建执行上下文，计划ID: {}", plan.getPlanId());
        
        return context;
    }
    
    /**
     * 设置系统内置变量
     */
    private void setupBuiltinVariables(ExecutionContext context, AgentContext agentContext) {
        Map<String, Object> builtinVars = new HashMap<>();
        
        // 时间相关变量
        builtinVars.put("current_date", java.time.LocalDate.now().toString());
        builtinVars.put("current_time", java.time.LocalDateTime.now().toString());
        builtinVars.put("timestamp", System.currentTimeMillis());
        
        // 用户输入
        if (agentContext.getQuery() != null) {
            builtinVars.put("user_input", agentContext.getQuery());
        }
        
        // 用户上传文件
        if (agentContext.getProductFiles() != null && !agentContext.getProductFiles().isEmpty()) {
            String firstFile = agentContext.getProductFiles().get(0).getFileName();
            builtinVars.put("user_file", firstFile);
            builtinVars.put("uploaded_file", firstFile);
        }
        
        // Agent 相关信息
        builtinVars.put("agent_id", "prompt_flow_v2");
        builtinVars.put("execution_id", java.util.UUID.randomUUID().toString());
        
        context.mergeVariables(builtinVars);
        
        log.debug("设置了 {} 个内置变量", builtinVars.size());
    }
}
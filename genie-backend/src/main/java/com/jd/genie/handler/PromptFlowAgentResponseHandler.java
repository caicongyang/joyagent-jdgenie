package com.jd.genie.handler;

import com.jd.genie.agent.enums.AgentType;
import com.jd.genie.model.multi.EventResult;
import com.jd.genie.model.req.AgentRequest;
import com.jd.genie.model.response.AgentResponse;
import com.jd.genie.model.response.GptProcessResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PromptFlow 智能体响应处理器
 */
@Slf4j
@Component
public class PromptFlowAgentResponseHandler extends BaseAgentResponseHandler {
    
    @Override
    public GptProcessResult handle(AgentRequest request, AgentResponse response, 
                                 List<AgentResponse> agentRespList, EventResult eventResult) {
        
        log.info("Handling PromptFlow agent response for request: {}", request.getRequestId());
        
        // 使用父类的基础处理逻辑
        GptProcessResult result = super.handle(request, response, agentRespList, eventResult);
        
        // 添加 PromptFlow 特定的元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("agent_type", "prompt_flow");
        metadata.put("flow_status", "completed");
        metadata.put("timestamp", System.currentTimeMillis());
        
        // 添加流程统计信息
        if (agentRespList != null && !agentRespList.isEmpty()) {
            metadata.put("total_steps", agentRespList.size());
        }
        
        // 设置结果映射
        if (result.getResultMap() == null) {
            result.setResultMap(new HashMap<>());
        }
        result.getResultMap().putAll(metadata);
        
        // 处理错误情况
        if (response != null && response.getContent() != null && 
            response.getContent().startsWith("PromptFlow执行失败")) {
            result.setStatus("error");
            result.setResponse("ERROR: " + response.getContent());
            log.error("PromptFlow execution failed: {}", response.getContent());
        }
        
        return result;
    }
}
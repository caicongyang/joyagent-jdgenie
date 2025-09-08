package com.jd.genie.agent.promptflow.planner.analyzer;

import com.jd.genie.agent.tool.BaseTool;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.agent.promptflow.planner.ToolCapabilities;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具能力分析器
 */
@Slf4j
@Component
public class ToolAnalyzer {
    
    /**
     * 分析可用工具能力
     */
    public ToolCapabilities analyzeAvailableTools(ToolCollection toolCollection) {
        Map<String, ToolInfo> tools = new HashMap<>();
        
        if (toolCollection != null && toolCollection.getToolMap() != null) {
            for (Map.Entry<String, BaseTool> entry : toolCollection.getToolMap().entrySet()) {
                String toolName = entry.getKey();
                BaseTool tool = entry.getValue();
                
                ToolInfo toolInfo = ToolInfo.builder()
                        .name(toolName)
                        .description(tool.getDescription())
                        .parameters(tool.toParams())
                        .build();
                
                tools.put(toolName, toolInfo);
            }
        }
        
        log.info("分析到 {} 个可用工具", tools.size());
        
        return ToolCapabilities.builder()
                .tools(tools)
                .build();
    }
}
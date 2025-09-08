package com.jd.genie.agent.promptflow.planner;

import com.jd.genie.agent.promptflow.planner.analyzer.ToolInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工具能力描述
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCapabilities {
    
    /**
     * 工具信息映射
     */
    private Map<String, ToolInfo> tools;
    
    /**
     * 检查是否包含指定工具
     */
    public boolean hasToolHasTool(String toolName) {
        return tools != null && tools.containsKey(toolName);
    }
    
    /**
     * 获取工具信息
     */
    public ToolInfo getToolInfo(String toolName) {
        return tools != null ? tools.get(toolName) : null;
    }
    
    /**
     * 获取工具能力描述文本
     */
    public String getDescription() {
        if (tools == null || tools.isEmpty()) {
            return "暂无可用工具";
        }
        
        StringBuilder sb = new StringBuilder();
        for (ToolInfo toolInfo : tools.values()) {
            sb.append("- **").append(toolInfo.getName()).append("**: ")
              .append(toolInfo.getDescription())
              .append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * 获取工具数量
     */
    public int getToolCount() {
        return tools != null ? tools.size() : 0;
    }
}


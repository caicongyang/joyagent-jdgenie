package com.jd.genie.agent.promptflow.planner.analyzer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工具信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolInfo {
    
    /**
     * 工具名称
     */
    private String name;
    
    /**
     * 工具描述
     */
    private String description;
    
    /**
     * 工具参数规范
     */
    private Map<String, Object> parameters;
}
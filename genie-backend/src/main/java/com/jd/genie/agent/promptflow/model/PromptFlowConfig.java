package com.jd.genie.agent.promptflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * PromptFlow 配置模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptFlowConfig {
    private String name;
    private String version;
    private String description;
    private Map<String, Object> variables;
    private FlowDefinition flowDefinition;
    private Map<String, Object> settings;
}
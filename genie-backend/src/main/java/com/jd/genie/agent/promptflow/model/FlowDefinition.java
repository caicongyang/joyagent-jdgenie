package com.jd.genie.agent.promptflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 流程定义
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowDefinition {
    private String startNode;
    private List<FlowNode> nodes;
    private Map<String, Object> globalVariables;
}
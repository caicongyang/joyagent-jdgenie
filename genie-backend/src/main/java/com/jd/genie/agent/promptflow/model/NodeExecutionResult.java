package com.jd.genie.agent.promptflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 节点执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeExecutionResult {
    private String nodeId;
    private boolean success;
    private String output;
    private String error;
    private FlowContext context;
    private String nextNodeId;
    private long executionTime;
}
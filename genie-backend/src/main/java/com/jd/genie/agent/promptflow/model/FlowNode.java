package com.jd.genie.agent.promptflow.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 流程节点基础类
 */
@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = PromptNode.class, name = "PROMPT"),
    @JsonSubTypes.Type(value = ToolNode.class, name = "TOOL"),
    @JsonSubTypes.Type(value = MarkdownNode.class, name = "MARKDOWN"),
    @JsonSubTypes.Type(value = ControlNode.class, name = "CONTROL")
})
public abstract class FlowNode {
    private String id;
    private String name;
    private String description;
    private NodeType type;
    private Map<String, Object> properties;
    private List<String> inputs;
    private List<String> outputs;
    private String nextNode;
    
    public abstract NodeExecutionResult execute(FlowContext context);
}


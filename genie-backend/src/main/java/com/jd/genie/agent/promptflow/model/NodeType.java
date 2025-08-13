package com.jd.genie.agent.promptflow.model;

/**
 * 节点类型枚举
 */
public enum NodeType {
    PROMPT,     // Prompt处理节点
    TOOL,       // 工具调用节点
    MARKDOWN,   // Markdown处理节点
    CONTROL,    // 流程控制节点
    PARALLEL,   // 并行执行节点
    CONDITION,  // 条件分支节点
    LOOP,       // 循环节点
    MERGE       // 结果合并节点
}
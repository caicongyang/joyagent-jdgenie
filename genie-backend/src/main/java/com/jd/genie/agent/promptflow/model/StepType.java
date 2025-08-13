package com.jd.genie.agent.promptflow.model;

/**
 * 步骤类型枚举
 */
public enum StepType {
    PROMPT,      // LLM对话步骤
    TOOL,        // 工具调用步骤
    MARKDOWN,    // Markdown生成步骤
    PARALLEL,    // 并行执行步骤
    CONDITION,   // 条件分支步骤
    LOOP,        // 循环步骤
    UNKNOWN      // 未知类型
}
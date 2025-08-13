package com.jd.genie.agent.workflow;

/**
 * 工作流步骤类型枚举
 */
public enum StepType {
    
    /**
     * 顺序步骤
     */
    SEQUENTIAL("sequential", "顺序步骤"),
    
    /**
     * 并行步骤
     */
    PARALLEL("parallel", "并行步骤"),
    
    /**
     * 条件分支
     */
    CONDITIONAL("conditional", "条件分支"),
    
    /**
     * 循环步骤
     */
    LOOP("loop", "循环步骤"),
    
    /**
     * 工具调用
     */
    TOOL_CALL("tool_call", "工具调用"),
    
    /**
     * 人工任务
     */
    HUMAN_TASK("human_task", "人工任务"),
    
    /**
     * 子工作流
     */
    SUB_WORKFLOW("sub_workflow", "子工作流"),
    
    /**
     * LLM调用
     */
    LLM_CALL("llm_call", "LLM调用");
    
    private final String code;
    private final String description;
    
    StepType(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 根据代码获取类型
     */
    public static StepType fromCode(String code) {
        for (StepType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid step type code: " + code);
    }
}
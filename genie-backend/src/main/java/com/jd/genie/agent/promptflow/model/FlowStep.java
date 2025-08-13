package com.jd.genie.agent.promptflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流程步骤模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowStep {
    private int stepNumber;
    private String name;
    private StepType type;
    private String originalType;
    private String content;
    private String toolName;
    private String condition;
}


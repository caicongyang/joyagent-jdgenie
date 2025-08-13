package com.jd.genie.agent.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作流定义
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDefinition {
    
    /**
     * 工作流ID
     */
    private String id;
    
    /**
     * 工作流名称
     */
    private String name;
    
    /**
     * 工作流描述
     */
    private String description;
    
    /**
     * 工作流步骤列表
     */
    private List<WorkflowStep> steps;
    
    /**
     * 工作流变量
     */
    @Builder.Default
    private Map<String, Object> variables = new ConcurrentHashMap<>();
    
    /**
     * 工作流触发器
     */
    private WorkflowTrigger trigger;
    
    /**
     * 最大执行步数
     */
    @Builder.Default
    private int maxSteps = 50;
    
    /**
     * 超时时间（秒）
     */
    @Builder.Default
    private long timeoutSeconds = 1800;
    
    /**
     * 创建简单的顺序工作流
     */
    public static WorkflowDefinition createSequential(String name, List<WorkflowStep> steps) {
        return WorkflowDefinition.builder()
                .id(name.toLowerCase().replace(" ", "-"))
                .name(name)
                .description("Sequential workflow: " + name)
                .steps(steps)
                .build();
    }
    
    /**
     * 获取指定ID的步骤
     */
    public WorkflowStep getStepById(String stepId) {
        return steps.stream()
                .filter(step -> stepId.equals(step.getId()))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 获取第一个步骤
     */
    public WorkflowStep getFirstStep() {
        return steps.isEmpty() ? null : steps.get(0);
    }
    
    /**
     * 检查是否还有未执行的步骤
     */
    public boolean hasNextStep(WorkflowContext context) {
        return steps.stream()
                .anyMatch(step -> context.getStepStatus(step.getId()) == StepStatus.PENDING);
    }
}
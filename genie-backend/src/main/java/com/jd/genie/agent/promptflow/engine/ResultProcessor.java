package com.jd.genie.agent.promptflow.engine;

import com.jd.genie.agent.promptflow.model.ExecutionContext;
import com.jd.genie.agent.promptflow.model.StepResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 结果处理器
 */
@Slf4j
@Component
public class ResultProcessor {
    
    /**
     * 生成最终输出结果
     */
    public String generateFinalOutput(ExecutionContext context) {
        StringBuilder result = new StringBuilder();
        
        // 添加执行摘要
        result.append("## 执行摘要\n\n");
        result.append("✅ 执行目标: ").append(context.getPlan().getGoal()).append("\n");
        result.append("📊 执行步骤: ").append(context.getPlan().getSteps().size()).append(" 个\n");
        result.append("⏱️ 执行时间: ").append(calculateTotalTime(context)).append("ms\n\n");
        
        // 添加步骤执行结果
        result.append("## 执行过程\n\n");
        
        for (int i = 0; i < context.getPlan().getSteps().size(); i++) {
            var step = context.getPlan().getSteps().get(i);
            StepResult stepResult = context.getStepResult(step.getId());
            
            if (stepResult != null) {
                result.append(String.format("%d. **%s** - %s\n", 
                        i + 1, step.getDescription(), stepResult.isSuccess() ? "✅ 成功" : "❌ 失败"));
                
                if (stepResult.isSuccess() && stepResult.getOutput() != null) {
                    String output = String.valueOf(stepResult.getOutput());
                    if (output.length() > 500) {
                        output = output.substring(0, 500) + "...";
                    }
                    result.append("   - 结果: ").append(output).append("\n");
                } else if (!stepResult.isSuccess()) {
                    result.append("   - 错误: ").append(stepResult.getError()).append("\n");
                }
                
                result.append("   - 耗时: ").append(stepResult.getExecutionTime()).append("ms\n");
            }
            
            result.append("\n");
        }
        
        // 获取最后一个成功步骤的输出作为主要结果
        String mainResult = getMainResult(context);
        if (mainResult != null && !mainResult.trim().isEmpty()) {
            result.append("## 最终结果\n\n");
            result.append(mainResult);
        }
        
        return result.toString();
    }
    
    /**
     * 获取主要结果（通常是最后一个成功步骤的输出）
     */
    private String getMainResult(ExecutionContext context) {
        // 倒序查找最后一个成功的步骤
        List<String> stepIds = context.getPlan().getSteps().stream()
                .map(step -> step.getId())
                .collect(Collectors.toList());
        
        for (int i = stepIds.size() - 1; i >= 0; i--) {
            String stepId = stepIds.get(i);
            StepResult result = context.getStepResult(stepId);
            
            if (result != null && result.isSuccess() && result.getOutput() != null) {
                String output = String.valueOf(result.getOutput());
                if (!output.trim().isEmpty()) {
                    return output;
                }
            }
        }
        
        return null;
    }
    
    /**
     * 计算总执行时间
     */
    private long calculateTotalTime(ExecutionContext context) {
        return System.currentTimeMillis() - context.getStartTime();
    }
}
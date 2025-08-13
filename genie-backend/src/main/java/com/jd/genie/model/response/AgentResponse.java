package com.jd.genie.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Assistant返回
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {
    /**
     * Get message ID
     */
    public String getMessageId() {
        return messageId;
    }
    
    /**
     * Get message type
     */
    public String getMessageType() {
        return messageType;
    }
    
    /**
     * Get result
     */
    public String getResult() {
        return result;
    }
    
    /**
     * Get finish
     */
    public Boolean getFinish() {
        return finish;
    }
    
    /**
     * Get is final
     */
    public Boolean getIsFinal() {
        return isFinal;
    }
    
    /**
     * Get result map
     */
    public Map<String, Object> getResultMap() {
        return resultMap;
    }
    
    /**
     * Get plan
     */
    public Plan getPlan() {
        return plan;
    }
    
    /**
     * Get plan thought
     */
    public String getPlanThought() {
        return planThought;
    }
    
    /**
     * Get content - for workflow compatibility
     */
    public String getContent() {
        return result;
    }
    
    /**
     * Get data - for workflow compatibility
     */
    public Object getData() {
        return resultMap;
    }
    
    /**
     * Get type - for workflow compatibility
     */
    public String getType() {
        return messageType;
    }
    private String requestId;
    private String messageId;
    private Boolean isFinal;
    private String messageType;
    private String digitalEmployee;
    private String messageTime;
    private String planThought;
    private Plan plan;
    private String task;
    private String taskSummary;
    private String toolThought;
    private ToolResult toolResult;
    private Map<String, Object> resultMap;
    private String result;
    private Boolean finish;
    private Map<String, String> ext;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Plan {
        private String title;
        private List<String> stages;
        private List<String> steps;
        private List<String> stepStatus;
        private List<String> notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolResult {
        private String toolName;
        private Map<String, Object> toolParam;
        private String toolResult;
    }

    public static Plan formatSteps(Plan plan) {
        Plan newplan = new Plan();
        newplan.setTitle(plan.title);
        newplan.setSteps(new ArrayList<>());
        newplan.setStages(new ArrayList<>());
        newplan.setStepStatus(new ArrayList<>());
        newplan.setNotes(new ArrayList<>());
        Pattern pattern = Pattern.compile("执行顺序(\\d+)\\.\\s?([\\w\\W]*)\\s?[：:](.*)");
        for (int i = 0; i < plan.getSteps().size(); i++) {
            newplan.getStepStatus().add(plan.getStepStatus().get(i));
            newplan.getNotes().add(plan.getNotes().get(i));

            String step = plan.getSteps().get(i);
            Matcher matcher = pattern.matcher(step);
            if (matcher.find()) {
                newplan.getSteps().add(matcher.group(3).trim());
                newplan.getStages().add(matcher.group(2).trim());
            } else {
                newplan.getSteps().add(step);
                newplan.getStages().add("");
            }
        }
        return newplan;
    }
}

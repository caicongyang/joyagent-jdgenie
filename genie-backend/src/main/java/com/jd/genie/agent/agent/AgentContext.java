package com.jd.genie.agent.agent;

import com.jd.genie.agent.dto.File;
import com.jd.genie.agent.printer.Printer;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.model.dto.FileInformation;
import com.jd.genie.model.req.AgentRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Data
@Builder
@Slf4j
@NoArgsConstructor
@AllArgsConstructor
public class AgentContext {
    /**
     * Get available tools
     */
    public ToolCollection getAvailableTools() {
        return toolCollection;
    }
    
    /**
     * Get LLM
     */
    public com.jd.genie.agent.llm.LLM getLlm() {
        // This is a stub implementation for compatibility
        return null;
    }
    String requestId;
    String sessionId;
    String query;
    String task;
    Printer printer;
    ToolCollection toolCollection;
    String dateInfo;
    List<File> productFiles;
    Boolean isStream;
    String streamMessageType;
    String sopPrompt;
    String basePrompt;
    Integer agentType;
    List<File> taskProductFiles;
    
    // PromptFlow specific fields
    String inlineMarkdown;
    String markdownFlow;
    Map<String, Object> flowVariables;
}
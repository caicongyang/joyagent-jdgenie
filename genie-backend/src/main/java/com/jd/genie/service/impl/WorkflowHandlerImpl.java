package com.jd.genie.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.agent.WorkflowAgent;
import com.jd.genie.agent.enums.AgentType;
import com.jd.genie.agent.workflow.WorkflowConfigLoader;
import com.jd.genie.agent.workflow.WorkflowDefinition;
import com.jd.genie.agent.workflow.WorkflowStep;
import com.jd.genie.agent.workflow.WorkflowTrigger;
import com.jd.genie.agent.workflow.steps.LlmCallStep;
import com.jd.genie.agent.workflow.steps.SequentialStep;
import com.jd.genie.agent.workflow.steps.ToolCallStep;
import com.jd.genie.config.GenieConfig;
import com.jd.genie.model.req.AgentRequest;
import com.jd.genie.service.AgentHandlerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流处理服务实现
 */
@Slf4j
@Component
public class WorkflowHandlerImpl implements AgentHandlerService {
    
    @Autowired
    private GenieConfig genieConfig;
    
    @Autowired
    private WorkflowConfigLoader workflowConfigLoader;
    
    @Override
    public String handle(AgentContext agentContext, AgentRequest request) {
        try {
            log.info("{} Starting workflow handler for request", request.getRequestId());
            
            // 解析或创建工作流定义
            WorkflowDefinition workflowDefinition = createWorkflowDefinition(request, agentContext);
            
            // 创建工作流Agent
            WorkflowAgent workflowAgent = new WorkflowAgent(agentContext, workflowDefinition);
            
            // 配置Agent属性
            configureWorkflowAgent(workflowAgent, request);
            
            // 执行工作流
            log.info("{} Executing workflow: {}", request.getRequestId(), workflowDefinition.getName());
            String result = workflowAgent.run(request.getQuery());
            
            log.info("{} Workflow execution completed", request.getRequestId());
            return result;
            
        } catch (Exception e) {
            log.error("{} Error in workflow handler: {}", request.getRequestId(), e.getMessage(), e);
            return "Workflow execution failed: " + e.getMessage();
        }
    }
    
    @Override
    public Boolean support(AgentContext agentContext, AgentRequest request) {
        return AgentType.WORKFLOW.getValue().equals(request.getAgentType());
    }
    
    /**
     * 创建工作流定义
     */
    private WorkflowDefinition createWorkflowDefinition(AgentRequest request, AgentContext agentContext) {
        // 1. 检查是否指定了工作流模板
        if (StringUtils.hasText(request.getWorkflowTemplate())) {
            WorkflowDefinition templateDefinition = workflowConfigLoader.getWorkflowByTemplate(request.getWorkflowTemplate());
            if (templateDefinition != null) {
                log.info("{} Using workflow template: {}", request.getRequestId(), request.getWorkflowTemplate());
                return applyVariables(templateDefinition, request);
            } else {
                log.warn("{} Workflow template not found: {}, falling back to other methods", 
                        request.getRequestId(), request.getWorkflowTemplate());
            }
        }
        
        // 2. 检查是否有预定义的工作流JSON
        if (StringUtils.hasText(request.getWorkflowDefinition())) {
            return parseWorkflowDefinition(request.getWorkflowDefinition());
        }
        
        // 3. 尝试根据查询内容匹配模板
        WorkflowDefinition matchedTemplate = findMatchingTemplate(request.getQuery());
        if (matchedTemplate != null) {
            log.info("{} Found matching template for query", request.getRequestId());
            return applyVariables(matchedTemplate, request);
        }
        
        // 4. 根据查询内容智能创建工作流
        return createIntelligentWorkflow(request, agentContext);
    }
    
    /**
     * 解析工作流定义
     */
    private WorkflowDefinition parseWorkflowDefinition(String workflowDefJson) {
        try {
            JSONObject json = JSON.parseObject(workflowDefJson);
            
            WorkflowDefinition definition = WorkflowDefinition.builder()
                    .id(json.getString("id"))
                    .name(json.getString("name"))
                    .description(json.getString("description"))
                    .maxSteps(json.getIntValue("maxSteps") > 0 ? json.getIntValue("maxSteps") : 50)
                    .timeoutSeconds(json.getLongValue("timeoutSeconds") > 0 ? json.getLongValue("timeoutSeconds") : 1800)
                    .trigger(WorkflowTrigger.manual())
                    .build();
            
            // 解析步骤
            JSONArray stepsArray = json.getJSONArray("steps");
            List<WorkflowStep> steps = new ArrayList<>();
            
            for (int i = 0; i < stepsArray.size(); i++) {
                JSONObject stepJson = stepsArray.getJSONObject(i);
                WorkflowStep step = parseWorkflowStep(stepJson);
                if (step != null) {
                    steps.add(step);
                }
            }
            
            definition.setSteps(steps);
            
            // 解析变量
            JSONObject variablesJson = json.getJSONObject("variables");
            if (variablesJson != null) {
                Map<String, Object> variables = new HashMap<>();
                for (String key : variablesJson.keySet()) {
                    variables.put(key, variablesJson.get(key));
                }
                definition.setVariables(variables);
            }
            
            return definition;
            
        } catch (Exception e) {
            log.error("Error parsing workflow definition: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse workflow definition", e);
        }
    }
    
    /**
     * 解析工作流步骤
     */
    private WorkflowStep parseWorkflowStep(JSONObject stepJson) {
        String type = stepJson.getString("type");
        String id = stepJson.getString("id");
        String name = stepJson.getString("name");
        
        switch (type.toLowerCase()) {
            case "tool_call":
                return parseToolCallStep(stepJson, id, name);
            case "llm_call":
                return parseLlmCallStep(stepJson, id, name);
            case "sequential":
                return parseSequentialStep(stepJson, id, name);
            default:
                log.warn("Unsupported step type: {}", type);
                return null;
        }
    }
    
    /**
     * 解析工具调用步骤
     */
    private ToolCallStep parseToolCallStep(JSONObject stepJson, String id, String name) {
        String toolName = stepJson.getString("toolName");
        JSONObject toolArgsJson = stepJson.getJSONObject("toolArgs");
        
        Map<String, Object> toolArgs = new HashMap<>();
        if (toolArgsJson != null) {
            for (String key : toolArgsJson.keySet()) {
                toolArgs.put(key, toolArgsJson.get(key));
            }
        }
        
        return new ToolCallStep(id, name, toolName, toolArgs);
    }
    
    /**
     * 解析LLM调用步骤
     */
    private LlmCallStep parseLlmCallStep(JSONObject stepJson, String id, String name) {
        String prompt = stepJson.getString("prompt");
        String systemPrompt = stepJson.getString("systemPrompt");
        
        return new LlmCallStep(id, name, prompt, systemPrompt);
    }
    
    /**
     * 解析顺序步骤
     */
    private SequentialStep parseSequentialStep(JSONObject stepJson, String id, String name) {
        JSONArray subStepsArray = stepJson.getJSONArray("subSteps");
        List<WorkflowStep> subSteps = new ArrayList<>();
        
        if (subStepsArray != null) {
            for (int i = 0; i < subStepsArray.size(); i++) {
                JSONObject subStepJson = subStepsArray.getJSONObject(i);
                WorkflowStep subStep = parseWorkflowStep(subStepJson);
                if (subStep != null) {
                    subSteps.add(subStep);
                }
            }
        }
        
        return new SequentialStep(id, name, subSteps);
    }
    
    /**
     * 智能创建工作流
     */
    private WorkflowDefinition createIntelligentWorkflow(AgentRequest request, AgentContext agentContext) {
        String query = request.getQuery();
        log.info("{} Creating intelligent workflow for query: {}", request.getRequestId(), query);
        
        // 分析查询意图，创建相应的工作流
        List<WorkflowStep> steps = new ArrayList<>();
        
        if (isDataAnalysisQuery(query)) {
            steps = createDataAnalysisWorkflow(request);
        } else if (isResearchQuery(query)) {
            steps = createResearchWorkflow(request);
        } else if (isProblemSolvingQuery(query)) {
            steps = createProblemSolvingWorkflow(request);
        } else {
            steps = createDefaultWorkflow(request);
        }
        
        return WorkflowDefinition.builder()
                .id("auto-workflow-" + System.currentTimeMillis())
                .name("Auto-generated Workflow")
                .description("Intelligently generated workflow for: " + query)
                .steps(steps)
                .maxSteps(genieConfig.getPlannerMaxSteps())
                .trigger(WorkflowTrigger.manual())
                .build();
    }
    
    /**
     * 判断是否为数据分析查询
     */
    private boolean isDataAnalysisQuery(String query) {
        String lowerQuery = query.toLowerCase();
        return lowerQuery.contains("分析") || lowerQuery.contains("数据") || 
               lowerQuery.contains("统计") || lowerQuery.contains("图表") ||
               lowerQuery.contains("analyze") || lowerQuery.contains("data");
    }
    
    /**
     * 判断是否为研究查询
     */
    private boolean isResearchQuery(String query) {
        String lowerQuery = query.toLowerCase();
        return lowerQuery.contains("研究") || lowerQuery.contains("调研") || 
               lowerQuery.contains("搜索") || lowerQuery.contains("查找") ||
               lowerQuery.contains("research") || lowerQuery.contains("search");
    }
    
    /**
     * 判断是否为问题解决查询
     */
    private boolean isProblemSolvingQuery(String query) {
        String lowerQuery = query.toLowerCase();
        return lowerQuery.contains("解决") || lowerQuery.contains("方案") || 
               lowerQuery.contains("如何") || lowerQuery.contains("怎么") ||
               lowerQuery.contains("solve") || lowerQuery.contains("solution");
    }
    
    /**
     * 创建数据分析工作流
     */
    private List<WorkflowStep> createDataAnalysisWorkflow(AgentRequest request) {
        List<WorkflowStep> steps = new ArrayList<>();
        
        // 步骤1：数据收集
        steps.add(LlmCallStep.create(
                "data-collection",
                "数据收集",
                "基于以下查询，确定需要收集什么数据：" + request.getQuery(),
                "你是一个数据分析专家，专门负责确定数据收集需求。"
        ));
        
        // 步骤2：数据处理
        steps.add(LlmCallStep.create(
                "data-processing",
                "数据处理",
                "基于收集到的数据信息 ${step.data-collection}，制定数据处理方案",
                "你是一个数据处理专家，专门负责数据清洗和预处理。"
        ));
        
        // 步骤3：数据分析
        steps.add(LlmCallStep.create(
                "data-analysis",
                "数据分析",
                "对处理后的数据进行分析：${step.data-processing}。原始查询：" + request.getQuery(),
                "你是一个数据分析专家，专门负责从数据中发现洞察和趋势。"
        ));
        
        // 步骤4：结果报告
        steps.add(LlmCallStep.create(
                "result-report",
                "结果报告",
                "基于分析结果 ${step.data-analysis}，生成完整的分析报告",
                "你是一个报告写作专家，专门负责将数据分析结果转化为易懂的报告。"
        ));
        
        return steps;
    }
    
    /**
     * 创建研究工作流
     */
    private List<WorkflowStep> createResearchWorkflow(AgentRequest request) {
        List<WorkflowStep> steps = new ArrayList<>();
        
        // 步骤1：研究计划
        steps.add(LlmCallStep.create(
                "research-planning",
                "制定研究计划",
                "为以下研究主题制定详细的研究计划：" + request.getQuery(),
                "你是一个研究专家，专门负责制定系统性的研究计划。"
        ));
        
        // 步骤2：信息搜索
        steps.add(ToolCallStep.create(
                "information-search",
                "信息搜索",
                "deep_search",
                Map.of("query", request.getQuery())
        ));
        
        // 步骤3：信息分析
        steps.add(LlmCallStep.create(
                "information-analysis",
                "信息分析",
                "分析搜索到的信息：${step.information-search}，并结合研究计划：${step.research-planning}",
                "你是一个信息分析专家，专门负责从大量信息中提取关键洞察。"
        ));
        
        // 步骤4：研究报告
        steps.add(LlmCallStep.create(
                "research-report",
                "生成研究报告",
                "基于分析结果生成完整的研究报告，回答原始问题：" + request.getQuery(),
                "你是一个专业的研究报告作者，专门负责撰写高质量的研究报告。"
        ));
        
        return steps;
    }
    
    /**
     * 创建问题解决工作流
     */
    private List<WorkflowStep> createProblemSolvingWorkflow(AgentRequest request) {
        List<WorkflowStep> steps = new ArrayList<>();
        
        // 步骤1：问题分析
        steps.add(LlmCallStep.create(
                "problem-analysis",
                "问题分析",
                "深入分析以下问题：" + request.getQuery(),
                "你是一个问题分析专家，专门负责将复杂问题分解为可管理的子问题。"
        ));
        
        // 步骤2：方案设计
        steps.add(LlmCallStep.create(
                "solution-design",
                "方案设计",
                "基于问题分析结果 ${step.problem-analysis}，设计解决方案",
                "你是一个解决方案设计专家，专门负责为复杂问题设计可行的解决方案。"
        ));
        
        // 步骤3：方案评估
        steps.add(LlmCallStep.create(
                "solution-evaluation",
                "方案评估",
                "评估设计的方案：${step.solution-design}，分析优缺点和可行性",
                "你是一个方案评估专家，专门负责分析解决方案的可行性和风险。"
        ));
        
        // 步骤4：实施建议
        steps.add(LlmCallStep.create(
                "implementation-advice",
                "实施建议",
                "基于方案评估 ${step.solution-evaluation}，提供具体的实施建议",
                "你是一个项目实施专家，专门负责将解决方案转化为可执行的实施计划。"
        ));
        
        return steps;
    }
    
    /**
     * 创建默认工作流
     */
    private List<WorkflowStep> createDefaultWorkflow(AgentRequest request) {
        List<WorkflowStep> steps = new ArrayList<>();
        
        // 默认的三步工作流：理解->分析->回答
        steps.add(LlmCallStep.create(
                "understand",
                "理解需求",
                "请仔细理解以下需求：" + request.getQuery(),
                "你是一个需求理解专家，专门负责准确理解用户需求。"
        ));
        
        steps.add(LlmCallStep.create(
                "analyze",
                "分析处理",
                "基于理解的需求 ${step.understand}，进行深入分析和处理",
                "你是一个分析专家，专门负责对问题进行深入分析。"
        ));
        
        steps.add(LlmCallStep.create(
                "respond",
                "生成回答",
                "基于分析结果 ${step.analyze}，生成完整的回答，回应原始问题：" + request.getQuery(),
                "你是一个回答生成专家，专门负责将分析结果转化为清晰的回答。"
        ));
        
        return steps;
    }
    
    /**
     * 配置工作流Agent
     */
    private void configureWorkflowAgent(WorkflowAgent agent, AgentRequest request) {
        // 设置最大步数
        agent.setMaxSteps(genieConfig.getPlannerMaxSteps());
        
        // 配置打印器
        if (agent.getContext() != null && agent.getContext().getPrinter() != null) {
            agent.setPrinter(agent.getContext().getPrinter());
        }
        
        // 配置LLM
        if (agent.getContext() != null && agent.getContext().getLlm() != null) {
            agent.setLlm(agent.getContext().getLlm());
        }
        
        // 根据请求类型决定是否启用单步模式
        if ("step".equals(request.getExecutionMode())) {
            agent.enableStepByStepMode();
        }
        
        log.info("Configured workflow agent: maxSteps={}, stepByStepMode={}", 
                agent.getMaxSteps(), agent.isStepByStepMode());
    }
    
    /**
     * 根据查询内容匹配工作流模板
     */
    private WorkflowDefinition findMatchingTemplate(String query) {
        if (!StringUtils.hasText(query)) {
            return null;
        }
        
        String lowerQuery = query.toLowerCase();
        
        // 数据分析相关
        if (lowerQuery.contains("分析") || lowerQuery.contains("数据") || 
            lowerQuery.contains("统计") || lowerQuery.contains("图表") ||
            lowerQuery.contains("analyze") || lowerQuery.contains("data")) {
            return workflowConfigLoader.getWorkflowByTemplate("data-analysis");
        }
        
        // 研究相关
        if (lowerQuery.contains("研究") || lowerQuery.contains("调研") || 
            lowerQuery.contains("搜索") || lowerQuery.contains("查找") ||
            lowerQuery.contains("research") || lowerQuery.contains("search")) {
            return workflowConfigLoader.getWorkflowByTemplate("research");
        }
        
        // 问题解决相关
        if (lowerQuery.contains("解决") || lowerQuery.contains("方案") || 
            lowerQuery.contains("如何") || lowerQuery.contains("怎么") ||
            lowerQuery.contains("solve") || lowerQuery.contains("solution")) {
            return workflowConfigLoader.getWorkflowByTemplate("problem-solving");
        }
        
        // 并行分析相关
        if (lowerQuery.contains("并行") || lowerQuery.contains("多维") || 
            lowerQuery.contains("综合") || lowerQuery.contains("parallel")) {
            return workflowConfigLoader.getWorkflowByTemplate("parallel-analysis");
        }
        
        return null;
    }
    
    /**
     * 应用变量到工作流定义
     */
    private WorkflowDefinition applyVariables(WorkflowDefinition template, AgentRequest request) {
        // 创建新的工作流定义副本
        WorkflowDefinition definition = WorkflowDefinition.builder()
                .id(template.getId() + "-" + System.currentTimeMillis())
                .name(template.getName())
                .description(template.getDescription())
                .maxSteps(template.getMaxSteps())
                .timeoutSeconds(template.getTimeoutSeconds())
                .trigger(template.getTrigger())
                .steps(template.getSteps())
                .build();
        
        // 复制并扩展变量
        Map<String, Object> variables = new HashMap<>();
        if (template.getVariables() != null) {
            variables.putAll(template.getVariables());
        }
        
        // 添加请求相关的变量
        variables.put("query", request.getQuery());
        variables.put("requestId", request.getRequestId());
        variables.put("erp", request.getErp());
        
        // 如果请求中有自定义变量，也添加进去
        if (StringUtils.hasText(request.getWorkflowVariables())) {
            try {
                JSONObject customVars = JSON.parseObject(request.getWorkflowVariables());
                for (String key : customVars.keySet()) {
                    variables.put(key, customVars.get(key));
                }
            } catch (Exception e) {
                log.warn("{} Error parsing workflow variables: {}", request.getRequestId(), e.getMessage());
            }
        }
        
        definition.setVariables(variables);
        return definition;
    }
    
    /**
     * 获取可用的工作流模板列表
     */
    public List<String> getAvailableTemplates() {
        return new ArrayList<>(workflowConfigLoader.getAvailableTemplates());
    }
    
    /**
     * 重新加载工作流配置
     */
    public void reloadWorkflowConfig() {
        workflowConfigLoader.reload();
        log.info("Workflow configuration reloaded");
    }
}
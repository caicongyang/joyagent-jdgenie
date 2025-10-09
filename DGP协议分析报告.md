# JoyAgent-JDGenie项目DGP协议实现与应用分析报告

## 1. 项目概述

JoyAgent-JDGenie是一个开源的端到端多智能体产品，其中包含了创新的**DGP（Data Governance Protocol）数据治理协议**。该协议是项目中DataAgent能力的核心组成部分，专门用于结构化表格知识的治理和智能问数。

### 1.1 DGP协议的定位
- **目标**：解决企业内部结构化表格知识的治理问题
- **范围**：涵盖数据治理、智能问数、诊断分析和工作建议
- **特点**：开箱即用，用户只需按照DGP协议治理表结构，即可直接进行问数和诊断分析

## 2. DGP协议核心理念

### 2.1 数据治理5原则
DGP协议基于**表设计、字段设计、字段值设计**的5大原则：

#### 表设计原则
- **明细表和指标表不要混合**：确保表结构的语义清晰
- **增量表和全量表不要混合**：避免数据逻辑混乱

#### 字段设计原则
- **字段避免混淆**：字段命名和含义要明确无歧义
- **时点指标和时期指标语义要说明**：明确时间维度的语义

#### 字段值设计原则
- **枚举值语义说明**：对于枚举类型字段，提供清晰的值含义说明

### 2.2 数据质量保证
DGP协议提供相关SDK确保数据的：
- **准确性（Accuracy）**
- **唯一性（Uniqueness）**
- **完整性（Completeness）**
- **一致性（Consistency）**
- **有效性（Validity）**

## 3. DGP协议技术实现架构

### 3.1 数据模型层
#### 3.1.1 核心数据表结构

```sql
-- 数据模型信息表
CREATE TABLE `chat_model_info` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code` varchar(50) NOT NULL COMMENT '模型编码',
  `type` varchar(10) NOT NULL COMMENT '模型类型TABLE,SQL',
  `name` varchar(100) DEFAULT NULL COMMENT '模型名称',
  `content` text NOT NULL COMMENT '模型内容，表或者sql',
  `use_prompt` text COMMENT '模型使用说明',
  `business_prompt` text COMMENT '模型业务限定提示词',
  `yn` tinyint(2) NOT NULL DEFAULT '1' COMMENT '是否有效'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据模型表信息';

-- 数据模型字段信息表
CREATE TABLE `chat_model_schema` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `model_code` varchar(200) NOT NULL COMMENT '模型编码',
  `column_id` varchar(1000) NOT NULL COMMENT '字段唯一ID',
  `column_name` varchar(200) NOT NULL COMMENT '字段中文名',
  `column_comment` varchar(1000) NOT NULL COMMENT '字段描述',
  `few_shot` text COMMENT '值枚举逗号分隔',
  `data_type` varchar(20) DEFAULT NULL COMMENT '字段值类型',
  `synonyms` varchar(300) DEFAULT NULL COMMENT '同义词',
  `vector_uuid` varchar(400) DEFAULT NULL COMMENT '向量库数据id',
  `default_recall` tinyint(2) NOT NULL DEFAULT '0' COMMENT '默认召回',
  `analyze_suggest` tinyint(2) NOT NULL DEFAULT '0' COMMENT '分析建议0可选，-1禁止用于分析维度，1建议',
  `yn` tinyint(2) NOT NULL DEFAULT '1' COMMENT '是否有效'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据模型表信息';
```

#### 3.1.2 Java实体类设计

```java
// 数据模型信息实体
@Data
@TableName("chat_model_info")
public class ChatModelInfo implements Serializable {
    private Long id;
    private String code;        // 模型编码
    private String type;        // 模型类型
    private String content;     // 模型内容
    private String name;        // 模型名称
    private String usePrompt;   // 使用说明
    private String businessPrompt; // 业务规则
    private Integer yn;         // 是否有效
}

// 数据模型字段信息实体
@Data
@TableName("chat_model_schema")
public class ChatModelSchema implements Serializable {
    private Long id;
    private String modelCode;      // 模型编码
    private String columnId;       // 字段唯一ID
    private String columnName;     // 字段中文名
    private String columnComment;  // 字段描述
    private String fewShot;        // 值枚举
    private String dataType;       // 字段类型
    private String synonyms;       // 同义词
    private String vectorUuid;     // 向量库ID
    private int defaultRecall;     // 默认召回
    private int analyzeSuggest;    // 分析建议
    private Integer yn;            // 是否有效
}
```

### 3.2 协议定义层

#### 3.2.1 Python协议模型
```python
# 协议请求模型定义
class TableRAGRequest(BaseModel):
    request_id: str = Field(alias="requestId", description="Request ID")
    query: str = Field(description="用户问题")
    current_date_info: str = Field(alias="currentDateInfo", description="系统当前日期")
    model_code_list: List = Field(alias="modelCodeList", description="表信息")
    schema_info: List = Field(alias="schemaInfo", description="字段信息")
    stream: bool = Field(alias="stream", default=True, description="是否流式响应")
    use_vector: Optional[bool] = Field(default=False, alias="useVector", description="使用qdrant进行向量检索")
    use_elastic: Optional[bool] = Field(default=False, alias="useElastic", description="使用es检索")
    recall_type: Optional[str] = Field(default="only_recall", alias="recallType", description="召回类型")

class NL2SQLRequest(BaseModel):
    request_id: str = Field(alias="requestId", description="Request ID")
    query: str = Field(description="用户问题")
    current_date_info: str = Field(alias="currentDateInfo", description="系统当前日期")
    table_id_list: List[str] = Field(alias="modelCodeList", description="表信息")
    column_info: List[Dict] = Field(alias="schemaInfo", description="字段信息")
    stream: bool = Field(alias="stream", default=True, description="是否流式响应")
    dialect: str = Field(alias="dbType", default="mysql", description="SQL方言类型")
```

### 3.3 数据治理实现层

#### 3.3.1 数据血缘治理
```java
// 数据血缘关系构建
public abstract class AbstractJdbcCatalog implements JdbcCatalog {
    @Override
    public List<SimpleTable> listTables(Connection connection, String schema) throws CatalogException {
        try (ResultSet rs = connection.getMetaData().getTables(null, schema, null, null)) {
            List<SimpleTable> tables = new ArrayList<>();
            while (rs.next()) {
                SimpleTable st = new SimpleTable();
                st.setTableName(rs.getString("TABLE_NAME"));
                st.setComments(rs.getString("REMARKS"));
                st.setTableType(rs.getString("TABLE_TYPE"));
                String tableSchem = rs.getString("TABLE_SCHEM");
                if (StringUtils.isBlank(tableSchem)) {
                    tableSchem = rs.getString("TABLE_CAT");
                }
                st.setTableSchema(tableSchem);
                tables.add(st);
            }
            return tables;
        } catch (SQLException e) {
            throw new CatalogException("Failed listing database in catalog %s", e);
        }
    }
}
```

#### 3.3.2 元数据管理接口
```java
public interface DataMetaProvider<T extends DataQueryRequest> {
    // 查询表信息
    List<SimpleTable> queryTables(T request, String schema) throws Exception;
    
    // 查询字段信息
    List<TableColumn> queryColumns(T request, String tableName, String schema) throws Exception;
    
    // 获取SQL字段信息
    List<TableColumn> getTableColumnsOfSql(T request) throws SQLException;
}
```

## 4. DGP协议核心功能实现

### 4.1 TableRAG智能检索

#### 4.1.1 两阶段动态选表选字段
```python
class TableRAGAgent(TableAgent):
    async def choose_schema(self, query, model_code_list=[], table_caption=""):
        # 第一阶段：字段召回
        retrieved_columns = []
        retrieved_cells = {}
        
        if self.use_vector:
            # 向量检索字段
            _retrieved_docs = await self.retrieve_schema_by_jieba(query, model_code_list=model_code_list)
            retrieved_columns.extend(_retrieved_docs.get("retrieved_docs", []))
            
            # 基于提示词的字段检索
            table_rag_prompts = get_prompt("table_rag")
            prompt = Template(table_rag_prompts["extract_column_prompt"]) \
                .render(table_caption=table_caption, query=query, keywords=keywords)
            _retrieved_docs = await self.retrieve_schema_by_prompt(prompt, model_code_list=model_code_list, query=query)
            retrieved_columns.extend(_retrieved_docs.get("retrieved_docs", []))
            
        if self.use_elastic:
            # ES检索单元格值
            _retrieved_docs = await self.retrieve_cell_by_jieba(query, model_code_list=model_code_list)
            retrieved_cells.update(_retrieved_docs.get("retrieved_docs", {}))
        
        # 第二阶段：合并和重排序
        update_schema_few_shot = self.retriever.qd_es_merge(retrieved_cells, retrieved_columns)
        model_code_schema_result = self.all_table_schema_list2model_code_schema(
            all_table_schema_list=update_schema_few_shot, 
            model_code_topk=self.model_code_topk,
            schema_topk=self.schema_topk
        )
        
        return model_code_schema_result
```

#### 4.1.2 细粒度查询拆解
```python
async def get_jieba_queries(self, query):
    column_queries = []
    if query not in self.jieba_query_map:
        # 过滤虚词，保留实词
        allowPOS = ('n', 'nr', 'ns', 'nt', 'nz', 'v', 'vn', 'a', 'an')
        column_queries = jieba.analyse.extract_tags(query, topK=20, withWeight=False, allowPOS=allowPOS)
        column_queries = list(column_queries) + [query]
        
        # 过滤数值型查询
        column_queries = self.filter_queries(column_queries)
        self.jieba_query_map[query] = copy.deepcopy(column_queries)
    else:
        column_queries = self.jieba_query_map[query]
    
    return column_queries
```

### 4.2 智能问数NL2SQL

#### 4.2.1 SQL生成流程
```yaml
# NL2SQL提示词模板
nl2sql_prompt: |-
  # 角色
  你是一个高级、精确的 SQL 查询生成器。你的任务是从「表信息以及相关字段信息」找出与用户问题最相关的表及其字段，将其转换为符合 ANSI SQL 标准的、语法正确的、高效的 SQL 查询语句。

  # 任务流程
  1.用户问题拆解
  - 将用户问题分解为独立且无歧义的子问题，每个子问题仅对应单一查询目标
  - 拆解结果用@@@分隔，例如：查询A表应届生人数@@@统计B表年龄分布
  
  2.表和字段的召回
  - 充分参考【思考伪代码】中的信息，结合【表信息以及相关字段信息】获取与用户问题最相关的数据表和字段信息
  - 禁止臆想不存在的字段
  
  3.生成SQL
  基于用户拆解的问题和召回的数据表、字段，结合【思考伪代码】的计算过程，生成最终的查询SQL

  # SQL生成规范
  1. 禁止使用JOIN的多表关联
  2. 生成的SQL禁止使用字段名称，必须使用字段ID
  3. 非统计类的查询SQL需要对查询的字段使用DISTINCT进行结果去重
  4. 聚合统计类的查询SQL需要对聚合或运算字段添加别名
  5. 当某张表完全满足用户问题时，使用该表的优先级最高
```

#### 4.2.2 自适应表类型支持
- **明细表 VS 指标表**：根据表类型自动调整查询策略
- **增量表 VS 全量表**：智能识别数据更新模式
- **时点指标 VS 时期指标**：正确处理时间维度语义

### 4.3 诊断分析引擎

#### 4.3.1 多种归因分析工具
```python
class AutoAnalysisAgent(object):
    async def analysis(self, context: AnalysisContext) -> List[InsightType]:
        # 构建分析指令
        instructions = Template(get_prompt("analysis")["analysis_auto_prompt"]).render(
            schema=context.schemas_markdown,
            business=context.businessKnowledge,
            current_date=datetime.now().strftime("%Y-%m-%d"),
            max_lenght=context.max_data_size,
        )
        
        # 创建分析智能体
        agent = create_agent(
            instructions=instructions,
            context=context,
            max_steps=self.max_steps,
            return_full_result=False,
        )
        
        # 执行分析
        result = agent.run(task=context.task, stream=self.stream)
        return result
```

#### 4.3.2 分析工具集
- **GetDataTool**：数据获取工具
- **DataTransTool**：数据转换工具
- **InsightTool**：洞察分析工具
- **SaveInsightTool**：洞察保存工具
- **FinalAnswerTool**：最终答案工具

支持的分析方法：
- **趋势分析**：时间序列趋势识别
- **周期分析**：周期性模式发现
- **异常分析**：异常值检测和分析
- **相关性分析**：变量间相关关系
- **因果分析**：因果关系推断

### 4.4 SOPPlan模式

#### 4.4.1 预定义分析流程
```python
class SopChooseRequest(BaseModel):
    request_id: str = Field(alias="requestId", description="Request ID")
    query: str = Field(description="用户问题")
    sop_list: Optional[List[Dict]] = Field(default=[], alias="sopList", description="SOP列表，包含每一个sop")
```

#### 4.4.2 Plan&Solve升级
- 基于用户预定义分析流程
- 升级传统Plan&Solve模式为SOPPlan模式
- 支持标准化分析流程定制

## 5. DGP协议的技术创新点

### 5.1 语义对齐和指标数据预编织

#### 5.1.1 语义归一化
- **分类统一**：维度含义的标准化
- **冲突解决**：多处定义冲突的自动化处理
- **语义补充**：基于业务知识的语义增强

#### 5.1.2 模型预编织
- **指标算子口径**：基于计算逻辑的约束
- **语义口径**：基于业务含义的约束
- **精准约束SQL**：在指标数据召回阶段提供精确约束

### 5.2 向量化检索与ES融合

#### 5.2.1 多模态检索
```python
# Qdrant向量检索配置
SCHEMA_COLLECTION_NAME = "genie_model_schema"

# ES列值检索配置
COLUMN_VALUE_ES_INDEX = "genie_model_column_value"
COLUMN_VALUE_ES_MAPPING = {
    "mappings": {
        "properties": {
            "modelCode": {"type": "keyword"},
            "columnId": {"type": "keyword"},
            "value": {
                "type": "text",
                "analyzer": "ik_max_word",
                "search_analyzer": "ik_max_word"
            },
            "valueId": {"type": "keyword"},
            "columnName": {"type": "keyword"},
            "columnComment": {"type": "keyword"}
        }
    }
}
```

#### 5.2.2 检索融合策略
- **向量检索**：语义相似度匹配
- **ES检索**：精确文本匹配
- **融合重排序**：多路召回结果的智能合并

### 5.3 流式处理与实时响应

#### 5.3.1 流式模式配置
```python
class StreamMode(BaseModel):
    mode: Literal["general", "token", "time"] = Field(default="general")
    token: Optional[int] = Field(default=5, ge=1)  # 每多少个token输出一次
    time: Optional[int] = Field(default=5, ge=1)   # 每多少秒输出一次
```

#### 5.3.2 异步并发处理
```python
async def retrieve_schemas_concurrent(
    self,
    columns: List[str],
    model_code_list: Optional[List[str]] = None,
    max_concurrent: int = 10,  # 控制最大并发数
    timeout: float = 30.0
) -> dict[Any]:
    # 使用信号量限制并发数
    semaphore = asyncio.Semaphore(max_concurrent)
    
    async def fetch_schema_with_limit(col: str) -> List[Any]:
        async with semaphore:
            try:
                result = await asyncio.wait_for(
                    self.retriever.retrieve_schema(query=col, model_code_list=model_code_list),
                    timeout=10.0
                )
                return result or []
            except asyncio.TimeoutError:
                logger.warning(f"Schema retrieve timeout for column='{col}'")
                return []
    
    # 创建并执行所有任务
    tasks = [fetch_schema_with_limit(col) for col in unique_columns]
    results = await asyncio.wait_for(
        asyncio.gather(*tasks, return_exceptions=False),
        timeout=timeout
    )
    
    return {"retrieved_docs": retrieved_docs}
```

## 6. DGP协议在项目中的应用

### 6.1 DataAgent控制器集成

```java
@RestController
@RequestMapping("/data")
public class DataAgentController {
    
    @Autowired
    DataAgentService dataAgentService;
    
    @Autowired
    SchemaRecallService schemaRecallService;
    
    // 向量召回接口
    @PostMapping(value = "vectorRecall")
    public List<Map<String, Object>> vectorRecall(@RequestBody ColumnVectorRecallReq req) {
        return schemaRecallService.vectorRecall(req);
    }
    
    // ES召回接口
    @PostMapping(value = "esRecall")
    public List<Map<String, Object>> esRecall(@RequestBody ColumnEsRecallReq req) throws IOException {
        return schemaRecallService.esValueRecall(req);
    }
    
    // 智能问数接口
    @PostMapping(value = "chatQuery", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatQuery(@RequestBody DataAgentChatReq req) throws Exception {
        return dataAgentService.webChatQueryData(req);
    }
    
    // 获取所有模型信息
    @GetMapping(value = "allModels")
    public Map<String, Object> allModels() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", chatModelInfoService.queryAllModelsWithSchema());
        return result;
    }
}
```

### 6.2 多智能体协作

#### 6.2.1 智能体工厂模式
```java
@Service
public class AgentHandlerFactory {
    
    public BaseAgent createAgent(String agentType, AgentContext context) {
        switch (agentType) {
            case "DATA_ANALYSIS":
                return new DataAnalysisAgent(context);
            case "TABLE_RAG":
                return new TableRAGAgent(context);
            case "NL2SQL":
                return new NL2SQLAgent(context);
            default:
                throw new IllegalArgumentException("Unknown agent type: " + agentType);
        }
    }
}
```

#### 6.2.2 智能体基类设计
```java
public abstract class BaseAgent {
    // 核心属性
    private String name;
    private String description;
    private String systemPrompt;
    public ToolCollection availableTools = new ToolCollection();
    private Memory memory = new Memory();
    protected LLM llm;
    protected AgentContext context;
    
    // 执行控制
    private AgentState state = AgentState.IDLE;
    private int maxSteps = 10;
    private int currentStep = 0;
    
    // 抽象方法
    public abstract String step();
    
    // 运行主循环
    public String run(String query) {
        setState(AgentState.IDLE);
        if (!query.isEmpty()) {
            updateMemory(RoleType.USER, query, null);
        }
        
        List<String> results = new ArrayList<>();
        try {
            while (currentStep < maxSteps && state != AgentState.FINISHED) {
                currentStep++;
                String stepResult = step();
                results.add(stepResult);
            }
        } catch (Exception e) {
            state = AgentState.ERROR;
            throw e;
        }
        
        return results.isEmpty() ? "No steps executed" : results.get(results.size() - 1);
    }
}
```

### 6.3 前端集成展示

#### 6.3.1 React组件集成
```typescript
// ChatView组件中的DGP协议调用
const handleDataQuery = async (query: string) => {
  const response = await fetch('/data/chatQuery', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      content: query,
      modelCodeList: selectedModels,
      useVector: true,
      useElastic: true
    })
  });
  
  // 处理流式响应
  const reader = response.body?.getReader();
  while (true) {
    const { done, value } = await reader?.read() || {};
    if (done) break;
    
    const chunk = new TextDecoder().decode(value);
    // 处理DGP协议返回的数据
    handleDGPResponse(chunk);
  }
};
```

## 7. DGP协议的优势与效果

### 7.1 性能表现

#### 7.1.1 BirdSQL榜单成绩
- **Test集准确率**：75.74%，排名第4（共84支队伍）
- **Dev集准确率**：74.25%
- **领先竞争对手**：超越字节跳动DataAgent、IBM等知名产品

#### 7.1.2 GAIA榜单成绩
- **Validation集准确率**：75.15%
- **Test集准确率**：65.12%
- **超越产品**：OWL（CAMEL）、Smolagent（Huggingface）、LRC-Huawei等

### 7.2 技术优势

#### 7.2.1 完整性
- **端到端解决方案**：从数据治理到智能分析的完整链路
- **开箱即用**：无需复杂配置，按协议治理即可使用
- **多模态支持**：结构化和非结构化数据的统一处理

#### 7.2.2 先进性
- **语义理解**：基于大模型的深度语义理解
- **多路召回**：向量检索+ES检索的融合策略
- **实时流式**：支持实时流式响应和处理

#### 7.2.3 扩展性
- **插件化架构**：支持自定义智能体和工具
- **协议标准化**：基于标准化协议的可扩展设计
- **多数据源支持**：支持多种数据库和数据源

### 7.3 业务价值

#### 7.3.1 降本增效
- **自动化治理**：减少人工数据治理成本
- **智能问数**：提升数据查询效率
- **诊断分析**：自动化业务洞察发现

#### 7.3.2 决策支持
- **多角度分析**：提供"新角度"与aha moment
- **实时响应**：支持实时业务决策
- **标准化流程**：基于SOP的标准化分析流程

## 8. DGP协议的未来发展

### 8.1 正在进行的工作

#### 8.1.1 数据血缘治理
- **SQL AST解析**：采集数仓脚本进行语法树解析
- **血缘关系构建**：识别字段、表、加工算子的血缘关系
- **知识图谱构建**：结合语义补充构成丰富的知识图谱

#### 8.1.2 语义对齐和指标数据预编织
- **语义归一化**：解决多处定义冲突问题
- **维度统一**：实现维度含义的标准化
- **模型预编织**：基于高质量语义与图谱知识的结合

### 8.2 技术演进方向

#### 8.2.1 智能化程度提升
- **自动化程度**：进一步提升数据治理的自动化水平
- **智能推荐**：基于使用模式的智能字段和表推荐
- **自适应优化**：基于反馈的查询优化和性能提升

#### 8.2.2 生态系统扩展
- **多模态RAG**：扩展到非结构化知识处理
- **行业适配**：针对不同行业的专业化适配
- **标准化推广**：推动DGP协议成为行业标准

## 9. 总结

DGP（Data Governance Protocol）协议是JoyAgent-JDGenie项目的核心创新，它通过标准化的数据治理方法、智能化的检索技术和多智能体协作机制，实现了从数据治理到智能分析的端到端解决方案。

### 9.1 核心价值
1. **标准化治理**：提供了一套完整的数据治理标准和实施方案
2. **智能化分析**：基于大模型的智能问数和诊断分析能力
3. **开箱即用**：用户只需按协议治理数据，即可获得完整的智能分析能力
4. **性能卓越**：在多个公开榜单上取得领先成绩

### 9.2 技术特色
1. **多路召回融合**：向量检索与ES检索的有机结合
2. **流式实时处理**：支持大规模并发和实时响应
3. **语义理解增强**：基于业务知识的深度语义理解
4. **可扩展架构**：支持自定义智能体和工具的插件化架构

### 9.3 应用前景
DGP协议不仅解决了当前企业数据治理的痛点问题，更为未来的智能化数据分析奠定了坚实基础。随着技术的不断演进和生态的持续完善，DGP协议有望成为数据治理和智能分析领域的重要标准。

---

*本报告基于JoyAgent-JDGenie项目源码分析，详细阐述了DGP协议的设计理念、技术实现和应用价值。项目地址：https://github.com/jd-opensource/joyagent-jdgenie*

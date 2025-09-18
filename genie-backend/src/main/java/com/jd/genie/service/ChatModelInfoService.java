package com.jd.genie.service;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jd.genie.config.data.DataAgentConfig;
import com.jd.genie.config.data.DataAgentConstants;
import com.jd.genie.config.data.DataAgentModelConfig;
import com.jd.genie.config.data.DbConfig;
import com.jd.genie.data.QueryResult;
import com.jd.genie.data.TableColumn;
import com.jd.genie.data.dto.ChatModelInfoDto;
import com.jd.genie.data.dto.ChatSchemaDto;
import com.jd.genie.data.dto.VectorModelSchema;
import com.jd.genie.data.dto.VectorSaveReq;
import com.jd.genie.data.exception.JdbcBizException;
import com.jd.genie.data.model.StandardColumnType;
import com.jd.genie.data.provider.jdbc.JdbcDataMetaProvider;
import com.jd.genie.data.provider.jdbc.JdbcDataProvider;
import com.jd.genie.data.provider.jdbc.JdbcQueryRequest;
import com.jd.genie.entity.ChatModelInfo;
import com.jd.genie.entity.ChatModelSchema;
import com.jd.genie.mapper.ChatModelInfoMapper;
import com.jd.genie.util.JdbcUtils;
import io.qdrant.client.grpc.Points;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static io.qdrant.client.ConditionFactory.matchKeyword;
import static io.qdrant.client.ConditionFactory.matchKeywords;

@Slf4j
@Service
public class ChatModelInfoService extends ServiceImpl<ChatModelInfoMapper, ChatModelInfo> {


    @Autowired
    DataAgentConfig dataAgentConfig;
    @Autowired
    JdbcDataMetaProvider jdbcDataMetaProvider;
    @Autowired
    JdbcDataProvider jdbcDataProvider;
    @Autowired
    ChatModelSchemaService chatModelSchemaService;
    @Autowired
    VectorService vectorService;
    @Autowired
    ColumnValueSyncService columnValueSyncService;
    @Autowired
    QdrantService qdrantService;


    public void initModelInfo(DataAgentConfig dataAgentConfig) throws Exception {
        List<DataAgentModelConfig> tableList = dataAgentConfig.getModelList();
        if (CollectionUtils.isEmpty(tableList)) {
            log.warn("dataAgent.tableList is empty");
        }
        for (DataAgentModelConfig modelConfig : tableList) {
            List<TableColumn> tableSchema = getModelSchema(modelConfig);
            Map<String, Set<String>> fewShotMap = queryModelFewShot(modelConfig, tableSchema);
            saveModelInfo(modelConfig, tableSchema, fewShotMap);
        }
    }


    private Map<String, Set<String>> queryModelFewShot(DataAgentModelConfig modelConfig, List<TableColumn> tableSchema) throws SQLException {
        String fewShotSql = getFewShotSql(modelConfig, tableSchema);
        JdbcQueryRequest jdbcQueryRequest = new JdbcQueryRequest();
        DbConfig dbConfig = dataAgentConfig.getDbConfig();
        jdbcQueryRequest.setJdbcConnectionConfig(JdbcUtils.parseJdbcConnectionConfig(dbConfig));
        jdbcQueryRequest.setSql(fewShotSql);
        QueryResult queryResult = jdbcDataProvider.queryData(jdbcQueryRequest);
        List<Map<String, Object>> dataList = queryResult.getDataList();
        if (CollectionUtils.isEmpty(dataList)) {
            return new HashMap<>();
        }
        Map<String, Set<String>> columnValueMap = new HashMap<>();
        for (Map<String, Object> data : dataList) {
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value == null || StringUtils.isBlank(value.toString())) {
                    continue;
                }
                // 将值转换为字符串
                String stringValue = value.toString();
                Set<String> values = columnValueMap.computeIfAbsent(key, k -> new HashSet<>());
                if (values.size() > 11) {
                    continue;
                }
                //截取枚举最大300字符
                stringValue = stringValue.substring(0, Math.min(300, stringValue.length()));
                values.add(stringValue);
            }
        }
        return columnValueMap;
    }

    private String getFewShotSql(DataAgentModelConfig modelConfig, List<TableColumn> tableSchema) {
        if ("table".equalsIgnoreCase(modelConfig.getType())) {
            return "SELECT * FROM " + modelConfig.getContent() + " LIMIT 10000";
        } else if ("sql".equalsIgnoreCase(modelConfig.getType())) {
            return modelConfig.getContent() + " LIMIT 10000";
        } else {
            throw new JdbcBizException("不支持的模型类型：" + modelConfig.getType());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public ChatModelInfo saveModelInfo(DataAgentModelConfig modelConfig, List<TableColumn> tableSchema, Map<String, Set<String>> fewShotMap) throws SQLException, ExecutionException, InterruptedException {
        ChatModelInfo modelInfo = new ChatModelInfo();
        String modelCode = modelConfig.getId();
        modelInfo.setCode(modelCode);
        modelInfo.setName(modelConfig.getName());
        modelInfo.setContent(modelConfig.getContent());
        modelInfo.setType(modelConfig.getType());
        modelInfo.setUsePrompt(modelConfig.getRemark());
        modelInfo.setBusinessPrompt(modelConfig.getBusinessPrompt());
        save(modelInfo);
        log.info("model info save success:{}", modelCode);
        List<ChatModelSchema> chatModelSchemas = chatModelSchemaService.saveModelSchema(modelCode, modelConfig, tableSchema, fewShotMap);
        log.info("model schema save success {},size:{}", modelCode, chatModelSchemas.size());
        if (dataAgentConfig.getQdrantConfig().getEnable()) {
            Points.Filter filter = Points.Filter.newBuilder().addMust(matchKeyword("modelCode", modelCode)).build();
            qdrantService.deleteByFilterSync(DataAgentConstants.SCHEMA_COLLECTION_NAME, filter);
            log.info("model schema clean success:{}", modelCode);
            int vectorSize = syncVectorInfo(chatModelSchemas);
            log.info("model schema vector sync success {},vector size:{}", modelCode, vectorSize);
        }
        if (dataAgentConfig.getEsConfig().getEnable() && StringUtils.isNotBlank(modelConfig.getSyncValueFields())) {
            String[] split = modelConfig.getSyncValueFields().toUpperCase().split(",");
            List<String> syncColumn = Arrays.asList(split);
            List<ChatModelSchema> syncValueList = chatModelSchemas.stream().filter(f -> syncColumn.contains(f.getColumnId().toUpperCase())).collect(Collectors.toList());
            columnValueSyncService.syncColumnValueBatch(modelInfo, syncValueList);
        }
        return modelInfo;
    }

    private int syncVectorInfo(List<ChatModelSchema> chatModelSchemas) {
        List<VectorSaveReq.VectorData> vectorDataList = convertToVectorData(chatModelSchemas);
        // 分批处理
        int batchSize = 20;
        int total = 0;
        for (int i = 0; i < vectorDataList.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, vectorDataList.size());
            List<VectorSaveReq.VectorData> batch = vectorDataList.subList(i, endIndex);
            try {
                VectorSaveReq req = new VectorSaveReq();
                req.setCollectionName(DataAgentConstants.SCHEMA_COLLECTION_NAME);
                req.setDataList(batch);
                vectorService.saveVector(req);
                total += endIndex - i;
            } catch (Exception e) {
                log.error("批量保存向量数据失败{}", e.getMessage(), e);
                throw new RuntimeException("批量保存向量数据失败");
            }
        }
        return total;
    }

    private List<VectorSaveReq.VectorData> convertToVectorData(List<ChatModelSchema> schemaList) {
        List<VectorSaveReq.VectorData> allVectors = new ArrayList<>();
        for (ChatModelSchema schema : schemaList) {
            String[] uuids = schema.getVectorUuid().split(",");
            addVectorSaveData(allVectors, schema, schema.getColumnName(), uuids[0]);
            addVectorSaveData(allVectors, schema, schema.getSynonyms(), uuids[1]);
            addVectorSaveData(allVectors, schema, schema.getColumnComment(), uuids[2]);
            if (!StandardColumnType.DECIMAL.name().equalsIgnoreCase(schema.getDataType())) {
                //数值类型fewShot不参与向量化
                addVectorSaveData(allVectors, schema, schema.getFewShot(), uuids[3]);
            }
        }
        return allVectors;
    }


    private void addVectorSaveData(List<VectorSaveReq.VectorData> allVectors, ChatModelSchema schema, String vectorText, String uuid) {
        if (StringUtils.isBlank(vectorText)) {
            return;
        }
        VectorModelSchema newSchema = new VectorModelSchema();
        BeanUtils.copyProperties(schema, newSchema);
        VectorSaveReq.VectorData vectorData = new VectorSaveReq.VectorData();
        vectorData.setEmbeddingText(vectorText);
        String json = JSONObject.toJSONString(newSchema);
        vectorData.setPayloads(JSONObject.parseObject(json, new TypeReference<>() {
        }));
        vectorData.setUuid(uuid);
        allVectors.add(vectorData);
    }


    public List<TableColumn> getModelSchema(DataAgentModelConfig modelConfig) throws SQLException {
        if ("table".equalsIgnoreCase(modelConfig.getType())) {
            return getTableSchema(modelConfig);
        } else if ("sql".equalsIgnoreCase(modelConfig.getType())) {
            return getSqlSchema(modelConfig);
        } else {
            throw new JdbcBizException("不支持的模型类型：" + modelConfig.getType());
        }
    }

    public List<TableColumn> getSqlSchema(DataAgentModelConfig modelConfig) throws SQLException {
        JdbcQueryRequest jdbcQueryRequest = new JdbcQueryRequest();
        DbConfig dbConfig = dataAgentConfig.getDbConfig();
        jdbcQueryRequest.setJdbcConnectionConfig(JdbcUtils.parseJdbcConnectionConfig(dbConfig));
        jdbcQueryRequest.setSql(modelConfig.getContent());
        jdbcQueryRequest.setLimit(1);
        return jdbcDataMetaProvider.getTableColumnsOfSql(jdbcQueryRequest);
    }

    public List<TableColumn> getTableSchema(DataAgentModelConfig modelConfig) throws SQLException {
        JdbcQueryRequest jdbcQueryRequest = new JdbcQueryRequest();
        DbConfig dbConfig = dataAgentConfig.getDbConfig();
        jdbcQueryRequest.setJdbcConnectionConfig(JdbcUtils.parseJdbcConnectionConfig(dbConfig));
        return jdbcDataMetaProvider.queryColumns(jdbcQueryRequest, modelConfig.getContent(), dbConfig.getSchema());
    }

    public List<ChatModelInfoDto> queryAllModelsWithSchema() {
        List<ChatModelInfo> modelList = list();
        List<ChatModelSchema> schemaList = chatModelSchemaService.list();
        List<ChatSchemaDto> schemaDtoList = new ArrayList<>();
        for (ChatModelSchema schema : schemaList) {
            ChatSchemaDto dto = new ChatSchemaDto();
            BeanUtils.copyProperties(schema, dto);
            schemaDtoList.add(dto);
        }
        Map<String, List<ChatSchemaDto>> schemaMap = schemaDtoList.stream().collect(Collectors.groupingBy(ChatSchemaDto::getModelCode));
        List<ChatModelInfoDto> dtoList = new ArrayList<>();
        for (ChatModelInfo modelInfo : modelList) {
            ChatModelInfoDto dto = new ChatModelInfoDto();
            dto.setModelCode(modelInfo.getCode());
            dto.setModelName(modelInfo.getName());
            dto.setBusinessPrompt(modelInfo.getBusinessPrompt());
            dto.setUsePrompt(modelInfo.getUsePrompt());
            dto.setType(modelInfo.getType());
            dto.setContent(modelInfo.getContent());
            dto.setSchemaList(schemaMap.get(modelInfo.getCode()));
            dtoList.add(dto);
        }
        return dtoList;
    }

    public QueryResult previewData(String modelCode) throws SQLException {
        LambdaQueryWrapper<ChatModelInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ChatModelInfo::getCode, modelCode);
        ChatModelInfo modelInfo = getOne(queryWrapper);
        String sql = "";
        if ("table".equalsIgnoreCase(modelInfo.getType())) {
            sql += "SELECT * FROM " + modelInfo.getContent();
        } else if ("sql".equalsIgnoreCase(modelInfo.getType())) {
            sql += modelInfo.getContent();
        }
        sql += " LIMIT 100";
        JdbcQueryRequest jdbcQueryRequest = new JdbcQueryRequest();
        DbConfig dbConfig = dataAgentConfig.getDbConfig();
        jdbcQueryRequest.setJdbcConnectionConfig(JdbcUtils.parseJdbcConnectionConfig(dbConfig));
        jdbcQueryRequest.setSql(sql);
        return jdbcDataProvider.queryData(jdbcQueryRequest);
    }

}

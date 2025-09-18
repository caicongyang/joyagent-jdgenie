package com.jd.genie.service;

import com.alibaba.fastjson.JSONObject;
import com.jd.genie.agent.util.OkHttpUtil;
import com.jd.genie.config.data.DataAgentConfig;
import com.jd.genie.data.dto.ChatSchemaDto;
import com.jd.genie.data.dto.NL2SQLReq;
import com.jd.genie.data.dto.TableRagResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TableRagService {

    public static final String TABLE_RAG_URL = "/v1/tool/table_rag";

    @Autowired
    DataAgentConfig dataAgentConfig;

    public List<ChatSchemaDto> tableRag(NL2SQLReq req) throws IOException {
        if (!dataAgentConfig.getEsConfig().getEnable() && !dataAgentConfig.getQdrantConfig().getEnable()) {
            log.info("{},{} 未开启向量和es，不进行tableRag",req.getTraceId(),req.getRequestId());
            return new ArrayList<>();
        }
        String res;
        try {
            res = OkHttpUtil.postJsonBody(dataAgentConfig.getAgentUrl() + TABLE_RAG_URL, null, JSONObject.toJSONString(req));
        } catch (Exception e) {
            log.warn("{},{} tableRag server error,retry:{}",req.getTraceId(),req.getRequestId(), e.getMessage());
            res = OkHttpUtil.postJsonBody(dataAgentConfig.getAgentUrl() + TABLE_RAG_URL, null, JSONObject.toJSONString(req));
        }
        log.info("{},{} tableRag result:{}", req.getTraceId(),req.getRequestId(),res);
        TableRagResult tableRagResult = JSONObject.parseObject(res, TableRagResult.class);
        if (tableRagResult == null || tableRagResult.getCode() == null) {
            throw new RuntimeException("tableRag result is null");
        }
        if (tableRagResult.getCode() != 200) {
            throw new RuntimeException("tableRag server return error");
        }
        List<TableRagResult.TableRagData> data = tableRagResult.getData();
        if (CollectionUtils.isEmpty(data)) {
            throw new RuntimeException("tableRag result data is empty");
        }
        return data.stream()
                .filter(Objects::nonNull)
                .map(TableRagResult.TableRagData::getSchemaList)
                .filter(CollectionUtils::isNotEmpty)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }
}

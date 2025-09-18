package com.jd.genie.service;

import com.jd.genie.config.data.DataAgentConstants;
import com.jd.genie.data.dto.ColumnEsRecallReq;
import com.jd.genie.data.dto.ColumnVectorRecallReq;
import com.jd.genie.data.dto.VectorRecallReq;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SchemaRecallService {

    @Autowired
    RestHighLevelClient dataAgentEsClient;
    @Autowired
    VectorService vectorService;


    public List<Map<String, Object>> vectorRecall(ColumnVectorRecallReq recallReq) {
        VectorRecallReq req = new VectorRecallReq();
        req.setQuery(recallReq.getQuery());
        req.setCollectionName(DataAgentConstants.SCHEMA_COLLECTION_NAME);
        Map<String, Object> filterMap = new HashMap<>();
        filterMap.put("modelCode", recallReq.getModelCodeList());
        req.setKeywordFilterMap(filterMap);
        req.setScoreThreshold(recallReq.getScoreThreshold());
        req.setTimeout(recallReq.getTimeout());
        req.setLimit(recallReq.getLimit());
        return vectorService.vectorRecall(req);
    }

    public List<Map<String, Object>> esValueRecall(ColumnEsRecallReq req) throws IOException {
        SearchRequest searchRequest = new SearchRequest(DataAgentConstants.COLUMN_VALUE_ES_INDEX);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.filter(QueryBuilders.termsQuery("model_code", req.getModelCodeList()));
        boolQueryBuilder.must(QueryBuilders.matchQuery("value", req.getQuery()));
        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.sort(SortBuilders.scoreSort().order(SortOrder.DESC));
        sourceBuilder.size(req.getLimit());
        log.info("esValueRecall query params:{}", sourceBuilder);

        searchRequest.source(sourceBuilder);
        SearchResponse search = dataAgentEsClient.search(searchRequest, RequestOptions.DEFAULT);
        SearchHit[] hits = search.getHits().getHits();
        List<Map<String, Object>> dataList = new ArrayList<>();
        for (SearchHit hit : hits) {
            Map<String, Object> row = hit.getSourceAsMap();
            row.put("_score", hit.getScore());
            dataList.add(row);
        }
        return dataList;
    }
}

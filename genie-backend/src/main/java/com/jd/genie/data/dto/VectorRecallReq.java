package com.jd.genie.data.dto;


import lombok.Data;

import java.util.List;
import java.util.Map;


@Data
public class VectorRecallReq {
    private String query;
    private String collectionName;
    private Integer limit = 100;
    private Float scoreThreshold = 0.5f;
    private Long timeout = 5000L;
    private Map<String, Object> keywordFilterMap;
    private List<String> payloads;
    private List<String> vectorIdList;
    private String requestId;
    private String vectorType;
}

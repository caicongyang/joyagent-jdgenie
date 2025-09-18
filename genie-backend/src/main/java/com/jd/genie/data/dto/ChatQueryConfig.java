package com.jd.genie.data.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ChatQueryConfig {
    private String projectCode;
    private String modelCode;
    private List<ChatQueryColumn> cols;
    private List<ChatQueryFilter> filters;
    private Map<String, Object> variableMap;
    private int limit;
    private boolean groupWithoutAgg = true;
    private String question;
}

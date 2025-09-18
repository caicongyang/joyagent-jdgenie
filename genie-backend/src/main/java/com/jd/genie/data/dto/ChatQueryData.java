package com.jd.genie.data.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ChatQueryData {

    private String question;
    private List<Map<String, Object>> dataList;
    private List<ChatQueryColumn> columnList;
    private List<ChatQueryFilter> filters;

    private String modelCode;
    private String modelName;
    private Boolean loadSucceed;
    private String errorMessage;
    private List<String> querySqlList;
    private int limit;
    private List<String> dimCols;
    private List<String> measureCols;

    private String nl2sqlResult;
    private String id;
}

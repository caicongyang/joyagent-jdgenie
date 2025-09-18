package com.jd.genie.data.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChatQueryFilter {
    private String col;
    private String opt;
    private String val;
    private String optName;
    private String name;
    private String dataType;
    //and or
    private String operator;
    private List<ChatQueryFilter> subFilters;
}

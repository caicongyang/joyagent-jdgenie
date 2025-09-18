package com.jd.genie.data.dto;

import lombok.Data;

@Data
public class ChatQueryColumn {
    private String col;
    private String agg;
    private String order;

    private String guid;
    private String name;
    private String dataType;
    private String colType;
}

package com.jd.genie.data;

import lombok.Data;

@Data
public class SimpleTable {
    private String tableSchema;
    private String tableName;
    private String tableType;
    private String comments;
    private Long datasourceId;
}

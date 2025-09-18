package com.jd.genie.data.model;

import lombok.Data;

@Data
public class DataOrderBy {
    private String tableAlias;
    private String columnName;
    private String columnKind;
    private String columnAlias;
    private String columnKey;
    private OrderByType orderType;
}

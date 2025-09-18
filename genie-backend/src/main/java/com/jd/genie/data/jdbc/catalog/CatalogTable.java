package com.jd.genie.data.jdbc.catalog;

import com.jd.genie.data.TableColumn;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Properties;

/**
 * @author: jinglingtuan
 * @date: 2023/4/25 21:57
 * @version: 1.0
 */
@Builder
@Data
public class CatalogTable {

    private List<TableColumn> columnList;
    private String schema;
    private String name;
    private String comment;
    private Properties config;

}

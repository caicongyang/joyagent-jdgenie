package com.jd.genie.data.jdbc.dialect.mysql;



import com.jd.genie.data.jdbc.dialect.DialectEnum;
import com.jd.genie.data.jdbc.dialect.JdbcDialect;

import java.util.Properties;

public class MysqlDialect implements JdbcDialect {
    @Override
    public DialectEnum dialectName() {
        return DialectEnum.MYSQL;
    }

    @Override
    public String driverName() {
        return "com.mysql.jdbc.Driver";
    }

    @Override
    public Properties defaultProperties() {
        Properties properties = new Properties();
        properties.setProperty("remarks", "true");
        properties.setProperty("useInformationSchema", "true");
        return properties;
    }
}

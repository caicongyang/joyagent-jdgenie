package com.jd.genie.config.data;

import lombok.Data;

@Data
public class EsConfig {
    private Boolean enable;
    private String host;
    private String user;
    private String password;
}

package com.jd.genie.model.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GptQueryReq {
    /**
     * Set user
     */
    public void setUser(String user) {
        this.user = user;
    }

    /**
     * Set deep think
     */
    public void setDeepThink(Integer deepThink) {
        this.deepThink = deepThink;
    }

    /**
     * Get deep think
     */
    public Integer getDeepThink() {
        return deepThink;
    }

    /**
     * Set trace ID
     */
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    /**
     * Get trace ID
     */
    public String getTraceId() {
        return traceId;
    }
    private String query;
    private String sessionId;
    private String requestId;
    private Integer deepThink;
    /**
     * 前端传入交付物格式：html(网页模式）,docs(文档模式）， table(表格模式）
     */
    private String outputStyle;
    private String traceId;
    private String user;
}

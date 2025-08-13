package com.jd.genie.agent.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 步骤执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepResult {
    /**
     * Check if result is successful
     */
    public boolean isSuccess() {
        return success;
    }
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 执行结果数据
     */
    private Object data;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 异常对象
     */
    private Throwable exception;
    
    /**
     * 执行开始时间
     */
    private Date startTime;
    
    /**
     * 执行结束时间
     */
    private Date endTime;
    
    /**
     * 执行耗时（毫秒）
     */
    private long executionTimeMs;
    
    /**
     * 附加属性
     */
    @Builder.Default
    private Map<String, Object> properties = new ConcurrentHashMap<>();
    
    /**
     * 创建成功结果
     */
    public static StepResult success() {
        return success(null);
    }
    
    /**
     * 创建成功结果
     */
    public static StepResult success(Object data) {
        return builder()
                .success(true)
                .data(data)
                .endTime(new Date())
                .build();
    }
    
    /**
     * 创建失败结果
     */
    public static StepResult failure(String errorMessage) {
        return failure(errorMessage, null);
    }
    
    /**
     * 创建失败结果
     */
    public static StepResult failure(String errorMessage, Throwable exception) {
        return builder()
                .success(false)
                .errorMessage(errorMessage)
                .exception(exception)
                .endTime(new Date())
                .build();
    }
    
    /**
     * 设置执行时间
     */
    public StepResult withExecutionTime(Date startTime, Date endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
        if (startTime != null && endTime != null) {
            this.executionTimeMs = endTime.getTime() - startTime.getTime();
        }
        return this;
    }
    
    /**
     * 添加属性
     */
    public StepResult withProperty(String key, Object value) {
        properties.put(key, value);
        return this;
    }
    
    /**
     * 获取属性
     */
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key, Class<T> type) {
        Object value = properties.get(key);
        return value != null ? type.cast(value) : null;
    }
    
    /**
     * 获取字符串类型的数据
     */
    public String getDataAsString() {
        return data != null ? data.toString() : null;
    }
    
    /**
     * 获取指定类型的数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getDataAs(Class<T> type) {
        return data != null ? type.cast(data) : null;
    }
    
    /**
     * 是否可以重试
     */
    public boolean isRetryable() {
        // 如果有异常且不是中断异常，则可以重试
        if (exception != null) {
            return !(exception instanceof InterruptedException);
        }
        // 如果只是简单的失败，默认可以重试
        return !success;
    }
    
    /**
     * 获取详细的错误信息
     */
    public String getDetailedErrorMessage() {
        StringBuilder sb = new StringBuilder();
        if (errorMessage != null) {
            sb.append(errorMessage);
        }
        if (exception != null) {
            if (sb.length() > 0) {
                sb.append(": ");
            }
            sb.append(exception.getMessage());
        }
        return sb.toString();
    }
}
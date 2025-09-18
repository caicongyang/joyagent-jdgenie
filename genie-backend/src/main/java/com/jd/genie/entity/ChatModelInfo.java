package com.jd.genie.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("chat_model_info")
public class ChatModelInfo implements Serializable {
    private static final long serialVersionUID = 8763697882256572393L;
    private Long id;
    private String code;
    private String type;
    private String content;
    private String name;
    private String usePrompt;
    private String businessPrompt;
    @TableLogic
    private Integer yn;
}

package com.example.teamflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ai_record")
public class AiRecord {
    public static final String TYPE_DOC_CHECK = "DOC_CHECK";
    public static final String SOURCE_ZHIPU_GLM = "ZHIPU_GLM";
    public static final String SOURCE_LOCAL_FALLBACK = "LOCAL_FALLBACK";
    public static final String DEFAULT_MODEL_NAME = "glm-4";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long userId;
    private String type;
    private String prompt;
    private String result;
    private String status;
    private Integer confirmedFlag;
    private String source;
    private String modelName;
    private String riskLevel;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

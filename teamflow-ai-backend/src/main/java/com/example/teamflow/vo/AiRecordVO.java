package com.example.teamflow.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AiRecordVO {
    private Long id;
    private Long projectId;
    private String projectName;
    private Long userId;
    private String username;
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

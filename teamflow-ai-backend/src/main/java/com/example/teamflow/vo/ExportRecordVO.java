package com.example.teamflow.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ExportRecordVO {
    private Long id;
    private Long projectId;
    private String projectName;
    private Long creatorId;
    private String creatorName;
    private String exportScope;
    private String fileName;
    private String fileUrl;
    private Long fileSize;
    private String status;
    private String failureReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

package com.example.teamflow.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NotificationVO {
    private Long id;
    private Long userId;
    private Long projectId;
    private String projectName;
    private String type;
    private String title;
    private String content;
    private Integer readFlag;
    private LocalDateTime readTime;
    private LocalDateTime createTime;
}

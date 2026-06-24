package com.example.teamflow.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TaskLogVO {
    private Long id;
    private Long taskId;
    private Long userId;
    private String username;
    private String actionType;
    private String oldStatus;
    private String newStatus;
    private String oldValue;
    private String newValue;
    private String content;
    private LocalDateTime createTime;
}

package com.example.teamflow.vo;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class TaskVO {
    private Long id;
    private Long projectId;
    private String projectName;
    private Long sectionId;
    private String sectionName;
    private String title;
    private String description;
    private Long assigneeId;
    private String assigneeName;
    private Long creatorId;
    private String creatorName;
    private String status;
    private String priority;
    private LocalDate startDate;
    private LocalDate dueDate;
    private LocalDateTime finishTime;
    private String blockReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

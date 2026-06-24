package com.example.teamflow.vo;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ProgressRecordVO {
    private Long id;
    private Long projectId;
    private Long userId;
    private String realName;
    private String roleName;
    private Long relatedTaskId;
    private String relatedTaskTitle;
    private Long relatedSectionId;
    private String reportPeriod;
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private String completedWork;
    private String problems;
    private String helpNeeded;
    private String nextPlan;
    private String submitStatus;
    private LocalDateTime submitTime;
}

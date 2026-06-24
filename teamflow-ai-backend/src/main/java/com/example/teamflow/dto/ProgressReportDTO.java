package com.example.teamflow.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class ProgressReportDTO {
    @NotNull(message = "项目不能为空")
    private Long projectId;
    private Long relatedTaskId;
    private Long relatedSectionId;
    @NotBlank(message = "已完成工作不能为空")
    private String completedWork;
    private String problems;
    private String helpNeeded;
    @NotBlank(message = "下一步计划不能为空")
    private String nextPlan;
    private String reportPeriod;
}

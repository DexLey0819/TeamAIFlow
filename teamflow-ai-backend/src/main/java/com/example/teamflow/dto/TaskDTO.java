package com.example.teamflow.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
public class TaskDTO {
    @NotNull(message = "项目不能为空")
    private Long projectId;
    private Long sectionId;
    @NotBlank(message = "任务标题不能为空")
    private String title;
    private String description;
    private Long assigneeId;
    private String status;
    private String priority;
    private LocalDate startDate;
    private LocalDate dueDate;
    private String blockReason;
}

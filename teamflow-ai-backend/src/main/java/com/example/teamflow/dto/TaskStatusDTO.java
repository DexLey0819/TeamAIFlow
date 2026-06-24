package com.example.teamflow.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class TaskStatusDTO {
    @NotBlank(message = "任务状态不能为空")
    private String status;
    private String content;
    private String blockReason;
}

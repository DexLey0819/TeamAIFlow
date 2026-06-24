package com.example.teamflow.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class SectionReviewDTO {
    @NotBlank(message = "审核结果不能为空")
    private String reviewResult;
    private String reviewComment;
}

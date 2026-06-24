package com.example.teamflow.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class SectionContentDTO {
    @NotBlank(message = "标题不能为空")
    private String title;
    private String body;
}

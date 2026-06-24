package com.example.teamflow.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class SectionCommentDTO {
    @NotNull(message = "内容不能为空")
    private Long contentId;
    @NotBlank(message = "评论内容不能为空")
    private String commentText;
}

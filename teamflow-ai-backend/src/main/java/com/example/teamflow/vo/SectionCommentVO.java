package com.example.teamflow.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SectionCommentVO {
    private Long id;
    private Long sectionId;
    private Long contentId;
    private Long userId;
    private String username;
    private String realName;
    private String commentText;
    private Integer resolvedFlag;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

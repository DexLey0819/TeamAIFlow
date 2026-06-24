package com.example.teamflow.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SectionReviewVO {
    private Long id;
    private Long sectionId;
    private Long contentId;
    private Long reviewerId;
    private String reviewerName;
    private String reviewResult;
    private String reviewComment;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

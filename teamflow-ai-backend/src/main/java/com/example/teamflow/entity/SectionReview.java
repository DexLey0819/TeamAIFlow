package com.example.teamflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("section_review")
public class SectionReview {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sectionId;
    private Long contentId;
    private Long reviewerId;
    private String reviewResult;
    private String reviewComment;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

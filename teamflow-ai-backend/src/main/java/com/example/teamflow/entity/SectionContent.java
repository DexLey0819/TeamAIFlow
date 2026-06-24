package com.example.teamflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("section_content")
public class SectionContent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sectionId;
    private Long projectId;
    private Integer versionNo;
    private String title;
    private String body;
    private Long editorId;
    private String submitStatus;
    private LocalDateTime submitTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

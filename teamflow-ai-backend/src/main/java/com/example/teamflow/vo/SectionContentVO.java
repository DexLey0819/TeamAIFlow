package com.example.teamflow.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SectionContentVO {
    private Long id;
    private Long sectionId;
    private Long projectId;
    private Integer versionNo;
    private String title;
    private String body;
    private Long editorId;
    private String editorName;
    private String submitStatus;
    private LocalDateTime submitTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

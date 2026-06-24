package com.example.teamflow.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ProjectVO {
    private Long id;
    private Long templateId;
    private String templateMode;
    private String projectCode;
    private String projectName;
    private String description;
    private String status;
    private String joinPolicy;
    private String joinLink;
    private Integer archivedFlag;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long creatorId;
    private String creatorName;
    private Integer memberCount;
    private Integer taskCount;
    private Integer doneTaskCount;
    private Integer sectionCount;
    private Integer approvedSectionCount;
    private BigDecimal completenessScore;
    private String riskLevel;
    private String wbsData;
    private String githubRepo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

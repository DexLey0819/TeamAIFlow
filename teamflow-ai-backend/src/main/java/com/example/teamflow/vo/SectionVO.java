package com.example.teamflow.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SectionVO {
    private Long id;
    private Long projectId;
    private String sectionCode;
    private String sectionName;
    private String description;
    private Integer requiredFlag;
    private Long ownerRoleId;
    private String ownerRoleName;
    private String status;
    private Integer sortOrder;
    private SectionContentVO latestContent;
    private Boolean missing;
    private Boolean allApproved;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

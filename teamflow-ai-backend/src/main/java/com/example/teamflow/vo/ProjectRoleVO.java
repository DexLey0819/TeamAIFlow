package com.example.teamflow.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ProjectRoleVO {
    private Long id;
    private Long projectId;
    private String roleCode;
    private String roleName;
    private String responsibility;
    private Integer maxCount;
    private Integer currentCount;
    private Integer remainingCount;
    private Integer requiredFlag;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

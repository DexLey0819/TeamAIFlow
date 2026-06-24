package com.example.teamflow.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ProjectTemplateVO {
    private Long id;
    private String code;
    private String name;
    private String mode;
    private String description;
    private String roleDefaults;
    private String sectionDefaults;
    private String permissionDefaults;
    private String aiCheckRules;
    private Integer enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

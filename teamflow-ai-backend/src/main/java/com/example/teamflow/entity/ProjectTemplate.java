package com.example.teamflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("project_template")
public class ProjectTemplate {
    @TableId(type = IdType.AUTO)
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

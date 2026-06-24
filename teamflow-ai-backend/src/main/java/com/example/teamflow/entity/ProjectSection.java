package com.example.teamflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("project_section")
public class ProjectSection {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String sectionCode;
    private String sectionName;
    private String description;
    private Integer requiredFlag;
    private Long ownerRoleId;
    private String status;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

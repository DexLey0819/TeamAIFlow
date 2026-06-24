package com.example.teamflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("project_role")
public class ProjectRole {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String roleCode;
    private String roleName;
    private String responsibility;
    private Integer maxCount;
    private Integer currentCount;
    private Integer requiredFlag;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

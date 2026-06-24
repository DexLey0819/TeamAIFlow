package com.example.teamflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("role_section_permission")
public class RoleSectionPermission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long projectRoleId;
    private Long sectionId;
    private String permissionCode;
    private LocalDateTime createTime;
}

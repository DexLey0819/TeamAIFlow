package com.example.teamflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("project_join_apply")
public class ProjectJoinApply {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long applicantId;
    private Long requestedRoleId;
    private String applicantName;
    private String applicantEmail;
    private String applyNote;
    private String status;
    private Long reviewUserId;
    private String reviewComment;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

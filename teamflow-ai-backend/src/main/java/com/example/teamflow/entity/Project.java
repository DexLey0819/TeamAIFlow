package com.example.teamflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("project")
public class Project {
    @TableId(type = IdType.AUTO)
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
    private String wbsData;
    private String githubRepo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

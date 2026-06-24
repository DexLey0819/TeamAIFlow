package com.example.teamflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("progress_record")
public class ProgressRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long userId;
    private Long projectRoleId;
    private Long relatedTaskId;
    private Long relatedSectionId;
    private String reportPeriod;
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private String completedWork;
    private String problems;
    private String helpNeeded;
    private String nextPlan;
    private String submitStatus;
    private LocalDateTime submitTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

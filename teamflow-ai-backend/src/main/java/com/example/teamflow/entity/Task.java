package com.example.teamflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("task")
public class Task {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long sectionId;
    private String title;
    private String description;
    private Long assigneeId;
    private Long creatorId;
    private String status;
    private String priority;
    private LocalDate startDate;
    private LocalDate dueDate;
    private LocalDateTime finishTime;
    private String blockReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

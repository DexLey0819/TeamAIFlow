package com.example.teamflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("task_log")
public class TaskLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long userId;
    private String actionType;
    private String oldStatus;
    private String newStatus;
    private String oldValue;
    private String newValue;
    private String content;
    private LocalDateTime createTime;
}

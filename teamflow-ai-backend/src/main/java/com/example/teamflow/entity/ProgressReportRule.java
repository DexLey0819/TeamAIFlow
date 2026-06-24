package com.example.teamflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("progress_report_rule")
public class ProgressReportRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String frequency;
    private String reportDay;
    private String reportTime;
    private Integer requiredFlag;
    private String overduePolicy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

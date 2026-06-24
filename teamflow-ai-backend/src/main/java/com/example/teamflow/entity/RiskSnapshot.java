package com.example.teamflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("risk_snapshot")
public class RiskSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long aiRecordId;
    private String riskLevel;
    private BigDecimal riskScore;
    private Integer taskDelayCount;
    private Integer blockedTaskCount;
    private Integer missingReportCount;
    private Integer rejectedSectionCount;
    private String trend;
    private String summary;
    private LocalDateTime snapshotTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

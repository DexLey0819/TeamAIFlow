package com.example.teamflow.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class RiskTrendVO {
    private Long projectId;
    private List<RiskSnapshotItem> snapshots;

    @Data
    public static class RiskSnapshotItem {
        private Long id;
        private String riskLevel;
        private BigDecimal riskScore;
        private Integer taskDelayCount;
        private Integer blockedTaskCount;
        private Integer missingReportCount;
        private Integer rejectedSectionCount;
        private String trend;
        private LocalDateTime snapshotTime;
    }
}

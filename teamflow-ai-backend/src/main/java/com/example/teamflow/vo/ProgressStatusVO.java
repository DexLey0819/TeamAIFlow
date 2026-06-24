package com.example.teamflow.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProgressStatusVO {
    private Long projectId;
    private String reportPeriod;
    private ReportRuleVO reportRule;
    private List<MemberStatus> memberStatuses;

    @Data
    public static class MemberStatus {
        private Long memberId;
        private Long userId;
        private String realName;
        private String roleName;
        private String submitStatus;
        private LocalDateTime submitTime;
    }
}

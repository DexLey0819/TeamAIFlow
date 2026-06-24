package com.example.teamflow.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class ContributionVO {
    private Long projectId;
    private List<MemberContribution> members;

    @Data
    public static class MemberContribution {
        private Long memberId;
        private Long userId;
        private String realName;
        private String roleName;
        private BigDecimal taskScore;
        private BigDecimal documentScore;
        private BigDecimal reportScore;
        private BigDecimal reviewScore;
        private BigDecimal defectScore;
        private BigDecimal totalScore;
    }
}

package com.example.teamflow.vo;

import lombok.Data;

@Data
public class ReportRuleVO {
    private Long id;
    private Long projectId;
    private String frequency;
    private String reportDay;
    private String reportTime;
    private Integer requiredFlag;
    private String overduePolicy;
}

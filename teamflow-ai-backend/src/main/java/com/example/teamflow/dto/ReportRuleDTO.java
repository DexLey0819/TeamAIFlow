package com.example.teamflow.dto;

import lombok.Data;

@Data
public class ReportRuleDTO {
    private String frequency;
    private String reportDay;
    private String reportTime;
    private Boolean requiredFlag;
    private String overduePolicy;
}

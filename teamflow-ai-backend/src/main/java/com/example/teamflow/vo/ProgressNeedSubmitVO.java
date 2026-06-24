package com.example.teamflow.vo;

import lombok.Data;

@Data
public class ProgressNeedSubmitVO {
    private Long projectId;
    private String projectName;
    private Boolean needSubmit;
    private Boolean overdue;
    private String reportPeriod;
    private ReportRuleVO reportRule;
}

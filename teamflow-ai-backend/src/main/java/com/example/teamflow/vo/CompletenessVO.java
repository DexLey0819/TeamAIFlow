package com.example.teamflow.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class CompletenessVO {
    private Long projectId;
    private BigDecimal score;
    private BigDecimal requiredSectionScore;
    private BigDecimal reviewPassScore;
    private BigDecimal taskCompletionScore;
    private BigDecimal defectClosureScore;
    private BigDecimal exportReadinessScore;
    private List<String> deductionReasons;
}

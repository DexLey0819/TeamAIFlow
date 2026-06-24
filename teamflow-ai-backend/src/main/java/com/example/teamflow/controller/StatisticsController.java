package com.example.teamflow.controller;

import com.example.teamflow.common.Result;
import com.example.teamflow.service.StatisticsService;
import com.example.teamflow.vo.CompletenessVO;
import com.example.teamflow.vo.ContributionVO;
import com.example.teamflow.vo.RiskTrendVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class StatisticsController {
    private final StatisticsService statisticsService;

    @GetMapping("/{id}/completeness")
    public Result<CompletenessVO> completeness(@PathVariable Long id) {
        return Result.success(statisticsService.completeness(id));
    }

    @GetMapping("/{id}/contribution")
    public Result<ContributionVO> contribution(@PathVariable Long id) {
        return Result.success(statisticsService.contribution(id));
    }

    @GetMapping("/{id}/risk-trend")
    public Result<RiskTrendVO> riskTrend(@PathVariable Long id) {
        return Result.success(statisticsService.riskTrend(id));
    }
}

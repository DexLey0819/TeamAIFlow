package com.example.teamflow.controller;

import com.example.teamflow.common.Result;
import com.example.teamflow.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;

    @GetMapping("/user")
    public Result<Map<String, Object>> user() {
        return Result.success(dashboardService.userDashboard());
    }

    @GetMapping("/project/{projectId}")
    public Result<Map<String, Object>> project(@PathVariable Long projectId) {
        return Result.success(dashboardService.projectDashboard(projectId));
    }
}

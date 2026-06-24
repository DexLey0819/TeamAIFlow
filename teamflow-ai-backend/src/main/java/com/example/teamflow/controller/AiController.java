package com.example.teamflow.controller;

import com.example.teamflow.common.Result;
import com.example.teamflow.service.AiBizService;
import com.example.teamflow.vo.AiRecordVO;
import com.example.teamflow.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/projects/{id}")
@RequiredArgsConstructor
public class AiController {
    private final AiBizService aiBizService;
    private final PermissionService permissionService;

    @PostMapping("/sections/{sectionId}/generate")
    public Result<Map<String, String>> generateSectionContent(
            @PathVariable("id") Long id,
            @PathVariable("sectionId") Long sectionId) {
        permissionService.requireSectionPermission(sectionId, "AI_GENERATE");
        return Result.success(aiBizService.generateSectionContent(id, sectionId));
    }

    @PostMapping("/sections/{sectionId}/chat")
    public Result<Map<String, String>> chatSectionContent(
            @PathVariable("id") Long id,
            @PathVariable("sectionId") Long sectionId,
            @RequestBody Map<String, Object> body) {
        permissionService.requireSectionPermission(sectionId, "AI_GENERATE");
        List<Map<String, String>> messages = (List<Map<String, String>>) body.get("messages");
        return Result.success(aiBizService.chatSectionContent(id, sectionId, messages));
    }

    @PostMapping("/weekly-report")
    public Result<AiRecordVO> weeklyReport(@PathVariable("id") Long id) {
        return Result.success(aiBizService.weeklyReport(id));
    }

    @PostMapping("/risk-analysis")
    public Result<AiRecordVO> riskAnalysis(@PathVariable("id") Long id) {
        return Result.success(aiBizService.riskAnalysis(id));
    }

    @PostMapping("/document-check")
    public Result<AiRecordVO> documentCheck(@PathVariable("id") Long id) {
        return Result.success(aiBizService.documentCheck(id));
    }

    @PostMapping("/summary-report")
    public Result<AiRecordVO> summaryReport(@PathVariable("id") Long id) {
        return Result.success(aiBizService.summaryReport(id));
    }

    @GetMapping("/records")
    public Result<List<AiRecordVO>> records(@PathVariable("id") Long id) {
        return Result.success(aiBizService.records(id));
    }

    @PostMapping("/extract-progress")
    public Result<Map<String, Object>> extractProgress(
            @PathVariable("id") Long id,
            @RequestBody Map<String, Object> body) {
        String reportText = (String) body.get("reportText");
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) body.get("tasks");
        return Result.success(aiBizService.extractProgress(id, reportText, tasks));
    }
}

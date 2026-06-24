package com.example.teamflow.controller;

import com.example.teamflow.common.Result;
import com.example.teamflow.dto.ProgressReportDTO;
import com.example.teamflow.service.ProgressService;
import com.example.teamflow.vo.ProgressNeedSubmitVO;
import com.example.teamflow.vo.ProgressStatusVO;
import com.example.teamflow.vo.ProgressRecordVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProgressController {
    private final ProgressService progressService;

    @GetMapping("/progress/need-submit")
    public Result<List<ProgressNeedSubmitVO>> needSubmit() {
        return Result.success(progressService.needSubmit());
    }

    @PostMapping("/progress/report")
    public Result<Void> submitReport(@Valid @RequestBody ProgressReportDTO dto) {
        progressService.submitReport(dto);
        return Result.success();
    }

    @GetMapping("/projects/{id}/progress-status")
    public Result<ProgressStatusVO> progressStatus(@PathVariable Long id) {
        return Result.success(progressService.progressStatus(id));
    }

    @GetMapping("/projects/{id}/progress-records")
    public Result<List<ProgressRecordVO>> progressRecords(@PathVariable Long id) {
        return Result.success(progressService.progressRecords(id));
    }
}

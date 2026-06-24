package com.example.teamflow.controller;

import com.example.teamflow.common.Result;
import com.example.teamflow.dto.ExportRequestDTO;
import com.example.teamflow.service.ExportService;
import com.example.teamflow.vo.ExportRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
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
public class ExportController {
    private final ExportService exportService;

    @PostMapping("/projects/{id}/export")
    public Result<ExportRecordVO> create(@PathVariable Long id, @RequestBody(required = false) ExportRequestDTO request) {
        return Result.success(exportService.create(id, request));
    }

    @GetMapping("/projects/{id}/export/history")
    public Result<List<ExportRecordVO>> history(@PathVariable Long id) {
        return Result.success(exportService.history(id));
    }

    @GetMapping("/exports/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        return exportService.download(id);
    }
}

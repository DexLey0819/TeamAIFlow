package com.example.teamflow.controller;

import com.example.teamflow.common.Result;
import com.example.teamflow.dto.TemplateUpdateDTO;
import com.example.teamflow.service.ProjectTemplateService;
import com.example.teamflow.vo.ProjectTemplateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ProjectTemplateController {
    private final ProjectTemplateService projectTemplateService;

    @GetMapping("/api/project-templates")
    public Result<List<ProjectTemplateVO>> listEnabled() {
        return Result.success(projectTemplateService.listEnabled());
    }

    @GetMapping("/api/project-templates/{code}")
    public Result<ProjectTemplateVO> detail(@PathVariable String code) {
        return Result.success(projectTemplateService.detail(code));
    }

    @GetMapping("/api/admin/project-templates")
    public Result<List<ProjectTemplateVO>> adminListAll() {
        return Result.success(projectTemplateService.adminListAll());
    }

    @PutMapping("/api/admin/project-templates/{id}")
    public Result<ProjectTemplateVO> adminUpdate(@PathVariable Long id, @RequestBody TemplateUpdateDTO dto) {
        return Result.success(projectTemplateService.adminUpdate(id, dto));
    }
}

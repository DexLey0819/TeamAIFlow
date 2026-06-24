package com.example.teamflow.controller;

import com.example.teamflow.common.Result;
import com.example.teamflow.dto.ProjectInitDTO;
import com.example.teamflow.service.ProjectService;
import com.example.teamflow.vo.ProjectInitResultVO;
import com.example.teamflow.vo.ProjectVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectService projectService;

    @PostMapping("/init")
    public Result<ProjectInitResultVO> init(@Valid @RequestBody ProjectInitDTO dto) {
        return Result.success(projectService.init(dto));
    }

    @GetMapping("/{id}/init-result")
    public Result<ProjectInitResultVO> initResult(@PathVariable Long id) {
        return Result.success(projectService.initResult(id));
    }

    @GetMapping({"", "/my"})
    public Result<List<ProjectVO>> myProjects() {
        return Result.success(projectService.myProjects());
    }

    @GetMapping("/{id}")
    public Result<ProjectVO> detail(@PathVariable Long id) {
        return Result.success(projectService.detail(id));
    }

    @PostMapping("/{id}/wbs")
    public Result<Void> saveWbs(@PathVariable Long id, @RequestBody String wbsData) {
        projectService.saveWbs(id, wbsData);
        return Result.success();
    }

    @PostMapping("/{id}/github-repo")
    public Result<Void> updateGithubRepo(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        projectService.updateGithubRepo(id, body.get("githubRepo"));
        return Result.success();
    }
}

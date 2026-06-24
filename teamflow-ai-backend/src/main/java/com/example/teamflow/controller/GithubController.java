package com.example.teamflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.teamflow.common.Result;
import com.example.teamflow.entity.Project;
import com.example.teamflow.mapper.ProjectMapper;
import com.example.teamflow.service.GithubService;
import com.example.teamflow.service.SectionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/github")
@RequiredArgsConstructor
public class GithubController {

    private final GithubService githubService;
    private final SectionService sectionService;
    private final ProjectMapper projectMapper;

    @GetMapping("/{projectId}/status")
    public Result<Map<String, Object>> status(@PathVariable Long projectId, @RequestParam String sectionCode) {
        Map<String, Object> data = new HashMap<>();
        data.put("branch", githubService.getBranchInfo(projectId, sectionCode));
        data.put("commits", githubService.getCommits(projectId, sectionCode));
        data.put("pullRequest", githubService.getPullRequest(projectId, sectionCode));
        data.put("workflows", githubService.getWorkflowRuns(projectId));
        return Result.success(data);
    }

    @PostMapping("/{projectId}/branch")
    public Result<Void> createBranch(@PathVariable Long projectId, @RequestBody Map<String, String> body) {
        githubService.createBranch(projectId, body.get("sectionCode"));
        return Result.success();
    }

    @PostMapping("/{projectId}/pull-request")
    public Result<Map<String, Object>> createPullRequest(
            @PathVariable Long projectId,
            @RequestBody Map<String, String> body) {
        return Result.success(githubService.createPullRequest(
                projectId,
                body.get("sectionCode"),
                body.get("title"),
                body.get("body")
        ));
    }

    @PostMapping("/{projectId}/ai-review")
    public Result<String> triggerAiReview(@PathVariable Long projectId, @RequestBody Map<String, String> body) {
        return Result.success(githubService.triggerAiReview(projectId, body.get("sectionCode")));
    }

    @PostMapping("/webhook")
    public Result<Void> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-GitHub-Event", required = false) String event) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(payload);

            if ("pull_request".equalsIgnoreCase(event)) {
                String action = root.path("action").asText();
                JsonNode prNode = root.path("pull_request");
                String headRef = prNode.path("head").path("ref").asText();
                String repoFullName = root.path("repository").path("full_name").asText();

                List<Project> projects = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                        .eq(Project::getGithubRepo, repoFullName));
                if (projects.isEmpty()) {
                    return Result.success();
                }

                String sectionCode = null;
                if (headRef.equalsIgnoreCase("feature/frontend")) {
                    sectionCode = "FRONTEND";
                } else if (headRef.equalsIgnoreCase("feature/backend")) {
                    sectionCode = "BACKEND";
                }

                if (sectionCode != null) {
                    for (Project project : projects) {
                        if ("opened".equalsIgnoreCase(action) || "reopened".equalsIgnoreCase(action) || "synchronize".equalsIgnoreCase(action)) {
                            sectionService.updateSectionStatus(project.getId(), sectionCode, "REVIEWING");
                        } else if ("closed".equalsIgnoreCase(action)) {
                            boolean merged = prNode.path("merged").asBoolean();
                            if (merged) {
                                sectionService.updateSectionStatus(project.getId(), sectionCode, "APPROVED");
                            } else {
                                sectionService.updateSectionStatus(project.getId(), sectionCode, "DRAFT");
                            }
                        }
                    }
                }
            } else if ("push".equalsIgnoreCase(event)) {
                String ref = root.path("ref").asText();
                String repoFullName = root.path("repository").path("full_name").asText();

                List<Project> projects = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                        .eq(Project::getGithubRepo, repoFullName));

                String sectionCode = null;
                if (ref.endsWith("/feature/frontend")) {
                    sectionCode = "FRONTEND";
                } else if (ref.endsWith("/feature/backend")) {
                    sectionCode = "BACKEND";
                }

                if (sectionCode != null && !projects.isEmpty()) {
                    for (Project project : projects) {
                        sectionService.updateSectionStatus(project.getId(), sectionCode, "DRAFT");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("处理 GitHub Webhook 异常: " + e.getMessage());
        }
        return Result.success();
    }
}

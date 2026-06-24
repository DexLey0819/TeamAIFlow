package com.example.teamflow.controller;

import com.example.teamflow.common.Result;
import com.example.teamflow.dto.SectionCommentDTO;
import com.example.teamflow.dto.SectionContentDTO;
import com.example.teamflow.dto.SectionReviewDTO;
import com.example.teamflow.service.SectionService;
import com.example.teamflow.vo.SectionCommentVO;
import com.example.teamflow.vo.SectionContentVO;
import com.example.teamflow.vo.SectionReviewVO;
import com.example.teamflow.vo.SectionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SectionController {
    private final SectionService sectionService;

    @GetMapping("/api/projects/{id}/sections")
    public Result<List<SectionVO>> list(@PathVariable Long id) {
        return Result.success(sectionService.list(id));
    }

    @GetMapping("/api/sections/{id}")
    public Result<SectionVO> detail(@PathVariable Long id) {
        return Result.success(sectionService.detail(id));
    }

    @PutMapping("/api/sections/{id}/content")
    public Result<SectionContentVO> saveDraft(@PathVariable Long id, @Valid @RequestBody SectionContentDTO dto) {
        return Result.success(sectionService.saveDraft(id, dto));
    }

    @PostMapping("/api/sections/{id}/submit")
    public Result<SectionContentVO> submit(@PathVariable Long id) {
        return Result.success(sectionService.submit(id));
    }

    @PostMapping("/api/sections/{id}/review")
    public Result<SectionReviewVO> review(@PathVariable Long id, @Valid @RequestBody SectionReviewDTO dto) {
        return Result.success(sectionService.review(id, dto));
    }

    @PostMapping("/api/sections/{id}/comments")
    public Result<SectionCommentVO> comment(@PathVariable Long id, @Valid @RequestBody SectionCommentDTO dto) {
        return Result.success(sectionService.comment(id, dto));
    }

    @GetMapping("/api/sections/{id}/comments")
    public Result<List<SectionCommentVO>> listComments(@PathVariable Long id) {
        return Result.success(sectionService.listComments(id));
    }

    @GetMapping("/api/sections/{id}/reviews")
    public Result<List<SectionReviewVO>> listReviews(@PathVariable Long id) {
        return Result.success(sectionService.listReviews(id));
    }

    @GetMapping("/api/projects/{projectId}/reviews/me")
    public Result<List<SectionVO>> listMyReviews(@PathVariable Long projectId) {
        return Result.success(sectionService.listMyReviews(projectId));
    }

    @GetMapping("/api/sections/{id}/versions")
    public Result<List<SectionContentVO>> versions(@PathVariable Long id) {
        return Result.success(sectionService.versions(id));
    }
}

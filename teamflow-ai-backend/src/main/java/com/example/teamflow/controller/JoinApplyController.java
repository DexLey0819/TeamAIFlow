package com.example.teamflow.controller;

import com.example.teamflow.common.Result;
import com.example.teamflow.dto.JoinApplyDTO;
import com.example.teamflow.dto.JoinReviewDTO;
import com.example.teamflow.service.JoinApplyService;
import com.example.teamflow.vo.JoinApplyVO;
import com.example.teamflow.vo.JoinPreviewVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class JoinApplyController {
    private final JoinApplyService joinApplyService;

    @GetMapping("/join/{projectCode}")
    public Result<JoinPreviewVO> preview(@PathVariable String projectCode) {
        return Result.success(joinApplyService.preview(projectCode));
    }

    @PostMapping("/{id}/join-apply")
    public Result<JoinApplyVO> apply(@PathVariable Long id, @Valid @RequestBody JoinApplyDTO dto) {
        return Result.success(joinApplyService.apply(id, dto));
    }

    @GetMapping("/{id}/join-applies")
    public Result<List<JoinApplyVO>> list(@PathVariable Long id) {
        return Result.success(joinApplyService.list(id));
    }

    @PutMapping("/{id}/join-applies/{applyId}/approve")
    public Result<JoinApplyVO> approve(@PathVariable Long id, @PathVariable Long applyId, @RequestBody JoinReviewDTO dto) {
        return Result.success(joinApplyService.approve(id, applyId, dto));
    }

    @PutMapping("/{id}/join-applies/{applyId}/reject")
    public Result<JoinApplyVO> reject(@PathVariable Long id, @PathVariable Long applyId, @RequestBody JoinReviewDTO dto) {
        return Result.success(joinApplyService.reject(id, applyId, dto));
    }
}

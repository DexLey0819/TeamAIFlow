package com.example.teamflow.controller;

import com.example.teamflow.common.Result;
import com.example.teamflow.service.MemberService;
import com.example.teamflow.vo.ProjectMemberVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/members")
@RequiredArgsConstructor
public class MemberController {
    private final MemberService memberService;

    @GetMapping
    public Result<List<ProjectMemberVO>> list(@PathVariable Long projectId) {
        return Result.success(memberService.list(projectId));
    }
}

package com.example.teamflow.controller;

import com.example.teamflow.common.Result;
import com.example.teamflow.service.SectionService;
import com.example.teamflow.vo.PermissionMatrixVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PermissionController {
    private final SectionService sectionService;

    @GetMapping("/api/projects/{id}/permissions")
    public Result<PermissionMatrixVO> projectPermissions(@PathVariable Long id) {
        return Result.success(sectionService.permissionMatrix(id));
    }

    @GetMapping("/api/sections/{id}/permissions/me")
    public Result<List<String>> mySectionPermissions(@PathVariable Long id) {
        return Result.success(sectionService.mySectionPermissions(id));
    }
}

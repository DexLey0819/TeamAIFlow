package com.example.teamflow.controller;

import com.example.teamflow.common.Result;
import com.example.teamflow.dto.AgentProfileUpdateDTO;
import com.example.teamflow.service.ai.AgentProfileService;
import com.example.teamflow.vo.AgentProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AgentProfileController {
    private final AgentProfileService agentProfileService;

    @GetMapping("/api/projects/{projectId}/agent-profiles")
    public Result<List<AgentProfileVO>> list(@PathVariable Long projectId) {
        return Result.success(agentProfileService.listVisible(projectId));
    }

    @GetMapping("/api/projects/{projectId}/roles/{roleId}/agent-profile")
    public Result<AgentProfileVO> detail(
            @PathVariable Long projectId,
            @PathVariable Long roleId
    ) {
        return Result.success(agentProfileService.getVisible(projectId, roleId));
    }

    @PutMapping("/api/projects/{projectId}/roles/{roleId}/agent-profile")
    public Result<AgentProfileVO> save(
            @PathVariable Long projectId,
            @PathVariable Long roleId,
            @Valid @RequestBody AgentProfileUpdateDTO dto
    ) {
        return Result.success(agentProfileService.saveProjectSnapshot(projectId, roleId, dto));
    }

    @DeleteMapping("/api/projects/{projectId}/roles/{roleId}/agent-profile")
    public Result<AgentProfileVO> reset(
            @PathVariable Long projectId,
            @PathVariable Long roleId
    ) {
        return Result.success(agentProfileService.resetProjectSnapshot(projectId, roleId));
    }
}

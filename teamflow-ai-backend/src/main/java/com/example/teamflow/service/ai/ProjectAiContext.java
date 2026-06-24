package com.example.teamflow.service.ai;

import com.example.teamflow.entity.ExportRecord;
import com.example.teamflow.entity.ProgressRecord;
import com.example.teamflow.entity.ProjectMember;
import com.example.teamflow.entity.ProjectRole;
import com.example.teamflow.entity.ProjectSection;
import com.example.teamflow.entity.RiskSnapshot;
import com.example.teamflow.entity.SysUser;
import com.example.teamflow.entity.Task;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ProjectAiContext {
    private List<ProjectRole> roles = List.of();
    private List<ProjectMember> members = List.of();
    private List<ProjectSection> sections = List.of();
    private List<Task> tasks = List.of();
    private List<ProgressRecord> progressRecords = List.of();
    private List<RiskSnapshot> riskSnapshots = List.of();
    private List<ExportRecord> exportRecords = List.of();
    private long fileCount;
    private Map<Long, SysUser> users = Map.of();
    private Map<Long, ProjectRole> rolesById = Map.of();
}

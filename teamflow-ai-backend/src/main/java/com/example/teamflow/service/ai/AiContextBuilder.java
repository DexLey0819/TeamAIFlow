package com.example.teamflow.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.teamflow.entity.ExportRecord;
import com.example.teamflow.entity.ProgressRecord;
import com.example.teamflow.entity.Project;
import com.example.teamflow.entity.ProjectFile;
import com.example.teamflow.entity.ProjectMember;
import com.example.teamflow.entity.ProjectRole;
import com.example.teamflow.entity.ProjectSection;
import com.example.teamflow.entity.RiskSnapshot;
import com.example.teamflow.entity.SysUser;
import com.example.teamflow.entity.Task;
import com.example.teamflow.mapper.ExportRecordMapper;
import com.example.teamflow.mapper.ProgressRecordMapper;
import com.example.teamflow.mapper.ProjectFileMapper;
import com.example.teamflow.mapper.ProjectMemberMapper;
import com.example.teamflow.mapper.ProjectRoleMapper;
import com.example.teamflow.mapper.ProjectSectionMapper;
import com.example.teamflow.mapper.RiskSnapshotMapper;
import com.example.teamflow.mapper.SysUserMapper;
import com.example.teamflow.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiContextBuilder {
    private final ProjectRoleMapper projectRoleMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectSectionMapper projectSectionMapper;
    private final TaskMapper taskMapper;
    private final ProgressRecordMapper progressRecordMapper;
    private final ProjectFileMapper projectFileMapper;
    private final RiskSnapshotMapper riskSnapshotMapper;
    private final ExportRecordMapper exportRecordMapper;
    private final SysUserMapper sysUserMapper;

    public ProjectAiContext build(Project project) {
        ProjectAiContext context = new ProjectAiContext();
        context.setRoles(projectRoleMapper.selectList(new LambdaQueryWrapper<ProjectRole>()
                .eq(ProjectRole::getProjectId, project.getId())
                .orderByAsc(ProjectRole::getSortOrder)
                .orderByAsc(ProjectRole::getId)));
        context.setMembers(projectMemberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, project.getId())
                .orderByAsc(ProjectMember::getId)));
        context.setSections(projectSectionMapper.selectList(new LambdaQueryWrapper<ProjectSection>()
                .eq(ProjectSection::getProjectId, project.getId())
                .orderByAsc(ProjectSection::getSortOrder)
                .orderByAsc(ProjectSection::getId)));
        context.setTasks(taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, project.getId())
                .orderByDesc(Task::getUpdateTime)
                .orderByDesc(Task::getId)));
        context.setProgressRecords(progressRecordMapper.selectList(new LambdaQueryWrapper<ProgressRecord>()
                .eq(ProgressRecord::getProjectId, project.getId())
                .orderByDesc(ProgressRecord::getSubmitTime)
                .orderByDesc(ProgressRecord::getCreateTime)
                .orderByDesc(ProgressRecord::getId)));
        context.setRiskSnapshots(riskSnapshotMapper.selectList(new LambdaQueryWrapper<RiskSnapshot>()
                .eq(RiskSnapshot::getProjectId, project.getId())
                .orderByDesc(RiskSnapshot::getSnapshotTime)
                .orderByDesc(RiskSnapshot::getId)));
        context.setExportRecords(exportRecordMapper.selectList(new LambdaQueryWrapper<ExportRecord>()
                .eq(ExportRecord::getProjectId, project.getId())
                .orderByDesc(ExportRecord::getCreateTime)
                .orderByDesc(ExportRecord::getId)));
        context.setFileCount(projectFileMapper.selectCount(new LambdaQueryWrapper<ProjectFile>()
                .eq(ProjectFile::getProjectId, project.getId())));

        Set<Long> userIds = context.getMembers().stream()
                .map(ProjectMember::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (project.getCreatorId() != null) {
            userIds.add(project.getCreatorId());
        }
        userIds.addAll(context.getProgressRecords().stream()
                .map(ProgressRecord::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        userIds.addAll(context.getTasks().stream()
                .map(Task::getAssigneeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        context.setUsers(loadUsers(userIds));
        context.setRolesById(context.getRoles().stream()
                .collect(Collectors.toMap(ProjectRole::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new)));
        return context;
    }

    private Map<Long, SysUser> loadUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }
}

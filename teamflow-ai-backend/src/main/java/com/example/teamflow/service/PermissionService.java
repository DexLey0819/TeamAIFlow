package com.example.teamflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.teamflow.common.BizException;
import com.example.teamflow.entity.ProjectMember;
import com.example.teamflow.entity.ProjectSection;
import com.example.teamflow.entity.RoleSectionPermission;
import com.example.teamflow.entity.SysUser;
import com.example.teamflow.mapper.ProjectMemberMapper;
import com.example.teamflow.mapper.ProjectSectionMapper;
import com.example.teamflow.mapper.RoleSectionPermissionMapper;
import com.example.teamflow.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PermissionService {
    private static final String ADMIN = "ADMIN";
    private static final String PROJECT_MANAGER = "PROJECT_MANAGER";
    private static final String MANAGE = "MANAGE";
    private static final List<String> ALL_PERMISSION_CODES = List.of(
            "VIEW", "EDIT", "COMMENT", "REVIEW", MANAGE, "EXPORT", "AI_GENERATE"
    );

    private final AuthService authService;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectSectionMapper projectSectionMapper;
    private final RoleSectionPermissionMapper roleSectionPermissionMapper;
    private final SysUserMapper sysUserMapper;

    public boolean isAdmin(SysUser user) {
        return user != null && ADMIN.equalsIgnoreCase(user.getRole());
    }

    public boolean isProjectManager(Long projectId, Long userId) {
        SysUser user = userId == null ? null : sysUserMapper.selectById(userId);
        if (isAdmin(user)) {
            return true;
        }
        ProjectMember member = findMember(projectId, userId);
        return member != null && PROJECT_MANAGER.equalsIgnoreCase(member.getMemberRole());
    }

    public void requireProjectMember(Long projectId) {
        SysUser user = authService.currentUser();
        if (isAdmin(user) || findMember(projectId, user.getId()) != null) {
            return;
        }
        throwNoPermission();
    }

    public void requireProjectManage(Long projectId) {
        SysUser user = authService.currentUser();
        if (isAdmin(user) || isProjectManager(projectId, user.getId())) {
            return;
        }
        throwNoPermission();
    }

    public void requireSectionPermission(Long sectionId, String permissionCode) {
        SysUser user = authService.currentUser();
        if (isAdmin(user)) {
            return;
        }

        ProjectSection section = findSectionOrThrow(sectionId);
        List<String> permissions = currentSectionPermissions(section, user);
        String normalizedPermission = normalize(permissionCode);
        if (permissions.contains(normalizedPermission) || permissions.contains(MANAGE)) {
            return;
        }
        throwNoPermission();
    }

    public List<String> currentSectionPermissions(Long sectionId) {
        SysUser user = authService.currentUser();
        if (isAdmin(user)) {
            return ALL_PERMISSION_CODES;
        }

        ProjectSection section = findSectionOrThrow(sectionId);
        return currentSectionPermissions(section, user);
    }

    private List<String> currentSectionPermissions(ProjectSection section, SysUser user) {
        ProjectMember member = findMember(section.getProjectId(), user.getId());
        if (member == null || member.getProjectRoleId() == null) {
            return List.of();
        }

        return roleSectionPermissionMapper.selectList(new LambdaQueryWrapper<RoleSectionPermission>()
                        .eq(RoleSectionPermission::getProjectId, section.getProjectId())
                        .eq(RoleSectionPermission::getProjectRoleId, member.getProjectRoleId())
                        .eq(RoleSectionPermission::getSectionId, section.getId()))
                .stream()
                .map(RoleSectionPermission::getPermissionCode)
                .filter(Objects::nonNull)
                .map(this::normalize)
                .distinct()
                .toList();
    }

    private ProjectMember findMember(Long projectId, Long userId) {
        if (projectId == null || userId == null) {
            return null;
        }
        return projectMemberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId)
                .last("limit 1"));
    }

    private ProjectSection findSectionOrThrow(Long sectionId) {
        ProjectSection section = sectionId == null ? null : projectSectionMapper.selectById(sectionId);
        if (section == null) {
            throw new BizException(404, "板块不存在");
        }
        return section;
    }

    private String normalize(String permissionCode) {
        return permissionCode == null ? "" : permissionCode.trim().toUpperCase(Locale.ROOT);
    }

    private void throwNoPermission() {
        throw new BizException(403, "没有权限执行该操作");
    }
}

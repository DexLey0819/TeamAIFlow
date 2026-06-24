package com.example.teamflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.teamflow.common.BizException;
import com.example.teamflow.dto.JoinApplyDTO;
import com.example.teamflow.dto.JoinReviewDTO;
import com.example.teamflow.entity.Project;
import com.example.teamflow.entity.ProjectJoinApply;
import com.example.teamflow.entity.ProjectMember;
import com.example.teamflow.entity.ProjectRole;
import com.example.teamflow.entity.SysUser;
import com.example.teamflow.mapper.ProjectJoinApplyMapper;
import com.example.teamflow.mapper.ProjectMapper;
import com.example.teamflow.mapper.ProjectMemberMapper;
import com.example.teamflow.mapper.ProjectRoleMapper;
import com.example.teamflow.mapper.SysUserMapper;
import com.example.teamflow.vo.JoinApplyVO;
import com.example.teamflow.vo.JoinPreviewVO;
import com.example.teamflow.vo.ProjectRoleVO;
import com.example.teamflow.vo.ProjectVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class JoinApplyService {
    private static final String PROJECT_MANAGER = "PROJECT_MANAGER";
    private static final String JOIN_REVIEW = "REVIEW";
    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";

    private final AuthService authService;
    private final PermissionService permissionService;
    private final NotificationService notificationService;
    private final ProjectMapper projectMapper;
    private final ProjectRoleMapper projectRoleMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectJoinApplyMapper projectJoinApplyMapper;
    private final SysUserMapper sysUserMapper;

    public JoinPreviewVO preview(String projectCode) {
        Project project = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getProjectCode, projectCode)
                .last("limit 1"));
        if (project == null) {
            throw new BizException(404, "项目不存在");
        }

        JoinPreviewVO vo = new JoinPreviewVO();
        vo.setProject(toProjectVO(project));
        vo.setJoinEnabled(JOIN_REVIEW.equalsIgnoreCase(project.getJoinPolicy()));
        if (JOIN_REVIEW.equalsIgnoreCase(project.getJoinPolicy())) {
            vo.setAvailableRoles(projectRoleMapper.selectList(new LambdaQueryWrapper<ProjectRole>()
                            .eq(ProjectRole::getProjectId, project.getId())
                            .orderByAsc(ProjectRole::getSortOrder)
                            .orderByAsc(ProjectRole::getId))
                    .stream()
                    .filter(role -> !isFull(role))
                    .map(this::toRoleVO)
                    .toList());
        } else {
            vo.setAvailableRoles(List.of());
        }
        return vo;
    }

    @Transactional
    public JoinApplyVO apply(Long projectId, JoinApplyDTO dto) {
        SysUser current = authService.currentUser();
        Project project = findProject(projectId);
        if (!JOIN_REVIEW.equalsIgnoreCase(project.getJoinPolicy())) {
            throw new BizException(400, "该项目暂未开放申请加入");
        }
        if (isMember(projectId, current.getId())) {
            throw new BizException(400, "你已经是项目成员");
        }
        Long pendingCount = projectJoinApplyMapper.selectCount(new LambdaQueryWrapper<ProjectJoinApply>()
                .eq(ProjectJoinApply::getProjectId, projectId)
                .eq(ProjectJoinApply::getApplicantId, current.getId())
                .eq(ProjectJoinApply::getStatus, PENDING));
        if (pendingCount != null && pendingCount > 0) {
            throw new BizException(400, "你已提交过待审核申请");
        }

        ProjectRole role = findRole(projectId, dto.getRequestedRoleId());
        ensureRoleAvailable(role);

        LocalDateTime now = LocalDateTime.now();
        ProjectJoinApply apply = new ProjectJoinApply();
        apply.setProjectId(projectId);
        apply.setApplicantId(current.getId());
        apply.setRequestedRoleId(role.getId());
        apply.setApplicantName(dto.getApplicantName());
        apply.setApplicantEmail(dto.getApplicantEmail());
        apply.setApplyNote(dto.getApplyNote());
        apply.setStatus(PENDING);
        apply.setCreateTime(now);
        apply.setUpdateTime(now);
        projectJoinApplyMapper.insert(apply);

        notifyManagers(project, "新的加入申请", current.getRealName() + " 申请加入项目：" + role.getRoleName(),
                "JOIN_APPLICATION", "/projects/" + projectId + "/members");
        return toVO(apply);
    }

    public List<JoinApplyVO> list(Long projectId) {
        permissionService.requireProjectManage(projectId);
        findProject(projectId);
        return projectJoinApplyMapper.selectList(new LambdaQueryWrapper<ProjectJoinApply>()
                        .eq(ProjectJoinApply::getProjectId, projectId)
                        .orderByDesc(ProjectJoinApply::getCreateTime))
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Transactional
    public JoinApplyVO approve(Long projectId, Long applyId, JoinReviewDTO dto) {
        permissionService.requireProjectManage(projectId);
        SysUser reviewer = authService.currentUser();
        Project project = findProject(projectId);
        ProjectJoinApply apply = findApply(projectId, applyId);
        requirePending(apply);
        if (isMember(projectId, apply.getApplicantId())) {
            throw new BizException(400, "申请人已是项目成员");
        }

        Long roleId = dto != null && dto.getTargetRoleId() != null ? dto.getTargetRoleId() : apply.getRequestedRoleId();
        ProjectRole role = findRole(projectId, roleId);
        ensureRoleAvailable(role);

        LocalDateTime now = LocalDateTime.now();
        ProjectJoinApply approvedApply = new ProjectJoinApply();
        approvedApply.setRequestedRoleId(role.getId());
        approvedApply.setStatus(APPROVED);
        approvedApply.setReviewUserId(reviewer.getId());
        approvedApply.setReviewComment(dto == null ? null : dto.getReviewComment());
        approvedApply.setReviewTime(now);
        approvedApply.setUpdateTime(now);
        int updatedRoleCount = incrementRoleCount(role);
        if (updatedRoleCount == 0) {
            throw new BizException(400, "该角色人数已满");
        }
        int updatedApplyCount = projectJoinApplyMapper.update(approvedApply, new LambdaUpdateWrapper<ProjectJoinApply>()
                .eq(ProjectJoinApply::getId, apply.getId())
                .eq(ProjectJoinApply::getProjectId, projectId)
                .eq(ProjectJoinApply::getStatus, PENDING));
        if (updatedApplyCount == 0) {
            throw new BizException(400, "该申请已被处理");
        }
        apply.setRequestedRoleId(role.getId());
        apply.setStatus(APPROVED);
        apply.setReviewUserId(reviewer.getId());
        apply.setReviewComment(approvedApply.getReviewComment());
        apply.setReviewTime(now);
        apply.setUpdateTime(now);

        ProjectMember member = new ProjectMember();
        member.setProjectId(projectId);
        member.setUserId(apply.getApplicantId());
        member.setProjectRoleId(role.getId());
        member.setMemberRole(role.getRoleCode());
        member.setMemberTitle(role.getRoleName());
        member.setJoinSource("APPLY");
        member.setJoinTime(now);
        member.setCreateTime(now);
        member.setUpdateTime(now);
        projectMemberMapper.insert(member);

        notificationService.notifyUser(apply.getApplicantId(), projectId, "加入申请已通过",
                "你已加入项目：" + project.getProjectName() + "，角色为：" + role.getRoleName(),
                "APPLICATION_RESULT", "/projects/" + projectId + "/overview");
        return toVO(apply);
    }

    @Transactional
    public JoinApplyVO reject(Long projectId, Long applyId, JoinReviewDTO dto) {
        permissionService.requireProjectManage(projectId);
        SysUser reviewer = authService.currentUser();
        Project project = findProject(projectId);
        ProjectJoinApply apply = findApply(projectId, applyId);
        requirePending(apply);

        LocalDateTime now = LocalDateTime.now();
        ProjectJoinApply rejectedApply = new ProjectJoinApply();
        rejectedApply.setStatus(REJECTED);
        rejectedApply.setReviewUserId(reviewer.getId());
        rejectedApply.setReviewComment(dto == null ? null : dto.getReviewComment());
        rejectedApply.setReviewTime(now);
        rejectedApply.setUpdateTime(now);
        int updatedApplyCount = projectJoinApplyMapper.update(rejectedApply, new LambdaUpdateWrapper<ProjectJoinApply>()
                .eq(ProjectJoinApply::getId, apply.getId())
                .eq(ProjectJoinApply::getProjectId, projectId)
                .eq(ProjectJoinApply::getStatus, PENDING));
        if (updatedApplyCount == 0) {
            throw new BizException(400, "该申请已被处理");
        }
        apply.setStatus(REJECTED);
        apply.setReviewUserId(reviewer.getId());
        apply.setReviewComment(rejectedApply.getReviewComment());
        apply.setReviewTime(now);
        apply.setUpdateTime(now);

        notificationService.notifyUser(apply.getApplicantId(), projectId, "加入申请未通过",
                "你申请加入项目：" + project.getProjectName() + " 未通过。"
                        + (dto == null || dto.getReviewComment() == null ? "" : "原因：" + dto.getReviewComment()),
                "APPLICATION_RESULT", "/join/" + project.getProjectCode());
        return toVO(apply);
    }

    private Project findProject(Long projectId) {
        Project project = projectId == null ? null : projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException(404, "项目不存在");
        }
        return project;
    }

    private ProjectRole findRole(Long projectId, Long roleId) {
        ProjectRole role = roleId == null ? null : projectRoleMapper.selectById(roleId);
        if (role == null || !Objects.equals(projectId, role.getProjectId())) {
            throw new BizException(404, "项目角色不存在");
        }
        return role;
    }

    private ProjectJoinApply findApply(Long projectId, Long applyId) {
        ProjectJoinApply apply = applyId == null ? null : projectJoinApplyMapper.selectById(applyId);
        if (apply == null || !Objects.equals(projectId, apply.getProjectId())) {
            throw new BizException(404, "加入申请不存在");
        }
        return apply;
    }

    private void requirePending(ProjectJoinApply apply) {
        if (!PENDING.equalsIgnoreCase(apply.getStatus())) {
            throw new BizException(400, "该申请已审核");
        }
    }

    private boolean isMember(Long projectId, Long userId) {
        Long count = projectMemberMapper.selectCount(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId));
        return count != null && count > 0;
    }

    private void ensureRoleAvailable(ProjectRole role) {
        if (isFull(role)) {
            throw new BizException(400, "该角色人数已满");
        }
    }

    private int incrementRoleCount(ProjectRole role) {
        LambdaUpdateWrapper<ProjectRole> wrapper = new LambdaUpdateWrapper<ProjectRole>()
                .eq(ProjectRole::getId, role.getId())
                .setSql("current_count = COALESCE(current_count, 0) + 1")
                .set(ProjectRole::getUpdateTime, LocalDateTime.now());
        if (role.getMaxCount() != null && role.getMaxCount() > 0) {
            wrapper.apply("COALESCE(current_count, 0) < max_count");
        }
        return projectRoleMapper.update(null, wrapper);
    }

    private boolean isFull(ProjectRole role) {
        return role.getMaxCount() != null
                && role.getMaxCount() > 0
                && role.getCurrentCount() != null
                && role.getCurrentCount() >= role.getMaxCount();
    }

    private void notifyManagers(Project project, String title, String content, String type, String link) {
        Set<Long> userIds = new LinkedHashSet<>();
        if (project.getCreatorId() != null) {
            userIds.add(project.getCreatorId());
        }
        projectMemberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, project.getId())
                        .eq(ProjectMember::getMemberRole, PROJECT_MANAGER))
                .forEach(member -> userIds.add(member.getUserId()));
        for (Long userId : userIds) {
            notificationService.notifyUser(userId, project.getId(), title, content, type, link);
        }
    }

    private JoinApplyVO toVO(ProjectJoinApply apply) {
        Project project = projectMapper.selectById(apply.getProjectId());
        SysUser applicant = apply.getApplicantId() == null ? null : sysUserMapper.selectById(apply.getApplicantId());
        ProjectRole role = apply.getRequestedRoleId() == null ? null : projectRoleMapper.selectById(apply.getRequestedRoleId());
        SysUser reviewer = apply.getReviewUserId() == null ? null : sysUserMapper.selectById(apply.getReviewUserId());

        JoinApplyVO vo = new JoinApplyVO();
        vo.setId(apply.getId());
        vo.setProjectId(apply.getProjectId());
        vo.setProjectName(project == null ? null : project.getProjectName());
        vo.setApplicantId(apply.getApplicantId());
        vo.setApplicantUsername(applicant == null ? null : applicant.getUsername());
        vo.setApplicantName(apply.getApplicantName());
        vo.setApplicantEmail(apply.getApplicantEmail());
        vo.setRequestedRoleId(apply.getRequestedRoleId());
        vo.setRequestedRoleName(role == null ? null : role.getRoleName());
        vo.setApplyNote(apply.getApplyNote());
        vo.setStatus(apply.getStatus());
        vo.setReviewUserId(apply.getReviewUserId());
        vo.setReviewUserName(reviewer == null ? null : reviewer.getRealName());
        vo.setReviewComment(apply.getReviewComment());
        vo.setReviewTime(apply.getReviewTime());
        vo.setCreateTime(apply.getCreateTime());
        return vo;
    }

    private ProjectVO toProjectVO(Project project) {
        SysUser creator = project.getCreatorId() == null ? null : sysUserMapper.selectById(project.getCreatorId());
        ProjectVO vo = new ProjectVO();
        vo.setId(project.getId());
        vo.setTemplateId(project.getTemplateId());
        vo.setTemplateMode(project.getTemplateMode());
        vo.setProjectCode(project.getProjectCode());
        vo.setProjectName(project.getProjectName());
        vo.setDescription(project.getDescription());
        vo.setStatus(project.getStatus());
        vo.setJoinPolicy(project.getJoinPolicy());
        vo.setJoinLink(project.getJoinLink());
        vo.setArchivedFlag(project.getArchivedFlag());
        vo.setStartDate(project.getStartDate());
        vo.setEndDate(project.getEndDate());
        vo.setCreatorId(project.getCreatorId());
        vo.setCreatorName(creator == null ? null : creator.getRealName());
        vo.setCreateTime(project.getCreateTime());
        vo.setUpdateTime(project.getUpdateTime());
        return vo;
    }

    private ProjectRoleVO toRoleVO(ProjectRole role) {
        ProjectRoleVO vo = new ProjectRoleVO();
        vo.setId(role.getId());
        vo.setProjectId(role.getProjectId());
        vo.setRoleCode(role.getRoleCode());
        vo.setRoleName(role.getRoleName());
        vo.setResponsibility(role.getResponsibility());
        vo.setMaxCount(role.getMaxCount());
        vo.setCurrentCount(role.getCurrentCount());
        vo.setRemainingCount(remainingCount(role));
        vo.setRequiredFlag(role.getRequiredFlag());
        vo.setSortOrder(role.getSortOrder());
        vo.setCreateTime(role.getCreateTime());
        vo.setUpdateTime(role.getUpdateTime());
        return vo;
    }

    private Integer remainingCount(ProjectRole role) {
        if (role.getMaxCount() == null || role.getMaxCount() <= 0) {
            return null;
        }
        int currentCount = role.getCurrentCount() == null ? 0 : role.getCurrentCount();
        return Math.max(role.getMaxCount() - currentCount, 0);
    }
}

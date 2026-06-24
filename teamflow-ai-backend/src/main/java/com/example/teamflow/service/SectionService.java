package com.example.teamflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.teamflow.common.BizException;
import com.example.teamflow.dto.SectionCommentDTO;
import com.example.teamflow.dto.SectionContentDTO;
import com.example.teamflow.dto.SectionReviewDTO;
import com.example.teamflow.entity.Project;
import com.example.teamflow.entity.ProjectMember;
import com.example.teamflow.entity.ProjectRole;
import com.example.teamflow.entity.ProjectSection;
import com.example.teamflow.entity.RoleSectionPermission;
import com.example.teamflow.entity.SectionComment;
import com.example.teamflow.entity.SectionContent;
import com.example.teamflow.entity.SectionReview;
import com.example.teamflow.entity.SysUser;
import com.example.teamflow.mapper.ProjectMemberMapper;
import com.example.teamflow.mapper.ProjectRoleMapper;
import com.example.teamflow.mapper.ProjectSectionMapper;
import com.example.teamflow.mapper.RoleSectionPermissionMapper;
import com.example.teamflow.mapper.SectionCommentMapper;
import com.example.teamflow.mapper.SectionContentMapper;
import com.example.teamflow.mapper.SectionReviewMapper;
import com.example.teamflow.mapper.SysUserMapper;
import com.example.teamflow.vo.PermissionMatrixVO;
import com.example.teamflow.vo.SectionCommentVO;
import com.example.teamflow.vo.SectionContentVO;
import com.example.teamflow.vo.SectionReviewVO;
import com.example.teamflow.vo.SectionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SectionService {
    private static final String VIEW = "VIEW";
    private static final String EDIT = "EDIT";
    private static final String COMMENT = "COMMENT";
    private static final String REVIEW = "REVIEW";
    private static final String DRAFT = "DRAFT";
    private static final String REVIEWING = "REVIEWING";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";

    private final AuthService authService;
    private final ProjectService projectService;
    private final PermissionService permissionService;
    private final NotificationService notificationService;
    private final ProjectRoleMapper projectRoleMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectSectionMapper projectSectionMapper;
    private final RoleSectionPermissionMapper roleSectionPermissionMapper;
    private final SectionContentMapper sectionContentMapper;
    private final SectionCommentMapper sectionCommentMapper;
    private final SectionReviewMapper sectionReviewMapper;
    private final SysUserMapper sysUserMapper;

    public List<SectionVO> list(Long projectId) {
        projectService.getProject(projectId);
        Map<Long, ProjectRole> roleById = roleById(projectId);
        return projectSectionMapper.selectList(new LambdaQueryWrapper<ProjectSection>()
                        .eq(ProjectSection::getProjectId, projectId)
                        .orderByAsc(ProjectSection::getSortOrder)
                        .orderByAsc(ProjectSection::getId))
                .stream()
                .map(section -> toSectionVO(section, roleById, latestContent(section.getId())))
                .toList();
    }

    public SectionVO detail(Long sectionId) {
        permissionService.requireSectionPermission(sectionId, VIEW);
        ProjectSection section = findSection(sectionId);
        return toSectionVO(section, roleById(section.getProjectId()), latestContent(sectionId));
    }

    public PermissionMatrixVO permissionMatrix(Long projectId) {
        permissionService.requireProjectMember(projectId);
        List<ProjectRole> roles = projectRoleMapper.selectList(new LambdaQueryWrapper<ProjectRole>()
                .eq(ProjectRole::getProjectId, projectId)
                .orderByAsc(ProjectRole::getSortOrder)
                .orderByAsc(ProjectRole::getId));
        List<ProjectSection> sections = projectSectionMapper.selectList(new LambdaQueryWrapper<ProjectSection>()
                .eq(ProjectSection::getProjectId, projectId)
                .orderByAsc(ProjectSection::getSortOrder)
                .orderByAsc(ProjectSection::getId));
        List<RoleSectionPermission> permissions = roleSectionPermissionMapper.selectList(new LambdaQueryWrapper<RoleSectionPermission>()
                .eq(RoleSectionPermission::getProjectId, projectId)
                .orderByAsc(RoleSectionPermission::getProjectRoleId)
                .orderByAsc(RoleSectionPermission::getSectionId));

        PermissionMatrixVO vo = new PermissionMatrixVO();
        vo.setRoleRows(roles.stream().map(role -> {
            PermissionMatrixVO.RoleRow row = new PermissionMatrixVO.RoleRow();
            row.setRoleId(role.getId());
            row.setRoleCode(role.getRoleCode());
            row.setRoleName(role.getRoleName());
            return row;
        }).toList());
        vo.setSectionColumns(sections.stream().map(section -> {
            PermissionMatrixVO.SectionColumn column = new PermissionMatrixVO.SectionColumn();
            column.setSectionId(section.getId());
            column.setSectionCode(section.getSectionCode());
            column.setSectionName(section.getSectionName());
            return column;
        }).toList());
        vo.setPermissions(groupPermissionEntries(permissions).stream().map(permission -> {
            PermissionMatrixVO.PermissionEntry entry = new PermissionMatrixVO.PermissionEntry();
            entry.setRoleId(permission.roleId);
            entry.setSectionId(permission.sectionId);
            entry.setPermissionCodes(permission.permissionCodes);
            return entry;
        }).toList());
        return vo;
    }

    public List<String> mySectionPermissions(Long sectionId) {
        return permissionService.currentSectionPermissions(sectionId);
    }

    @Transactional
    public SectionContentVO saveDraft(Long sectionId, SectionContentDTO dto) {
        permissionService.requireSectionPermission(sectionId, EDIT);
        SysUser current = authService.currentUser();
        ProjectSection section = findSectionForUpdate(sectionId);
        SectionContent latest = latestContent(sectionId);
        // Allow APPROVED sections to save new draft
        if (REVIEWING.equalsIgnoreCase(section.getStatus())) {
            throw new BizException(400, "当前章节正在审核中，暂不可编辑");
        }
        if (latest != null && REVIEWING.equalsIgnoreCase(latest.getSubmitStatus())) {
            throw new BizException(400, "当前章节内容正在审核中，暂不可编辑");
        }
        LocalDateTime now = LocalDateTime.now();

        SectionContent content = new SectionContent();
        content.setSectionId(sectionId);
        content.setProjectId(section.getProjectId());
        content.setVersionNo(latest == null || latest.getVersionNo() == null ? 1 : latest.getVersionNo() + 1);
        content.setTitle(dto.getTitle());
        content.setBody(dto.getBody());
        content.setEditorId(current.getId());
        content.setSubmitStatus(DRAFT);
        content.setCreateTime(now);
        content.setUpdateTime(now);
        sectionContentMapper.insert(content);

        section.setStatus(DRAFT);
        section.setUpdateTime(now);
        projectSectionMapper.updateById(section);
        return toContentVO(content);
    }

    @Transactional
    public SectionContentVO submit(Long sectionId) {
        permissionService.requireSectionPermission(sectionId, EDIT);
        SysUser current = authService.currentUser();
        ProjectSection section = findSectionForUpdate(sectionId);
        SectionContent content = latestContent(sectionId);
        if (content == null) {
            throw new BizException(400, "请先保存章节草稿");
        }
        if (!DRAFT.equalsIgnoreCase(content.getSubmitStatus()) && !REJECTED.equalsIgnoreCase(content.getSubmitStatus())) {
            throw new BizException(400, "当前章节内容不可提交");
        }

        LocalDateTime now = LocalDateTime.now();
        content.setSubmitStatus(REVIEWING);
        content.setSubmitTime(now);
        content.setUpdateTime(now);
        sectionContentMapper.updateById(content);

        section.setStatus(REVIEWING);
        section.setUpdateTime(now);
        projectSectionMapper.updateById(section);

        notifyReviewers(section, current, "章节待审核",
                current.getRealName() + " 提交了章节：" + section.getSectionName(),
                "/projects/" + section.getProjectId() + "/sections/" + sectionId);
        return toContentVO(content);
    }

    @Transactional
    public SectionReviewVO review(Long sectionId, SectionReviewDTO dto) {
        permissionService.requireSectionPermission(sectionId, REVIEW);
        SysUser current = authService.currentUser();
        ProjectSection section = findSection(sectionId);
        SectionContent content = latestContent(sectionId);
        if (content == null || !REVIEWING.equalsIgnoreCase(content.getSubmitStatus())) {
            throw new BizException(400, "当前章节没有待审核内容");
        }
        String result = normalize(dto.getReviewResult());
        if (!APPROVED.equals(result) && !REJECTED.equals(result)) {
            throw new BizException(400, "审核结果必须为 APPROVED 或 REJECTED");
        }

        // Check if the current reviewer has already approved/rejected this content version to prevent duplicate reviews
        Long existingReviewCount = sectionReviewMapper.selectCount(new LambdaQueryWrapper<SectionReview>()
                .eq(SectionReview::getSectionId, sectionId)
                .eq(SectionReview::getContentId, content.getId())
                .eq(SectionReview::getReviewerId, current.getId()));
        if (existingReviewCount > 0) {
            throw new BizException(400, "您已对该版本提交过审核意见");
        }

        LocalDateTime now = LocalDateTime.now();

        // Insert SectionReview first
        SectionReview review = new SectionReview();
        review.setSectionId(sectionId);
        review.setContentId(content.getId());
        review.setReviewerId(current.getId());
        review.setReviewResult(result);
        review.setReviewComment(dto.getReviewComment());
        review.setCreateTime(now);
        review.setUpdateTime(now);
        sectionReviewMapper.insert(review);

        // Compute the final status for the content and section
        String finalStatus;
        if (REJECTED.equalsIgnoreCase(result)) {
            finalStatus = REJECTED;
        } else {
            // Get all role ids that have REVIEW permission on this section
            List<Long> reviewerRoleIds = roleSectionPermissionMapper.selectList(new LambdaQueryWrapper<RoleSectionPermission>()
                            .eq(RoleSectionPermission::getSectionId, sectionId)
                            .eq(RoleSectionPermission::getPermissionCode, REVIEW))
                    .stream()
                    .map(RoleSectionPermission::getProjectRoleId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

            List<ProjectMember> reviewers = reviewerRoleIds.isEmpty() ? List.of() :
                    projectMemberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                            .eq(ProjectMember::getProjectId, section.getProjectId())
                            .in(ProjectMember::getProjectRoleId, reviewerRoleIds));

            Long editorId = content.getEditorId();
            List<ProjectMember> requiredReviewers = new ArrayList<>();
            for (ProjectMember m : reviewers) {
                if (!Objects.equals(m.getUserId(), editorId)) {
                    requiredReviewers.add(m);
                }
            }

            // Get all APPROVED reviews for this content version
            List<SectionReview> approvedReviews = sectionReviewMapper.selectList(new LambdaQueryWrapper<SectionReview>()
                    .eq(SectionReview::getSectionId, sectionId)
                    .eq(SectionReview::getContentId, content.getId())
                    .eq(SectionReview::getReviewResult, APPROVED));

            Set<Long> approvedReviewerIds = approvedReviews.stream()
                    .map(SectionReview::getReviewerId)
                    .collect(Collectors.toSet());

            int requiredCount = requiredReviewers.size();
            int approvedCount = 0;
            for (ProjectMember m : requiredReviewers) {
                if (approvedReviewerIds.contains(m.getUserId())) {
                    approvedCount++;
                }
            }

            if (requiredCount > 0 && approvedCount >= requiredCount) {
                finalStatus = APPROVED;
            } else {
                finalStatus = REVIEWING;
            }
        }

        // Update the SectionContent and ProjectSection status to finalStatus
        SectionContent reviewedContent = new SectionContent();
        reviewedContent.setSubmitStatus(finalStatus);
        reviewedContent.setUpdateTime(now);
        sectionContentMapper.update(reviewedContent, new LambdaUpdateWrapper<SectionContent>()
                .eq(SectionContent::getId, content.getId()));

        section.setStatus(finalStatus);
        section.setUpdateTime(now);
        projectSectionMapper.updateById(section);

        if (APPROVED.equalsIgnoreCase(finalStatus)) {
            notifySubsequentMembers(section);
        }

        content.setSubmitStatus(finalStatus);
        content.setUpdateTime(now);

        if (content.getEditorId() != null && !Objects.equals(content.getEditorId(), current.getId())) {
            notificationService.notifyUser(content.getEditorId(), section.getProjectId(), "章节审核结果",
                    section.getSectionName() + " 审核结果：" + finalStatus,
                    "SECTION_REVIEW", "/projects/" + section.getProjectId() + "/sections/" + sectionId);
        }
        return toReviewVO(review);
    }

    @Transactional
    public SectionCommentVO comment(Long sectionId, SectionCommentDTO dto) {
        permissionService.requireSectionPermission(sectionId, COMMENT);
        SysUser current = authService.currentUser();
        ProjectSection section = findSection(sectionId);
        SectionContent content = dto.getContentId() == null ? null : sectionContentMapper.selectById(dto.getContentId());
        if (content == null || !Objects.equals(sectionId, content.getSectionId())) {
            throw new BizException(404, "章节内容不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        SectionComment comment = new SectionComment();
        comment.setSectionId(sectionId);
        comment.setContentId(content.getId());
        comment.setUserId(current.getId());
        comment.setCommentText(dto.getCommentText());
        comment.setResolvedFlag(0);
        comment.setCreateTime(now);
        comment.setUpdateTime(now);
        sectionCommentMapper.insert(comment);

        if (content.getEditorId() != null && !Objects.equals(content.getEditorId(), current.getId())) {
            notificationService.notifyUser(content.getEditorId(), section.getProjectId(), "章节收到评论",
                    section.getSectionName() + " 收到新的评论。",
                    "SECTION_COMMENT", "/projects/" + section.getProjectId() + "/sections/" + sectionId);
        }
        return toCommentVO(comment);
    }

    public List<SectionContentVO> versions(Long sectionId) {
        permissionService.requireSectionPermission(sectionId, VIEW);
        return sectionContentMapper.selectList(new LambdaQueryWrapper<SectionContent>()
                        .eq(SectionContent::getSectionId, sectionId)
                        .orderByDesc(SectionContent::getVersionNo)
                        .orderByDesc(SectionContent::getCreateTime))
                .stream()
                .map(this::toContentVO)
                .toList();
    }

    private ProjectSection findSection(Long sectionId) {
        ProjectSection section = sectionId == null ? null : projectSectionMapper.selectById(sectionId);
        if (section == null) {
            throw new BizException(404, "板块不存在");
        }
        return section;
    }

    private ProjectSection findSectionForUpdate(Long sectionId) {
        ProjectSection section = sectionId == null ? null : projectSectionMapper.selectOne(new LambdaQueryWrapper<ProjectSection>()
                .eq(ProjectSection::getId, sectionId)
                .last("for update"));
        if (section == null) {
            throw new BizException(404, "板块不存在");
        }
        return section;
    }

    private SectionContent latestContent(Long sectionId) {
        return sectionContentMapper.selectOne(new LambdaQueryWrapper<SectionContent>()
                .eq(SectionContent::getSectionId, sectionId)
                .orderByDesc(SectionContent::getVersionNo)
                .orderByDesc(SectionContent::getCreateTime)
                .last("limit 1"));
    }

    private Map<Long, ProjectRole> roleById(Long projectId) {
        return projectRoleMapper.selectList(new LambdaQueryWrapper<ProjectRole>()
                        .eq(ProjectRole::getProjectId, projectId))
                .stream()
                .collect(Collectors.toMap(ProjectRole::getId, Function.identity(), (left, right) -> left));
    }

    private void notifyReviewers(ProjectSection section, SysUser editor, String title, String content, String link) {
        Set<Long> userIds = new LinkedHashSet<>();
        List<Long> reviewerRoleIds = roleSectionPermissionMapper.selectList(new LambdaQueryWrapper<RoleSectionPermission>()
                        .eq(RoleSectionPermission::getSectionId, section.getId())
                        .eq(RoleSectionPermission::getPermissionCode, REVIEW))
                .stream()
                .map(RoleSectionPermission::getProjectRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!reviewerRoleIds.isEmpty()) {
            projectMemberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                            .eq(ProjectMember::getProjectId, section.getProjectId())
                            .in(ProjectMember::getProjectRoleId, reviewerRoleIds))
                    .forEach(member -> userIds.add(member.getUserId()));
        }
        if (userIds.isEmpty()) {
            Project project = projectService.getProject(section.getProjectId());
            if (project.getCreatorId() != null) {
                userIds.add(project.getCreatorId());
            }
        }
        userIds.remove(editor.getId());
        for (Long userId : userIds) {
            notificationService.notifyUser(userId, section.getProjectId(), title, content, "SECTION_REVIEW", link);
        }
    }

    private SectionVO toSectionVO(ProjectSection section, Map<Long, ProjectRole> roleById, SectionContent latestContent) {
        ProjectRole ownerRole = section.getOwnerRoleId() == null ? null : roleById.get(section.getOwnerRoleId());
        SectionVO vo = new SectionVO();
        vo.setId(section.getId());
        vo.setProjectId(section.getProjectId());
        vo.setSectionCode(section.getSectionCode());
        vo.setSectionName(section.getSectionName());
        vo.setDescription(section.getDescription());
        vo.setRequiredFlag(section.getRequiredFlag());
        vo.setOwnerRoleId(section.getOwnerRoleId());
        vo.setOwnerRoleName(ownerRole == null ? null : ownerRole.getRoleName());
        vo.setStatus(section.getStatus());
        vo.setSortOrder(section.getSortOrder());
        vo.setLatestContent(latestContent == null ? null : toContentVO(latestContent));
        vo.setMissing(latestContent == null || "EMPTY".equalsIgnoreCase(section.getStatus()));
        vo.setAllApproved(isAllApproved(section.getId(), latestContent, section.getProjectId(), section.getStatus()));
        vo.setCreateTime(section.getCreateTime());
        vo.setUpdateTime(section.getUpdateTime());
        return vo;
    }

    public boolean isAllApproved(Long sectionId, SectionContent latest, Long projectId, String status) {
        if (latest == null) return false;

        List<Long> reviewerRoleIds = roleSectionPermissionMapper.selectList(new LambdaQueryWrapper<RoleSectionPermission>()
                        .eq(RoleSectionPermission::getSectionId, sectionId)
                        .eq(RoleSectionPermission::getPermissionCode, REVIEW))
                .stream()
                .map(RoleSectionPermission::getProjectRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (reviewerRoleIds.isEmpty()) {
            return APPROVED.equalsIgnoreCase(status);
        }

        List<ProjectMember> reviewers = projectMemberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .in(ProjectMember::getProjectRoleId, reviewerRoleIds));

        if (reviewers.isEmpty()) {
            return APPROVED.equalsIgnoreCase(status);
        }

        List<SectionReview> approvedReviews = sectionReviewMapper.selectList(new LambdaQueryWrapper<SectionReview>()
                .eq(SectionReview::getSectionId, sectionId)
                .eq(SectionReview::getContentId, latest.getId())
                .eq(SectionReview::getReviewResult, APPROVED));

        Set<Long> approvedReviewerIds = approvedReviews.stream()
                .map(SectionReview::getReviewerId)
                .collect(Collectors.toSet());

        Long editorId = latest.getEditorId();
        int reviewerCountToCheck = 0;
        int approvalCount = 0;
        for (ProjectMember reviewer : reviewers) {
            if (Objects.equals(reviewer.getUserId(), editorId)) {
                continue;
            }
            reviewerCountToCheck++;
            if (approvedReviewerIds.contains(reviewer.getUserId())) {
                approvalCount++;
            }
        }
        return reviewerCountToCheck > 0 ? (approvalCount == reviewerCountToCheck) : APPROVED.equalsIgnoreCase(status);
    }


    private SectionContentVO toContentVO(SectionContent content) {
        SysUser editor = content.getEditorId() == null ? null : sysUserMapper.selectById(content.getEditorId());
        SectionContentVO vo = new SectionContentVO();
        vo.setId(content.getId());
        vo.setSectionId(content.getSectionId());
        vo.setProjectId(content.getProjectId());
        vo.setVersionNo(content.getVersionNo());
        vo.setTitle(content.getTitle());
        vo.setBody(content.getBody());
        vo.setEditorId(content.getEditorId());
        vo.setEditorName(editor == null ? null : editor.getRealName());
        vo.setSubmitStatus(content.getSubmitStatus());
        vo.setSubmitTime(content.getSubmitTime());
        vo.setCreateTime(content.getCreateTime());
        vo.setUpdateTime(content.getUpdateTime());
        return vo;
    }

    private SectionCommentVO toCommentVO(SectionComment comment) {
        SysUser user = comment.getUserId() == null ? null : sysUserMapper.selectById(comment.getUserId());
        SectionCommentVO vo = new SectionCommentVO();
        vo.setId(comment.getId());
        vo.setSectionId(comment.getSectionId());
        vo.setContentId(comment.getContentId());
        vo.setUserId(comment.getUserId());
        vo.setUsername(user == null ? null : user.getUsername());
        vo.setRealName(user == null ? null : user.getRealName());
        vo.setCommentText(comment.getCommentText());
        vo.setResolvedFlag(comment.getResolvedFlag());
        vo.setCreateTime(comment.getCreateTime());
        vo.setUpdateTime(comment.getUpdateTime());
        return vo;
    }

    private SectionReviewVO toReviewVO(SectionReview review) {
        SysUser reviewer = review.getReviewerId() == null ? null : sysUserMapper.selectById(review.getReviewerId());
        SectionReviewVO vo = new SectionReviewVO();
        vo.setId(review.getId());
        vo.setSectionId(review.getSectionId());
        vo.setContentId(review.getContentId());
        vo.setReviewerId(review.getReviewerId());
        vo.setReviewerName(reviewer == null ? null : reviewer.getRealName());
        vo.setReviewResult(review.getReviewResult());
        vo.setReviewComment(review.getReviewComment());
        vo.setCreateTime(review.getCreateTime());
        vo.setUpdateTime(review.getUpdateTime());
        return vo;
    }

    public List<SectionCommentVO> listComments(Long sectionId) {
        permissionService.requireSectionPermission(sectionId, VIEW);
        List<SectionComment> comments = sectionCommentMapper.selectList(new LambdaQueryWrapper<SectionComment>()
                .eq(SectionComment::getSectionId, sectionId)
                .orderByDesc(SectionComment::getCreateTime));
        return comments.stream().map(this::toCommentVO).toList();
    }

    public List<SectionReviewVO> listReviews(Long sectionId) {
        permissionService.requireSectionPermission(sectionId, VIEW);
        List<SectionReview> reviews = sectionReviewMapper.selectList(new LambdaQueryWrapper<SectionReview>()
                .eq(SectionReview::getSectionId, sectionId)
                .orderByDesc(SectionReview::getCreateTime));
        return reviews.stream().map(this::toReviewVO).toList();
    }

    public List<SectionVO> listMyReviews(Long projectId) {
        SysUser current = authService.currentUser();
        projectService.getProject(projectId);
        
        ProjectMember member = projectMemberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, current.getId())
                .last("limit 1"));
        if (member == null || member.getProjectRoleId() == null) {
            return List.of();
        }
        
        List<Long> sectionIdsWithReviewPermission = roleSectionPermissionMapper.selectList(new LambdaQueryWrapper<RoleSectionPermission>()
                .eq(RoleSectionPermission::getProjectId, projectId)
                .eq(RoleSectionPermission::getProjectRoleId, member.getProjectRoleId())
                .eq(RoleSectionPermission::getPermissionCode, REVIEW))
                .stream()
                .map(RoleSectionPermission::getSectionId)
                .toList();
        
        if (sectionIdsWithReviewPermission.isEmpty()) {
            return List.of();
        }
        
        List<ProjectSection> sections = projectSectionMapper.selectList(new LambdaQueryWrapper<ProjectSection>()
                .eq(ProjectSection::getProjectId, projectId)
                .in(ProjectSection::getId, sectionIdsWithReviewPermission)
                .orderByAsc(ProjectSection::getSortOrder));
                
        Map<Long, ProjectRole> roleById = roleById(projectId);
        
        return sections.stream()
                .filter(s -> List.of(REVIEWING, APPROVED, REJECTED).contains(s.getStatus()))
                .map(s -> toSectionVO(s, roleById, latestContent(s.getId())))
                .sorted((a, b) -> {
                    boolean aReviewing = REVIEWING.equalsIgnoreCase(a.getStatus());
                    boolean bReviewing = REVIEWING.equalsIgnoreCase(b.getStatus());
                    if (aReviewing && !bReviewing) return -1;
                    if (!aReviewing && bReviewing) return 1;
                    
                    LocalDateTime aTime = a.getUpdateTime() != null ? a.getUpdateTime() : LocalDateTime.MIN;
                    LocalDateTime bTime = b.getUpdateTime() != null ? b.getUpdateTime() : LocalDateTime.MIN;
                    return bTime.compareTo(aTime);
                })
                .toList();
    }

    private void notifySubsequentMembers(ProjectSection approvedSection) {
        if (approvedSection == null || approvedSection.getProjectId() == null) {
            return;
        }
        String sectionCode = approvedSection.getSectionCode();
        if (!StringUtils.hasText(sectionCode)) {
            return;
        }

        List<String> targetRoles = new ArrayList<>();
        String msgContent = "";
        boolean notifyAll = false;

        switch (sectionCode.toUpperCase()) {
            case "MANAGEMENT_DELIVERY":
                notifyAll = true;
                msgContent = "项目管理章节已审批通过，后续章节开发人员可以开始进行对应的工作了。";
                break;
            case "REQUIREMENT":
            case "USE_CASE_ANALYSIS":
                targetRoles.add("TECHNICAL_DIRECTOR");
                msgContent = approvedSection.getSectionName() + " 章节已审批通过，请技术总监进行后续的设计工作。";
                break;
            case "TECH_FRAMEWORK":
                targetRoles.add("FRONTEND_DEV");
                targetRoles.add("BACKEND_DEV");
                msgContent = "技术框架设计章节已审批通过，开发人员可以开始对应的开发准备工作了。";
                break;
            case "PROTOTYPE":
                targetRoles.add("FRONTEND_DEV");
                msgContent = "原型图设计章节已审批通过，您可以开始前端实现编码了。";
                break;
            case "API":
                targetRoles.add("FRONTEND_DEV");
                targetRoles.add("BACKEND_DEV");
                msgContent = "接口文档章节已审批通过，可以开始进行接口联调和对接开发了。";
                break;
            case "DATABASE":
            case "USE_CASE":
                targetRoles.add("BACKEND_DEV");
                msgContent = approvedSection.getSectionName() + " 章节已审批通过，您可以开始后端开发编码了。";
                break;
            case "FRONTEND":
            case "BACKEND":
                targetRoles.add("QA");
                msgContent = approvedSection.getSectionName() + " 章节已审批通过，测试负责人可以开始准备测试用例验证了。";
                break;
            default:
                break;
        }

        if (!notifyAll && targetRoles.isEmpty()) {
            return;
        }

        List<ProjectMember> members;
        if (notifyAll) {
            members = projectMemberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                    .eq(ProjectMember::getProjectId, approvedSection.getProjectId()));
        } else {
            members = projectMemberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                    .eq(ProjectMember::getProjectId, approvedSection.getProjectId())
                    .in(ProjectMember::getMemberRole, targetRoles));
        }

        for (ProjectMember member : members) {
            if (member.getUserId() != null) {
                notificationService.notifyUser(
                        member.getUserId(),
                        approvedSection.getProjectId(),
                        "后续阶段提醒",
                        msgContent,
                        "SECTION_REVIEW",
                        "/projects/" + approvedSection.getProjectId() + "/sections/" + approvedSection.getId()
                );
            }
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private List<PermissionCell> groupPermissionEntries(List<RoleSectionPermission> permissions) {
        Map<String, PermissionCell> cells = new LinkedHashMap<>();
        for (RoleSectionPermission permission : permissions) {
            String key = permission.getProjectRoleId() + ":" + permission.getSectionId();
            PermissionCell cell = cells.computeIfAbsent(key, ignored -> {
                PermissionCell created = new PermissionCell();
                created.roleId = permission.getProjectRoleId();
                created.sectionId = permission.getSectionId();
                created.permissionCodes = new ArrayList<>();
                return created;
            });
            if (StringUtils.hasText(permission.getPermissionCode())) {
                cell.permissionCodes.add(permission.getPermissionCode());
            }
        }
        return new ArrayList<>(cells.values());
    }

    public void updateSectionStatus(Long projectId, String sectionCode, String status) {
        ProjectSection section = projectSectionMapper.selectOne(new LambdaQueryWrapper<ProjectSection>()
                .eq(ProjectSection::getProjectId, projectId)
                .eq(ProjectSection::getSectionCode, normalize(sectionCode))
                .last("limit 1"));
        if (section == null) {
            return;
        }
        section.setStatus(status);
        section.setUpdateTime(LocalDateTime.now());
        projectSectionMapper.updateById(section);

        SectionContent content = sectionContentMapper.selectOne(new LambdaQueryWrapper<SectionContent>()
                .eq(SectionContent::getSectionId, section.getId())
                .orderByDesc(SectionContent::getVersionNo)
                .last("limit 1"));
        if (content != null) {
            content.setSubmitStatus(status);
            content.setUpdateTime(LocalDateTime.now());
            sectionContentMapper.updateById(content);
        } else {
            content = new SectionContent();
            content.setSectionId(section.getId());
            content.setProjectId(projectId);
            content.setVersionNo(1);
            content.setTitle(section.getSectionName());
            content.setBody("由 GitHub 同步自动维护");
            content.setEditorId(1L);
            content.setSubmitStatus(status);
            content.setCreateTime(LocalDateTime.now());
            content.setUpdateTime(LocalDateTime.now());
            sectionContentMapper.insert(content);
        }

        if ("APPROVED".equalsIgnoreCase(status)) {
            notifySubsequentMembers(section);
        }
    }

    private static class PermissionCell {
        private Long roleId;
        private Long sectionId;
        private List<String> permissionCodes;
    }
}

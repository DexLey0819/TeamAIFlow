package com.example.teamflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.teamflow.common.BizException;
import com.example.teamflow.dto.SectionReviewDTO;
import com.example.teamflow.entity.ProjectSection;
import com.example.teamflow.entity.RoleSectionPermission;
import com.example.teamflow.entity.ProjectMember;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SectionServiceNotificationTest {

    @Mock
    private AuthService authService;
    @Mock
    private ProjectService projectService;
    @Mock
    private PermissionService permissionService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ProjectRoleMapper projectRoleMapper;
    @Mock
    private ProjectMemberMapper projectMemberMapper;
    @Mock
    private ProjectSectionMapper projectSectionMapper;
    @Mock
    private RoleSectionPermissionMapper roleSectionPermissionMapper;
    @Mock
    private SectionContentMapper sectionContentMapper;
    @Mock
    private SectionCommentMapper sectionCommentMapper;
    @Mock
    private SectionReviewMapper sectionReviewMapper;
    @Mock
    private SysUserMapper sysUserMapper;

    @InjectMocks
    private SectionService sectionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testManagementDeliveryApprovalNotifiesAllMembers() {
        Long sectionId = 100L;
        Long projectId = 1L;

        // Mock current user
        SysUser current = new SysUser();
        current.setId(10L);
        when(authService.currentUser()).thenReturn(current);

        // Mock Section
        ProjectSection section = new ProjectSection();
        section.setId(sectionId);
        section.setProjectId(projectId);
        section.setSectionCode("MANAGEMENT_DELIVERY");
        section.setSectionName("项目管理");
        when(projectSectionMapper.selectById(sectionId)).thenReturn(section);

        // Mock Content
        SectionContent content = new SectionContent();
        content.setId(200L);
        content.setSectionId(sectionId);
        content.setProjectId(projectId);
        content.setSubmitStatus("REVIEWING");
        content.setEditorId(12L);
        when(sectionContentMapper.selectOne(any())).thenReturn(content);

        // Mock Review Mapper
        when(sectionReviewMapper.selectCount(any())).thenReturn(0L);

        // Mock reviewer role ids (Role 5)
        RoleSectionPermission rsp = new RoleSectionPermission();
        rsp.setProjectRoleId(5L);
        when(roleSectionPermissionMapper.selectList(any())).thenReturn(List.of(rsp));

        // Mock Project Member (User 11 is the reviewer)
        ProjectMember reviewerMember = new ProjectMember();
        reviewerMember.setUserId(11L);
        reviewerMember.setProjectRoleId(5L);
        when(projectMemberMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(reviewerMember)) // First call for required reviewers
                .thenReturn(List.of( // Second call for notifyAll members
                        member(1L, "PROJECT_MANAGER"),
                        member(2L, "PRODUCT_MANAGER"),
                        member(3L, "FRONTEND_DEV")
                ));

        // Mock approved reviews
        SectionReview approvedReview = new SectionReview();
        approvedReview.setReviewerId(11L);
        approvedReview.setReviewResult("APPROVED");
        when(sectionReviewMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(approvedReview));

        // Review DTO
        SectionReviewDTO dto = new SectionReviewDTO();
        dto.setReviewResult("APPROVED");
        dto.setReviewComment("Approved management delivery");

        sectionService.review(sectionId, dto);

        // Verify notifications sent to all 3 members
        verify(notificationService, times(3)).notifyUser(
                anyLong(),
                eq(projectId),
                eq("后续阶段提醒"),
                contains("项目管理章节已审批通过"),
                eq("SECTION_REVIEW"),
                anyString()
        );
    }

    @Test
    void testPrototypeApprovalNotifiesFrontendDevs() {
        Long sectionId = 101L;
        Long projectId = 1L;

        // Mock current user
        SysUser current = new SysUser();
        current.setId(10L);
        when(authService.currentUser()).thenReturn(current);

        // Mock Section
        ProjectSection section = new ProjectSection();
        section.setId(sectionId);
        section.setProjectId(projectId);
        section.setSectionCode("PROTOTYPE");
        section.setSectionName("原型图设计");
        when(projectSectionMapper.selectById(sectionId)).thenReturn(section);

        // Mock Content
        SectionContent content = new SectionContent();
        content.setId(200L);
        content.setSectionId(sectionId);
        content.setProjectId(projectId);
        content.setSubmitStatus("REVIEWING");
        content.setEditorId(12L);
        when(sectionContentMapper.selectOne(any())).thenReturn(content);

        // Mock Review Mapper
        when(sectionReviewMapper.selectCount(any())).thenReturn(0L);

        // Mock reviewer role ids (Role 5)
        RoleSectionPermission rsp = new RoleSectionPermission();
        rsp.setProjectRoleId(5L);
        when(roleSectionPermissionMapper.selectList(any())).thenReturn(List.of(rsp));

        // Mock Project Member (User 11 is reviewer)
        ProjectMember reviewerMember = new ProjectMember();
        reviewerMember.setUserId(11L);
        reviewerMember.setProjectRoleId(5L);
        when(projectMemberMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(reviewerMember)) // First call
                .thenReturn(List.of(member(3L, "FRONTEND_DEV"))); // Second call (subsequent notification query)

        // Mock approved reviews
        SectionReview approvedReview = new SectionReview();
        approvedReview.setReviewerId(11L);
        approvedReview.setReviewResult("APPROVED");
        when(sectionReviewMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(approvedReview));

        // Review DTO
        SectionReviewDTO dto = new SectionReviewDTO();
        dto.setReviewResult("APPROVED");
        dto.setReviewComment("Approved prototype");

        sectionService.review(sectionId, dto);

        // Verify notification sent to Frontend Dev (User 3)
        verify(notificationService, times(1)).notifyUser(
                eq(3L),
                eq(projectId),
                eq("后续阶段提醒"),
                contains("原型图设计章节已审批通过，您可以开始前端实现编码了。"),
                eq("SECTION_REVIEW"),
                anyString()
        );
    }

    private static ProjectMember member(Long userId, String roleCode) {
        ProjectMember m = new ProjectMember();
        m.setUserId(userId);
        m.setMemberRole(roleCode);
        return m;
    }
}

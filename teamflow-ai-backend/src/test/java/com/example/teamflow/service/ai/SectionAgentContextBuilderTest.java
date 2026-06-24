package com.example.teamflow.service.ai;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.teamflow.entity.Project;
import com.example.teamflow.entity.ProjectFile;
import com.example.teamflow.entity.ProjectMember;
import com.example.teamflow.entity.ProjectRole;
import com.example.teamflow.entity.ProjectSection;
import com.example.teamflow.entity.RiskSnapshot;
import com.example.teamflow.entity.SectionComment;
import com.example.teamflow.entity.SectionContent;
import com.example.teamflow.entity.SectionReview;
import com.example.teamflow.entity.SysUser;
import com.example.teamflow.entity.Task;
import com.example.teamflow.mapper.ProjectFileMapper;
import com.example.teamflow.mapper.ProjectMemberMapper;
import com.example.teamflow.mapper.ProjectRoleMapper;
import com.example.teamflow.mapper.ProjectSectionMapper;
import com.example.teamflow.mapper.RiskSnapshotMapper;
import com.example.teamflow.mapper.SectionCommentMapper;
import com.example.teamflow.mapper.SectionContentMapper;
import com.example.teamflow.mapper.SectionReviewMapper;
import com.example.teamflow.mapper.SysUserMapper;
import com.example.teamflow.mapper.TaskMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SectionAgentContextBuilderTest {
    private static final Long PROJECT_ID = 31L;
    private static final Long SECTION_ID = 320L;
    private static final List<Class<?>> QUERY_ENTITIES = List.of(
            ProjectSection.class,
            SectionContent.class,
            ProjectMember.class,
            ProjectRole.class,
            SysUser.class,
            Task.class,
            RiskSnapshot.class,
            ProjectFile.class,
            SectionComment.class,
            SectionReview.class
    );

    private ProjectSectionMapper projectSectionMapper;
    private SectionContentMapper sectionContentMapper;
    private ProjectMemberMapper projectMemberMapper;
    private ProjectRoleMapper projectRoleMapper;
    private SysUserMapper sysUserMapper;
    private TaskMapper taskMapper;
    private RiskSnapshotMapper riskSnapshotMapper;
    private ProjectFileMapper projectFileMapper;
    private SectionCommentMapper sectionCommentMapper;
    private SectionReviewMapper sectionReviewMapper;
    private SectionAgentContextBuilder builder;

    @BeforeAll
    static void initializeMybatisTableMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        for (Class<?> entityClass : QUERY_ENTITIES) {
            if (TableInfoHelper.getTableInfo(entityClass) == null) {
                TableInfoHelper.initTableInfo(
                        new MapperBuilderAssistant(configuration, "context-builder-" + entityClass.getSimpleName()),
                        entityClass
                );
            }
        }
    }

    @BeforeEach
    void setUp() {
        projectSectionMapper = mock(ProjectSectionMapper.class);
        sectionContentMapper = mock(SectionContentMapper.class);
        projectMemberMapper = mock(ProjectMemberMapper.class);
        projectRoleMapper = mock(ProjectRoleMapper.class);
        sysUserMapper = mock(SysUserMapper.class);
        taskMapper = mock(TaskMapper.class);
        riskSnapshotMapper = mock(RiskSnapshotMapper.class);
        projectFileMapper = mock(ProjectFileMapper.class);
        sectionCommentMapper = mock(SectionCommentMapper.class);
        sectionReviewMapper = mock(SectionReviewMapper.class);
        builder = new SectionAgentContextBuilder(
                projectSectionMapper,
                sectionContentMapper,
                projectMemberMapper,
                projectRoleMapper,
                sysUserMapper,
                taskMapper,
                riskSnapshotMapper,
                projectFileMapper,
                sectionCommentMapper,
                sectionReviewMapper
        );
    }

    @Test
    void selectedOverviewAndTasksRenderOnlyTheirHeadingsAndSkipUnrelatedMappers() {
        Task task = task(1L, "准备发布", "TODO", "HIGH", "等待验收");
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(task));

        String result = builder.build(project(), section(), profile("TASKS", "PROJECT_OVERVIEW"));

        assertAll(
                () -> assertTrue(result.contains("【项目概述】")),
                () -> assertTrue(result.contains("TeamFlow 上线")),
                () -> assertTrue(result.contains("【任务】")),
                () -> assertTrue(result.contains("准备发布")),
                () -> assertFalse(result.contains("【当前章节】")),
                () -> assertFalse(result.contains("【成员】")),
                () -> assertFalse(result.contains("【风险】")),
                () -> assertFalse(result.contains("【文件】")),
                () -> assertFalse(result.contains("【评论】")),
                () -> assertFalse(result.contains("【审核历史】"))
        );
        verify(taskMapper).selectList(any(LambdaQueryWrapper.class));
        verifyNoInteractions(
                projectSectionMapper,
                sectionContentMapper,
                projectMemberMapper,
                projectRoleMapper,
                sysUserMapper,
                riskSnapshotMapper,
                projectFileMapper,
                sectionCommentMapper,
                sectionReviewMapper
        );
    }

    @Test
    void everyCategoryRendersExactHeadingAndAllowedFields() {
        ProjectSection previous = section(300L, 10, "REQ", "需求", "已确认", "DONE");
        SectionContent content = content(9L, 300L, 2, "需求基线", "范围和验收标准");
        ProjectMember member = member(6L, 41L, 51L, "OWNER", "MEMBER_TITLE_SENTINEL");
        ProjectRole role = role(51L, "后端开发");
        SysUser user = user(41L, "dex", "Dex Display Name");
        Task task = task(7L, "完成接口", "DOING", "HIGH", "依赖网关");
        RiskSnapshot risk = risk(8L, "HIGH", "87.5", "UP", "延期风险上升", LocalDateTime.of(2026, 6, 24, 10, 0));
        ProjectFile file = file(9L, "api.md", "text/markdown", "DESIGN", "接口设计");
        SectionComment comment = comment(10L, "请补充错误码", 0);
        SectionReview review = review(11L, "REJECTED", "需补充测试证据");
        when(projectSectionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(previous));
        when(sectionContentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(content));
        when(projectMemberMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(member));
        when(projectRoleMapper.selectBatchIds(any())).thenReturn(List.of(role));
        when(sysUserMapper.selectBatchIds(any())).thenReturn(List.of(user));
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(task));
        when(riskSnapshotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(risk));
        when(projectFileMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(file));
        when(sectionCommentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(comment));
        when(sectionReviewMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(review));

        String result = builder.build(project(), section(), profile(
                "REVIEW_HISTORY", "COMMENTS", "FILES", "RISKS", "TASKS",
                "MEMBERS", "PREVIOUS_SECTIONS", "CURRENT_SECTION", "PROJECT_OVERVIEW"
        ));

        assertAll(
                () -> assertContainsInOrder(result,
                        "【项目概述】", "【当前章节】", "【前置章节】", "【成员】", "【任务】",
                        "【风险】", "【文件】", "【评论】", "【审核历史】"),
                () -> assertTrue(result.contains("TeamFlow 上线")),
                () -> assertTrue(result.contains("发布协作平台")),
                () -> assertTrue(result.contains("ACTIVE")),
                () -> assertTrue(result.contains("2026-06-01")),
                () -> assertTrue(result.contains("2026-07-31")),
                () -> assertTrue(result.contains("DEV")),
                () -> assertTrue(result.contains("开发实现")),
                () -> assertTrue(result.contains("实现核心功能")),
                () -> assertTrue(result.contains("DOING")),
                () -> assertTrue(result.contains("需求基线")),
                () -> assertTrue(result.contains("范围和验收标准")),
                () -> assertTrue(result.contains("OWNER")),
                () -> assertTrue(result.contains("后端开发")),
                () -> assertTrue(result.contains("Dex Display Name")),
                () -> assertFalse(result.contains("MEMBER_TITLE_SENTINEL")),
                () -> assertTrue(result.contains("完成接口")),
                () -> assertTrue(result.contains("依赖网关")),
                () -> assertTrue(result.contains("87.5")),
                () -> assertTrue(result.contains("延期风险上升")),
                () -> assertTrue(result.contains("api.md")),
                () -> assertTrue(result.contains("text/markdown")),
                () -> assertTrue(result.contains("DESIGN")),
                () -> assertTrue(result.contains("接口设计")),
                () -> assertTrue(result.contains("请补充错误码")),
                () -> assertTrue(result.contains("未解决")),
                () -> assertTrue(result.contains("REJECTED")),
                () -> assertTrue(result.contains("需补充测试证据"))
        );
    }

    @Test
    void excludedCategoryNeverCallsItsMapper() {
        String result = builder.build(project(), section(), profile("CURRENT_SECTION"));

        assertTrue(result.contains("【当前章节】"));
        verify(taskMapper, never()).selectList(any(LambdaQueryWrapper.class));
        verifyNoInteractions(
                projectSectionMapper,
                sectionContentMapper,
                projectMemberMapper,
                projectRoleMapper,
                sysUserMapper,
                riskSnapshotMapper,
                projectFileMapper,
                sectionCommentMapper,
                sectionReviewMapper
        );
    }

    @Test
    void categoriesAlwaysUseCanonicalOrderRegardlessOfScopeOrder() {
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(projectFileMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        String result = builder.build(project(), section(), profile(
                "FILES", "CURRENT_SECTION", "TASKS", "PROJECT_OVERVIEW"
        ));

        assertContainsInOrder(result, "【项目概述】", "【当前章节】", "【任务】", "【文件】");
    }

    @Test
    void rendersAtMostTwentyItemsWhenMapperReturnsFifty() {
        List<Task> tasks = new ArrayList<>();
        for (int index = 1; index <= 50; index++) {
            tasks.add(task((long) index, "TASK_MARKER_" + index, "TODO", "MEDIUM", ""));
        }
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(tasks);

        String result = builder.build(project(), section(), profile("TASKS"));

        assertEquals(20, countOccurrences(result, "TASK_MARKER_"));
        assertFalse(result.contains("TASK_MARKER_21"));
    }

    @Test
    void truncatesScalarsSectionBodiesAndFinalContext() {
        String scalarMarker = "S".repeat(1300);
        String bodyMarker = "B".repeat(2300);
        Project longProject = project();
        longProject.setDescription(scalarMarker);
        ProjectSection previous = section(300L, 10, "PREV", "前置", "", "DONE");
        when(projectSectionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(previous));
        when(sectionContentMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(content(1L, 300L, 1, "正文", bodyMarker)));
        List<Task> tasks = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            tasks.add(task((long) index, "T".repeat(1200), "S".repeat(1200), "P".repeat(1200), "R".repeat(1200)));
        }
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(tasks);

        String result = builder.build(longProject, section(), profile(
                "PROJECT_OVERVIEW", "PREVIOUS_SECTIONS", "TASKS"
        ));

        assertAll(
                () -> assertTrue(result.contains("S".repeat(1200))),
                () -> assertFalse(result.contains("S".repeat(1201))),
                () -> assertTrue(result.contains("B".repeat(2000))),
                () -> assertFalse(result.contains("B".repeat(2001))),
                () -> assertTrue(result.length() <= SectionAgentContextBuilder.MAX_CONTEXT_LENGTH)
        );
    }

    @Test
    void previousSectionsExcludeCurrentAndLaterAndUseLatestContent() {
        ProjectSection earlier = section(300L, 10, "EARLY", "前置一", "", "DONE");
        ProjectSection sameOrderEarlierId = section(310L, 20, "EARLY_SAME", "前置二", "", "DONE");
        ProjectSection current = section();
        ProjectSection later = section(330L, 30, "LATER", "后置", "", "TODO");
        SectionContent oldVersion = content(90L, 300L, 1, "旧版本", "OLD_BODY");
        SectionContent latestById = content(92L, 300L, 2, "最新版", "LATEST_BODY");
        SectionContent sameVersionLowerId = content(91L, 300L, 2, "同版本旧记录", "STALE_BODY");
        SectionContent secondLatest = content(93L, 310L, 4, "第二章节最新", "SECOND_BODY");
        SectionContent currentContent = content(94L, SECTION_ID, 99, "当前内容", "CURRENT_BODY");
        when(projectSectionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(later, current, sameOrderEarlierId, earlier));
        when(sectionContentMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> {
                    LambdaQueryWrapper<SectionContent> query = invocation.getArgument(0);
                    query.getSqlSegment();
                    if (query.getParamNameValuePairs().containsValue(300L)) {
                        return List.of(latestById, sameVersionLowerId, oldVersion);
                    }
                    if (query.getParamNameValuePairs().containsValue(310L)) {
                        return List.of(secondLatest);
                    }
                    return List.of(currentContent);
                });

        String result = builder.build(project(), section(), profile("PREVIOUS_SECTIONS"));

        assertAll(
                () -> assertContainsInOrder(result, "EARLY", "EARLY_SAME"),
                () -> assertTrue(result.contains("最新版")),
                () -> assertTrue(result.contains("LATEST_BODY")),
                () -> assertTrue(result.contains("第二章节最新")),
                () -> assertFalse(result.contains("旧版本")),
                () -> assertFalse(result.contains("同版本旧记录")),
                () -> assertFalse(result.contains("CURRENT_BODY")),
                () -> assertFalse(result.contains("LATER"))
        );
        verify(sectionContentMapper, times(2)).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void riskRendersOnlyLatestSnapshot() {
        RiskSnapshot oldRisk = risk(1L, "LOW", "10", "DOWN", "OLD_RISK", LocalDateTime.of(2026, 6, 20, 9, 0));
        RiskSnapshot sameTimeLowerId = risk(2L, "MEDIUM", "50", "FLAT", "STALE_RISK", LocalDateTime.of(2026, 6, 24, 9, 0));
        RiskSnapshot latest = risk(3L, "HIGH", "90", "UP", "LATEST_RISK", LocalDateTime.of(2026, 6, 24, 9, 0));
        when(riskSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(latest, sameTimeLowerId, oldRisk));

        String result = builder.build(project(), section(), profile("RISKS"));

        assertAll(
                () -> assertTrue(result.contains("LATEST_RISK")),
                () -> assertFalse(result.contains("OLD_RISK")),
                () -> assertFalse(result.contains("STALE_RISK"))
        );
    }

    @Test
    void sensitiveFileAndUserFieldsNeverAppear() {
        ProjectMember member = member(1L, 41L, 51L, "MEMBER", "开发者");
        ProjectFile file = file(2L, "safe-name.txt", "text/plain", "NOTE", "safe-description");
        file.setFileUrl("SENSITIVE_FILE_URL_SENTINEL");
        file.setUploaderId(999_999L);
        SysUser user = user(41L, "safe-user", "Safe Real Name");
        user.setPassword("SENSITIVE_PASSWORD_SENTINEL");
        user.setEmail("SENSITIVE_EMAIL_SENTINEL");
        user.setPhone("SENSITIVE_PHONE_SENTINEL");
        when(projectMemberMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(member));
        when(projectRoleMapper.selectBatchIds(any())).thenReturn(List.of(role(51L, "安全角色")));
        when(sysUserMapper.selectBatchIds(any())).thenReturn(List.of(user));
        when(projectFileMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(file));

        String result = builder.build(project(), section(), profile("MEMBERS", "FILES"));

        assertAll(
                () -> assertTrue(result.contains("safe-name.txt")),
                () -> assertTrue(result.contains("safe-description")),
                () -> assertTrue(result.contains("Safe Real Name")),
                () -> assertFalse(result.contains("SENSITIVE_FILE_URL_SENTINEL")),
                () -> assertFalse(result.contains("999999")),
                () -> assertFalse(result.contains("SENSITIVE_PASSWORD_SENTINEL")),
                () -> assertFalse(result.contains("SENSITIVE_EMAIL_SENTINEL")),
                () -> assertFalse(result.contains("SENSITIVE_PHONE_SENTINEL"))
        );
    }

    @Test
    void nullInputsAndNullScopeValuesAreSafeAndNeverRenderLiteralNull() {
        EffectiveAgentProfile nullScope = new EffectiveAgentProfile();
        nullScope.setContextScope(null);

        String empty = assertDoesNotThrow(() -> builder.build(null, null, null));
        String alsoEmpty = assertDoesNotThrow(() -> builder.build(null, null, nullScope));
        String missingData = assertDoesNotThrow(() -> builder.build(null, null, profile(
                null, "PROJECT_OVERVIEW", "CURRENT_SECTION", "TASKS", "COMMENTS"
        )));

        assertAll(
                () -> assertEquals("", empty),
                () -> assertEquals("", alsoEmpty),
                () -> assertFalse(missingData.toLowerCase().contains("null")),
                () -> assertTrue(missingData.contains("【项目概述】")),
                () -> assertTrue(missingData.contains("【当前章节】")),
                () -> assertTrue(missingData.contains("暂无数据"))
        );
        verifyNoInteractions(
                projectSectionMapper,
                sectionContentMapper,
                projectMemberMapper,
                projectRoleMapper,
                sysUserMapper,
                taskMapper,
                riskSnapshotMapper,
                projectFileMapper,
                sectionCommentMapper,
                sectionReviewMapper
        );
    }

    @Test
    void everyDatabaseCategoryQueryUsesCorrectScopeOrderAndHardLimit() {
        ProjectSection previous = section(300L, 10, "PREV", "前置", "", "DONE");
        when(projectSectionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(previous));
        when(sectionContentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(projectMemberMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(riskSnapshotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(projectFileMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(sectionCommentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(sectionReviewMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        builder.build(project(), section(), profile(
                "PREVIOUS_SECTIONS", "MEMBERS", "TASKS", "RISKS", "FILES", "COMMENTS", "REVIEW_HISTORY"
        ));

        ArgumentCaptor<LambdaQueryWrapper<ProjectSection>> sectionQuery = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        ArgumentCaptor<LambdaQueryWrapper<SectionContent>> contentQuery = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        ArgumentCaptor<LambdaQueryWrapper<ProjectMember>> memberQuery = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        ArgumentCaptor<LambdaQueryWrapper<Task>> taskQuery = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        ArgumentCaptor<LambdaQueryWrapper<RiskSnapshot>> riskQuery = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        ArgumentCaptor<LambdaQueryWrapper<ProjectFile>> fileQuery = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        ArgumentCaptor<LambdaQueryWrapper<SectionComment>> commentQuery = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        ArgumentCaptor<LambdaQueryWrapper<SectionReview>> reviewQuery = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(projectSectionMapper).selectList(sectionQuery.capture());
        verify(sectionContentMapper).selectList(contentQuery.capture());
        verify(projectMemberMapper).selectList(memberQuery.capture());
        verify(taskMapper).selectList(taskQuery.capture());
        verify(riskSnapshotMapper).selectList(riskQuery.capture());
        verify(projectFileMapper).selectList(fileQuery.capture());
        verify(sectionCommentMapper).selectList(commentQuery.capture());
        verify(sectionReviewMapper).selectList(reviewQuery.capture());

        assertAll(
                () -> assertQueryShape(sectionQuery.getValue(), "project_id", PROJECT_ID,
                        "order by sort_order asc,id asc", 20),
                () -> assertSqlContains(sectionQuery.getValue(),
                        "sort_order is null", "sort_order <", "sort_order =", "id <"),
                () -> assertBoundValue(sectionQuery.getValue(), "sort_order", "<", section().getSortOrder()),
                () -> assertBoundValue(sectionQuery.getValue(), "sort_order", "=", section().getSortOrder()),
                () -> assertBoundValue(sectionQuery.getValue(), "id", "<", SECTION_ID),
                () -> assertQueryShape(contentQuery.getValue(), "project_id", PROJECT_ID,
                        "order by version_no desc,id desc", 1),
                () -> assertBoundValue(contentQuery.getValue(), "section_id", "=", previous.getId()),
                () -> assertQueryShape(memberQuery.getValue(), "project_id", PROJECT_ID,
                        "order by id asc", 20),
                () -> assertQueryShape(taskQuery.getValue(), "project_id", PROJECT_ID,
                        "order by update_time desc,id desc", 20),
                () -> assertQueryShape(riskQuery.getValue(), "project_id", PROJECT_ID,
                        "order by snapshot_time desc,id desc", 1),
                () -> assertQueryShape(fileQuery.getValue(), "project_id", PROJECT_ID,
                        "order by update_time desc,id desc", 20),
                () -> assertQueryShape(commentQuery.getValue(), "section_id", SECTION_ID,
                        "order by create_time desc,id desc", 20),
                () -> assertQueryShape(reviewQuery.getValue(), "section_id", SECTION_ID,
                        "order by create_time desc,id desc", 20)
        );

        assertThrows(AssertionError.class, () -> assertAll(
                () -> assertBoundValue(contentQuery.getValue(), "project_id", "=", previous.getId()),
                () -> assertBoundValue(contentQuery.getValue(), "section_id", "=", PROJECT_ID)
        ));
    }

    @Test
    void previousContentQueriesArePerSectionLatestAndBoundedToTwentyCalls() {
        ProjectSection current = section(1000L, 100, "CURRENT", "当前", "", "DOING");
        List<ProjectSection> mapperRows = new ArrayList<>();
        for (int index = 1; index <= 25; index++) {
            mapperRows.add(section((long) index, index, "PREV_" + index, "前置" + index, "", "DONE"));
        }
        when(projectSectionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(mapperRows);
        when(sectionContentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        builder.build(project(), current, profile("PREVIOUS_SECTIONS"));

        ArgumentCaptor<LambdaQueryWrapper<SectionContent>> queries = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(sectionContentMapper, times(20)).selectList(queries.capture());
        assertEquals(20, queries.getAllValues().size());
        for (int index = 0; index < queries.getAllValues().size(); index++) {
            LambdaQueryWrapper<SectionContent> query = queries.getAllValues().get(index);
            long expectedSectionId = index + 1L;
            assertAll(
                    () -> assertQueryShape(query, "project_id", PROJECT_ID,
                            "order by version_no desc,id desc", 1),
                    () -> assertBoundValue(query, "section_id", "=", expectedSectionId)
            );
        }
    }

    @Test
    void previousSectionQueryHandlesNullSortAndRequiresCurrentId() {
        ProjectSection nullSort = section(900L, null, "NULL_SORT", "未排序", "", "DOING");
        when(projectSectionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        builder.build(project(), nullSort, profile("PREVIOUS_SECTIONS"));

        ArgumentCaptor<LambdaQueryWrapper<ProjectSection>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(projectSectionMapper).selectList(query.capture());
        assertAll(
                () -> assertQueryShape(query.getValue(), "project_id", PROJECT_ID,
                        "order by sort_order asc,id asc", 20),
                () -> assertSqlContains(query.getValue(), "sort_order is null", "id <"),
                () -> assertBoundValue(query.getValue(), "id", "<", nullSort.getId())
        );

        setUp();
        ProjectSection missingId = section(null, 20, "NO_ID", "无编号", "", "DOING");
        String result = builder.build(project(), missingId, profile("PREVIOUS_SECTIONS"));

        assertTrue(result.contains("暂无数据"));
        verifyNoInteractions(projectSectionMapper, sectionContentMapper);
    }

    @Test
    void truncationNeverSplitsSupplementaryCharactersAtAnyBoundary() {
        String emoji = "\uD83D\uDE80";
        Project scalarProject = project();
        scalarProject.setDescription("S".repeat(1199) + emoji + "tail");
        String scalarResult = builder.build(scalarProject, section(), profile("PROJECT_OVERVIEW"));

        ProjectSection previous = section(300L, 10, "PREV", "前置", "", "DONE");
        when(projectSectionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(previous));
        when(sectionContentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                content(1L, 300L, 1, "正文", "B".repeat(1999) + emoji + "tail")
        ));
        String bodyResult = builder.build(project(), section(), profile("PREVIOUS_SECTIONS"));

        List<Task> tasks = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            tasks.add(task((long) index, "T".repeat(1200), "", "", ""));
        }
        tasks.add(task(13L, "T".repeat(1133) + emoji + "T".repeat(65), "", "", ""));
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(tasks);
        String contextResult = builder.build(project(), section(), profile("TASKS"));

        assertAll(
                () -> assertValidSurrogatePairing(scalarResult),
                () -> assertValidSurrogatePairing(bodyResult),
                () -> assertValidSurrogatePairing(contextResult),
                () -> assertTrue(contextResult.length() <= SectionAgentContextBuilder.MAX_CONTEXT_LENGTH)
        );
    }

    private static EffectiveAgentProfile profile(String... categories) {
        EffectiveAgentProfile profile = new EffectiveAgentProfile();
        profile.setContextScope(categories == null ? null : Arrays.asList(categories));
        return profile;
    }

    private static Project project() {
        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setProjectName("TeamFlow 上线");
        project.setDescription("发布协作平台");
        project.setStatus("ACTIVE");
        project.setStartDate(LocalDate.of(2026, 6, 1));
        project.setEndDate(LocalDate.of(2026, 7, 31));
        return project;
    }

    private static ProjectSection section() {
        return section(SECTION_ID, 20, "DEV", "开发实现", "实现核心功能", "DOING");
    }

    private static ProjectSection section(Long id, Integer sortOrder, String code, String name,
                                          String description, String status) {
        ProjectSection section = new ProjectSection();
        section.setId(id);
        section.setProjectId(PROJECT_ID);
        section.setSortOrder(sortOrder);
        section.setSectionCode(code);
        section.setSectionName(name);
        section.setDescription(description);
        section.setStatus(status);
        return section;
    }

    private static SectionContent content(Long id, Long sectionId, Integer version, String title, String body) {
        SectionContent content = new SectionContent();
        content.setId(id);
        content.setProjectId(PROJECT_ID);
        content.setSectionId(sectionId);
        content.setVersionNo(version);
        content.setTitle(title);
        content.setBody(body);
        return content;
    }

    private static ProjectMember member(Long id, Long userId, Long roleId, String memberRole, String title) {
        ProjectMember member = new ProjectMember();
        member.setId(id);
        member.setProjectId(PROJECT_ID);
        member.setUserId(userId);
        member.setProjectRoleId(roleId);
        member.setMemberRole(memberRole);
        member.setMemberTitle(title);
        return member;
    }

    private static ProjectRole role(Long id, String name) {
        ProjectRole role = new ProjectRole();
        role.setId(id);
        role.setProjectId(PROJECT_ID);
        role.setRoleName(name);
        return role;
    }

    private static SysUser user(Long id, String username, String realName) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setRealName(realName);
        return user;
    }

    private static Task task(Long id, String title, String status, String priority, String blockReason) {
        Task task = new Task();
        task.setId(id);
        task.setProjectId(PROJECT_ID);
        task.setTitle(title);
        task.setStatus(status);
        task.setPriority(priority);
        task.setDueDate(LocalDate.of(2026, 7, 1));
        task.setBlockReason(blockReason);
        return task;
    }

    private static RiskSnapshot risk(Long id, String level, String score, String trend, String summary,
                                     LocalDateTime snapshotTime) {
        RiskSnapshot risk = new RiskSnapshot();
        risk.setId(id);
        risk.setProjectId(PROJECT_ID);
        risk.setRiskLevel(level);
        risk.setRiskScore(new BigDecimal(score));
        risk.setTrend(trend);
        risk.setSummary(summary);
        risk.setSnapshotTime(snapshotTime);
        return risk;
    }

    private static ProjectFile file(Long id, String name, String type, String documentType, String description) {
        ProjectFile file = new ProjectFile();
        file.setId(id);
        file.setProjectId(PROJECT_ID);
        file.setFileName(name);
        file.setFileType(type);
        file.setDocumentType(documentType);
        file.setDescription(description);
        return file;
    }

    private static SectionComment comment(Long id, String text, Integer resolved) {
        SectionComment comment = new SectionComment();
        comment.setId(id);
        comment.setSectionId(SECTION_ID);
        comment.setCommentText(text);
        comment.setResolvedFlag(resolved);
        return comment;
    }

    private static SectionReview review(Long id, String result, String text) {
        SectionReview review = new SectionReview();
        review.setId(id);
        review.setSectionId(SECTION_ID);
        review.setReviewResult(result);
        review.setReviewComment(text);
        return review;
    }

    private static void assertContainsInOrder(String text, String... markers) {
        int previous = -1;
        for (String marker : markers) {
            int current = text.indexOf(marker);
            assertTrue(current > previous, marker + " should follow the previous marker");
            previous = current;
        }
    }

    private static int countOccurrences(String text, String marker) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(marker, offset)) >= 0) {
            count++;
            offset += marker.length();
        }
        return count;
    }

    private static void assertQueryShape(LambdaQueryWrapper<?> query, String scopeColumn, Object scopeValue,
                                         String expectedOrder, int expectedLimit) {
        String sql = compactSql(normalizedSql(query));
        assertAll(
                () -> assertBoundValue(query, scopeColumn, "=", scopeValue),
                () -> assertTrue(sql.contains(compactSql(expectedOrder)), sql),
                () -> assertTrue(sql.endsWith("limit" + expectedLimit), sql)
        );
    }

    private static void assertBoundValue(LambdaQueryWrapper<?> query, String column, String operator,
                                         Object expectedValue) {
        String sql = normalizedSql(query);
        Pattern bindingPattern = Pattern.compile(
                "(?<![a-z0-9_])"
                        + Pattern.quote(column.trim().toLowerCase(Locale.ROOT))
                        + "\\s*"
                        + Pattern.quote(operator.trim().toLowerCase(Locale.ROOT))
                        + "\\s*"
                        + "#\\{ew\\.paramnamevaluepairs\\.([a-z0-9_]+)}"
        );
        Matcher matcher = bindingPattern.matcher(sql);
        assertTrue(matcher.find(), column + " " + operator + " binding missing from " + sql);

        String placeholder = matcher.group(1);
        String parameterName = query.getParamNameValuePairs().keySet().stream()
                .filter(key -> key.equalsIgnoreCase(placeholder))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "placeholder " + placeholder + " missing from " + query.getParamNameValuePairs()));
        assertEquals(expectedValue, query.getParamNameValuePairs().get(parameterName),
                column + " " + operator + " should bind through " + parameterName);
    }

    private static void assertSqlContains(LambdaQueryWrapper<?> query, String... fragments) {
        String sql = compactSql(normalizedSql(query));
        for (String fragment : fragments) {
            assertTrue(sql.contains(compactSql(fragment)), fragment + " missing from " + sql);
        }
    }

    private static String normalizedSql(LambdaQueryWrapper<?> query) {
        return query.getSqlSegment()
                .replace("`", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String compactSql(String sql) {
        return sql.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static void assertValidSurrogatePairing(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                assertTrue(index + 1 < value.length() && Character.isLowSurrogate(value.charAt(index + 1)),
                        "unpaired high surrogate at index " + index);
                index++;
            } else {
                assertFalse(Character.isLowSurrogate(current), "unpaired low surrogate at index " + index);
            }
        }
    }
}

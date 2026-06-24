package com.example.teamflow.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.teamflow.common.BizException;
import com.example.teamflow.dto.AgentProfileUpdateDTO;
import com.example.teamflow.entity.AgentProfile;
import com.example.teamflow.entity.ProjectRole;
import com.example.teamflow.entity.SysUser;
import com.example.teamflow.mapper.AgentProfileMapper;
import com.example.teamflow.mapper.ProjectRoleMapper;
import com.example.teamflow.service.AuthService;
import com.example.teamflow.service.PermissionService;
import com.example.teamflow.vo.AgentProfileVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentProfileServiceTest {
    private static final Long PROJECT_ID = 3L;
    private static final Long ROLE_ID = 7L;
    private static final Pattern BOUND_EQUALITY = Pattern.compile(
            "([a-z_]+)\\s*=\\s*#\\{[^}]*?paramNameValuePairs\\.([^,}]+)[^}]*}",
            Pattern.CASE_INSENSITIVE
    );

    @Mock
    private AgentProfileMapper agentProfileMapper;
    @Mock
    private ProjectRoleMapper projectRoleMapper;
    @Mock
    private PermissionService permissionService;
    @Mock
    private AuthService authService;

    private ObjectMapper objectMapper;
    private AgentProfileService service;

    @BeforeAll
    static void initializeMybatisTableMetadata() {
        if (TableInfoHelper.getTableInfo(AgentProfile.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), "agent-profile-service-test"),
                    AgentProfile.class
            );
        }
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new AgentProfileService(
                agentProfileMapper,
                projectRoleMapper,
                permissionService,
                authService,
                objectMapper
        );
    }

    @Test
    void projectSnapshotWinsOverGlobalDefault() {
        when(projectRoleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, PROJECT_ID, "QA", "测试负责人", 1));
        when(agentProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(projectProfile());

        EffectiveAgentProfile result = service.resolveEffective(PROJECT_ID, ROLE_ID);

        assertAll(
                () -> assertEquals(AgentProfileService.SOURCE_PROJECT, result.getSource()),
                () -> assertEquals(PROJECT_ID, result.getProjectId()),
                () -> assertEquals(ROLE_ID, result.getProjectRoleId()),
                () -> assertEquals("项目级职责", result.getResponsibilities())
        );
        verify(agentProfileMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void globalDefaultIsUsedWithRequestedRuntimeIds() {
        when(projectRoleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, PROJECT_ID, "QA", "测试负责人", 1));
        when(agentProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null, globalProfile());

        EffectiveAgentProfile result = service.resolveEffective(PROJECT_ID, ROLE_ID);

        assertAll(
                () -> assertEquals(AgentProfileService.SOURCE_GLOBAL, result.getSource()),
                () -> assertEquals(PROJECT_ID, result.getProjectId()),
                () -> assertEquals(ROLE_ID, result.getProjectRoleId()),
                () -> assertEquals("QA", result.getRoleCode())
        );
    }

    @Test
    void customRoleGetsSafeSyntheticProfile() {
        when(projectRoleMapper.selectById(8L))
                .thenReturn(role(8L, PROJECT_ID, "DATA_ANALYST", "数据分析师", 1));
        when(agentProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        EffectiveAgentProfile result = service.resolveEffective(PROJECT_ID, 8L);

        assertAll(
                () -> assertEquals(AgentProfileService.SOURCE_SYNTHETIC, result.getSource()),
                () -> assertEquals(PROJECT_ID, result.getProjectId()),
                () -> assertEquals(8L, result.getProjectRoleId()),
                () -> assertEquals("数据分析师", result.getRoleName()),
                () -> assertEquals("负责项目角色工作", result.getResponsibilities()),
                () -> assertEquals(List.of("PROJECT_OVERVIEW", "CURRENT_SECTION", "PREVIOUS_SECTIONS"),
                        result.getContextScope()),
                () -> assertEquals(List.of("READ_CONTEXT", "GENERATE_DRAFT"), result.getToolPermissions()),
                () -> assertEquals(Map.of("enabled", false), result.getMemoryPolicy()),
                () -> assertEquals(Map.of("enabled", false, "inputTypes", List.of()), result.getMultimodalConfig()),
                () -> assertEquals("围绕当前板块输出结构清晰、可执行、可验证的内容。",
                        result.getOutputTemplate()),
                () -> assertEquals("你是 TeamFlowAI 的数据分析师角色智能体。只基于授权上下文工作，不编造事实。",
                        result.getSystemPrompt()),
                () -> assertEquals(
                        "请以${roleName}身份为${projectName}的${sectionName}生成内容。职责：${responsibilities}。"
                                + "上下文：${context}。输出要求：${outputTemplate}。允许能力：${toolPermissions}。",
                        result.getTaskPromptTemplate()),
                () -> assertTrue(result.isEnabled())
        );
    }

    @Test
    void missingOrCrossProjectRoleIsRejected() {
        when(projectRoleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, 99L, "QA", "测试负责人", 1));

        BizException crossProject = assertThrows(BizException.class,
                () -> service.resolveEffective(PROJECT_ID, ROLE_ID));

        assertAll(
                () -> assertEquals(404, crossProject.getCode()),
                () -> assertEquals("项目角色不存在", crossProject.getMessage())
        );
        verifyNoInteractions(agentProfileMapper);
    }

    @Test
    void memberDetailRequiresMembershipAndRedactsRestrictedFields() {
        when(authService.currentUser()).thenReturn(user(11L, "USER"));
        when(permissionService.isProjectManager(PROJECT_ID, 11L)).thenReturn(false);
        when(projectRoleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, PROJECT_ID, "QA", "测试负责人", 1));
        when(agentProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null, globalProfile());

        AgentProfileVO result = service.getVisible(PROJECT_ID, ROLE_ID);

        assertAll(
                () -> assertNull(result.getSystemPrompt()),
                () -> assertNull(result.getTaskPromptTemplate()),
                () -> assertNull(result.getMemoryPolicy()),
                () -> assertNull(result.getMultimodalConfig()),
                () -> assertEquals("全局输出模板", result.getOutputTemplate())
        );
        verify(permissionService).requireProjectMember(PROJECT_ID);
        verify(permissionService, never()).requireProjectManage(PROJECT_ID);
    }

    @Test
    void managerDetailKeepsFullConfiguration() {
        when(authService.currentUser()).thenReturn(user(1L, "USER"));
        when(permissionService.isProjectManager(PROJECT_ID, 1L)).thenReturn(true);
        when(projectRoleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, PROJECT_ID, "QA", "测试负责人", 1));
        when(agentProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(projectProfile());

        AgentProfileVO result = service.getVisible(PROJECT_ID, ROLE_ID);

        assertAll(
                () -> assertEquals("系统提示词", result.getSystemPrompt()),
                () -> assertEquals("任务 ${context}", result.getTaskPromptTemplate()),
                () -> assertNotNull(result.getMemoryPolicy()),
                () -> assertNotNull(result.getMultimodalConfig())
        );
        verify(permissionService).requireProjectMember(PROJECT_ID);
        verify(permissionService).isProjectManager(PROJECT_ID, 1L);
    }

    @Test
    void listRequiresMembershipOrdersRolesAndAlwaysRedacts() {
        ProjectRole later = role(8L, PROJECT_ID, "DATA_ANALYST", "数据分析师", 20);
        ProjectRole first = role(ROLE_ID, PROJECT_ID, "QA", "测试负责人", 10);
        when(projectRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(later, first));
        when(agentProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        List<AgentProfileVO> result = service.listVisible(PROJECT_ID);

        assertAll(
                () -> assertEquals(List.of(ROLE_ID, 8L),
                        result.stream().map(AgentProfileVO::getProjectRoleId).toList()),
                () -> assertTrue(result.stream().allMatch(profile -> profile.getSystemPrompt() == null)),
                () -> assertTrue(result.stream().allMatch(profile -> profile.getTaskPromptTemplate() == null)),
                () -> assertTrue(result.stream().allMatch(profile -> profile.getMemoryPolicy() == null)),
                () -> assertTrue(result.stream().allMatch(profile -> profile.getMultimodalConfig() == null))
        );
        verify(permissionService).requireProjectMember(PROJECT_ID);
        verify(authService, never()).currentUser();
    }

    @Test
    void firstSaveCreatesCompleteProjectSnapshot() {
        when(projectRoleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, PROJECT_ID, "QA", "测试负责人", 1));
        when(agentProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        AgentProfileVO result = service.saveProjectSnapshot(PROJECT_ID, ROLE_ID, validUpdate());

        ArgumentCaptor<AgentProfile> captor = ArgumentCaptor.forClass(AgentProfile.class);
        verify(agentProfileMapper).insert(captor.capture());
        AgentProfile stored = captor.getValue();
        assertAll(
                () -> assertEquals("PROJECT", stored.getScopeType()),
                () -> assertEquals(PROJECT_ID, stored.getScopeId()),
                () -> assertEquals(PROJECT_ID, stored.getProjectId()),
                () -> assertEquals(ROLE_ID, stored.getProjectRoleId()),
                () -> assertEquals("QA", stored.getRoleCode()),
                () -> assertEquals("测试负责人", stored.getRoleName()),
                () -> assertEquals("[\"READ_CONTEXT\",\"GENERATE_DRAFT\"]", stored.getToolPermissions()),
                () -> assertEquals(1, stored.getEnabled()),
                () -> assertEquals(AgentProfileService.SOURCE_PROJECT, result.getSource())
        );
        verify(permissionService).requireProjectManage(PROJECT_ID);
        verify(permissionService, never()).requireProjectMember(PROJECT_ID);
    }

    @Test
    void saveSerializesAndReturnsEveryJsonConfigurationField() {
        when(projectRoleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, PROJECT_ID, "QA", "测试负责人", 1));
        when(agentProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        AgentProfileUpdateDTO dto = validUpdate();
        List<String> contexts = List.of("PROJECT_OVERVIEW", "CURRENT_SECTION", "FILES", "COMMENTS");
        List<String> tools = List.of("READ_CONTEXT", "GENERATE_DRAFT", "INSPECT_FILES");
        Map<String, Object> memory = Map.of("enabled", false, "window", 3);
        Map<String, Object> multimodal = Map.of("enabled", false, "inputTypes", List.of("TEXT", "IMAGE"));
        dto.setContextScope(contexts);
        dto.setToolPermissions(tools);
        dto.setMemoryPolicy(memory);
        dto.setMultimodalConfig(multimodal);

        AgentProfileVO result = service.saveProjectSnapshot(PROJECT_ID, ROLE_ID, dto);

        ArgumentCaptor<AgentProfile> captor = ArgumentCaptor.forClass(AgentProfile.class);
        verify(agentProfileMapper).insert(captor.capture());
        AgentProfile stored = captor.getValue();
        assertAll(
                () -> assertEquals(objectMapper.readTree(objectMapper.writeValueAsString(contexts)),
                        objectMapper.readTree(stored.getContextScope())),
                () -> assertEquals(objectMapper.readTree(objectMapper.writeValueAsString(tools)),
                        objectMapper.readTree(stored.getToolPermissions())),
                () -> assertEquals(objectMapper.readTree(objectMapper.writeValueAsString(memory)),
                        objectMapper.readTree(stored.getMemoryPolicy())),
                () -> assertEquals(objectMapper.readTree(objectMapper.writeValueAsString(multimodal)),
                        objectMapper.readTree(stored.getMultimodalConfig())),
                () -> assertEquals(contexts, result.getContextScope()),
                () -> assertEquals(tools, result.getToolPermissions()),
                () -> assertEquals(memory, result.getMemoryPolicy()),
                () -> assertEquals(multimodal, result.getMultimodalConfig())
        );
    }

    @Test
    void everyAllowedContextAndToolValueIsAccepted() {
        when(projectRoleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, PROJECT_ID, "QA", "测试负责人", 1));
        when(agentProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        AgentProfileUpdateDTO dto = validUpdate();
        List<String> allContexts = List.of(
                "PROJECT_OVERVIEW",
                "CURRENT_SECTION",
                "PREVIOUS_SECTIONS",
                "MEMBERS",
                "TASKS",
                "RISKS",
                "FILES",
                "COMMENTS",
                "REVIEW_HISTORY"
        );
        List<String> allTools = List.of(
                "READ_CONTEXT",
                "GENERATE_DRAFT",
                "SUGGEST_REVIEW_COMMENT",
                "INSPECT_FILES",
                "SUGGEST_TASKS"
        );
        dto.setContextScope(allContexts);
        dto.setToolPermissions(allTools);

        AgentProfileVO result = service.saveProjectSnapshot(PROJECT_ID, ROLE_ID, dto);

        assertAll(
                () -> assertEquals(allContexts, result.getContextScope()),
                () -> assertEquals(allTools, result.getToolPermissions())
        );
        verify(agentProfileMapper).insert(any(AgentProfile.class));
    }

    @Test
    void everySupportedTemplateVariableIsAccepted() {
        when(projectRoleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, PROJECT_ID, "QA", "测试负责人", 1));
        when(agentProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        AgentProfileUpdateDTO dto = validUpdate();
        String allVariables = "${roleCode} ${roleName} ${responsibilities} ${projectName} "
                + "${projectDescription} ${sectionName} ${sectionCode} ${sectionDescription} "
                + "${context} ${outputTemplate} ${toolPermissions}";
        dto.setTaskPromptTemplate(allVariables);

        AgentProfileVO result = service.saveProjectSnapshot(PROJECT_ID, ROLE_ID, dto);

        assertEquals(allVariables, result.getTaskPromptTemplate());
        verify(agentProfileMapper).insert(any(AgentProfile.class));
    }

    @Test
    void saveProjectSnapshotUsesReadCommittedIsolation() throws Exception {
        Method method = AgentProfileService.class.getMethod(
                "saveProjectSnapshot",
                Long.class,
                Long.class,
                AgentProfileUpdateDTO.class
        );

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertAll(
                () -> assertNotNull(transactional),
                () -> assertEquals(Isolation.READ_COMMITTED, transactional.isolation())
        );
    }

    @Test
    void updatingExistingSnapshotReappliesProjectIdentityAndNeverWritesGlobal() {
        AgentProfile existing = projectProfile();
        existing.setRoleName("过期名称");
        AgentProfile global = globalProfile();
        AgentProfile unchangedGlobal = globalProfile();
        when(projectRoleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, PROJECT_ID, "QA", "测试负责人", 1));
        when(agentProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<AgentProfile> wrapper = invocation.getArgument(0);
            if (profileLookupMatches(wrapper, existing)) {
                return existing;
            }
            if (profileLookupMatches(wrapper, global)) {
                return global;
            }
            return null;
        });
        when(agentProfileMapper.updateById(existing)).thenReturn(1);
        AgentProfileUpdateDTO dto = validUpdate();
        dto.setEnabled(false);

        AgentProfileVO result = service.saveProjectSnapshot(PROJECT_ID, ROLE_ID, dto);

        ArgumentCaptor<AgentProfile> captor = ArgumentCaptor.forClass(AgentProfile.class);
        verify(agentProfileMapper).updateById(captor.capture());
        verify(agentProfileMapper, never()).insert(any(AgentProfile.class));
        ArgumentCaptor<LambdaQueryWrapper<AgentProfile>> queryCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(agentProfileMapper, times(1)).selectOne(queryCaptor.capture());
        LambdaQueryWrapper<AgentProfile> lookup = queryCaptor.getValue();
        Map<String, Object> criteria = boundEqualities(lookup);
        AgentProfile updated = captor.getValue();
        assertAll(
                () -> assertEquals(Set.of("scope_type", "scope_id", "role_code"), criteria.keySet()),
                () -> assertEquals("PROJECT", criteria.get("scope_type")),
                () -> assertEquals(PROJECT_ID, criteria.get("scope_id")),
                () -> assertEquals("QA", criteria.get("role_code")),
                () -> assertTrue(profileLookupMatches(lookup, existing)),
                () -> assertFalse(profileLookupMatches(lookup, global)),
                () -> assertSame(existing, updated),
                () -> assertFalse(updated == global),
                () -> assertEquals(30L, updated.getId()),
                () -> assertEquals("PROJECT", updated.getScopeType()),
                () -> assertEquals(PROJECT_ID, updated.getScopeId()),
                () -> assertEquals(PROJECT_ID, updated.getProjectId()),
                () -> assertEquals(ROLE_ID, updated.getProjectRoleId()),
                () -> assertEquals("QA", updated.getRoleCode()),
                () -> assertEquals("测试负责人", updated.getRoleName()),
                () -> assertEquals(0, updated.getEnabled()),
                () -> assertFalse(result.getEnabled()),
                () -> assertEquals(unchangedGlobal, global)
        );
        verify(agentProfileMapper, never()).updateById(global);
    }

    @Test
    void duplicateInsertRaceRetriesAsUpdateWithoutLeakingSqlError() {
        AgentProfile raced = projectProfile();
        when(projectRoleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, PROJECT_ID, "QA", "测试负责人", 1));
        when(agentProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null, raced);
        when(agentProfileMapper.insert(any(AgentProfile.class)))
                .thenThrow(new DuplicateKeyException("raw duplicate key sql"));
        when(agentProfileMapper.updateById(raced)).thenReturn(1);

        AgentProfileVO result = service.saveProjectSnapshot(PROJECT_ID, ROLE_ID, validUpdate());

        verify(agentProfileMapper).updateById(raced);
        assertEquals(AgentProfileService.SOURCE_PROJECT, result.getSource());
    }

    @Test
    void zeroRowExistingUpdateReturnsRereadPersistedSnapshot() {
        AgentProfile existing = projectProfile();
        AgentProfile persisted = projectProfile();
        persisted.setResponsibilities("数据库中的职责");
        when(projectRoleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, PROJECT_ID, "QA", "测试负责人", 1));
        when(agentProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing, persisted);
        when(agentProfileMapper.updateById(existing)).thenReturn(0);

        AgentProfileVO result = service.saveProjectSnapshot(PROJECT_ID, ROLE_ID, validUpdate());

        assertAll(
                () -> assertEquals(AgentProfileService.SOURCE_PROJECT, result.getSource()),
                () -> assertEquals("数据库中的职责", result.getResponsibilities())
        );
        verify(agentProfileMapper, times(2)).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void zeroRowExistingUpdateDeletedConcurrentlyReturnsStableConflict() {
        AgentProfile existing = projectProfile();
        when(projectRoleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, PROJECT_ID, "QA", "测试负责人", 1));
        when(agentProfileMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(existing)
                .thenReturn(null);
        when(agentProfileMapper.updateById(existing)).thenReturn(0);

        BizException error = assertThrows(BizException.class,
                () -> service.saveProjectSnapshot(PROJECT_ID, ROLE_ID, validUpdate()));

        assertAll(
                () -> assertEquals(409, error.getCode()),
                () -> assertEquals("角色智能体配置已被并发修改", error.getMessage())
        );
        verify(agentProfileMapper, times(2)).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void zeroRowDuplicateRecoveryDeletedConcurrentlyReturnsStableConflict() {
        AgentProfile raced = projectProfile();
        when(projectRoleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, PROJECT_ID, "QA", "测试负责人", 1));
        when(agentProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null, raced, null);
        when(agentProfileMapper.insert(any(AgentProfile.class)))
                .thenThrow(new DuplicateKeyException("raw duplicate key sql"));
        when(agentProfileMapper.updateById(raced)).thenReturn(0);

        BizException error = assertThrows(BizException.class,
                () -> service.saveProjectSnapshot(PROJECT_ID, ROLE_ID, validUpdate()));

        assertAll(
                () -> assertEquals(409, error.getCode()),
                () -> assertEquals("角色智能体配置已被并发修改", error.getMessage())
        );
        verify(agentProfileMapper, times(3)).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void duplicateInsertRaceWithoutProjectRowReturnsStableConflict() {
        when(projectRoleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, PROJECT_ID, "QA", "测试负责人", 1));
        when(agentProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(agentProfileMapper.insert(any(AgentProfile.class)))
                .thenThrow(new DuplicateKeyException("raw duplicate key sql"));

        BizException error = assertThrows(BizException.class,
                () -> service.saveProjectSnapshot(PROJECT_ID, ROLE_ID, validUpdate()));

        assertAll(
                () -> assertEquals(409, error.getCode()),
                () -> assertEquals("角色智能体配置已被并发修改", error.getMessage()),
                () -> assertFalse(error.getMessage().contains("sql"))
        );
        verify(agentProfileMapper, times(2)).selectOne(any(LambdaQueryWrapper.class));
        verify(agentProfileMapper, never()).updateById(any(AgentProfile.class));
    }

    @Test
    void resetDeletesOnlyProjectSnapshotAndReturnsFullGlobalFallback() {
        AgentProfile project = projectProfile();
        when(projectRoleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, PROJECT_ID, "QA", "测试负责人", 1));
        when(agentProfileMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(project, null, globalProfile());

        AgentProfileVO result = service.resetProjectSnapshot(PROJECT_ID, ROLE_ID);

        verify(permissionService).requireProjectManage(PROJECT_ID);
        verify(agentProfileMapper).deleteById(project.getId());
        assertAll(
                () -> assertEquals(AgentProfileService.SOURCE_GLOBAL, result.getSource()),
                () -> assertEquals("全局系统提示词", result.getSystemPrompt()),
                () -> assertNotNull(result.getMemoryPolicy())
        );
    }

    @Test
    void resetWithoutProjectSnapshotIsIdempotentAndReturnsGlobalFallback() {
        AgentProfile global = globalProfile();
        when(projectRoleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, PROJECT_ID, "QA", "测试负责人", 1));
        when(agentProfileMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null, null, global, null, null, global);

        AgentProfileVO first = service.resetProjectSnapshot(PROJECT_ID, ROLE_ID);
        AgentProfileVO second = service.resetProjectSnapshot(PROJECT_ID, ROLE_ID);

        assertAll(
                () -> assertEquals(AgentProfileService.SOURCE_GLOBAL, first.getSource()),
                () -> assertEquals("全局系统提示词", first.getSystemPrompt()),
                () -> assertEquals(first.getSource(), second.getSource()),
                () -> assertEquals(first.getSystemPrompt(), second.getSystemPrompt()),
                () -> assertEquals(first.getMemoryPolicy(), second.getMemoryPolicy())
        );
        verify(permissionService, times(2)).requireProjectManage(PROJECT_ID);
        verify(agentProfileMapper, never()).deleteById(anyLong());
    }

    @Test
    void invalidContextScopeIsRejectedBeforePersistence() {
        when(projectRoleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, PROJECT_ID, "QA", "测试负责人", 1));
        AgentProfileUpdateDTO dto = validUpdate();
        dto.setContextScope(List.of("SECRET_DATABASE"));

        BizException error = assertThrows(BizException.class,
                () -> service.saveProjectSnapshot(PROJECT_ID, ROLE_ID, dto));

        assertAll(
                () -> assertEquals(400, error.getCode()),
                () -> assertEquals("角色智能体上下文范围不合法", error.getMessage())
        );
        verify(agentProfileMapper, never()).insert(any(AgentProfile.class));
        verify(agentProfileMapper, never()).updateById(any(AgentProfile.class));
    }

    @Test
    void nullContextScopeEntryReturnsStableValidationError() {
        when(projectRoleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, PROJECT_ID, "QA", "测试负责人", 1));
        AgentProfileUpdateDTO dto = validUpdate();
        List<String> contexts = new ArrayList<>();
        contexts.add("PROJECT_OVERVIEW");
        contexts.add(null);
        dto.setContextScope(contexts);

        BizException error = assertThrows(BizException.class,
                () -> service.saveProjectSnapshot(PROJECT_ID, ROLE_ID, dto));

        assertAll(
                () -> assertEquals(400, error.getCode()),
                () -> assertEquals("角色智能体上下文范围不合法", error.getMessage())
        );
        verify(agentProfileMapper, never()).insert(any(AgentProfile.class));
        verify(agentProfileMapper, never()).updateById(any(AgentProfile.class));
    }

    @Test
    void invalidToolPermissionIsRejectedBeforePersistence() {
        when(projectRoleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, PROJECT_ID, "QA", "测试负责人", 1));
        AgentProfileUpdateDTO dto = validUpdate();
        dto.setToolPermissions(List.of("WRITE_DATABASE"));

        BizException error = assertThrows(BizException.class,
                () -> service.saveProjectSnapshot(PROJECT_ID, ROLE_ID, dto));

        assertAll(
                () -> assertEquals(400, error.getCode()),
                () -> assertEquals("角色智能体工具权限不合法", error.getMessage())
        );
        verify(agentProfileMapper, never()).insert(any(AgentProfile.class));
    }

    @Test
    void nullToolPermissionEntryReturnsStableValidationError() {
        when(projectRoleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, PROJECT_ID, "QA", "测试负责人", 1));
        AgentProfileUpdateDTO dto = validUpdate();
        List<String> tools = new ArrayList<>();
        tools.add("READ_CONTEXT");
        tools.add(null);
        dto.setToolPermissions(tools);

        BizException error = assertThrows(BizException.class,
                () -> service.saveProjectSnapshot(PROJECT_ID, ROLE_ID, dto));

        assertAll(
                () -> assertEquals(400, error.getCode()),
                () -> assertEquals("角色智能体工具权限不合法", error.getMessage())
        );
        verify(agentProfileMapper, never()).insert(any(AgentProfile.class));
        verify(agentProfileMapper, never()).updateById(any(AgentProfile.class));
    }

    @Test
    void unknownPromptVariableIsRejectedBeforePersistence() {
        when(projectRoleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, PROJECT_ID, "QA", "测试负责人", 1));
        AgentProfileUpdateDTO dto = validUpdate();
        dto.setTaskPromptTemplate("生成 ${unknownValue}");

        BizException error = assertThrows(BizException.class,
                () -> service.saveProjectSnapshot(PROJECT_ID, ROLE_ID, dto));

        assertAll(
                () -> assertEquals(400, error.getCode()),
                () -> assertEquals("角色智能体模板变量不合法", error.getMessage())
        );
        verify(agentProfileMapper, never()).insert(any(AgentProfile.class));
    }

    @Test
    void missingRequiredDtoFieldIsRejectedByServiceValidation() {
        when(projectRoleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, PROJECT_ID, "QA", "测试负责人", 1));
        AgentProfileUpdateDTO dto = validUpdate();
        dto.setMemoryPolicy(null);

        BizException error = assertThrows(BizException.class,
                () -> service.saveProjectSnapshot(PROJECT_ID, ROLE_ID, dto));

        assertAll(
                () -> assertEquals(400, error.getCode()),
                () -> assertEquals("角色智能体记忆策略不合法", error.getMessage())
        );
        verify(agentProfileMapper, never()).insert(any(AgentProfile.class));
    }

    @Test
    void corruptStoredJsonReturnsStableServerError() {
        AgentProfile corrupt = projectProfile();
        corrupt.setContextScope("not-json");
        when(projectRoleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, PROJECT_ID, "QA", "测试负责人", 1));
        when(agentProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(corrupt);

        BizException error = assertThrows(BizException.class,
                () -> service.resolveEffective(PROJECT_ID, ROLE_ID));

        assertAll(
                () -> assertEquals(500, error.getCode()),
                () -> assertEquals("角色智能体配置数据损坏", error.getMessage())
        );
    }

    private ProjectRole role(Long id, Long projectId, String code, String name, Integer sortOrder) {
        ProjectRole role = new ProjectRole();
        role.setId(id);
        role.setProjectId(projectId);
        role.setRoleCode(code);
        role.setRoleName(name);
        role.setResponsibility("负责项目角色工作");
        role.setSortOrder(sortOrder);
        return role;
    }

    private SysUser user(Long id, String role) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setRole(role);
        return user;
    }

    private AgentProfile projectProfile() {
        AgentProfile profile = baseProfile();
        profile.setId(30L);
        profile.setScopeType("PROJECT");
        profile.setScopeId(PROJECT_ID);
        profile.setProjectId(PROJECT_ID);
        profile.setProjectRoleId(ROLE_ID);
        profile.setResponsibilities("项目级职责");
        profile.setOutputTemplate("项目级输出模板");
        profile.setSystemPrompt("系统提示词");
        profile.setTaskPromptTemplate("任务 ${context}");
        return profile;
    }

    private AgentProfile globalProfile() {
        AgentProfile profile = baseProfile();
        profile.setId(10L);
        profile.setScopeType("GLOBAL");
        profile.setScopeId(0L);
        profile.setResponsibilities("全局职责");
        profile.setOutputTemplate("全局输出模板");
        profile.setSystemPrompt("全局系统提示词");
        return profile;
    }

    private AgentProfile baseProfile() {
        AgentProfile profile = new AgentProfile();
        profile.setRoleCode("QA");
        profile.setRoleName("测试负责人");
        profile.setContextScope("[\"PROJECT_OVERVIEW\",\"CURRENT_SECTION\"]");
        profile.setOutputTemplate("输出模板");
        profile.setToolPermissions("[\"READ_CONTEXT\",\"GENERATE_DRAFT\"]");
        profile.setMemoryPolicy("{\"enabled\":false}");
        profile.setSystemPrompt("系统提示词");
        profile.setTaskPromptTemplate("任务 ${context}");
        profile.setMultimodalConfig("{\"enabled\":false,\"inputTypes\":[]}");
        profile.setEnabled(1);
        profile.setUpdateTime(LocalDateTime.of(2026, 6, 23, 10, 0));
        return profile;
    }

    private AgentProfileUpdateDTO validUpdate() {
        AgentProfileUpdateDTO dto = new AgentProfileUpdateDTO();
        dto.setResponsibilities("更新后的职责");
        dto.setContextScope(List.of("PROJECT_OVERVIEW", "CURRENT_SECTION"));
        dto.setOutputTemplate("按验收标准输出 ${sectionName}");
        dto.setToolPermissions(List.of("READ_CONTEXT", "GENERATE_DRAFT"));
        dto.setMemoryPolicy(Map.of("enabled", false));
        dto.setSystemPrompt("你是 ${roleName}");
        dto.setTaskPromptTemplate("根据 ${context} 生成 ${outputTemplate}");
        dto.setMultimodalConfig(Map.of("enabled", false, "inputTypes", List.of()));
        dto.setEnabled(true);
        return dto;
    }

    private static boolean profileLookupMatches(
            LambdaQueryWrapper<AgentProfile> wrapper,
            AgentProfile fixture
    ) {
        Map<String, Object> criteria = boundEqualities(wrapper);
        Set<String> expectedColumns = criteria.containsKey("project_id")
                ? Set.of("scope_type", "scope_id", "project_id", "role_code")
                : Set.of("scope_type", "scope_id", "role_code");
        return criteria.keySet().equals(expectedColumns)
                && Objects.equals(criteria.get("scope_type"), fixture.getScopeType())
                && Objects.equals(criteria.get("scope_id"), fixture.getScopeId())
                && (!criteria.containsKey("project_id")
                        || Objects.equals(criteria.get("project_id"), fixture.getProjectId()))
                && Objects.equals(criteria.get("role_code"), fixture.getRoleCode());
    }

    private static Map<String, Object> boundEqualities(LambdaQueryWrapper<AgentProfile> wrapper) {
        Map<String, Object> criteria = new LinkedHashMap<>();
        Matcher matcher = BOUND_EQUALITY.matcher(wrapper.getSqlSegment());
        Map<String, Object> parameters = wrapper.getParamNameValuePairs();
        while (matcher.find()) {
            criteria.put(
                    matcher.group(1).toLowerCase(Locale.ROOT),
                    parameters.get(matcher.group(2))
            );
        }
        return criteria;
    }
}

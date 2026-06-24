package com.example.teamflow.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.teamflow.ai.AiClient;
import com.example.teamflow.common.BizException;
import com.example.teamflow.entity.AiRecord;
import com.example.teamflow.entity.Project;
import com.example.teamflow.entity.ProjectRole;
import com.example.teamflow.entity.ProjectSection;
import com.example.teamflow.mapper.ProjectRoleMapper;
import com.example.teamflow.mapper.ProjectSectionMapper;
import com.example.teamflow.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SectionAgentServiceProfileTest {
    private static final Long PROJECT_ID = 1L;
    private static final Long SECTION_ID = 2L;
    private static final Long ROLE_ID = 3L;
    private static final String SYSTEM_TEMPLATE_SECRET = "SYSTEM_TEMPLATE_SECRET";
    private static final String TASK_TEMPLATE_SECRET = "TASK_TEMPLATE_SECRET";
    private static final String OUTPUT_TEMPLATE_SECRET = "OUTPUT_TEMPLATE_SECRET";
    private static final String CONTEXT_SECRET = "CONTEXT_SECRET";
    private static final String COMPOSED_PROMPT_SECRET = "COMPOSED_PROMPT_SECRET";
    private static final String USER_MESSAGE_SECRET = "USER_MESSAGE_SECRET";

    @Mock
    private AiClient aiClient;
    @Mock
    private ProjectService projectService;
    @Mock
    private ProjectRoleMapper projectRoleMapper;
    @Mock
    private ProjectSectionMapper projectSectionMapper;
    @Mock
    private AiRecordService aiRecordService;
    @Mock
    private AgentProfileService agentProfileService;
    @Mock
    private SectionAgentContextBuilder sectionAgentContextBuilder;
    @Mock
    private AgentPromptComposer agentPromptComposer;

    private ObjectMapper objectMapper;
    private SectionAgentService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = newService(objectMapper);
    }

    private SectionAgentService newService(ObjectMapper mapper) {
        return new SectionAgentService(
                aiClient,
                projectService,
                projectRoleMapper,
                projectSectionMapper,
                aiRecordService,
                agentProfileService,
                sectionAgentContextBuilder,
                agentPromptComposer,
                mapper);
    }

    @Test
    void disabledProfileStopsGenerationBeforeContextPromptAiOrRecord() {
        stubValidatedSection(profile(false, "配置角色"));

        BizException error = assertThrows(BizException.class,
                () -> service.generateSectionContent(PROJECT_ID, SECTION_ID));

        assertEquals(409, error.getCode());
        assertEquals("该角色智能体已停用", error.getMessage());
        verifyNoInteractions(sectionAgentContextBuilder, agentPromptComposer, aiClient, aiRecordService);
    }

    @Test
    void disabledProfileStopsChatBeforeContextPromptAiOrRecord() {
        stubValidatedSection(profile(false, "配置角色"));

        BizException error = assertThrows(BizException.class,
                () -> service.chatSectionContent(PROJECT_ID, SECTION_ID, List.of()));

        assertEquals(409, error.getCode());
        assertEquals("该角色智能体已停用", error.getMessage());
        verifyNoInteractions(sectionAgentContextBuilder, agentPromptComposer, aiClient, aiRecordService);
    }

    @Test
    void missingOwnerRoleReturnsStableNotFoundBeforeResolvingProfile() {
        when(projectService.getProject(PROJECT_ID)).thenReturn(project());
        ProjectSection section = section();
        section.setOwnerRoleId(null);
        when(projectSectionMapper.selectById(SECTION_ID)).thenReturn(section);

        BizException error = assertThrows(BizException.class,
                () -> service.generateSectionContent(PROJECT_ID, SECTION_ID));

        assertEquals(404, error.getCode());
        assertEquals("项目角色不存在", error.getMessage());
        verify(projectRoleMapper, never()).selectById(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(agentProfileService, sectionAgentContextBuilder,
                agentPromptComposer, aiClient, aiRecordService);
    }

    @Test
    void crossProjectOwnerRoleReturnsStableNotFoundBeforeResolvingProfile() {
        when(projectService.getProject(PROJECT_ID)).thenReturn(project());
        when(projectSectionMapper.selectById(SECTION_ID)).thenReturn(section());
        when(projectRoleMapper.selectById(ROLE_ID)).thenReturn(role(99L, "其他角色"));

        BizException error = assertThrows(BizException.class,
                () -> service.chatSectionContent(PROJECT_ID, SECTION_ID, List.of()));

        assertEquals(404, error.getCode());
        assertEquals("项目角色不存在", error.getMessage());
        verifyNoInteractions(agentProfileService, sectionAgentContextBuilder,
                agentPromptComposer, aiClient, aiRecordService);
    }

    @Test
    void enabledGenerationResolvesBuildsAndComposesInOrderThenRecordsGlmResult() {
        Project project = project();
        ProjectSection section = section();
        EffectiveAgentProfile profile = profile(true, "配置角色");
        stubValidatedSection(project, section, role(PROJECT_ID, "所有者角色"), profile);
        when(sectionAgentContextBuilder.build(project, section, profile)).thenReturn(CONTEXT_SECRET);
        String composedPrompt = String.join("|", COMPOSED_PROMPT_SECRET, SYSTEM_TEMPLATE_SECRET,
                TASK_TEMPLATE_SECRET, OUTPUT_TEMPLATE_SECRET, CONTEXT_SECRET);
        when(agentPromptComposer.composeGeneration(project, section, profile, CONTEXT_SECRET))
                .thenReturn(composedPrompt);
        when(aiClient.enabled()).thenReturn(true);
        when(aiClient.chat(composedPrompt)).thenReturn("generated-body");

        Map<String, String> result = service.generateSectionContent(PROJECT_ID, SECTION_ID);

        assertEquals(Map.of(
                "title", "需求分析 (AI智能体协助生成)",
                "body", "generated-body"), result);
        InOrder order = inOrder(agentProfileService, sectionAgentContextBuilder, agentPromptComposer);
        order.verify(agentProfileService).resolveEffective(PROJECT_ID, ROLE_ID);
        order.verify(sectionAgentContextBuilder).build(project, section, profile);
        order.verify(agentPromptComposer).composeGeneration(project, section, profile, CONTEXT_SECRET);
        verify(aiClient).chat(composedPrompt);
        ArgumentCaptor<String> auditPrompt = ArgumentCaptor.forClass(String.class);
        verify(aiRecordService).saveGeneratedRecord(
                eq(PROJECT_ID),
                eq("SECTION_GENERATE"),
                auditPrompt.capture(),
                eq("generated-body"),
                eq(AiRecord.SOURCE_ZHIPU_GLM),
                eq(AiClient.class.getSimpleName()),
                isNull());
        assertSafeAuditPrompt(auditPrompt.getValue(), 0);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void enabledChatUsesComposedSystemPromptFiltersMessagesParsesAndRecords() {
        Project project = project();
        ProjectSection section = section();
        EffectiveAgentProfile profile = profile(true, "配置角色");
        stubValidatedSection(project, section, role(PROJECT_ID, "所有者角色"), profile);
        when(sectionAgentContextBuilder.build(project, section, profile)).thenReturn(CONTEXT_SECRET);
        String composedSystem = String.join("|", COMPOSED_PROMPT_SECRET, SYSTEM_TEMPLATE_SECRET,
                TASK_TEMPLATE_SECRET, OUTPUT_TEMPLATE_SECRET, CONTEXT_SECRET);
        when(agentPromptComposer.composeChatSystem(project, section, profile, CONTEXT_SECRET))
                .thenReturn(composedSystem);
        when(aiClient.enabled()).thenReturn(true);
        when(aiClient.chat(anyList())).thenReturn(
                "{\"explanation\":\"done\",\"title\":\"new title\",\"body\":\"new body\"}");
        List<Map<String, String>> messages = Arrays.asList(
                null,
                Map.of("role", "SYSTEM", "content", "client system secret"),
                Map.of("role", "tool", "content", "tool secret"),
                Map.of("role", "user", "content", "   "),
                Map.of("role", "USER", "content", USER_MESSAGE_SECRET),
                Map.of("role", "assistant", "content", "prior answer"),
                Map.of("role", "arbitrary", "content", "ignored"));

        Map<String, String> result = service.chatSectionContent(PROJECT_ID, SECTION_ID, messages);

        assertEquals("done", result.get("explanation"));
        assertEquals("new title", result.get("title"));
        assertEquals("new body", result.get("body"));
        InOrder order = inOrder(agentProfileService, sectionAgentContextBuilder, agentPromptComposer);
        order.verify(agentProfileService).resolveEffective(PROJECT_ID, ROLE_ID);
        order.verify(sectionAgentContextBuilder).build(project, section, profile);
        order.verify(agentPromptComposer).composeChatSystem(project, section, profile, CONTEXT_SECRET);

        ArgumentCaptor<List<Map<String, String>>> messageCaptor = ArgumentCaptor.forClass(List.class);
        verify(aiClient).chat(messageCaptor.capture());
        assertEquals(List.of(
                Map.of("role", "system", "content", composedSystem),
                Map.of("role", "user", "content", USER_MESSAGE_SECRET),
                Map.of("role", "assistant", "content", "prior answer")), messageCaptor.getValue());
        ArgumentCaptor<String> auditPrompt = ArgumentCaptor.forClass(String.class);
        verify(aiRecordService).saveGeneratedRecord(
                eq(PROJECT_ID),
                eq("SECTION_GENERATE"),
                auditPrompt.capture(),
                eq("{\"explanation\":\"done\",\"title\":\"new title\",\"body\":\"new body\"}"),
                eq(AiRecord.SOURCE_ZHIPU_GLM),
                eq("AiClient"),
                isNull());
        assertSafeAuditPrompt(auditPrompt.getValue(), messages.size());
    }

    @Test
    void blankGenerationUsesUnchangedLocalDocumentWithProfileRoleAndFallbackMetadata() {
        stubEnabledGeneration("配置角色");
        when(aiClient.chat("generation-prompt")).thenReturn("   ");

        Map<String, String> result = service.generateSectionContent(PROJECT_ID, SECTION_ID);

        assertTrue(result.get("body").startsWith("### 🤖 配置角色 智能体协助生成草稿"));
        assertTrue(result.get("body").contains("#### 1. 核心业务与场景描述"));
        verifyFallbackRecord(result.get("body"));
    }

    @Test
    void generationExceptionUsesUnchangedLocalDocumentWithProfileRoleAndFallbackMetadata() {
        stubEnabledGeneration("配置角色");
        when(aiClient.chat("generation-prompt")).thenThrow(new RuntimeException("offline"));

        Map<String, String> result = service.generateSectionContent(PROJECT_ID, SECTION_ID);

        assertTrue(result.get("body").startsWith("### 🤖 配置角色 智能体协助生成草稿"));
        assertTrue(result.get("body").contains("#### 1. 核心业务与场景描述"));
        assertTrue(result.get("body").contains("原因：offline"));
        verifyFallbackRecord(result.get("body"));
    }

    @Test
    void blankChatReturnsThreeFallbackKeysUsingProfileRole() {
        stubEnabledChat("配置角色");
        when(aiClient.chat(anyList())).thenReturn("\t");

        Map<String, String> result = service.chatSectionContent(
                PROJECT_ID, SECTION_ID, List.of(Map.of("role", "user", "content", "补充范围")));

        assertEquals(3, result.size());
        assertTrue(result.get("explanation").contains("我是【配置角色】"));
        assertEquals("需求分析说明书", result.get("title"));
        assertTrue(result.get("body").startsWith("### 🤖 配置角色 智能体协助生成草稿"));
        verifyChatFallbackRecord(1);
    }

    @Test
    void chatExceptionReturnsThreeFallbackKeysUsingProfileRole() {
        stubEnabledChat("配置角色");
        when(aiClient.chat(anyList())).thenThrow(new RuntimeException("offline"));

        Map<String, String> result = service.chatSectionContent(
                PROJECT_ID, SECTION_ID, List.of(Map.of("role", "user", "content", "补充范围")));

        assertEquals(3, result.size());
        assertTrue(result.get("explanation").contains("我是【配置角色】"));
        assertEquals("需求分析说明书", result.get("title"));
        assertTrue(result.get("body").startsWith("### 🤖 配置角色 智能体协助生成草稿"));
        verifyChatFallbackRecord(1);
    }

    @Test
    void chatStripsJsonCodeFenceAndParsesResponse() {
        stubEnabledChat("配置角色");
        when(aiClient.chat(anyList())).thenReturn(
                "```json\n{\"explanation\":\"ok\",\"title\":\"title\",\"body\":\"body\"}\n```");

        Map<String, String> result = service.chatSectionContent(PROJECT_ID, SECTION_ID, List.of());

        assertEquals(Map.of("explanation", "ok", "title", "title", "body", "body"), result);
        verify(aiRecordService).saveGeneratedRecord(
                eq(PROJECT_ID),
                eq("SECTION_GENERATE"),
                eq("sectionId=2;profileSource=PROJECT;roleCode=PRODUCT_MANAGER;chatMessageCount=0"),
                eq("{\"explanation\":\"ok\",\"title\":\"title\",\"body\":\"body\"}"),
                eq(AiRecord.SOURCE_ZHIPU_GLM),
                eq("AiClient"),
                isNull());
    }

    @Test
    void disabledAiGenerationSkipsRemoteCallAndRecordsLocalFallbackMetadata() {
        stubGeneration("配置角色", false);

        Map<String, String> result = service.generateSectionContent(PROJECT_ID, SECTION_ID);

        verify(aiClient, never()).chat(anyString());
        assertTrue(result.get("body").startsWith("### 🤖 配置角色 智能体协助生成草稿"));
        verifyFallbackRecord(result.get("body"));
    }

    @Test
    void disabledAiChatSkipsRemoteCallAndHandlesNullAndUntrustedMessagesSafely() {
        stubChat("配置角色", false);
        List<Map<String, String>> messages = Arrays.asList(
                null,
                Map.of("role", "SYSTEM", "content", "untrusted system"),
                Map.of("role", "tool", "content", "untrusted tool"),
                Map.of("role", "USER", "content", "用户\"意见\n第二行"));

        Map<String, String> result = service.chatSectionContent(PROJECT_ID, SECTION_ID, messages);

        verify(aiClient, never()).chat(anyList());
        assertEquals(3, result.size());
        assertTrue(result.get("explanation").contains("用户\"意见\n第二行"));
        assertFalse(result.get("explanation").contains("untrusted system"));
        assertFalse(result.get("explanation").contains("untrusted tool"));
        verifyChatFallbackRecord(messages.size());
    }

    @Test
    void nullGenerationResponseUsesLocalFallback() {
        stubEnabledGeneration("配置角色");
        when(aiClient.chat("generation-prompt")).thenReturn(null);

        Map<String, String> result = service.generateSectionContent(PROJECT_ID, SECTION_ID);

        assertTrue(result.get("body").startsWith("### 🤖 配置角色 智能体协助生成草稿"));
        verifyFallbackRecord(result.get("body"));
    }

    @Test
    void malformedChatJsonReturnsStableWrapperWithRawBody() {
        stubEnabledChat("配置角色");
        when(aiClient.chat(anyList())).thenReturn("not-json");

        Map<String, String> result = service.chatSectionContent(PROJECT_ID, SECTION_ID, List.of());

        assertEquals(3, result.size());
        assertTrue(result.get("explanation").contains("not-json"));
        assertEquals("需求分析 (AI智能体协同设计)", result.get("title"));
        assertEquals("not-json", result.get("body"));
    }

    @Test
    void validJsonWithInvalidContractReturnsStableParseFailureWrapper() {
        stubEnabledChat("配置角色");
        List<String> invalidResponses = List.of(
                "42",
                "[]",
                "null",
                "{\"explanation\":1,\"title\":\"title\",\"body\":\"body\"}",
                "{\"explanation\":[],\"title\":\"title\",\"body\":\"body\"}",
                "{\"explanation\":{},\"title\":\"title\",\"body\":\"body\"}",
                "{\"explanation\":\"ok\",\"title\":\"title\"}");
        when(aiClient.chat(anyList())).thenReturn(
                invalidResponses.get(0),
                invalidResponses.subList(1, invalidResponses.size()).toArray(String[]::new));

        for (String raw : invalidResponses) {
            Map<String, String> result = service.chatSectionContent(PROJECT_ID, SECTION_ID, List.of());

            assertEquals(3, result.size());
            assertTrue(result.get("explanation").contains(raw));
            assertEquals("需求分析 (AI智能体协同设计)", result.get("title"));
            assertEquals(raw, result.get("body"));
        }
    }

    @Test
    void validChatJsonDropsUnexpectedKeys() {
        stubEnabledChat("配置角色");
        when(aiClient.chat(anyList())).thenReturn(
                "{\"explanation\":\"ok\",\"title\":\"title\",\"body\":\"body\",\"secret\":\"drop-me\"}");

        Map<String, String> result = service.chatSectionContent(PROJECT_ID, SECTION_ID, List.of());

        assertEquals(Map.of("explanation", "ok", "title", "title", "body", "body"), result);
        assertFalse(result.containsKey("secret"));
    }

    @Test
    void localFallbackSerializationEscapesProfileRoleAndLastUserMessage() throws Exception {
        String roleName = "角色\"甲\n第二行";
        String userMessage = "意见\"一\n下一行";
        stubChat(roleName, false);
        List<Map<String, String>> messages = Arrays.asList(
                null,
                Map.of("role", "USER", "content", userMessage));

        Map<String, String> result = service.chatSectionContent(PROJECT_ID, SECTION_ID, messages);

        assertTrue(result.get("explanation").contains(roleName));
        assertTrue(result.get("explanation").contains(userMessage));
        ArgumentCaptor<String> recordedJson = ArgumentCaptor.forClass(String.class);
        verify(aiRecordService).saveGeneratedRecord(
                eq(PROJECT_ID),
                eq("SECTION_GENERATE"),
                eq("sectionId=2;profileSource=PROJECT;roleCode=PRODUCT_MANAGER;chatMessageCount=2"),
                recordedJson.capture(),
                eq(AiRecord.SOURCE_LOCAL_FALLBACK),
                eq("local-fallback"),
                isNull());
        JsonNode recorded = objectMapper.readTree(recordedJson.getValue());
        assertEquals(result.get("explanation"), recorded.get("explanation").textValue());
        assertEquals(result.get("title"), recorded.get("title").textValue());
        assertEquals(result.get("body"), recorded.get("body").textValue());
    }

    @Test
    void localFallbackSerializationFailureReturnsFixedSafeThreeKeyJson() throws Exception {
        ObjectMapper failingMapper = spy(new ObjectMapper());
        doThrow(new JsonProcessingException("serialization failed") {
        }).when(failingMapper).writeValueAsString(any());
        service = newService(failingMapper);
        stubChat("角色\"甲\n第二行", false);

        Map<String, String> result = service.chatSectionContent(
                PROJECT_ID,
                SECTION_ID,
                List.of(Map.of("role", "user", "content", "意见\"一\n下一行")));

        assertEquals(Map.of(
                "explanation", "本地备用智能体已生成章节草稿。",
                "title", "章节文档",
                "body", ""), result);
        ArgumentCaptor<String> recordedJson = ArgumentCaptor.forClass(String.class);
        verify(aiRecordService).saveGeneratedRecord(
                eq(PROJECT_ID),
                eq("SECTION_GENERATE"),
                anyString(),
                recordedJson.capture(),
                eq(AiRecord.SOURCE_LOCAL_FALLBACK),
                eq("local-fallback"),
                isNull());
        JsonNode recorded = new ObjectMapper().readTree(recordedJson.getValue());
        assertTrue(recorded.isObject());
        assertEquals(3, recorded.size());
        assertEquals("本地备用智能体已生成章节草稿。", recorded.get("explanation").textValue());
        assertEquals("章节文档", recorded.get("title").textValue());
        assertEquals("", recorded.get("body").textValue());
    }

    @Test
    void serviceSourceRemovesLegacyContextQueryAndKeepsOnlyFallbackRequirementSwitch() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/example/teamflow/service/ai/SectionAgentService.java"));

        assertFalse(source.contains("SectionContentMapper"));
        assertFalse(source.contains("projectSectionMapper.selectList"));
        assertEquals(1, occurrences(source, "case \"REQUIREMENT\""));
        assertTrue(source.contains("getLocalFallbackForSection"));
        assertTrue(source.contains("#### 1. 核心业务与场景描述"));
    }

    private void stubValidatedSection(EffectiveAgentProfile profile) {
        stubValidatedSection(project(), section(), role(PROJECT_ID, "所有者角色"), profile);
    }

    private void stubValidatedSection(Project project, ProjectSection section,
            ProjectRole role, EffectiveAgentProfile profile) {
        when(projectService.getProject(PROJECT_ID)).thenReturn(project);
        when(projectSectionMapper.selectById(SECTION_ID)).thenReturn(section);
        when(projectRoleMapper.selectById(ROLE_ID)).thenReturn(role);
        when(agentProfileService.resolveEffective(PROJECT_ID, ROLE_ID)).thenReturn(profile);
    }

    private void stubEnabledGeneration(String profileRoleName) {
        stubGeneration(profileRoleName, true);
    }

    private void stubGeneration(String profileRoleName, boolean aiEnabled) {
        Project project = project();
        ProjectSection section = section();
        EffectiveAgentProfile profile = profile(true, profileRoleName);
        stubValidatedSection(project, section, role(PROJECT_ID, "所有者角色"), profile);
        when(sectionAgentContextBuilder.build(project, section, profile)).thenReturn("context");
        when(agentPromptComposer.composeGeneration(project, section, profile, "context"))
                .thenReturn("generation-prompt");
        when(aiClient.enabled()).thenReturn(aiEnabled);
    }

    private void stubEnabledChat(String profileRoleName) {
        stubChat(profileRoleName, true);
    }

    private void stubChat(String profileRoleName, boolean aiEnabled) {
        Project project = project();
        ProjectSection section = section();
        EffectiveAgentProfile profile = profile(true, profileRoleName);
        stubValidatedSection(project, section, role(PROJECT_ID, "所有者角色"), profile);
        when(sectionAgentContextBuilder.build(project, section, profile)).thenReturn("context");
        when(agentPromptComposer.composeChatSystem(project, section, profile, "context"))
                .thenReturn("chat-system");
        when(aiClient.enabled()).thenReturn(aiEnabled);
    }

    private void verifyFallbackRecord(String body) {
        verify(aiRecordService).saveGeneratedRecord(
                PROJECT_ID,
                "SECTION_GENERATE",
                "sectionId=2;profileSource=PROJECT;roleCode=PRODUCT_MANAGER;chatMessageCount=0",
                body,
                AiRecord.SOURCE_LOCAL_FALLBACK,
                "local-fallback",
                null);
    }

    private void verifyChatFallbackRecord(int messageCount) {
        ArgumentCaptor<String> auditPrompt = ArgumentCaptor.forClass(String.class);
        verify(aiRecordService).saveGeneratedRecord(
                eq(PROJECT_ID),
                eq("SECTION_GENERATE"),
                auditPrompt.capture(),
                anyString(),
                eq(AiRecord.SOURCE_LOCAL_FALLBACK),
                eq("local-fallback"),
                isNull());
        assertSafeAuditPrompt(auditPrompt.getValue(), messageCount);
    }

    private Project project() {
        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setProjectName("示例项目");
        project.setDescription("项目描述");
        return project;
    }

    private ProjectSection section() {
        ProjectSection section = new ProjectSection();
        section.setId(SECTION_ID);
        section.setProjectId(PROJECT_ID);
        section.setOwnerRoleId(ROLE_ID);
        section.setSectionCode("REQUIREMENT");
        section.setSectionName("需求分析");
        section.setDescription("章节描述");
        return section;
    }

    private ProjectRole role(Long projectId, String roleName) {
        ProjectRole role = new ProjectRole();
        role.setId(ROLE_ID);
        role.setProjectId(projectId);
        role.setRoleCode("PRODUCT_MANAGER");
        role.setRoleName(roleName);
        return role;
    }

    private EffectiveAgentProfile profile(boolean enabled, String roleName) {
        EffectiveAgentProfile profile = new EffectiveAgentProfile();
        profile.setProjectId(PROJECT_ID);
        profile.setProjectRoleId(ROLE_ID);
        profile.setRoleCode("PRODUCT_MANAGER");
        profile.setRoleName(roleName);
        profile.setSource("PROJECT");
        profile.setSystemPrompt(SYSTEM_TEMPLATE_SECRET);
        profile.setTaskPromptTemplate(TASK_TEMPLATE_SECRET);
        profile.setOutputTemplate(OUTPUT_TEMPLATE_SECRET);
        profile.setEnabled(enabled);
        return profile;
    }

    private void assertSafeAuditPrompt(String auditPrompt, int messageCount) {
        assertTrue(auditPrompt.contains("sectionId=" + SECTION_ID));
        assertTrue(auditPrompt.contains("profileSource=PROJECT"));
        assertTrue(auditPrompt.contains("roleCode=PRODUCT_MANAGER"));
        assertTrue(auditPrompt.contains("chatMessageCount=" + messageCount));
        for (String secret : List.of(SYSTEM_TEMPLATE_SECRET, TASK_TEMPLATE_SECRET,
                OUTPUT_TEMPLATE_SECRET, CONTEXT_SECRET, COMPOSED_PROMPT_SECRET,
                USER_MESSAGE_SECRET)) {
            assertFalse(auditPrompt.contains(secret), "audit prompt leaked " + secret);
        }
    }

    private int occurrences(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}

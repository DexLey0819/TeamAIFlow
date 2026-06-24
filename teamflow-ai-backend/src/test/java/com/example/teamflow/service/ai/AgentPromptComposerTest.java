package com.example.teamflow.service.ai;

import com.example.teamflow.common.BizException;
import com.example.teamflow.entity.Project;
import com.example.teamflow.entity.ProjectSection;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPromptComposerTest {
    private final AgentPromptComposer composer = new AgentPromptComposer();

    @Test
    void generationExpandsEverySupportedVariable() {
        Project project = project("TeamFlowAI", "项目描述");
        ProjectSection section = section("REQUIREMENT", "需求分析", "章节描述");
        EffectiveAgentProfile profile = profile(
                "系统提示词",
                "${roleCode}|${roleName}|${responsibilities}|${projectName}|${projectDescription}|"
                        + "${sectionName}|${sectionCode}|${sectionDescription}|${context}|${outputTemplate}|"
                        + "${toolPermissions}");

        String result = composer.composeGeneration(project, section, profile, "前置上下文");

        assertAll(
                () -> assertTrue(result.contains(
                        "QA|测试负责人|负责质量保障|TeamFlowAI|项目描述|需求分析|REQUIREMENT|章节描述|"
                                + "前置上下文|Markdown 测试报告|READ_CONTEXT, GENERATE_DRAFT")),
                () -> assertFalse(result.contains("${"))
        );
    }

    @Test
    void replacementValuesContainingDollarAndBackslashArePreserved() {
        Project project = project("预算 $5", "路径 C:\\workspace\\teamflow");
        ProjectSection section = section("OTHER", "费用 $section", "保存到 D:\\deliverables");
        EffectiveAgentProfile profile = profile("系统 $prompt\\base",
                "${projectName}|${projectDescription}|${sectionName}|${sectionDescription}|${context}");

        String result = composer.composeGeneration(project, section, profile, "$context\\tail");

        assertTrue(result.contains(
                "预算 $5|路径 C:\\workspace\\teamflow|费用 $section|保存到 D:\\deliverables|$context\\tail"));
    }

    @Test
    void unknownTemplateVariableIsRejectedWithExactError() {
        EffectiveAgentProfile profile = profile("系统提示词", "${unknownValue}");

        BizException error = assertThrows(BizException.class,
                () -> composer.composeGeneration(project("项目", "描述"),
                        section("API", "接口", "接口描述"), profile, "上下文"));

        assertTemplateError(error);
    }

    @Test
    void malformedAndDanglingTemplateVariablesAreRejectedWithExactError() {
        for (String template : List.of("${}", "${role-name}", "${ roleName}", "${roleName")) {
            BizException error = assertThrows(BizException.class,
                    () -> composer.validateTemplateVariables(template), template);
            assertTemplateError(error);
        }
    }

    @Test
    void generationOrdersSystemPromptThenExpandedTaskThenGuidance() {
        EffectiveAgentProfile profile = profile("SYSTEM-INSTRUCTIONS", "TASK-${sectionName}-INSTRUCTIONS");

        String result = composer.composeGeneration(project("项目", "描述"),
                section("API", "接口设计", "接口描述"), profile, "上下文");

        int systemIndex = result.indexOf("SYSTEM-INSTRUCTIONS");
        int taskIndex = result.indexOf("TASK-接口设计-INSTRUCTIONS");
        int guidanceIndex = result.indexOf("核心 RESTful API 契约");
        assertTrue(systemIndex >= 0 && systemIndex < taskIndex && taskIndex < guidanceIndex);
    }

    @Test
    void everyKnownSectionCodeGetsItsDistinctiveGuidance() {
        Map<String, String> expectedGuidance = new LinkedHashMap<>();
        expectedGuidance.put("REQUIREMENT", "核心功能模块、非功能需求、核心业务用例清单");
        expectedGuidance.put("USE_CASE", "参与者、前置条件、主成功路径、分支路径及后置条件");
        expectedGuidance.put("PROTOTYPE", "主要页面结构、跳转关系和交互逻辑");
        expectedGuidance.put("DATABASE", "表名、字段、数据类型、主外键、索引及字段注释");
        expectedGuidance.put("API", "核心 RESTful API 契约");
        expectedGuidance.put("FRONTEND", "脚手架选型、组件化拆分方案、状态管理");
        expectedGuidance.put("BACKEND", "分层架构、包结构");
        expectedGuidance.put("TESTING", "用例ID、测试模块、前置条件、测试步骤和预期输出");
        expectedGuidance.put("SUMMARY", "交付成果、任务燃尽与完成情况");

        expectedGuidance.forEach((code, guidance) -> {
            String result = composer.composeGeneration(project("项目", "描述"),
                    section(code, "章节", "默认说明"), profile("系统", "任务"), "上下文");
            assertTrue(result.contains(guidance), code);
        });
    }

    @Test
    void sectionCodeMatchingIsCaseInsensitive() {
        String result = composer.composeGeneration(project("项目", "描述"),
                section("testing", "测试", "默认说明"), profile("系统", "任务"), "上下文");

        assertTrue(result.contains("用例ID、测试模块、前置条件、测试步骤和预期输出"));
    }

    @Test
    void unknownSectionCodeUsesSectionDescriptionGuidance() {
        String result = composer.composeGeneration(project("项目", "描述"),
                section("CUSTOM", "自定义", "自定义交付说明"), profile("系统", "任务"), "上下文");

        assertTrue(result.contains("请根据该章节的功能说明：\"自定义交付说明\" 生成对应的高质量交付物文档草稿。"));
    }

    @Test
    void chatOrdersProfileInstructionsBeforeStrictJsonContractAndGuidance() {
        String result = composer.composeChatSystem(project("项目", "描述"),
                section("DATABASE", "数据库", "数据库说明"),
                profile("SYSTEM-CHAT", "TASK-${context}"), "CONTEXT-CHAT");

        int systemIndex = result.indexOf("SYSTEM-CHAT");
        int taskIndex = result.indexOf("TASK-CONTEXT-CHAT");
        int contractIndex = result.indexOf("【重要且必须遵守的答复格式】");
        int guidanceIndex = result.indexOf("数据库表结构设计");
        assertTrue(systemIndex >= 0 && systemIndex < taskIndex && taskIndex < contractIndex
                && contractIndex < guidanceIndex);
    }

    @Test
    void chatRequiresExactJsonKeysWithoutInstructingCodeFenceWrapping() {
        String result = composer.composeChatSystem(project("项目", "描述"),
                section("API", "接口", "接口说明"), profile("系统", "任务"), "上下文");

        assertAll(
                () -> assertTrue(result.contains("\"explanation\"")),
                () -> assertTrue(result.contains("\"title\"")),
                () -> assertTrue(result.contains("\"body\"")),
                () -> assertTrue(result.contains("必须以严格的 JSON 格式回复")),
                () -> assertTrue(result.contains("不应该包含任何 JSON 外包的 Markdown 代码块语法标记")),
                () -> assertFalse(result.contains("请使用 ```")),
                () -> assertFalse(result.contains("包裹在 ```"))
        );
    }

    @Test
    void nullBackedVariablesExpandToEmptyStrings() {
        Project project = project(null, null);
        ProjectSection section = section(null, null, null);
        EffectiveAgentProfile profile = profile(
                "SYSTEM-MARKER",
                "BEGIN[${roleCode}][${roleName}][${responsibilities}][${projectName}]"
                        + "[${projectDescription}][${sectionName}][${sectionCode}][${sectionDescription}]"
                        + "[${context}][${outputTemplate}][${toolPermissions}]END");
        profile.setRoleCode(null);
        profile.setRoleName(null);
        profile.setResponsibilities(null);
        profile.setOutputTemplate(null);
        profile.setToolPermissions(null);

        String generation = composer.composeGeneration(project, section, profile, null);
        String chat = composer.composeChatSystem(project, section, profile, null);
        String emptyExpansion = "BEGIN[][][][][][][][][][][]END";

        assertAll(
                () -> assertTrue(generation.contains(
                        "SYSTEM-MARKER\n\n" + emptyExpansion + "\n\n【章节特定内容指南】")),
                () -> assertTrue(chat.contains(
                        "SYSTEM-MARKER\n\n" + emptyExpansion + "\n\n【重要且必须遵守的答复格式】")),
                () -> assertFalse(generation.contains("${")),
                () -> assertFalse(chat.contains("${")),
                () -> assertFalse(generation.contains("null")),
                () -> assertFalse(chat.contains("null"))
        );
    }

    private void assertTemplateError(BizException error) {
        assertAll(
                () -> assertEquals(400, error.getCode()),
                () -> assertEquals("角色智能体模板变量不合法", error.getMessage())
        );
    }

    private Project project(String name, String description) {
        Project project = new Project();
        project.setProjectName(name);
        project.setDescription(description);
        return project;
    }

    private ProjectSection section(String code, String name, String description) {
        ProjectSection section = new ProjectSection();
        section.setSectionCode(code);
        section.setSectionName(name);
        section.setDescription(description);
        return section;
    }

    private EffectiveAgentProfile profile(String systemPrompt, String taskPromptTemplate) {
        EffectiveAgentProfile profile = new EffectiveAgentProfile();
        profile.setRoleCode("QA");
        profile.setRoleName("测试负责人");
        profile.setResponsibilities("负责质量保障");
        profile.setOutputTemplate("Markdown 测试报告");
        profile.setToolPermissions(List.of("READ_CONTEXT", "GENERATE_DRAFT"));
        profile.setSystemPrompt(systemPrompt);
        profile.setTaskPromptTemplate(taskPromptTemplate);
        return profile;
    }
}

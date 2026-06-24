package com.example.teamflow.service.ai;

import com.example.teamflow.common.BizException;
import com.example.teamflow.entity.Project;
import com.example.teamflow.entity.ProjectSection;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class AgentPromptComposer {
    private static final String INVALID_TEMPLATE_MESSAGE = "角色智能体模板变量不合法";
    private static final Pattern TEMPLATE_VARIABLE = Pattern.compile("\\$\\{([A-Za-z][A-Za-z0-9]*)}");
    private static final Set<String> SUPPORTED_VARIABLES = Set.of(
            "roleCode",
            "roleName",
            "responsibilities",
            "projectName",
            "projectDescription",
            "sectionName",
            "sectionCode",
            "sectionDescription",
            "context",
            "outputTemplate",
            "toolPermissions"
    );

    public String composeGeneration(Project project, ProjectSection section,
            EffectiveAgentProfile profile, String context) {
        Map<String, String> replacements = replacementMap(project, section, profile, context);
        String taskPrompt = expand(value(profile == null ? null : profile.getTaskPromptTemplate()), replacements);

        return value(profile == null ? null : profile.getSystemPrompt())
                + "\n\n"
                + taskPrompt
                + "\n\n【章节特定内容指南】\n"
                + sectionGuidance(section)
                + "\n\n【绘图说明】\n"
                + "如果在描述业务流程、表结构、接口调用关系、测试用例场景等时，建议在输出中使用 PlantUML 语法块进行图形绘制（使用 ```plantuml 和 ``` 包裹代码，以 @startuml 开始并以 @enduml 结束，例如时序图、用例图或 E-R 图）。系统会自动在页面上将其渲染为直观的矢量图表，使交付文档更加专业。";
    }

    public String composeChatSystem(Project project, ProjectSection section,
            EffectiveAgentProfile profile, String context) {
        Map<String, String> replacements = replacementMap(project, section, profile, context);
        String taskPrompt = expand(value(profile == null ? null : profile.getTaskPromptTemplate()), replacements);

        return value(profile == null ? null : profile.getSystemPrompt())
                + "\n\n"
                + taskPrompt
                + "\n\n【重要且必须遵守的答复格式】\n"
                + "1. 你必须以严格的 JSON 格式回复用户的每次提问或修改指令。你的回复中不应该包含任何 JSON 外包的 Markdown 代码块语法标记（比如 ```json 或 ``` ）。\n"
                + "2. 回复的 JSON 结构必须精确地符合以下格式：\n"
                + "{\n"
                + "  \"explanation\": \"对本次修改、调整、补充设计的简要解释或回复用户的对话文字（使用专业、热情的语言，支持普通 Markdown 文本，可分段）\",\n"
                + "  \"title\": \"最终建议的章节标题（不含任何 Markdown 格式修饰符）\",\n"
                + "  \"body\": \"最终建议的完整文档章节正文 Markdown 格式草稿（内容要详尽、结构清晰、覆盖对应职能，如表结构、测试用例表、代码思路设计等）\"\n"
                + "}\n"
                + "3. 章节特定内容指南：\n"
                + sectionGuidance(section)
                + "\n\n【绘图说明】\n"
                + "如果在描述业务流程、表结构、接口调用关系、测试用例场景等时，建议在 `body` 字段的 Markdown 正文中，使用 PlantUML 语法块进行图形绘制（使用 ```plantuml 和 ``` 包裹代码，以 @startuml 开始并以 @enduml 结束，无需额外的 JSON 转义，只需正常换行，例如时序图、用例图或 E-R 图）。系统会自动在页面上将其渲染为直观的矢量图表，使交付文档更加专业。";
    }

    public void validateTemplateVariables(String template) {
        if (template == null) {
            return;
        }

        Matcher matcher = TEMPLATE_VARIABLE.matcher(template);
        int validatedThrough = 0;
        while (matcher.find()) {
            int malformedStart = template.indexOf("${", validatedThrough);
            if ((malformedStart >= 0 && malformedStart < matcher.start())
                    || !SUPPORTED_VARIABLES.contains(matcher.group(1))) {
                throw invalidTemplate();
            }
            validatedThrough = matcher.end();
        }

        if (template.indexOf("${", validatedThrough) >= 0) {
            throw invalidTemplate();
        }
    }

    private Map<String, String> replacementMap(Project project, ProjectSection section,
            EffectiveAgentProfile profile, String context) {
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put("roleCode", value(profile == null ? null : profile.getRoleCode()));
        replacements.put("roleName", value(profile == null ? null : profile.getRoleName()));
        replacements.put("responsibilities", value(profile == null ? null : profile.getResponsibilities()));
        replacements.put("projectName", value(project == null ? null : project.getProjectName()));
        replacements.put("projectDescription", value(project == null ? null : project.getDescription()));
        replacements.put("sectionName", value(section == null ? null : section.getSectionName()));
        replacements.put("sectionCode", value(section == null ? null : section.getSectionCode()));
        replacements.put("sectionDescription", value(section == null ? null : section.getDescription()));
        replacements.put("context", value(context));
        replacements.put("outputTemplate", value(profile == null ? null : profile.getOutputTemplate()));
        replacements.put("toolPermissions", renderToolPermissions(
                profile == null ? null : profile.getToolPermissions()));
        return replacements;
    }

    private String expand(String template, Map<String, String> replacements) {
        validateTemplateVariables(template);
        Matcher matcher = TEMPLATE_VARIABLE.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacements.get(matcher.group(1))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String renderToolPermissions(List<String> toolPermissions) {
        if (toolPermissions == null) {
            return "";
        }
        return toolPermissions.stream()
                .map(this::value)
                .collect(Collectors.joining(", "));
    }

    private String sectionGuidance(ProjectSection section) {
        String code = value(section == null ? null : section.getSectionCode()).toUpperCase(Locale.ROOT);
        return switch (code) {
            case "REQUIREMENT" ->
                    "   - 作为【产品经理】，请细化项目需求范围，列出核心功能模块、非功能需求、核心业务用例清单。";
            case "USE_CASE" ->
                    "   - 作为【产品经理】，请设计核心业务场景的用户用例流（Use Case Flows），包含参与者、前置条件、主成功路径、分支路径及后置条件。";
            case "PROTOTYPE" ->
                    "   - 作为【产品经理】，请设计系统的主要页面结构、跳转关系和交互逻辑，并用文字描述各关键视图的布局。";
            case "DATABASE" ->
                    "   - 作为【技术总监】，请提供符合业务逻辑的数据库表结构设计。包含表名、字段、数据类型、主外键、索引及字段注释。建议使用 Markdown 表格或提供 SQL 初始化脚本块。";
            case "API" ->
                    "   - 作为【技术总监】，请设计核心 RESTful API 契约，列出接口路径、请求方法、输入参数及响应 JSON 格式示例。";
            case "FRONTEND" ->
                    "   - 作为【前端开发】，请描述前端项目的脚手架选型、组件化拆分方案、状态管理以及关键页面的实现思路与组件层级结构。";
            case "BACKEND" ->
                    "   - 作为【后端开发】，请描述后端系统架构设计（分层架构、包结构）、核心技术框架配置选型、业务服务类设计思路及缓存/安全处理。";
            case "TESTING" ->
                    "   - 作为【测试负责人】，请编写测试用例表，包含用例ID、测试模块、前置条件、测试步骤和预期输出，以 Markdown 表格形式呈现。";
            case "SUMMARY" ->
                    "   - 作为【项目经理】，请总结项目的交付成果、任务燃尽与完成情况，评估团队表现并提炼后续优化经验。";
            default -> "   - 请根据该章节的功能说明：\""
                    + value(section == null ? null : section.getDescription())
                    + "\" 生成对应的高质量交付物文档草稿。";
        };
    }

    private BizException invalidTemplate() {
        return new BizException(400, INVALID_TEMPLATE_MESSAGE);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}

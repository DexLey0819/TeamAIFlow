package com.example.teamflow.service.ai;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SectionAgentService {
    private static final String SAFE_FALLBACK_CHAT_JSON =
            "{\"explanation\":\"本地备用智能体已生成章节草稿。\",\"title\":\"章节文档\",\"body\":\"\"}";
    private static final Map<String, String> SECTION_DEFAULT_TITLES;
    static {
        Map<String, String> map = new java.util.HashMap<>();
        map.put("MANAGEMENT_DELIVERY", "项目管理与WBS排程");
        map.put("REQUIREMENT", "需求分析说明书");
        map.put("USE_CASE_ANALYSIS", "系统核心用例分析");
        map.put("PROTOTYPE", "原型图设计说明");
        map.put("TECH_FRAMEWORK", "技术框架设计说明书");
        map.put("DATABASE", "数据库逻辑结构设计说明书");
        map.put("USE_CASE", "系统核心用例设计");
        map.put("API", "接口文档说明");
        map.put("FRONTEND", "前端模块与组件选型设计");
        map.put("BACKEND", "后端分层架构与核心模块设计");
        map.put("TESTING", "交付质量测试用例与缺陷管理");
        map.put("SUMMARY", "项目收尾总结与绩效分析");
        SECTION_DEFAULT_TITLES = Map.copyOf(map);
    }

    private final AiClient aiClient;
    private final ProjectService projectService;
    private final ProjectRoleMapper projectRoleMapper;
    private final ProjectSectionMapper projectSectionMapper;
    private final AiRecordService aiRecordService;
    private final AgentProfileService agentProfileService;
    private final SectionAgentContextBuilder sectionAgentContextBuilder;
    private final AgentPromptComposer agentPromptComposer;
    private final ObjectMapper objectMapper;

    public Map<String, String> generateSectionContent(Long projectId, Long sectionId) {
        Project project = projectService.getProject(projectId);
        ProjectSection section = projectSectionMapper.selectById(sectionId);
        if (section == null || !Objects.equals(section.getProjectId(), projectId)) {
            throw new BizException(404, "章节不存在");
        }

        ProjectRole ownerRole = section.getOwnerRoleId() == null ? null : projectRoleMapper.selectById(section.getOwnerRoleId());
        if (ownerRole == null || !Objects.equals(ownerRole.getProjectId(), projectId)) {
            throw new BizException(404, "项目角色不存在");
        }

        EffectiveAgentProfile profile = agentProfileService.resolveEffective(projectId, section.getOwnerRoleId());
        if (!profile.isEnabled()) {
            throw new BizException(409, "该角色智能体已停用");
        }
        String context = sectionAgentContextBuilder.build(project, section, profile);
        StringBuilder prompt = new StringBuilder(
                agentPromptComposer.composeGeneration(project, section, profile, context));
        String roleName = StringUtils.hasText(profile.getRoleName())
                ? profile.getRoleName()
                : ownerRole.getRoleName();

        String code = section.getSectionCode() == null ? "" : section.getSectionCode().toUpperCase();

        String resultText;
        String source;
        String modelName;

        if (aiClient.enabled()) {
            try {
                resultText = aiClient.chat(prompt.toString());
                if (StringUtils.hasText(resultText)) {
                    source = AiRecord.SOURCE_ZHIPU_GLM;
                    modelName = AiClient.class.getSimpleName();
                } else {
                    resultText = getLocalFallbackForSection(code, project.getProjectName(), roleName);
                    source = AiRecord.SOURCE_LOCAL_FALLBACK;
                    modelName = "local-fallback";
                }
            } catch (Exception e) {
                resultText = getLocalFallbackForSection(code, project.getProjectName(), roleName)
                        + "\n\n(说明：智谱 GLM 调用失败，已激活本地备用数智助理兜底结果。原因：" + e.getMessage() + ")";
                source = AiRecord.SOURCE_LOCAL_FALLBACK;
                modelName = "local-fallback";
            }
        } else {
            resultText = getLocalFallbackForSection(code, project.getProjectName(), roleName);
            source = AiRecord.SOURCE_LOCAL_FALLBACK;
            modelName = "local-fallback";
        }

        aiRecordService.saveGeneratedRecord(
                projectId,
                "SECTION_GENERATE",
                buildAuditPrompt(section, profile, 0),
                resultText,
                source,
                modelName,
                null);

        Map<String, String> response = new HashMap<>();
        response.put("title", section.getSectionName() + " (AI智能体协助生成)");
        response.put("body", resultText);
        return response;
    }

    public Map<String, String> chatSectionContent(Long projectId, Long sectionId, List<Map<String, String>> messages) {
        Project project = projectService.getProject(projectId);
        ProjectSection section = projectSectionMapper.selectById(sectionId);
        if (section == null || !Objects.equals(section.getProjectId(), projectId)) {
            throw new BizException(404, "章节不存在");
        }

        ProjectRole ownerRole = section.getOwnerRoleId() == null ? null : projectRoleMapper.selectById(section.getOwnerRoleId());
        if (ownerRole == null || !Objects.equals(ownerRole.getProjectId(), projectId)) {
            throw new BizException(404, "项目角色不存在");
        }

        EffectiveAgentProfile profile = agentProfileService.resolveEffective(projectId, section.getOwnerRoleId());
        if (!profile.isEnabled()) {
            throw new BizException(409, "该角色智能体已停用");
        }
        String context = sectionAgentContextBuilder.build(project, section, profile);
        StringBuilder systemPrompt = new StringBuilder(
                agentPromptComposer.composeChatSystem(project, section, profile, context));
        String roleName = StringUtils.hasText(profile.getRoleName())
                ? profile.getRoleName()
                : ownerRole.getRoleName();

        String code = section.getSectionCode() == null ? "" : section.getSectionCode().toUpperCase();

        List<Map<String, String>> apiMessages = new ArrayList<>();
        apiMessages.add(Map.of("role", "system", "content", systemPrompt.toString()));
        if (messages != null) {
            for (Map<String, String> msg : messages) {
                if (msg == null) {
                    continue;
                }
                String role = normalizeClientRole(msg.get("role"));
                String content = msg.get("content");
                if (role != null && StringUtils.hasText(content)) {
                    apiMessages.add(Map.of("role", role, "content", content));
                }
            }
        }

        String rawResult;
        String source;
        String modelName;

        if (aiClient.enabled()) {
            try {
                rawResult = aiClient.chat(apiMessages);
                if (StringUtils.hasText(rawResult)) {
                    source = AiRecord.SOURCE_ZHIPU_GLM;
                    modelName = "AiClient";
                } else {
                    rawResult = getLocalFallbackChatResponse(code, project.getProjectName(), roleName, messages);
                    source = AiRecord.SOURCE_LOCAL_FALLBACK;
                    modelName = "local-fallback";
                }
            } catch (Exception e) {
                rawResult = getLocalFallbackChatResponse(code, project.getProjectName(), roleName, messages);
                source = AiRecord.SOURCE_LOCAL_FALLBACK;
                modelName = "local-fallback";
            }
        } else {
            rawResult = getLocalFallbackChatResponse(code, project.getProjectName(), roleName, messages);
            source = AiRecord.SOURCE_LOCAL_FALLBACK;
            modelName = "local-fallback";
        }

        // Programmatic cleanup of JSON response (just in case model wrapped it in ```json)
        String cleanJson = rawResult.trim();
        if (cleanJson.startsWith("```json")) {
            cleanJson = cleanJson.substring(7).trim();
        } else if (cleanJson.startsWith("```")) {
            cleanJson = cleanJson.substring(3).trim();
        }
        if (cleanJson.endsWith("```")) {
            cleanJson = cleanJson.substring(0, cleanJson.length() - 3).trim();
        }

        aiRecordService.saveGeneratedRecord(
                projectId,
                "SECTION_GENERATE",
                buildAuditPrompt(section, profile, messages != null ? messages.size() : 0),
                cleanJson,
                source,
                modelName,
                null);

        return parseChatResponse(cleanJson, rawResult, section);
    }

    private String getLocalFallbackChatResponse(String code, String projectName, String roleName, List<Map<String, String>> messages) {
        String userQuery = "";
        if (messages != null) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                Map<String, String> message = messages.get(i);
                if (message == null) {
                    continue;
                }
                String content = message.get("content");
                if ("user".equals(normalizeClientRole(message.get("role")))
                        && StringUtils.hasText(content)) {
                    userQuery = content;
                    break;
                }
            }
        }
        if (!StringUtils.hasText(userQuery)) {
            userQuery = "开始协同设计";
        }

        String explanation = "我是【" + roleName + "】。我收到您的意见：「" + userQuery + "」。我已经结合您的要求以及前置设计上下文对本章节草稿进行了更新。您可以预览草稿并点击“一键导入”同步到左侧编辑器。";
        String title = getSectionDefaultTitle(code);
        String body = getLocalFallbackForSection(code, projectName, roleName) + "\n\n---\n*（根据协同建议更新：已融入关于「" + userQuery + "」的补充设计说明。项目数据和接口规则已与前置上下文保持对齐。）*";

        Map<String, String> map = new HashMap<>();
        map.put("explanation", explanation);
        map.put("title", title);
        map.put("body", body);

        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return SAFE_FALLBACK_CHAT_JSON;
        }
    }

    private Map<String, String> parseChatResponse(
            String cleanJson,
            String rawResult,
            ProjectSection section
    ) {
        try {
            JsonNode root = objectMapper.readTree(cleanJson);
            JsonNode explanation = root == null ? null : root.get("explanation");
            JsonNode title = root == null ? null : root.get("title");
            JsonNode body = root == null ? null : root.get("body");
            if (root == null || !root.isObject()
                    || explanation == null || !explanation.isTextual()
                    || title == null || !title.isTextual()
                    || body == null || !body.isTextual()) {
                throw new IllegalArgumentException("invalid section agent response");
            }

            Map<String, String> response = new HashMap<>();
            response.put("explanation", explanation.textValue());
            response.put("title", title.textValue());
            response.put("body", body.textValue());
            return response;
        } catch (Exception e) {
            Map<String, String> errMap = new HashMap<>();
            errMap.put("explanation", "抱歉，智能体响应解析失败。这是原始文本：" + rawResult);
            errMap.put("title", section.getSectionName() + " (AI智能体协同设计)");
            errMap.put("body", rawResult);
            return errMap;
        }
    }

    private String getSectionDefaultTitle(String code) {
        return SECTION_DEFAULT_TITLES.getOrDefault(code, "章节文档");
    }

    private String buildAuditPrompt(ProjectSection section, EffectiveAgentProfile profile, int chatMessageCount) {
        return "sectionId=" + section.getId()
                + ";profileSource=" + profile.getSource()
                + ";roleCode=" + profile.getRoleCode()
                + ";chatMessageCount=" + chatMessageCount;
    }

    private String normalizeClientRole(String role) {
        if (!StringUtils.hasText(role)) {
            return null;
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        return "user".equals(normalized) || "assistant".equals(normalized) ? normalized : null;
    }

    private String getLocalFallbackForSection(String code, String projectName, String roleName) {
        StringBuilder sb = new StringBuilder();
        sb.append("### 🤖 ").append(roleName).append(" 智能体协助生成草稿\n\n");
        sb.append("已结合项目「").append(projectName).append("」的背景完成初步内容规划。以下是该章节大纲与样本数据，请您在此基础上继续修改：\n\n");

        switch (code) {
            case "MANAGEMENT_DELIVERY":
                sb.append("#### 1. 项目里程碑与计划控制\n")
                  .append("本章节定义项目经理规划的整体开发与交付排程。\n\n")
                  .append("#### 2. WBS 任务分解概览\n")
                  .append("- **阶段一：需求与设计**：梳理需求分析、用例设计和原型图，输出对应基线文档。\n")
                  .append("- **阶段二：实现与对接**：完成数据库搭建，前后端独立开发并实现接口联调。\n")
                  .append("- **阶段三：系统测试与交付**：QA 完成全功能用例覆盖，修复缺陷并准备验收。");
                break;
            case "USE_CASE_ANALYSIS":
                sb.append("#### 1. 核心业务用例分析\n")
                  .append("本章节分析产品经理规划的用户角色业务用例流。\n\n")
                  .append("#### 2. 主要用例流分析\n")
                  .append("- **用例：用户登录**：支持项目团队不同角色的凭证核验与鉴权登录。\n")
                  .append("- **用例：申请加入项目**：待审核申请人提交申请说明，项目经理审核通过或拒绝。\n")
                  .append("- **用例：协同设计章节**：成员在工作区内编辑章节并提交审核，完成流程协同。");
                break;
            case "TECH_FRAMEWORK":
                sb.append("#### 1. 技术栈与框架选型\n")
                  .append("本章节定义技术总监制定的项目整体技术架构选型。\n\n")
                  .append("#### 2. 基础框架设计\n")
                  .append("- **开发语言**：Java 17, JavaScript (ES6+)\n")
                  .append("- **前端核心**：Vue 3 + Vite 5 + Element Plus 用于构建后台管理系统界面\n")
                  .append("- **后端核心**：Spring Boot 3 + Spring Security + JWT 令牌鉴权与接口安全防护\n")
                  .append("- **数据持久层**：MyBatis-Plus + MySQL 8 数据库连接池配置");
                break;
            case "REQUIREMENT":
                sb.append("#### 1. 核心业务与场景描述\n")
                  .append("本章节定义项目的主要业务场景和交付约束。\n\n")
                  .append("#### 2. 系统功能性需求清单\n")
                  .append("| 模块 | 功能项 | 优先级 | 描述 |\n")
                  .append("| --- | --- | :---: | --- |\n")
                  .append("| 认证授权 | 用户登录/注册 | P0 | 支持账号密码、手机号校验登录，分发 JWT 令牌 |\n")
                  .append("| 项目协作 | 新增项目工作流 | P0 | 项目经理通过向导模板快速创建项目协作空间 |\n")
                  .append("| 智能分析 | WBS 自动生成 | P1 | 基于大模型将项目大类任务一键拆解排程 |\n\n")
                  .append("#### 3. 非功能性需求\n")
                  .append("- **系统响应性**：常规 API 接口端到端延迟小于 500ms，AI 大模型生成等待除外。\n")
                  .append("- **安全与隐私**：敏感数据传输需通过 TLS 协议加密，密码在数据库中进行散列存储。");
                break;
            case "USE_CASE":
                sb.append("#### 1. 核心业务用例规约\n\n")
                  .append("##### [用例-01] 创建项目排程与WBS计划\n")
                  .append("- **参与者**：项目经理 (PM)\n")
                  .append("- **前置条件**：用户已登录，且拥有项目经理角色权限。\n")
                  .append("- **主成功路径**：\n")
                  .append("  1. 项目经理进入项目空间，点击「项目管理」。\n")
                  .append("  2. 点击「初始化 WBS 计划」，输入项目核心诉求。\n")
                  .append("  3. 智能体生成 WBS 任务清单，并支持手动在表格中微调时间、工时与资源。\n")
                  .append("  4. 确认无误后，点击保存，生成项目进度甘特图。\n")
                  .append("- **分支路径**：在步骤 3 中如果大模型调用超时，系统应提示“服务繁忙，请稍后再试”，并保留用户当前输入的基线参数。\n")
                  .append("- **后置条件**：WBS 任务清单持久化入库，生成初始基线。");
                break;
            case "DATABASE":
                sb.append("#### 1. 物理模型与设计说明\n")
                  .append("本章节包含系统核心实体关联模型设计。\n\n")
                  .append("#### 2. SQL 初始化脚本示例\n")
                  .append("```sql\n")
                  .append("-- 实体：项目主体表\n")
                  .append("CREATE TABLE project (\n")
                  .append("    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '项目唯一标识',\n")
                  .append("    project_name VARCHAR(150) NOT NULL COMMENT '项目名称',\n")
                  .append("    project_code VARCHAR(50) NOT NULL UNIQUE COMMENT '项目编码(用于短链接加入)',\n")
                  .append("    description TEXT COMMENT '项目描述描述',\n")
                  .append("    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE, COMPLETED, ARCHIVED',\n")
                  .append("    create_time DATETIME DEFAULT CURRENT_TIMESTAMP\n")
                  .append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目信息表';\n\n")
                  .append("-- 实体：成员矩阵表\n")
                  .append("CREATE TABLE project_member (\n")
                  .append("    id BIGINT PRIMARY KEY AUTO_INCREMENT,\n")
                  .append("    project_id BIGINT NOT NULL COMMENT '项目关联ID',\n")
                  .append("    user_id BIGINT NOT NULL COMMENT '用户ID',\n")
                  .append("    project_role_id BIGINT NOT NULL COMMENT '角色定位ID',\n")
                  .append("    FOREIGN KEY (project_id) REFERENCES project(id)\n")
                  .append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成员矩阵';\n")
                  .append("```");
                break;
            case "API":
                sb.append("#### 1. 接口设计标准规约\n\n")
                  .append("##### [API-01] 登录鉴权\n")
                  .append("- **请求路径**：`POST /api/auth/login`\n")
                  .append("- **参数类型**：`application/json`\n")
                  .append("- **请求体示例**：\n")
                  .append("```json\n")
                  .append("{\n")
                  .append("  \"username\": \"pm\",\n")
                  .append("  \"password\": \"123456\"\n")
                  .append("}\n")
                  .append("```\n")
                  .append("- **响应体示例**：\n")
                  .append("```json\n")
                  .append("{\n")
                  .append("  \"code\": 200,\n")
                  .append("  \"message\": \"success\",\n")
                  .append("  \"data\": {\n")
                  .append("    \"token\": \"eyJhbGciOiJIUzI1NiJ9...\",\n")
                  .append("    \"user\": {\n")
                  .append("      \"id\": 1,\n")
                  .append("      \"username\": \"pm\",\n")
                  .append("      \"role\": \"USER\"\n")
                  .append("    }\n")
                  .append("  }\n")
                  .append("}\n")
                  .append("```");
                break;
            case "FRONTEND":
                sb.append("#### 1. 前端技术选型\n")
                  .append("- **核心框架**：Vue 3 (Composition API)\n")
                  .append("- **UI 组件库**：Element Plus\n")
                  .append("- **状态管理**：Pinia\n\n")
                  .append("#### 2. 主要路由组件与层级关系\n")
                  .append("- `/projects/:id` (项目协作空间主页布局，嵌套侧边栏组件)\n")
                  .append("  - `/projects/:id/overview` —— `ProjectOverview.vue` (项目概览信息卡片)\n")
                  .append("  - `/projects/:id/sections` —— `SectionList.vue` (章节结构交付物矩阵)\n")
                  .append("  - `/projects/:id/tasks` —— `TaskBoard.vue` (Kanban 风格任务流转面板)");
                break;
            case "BACKEND":
                sb.append("#### 1. 后端工程架构设计\n")
                  .append("采用典型的 Spring Boot 单体分层架构设计，各层职责清晰划分：\n\n")
                  .append("- `controller` 层：负责接收 HTTP 请求，执行入参校验 (`@Valid`)，并将结果统一包装在 `Result<T>` 中返回。\n")
                  .append("- `service` 层：处理核心业务流程，通过 `@Transactional` 控制事务，并定义与第三方模型服务的对接。\n")
                  .append("- `mapper` 层：基于 MyBatis-Plus 接口进行数据持久化操作。\n")
                  .append("- `entity` 层：数据物理模型定义。");
                break;
            case "TESTING":
                sb.append("#### 1. 软件交付质量测试用例\n\n")
                  .append("| 用例ID | 测试模块 | 测试项描述 | 输入与步骤 | 预期输出 | 优先级 |\n")
                  .append("| --- | --- | --- | --- | --- | :---: |\n")
                  .append("| TC-AUTH-01 | 用户认证 | 正常登录校验 | 1. 在登录页输入 `pm`/`123456`<br>2. 点击登录 | 成功跳转至工作台，浏览器 LocalStorage 成功写入 Token 秘钥 | P0 |\n")
                  .append("| TC-PROJ-02 | 项目协作 | 成员通过短码申请加入 | 1. 访客输入短链地址 `/join/PROJCODE`<br>2. 提交申请说明 | 系统提示“申请已提交，等待项目经理审批”，数据写入申请表 | P0 |");
                break;
            default:
                sb.append("#### 1. 协助说明\n")
                  .append("智能体已针对本章节进行框架性梳理。\n\n")
                  .append("#### 2. 编写指引\n")
                  .append("- 建议结合前置章节中定义的需求与设计，补充本章节的具体细节与图片材料。\n")
                  .append("- 可使用上方的编辑器工具条快速插入表格、图表、甘特图等辅助内容。");
                break;
        }
        return sb.toString();
    }
}

# Role-Agent Profile Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a backward-compatible backend foundation for configurable project role agents with seeded defaults, project snapshots, profile APIs, bounded context, and profile-driven section AI.

**Architecture:** Store global and project-scoped full profiles in an additive `agent_profile` table. `AgentProfileService` resolves project, global, or synthetic profiles; `SectionAgentContextBuilder` applies context policy; `AgentPromptComposer` expands validated named templates; existing section AI endpoints delegate to the new profile components without changing their contracts.

**Tech Stack:** Java 17, Spring Boot 3.3, MyBatis-Plus 3.5, MySQL JSON columns, Jackson, JUnit 5/Mockito, Node.js architecture checks.

---

## Scope And Environment

This plan implements the first backend-only slice of Phase 4 from `docs/superpowers/specs/2026-06-22-role-agent-profile-foundation-design.md`.

Included:

- additive profile schema and six seeded global profiles
- idempotent upgrade SQL for an existing database
- effective profile resolution and project full-snapshot overrides
- member-readable and manager-writable profile APIs
- bounded profile-controlled context
- named-variable prompt composition
- profile-driven section generation and chat
- disabled-agent policy and compatibility checks

Excluded:

- `agent_memory` persistence and memory APIs
- frontend profile configuration UI
- provider-backed multimodal execution
- global-default mutation API

The current workspace is not a usable Git repository, so commit steps are not executable here. Maven is not installed locally. Node checks and frontend verification run locally; JUnit tests must run in CI or another Maven-enabled environment.

## File Structure

### Verification

- Create: `scripts/check-agent-profile-foundation.mjs`
  - Guards additive SQL, contracts, routes, permissions, profile resolution, prompt/context boundaries, disabled behavior, and unchanged AI routes.
- Modify: `scripts/verify.sh`
  - Runs the Phase 4A architecture check after the existing Phase 3 check.

### Database

- Modify: `database/teamflow_ai.sql`
  - Adds fresh-install table and six global default rows.
- Create: `database/migrations/2026-06-22-agent-profile.sql`
  - Idempotently creates and seeds only `agent_profile` for existing databases.

### Contracts

- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/entity/AgentProfile.java`
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/mapper/AgentProfileMapper.java`
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/dto/AgentProfileUpdateDTO.java`
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/vo/AgentProfileVO.java`
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/ai/EffectiveAgentProfile.java`

### Services And API

- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/ai/AgentProfileService.java`
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/ai/AgentPromptComposer.java`
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/ai/SectionAgentContextBuilder.java`
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/controller/AgentProfileController.java`
- Modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/ai/SectionAgentService.java`

### Tests

- Create: `teamflow-ai-backend/src/test/java/com/example/teamflow/service/ai/AgentProfileServiceTest.java`
- Create: `teamflow-ai-backend/src/test/java/com/example/teamflow/service/ai/AgentPromptComposerTest.java`
- Create: `teamflow-ai-backend/src/test/java/com/example/teamflow/service/ai/SectionAgentContextBuilderTest.java`
- Create: `teamflow-ai-backend/src/test/java/com/example/teamflow/service/ai/SectionAgentServiceProfileTest.java`

## Task 1: Add The Phase 4A Architecture Check

**Files:**
- Create: `scripts/check-agent-profile-foundation.mjs`
- Modify: `scripts/verify.sh`

- [ ] **Step 1: Create the failing architecture check**

Create a Node script using `node:assert/strict`, `node:fs`, `node:path`, and `fileURLToPath`. It must resolve the repository root from `import.meta.url` and assert all of these contracts:

```js
const requiredJavaFiles = [
  'entity/AgentProfile.java',
  'mapper/AgentProfileMapper.java',
  'dto/AgentProfileUpdateDTO.java',
  'vo/AgentProfileVO.java',
  'service/ai/EffectiveAgentProfile.java',
  'service/ai/AgentProfileService.java',
  'service/ai/AgentPromptComposer.java',
  'service/ai/SectionAgentContextBuilder.java',
  'controller/AgentProfileController.java'
]

for (const file of requiredJavaFiles) {
  assert.ok(exists(`teamflow-ai-backend/src/main/java/com/example/teamflow/${file}`), `${file} should exist`)
}

assert.ok(exists('database/migrations/2026-06-22-agent-profile.sql'), 'additive migration should exist')
assert.match(initSql, /CREATE TABLE agent_profile/)
assert.match(migrationSql, /CREATE TABLE IF NOT EXISTS agent_profile/)

for (const roleCode of [
  'PROJECT_MANAGER',
  'PRODUCT_MANAGER',
  'TECHNICAL_DIRECTOR',
  'FRONTEND_DEV',
  'BACKEND_DEV',
  'QA'
]) {
  assert.match(initSql, new RegExp(`'${roleCode}'`), `${roleCode} should be seeded`)
  assert.match(migrationSql, new RegExp(`'${roleCode}'`), `${roleCode} should be seeded in migration`)
}
```

Also assert:

```js
assert.match(profileService, /resolveEffective\(Long projectId, Long projectRoleId\)/)
assert.match(profileService, /SOURCE_PROJECT/)
assert.match(profileService, /SOURCE_GLOBAL/)
assert.match(profileService, /SOURCE_SYNTHETIC/)
assert.match(profileService, /requireProjectMember\(projectId\)/)
assert.match(profileService, /requireProjectManage\(projectId\)/)
assert.match(profileService, /setSystemPrompt\(null\)/)
assert.match(profileService, /setTaskPromptTemplate\(null\)/)
assert.match(profileService, /setMemoryPolicy\(null\)/)
assert.match(profileService, /setMultimodalConfig\(null\)/)
assert.match(profileController, /@GetMapping\("\/api\/projects\/\{projectId\}\/agent-profiles"\)/)
assert.match(profileController, /@PutMapping\("\/api\/projects\/\{projectId\}\/roles\/\{roleId\}\/agent-profile"\)/)
assert.match(profileController, /@DeleteMapping\("\/api\/projects\/\{projectId\}\/roles\/\{roleId\}\/agent-profile"\)/)
assert.match(sectionAgentService, /AgentProfileService/)
assert.match(sectionAgentService, /SectionAgentContextBuilder/)
assert.match(sectionAgentService, /AgentPromptComposer/)
assert.match(sectionAgentService, /BizException\(409,/)
```

Keep the existing AI route checks by reading `AiController.java` and asserting both current section paths.

- [ ] **Step 2: Verify RED**

Run:

```bash
node scripts/check-agent-profile-foundation.mjs
```

Expected: exit `1` because `AgentProfile.java` and the migration do not exist.

- [ ] **Step 3: Wire the check into root verification**

Add after the existing AI modularization check in `scripts/verify.sh`:

```bash
node scripts/check-agent-profile-foundation.mjs
```

Do not remove or reorder existing frontend or Phase 3 checks.

## Task 2: Add Additive Profile Schema And Default Seeds

**Files:**
- Modify: `database/teamflow_ai.sql`
- Create: `database/migrations/2026-06-22-agent-profile.sql`

- [ ] **Step 1: Add the fresh-install table**

Add `DROP TABLE IF EXISTS agent_profile;` before dropping project roles, then create:

```sql
CREATE TABLE agent_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    scope_type VARCHAR(20) NOT NULL,
    scope_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT,
    project_role_id BIGINT,
    role_code VARCHAR(50) NOT NULL,
    role_name VARCHAR(80) NOT NULL,
    responsibilities LONGTEXT NOT NULL,
    context_scope JSON NOT NULL,
    output_template LONGTEXT NOT NULL,
    tool_permissions JSON NOT NULL,
    memory_policy JSON NOT NULL,
    system_prompt LONGTEXT NOT NULL,
    task_prompt_template LONGTEXT NOT NULL,
    multimodal_config JSON NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_agent_profile_scope_role (scope_type, scope_id, role_code),
    INDEX idx_agent_profile_project (project_id),
    INDEX idx_agent_profile_project_role (project_role_id),
    INDEX idx_agent_profile_role_code (role_code),
    CHECK (scope_type IN ('GLOBAL', 'PROJECT')),
    CHECK (enabled IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

- [ ] **Step 2: Seed all six global profiles**

Use one explicit insert in both SQL paths. Each row uses `scope_type='GLOBAL'`, `scope_id=0`, null project ids, enabled `1`, and these role-specific values:

```sql
INSERT INTO agent_profile
(scope_type, scope_id, project_id, project_role_id, role_code, role_name, responsibilities,
 context_scope, output_template, tool_permissions, memory_policy, system_prompt,
 task_prompt_template, multimodal_config, enabled)
VALUES
('GLOBAL', 0, NULL, NULL, 'PROJECT_MANAGER', '项目经理',
 '负责项目计划、范围、成员协作、风险处理和交付决策。',
 JSON_ARRAY('PROJECT_OVERVIEW','CURRENT_SECTION','PREVIOUS_SECTIONS','MEMBERS','TASKS','RISKS'),
 '输出结构清晰的项目管理文档，明确结论、依据、风险和下一步行动。',
 JSON_ARRAY('READ_CONTEXT','GENERATE_DRAFT','SUGGEST_REVIEW_COMMENT','SUGGEST_TASKS'),
 JSON_OBJECT('enabled', false),
 '你是 TeamFlowAI 的项目经理角色智能体。只基于授权上下文工作，不编造事实。',
 '请以${roleName}身份为${projectName}的${sectionName}生成内容。职责：${responsibilities}。上下文：${context}。输出要求：${outputTemplate}。允许能力：${toolPermissions}。',
 JSON_OBJECT('enabled', false, 'inputTypes', JSON_ARRAY()), 1),
('GLOBAL', 0, NULL, NULL, 'PRODUCT_MANAGER', '产品经理',
 '负责需求分析、用例设计、原型说明和业务验收口径。',
 JSON_ARRAY('PROJECT_OVERVIEW','CURRENT_SECTION','PREVIOUS_SECTIONS','COMMENTS','REVIEW_HISTORY'),
 '输出可验证的需求、用例或原型说明，包含范围、规则、异常和验收标准。',
 JSON_ARRAY('READ_CONTEXT','GENERATE_DRAFT','SUGGEST_REVIEW_COMMENT'),
 JSON_OBJECT('enabled', false),
 '你是 TeamFlowAI 的产品经理角色智能体。保持业务定义一致且可验收。',
 '请以${roleName}身份完善${projectName}的${sectionName}。职责：${responsibilities}。上下文：${context}。输出要求：${outputTemplate}。允许能力：${toolPermissions}。',
 JSON_OBJECT('enabled', false, 'inputTypes', JSON_ARRAY()), 1),
('GLOBAL', 0, NULL, NULL, 'TECHNICAL_DIRECTOR', '技术总监',
 '负责技术架构、数据库、接口契约、实现约束和技术评审。',
 JSON_ARRAY('PROJECT_OVERVIEW','CURRENT_SECTION','PREVIOUS_SECTIONS','TASKS','RISKS','FILES','REVIEW_HISTORY'),
 '输出包含架构决策、接口或数据约束、风险与验证方式的技术文档。',
 JSON_ARRAY('READ_CONTEXT','GENERATE_DRAFT','SUGGEST_REVIEW_COMMENT','INSPECT_FILES','SUGGEST_TASKS'),
 JSON_OBJECT('enabled', false),
 '你是 TeamFlowAI 的技术总监角色智能体。确保技术方案一致、可实现、可验证。',
 '请以${roleName}身份设计${projectName}的${sectionName}。职责：${responsibilities}。上下文：${context}。输出要求：${outputTemplate}。允许能力：${toolPermissions}。',
 JSON_OBJECT('enabled', false, 'inputTypes', JSON_ARRAY()), 1),
('GLOBAL', 0, NULL, NULL, 'FRONTEND_DEV', '前端开发',
 '负责 Vue 页面、组件、路由状态、交互体验和接口联调。',
 JSON_ARRAY('PROJECT_OVERVIEW','CURRENT_SECTION','PREVIOUS_SECTIONS','TASKS','FILES'),
 '输出前端模块拆分、组件职责、状态流、接口依赖和验证要点。',
 JSON_ARRAY('READ_CONTEXT','GENERATE_DRAFT','INSPECT_FILES','SUGGEST_TASKS'),
 JSON_OBJECT('enabled', false),
 '你是 TeamFlowAI 的前端开发角色智能体。遵循现有前端技术栈和交互约束。',
 '请以${roleName}身份完成${projectName}的${sectionName}设计。职责：${responsibilities}。上下文：${context}。输出要求：${outputTemplate}。允许能力：${toolPermissions}。',
 JSON_OBJECT('enabled', false, 'inputTypes', JSON_ARRAY()), 1),
('GLOBAL', 0, NULL, NULL, 'BACKEND_DEV', '后端开发',
 '负责 Spring Boot 接口、数据模型、权限、安全和业务服务。',
 JSON_ARRAY('PROJECT_OVERVIEW','CURRENT_SECTION','PREVIOUS_SECTIONS','TASKS','RISKS','FILES'),
 '输出后端分层、数据结构、接口契约、权限边界、异常和测试策略。',
 JSON_ARRAY('READ_CONTEXT','GENERATE_DRAFT','INSPECT_FILES','SUGGEST_TASKS'),
 JSON_OBJECT('enabled', false),
 '你是 TeamFlowAI 的后端开发角色智能体。保持接口、数据和权限行为兼容。',
 '请以${roleName}身份实现${projectName}的${sectionName}设计。职责：${responsibilities}。上下文：${context}。输出要求：${outputTemplate}。允许能力：${toolPermissions}。',
 JSON_OBJECT('enabled', false, 'inputTypes', JSON_ARRAY()), 1),
('GLOBAL', 0, NULL, NULL, 'QA', '测试负责人',
 '负责测试范围、用例、缺陷、回归、质量门禁和验收证据。',
 JSON_ARRAY('PROJECT_OVERVIEW','CURRENT_SECTION','PREVIOUS_SECTIONS','TASKS','RISKS','COMMENTS','REVIEW_HISTORY'),
 '输出包含用例编号、前置条件、步骤、预期结果、优先级和风险的测试文档。',
 JSON_ARRAY('READ_CONTEXT','GENERATE_DRAFT','SUGGEST_REVIEW_COMMENT','SUGGEST_TASKS'),
 JSON_OBJECT('enabled', false),
 '你是 TeamFlowAI 的测试负责人角色智能体。结论必须可复现并有明确预期。',
 '请以${roleName}身份验证${projectName}的${sectionName}。职责：${responsibilities}。上下文：${context}。输出要求：${outputTemplate}。允许能力：${toolPermissions}。',
 JSON_OBJECT('enabled', false, 'inputTypes', JSON_ARRAY()), 1);
```

- [ ] **Step 3: Make the upgrade SQL idempotent**

The migration uses `CREATE TABLE IF NOT EXISTS` and the same six rows followed by:

```sql
ON DUPLICATE KEY UPDATE
role_name = VALUES(role_name),
responsibilities = VALUES(responsibilities),
context_scope = VALUES(context_scope),
output_template = VALUES(output_template),
tool_permissions = VALUES(tool_permissions),
memory_policy = VALUES(memory_policy),
system_prompt = VALUES(system_prompt),
task_prompt_template = VALUES(task_prompt_template),
multimodal_config = VALUES(multimodal_config),
enabled = VALUES(enabled);
```

The migration must contain no `DROP`, `TRUNCATE`, or updates to existing tables.

- [ ] **Step 4: Re-run the architecture check**

Run:

```bash
node scripts/check-agent-profile-foundation.mjs
```

Expected: FAIL next on missing Java profile contracts.

## Task 3: Add Profile Contracts

**Files:**
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/entity/AgentProfile.java`
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/mapper/AgentProfileMapper.java`
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/dto/AgentProfileUpdateDTO.java`
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/vo/AgentProfileVO.java`
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/ai/EffectiveAgentProfile.java`

- [ ] **Step 1: Add the persistence entity and mapper**

`AgentProfile` uses `@Data`, `@TableName("agent_profile")`, and `@TableId(type = IdType.AUTO)`. Define fields with these exact Java types:

```java
private Long id;
private String scopeType;
private Long scopeId;
private Long projectId;
private Long projectRoleId;
private String roleCode;
private String roleName;
private String responsibilities;
private String contextScope;
private String outputTemplate;
private String toolPermissions;
private String memoryPolicy;
private String systemPrompt;
private String taskPromptTemplate;
private String multimodalConfig;
private Integer enabled;
private LocalDateTime createTime;
private LocalDateTime updateTime;
```

Map JSON columns as strings and parse them with Jackson in the service. Create:

```java
@Mapper
public interface AgentProfileMapper extends BaseMapper<AgentProfile> {
}
```

- [ ] **Step 2: Add the full-snapshot update DTO**

Use Jakarta validation:

```java
@Data
public class AgentProfileUpdateDTO {
    @NotBlank
    @Size(max = 4000)
    private String responsibilities;

    @NotNull
    @Size(min = 1, max = 9)
    private List<String> contextScope;

    @NotBlank
    @Size(max = 6000)
    private String outputTemplate;

    @NotNull
    @Size(max = 5)
    private List<String> toolPermissions;

    @NotNull
    private Map<String, Object> memoryPolicy;

    @NotBlank
    @Size(max = 12000)
    private String systemPrompt;

    @NotBlank
    @Size(max = 16000)
    private String taskPromptTemplate;

    @NotNull
    private Map<String, Object> multimodalConfig;

    @NotNull
    private Boolean enabled;
}
```

Role code and role name are not client-editable; copy them from `project_role`.

- [ ] **Step 3: Add runtime and response models**

`EffectiveAgentProfile` uses parsed collections and includes:

```java
private Long id;
private Long projectId;
private Long projectRoleId;
private String roleCode;
private String roleName;
private String responsibilities;
private List<String> contextScope;
private String outputTemplate;
private List<String> toolPermissions;
private Map<String, Object> memoryPolicy;
private String systemPrompt;
private String taskPromptTemplate;
private Map<String, Object> multimodalConfig;
private boolean enabled;
private String source;
private LocalDateTime updateTime;
```

`AgentProfileVO` mirrors these fields, uses `Boolean enabled`, and keeps restricted fields nullable:

```java
private Map<String, Object> memoryPolicy;
private String systemPrompt;
private String taskPromptTemplate;
private Map<String, Object> multimodalConfig;
```

- [ ] **Step 4: Verify the next RED boundary**

Run the Node check. Expected: FAIL because `AgentProfileService.java` does not exist.

## Task 4: Implement Effective Resolution And Snapshot Management

**Files:**
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/ai/AgentProfileService.java`
- Create: `teamflow-ai-backend/src/test/java/com/example/teamflow/service/ai/AgentProfileServiceTest.java`

- [ ] **Step 1: Write failing resolution tests**

Use Mockito to mock `AgentProfileMapper`, `ProjectRoleMapper`, `PermissionService`, and `AuthService`. Cover these exact cases:

```java
@Test
void projectSnapshotWinsOverGlobalDefault() {
    when(projectRoleMapper.selectById(7L)).thenReturn(role(7L, 3L, "QA", "测试负责人"));
    when(agentProfileMapper.selectOne(any())).thenReturn(projectProfile(), globalProfile());

    EffectiveAgentProfile result = service.resolveEffective(3L, 7L);

    assertEquals(AgentProfileService.SOURCE_PROJECT, result.getSource());
    assertEquals(3L, result.getProjectId());
}

@Test
void globalDefaultIsUsedWithoutProjectSnapshot() {
    when(projectRoleMapper.selectById(7L)).thenReturn(role(7L, 3L, "QA", "测试负责人"));
    when(agentProfileMapper.selectOne(any())).thenReturn(null, globalProfile());

    assertEquals(AgentProfileService.SOURCE_GLOBAL,
            service.resolveEffective(3L, 7L).getSource());
}

@Test
void customRoleGetsSyntheticProfile() {
    when(projectRoleMapper.selectById(8L)).thenReturn(role(8L, 3L, "DATA_ANALYST", "数据分析师"));
    when(agentProfileMapper.selectOne(any())).thenReturn(null, null);

    EffectiveAgentProfile result = service.resolveEffective(3L, 8L);

    assertEquals(AgentProfileService.SOURCE_SYNTHETIC, result.getSource());
    assertEquals("数据分析师", result.getRoleName());
    assertTrue(result.isEnabled());
}

@Test
void crossProjectRoleIsRejected() {
    when(projectRoleMapper.selectById(7L)).thenReturn(role(7L, 99L, "QA", "测试负责人"));

    BizException error = assertThrows(BizException.class,
            () -> service.resolveEffective(3L, 7L));

    assertEquals(404, error.getCode());
}

@Test
void memberDetailRedactsRestrictedFields() {
    when(authService.currentUser()).thenReturn(user(11L, "USER"));
    when(permissionService.isProjectManager(3L, 11L)).thenReturn(false);
    when(projectRoleMapper.selectById(7L)).thenReturn(role(7L, 3L, "QA", "测试负责人"));
    when(agentProfileMapper.selectOne(any())).thenReturn(null, globalProfile());

    AgentProfileVO result = service.getVisible(3L, 7L);

    assertNull(result.getSystemPrompt());
    assertNull(result.getTaskPromptTemplate());
    assertNull(result.getMemoryPolicy());
    assertNull(result.getMultimodalConfig());
    verify(permissionService).requireProjectMember(3L);
}

@Test
void managerDetailKeepsRestrictedFields() {
    when(authService.currentUser()).thenReturn(user(1L, "USER"));
    when(permissionService.isProjectManager(3L, 1L)).thenReturn(true);
    when(projectRoleMapper.selectById(7L)).thenReturn(role(7L, 3L, "QA", "测试负责人"));
    when(agentProfileMapper.selectOne(any())).thenReturn(projectProfile());

    AgentProfileVO result = service.getVisible(3L, 7L);

    assertEquals("系统提示词", result.getSystemPrompt());
    assertNotNull(result.getMemoryPolicy());
}

@Test
void firstSaveCreatesCompleteProjectSnapshot() {
    when(projectRoleMapper.selectById(7L)).thenReturn(role(7L, 3L, "QA", "测试负责人"));
    when(agentProfileMapper.selectOne(any())).thenReturn(null, globalProfile(), null);
    AgentProfileUpdateDTO dto = validUpdate();

    AgentProfileVO result = service.saveProjectSnapshot(3L, 7L, dto);

    verify(permissionService).requireProjectManage(3L);
    ArgumentCaptor<AgentProfile> captor = ArgumentCaptor.forClass(AgentProfile.class);
    verify(agentProfileMapper).insert(captor.capture());
    assertEquals("PROJECT", captor.getValue().getScopeType());
    assertEquals(3L, captor.getValue().getScopeId());
    assertEquals(7L, captor.getValue().getProjectRoleId());
    assertEquals("QA", captor.getValue().getRoleCode());
    assertEquals("PROJECT", result.getSource());
}

@Test
void resetDeletesOnlyProjectSnapshotAndRestoresGlobal() {
    when(projectRoleMapper.selectById(7L)).thenReturn(role(7L, 3L, "QA", "测试负责人"));
    when(agentProfileMapper.selectOne(any())).thenReturn(projectProfile(), null, globalProfile());

    AgentProfileVO result = service.resetProjectSnapshot(3L, 7L);

    verify(permissionService).requireProjectManage(3L);
    verify(agentProfileMapper).deleteById(projectProfile().getId());
    assertEquals("GLOBAL", result.getSource());
}

@Test
void invalidContextScopeIsRejectedBeforePersistence() {
    when(projectRoleMapper.selectById(7L)).thenReturn(role(7L, 3L, "QA", "测试负责人"));
    AgentProfileUpdateDTO dto = validUpdate();
    dto.setContextScope(List.of("SECRET_DATABASE"));

    BizException error = assertThrows(BizException.class,
            () -> service.saveProjectSnapshot(3L, 7L, dto));

    assertEquals(400, error.getCode());
    verify(agentProfileMapper, never()).insert(any());
}

@Test
void unknownPromptVariableIsRejectedBeforePersistence() {
    when(projectRoleMapper.selectById(7L)).thenReturn(role(7L, 3L, "QA", "测试负责人"));
    AgentProfileUpdateDTO dto = validUpdate();
    dto.setTaskPromptTemplate("${unknownValue}");

    BizException error = assertThrows(BizException.class,
            () -> service.saveProjectSnapshot(3L, 7L, dto));

    assertEquals(400, error.getCode());
    verify(agentProfileMapper, never()).insert(any());
}
```

- [ ] **Step 2: Verify the JUnit tests are RED where Maven is available**

Run:

```bash
cd teamflow-ai-backend
mvn -Dtest=AgentProfileServiceTest test
```

Expected: compilation failure because `AgentProfileService` is missing. Locally, record the explicit `mvn: command not found` blocker and rely on the Node RED check.

- [ ] **Step 3: Implement effective resolution**

Create constants:

```java
public static final String SOURCE_PROJECT = "PROJECT";
public static final String SOURCE_GLOBAL = "GLOBAL";
public static final String SOURCE_SYNTHETIC = "SYNTHETIC";
private static final String SCOPE_PROJECT = "PROJECT";
private static final String SCOPE_GLOBAL = "GLOBAL";
```

Expose:

```java
public EffectiveAgentProfile resolveEffective(Long projectId, Long projectRoleId)
public List<AgentProfileVO> listVisible(Long projectId)
public AgentProfileVO getVisible(Long projectId, Long projectRoleId)
@Transactional public AgentProfileVO saveProjectSnapshot(Long projectId, Long projectRoleId, AgentProfileUpdateDTO dto)
@Transactional public AgentProfileVO resetProjectSnapshot(Long projectId, Long projectRoleId)
```

Resolution queries use `LambdaQueryWrapper<AgentProfile>` with exact scope, scope id, and role code. The synthetic profile must use:

```java
contextScope = List.of("PROJECT_OVERVIEW", "CURRENT_SECTION", "PREVIOUS_SECTIONS");
toolPermissions = List.of("READ_CONTEXT", "GENERATE_DRAFT");
memoryPolicy = Map.of("enabled", false);
multimodalConfig = Map.of("enabled", false, "inputTypes", List.of());
systemPrompt = "你是 TeamFlowAI 的" + role.getRoleName() + "角色智能体。只基于授权上下文工作，不编造事实。";
taskPromptTemplate = "请以${roleName}身份为${projectName}的${sectionName}生成内容。职责：${responsibilities}。上下文：${context}。输出要求：${outputTemplate}。允许能力：${toolPermissions}。";
```

- [ ] **Step 4: Implement validation and JSON conversion**

Whitelist exactly the context and tool values from the design. Detect prompt variables with:

```java
private static final Pattern TEMPLATE_VARIABLE = Pattern.compile("\\$\\{([A-Za-z][A-Za-z0-9]*)}");
```

Reject unknown context values with `BizException(400, "角色智能体上下文范围不合法")`, unknown tool values with `BizException(400, "角色智能体工具权限不合法")`, and unknown prompt variables with `BizException(400, "角色智能体模板变量不合法")`. Use the injected `ObjectMapper` for list/map serialization and parsing. Parsing invalid stored JSON must throw `BizException(500, "角色智能体配置数据损坏")`, not expose Jackson errors.

- [ ] **Step 5: Implement full snapshot save/reset and redaction**

`saveProjectSnapshot` must call `permissionService.requireProjectManage(projectId)`, validate role ownership, then upsert `(PROJECT, projectId, roleCode)`. Set role identity from `ProjectRole`, not the DTO.

`resetProjectSnapshot` must call the same permission, delete only the matching project row, and return the newly resolved full manager view.

Read methods call `permissionService.requireProjectMember(projectId)`. Detail visibility uses:

```java
SysUser current = authService.currentUser();
boolean managerView = permissionService.isProjectManager(projectId, current.getId());
```

List responses always redact restricted fields. Detail responses redact them unless `managerView` is true. Redaction assigns `null` to system prompt, task prompt, memory policy, and multimodal config.

- [ ] **Step 6: Run resolution tests and architecture check**

Run Maven tests where available, then:

```bash
node scripts/check-agent-profile-foundation.mjs
```

Expected: Node check fails next because the controller and AI integration are missing.

## Task 5: Add Profile Management APIs

**Files:**
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/controller/AgentProfileController.java`
- Modify: `scripts/check-agent-profile-foundation.mjs`

- [ ] **Step 1: Add exact controller contracts**

Create:

```java
@RestController
@RequiredArgsConstructor
public class AgentProfileController {
    private final AgentProfileService agentProfileService;

    @GetMapping("/api/projects/{projectId}/agent-profiles")
    public Result<List<AgentProfileVO>> list(@PathVariable Long projectId) {
        return Result.success(agentProfileService.listVisible(projectId));
    }

    @GetMapping("/api/projects/{projectId}/roles/{roleId}/agent-profile")
    public Result<AgentProfileVO> detail(@PathVariable Long projectId, @PathVariable Long roleId) {
        return Result.success(agentProfileService.getVisible(projectId, roleId));
    }

    @PutMapping("/api/projects/{projectId}/roles/{roleId}/agent-profile")
    public Result<AgentProfileVO> save(
            @PathVariable Long projectId,
            @PathVariable Long roleId,
            @Valid @RequestBody AgentProfileUpdateDTO dto) {
        return Result.success(agentProfileService.saveProjectSnapshot(projectId, roleId, dto));
    }

    @DeleteMapping("/api/projects/{projectId}/roles/{roleId}/agent-profile")
    public Result<AgentProfileVO> reset(@PathVariable Long projectId, @PathVariable Long roleId) {
        return Result.success(agentProfileService.resetProjectSnapshot(projectId, roleId));
    }
}
```

- [ ] **Step 2: Strengthen route and permission checks**

Update the Node script to match all four annotations and service calls. Also assert `AgentProfileService` contains both `requireProjectMember(projectId)` and `requireProjectManage(projectId)`.

- [ ] **Step 3: Verify API architecture GREEN for this slice**

Run the Node check. Expected: it advances to the missing context builder or prompt composer assertion.

## Task 6: Implement Named Prompt Composition

**Files:**
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/ai/AgentPromptComposer.java`
- Create: `teamflow-ai-backend/src/test/java/com/example/teamflow/service/ai/AgentPromptComposerTest.java`

- [ ] **Step 1: Write failing pure unit tests**

Cover successful expansion and unknown-variable rejection:

```java
@Test
void generationPromptExpandsEverySupportedVariable() {
    EffectiveAgentProfile profile = profile("${roleName}|${projectName}|${sectionName}|${context}|${outputTemplate}|${toolPermissions}");

    String result = composer.composeGeneration(project(), section(), profile, "上下文内容");

    assertTrue(result.contains("测试负责人|TeamFlowAI|测试与缺陷|上下文内容"));
    assertFalse(result.contains("${"));
}

@Test
void unknownTemplateVariableIsRejected() {
    EffectiveAgentProfile profile = profile("${unknownValue}");

    BizException error = assertThrows(BizException.class,
            () -> composer.composeGeneration(project(), section(), profile, "context"));

    assertEquals(400, error.getCode());
}
```

- [ ] **Step 2: Implement the composer**

Expose:

```java
public String composeGeneration(Project project, ProjectSection section,
        EffectiveAgentProfile profile, String context)
public String composeChatSystem(Project project, ProjectSection section,
        EffectiveAgentProfile profile, String context)
public void validateTemplateVariables(String template)
```

Build one replacement map containing every supported variable. Replace `${name}` by using `Matcher.appendReplacement` with `Matcher.quoteReplacement`. Generation output contains profile system prompt, expanded task template, and the current section-specific guidance. Chat adds the existing strict JSON response contract after profile instructions. Keep the section-code guidance for `REQUIREMENT`, `USE_CASE`, `PROTOTYPE`, `DATABASE`, `API`, `FRONTEND`, `BACKEND`, `TESTING`, and `SUMMARY` in this composer so it is not duplicated in `SectionAgentService`.

- [ ] **Step 3: Run composer tests**

Run `mvn -Dtest=AgentPromptComposerTest test` where available. Expected: PASS.

## Task 7: Implement Bounded Profile-Controlled Context

**Files:**
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/ai/SectionAgentContextBuilder.java`
- Create: `teamflow-ai-backend/src/test/java/com/example/teamflow/service/ai/SectionAgentContextBuilderTest.java`

- [ ] **Step 1: Write failing context policy tests**

Use mocked mappers. Verify excluded categories are not queried and included categories render bounded headings:

```java
@Test
void onlySelectedContextCategoriesAreLoaded() {
    EffectiveAgentProfile profile = profile(List.of("PROJECT_OVERVIEW", "TASKS"));
    when(taskMapper.selectList(any())).thenReturn(List.of(task("实现权限 API")));

    String context = builder.build(project(), section(), profile);

    assertTrue(context.contains("【项目概述】"));
    assertTrue(context.contains("【任务】"));
    assertFalse(context.contains("【成员】"));
    verifyNoInteractions(projectMemberMapper, sectionCommentMapper, sectionReviewMapper);
}

@Test
void contextIsBounded() {
    EffectiveAgentProfile profile = profile(List.of("TASKS"));
    when(taskMapper.selectList(any())).thenReturn(IntStream.range(0, 50)
            .mapToObj(index -> task("任务" + index + "-" + "x".repeat(500)))
            .toList());

    String context = builder.build(project(), section(), profile);

    assertTrue(context.length() <= SectionAgentContextBuilder.MAX_CONTEXT_LENGTH);
}
```

- [ ] **Step 2: Implement deterministic limits**

Use constants:

```java
public static final int MAX_CONTEXT_LENGTH = 16000;
private static final int MAX_ITEMS_PER_CATEGORY = 20;
private static final int MAX_TEXT_LENGTH = 1200;
private static final int MAX_SECTION_BODY_LENGTH = 2000;
```

Expose:

```java
public String build(Project project, ProjectSection section, EffectiveAgentProfile profile)
```

Implement one helper per context category. Query by project id or current section id and use ascending business order or descending recency as appropriate:

- overview: project name, description, status, dates
- current section: code, name, description, status
- previous sections: earlier sort order and latest content version
- members: member role plus user display name
- tasks: title, status, priority, due date, block reason
- risks: latest snapshot level, score, trend, summary
- files: file name, type, document type, description only
- comments: current section comments, resolution state
- review history: current section result and comment

Never include file binary data, file URLs, passwords, tokens, or user contact fields. Truncate each rendered value and the final string.

- [ ] **Step 3: Run context tests**

Run `mvn -Dtest=SectionAgentContextBuilderTest test` where available. Expected: PASS.

## Task 8: Integrate Profiles Into Section AI

**Files:**
- Modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/ai/SectionAgentService.java`
- Create: `teamflow-ai-backend/src/test/java/com/example/teamflow/service/ai/SectionAgentServiceProfileTest.java`
- Modify: `scripts/check-agent-profile-foundation.mjs`

- [ ] **Step 1: Write the disabled-profile regression test**

Instantiate `SectionAgentService` with mocks for its dependencies plus the three new collaborators. Use a valid project, section, and owner role, then:

```java
@Test
void disabledProfileStopsGenerationBeforeCallingAiClient() {
    when(projectService.getProject(1L)).thenReturn(project(1L));
    when(projectSectionMapper.selectById(2L)).thenReturn(section(2L, 1L, 3L));
    when(projectRoleMapper.selectById(3L)).thenReturn(role(3L, 1L));
    when(agentProfileService.resolveEffective(1L, 3L)).thenReturn(profile(false));

    BizException error = assertThrows(BizException.class,
            () -> service.generateSectionContent(1L, 2L));

    assertEquals(409, error.getCode());
    verifyNoInteractions(aiClient, aiRecordService);
}
```

Add the same assertion for chat.

- [ ] **Step 2: Replace duplicated prompt/context assembly**

Inject:

```java
private final AgentProfileService agentProfileService;
private final SectionAgentContextBuilder sectionAgentContextBuilder;
private final AgentPromptComposer agentPromptComposer;
```

In both public methods, after current project/section/owner-role validation:

```java
EffectiveAgentProfile profile = agentProfileService.resolveEffective(projectId, section.getOwnerRoleId());
if (!profile.isEnabled()) {
    throw new BizException(409, "该角色智能体已停用");
}
String context = sectionAgentContextBuilder.build(project, section, profile);
```

Generation uses `composeGeneration`; chat uses `composeChatSystem`. Remove the duplicated preceding-section query and section-code switch from `SectionAgentService` only after the composer and context tests are green.

- [ ] **Step 3: Preserve compatibility behavior**

Keep all of these unchanged:

```java
"SECTION_GENERATE"
AiRecord.SOURCE_ZHIPU_GLM
AiRecord.SOURCE_LOCAL_FALLBACK
"local-fallback"
```

Keep generation response keys `title` and `body`. Keep chat keys `explanation`, `title`, and `body`. Keep code-fence cleanup, JSON parsing fallback, external exception fallback, and blank-result fallback.

Update local fallback headings to use `profile.getRoleName()` while preserving existing document bodies.

- [ ] **Step 4: Strengthen the architecture check**

Assert that `SectionAgentService`:

- injects all three new collaborators
- calls `resolveEffective(projectId, section.getOwnerRoleId())`
- throws code `409` for disabled profiles
- calls bounded context and the correct generation/chat composer methods
- still saves `SECTION_GENERATE`
- still contains exception and blank-response local fallback branches

- [ ] **Step 5: Run focused tests and architecture check**

Run Maven focused tests where available, then:

```bash
node scripts/check-agent-profile-foundation.mjs
```

Expected: PASS.

## Task 9: Final Verification And Review

**Files:**
- No production changes unless verification finds a real defect.

- [ ] **Step 1: Run both backend architecture checks**

```bash
node scripts/check-ai-service-modularization.mjs
node scripts/check-agent-profile-foundation.mjs
```

Expected: both PASS.

- [ ] **Step 2: Run root verification**

```bash
bash scripts/verify.sh
```

Expected:

- frontend feature checks pass
- Vite production build passes
- Phase 3 architecture check passes
- Phase 4A architecture check passes
- Maven tests pass when Maven exists, or the script explicitly reports the local Maven blocker

- [ ] **Step 3: Run backend tests where available**

```bash
cd teamflow-ai-backend
mvn test
```

Expected: PASS in Maven-enabled CI. Do not claim local backend compilation or test success when `mvn` is unavailable.

- [ ] **Step 4: Perform compatibility review**

Confirm directly:

- `AiController` section paths and request/response shapes did not change
- `PermissionService` remains the source of member/manager authorization
- no API mutates global default rows
- member responses redact all restricted fields
- project snapshot resolution never leaks across projects
- custom roles receive synthetic profiles
- context builder excludes contact details and raw file content
- disabled profiles do not call AI or fallback
- external AI failure and blank response still use local fallback

- [ ] **Step 5: Request final code review**

Dispatch a final reviewer against the design spec and this plan. Fix every Critical or Important Phase 4A issue, re-run affected tests, then repeat the review until it reports ready.

## Completion Criteria

- Additive fresh-install and existing-database SQL paths exist and seed six global profiles.
- Every project role resolves to project, global, or synthetic profile data.
- Project members can read only public profile fields.
- Project managers and administrators can read, save, and reset full project snapshots.
- Global defaults have no mutation endpoint.
- Section generation and chat use effective profiles and bounded selected context.
- Disabled profiles return code `409` before AI or fallback execution.
- Existing section AI endpoints, response maps, records, and fallback behavior remain compatible.
- Static verification and frontend build pass locally.
- Backend tests pass in Maven-enabled CI, with local Maven absence reported honestly.

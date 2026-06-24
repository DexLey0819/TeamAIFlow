# TeamFlowAI Full Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild TeamFlowAI from the approved requirement document into a template-driven intelligent software project collaboration platform using Zhipu GLM.

**Architecture:** Replace the old MVP domain with a new template/project-role/project-section workflow while preserving useful infrastructure such as JWT, `Result`, exception handling, Axios, Vue 3, and Element Plus. Implement the backend first so the frontend can consume stable REST APIs. Keep each phase buildable and verify backend compile plus frontend build before completion.

**Tech Stack:** Java 17, Spring Boot 3.3.5, Spring Security, JWT, MyBatis-Plus, MySQL 8, Vue 3, Vite, Element Plus, Pinia, Axios, ECharts, Zhipu BigModel GLM chat completions.

---

## Scope Note

The approved design covers multiple subsystems. This is a master implementation plan split into independently verifiable slices. Each task should leave the project closer to a running system; do not start frontend pages for an API until the backend endpoint contracts for that page exist.

The project is not a git repository. Where the normal workflow says "commit", record the checkpoint by running the verification command and noting changed files in the task summary instead.

## File Structure Map

### Database

- Modify: `database/teamflow_ai.sql`
  - Owns the complete schema and demo data.

### Backend Existing Infrastructure To Preserve

- Preserve/modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/TeamflowAiApplication.java`
- Preserve/modify: `teamflow-ai-backend/src/main/resources/application.yml`
- Preserve/modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/common/Result.java`
- Preserve/modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/common/BizException.java`
- Preserve/modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/common/GlobalExceptionHandler.java`
- Preserve/modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/security/*`
- Preserve/modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/config/*`

### Backend Domain Packages

- Create/replace entities in `teamflow-ai-backend/src/main/java/com/example/teamflow/entity/`
- Create/replace DTOs in `teamflow-ai-backend/src/main/java/com/example/teamflow/dto/`
- Create/replace VOs in `teamflow-ai-backend/src/main/java/com/example/teamflow/vo/`
- Create/replace mappers in `teamflow-ai-backend/src/main/java/com/example/teamflow/mapper/`
- Create/replace services in `teamflow-ai-backend/src/main/java/com/example/teamflow/service/`
- Create/replace controllers in `teamflow-ai-backend/src/main/java/com/example/teamflow/controller/`

### Frontend Structure

- Modify: `teamflow-ai-frontend/src/router/index.js`
- Modify: `teamflow-ai-frontend/src/views/Layout.vue`
- Modify: `teamflow-ai-frontend/src/stores/user.js`
- Modify: `teamflow-ai-frontend/src/utils/request.js`
- Modify/create API modules in `teamflow-ai-frontend/src/api/`
- Modify/create views in `teamflow-ai-frontend/src/views/`
- Modify/create components in `teamflow-ai-frontend/src/components/`
- Modify: `teamflow-ai-frontend/src/styles/global.css`

### Documentation

- Modify: `README.md`
- Preserve: `docs/superpowers/specs/2026-06-04-teamflowai-full-rebuild-design.md`

---

## API Contract Summary

Implement these endpoint groups:

- Auth:
  - `POST /api/auth/register`
  - `POST /api/auth/login`
  - `GET /api/auth/me`
- Templates:
  - `GET /api/project-templates`
  - `GET /api/project-templates/{code}`
  - `PUT /api/admin/project-templates/{id}`
- Project initialization:
  - `POST /api/projects/init`
  - `GET /api/projects/{id}/init-result`
  - `GET /api/projects/my`
  - `GET /api/projects/{id}`
- Join applications:
  - `GET /api/projects/join/{projectCode}`
  - `POST /api/projects/{id}/join-apply`
  - `GET /api/projects/{id}/join-applies`
  - `PUT /api/projects/{id}/join-applies/{applyId}/approve`
  - `PUT /api/projects/{id}/join-applies/{applyId}/reject`
- Permissions:
  - `GET /api/projects/{id}/permissions`
  - `GET /api/sections/{id}/permissions/me`
- Sections:
  - `GET /api/projects/{id}/sections`
  - `GET /api/sections/{id}`
  - `PUT /api/sections/{id}/content`
  - `POST /api/sections/{id}/submit`
  - `POST /api/sections/{id}/review`
  - `POST /api/sections/{id}/comments`
- Tasks:
  - `GET /api/projects/{id}/tasks`
  - `POST /api/tasks`
  - `PUT /api/tasks/{id}`
  - `PUT /api/tasks/{id}/status`
  - `GET /api/tasks/{id}/logs`
- Progress:
  - `GET /api/progress/need-submit`
  - `POST /api/progress/report`
  - `GET /api/projects/{id}/progress-status`
- AI:
  - `POST /api/ai/projects/{id}/weekly-report`
  - `POST /api/ai/projects/{id}/risk-analysis`
  - `POST /api/ai/projects/{id}/document-check`
  - `POST /api/ai/projects/{id}/summary-report`
  - `GET /api/ai/projects/{id}/records`
- Statistics:
  - `GET /api/projects/{id}/completeness`
  - `GET /api/projects/{id}/contribution`
  - `GET /api/projects/{id}/risk-trend`
- Notifications:
  - `GET /api/notifications`
  - `GET /api/notifications/unread-count`
  - `PUT /api/notifications/{id}/read`
  - `PUT /api/notifications/read-all`
- Export:
  - `POST /api/projects/{id}/export`
  - `GET /api/projects/{id}/export/history`
  - `GET /api/exports/{id}/download`
- Teacher:
  - `GET /api/teacher/projects`
  - `GET /api/teacher/projects/{id}`

---

### Task 1: Replace Database Schema And Demo Data

**Files:**
- Modify: `database/teamflow_ai.sql`

- [ ] **Step 1: Replace the drop/create order**

Write `DROP TABLE IF EXISTS` statements in dependency order:

```sql
DROP TABLE IF EXISTS export_record;
DROP TABLE IF EXISTS notification;
DROP TABLE IF EXISTS risk_snapshot;
DROP TABLE IF EXISTS ai_record;
DROP TABLE IF EXISTS project_file;
DROP TABLE IF EXISTS progress_record;
DROP TABLE IF EXISTS progress_report_rule;
DROP TABLE IF EXISTS task_log;
DROP TABLE IF EXISTS task;
DROP TABLE IF EXISTS section_review;
DROP TABLE IF EXISTS section_comment;
DROP TABLE IF EXISTS role_section_permission;
DROP TABLE IF EXISTS section_content;
DROP TABLE IF EXISTS project_section;
DROP TABLE IF EXISTS project_join_apply;
DROP TABLE IF EXISTS project_member;
DROP TABLE IF EXISTS project_role;
DROP TABLE IF EXISTS project;
DROP TABLE IF EXISTS project_template;
DROP TABLE IF EXISTS sys_user;
```

- [ ] **Step 2: Create schema tables**

Add tables for all approved data objects. Use `BIGINT AUTO_INCREMENT` primary keys, `DATETIME DEFAULT CURRENT_TIMESTAMP`, `VARCHAR` status/code fields, `TEXT` or `LONGTEXT` for rich content, and indexes on foreign-key-like columns.

Required status/code values:

```text
sys_user.role: STUDENT, TEACHER, ADMIN
project.status: PLANNING, DEVELOPING, TESTING, FINISHED, ARCHIVED
project_template.mode: FULL_STACK, FRONT_BACKEND, SOFTWARE_ENGINEERING, AI_APP, MOBILE_APP
project_join_apply.status: PENDING, APPROVED, REJECTED
project_section.status: EMPTY, DRAFT, REVIEWING, APPROVED, REJECTED
section_content.submit_status: DRAFT, REVIEWING, APPROVED, REJECTED
task.status: TODO, IN_PROGRESS, REVIEW, DONE, BLOCKED
task.priority: LOW, MEDIUM, HIGH, URGENT
progress_record.submit_status: NORMAL, LATE, SUPPLEMENTED
ai_record.type: WEEKLY_REPORT, RISK_ANALYSIS, DOC_CHECK, SUMMARY_REPORT
ai_record.source: ZHIPU_GLM, LOCAL_FALLBACK
notification.read_flag: 0, 1
export_record.status: GENERATED, FAILED
```

- [ ] **Step 3: Insert demo users**

Insert these users with the existing BCrypt password hash for password `123456`:

```text
pm / 张经理 / STUDENT
pd / 陈产品 / STUDENT
frontend / 李前端 / STUDENT
backend / 王后端 / STUDENT
qa / 孙测试 / STUDENT
newmember / 周申请 / STUDENT
teacher / 王老师 / TEACHER
admin / 系统管理员 / ADMIN
```

- [ ] **Step 4: Insert five templates**

Insert templates with codes:

```text
FULL_STACK
FRONT_BACKEND
SOFTWARE_ENGINEERING
AI_APP
MOBILE_APP
```

Store role and section defaults as readable JSON text columns if separate template child tables are not created. The implementation must still create project-scoped `project_role`, `project_section`, and `role_section_permission` rows during project initialization.

- [ ] **Step 5: Insert one complete demo project**

Create a `FRONT_BACKEND` demo project with project code `TF-DEMO-001`, members, project roles, required sections, permissions, tasks, progress reports, AI records, risk snapshots, notifications, and one export record.

- [ ] **Step 6: Smoke-check SQL text**

Run:

```bash
rg -n "CREATE TABLE project_template|CREATE TABLE project_section|CREATE TABLE notification|CREATE TABLE export_record|TF-DEMO-001|glm" database/teamflow_ai.sql
```

Expected: all patterns are found.

---

### Task 2: Rebuild Backend Entity, DTO, VO, And Mapper Layer

**Files:**
- Replace/create: `teamflow-ai-backend/src/main/java/com/example/teamflow/entity/*.java`
- Replace/create: `teamflow-ai-backend/src/main/java/com/example/teamflow/dto/*.java`
- Replace/create: `teamflow-ai-backend/src/main/java/com/example/teamflow/vo/*.java`
- Replace/create: `teamflow-ai-backend/src/main/java/com/example/teamflow/mapper/*.java`

- [ ] **Step 1: Create entity classes**

Create one Lombok `@Data` class per schema table. Entity class names:

```text
SysUser, ProjectTemplate, Project, ProjectRole, ProjectMember,
ProjectJoinApply, ProjectSection, SectionContent, RoleSectionPermission,
SectionComment, SectionReview, Task, TaskLog, ProgressReportRule,
ProgressRecord, ProjectFile, AiRecord, RiskSnapshot, Notification, ExportRecord
```

Each class uses:

```java
@Data
@TableName("table_name")
public class EntityName {
    @TableId(type = IdType.AUTO)
    private Long id;
    // fields matching snake_case DB names converted to camelCase
}
```

- [ ] **Step 2: Create mapper interfaces**

For every entity create:

```java
@Mapper
public interface EntityNameMapper extends BaseMapper<EntityName> {
}
```

- [ ] **Step 3: Create DTO request classes**

Create DTOs:

```text
LoginDTO, RegisterDTO, ProjectInitDTO, RoleHeadcountDTO, ReportRuleDTO,
JoinApplyDTO, JoinReviewDTO, SectionContentDTO, SectionReviewDTO,
SectionCommentDTO, TaskDTO, TaskStatusDTO, ProgressReportDTO,
TemplateUpdateDTO, ExportRequestDTO
```

Use `jakarta.validation` annotations for required fields. For example:

```java
@Data
public class ProjectInitDTO {
    @NotBlank(message = "模板编码不能为空")
    private String templateCode;
    @NotBlank(message = "项目名称不能为空")
    private String projectName;
    private String courseName;
    private String description;
    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;
    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;
    private Integer maxMembers;
    private Boolean allowJoinApply;
    @Valid
    private List<RoleHeadcountDTO> roles;
    @Valid
    private ReportRuleDTO reportRule;
}
```

- [ ] **Step 4: Create VO response classes**

Create VOs:

```text
UserVO, LoginVO, ProjectTemplateVO, ProjectInitResultVO, ProjectVO,
ProjectRoleVO, ProjectMemberVO, JoinPreviewVO, JoinApplyVO,
PermissionMatrixVO, SectionVO, SectionContentVO, SectionCommentVO,
SectionReviewVO, TaskVO, TaskLogVO, ProgressNeedSubmitVO,
ProgressStatusVO, AiRecordVO, CompletenessVO, ContributionVO,
RiskTrendVO, NotificationVO, ExportRecordVO, TeacherProjectVO
```

- [ ] **Step 5: Compile to expose missing names**

Run:

```bash
cd teamflow-ai-backend
mvn -DskipTests package
```

Expected: compilation may fail because services/controllers still reference old fields. The next tasks remove old references.

---

### Task 3: Implement Shared Backend Helpers

**Files:**
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/PermissionService.java`
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/NotificationService.java`
- Modify: `teamflow-ai-backend/src/main/resources/application.yml`
- Modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/ai/AiClient.java`

- [ ] **Step 1: Update AI configuration**

Set defaults:

```yaml
ai:
  provider: ${AI_PROVIDER:zhipu}
  api-key: ${AI_API_KEY:}
  api-url: ${AI_API_URL:https://open.bigmodel.cn/api/paas/v4/chat/completions}
  model: ${AI_MODEL:glm-4.7}
```

- [ ] **Step 2: Update `AiClient` for Zhipu GLM**

Implement request body:

```java
Map<String, Object> body = new HashMap<>();
body.put("model", model);
body.put("temperature", 0.3);
body.put("max_tokens", 2048);
body.put("messages", List.of(
        Map.of("role", "system", "content", "你是 TeamFlowAI 的项目管理智能体。只基于已提供数据分析，不编造未出现内容。"),
        Map.of("role", "user", "content", prompt)
));
```

Keep existing bearer token logic and compatible response parsing from `choices[0].message.content`.

- [ ] **Step 3: Implement permission helper methods**

`PermissionService` must expose:

```java
public boolean isAdmin(SysUser user);
public boolean isProjectManager(Long projectId, Long userId);
public void requireProjectMember(Long projectId);
public void requireProjectManage(Long projectId);
public void requireSectionPermission(Long sectionId, String permissionCode);
public List<String> currentSectionPermissions(Long sectionId);
```

Permission codes:

```text
VIEW, EDIT, COMMENT, REVIEW, MANAGE, EXPORT, AI_GENERATE
```

- [ ] **Step 4: Implement notification helper methods**

`NotificationService` must expose:

```java
public void notifyUser(Long userId, Long projectId, String title, String content, String type, String link);
public List<NotificationVO> myNotifications();
public long unreadCount();
public void markRead(Long id);
public void markAllRead();
```

- [ ] **Step 5: Compile shared layer**

Run:

```bash
cd teamflow-ai-backend
mvn -DskipTests package
```

Expected: errors only from old service/controller references that later tasks replace.

---

### Task 4: Implement Auth, Templates, And Project Initialization

**Files:**
- Replace/modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/AuthService.java`
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/ProjectTemplateService.java`
- Replace/modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/ProjectService.java`
- Replace/modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/controller/AuthController.java`
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/controller/ProjectTemplateController.java`
- Replace/modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/controller/ProjectController.java`

- [ ] **Step 1: Keep auth contract stable**

Auth endpoints return:

```text
POST /api/auth/login -> LoginVO { token, user }
POST /api/auth/register -> UserVO
GET /api/auth/me -> UserVO
```

- [ ] **Step 2: Implement template listing**

`ProjectTemplateService` methods:

```java
public List<ProjectTemplateVO> listEnabled();
public ProjectTemplateVO detail(String code);
public ProjectTemplateVO adminUpdate(Long id, TemplateUpdateDTO dto);
```

- [ ] **Step 3: Implement project initialization**

`ProjectService.init(ProjectInitDTO dto)` must:

1. validate template exists and enabled.
2. validate `endDate` is not before `startDate`.
3. create project with unique code.
4. create project manager role and membership for current user.
5. create roles from DTO/template.
6. create sections from template.
7. create role-section permissions.
8. create report rule.
9. return `ProjectInitResultVO`.

- [ ] **Step 4: Implement my projects and detail**

`ProjectService.myProjects()` returns projects where the current user is creator, member, teacher, or admin.

- [ ] **Step 5: Compile**

Run:

```bash
cd teamflow-ai-backend
mvn -DskipTests package
```

Expected: Auth, template, and project initialization code compiles; errors may remain for not-yet-rebuilt modules.

---

### Task 5: Implement Join Applications, Permissions, And Sections

**Files:**
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/JoinApplyService.java`
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/SectionService.java`
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/controller/JoinApplyController.java`
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/controller/PermissionController.java`
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/controller/SectionController.java`

- [ ] **Step 1: Implement join preview**

`JoinApplyService.preview(String projectCode)` returns project name, mode, creator, available roles, remaining counts, and join enabled flag.

- [ ] **Step 2: Implement application submission**

`JoinApplyService.apply(Long projectId, JoinApplyDTO dto)` requires login, blocks duplicate pending applications for same project/user, blocks full role, and creates a pending application plus notification for project manager.

- [ ] **Step 3: Implement approval and rejection**

Approval updates application to `APPROVED`, creates membership, increments role count, and notifies applicant. Rejection sets `REJECTED`, stores review comment, and notifies applicant.

- [ ] **Step 4: Implement permission matrix**

Return role rows and section columns with permission code arrays. Current member without manage permission can still view their own permission summary.

- [ ] **Step 5: Implement section workflow**

`SectionService` must support list, detail, save draft, submit, review, comment, and version history. Status transitions:

```text
EMPTY -> DRAFT -> REVIEWING -> APPROVED
REVIEWING -> REJECTED -> DRAFT
```

- [ ] **Step 6: Compile**

Run:

```bash
cd teamflow-ai-backend
mvn -DskipTests package
```

Expected: join, permission, and section code compiles.

---

### Task 6: Implement Tasks, Progress Reports, AI Agents, Statistics, Notifications, Export, Teacher

**Files:**
- Replace/modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/TaskBizService.java`
- Replace/modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/ProgressService.java`
- Replace/modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/AiBizService.java`
- Replace/modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/DashboardService.java`
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/StatisticsService.java`
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/ExportService.java`
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/TeacherService.java`
- Replace/modify controllers for task, progress, AI, dashboard.
- Create controllers for statistics, notifications, export, teacher.

- [ ] **Step 1: Rebuild task service**

Support section-linked tasks, status transition validation, block reason requirement, urgent priority, and task logs.

- [ ] **Step 2: Rebuild progress service**

Support report need detection, role-aware report submission, late/supplemented status, manager status table, and overdue notifications.

- [ ] **Step 3: Rebuild AI service**

Support agent types:

```text
WEEKLY_REPORT
RISK_ANALYSIS
DOC_CHECK
SUMMARY_REPORT
```

Use Zhipu GLM through `AiClient`. On failure, return local fallback and save `source=LOCAL_FALLBACK`. Risk analysis writes `risk_snapshot`.

- [ ] **Step 4: Implement statistics service**

Completeness formula:

```text
required section completion 30
review pass rate 20
task completion rate 20
testing and defect closure 15
summary and export readiness 15
```

Return score, component scores, and deduction reasons.

- [ ] **Step 5: Implement export service**

Generate downloadable HTML file under `file.upload-path/exports/`. Store export record with scope, file URL, status, and creator.

- [ ] **Step 6: Implement teacher service**

Return project ranking with project name, course, status, completeness, risk level, progress rate, and read-only detail.

- [ ] **Step 7: Compile full backend**

Run:

```bash
cd teamflow-ai-backend
mvn -DskipTests package
```

Expected: `BUILD SUCCESS`.

---

### Task 7: Rebuild Frontend API Layer And Router

**Files:**
- Modify: `teamflow-ai-frontend/src/router/index.js`
- Modify: `teamflow-ai-frontend/src/stores/user.js`
- Create/replace API files:
  - `teamflow-ai-frontend/src/api/auth.js`
  - `teamflow-ai-frontend/src/api/templates.js`
  - `teamflow-ai-frontend/src/api/project.js`
  - `teamflow-ai-frontend/src/api/join.js`
  - `teamflow-ai-frontend/src/api/permission.js`
  - `teamflow-ai-frontend/src/api/section.js`
  - `teamflow-ai-frontend/src/api/task.js`
  - `teamflow-ai-frontend/src/api/progress.js`
  - `teamflow-ai-frontend/src/api/ai.js`
  - `teamflow-ai-frontend/src/api/statistics.js`
  - `teamflow-ai-frontend/src/api/notification.js`
  - `teamflow-ai-frontend/src/api/export.js`
  - `teamflow-ai-frontend/src/api/teacher.js`

- [ ] **Step 1: Define routes**

Routes must include:

```text
/login
/register
/dashboard
/projects
/projects/new
/join/:projectCode
/projects/:id/overview
/projects/:id/members
/projects/:id/permissions
/projects/:id/sections
/projects/:id/sections/:sectionId
/projects/:id/tasks
/projects/:id/progress
/projects/:id/ai
/projects/:id/statistics
/projects/:id/export
/notifications
/teacher/dashboard
/admin/templates
```

- [ ] **Step 2: Keep auth guard**

Allow public access to login, register, and join preview. Require token for every other route.

- [ ] **Step 3: Create API methods**

Each API file should export one function per endpoint listed in the API Contract Summary.

- [ ] **Step 4: Build frontend to catch import errors**

Run:

```bash
cd teamflow-ai-frontend
npm run build
```

Expected: build may fail until views are created in later tasks; API modules should have no syntax errors.

---

### Task 8: Rebuild Global Layout, Workspace, Projects, And Wizard Pages

**Files:**
- Modify: `teamflow-ai-frontend/src/views/Layout.vue`
- Create/replace:
  - `teamflow-ai-frontend/src/views/Dashboard.vue`
  - `teamflow-ai-frontend/src/views/ProjectList.vue`
  - `teamflow-ai-frontend/src/views/ProjectWizard.vue`
  - `teamflow-ai-frontend/src/views/JoinProject.vue`
  - `teamflow-ai-frontend/src/views/ProjectOverview.vue`
  - `teamflow-ai-frontend/src/components/ProjectNav.vue`
  - `teamflow-ai-frontend/src/components/StatCard.vue`

- [ ] **Step 1: Rebuild layout sidebar**

Sidebar items:

```text
我的工作台, 项目管理, 通知中心, 教师看板, 模板管理
```

Show template management only for `ADMIN`; show teacher dashboard for `TEACHER` and `ADMIN`.

- [ ] **Step 2: Implement workspace dashboard**

Show created projects, joined projects, pending reviews, pending reports, and notification previews.

- [ ] **Step 3: Implement project wizard**

Use Element Plus `el-steps`:

```text
选择模式 -> 基本信息 -> 角色人数 -> 汇报规则 -> 创建成功
```

- [ ] **Step 4: Implement join page**

Show project preview and role dropdown. Require login before submission; if token is missing, show a button to login.

- [ ] **Step 5: Implement project overview**

Show completeness, task completion, risk level, member report rate, AI suggestion, and quick links.

- [ ] **Step 6: Build**

Run:

```bash
cd teamflow-ai-frontend
npm run build
```

Expected: remaining missing views are fixed in later tasks.

---

### Task 9: Rebuild Members, Permissions, Sections, Tasks, And Progress Pages

**Files:**
- Create/replace:
  - `teamflow-ai-frontend/src/views/ProjectMembers.vue`
  - `teamflow-ai-frontend/src/views/PermissionMatrix.vue`
  - `teamflow-ai-frontend/src/views/SectionList.vue`
  - `teamflow-ai-frontend/src/views/SectionEditor.vue`
  - `teamflow-ai-frontend/src/views/TaskBoard.vue`
  - `teamflow-ai-frontend/src/views/ProgressRecord.vue`
  - `teamflow-ai-frontend/src/components/SectionCard.vue`
  - `teamflow-ai-frontend/src/components/TaskCard.vue`
  - `teamflow-ai-frontend/src/components/MemberList.vue`

- [ ] **Step 1: Implement members/application review page**

Show members, roles, role counts, pending applications, approve/reject buttons, and review comment dialog.

- [ ] **Step 2: Implement permission matrix**

Render role rows, section columns, and permission tags for `VIEW`, `EDIT`, `COMMENT`, `REVIEW`, `MANAGE`, `EXPORT`, `AI_GENERATE`.

- [ ] **Step 3: Implement section list**

Show required and optional sections with status, owner role, last editor, and action buttons.

- [ ] **Step 4: Implement section editor**

Layout:

```text
left: section metadata
center: title/content editor
right: comments and review history
footer: save draft, submit, comment, approve, reject
```

- [ ] **Step 5: Implement task board**

Columns:

```text
TODO, IN_PROGRESS, REVIEW, DONE, BLOCKED
```

Support create, status update, blocked reason, priority, assignee, due date, section link.

- [ ] **Step 6: Implement progress page**

Show need-submit warning, report form, current user's reports, manager status table, late and missing lists.

- [ ] **Step 7: Build**

Run:

```bash
cd teamflow-ai-frontend
npm run build
```

Expected: no missing imports from these pages.

---

### Task 10: Rebuild AI, Statistics, Notification, Export, Teacher, And Admin Pages

**Files:**
- Create/replace:
  - `teamflow-ai-frontend/src/views/AiReport.vue`
  - `teamflow-ai-frontend/src/views/ProjectStatistics.vue`
  - `teamflow-ai-frontend/src/views/NotificationCenter.vue`
  - `teamflow-ai-frontend/src/views/ProjectExport.vue`
  - `teamflow-ai-frontend/src/views/TeacherDashboard.vue`
  - `teamflow-ai-frontend/src/views/TemplateManage.vue`
  - `teamflow-ai-frontend/src/components/AiResultPanel.vue`

- [ ] **Step 1: Implement AI center**

Show four agent cards:

```text
周报生成, 风险识别, 文档缺失检查, 项目总结
```

Each calls the correct endpoint and reloads history.

- [ ] **Step 2: Implement statistics page**

Use ECharts for contribution bar chart and risk trend line chart. Show completeness score and deduction reasons.

- [ ] **Step 3: Implement notification center**

Show unread filters, type filters, mark read, mark all read, and jump link if provided.

- [ ] **Step 4: Implement export page**

Show completeness check, export scope, generate button, export history, and download link.

- [ ] **Step 5: Implement teacher dashboard**

Show project ranking table and read-only detail links.

- [ ] **Step 6: Implement admin template management**

Show five templates with enabled flag, mode, description, default roles, required sections, and edit dialog.

- [ ] **Step 7: Build**

Run:

```bash
cd teamflow-ai-frontend
npm run build
```

Expected: `vite build` succeeds.

---

### Task 11: Update README And Demo Flow

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Update technical stack**

State that AI provider is Zhipu GLM and defaults are:

```bash
export AI_PROVIDER=zhipu
export AI_API_URL=https://open.bigmodel.cn/api/paas/v4/chat/completions
export AI_MODEL=glm-4.7
export AI_API_KEY=你的智谱API_KEY
```

- [ ] **Step 2: Update database instructions**

Keep:

```bash
mysql -uroot -p < /Users/dexley/Documents/TeamFlowAI/database/teamflow_ai.sql
```

- [ ] **Step 3: Update demo accounts**

Document:

```text
pm / 123456
pd / 123456
frontend / 123456
backend / 123456
qa / 123456
newmember / 123456
teacher / 123456
admin / 123456
```

- [ ] **Step 4: Update demo flow**

List the primary demo from the design spec:

```text
login as pm -> create front-backend project -> review join link -> submit join application as newmember -> approve as pm -> edit sections -> move tasks -> submit progress -> generate four AI outputs -> view statistics -> export -> inspect as teacher -> manage templates as admin
```

---

### Task 12: Final Verification

**Files:**
- All modified files.

- [ ] **Step 1: Backend package**

Run:

```bash
cd teamflow-ai-backend
mvn -DskipTests package
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Frontend build**

Run:

```bash
cd teamflow-ai-frontend
npm run build
```

Expected: `vite build` succeeds.

- [ ] **Step 3: SQL sanity scan**

Run:

```bash
rg -n "CREATE TABLE project_template|CREATE TABLE project_role|CREATE TABLE project_section|CREATE TABLE notification|CREATE TABLE export_record|TF-DEMO-001" database/teamflow_ai.sql
```

Expected: all required objects and demo project are present.

- [ ] **Step 4: AI GLM sanity scan**

Run:

```bash
rg -n "AI_PROVIDER|open.bigmodel.cn|glm-4.7|ZHIPU_GLM|DOC_CHECK" teamflow-ai-backend README.md docs/superpowers/specs/2026-06-04-teamflowai-full-rebuild-design.md
```

Expected: all GLM defaults and document-check AI type are present.

- [ ] **Step 5: Final source scan**

Run:

```bash
rg -n "deepseek|DeepSeek|TODO|TBD|your-api-key" README.md database teamflow-ai-backend/src/main teamflow-ai-frontend/src
```

Expected: no stale DeepSeek defaults and no unfinished placeholders in deliverable code.

---

## Self-Review Checklist

- Spec coverage:
  - P0 login/project modes/roles/join/sections/tasks/progress/AI/export are mapped to Tasks 1-10.
  - P1 completeness/contribution/risk/notifications/templates are mapped to Tasks 6 and 10.
  - Zhipu GLM is covered by Tasks 3, 6, 11, and 12.
- No old API compatibility is required.
- The plan acknowledges there is no git repository and uses verification checkpoints instead of commits.
- The accepted export fallback is HTML if PDF dependencies are unavailable.

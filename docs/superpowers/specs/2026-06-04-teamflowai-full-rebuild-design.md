# TeamFlowAI Full Rebuild Design

Date: 2026-06-04

## Decision

Rebuild TeamFlowAI strictly from the requirement document:

`/Users/dexley/Downloads/TeamFlowAI需求分析用例设计与原型设计.docx`

The existing codebase is treated as reusable material only. The rebuilt system does not need to preserve the old database schema, old seed data, or old API compatibility. The new source of truth is the requirement document's "P0 + P1" scope:

- P0: login, project creation, five project modes, role headcount, join applications, application review, project sections, tasks, progress reports, four AI agents, export.
- P1: completeness score, member contribution statistics, risk trend chart, notification center, project template management.

## Current State

The current project is a Spring Boot + Vue MVP with:

- JWT login and registration.
- Project, member, task, progress, file, AI record, and teacher dashboard modules.
- MySQL seed data for a course project.
- Vue 3 pages for dashboard, project list/detail, task board, progress, file management, AI reports, and teacher dashboard.

The MVP lacks the requirement document's template-driven project model, join application review, role-section permission matrix, section document workflow, notification center, export workflow, risk snapshots, contribution statistics, and project template management.

## Target Architecture

Keep the technical stack, rebuild the domain:

- Backend: Java 17, Spring Boot 3.3.5, Spring Web, Spring Security, JWT, Spring Validation, MyBatis-Plus, MySQL 8, Knife4j/OpenAPI 3.
- Frontend: Vue 3, Vite, Element Plus, Vue Router, Pinia, Axios, ECharts.
- AI: Zhipu GLM through the BigModel chat completions API, with local demo fallback. The default planned endpoint is `https://open.bigmodel.cn/api/paas/v4/chat/completions`, and the default planned model is `glm-4.7`. Both remain configurable through environment variables.
- Deployment style: frontend on Vite port `5173`, backend on `8080`, `/api` proxy from frontend to backend.

The system becomes a template-driven intelligent software project collaboration platform:

1. Admin maintains project templates.
2. Project initiator selects one of five modes.
3. Project initialization creates roles, sections, permissions, report rules, project code, and join link.
4. Members apply to join by role.
5. Project manager reviews applications.
6. Members collaborate through sections, tasks, progress reports, comments, reviews, AI agents, notifications, statistics, and export.

## Data Model

Rebuild `database/teamflow_ai.sql` around these tables:

- `sys_user`: system user account, profile, system role, status.
- `project_template`: five development mode templates and enabled status.
- `project`: project base information, template mode, immutable project code, join policy, status, archive status.
- `project_role`: project-scoped role name/code, default responsibility, max count, current count, required flag.
- `project_member`: user-project-role binding and join metadata.
- `project_join_apply`: applicant, requested role, status, review user, review comment.
- `project_section`: project section generated from template or manually added, required flag, owner role, status.
- `section_content`: section content version, title, body, editor, submit status.
- `role_section_permission`: project role, section, permission code.
- `section_comment`: comments against section content.
- `section_review`: review result and reviewer comment for submitted section content.
- `task`: project task with optional section link, assignee, status, priority, due date, block reason.
- `task_log`: task changes and status flow log.
- `progress_report_rule`: report frequency, report day/time, required flag, overdue policy.
- `progress_record`: member report, completed work, problems, help needed, next plan, related task/section, late status.
- `project_file`: optional uploaded artifacts retained for section attachments and exports.
- `ai_record`: AI agent type, prompt, result, status, confirmation flag, generation source.
- `risk_snapshot`: risk level and trend metrics.
- `notification`: personal notification center.
- `export_record`: export scope, file URL/path, status, creator.

The initialization script will drop and recreate the schema, then insert demo users, five project templates, one complete front-backend-separated demo project, roles, members, sections, permissions, tasks, reports, AI records, risk snapshots, notifications, and export records.

## Backend Modules

### Auth

Responsibilities:

- Register, login, current user, logout-on-client.
- Keep JWT stateless authentication and BCrypt password hashing.
- Require login for business APIs except login/register and public project join preview.
- Project join application submission requires login so approval can bind the application to a real system user. The application form still stores applicant name, email, requested role, and note for review.

### Project Template

Responsibilities:

- List and preview five templates: full-stack, front-backend, standard software engineering, AI app, mobile app.
- Admin can enable/disable and update template metadata, default role definitions, required sections, default permissions, and AI check rules.

### Project Initialization

Responsibilities:

- Create projects through a multi-step payload: template code, base info, role headcounts, report rule, join policy.
- Validate immutable template mode.
- Generate unique project code and join link.
- Create project roles, project manager membership, sections, section permissions, and report rule.
- Return initialization result with project code, link, roles, sections, and permission matrix.

### Join Applications

Responsibilities:

- Preview project join information by project code without login.
- Submit join application with requested role, applicant name/email, and application note.
- Prevent duplicate pending applications and role overflow.
- Project manager approves, rejects, or changes role then approves.
- On approval, create project membership, update role count, and notify applicant/project manager.

### Permissions

Responsibilities:

- Return full role-section permission matrix.
- Return current user's permissions for project/section.
- Enforce backend permission checks for view, edit, comment, review, manage, export, and AI generation.

### Sections

Responsibilities:

- List project sections with status, owner role, required flag, current content status, and missing-state indicators.
- View section detail and latest content.
- Save draft, submit for review, approve, reject, comment, and view versions.
- Use a shared section editor model for requirement, use case, page/prototype, database, API, testing, defect, summary, and custom sections.

### Tasks

Responsibilities:

- Create task with optional section link.
- Assign member, update status, update priority including urgent priority, mark blocked with reason, review completion, and view logs.
- Write logs on task creation, status update, assignment change, priority change, blocked state, and review result.
- Send notifications for assignment, urgent task, blocked task, and review changes.

### Progress Reports

Responsibilities:

- Return whether current user needs to submit a report.
- Submit role-aware report fields.
- Allow late submission and mark late status.
- Return project report status: submitted, missing, late, supplemented.
- Trigger notifications when late count crosses risk threshold.

### AI Agents

Responsibilities:

- Provide four agent endpoints:
  - weekly report.
  - risk analysis.
  - document missing check.
  - project summary.
- Build prompts from project, roles, members, sections, section statuses, tasks, reports, risks, and exports.
- Include the constraint: "analyze only provided data; do not fabricate missing content".
- Use Zhipu GLM by default:
  - `AI_PROVIDER=zhipu`.
  - `AI_API_URL=https://open.bigmodel.cn/api/paas/v4/chat/completions`.
  - `AI_MODEL=glm-4.7`.
  - request body includes `model`, `messages`, `temperature`, `max_tokens`, and optional `thinking.type`.
- On API failure or missing key, return local demo output and mark source as local fallback.
- Store AI history and allow result confirm/discard.
- Risk analysis creates or updates risk snapshots.

### Statistics

Responsibilities:

- Completeness score:
  - required section completion: 30%.
  - review pass rate: 20%.
  - task completion rate: 20%.
  - testing/defect closure: 15%.
  - summary/export readiness: 15%.
- Missing and deduction reasons.
- Member contribution statistics from tasks, section edits, progress reports, comments/reviews, and defects.
- Risk trend from risk snapshots and current task/report/section metrics.

### Notifications

Responsibilities:

- List current user's notifications, unread count, mark one read, mark all read.
- Types include join application, application result, section review, task assignment, task blocked, report overdue, AI risk, export complete.

### Export

Responsibilities:

- Check project completeness before export.
- Generate an export record and downloadable project result file.
- Prefer PDF export if available with local dependencies; otherwise produce a stable HTML or text export file and record the fallback.
- Track export history.
- Allow project manager to archive project and make it read-only.

### Teacher Dashboard

Responsibilities:

- Teacher can view project ranking by completeness, risk, and progress.
- Teacher can enter project detail in read-only mode.
- Teacher can view sections, tasks, AI records, statistics, and export history.

## Frontend Design

Rebuild navigation around the requirement document's 27 low-fidelity pages.

### Global Navigation

Sidebar top level:

- My Workspace.
- Projects.
- Notifications.
- Teacher Dashboard.
- Template Management.

Project workspace navigation:

- Overview.
- Members and Applications.
- Permissions.
- Sections.
- Tasks.
- Progress Reports.
- AI Center.
- Statistics.
- Archive and Export.

### Pages

Implement the following screens:

- Login.
- Register.
- My workspace with created projects, joined projects, pending reviews, pending reports, and notifications.
- New project wizard:
  - mode selection.
  - base information.
  - role headcount configuration.
  - report rule configuration.
  - creation success with project code and join link.
- Join application page.
- Member application review page.
- Project overview with status, completeness score, task completion, risk level, report submit rate, and AI suggestion.
- Role permission matrix.
- Section overview.
- Shared section editor with content, comments, review history, save draft, submit, approve, reject.
- Task board with filters, blocked reason, task logs.
- Progress report page with submit form and manager status table.
- AI center with weekly, risk, document check, summary agents and history.
- Statistics page with completeness, contribution chart, risk trend.
- Notification center.
- Admin template management page.
- Archive/export page.
- Teacher dashboard and read-only project entry.

### Frontend State and API

- Keep Axios wrapper and JWT header injection.
- Expand Pinia user store only where needed.
- Add API modules by domain: templates, init, joins, sections, permissions, statistics, notifications, exports.
- Use Element Plus forms, tables, steps, tabs, tags, dialogs, cards, and messages.
- Use ECharts for contribution and risk trend.
- Provide empty, loading, and error states for core screens.

## Data Flow

Project creation:

1. Frontend fetches templates.
2. User chooses mode and submits wizard.
3. Backend creates project, roles, sections, permissions, and report rule in one transaction.
4. Backend returns initialization result.
5. Frontend shows project code and join link.

Member joining:

1. Applicant opens join page by project code.
2. Backend returns available roles and remaining counts.
3. Applicant submits application.
4. Project manager reviews.
5. Backend creates membership and notification on approval.

Section workflow:

1. Member opens section list.
2. Backend filters actions by permission.
3. Editor saves draft or submits.
4. Reviewer comments/approves/rejects.
5. Status affects completeness and export readiness.

AI workflow:

1. User triggers an agent.
2. Backend aggregates current project data.
3. Backend calls configured AI API or local fallback.
4. Result is saved to `ai_record`.
5. Risk agent also writes `risk_snapshot`.
6. Frontend reloads history and statistics.

Export workflow:

1. Frontend requests completeness check.
2. User chooses export scope.
3. Backend generates file and export record.
4. Frontend displays download entry and history.

## Error Handling

- Use existing `Result` wrapper for success/error response consistency.
- Use `BizException` for business errors and `GlobalExceptionHandler` for API responses.
- Validate all DTOs with Spring Validation.
- Return clear business messages for:
  - missing permission.
  - duplicate join application.
  - role capacity exceeded.
  - immutable project mode changes.
  - required section missing.
  - invalid task state transition.
  - missing block reason.
  - AI unavailable with fallback source.
  - export fallback.
- Frontend shows Element Plus messages and keeps pages recoverable.

## Testing and Verification

Backend:

- `mvn -DskipTests package` must compile.
- If time allows, add focused tests for project initialization, join approval, permission checks, completeness calculation, and AI fallback.

Frontend:

- `npm run build` must pass.
- If a dev server is started, visually inspect core flow in browser:
  - login.
  - create project.
  - review join application.
  - edit section.
  - task flow.
  - AI center.
  - statistics.
  - export.

Database:

- Re-importing `database/teamflow_ai.sql` should create all tables and demo data without manual edits.
- Demo accounts must support project manager, member, teacher, and admin flows.

## Acceptance Flow

Use these demo accounts:

- Project manager.
- Product/section editor.
- Frontend/backend/test member.
- Teacher.
- Admin.

Primary demo:

1. Log in as project manager.
2. Create a front-backend separated project from template.
3. Review generated project code, roles, sections, and permissions.
4. Submit a join application as another member.
5. Approve application as project manager.
6. Edit and submit a requirement/API/test section.
7. Review section.
8. Create and move tasks across board statuses.
9. Submit progress report.
10. Generate weekly report, risk analysis, document check, and summary.
11. View completeness, contribution, and risk trend.
12. Export project result.
13. Log in as teacher and inspect project read-only.
14. Log in as admin and view template management.

## Implementation Boundary

This rebuild can replace:

- `database/teamflow_ai.sql`.
- backend entities, DTOs, VOs, mappers, services, controllers.
- frontend routes, API modules, pages, components, styles.
- README instructions and demo account list.

This rebuild should preserve only reusable infrastructure where helpful:

- Spring Boot app entry.
- `Result`, `BizException`, `GlobalExceptionHandler`.
- JWT security and password hashing structure.
- Axios request wrapper and basic Pinia user store approach.
- Element Plus/Vue/ECharts stack.

No old API compatibility is required.

## Known Risks

- The project is not a git repository, so changes cannot be committed or reverted with git unless the user initializes one.
- Strict full rebuild has broad blast radius; implementation should be staged and compile after each major layer.
- PDF export may require local dependencies unavailable in the current environment. The accepted fallback is downloadable HTML or text export with an export record.
- Some advanced P2 features, such as precise comment anchoring and version comparison, are not in scope for this rebuild unless time remains after P0 + P1.

## User Approvals Recorded

- Scope selected: P0 + P1.
- Implementation approach selected: strict full rebuild.
- Replacing old database and API compatibility accepted.
- Overall architecture, backend rules, frontend pages, and implementation boundary confirmed.

# TeamFlowAI Phased Compatible Refactor Design

Date: 2026-06-17

## Decision

Improve TeamFlowAI through a phased, backward-compatible refactor. The work focuses on two approved directions:

- Fix the highest-value engineering gaps: automated verification, CI, stale checks, oversized frontend pages, route parameter safety, and AI service structure.
- Upgrade role agents from prompt-only role play into configurable project-role agents with profile, context policy, output template, tool permissions, memory records, and prompt configuration.

The implementation must keep existing routes, APIs, database seed data, and user flows working while each phase lands.

## Current State

The project is a web application with:

- Frontend: Vue 3, Vite, Element Plus, Pinia, Vue Router, ECharts.
- Backend: Java Spring Boot, MyBatis-Plus, JWT auth.
- Database: MySQL seed schema in `database/teamflow_ai.sql`.

Recent project-workspace work added project-specific navigation, section view/edit behavior, content insert tools, PlantUML/chart rendering, inline image upload, profile editing, and member/application visibility boundaries.

The main remaining risks are:

- Verification relies mostly on static Node scripts and frontend build checks. Backend tests, API integration tests, and end-to-end UI checks are thin.
- `teamflow-ai-frontend/scripts/check-join-redirect.mjs` still asserts the old `STUDENT` role even though the current system role model is `USER` and `ADMIN`.
- `SectionEditor.vue` and `ManagementDelivery.vue` are too large and carry too many responsibilities.
- Several project pages parse `route.params.id` once with `Number(...)`, which can produce stale `projectId` values when Vue reuses a component across project routes.
- `AiBizService.java` mixes prompt construction, context assembly, AI calls, fallback behavior, record persistence, project-level AI, and section-level role-agent behavior.
- The current role-agent behavior is mainly prompt-based. It uses the section owner role to ask the AI to act as that role, but the role does not yet have an independent configurable profile, memory, template, or tool policy.

## Goals

1. Establish reliable automated verification that can run locally and in CI.
2. Fix stale checks so the verification suite reflects the current `USER`/`ADMIN` system-role model.
3. Make project route parameter handling consistent and reactive across project pages.
4. Split large frontend pages into smaller components without changing user-facing routes.
5. Modularize backend AI code behind focused services while keeping existing controller endpoints compatible.
6. Introduce a configurable role-agent model that can evolve toward multimodal project assistants.
7. Preserve current user workflows during every phase.

## Non-Goals

- No full rewrite of the frontend or backend.
- No breaking URL changes for project pages, section pages, or AI endpoints.
- No forced migration to a third-party rich-text editor.
- No removal of existing demo users, project roles, or seeded project data.
- No immediate dependency on a paid or external multimodal AI provider for basic role-agent configuration.
- No real-time collaborative editing in this refactor.

## Compatibility Principles

- Existing APIs remain callable. When services are split, current controllers keep their endpoint paths and response shapes.
- Existing database data stays readable. New tables or columns are additive.
- Existing frontend routes stay stable. Shared helpers replace ad hoc parameter parsing internally.
- Existing AI behavior remains available as fallback behavior while profile-driven agents are introduced.
- Verification is added before or alongside each risky change, so regressions are visible early.

## Phase 1: Verification And CI Baseline

### Scope

Fix stale verification and add a repeatable verification path before larger refactors.

### Changes

- Update `teamflow-ai-frontend/scripts/check-join-redirect.mjs` to assert the current `USER` and `ADMIN` model instead of the removed `STUDENT` role.
- Add a root-level verification script that runs the existing frontend feature checks and frontend build.
- Add CI configuration that runs frontend install/build/checks and backend Maven tests on a standard runner.
- Add or document backend test execution. If Maven Wrapper cannot be generated in this local environment, CI should use the runner-provided Maven installation and local docs should explain that `mvn test` requires Maven.
- Add focused backend tests where the current structure allows low-risk coverage:
  - auth/login/profile update boundaries.
  - join-project request flow.
  - project member versus application visibility.
  - section permission checks.
  - file upload and inline image access rules.
  - AI endpoint fallback behavior.

### Acceptance Criteria

- The stale join redirect check passes against `USER`/`ADMIN`.
- A single local command can run the existing frontend static checks and build.
- CI exists under `.github/workflows/` and is documented.
- Backend verification is either runnable through Maven or explicitly reported as blocked by missing local Maven tooling.

## Phase 2: Route Safety And Frontend Decomposition

### Scope

Make project route handling consistent, then split the largest frontend views gradually.

### Changes

- Add a route-parameter helper for positive integer project and section ids.
- Replace one-time `Number(route.params.id)` parsing in project views with reactive/computed parsing.
- Guard invalid or missing project ids before API calls, avoiding `/projects/NaN/...` requests.
- Split `SectionEditor.vue` into focused pieces around the existing behavior:
  - page data loader and permission boundary.
  - content view/edit shell.
  - insert toolbar and insert dialogs.
  - AI chat/import panel.
  - version/review/comment panels.
- Split `ManagementDelivery.vue` around stable feature boundaries:
  - WBS table.
  - Gantt chart.
  - resource allocation.
  - baseline management.
  - progress summary.
- Move repeated inline styles into scoped classes where it reduces noise.
- Remove leftover debugging `console.log` calls from production paths.
- Keep `localStorage` compatibility for management delivery during the first split. Backend persistence can be introduced as a later, smaller migration after the component boundaries are clear.

### Acceptance Criteria

- Project navigation between two different project ids refreshes API calls with the new id.
- Invalid project ids do not trigger `NaN` API requests.
- Existing project, section, and management delivery screens still render through the same routes.
- Frontend build passes after each split.

## Phase 3: AI Service Modularization

### Scope

Turn `AiBizService` from a large all-in-one service into a compatibility facade over focused AI services.

### Target Services

- `AiContextBuilder`
  - Collects project, section, member, role, file, and history context.
  - Produces bounded context objects for project-level and section-level prompts.
- `AgentProfileService`
  - Loads default and project-specific role-agent profiles.
  - Resolves the effective profile for a project role and section.
- `AiRecordService`
  - Saves and reads AI interaction records.
  - Centralizes record type, request summary, response content, and user attribution.
- `SectionAgentService`
  - Handles section generation and section chat.
  - Uses role profile, section context, output template, and fallback rules.
- `ProjectAiService`
  - Handles weekly report, risk analysis, document check, and project summary behavior.
- `AiBizService`
  - Remains as a thin compatibility facade while controllers and frontend clients keep their current endpoint paths.

### Acceptance Criteria

- Existing `/api/ai/...` endpoints continue to work.
- Section generation and chat still use the current section ownership and permission model.
- Prompt creation and record persistence are no longer concentrated in one large service.
- Fallback responses remain available when external AI calls fail or return empty content.

## Phase 4: Configurable Role-Agent Model

### Scope

Introduce a data model and service layer that makes each project role an independently configurable agent.

### Agent Profile Model

Each role-agent profile should include:

- `roleCode`: project role code such as `PROJECT_MANAGER`, `PRODUCT_MANAGER`, `TECHNICAL_DIRECTOR`, `FRONTEND_DEV`, `BACKEND_DEV`, or `QA`.
- `roleName`: display name.
- `responsibilities`: role duties and decision boundaries.
- `contextScope`: which context types the agent can use, such as project overview, section content, files, comments, review history, tasks, risks, and member information.
- `outputTemplate`: expected answer structure for generation and chat.
- `toolPermissions`: allowed operations, such as read context, generate draft, suggest review comments, inspect files, or create task suggestions.
- `memoryPolicy`: which interaction records become reusable memory.
- `systemPrompt`: base behavior instructions.
- `taskPromptTemplate`: task-specific prompt template with named variables.
- `multimodalConfig`: additive configuration for image/file understanding when provider support is available.
- `enabled`: whether the profile is active.

### Storage Strategy

Use additive database structures:

- `agent_profile`
  - Stores default or project-specific role-agent profile configuration.
  - Supports global defaults by leaving `project_id` empty.
  - Supports project overrides by setting `project_id`.
- `agent_memory`
  - Stores reusable memories linked to project, role, optional section, and source record.
  - Keeps memory type explicit, such as decision, preference, risk, or document insight.

Seed data should create default profiles for the existing project roles so current projects gain agent behavior without manual setup.

### API Strategy

Add management APIs while keeping current AI generation endpoints:

- Read effective role-agent profile for a project role.
- Update project-specific role-agent profile when the user has project management permission.
- List role-agent memories for a role.
- Add or disable memory records through service logic.

The existing section AI generation and chat APIs should internally use the effective role-agent profile but keep their frontend contracts stable.

### Frontend Strategy

Introduce configuration UI in a later step after backend behavior is stable:

- Project managers can inspect role-agent profiles for each project role.
- Editable fields include responsibilities, output template, enabled tools, and prompt text.
- Non-managers can see agent identity and generated output but cannot modify profiles.

### Acceptance Criteria

- Every existing project role has a default effective profile.
- Section AI generation uses the resolved profile for that section owner role.
- Project-specific overrides do not affect other projects.
- Existing AI screens continue to work when no custom profile has been created.
- Agent memory is additive and can be disabled or ignored without breaking AI generation.

## Phase 5: Documentation And Verification Closure

### Scope

Document the new architecture and keep verification easy to run.

### Changes

- Update developer documentation with verification commands, CI expectations, and known local prerequisites.
- Add architecture notes for AI service boundaries and role-agent profile resolution.
- Add feature checks for configurable role agents once UI/API behavior exists.
- Keep a short migration note for any database additions.

### Acceptance Criteria

- A new contributor can identify the verification command, CI workflow, AI service boundaries, and role-agent profile model from docs.
- All phase-specific checks pass or clearly report environmental blockers.

## Risks And Mitigations

- **Backend Maven not available locally**: use CI Maven and document local prerequisite. Generate Maven Wrapper only when Maven tooling is available.
- **Database migration drift**: keep new schema additive and update `database/teamflow_ai.sql` in the same phase as backend entities.
- **AI behavior regression**: keep `AiBizService` as a facade until the split services are verified.
- **Route refactor regressions**: add parameter helper checks before replacing route parsing broadly.
- **Frontend split churn**: split one responsibility at a time and run build after each group of changes.
- **Agent model overreach**: start with text/profile configuration and additive multimodal fields; provider-specific multimodal execution can be introduced later.

## Testing Strategy

Testing should grow in layers:

- Static feature checks for high-value frontend and route contracts.
- Frontend build verification.
- Backend unit tests for permission and profile-resolution rules.
- Backend API tests for auth, join flow, member/application boundary, file access, section AI, and profile updates.
- End-to-end smoke tests for login, joining a project, editing a section, uploading an inline image, and using section AI.

Large automated UI coverage can come after Phase 1 establishes CI and the core route/permission checks are reliable.

## Rollout Order

1. Verification and CI baseline.
2. Route safety helper and project-page parameter migration.
3. Low-risk frontend component extraction.
4. AI service modularization behind compatible endpoints.
5. Agent profile and memory schema with default role profiles.
6. Role-agent management APIs and optional frontend configuration UI.
7. Documentation and verification hardening.

This order keeps the project usable after every phase and avoids coupling the role-agent model to unrelated frontend cleanup.

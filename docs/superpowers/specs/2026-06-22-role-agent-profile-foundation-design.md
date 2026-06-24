# TeamFlowAI Role-Agent Profile Foundation Design

Date: 2026-06-22

## Decision

Implement the first delivery slice of Phase 4 as a backend role-agent profile foundation. The slice adds additive profile storage, effective-profile resolution, project-level full snapshot overrides, profile management APIs, profile-driven section AI prompts, bounded context selection, and compatibility checks.

This slice does not add agent memory persistence, a frontend profile editor, or real multimodal provider calls. Those remain separate Phase 4 deliveries after the profile foundation is stable.

## Goals

1. Give every project role an effective agent profile without requiring manual configuration.
2. Let project managers and system administrators create, update, and reset project-specific profile snapshots.
3. Keep global defaults read-only and seed-driven in this delivery.
4. Preserve existing section generation and chat endpoint paths and response shapes.
5. Make profile responsibilities, context policy, output template, tool policy, and prompts affect section AI behavior.
6. Keep external AI exception and empty-response fallbacks working.
7. Store memory and multimodal policy fields now without implementing memory retrieval or multimodal execution.

## Non-Goals

- No `agent_memory` table or memory management APIs.
- No frontend agent-profile configuration page.
- No system-administrator API for editing global defaults.
- No image, audio, video, or document-content calls to a multimodal model.
- No agent-triggered writes to tasks, comments, reviews, files, or project settings.
- No changes to existing `/api/ai/projects/{id}/...` request or response contracts.

## Existing Constraints

- Project roles are stored in `project_role` and are scoped by `project_id`.
- Section ownership is expressed through `project_section.owner_role_id`.
- Project management permission is centralized in `PermissionService.requireProjectManage`.
- Section AI permission remains enforced through `AI_GENERATE` before existing AI endpoints execute.
- Fresh database setup uses `database/teamflow_ai.sql`; there is no migration framework in this repository. This delivery therefore also provides one standalone, idempotent additive upgrade script for existing databases.
- Backend Maven execution is unavailable in the current local environment and must run in CI or another Maven-enabled environment.

## Architecture

The design uses relational columns for profile identity and human-authored text, plus JSON columns for bounded option sets. It follows the current MyBatis-Plus entity/mapper/service/controller structure.

### Components

- `AgentProfile`
  - Maps the additive `agent_profile` table.
  - Stores both global defaults and project-specific full snapshots.
- `AgentProfileService`
  - Validates project-role ownership.
  - Resolves project override, global default, or safe synthetic fallback.
  - Creates and updates full project snapshots.
  - Deletes project snapshots to restore default resolution.
  - Produces manager and member views with field-level redaction.
- `SectionAgentContextBuilder`
  - Loads only context categories allowed by the effective profile.
  - Bounds list counts and text lengths before prompt assembly.
- `AgentPromptComposer`
  - Expands a fixed whitelist of named variables.
  - Combines system prompt, task prompt, responsibilities, output template, tool policy, and bounded context.
- `AgentProfileController`
  - Exposes member-readable profile endpoints and manager-only mutation endpoints.
- `SectionAgentService`
  - Keeps current public behavior while replacing hard-coded role prompt assembly with resolved profiles.

## Data Model

Add one table named `agent_profile`.

Update `database/teamflow_ai.sql` for fresh installations and add `database/migrations/2026-06-22-agent-profile.sql` for existing databases. The migration script may create and seed only the new table; it must not drop or rewrite existing project data.

| Column | Type | Purpose |
| --- | --- | --- |
| `id` | `BIGINT` | Primary key. |
| `scope_type` | `VARCHAR(20)` | `GLOBAL` or `PROJECT`. |
| `scope_id` | `BIGINT` | `0` for global defaults, project id for project snapshots. |
| `project_id` | `BIGINT NULL` | Empty for global defaults, populated for project snapshots. |
| `project_role_id` | `BIGINT NULL` | Empty for global defaults, populated for project snapshots. |
| `role_code` | `VARCHAR(50)` | Stable role code used for default resolution. |
| `role_name` | `VARCHAR(80)` | Display name captured in the profile snapshot. |
| `responsibilities` | `LONGTEXT` | Duties and decision boundaries. |
| `context_scope` | `JSON` | Allowed context categories. |
| `output_template` | `LONGTEXT` | Required answer structure. |
| `tool_permissions` | `JSON` | Allowed advisory capabilities. |
| `memory_policy` | `JSON` | Stored for the later memory delivery. |
| `system_prompt` | `LONGTEXT` | Base behavior instructions. |
| `task_prompt_template` | `LONGTEXT` | Named-variable prompt template. |
| `multimodal_config` | `JSON` | Stored capability policy; not executed in this delivery. |
| `enabled` | `TINYINT` | Whether this role agent may be used. |
| `create_time` | `DATETIME` | Creation timestamp. |
| `update_time` | `DATETIME` | Last update timestamp. |

Use a unique key on `(scope_type, scope_id, role_code)`. This avoids MySQL nullable-unique behavior while retaining `project_id IS NULL` for global rows.

Project snapshots must satisfy:

- `scope_type = 'PROJECT'`
- `scope_id = project_id`
- `project_id IS NOT NULL`
- `project_role_id IS NOT NULL`
- `role_code` equals the referenced project role code

Global defaults must satisfy:

- `scope_type = 'GLOBAL'`
- `scope_id = 0`
- `project_id IS NULL`
- `project_role_id IS NULL`

## Default Profiles

Seed global defaults for:

- `PROJECT_MANAGER`
- `PRODUCT_MANAGER`
- `TECHNICAL_DIRECTOR`
- `FRONTEND_DEV`
- `BACKEND_DEV`
- `QA`

The seeded prompts must preserve the intent of the current section-specific role behavior. All seeded profiles are enabled.

When a custom role code has no global row, resolution returns a non-persisted synthetic profile built from `ProjectRole.roleName` and `ProjectRole.responsibility`. The synthetic profile uses safe default context, draft-generation permission, the current section output expectations, and enabled status. A manager can save this effective profile to create a normal project snapshot.

## Effective Profile Resolution

`AgentProfileService.resolveEffective(projectId, projectRoleId)` performs these steps:

1. Load the project role and reject missing or cross-project role ids.
2. Query a `PROJECT` snapshot by project id and role code.
3. If absent, query the `GLOBAL` default by role code.
4. If absent, synthesize a safe runtime profile from the project role.
5. Return the profile with a `source` value of `PROJECT`, `GLOBAL`, or `SYNTHETIC`.

Project overrides use full snapshot semantics. The first `PUT` copies the effective profile into a project row and applies the validated request. Later global seed changes do not change that project snapshot. `DELETE` removes only the project row and restores normal global or synthetic resolution.

## Profile Fields And Validation

### Context Scope

Supported values:

- `PROJECT_OVERVIEW`
- `CURRENT_SECTION`
- `PREVIOUS_SECTIONS`
- `MEMBERS`
- `TASKS`
- `RISKS`
- `FILES`
- `COMMENTS`
- `REVIEW_HISTORY`

The context builder loads only selected categories. It uses metadata rather than raw file binary content. Every category has a maximum item count, and every text value is truncated before prompt composition.

### Tool Permissions

Supported values:

- `READ_CONTEXT`
- `GENERATE_DRAFT`
- `SUGGEST_REVIEW_COMMENT`
- `INSPECT_FILES`
- `SUGGEST_TASKS`

These permissions constrain what the model may claim or suggest. They do not authorize database mutations in this delivery.

### Prompt Variables

Supported named variables:

- `roleCode`
- `roleName`
- `responsibilities`
- `projectName`
- `projectDescription`
- `sectionName`
- `sectionCode`
- `sectionDescription`
- `context`
- `outputTemplate`
- `toolPermissions`

Unknown variables are rejected during update. Required profile text fields reject blank content. DTO and service validation enforce bounded lengths for names, responsibilities, templates, and prompts.

### Stored Future Policies

`memory_policy` is validated as a JSON object but is not consumed by prompt context in this delivery. `multimodal_config` is validated as a JSON object and returned to authorized managers, but no provider call uses it yet.

## API Design

### List Effective Profiles

`GET /api/projects/{projectId}/agent-profiles`

- Access: project member or system administrator.
- Returns one effective profile summary per current project role.
- Prompt text, memory policy, and multimodal configuration are omitted.

### Read Effective Role Profile

`GET /api/projects/{projectId}/roles/{roleId}/agent-profile`

- Access: project member or system administrator.
- Always returns stable fields and the effective `source`.
- Project managers and administrators receive complete prompt and future-policy fields.
- Other members receive those restricted fields as `null` rather than a different response type.

### Create Or Update Project Snapshot

`PUT /api/projects/{projectId}/roles/{roleId}/agent-profile`

- Access: `PermissionService.requireProjectManage(projectId)`.
- Validates role ownership, enums, JSON structure, prompt variables, and lengths.
- Creates or updates a full `PROJECT` snapshot transactionally.
- Does not modify seeded global rows.

### Reset Project Snapshot

`DELETE /api/projects/{projectId}/roles/{roleId}/agent-profile`

- Access: `PermissionService.requireProjectManage(projectId)`.
- Deletes only the matching project snapshot.
- Returns the newly effective global or synthetic profile.

## Visibility Rules

Project members may read:

- role code and name
- responsibilities
- context scope
- output template
- tool permissions
- enabled status
- effective source

Only project managers and system administrators may read:

- system prompt
- task prompt template
- memory policy
- multimodal configuration
- project snapshot identifiers and update metadata intended for management

Only project managers and system administrators may mutate or reset profiles.

## Section AI Integration

The existing endpoints remain unchanged:

- `POST /api/ai/projects/{id}/sections/{sectionId}/generate`
- `POST /api/ai/projects/{id}/sections/{sectionId}/chat`

`SectionAgentService` changes internally:

1. Preserve the existing project/section ownership check.
2. Resolve the section owner role and its effective profile.
3. If `enabled` is false, throw a business error with code `409` and a clear disabled message.
4. Build context according to `contextScope` with deterministic limits.
5. Compose generation or chat instructions from the profile and whitelist variables.
6. Keep existing AI client calls, JSON cleanup, response maps, record type, and local fallback behavior.

The local fallback remains available for external AI exceptions and null or blank results. A disabled profile is intentional policy and does not fall back to the global profile or legacy prompt.

## Error Handling

- `400`: invalid enums, malformed JSON configuration, blank required fields, unknown prompt variables, or length violations.
- `403`: non-member read attempt or non-manager mutation attempt.
- `404`: missing project role or role id that does not belong to the path project.
- `409`: effective role agent is disabled when section AI is invoked.

Database uniqueness violations from concurrent first writes must be translated into a stable business error or retried as an update; raw SQL exceptions must not reach the API response.

## Compatibility

- Existing tables remain readable and unchanged.
- `agent_profile` is additive, and the standalone migration script does not drop existing tables or data.
- Existing AI controller paths and payload shapes remain unchanged.
- Default profiles reproduce the current role and section guidance.
- Projects without a project snapshot use global defaults automatically.
- Custom roles remain functional through synthetic defaults.
- Project-specific snapshots never affect other projects.
- Existing AI record persistence and fallback behavior remain intact.

## Verification Strategy

### Static Architecture Check

Add a focused Node.js check and wire it into `scripts/verify.sh`. It verifies:

- additive SQL table and six default seeds in both fresh-install and upgrade paths
- entity and mapper existence
- effective resolution precedence
- member read and manager mutation permission calls
- API path stability
- restricted-field redaction
- `SectionAgentService` dependency on profile resolution, context building, and prompt composition
- disabled-agent handling
- unchanged existing section AI endpoint paths

### Backend Tests

Add focused JUnit tests for:

- project snapshot overrides global default
- global default is used when no project snapshot exists
- synthetic profile supports custom roles
- first update creates a full project snapshot
- reset restores global or synthetic resolution
- cross-project role ids are rejected
- members receive redacted restricted fields
- managers receive complete fields
- only managers and administrators can update or reset
- invalid JSON, enum values, prompt variables, and lengths are rejected
- disabled profile blocks section generation and chat
- profile-driven generation and chat preserve existing response contracts
- external AI failure and blank response still use local fallback

Maven tests must run in CI or a Maven-enabled environment. Local verification must explicitly report when Maven is unavailable.

## Rollout

1. Add the architecture check in a failing state.
2. Add the SQL table and default seed profiles to the fresh-install SQL and the idempotent additive upgrade script.
3. Add entity, mapper, DTO, and VO contracts.
4. Add effective resolution and snapshot management.
5. Add profile APIs and visibility enforcement.
6. Add bounded context and prompt composition.
7. Integrate profiles into section generation and chat.
8. Run static verification, frontend checks/build, and Maven tests where available.

## Acceptance Criteria

- Every project role resolves to a `PROJECT`, `GLOBAL`, or `SYNTHETIC` effective profile.
- Project managers can create, update, and reset full project snapshots.
- Global defaults remain seed-driven and read-only through the API.
- Members can read public profile fields but not complete prompts or future policy configuration.
- Section generation and chat use the effective profile and selected context.
- Disabled profiles block section AI with a stable business error.
- Existing AI endpoint paths, response shapes, records, and fallbacks remain compatible.
- Project overrides do not affect other projects.
- Static checks pass, frontend verification remains green, and Maven tests pass where Maven is available.

## Deferred Phase 4 Deliveries

1. Agent memory table, memory capture policy, and memory management APIs.
2. Project agent-profile management UI.
3. Provider-backed multimodal execution for image and file understanding.
4. Optional administrator management API for global defaults.

# AI Service Modularization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn `AiBizService` into a compatibility facade over focused AI services while keeping existing `/api/ai/...` endpoints and response shapes stable.

**Architecture:** Introduce backend AI service boundaries under `com.example.teamflow.service.ai`. `AiContextBuilder` owns project context assembly, `AiRecordService` owns AI record persistence and VO mapping, `ProjectAiService` owns project-level reports, `SectionAgentService` owns section generation/chat, and `ProgressExtractionAiService` owns report-to-WBS progress extraction. `AiBizService` delegates to these services so controllers remain unchanged.

**Tech Stack:** Java 17, Spring Boot 3.3, MyBatis-Plus, JUnit 5 where Maven is available, Node.js static architecture checks for this local environment.

---

## Scope Note

This is Phase 3 from `docs/superpowers/specs/2026-06-17-teamflowai-phased-compatible-refactor-design.md`. It does not add the Phase 4 database-backed configurable role-agent profile model yet. The split must be backward-compatible: no controller path changes, no database schema changes, no frontend contract changes.

The current local workspace does not expose a usable Git repository and Maven is not installed. Commit steps are skipped here. Static checks and frontend verification still run locally; backend Maven tests should run in CI or any environment with Maven 3.8+.

## File Structure

- Create: `scripts/check-ai-service-modularization.mjs`
  - Static architecture check for AI service boundaries and facade shape.
- Modify: `scripts/verify.sh`
  - Run AI modularization static check before Maven-dependent backend tests.
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/ai/ProjectAiContext.java`
  - Project AI context data object.
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/ai/AiContextBuilder.java`
  - Loads project roles, members, sections, tasks, progress records, risk snapshots, exports, file count, users, and role lookup map.
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/ai/AiRecordService.java`
  - Reads AI records, saves generated records, maps records to `AiRecordVO`.
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/ai/AiTextSupport.java`
  - Shared text helpers such as `firstNonBlank`, `nullSafe`, `valueOf`, `normalize`, `truncate`, and `currentPeriod`.
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/ai/ProjectAiService.java`
  - Handles weekly report, risk analysis, document check, summary report, prompt building, local fallback, and risk snapshot persistence.
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/ai/SectionAgentService.java`
  - Handles section content generation and section chat using section owner role and preceding-section context.
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/ai/ProgressExtractionAiService.java`
  - Handles WBS progress extraction from member report text.
- Modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/AiBizService.java`
  - Reduce to compatibility facade delegating to focused services.
- Optional Test: `teamflow-ai-backend/src/test/java/com/example/teamflow/service/ai/AiTextSupportTest.java`
  - Pure unit tests for shared text helpers if Maven is available.

## Task 1: Add AI Modularization Architecture Check

**Files:**
- Create: `scripts/check-ai-service-modularization.mjs`
- Modify: `scripts/verify.sh`

- [ ] **Step 1: Write the failing architecture check**

Create `scripts/check-ai-service-modularization.mjs` with assertions for:

```js
import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const root = resolve(new URL('..', import.meta.url).pathname)
const read = (path) => readFileSync(resolve(root, path), 'utf8')
const exists = (path) => existsSync(resolve(root, path))

const aiServiceDir = 'teamflow-ai-backend/src/main/java/com/example/teamflow/service/ai'
const requiredFiles = [
  'ProjectAiContext.java',
  'AiContextBuilder.java',
  'AiRecordService.java',
  'AiTextSupport.java',
  'ProjectAiService.java',
  'SectionAgentService.java',
  'ProgressExtractionAiService.java'
]

for (const file of requiredFiles) {
  assert.ok(exists(`${aiServiceDir}/${file}`), `${file} should exist`)
}

const facade = read('teamflow-ai-backend/src/main/java/com/example/teamflow/service/AiBizService.java')
assert.match(facade, /private final ProjectAiService projectAiService;/, 'AiBizService should delegate project AI behavior')
assert.match(facade, /private final SectionAgentService sectionAgentService;/, 'AiBizService should delegate section agent behavior')
assert.match(facade, /private final ProgressExtractionAiService progressExtractionAiService;/, 'AiBizService should delegate progress extraction')
assert.match(facade, /private final AiRecordService aiRecordService;/, 'AiBizService should delegate records')
assert.doesNotMatch(facade, /Mapper;/, 'AiBizService facade should not inject mappers directly')
assert.doesNotMatch(facade, /AiClient;/, 'AiBizService facade should not call AiClient directly')
assert.doesNotMatch(facade, /private String buildPrompt|private String fallback|private ProjectAiContext loadContext/, 'prompt/context internals should move out of facade')

const contextBuilder = read(`${aiServiceDir}/AiContextBuilder.java`)
assert.match(contextBuilder, /class AiContextBuilder/, 'AiContextBuilder should define the context builder')
assert.match(contextBuilder, /ProjectAiContext build\(Project project\)/, 'AiContextBuilder should build context from project')

const recordService = read(`${aiServiceDir}/AiRecordService.java`)
assert.match(recordService, /class AiRecordService/, 'AiRecordService should exist')
assert.match(recordService, /List<AiRecordVO> records\(Long projectId\)/, 'AiRecordService should read records')
assert.match(recordService, /AiRecord saveGeneratedRecord/, 'AiRecordService should centralize generated record persistence')

const projectService = read(`${aiServiceDir}/ProjectAiService.java`)
assert.match(projectService, /class ProjectAiService/, 'ProjectAiService should exist')
assert.match(projectService, /weeklyReport\(Long projectId\)/, 'ProjectAiService should handle weekly report')
assert.match(projectService, /riskAnalysis\(Long projectId\)/, 'ProjectAiService should handle risk analysis')
assert.match(projectService, /documentCheck\(Long projectId\)/, 'ProjectAiService should handle document check')
assert.match(projectService, /summaryReport\(Long projectId\)/, 'ProjectAiService should handle summary report')

const sectionService = read(`${aiServiceDir}/SectionAgentService.java`)
assert.match(sectionService, /class SectionAgentService/, 'SectionAgentService should exist')
assert.match(sectionService, /generateSectionContent\(Long projectId, Long sectionId\)/, 'SectionAgentService should generate section content')
assert.match(sectionService, /chatSectionContent\(Long projectId, Long sectionId, List<Map<String, String>> messages\)/, 'SectionAgentService should chat for section content')

const progressService = read(`${aiServiceDir}/ProgressExtractionAiService.java`)
assert.match(progressService, /class ProgressExtractionAiService/, 'ProgressExtractionAiService should exist')
assert.match(progressService, /extractProgress\(Long projectId, String reportText, List<Map<String, Object>> tasks\)/, 'ProgressExtractionAiService should extract progress')

console.log('ai service modularization checks passed')
```

- [ ] **Step 2: Verify RED**

Run:

```bash
node scripts/check-ai-service-modularization.mjs
```

Expected: FAIL because `teamflow-ai-backend/src/main/java/com/example/teamflow/service/ai/ProjectAiContext.java` and the other focused services do not exist yet.

- [ ] **Step 3: Wire check into root verification**

Modify `scripts/verify.sh` so it runs:

```bash
echo "==> Backend architecture checks"
cd "$ROOT_DIR"
node scripts/check-ai-service-modularization.mjs
```

before the Maven-dependent backend tests.

## Task 2: Extract Shared Context And Text Support

**Files:**
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/ai/ProjectAiContext.java`
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/ai/AiTextSupport.java`
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/ai/AiContextBuilder.java`
- Modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/AiBizService.java`

- [ ] **Step 1: Move `ProjectAiContext` into a package-visible data object**

Create a public class with mutable fields and getters/setters or package-visible fields for:

```java
List<ProjectRole> roles
List<ProjectMember> members
List<ProjectSection> sections
List<Task> tasks
List<ProgressRecord> progressRecords
List<RiskSnapshot> riskSnapshots
List<ExportRecord> exportRecords
long fileCount
Map<Long, SysUser> users
Map<Long, ProjectRole> rolesById
```

- [ ] **Step 2: Move text helpers into `AiTextSupport`**

Move helper methods from `AiBizService`:

```java
firstNonBlank
nullSafe
valueOf
normalize
currentPeriod
truncate
toInt
```

Keep behavior unchanged.

- [ ] **Step 3: Move `loadContext` and `loadUsers` into `AiContextBuilder`**

`AiContextBuilder` injects:

```java
ProjectRoleMapper
ProjectMemberMapper
ProjectSectionMapper
TaskMapper
ProgressRecordMapper
ProjectFileMapper
RiskSnapshotMapper
ExportRecordMapper
SysUserMapper
```

It exposes:

```java
public ProjectAiContext build(Project project)
```

- [ ] **Step 4: Update imports and remove nested context class**

Remove the nested `ProjectAiContext` class from `AiBizService` after its users move to dedicated services in later tasks.

## Task 3: Extract AI Record Service

**Files:**
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/ai/AiRecordService.java`
- Modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/AiBizService.java`

- [ ] **Step 1: Move record read and VO mapping**

`AiRecordService` injects:

```java
AiRecordMapper
ProjectMapper
SysUserMapper
AuthService
ProjectService
```

It exposes:

```java
public List<AiRecordVO> records(Long projectId)
public AiRecord saveGeneratedRecord(Long projectId, String type, String prompt, String result, String source, String modelName, String riskLevel)
public AiRecordVO toVO(AiRecord record)
```

- [ ] **Step 2: Update `AiBizService.records` to delegate**

`AiBizService.records(projectId)` becomes:

```java
return aiRecordService.records(projectId);
```

## Task 4: Extract Project-Level AI Service

**Files:**
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/ai/ProjectAiService.java`
- Modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/AiBizService.java`

- [ ] **Step 1: Move project-level generation**

Move these behaviors from `AiBizService` into `ProjectAiService`:

```java
weeklyReport
riskAnalysis
documentCheck
summaryReport
generate
buildPrompt
fallback
inferRiskLevel
saveRiskSnapshot
calculateRiskScore
countBlockedTasks
countOverdueTasks
renderRoles
renderMembers
renderSections
renderTasks
renderProgressRecords
renderRiskSnapshots
renderExportRecords
```

`ProjectAiService` injects:

```java
AiClient
ProjectService
AiContextBuilder
AiRecordService
RiskSnapshotMapper
```

- [ ] **Step 2: Update facade delegation**

`AiBizService` delegates:

```java
weeklyReport -> projectAiService.weeklyReport
riskAnalysis -> projectAiService.riskAnalysis
documentCheck -> projectAiService.documentCheck
summaryReport -> projectAiService.summaryReport
```

## Task 5: Extract Section Agent Service

**Files:**
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/ai/SectionAgentService.java`
- Modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/AiBizService.java`

- [ ] **Step 1: Move section generation/chat**

Move these behaviors from `AiBizService`:

```java
generateSectionContent
chatSectionContent
getLocalFallbackChatResponse
getSectionDefaultTitle
getLocalFallbackForSection
```

`SectionAgentService` injects:

```java
AiClient
ProjectService
ProjectRoleMapper
ProjectSectionMapper
SectionContentMapper
AiRecordService
```

- [ ] **Step 2: Keep record type and fallback compatible**

Generated records keep type `"SECTION_GENERATE"`, current source/model behavior, and the same response maps.

- [ ] **Step 3: Update facade delegation**

`AiBizService.generateSectionContent` and `AiBizService.chatSectionContent` delegate to `sectionAgentService`.

## Task 6: Extract Progress Extraction Service And Thin Facade

**Files:**
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/ai/ProgressExtractionAiService.java`
- Modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/AiBizService.java`

- [ ] **Step 1: Move progress extraction**

Move `extractProgress` into `ProgressExtractionAiService`.

`ProgressExtractionAiService` injects:

```java
AiClient
```

- [ ] **Step 2: Reduce `AiBizService` to facade**

`AiBizService` should only inject:

```java
ProjectAiService
AiRecordService
SectionAgentService
ProgressExtractionAiService
```

It should not inject mappers or `AiClient`, and should not contain prompt builders or context loaders.

## Task 7: Verification And Review

**Files:**
- No new production files unless fixes are needed.

- [ ] **Step 1: Run local checks**

Run:

```bash
node scripts/check-ai-service-modularization.mjs
bash scripts/verify.sh
```

Expected: static checks pass, frontend checks/build pass, Maven tests run if Maven is installed or are explicitly skipped by `scripts/verify.sh`.

- [ ] **Step 2: Run Maven tests where available**

Run:

```bash
cd teamflow-ai-backend
mvn test
```

Expected: PASS in environments with Maven. In this local workspace, if `mvn` is unavailable, report that backend tests were skipped by local environment and must run in CI.

- [ ] **Step 3: Final code review**

Review that:

- Controller endpoints are unchanged.
- `AiBizService` is a thin compatibility facade.
- Project-level and section-level AI behavior remains in dedicated services.
- Fallback behavior remains available.
- AI records are persisted through `AiRecordService`.


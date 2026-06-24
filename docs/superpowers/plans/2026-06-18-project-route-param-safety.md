# Project Route Parameter Safety Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make project route parameter handling reactive and safe so project pages do not call `/projects/NaN/...` and do not reuse stale `projectId` or `sectionId` values when Vue reuses route components.

**Architecture:** Add a shared route parameter utility, add a router guard for invalid project/section ids, and migrate project pages from one-time `Number(route.params.id)` parsing to computed route params plus guarded loaders. Keep existing routes and user flows unchanged.

**Tech Stack:** Vue 3 Composition API, Vue Router 4, Node.js static verification scripts, Vite build.

---

## Scope Note

This plan implements the route-safety part of Phase 2 from the phased compatible refactor spec. It does not split `SectionEditor.vue` or `ManagementDelivery.vue` into new feature components; that should be a separate frontend decomposition plan after route safety is stable.

Current workspace note: `/Users/dexley/Documents/TeamFlowAI` does not contain a `.git` directory, so commit steps cannot run in this local copy. If executing from a Git checkout, commit after each task using the command shown in that task.

## File Structure

- Create: `teamflow-ai-frontend/src/utils/routeParams.js`
  - Responsibility: parse positive integer route params and recognize the special `management-delivery` section route.
- Create: `teamflow-ai-frontend/scripts/check-route-param-safety.mjs`
  - Responsibility: verify the shared helper, router guard, keyed router view, and removal of unsafe one-time `Number(route.params...)` parsing from project views.
- Modify: `teamflow-ai-frontend/package.json`
  - Responsibility: expose `check:route-params` and include it in `npm run check`.
- Modify: `teamflow-ai-frontend/src/router/index.js`
  - Responsibility: reject invalid `/projects/:id` and invalid numeric section/review params before components make API calls.
- Modify: `teamflow-ai-frontend/src/views/Layout.vue`
  - Responsibility: use the shared helper and key the nested route view by full path so project route changes remount legacy pages that still hold local state.
- Modify: simple project pages:
  - `teamflow-ai-frontend/src/views/ProjectOverview.vue`
  - `teamflow-ai-frontend/src/views/ProjectMembers.vue`
  - `teamflow-ai-frontend/src/views/PermissionMatrix.vue`
  - `teamflow-ai-frontend/src/views/SectionList.vue`
  - `teamflow-ai-frontend/src/views/FileManage.vue`
  - `teamflow-ai-frontend/src/views/AiReport.vue`
  - `teamflow-ai-frontend/src/views/ProjectStatistics.vue`
  - `teamflow-ai-frontend/src/views/ProjectExport.vue`
  - `teamflow-ai-frontend/src/views/ProjectReviews.vue`
  - `teamflow-ai-frontend/src/views/SectionReviewPage.vue`
  - Responsibility: use reactive positive project/section params and guard all API calls.
- Modify: interactive project pages:
  - `teamflow-ai-frontend/src/views/TaskBoard.vue`
  - `teamflow-ai-frontend/src/views/ProgressRecord.vue`
  - `teamflow-ai-frontend/src/views/ManagementDelivery.vue`
  - Responsibility: keep form/localStorage behavior compatible while reading `projectId.value` at action time.
- Modify: `teamflow-ai-frontend/src/views/SectionEditor.vue`
  - Responsibility: replace its local `toPositiveInteger` helper with the shared helper.

## Shared Implementation Pattern

For project pages, use this pattern:

```js
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { usePositiveProjectId } from '../utils/routeParams'

const route = useRoute()
const projectId = usePositiveProjectId(route)
let loadRequestId = 0

const load = async () => {
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  const requestId = ++loadRequestId
  const result = await loadSomething(currentProjectId)
  if (requestId !== loadRequestId || projectId.value !== currentProjectId) return
  applyResult(result)
}

watch(projectId, load, { immediate: true })
```

For actions, capture the current value at the start of the function:

```js
const submit = async () => {
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  await submitSomething(currentProjectId)
  load()
}
```

For forms that store `projectId`, build the object from the current value:

```js
const blankForm = () => ({
  projectId: projectId.value,
  title: ''
})
```

## Task 1: Add Route Parameter Safety Check

**Files:**
- Create: `teamflow-ai-frontend/scripts/check-route-param-safety.mjs`
- Modify: `teamflow-ai-frontend/package.json`

- [ ] **Step 1: Create the failing check script**

Create `teamflow-ai-frontend/scripts/check-route-param-safety.mjs`:

```js
import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const workspaceRoot = resolve(root, '..')
const read = (path) => readFileSync(resolve(workspaceRoot, path), 'utf8')
const exists = (path) => existsSync(resolve(workspaceRoot, path))

assert.ok(exists('teamflow-ai-frontend/src/utils/routeParams.js'), 'route param helper should exist')

const helper = read('teamflow-ai-frontend/src/utils/routeParams.js')
assert.match(helper, /export const toPositiveInteger/, 'helper should export toPositiveInteger')
assert.match(helper, /export const usePositiveProjectId/, 'helper should export usePositiveProjectId')
assert.match(helper, /export const usePositiveSectionId/, 'helper should export usePositiveSectionId')
assert.match(helper, /export const isManagementDeliverySection/, 'helper should recognize management delivery section route')

const router = read('teamflow-ai-frontend/src/router/index.js')
assert.match(router, /toPositiveInteger/, 'router guard should use route param helper')
assert.match(router, /return '\\/projects'/, 'router guard should redirect invalid project ids')
assert.match(router, /return `\\/projects\\/\\$\\{projectId\\}\\/sections`/, 'router guard should redirect invalid section ids')

const layout = read('teamflow-ai-frontend/src/views/Layout.vue')
assert.match(layout, /<RouterView\\s+:key="\\$route\\.fullPath"\\s*\\/>/, 'layout should key nested route view by full path')
assert.match(layout, /usePositiveProjectId/, 'layout should use shared project id helper')

const sectionEditor = read('teamflow-ai-frontend/src/views/SectionEditor.vue')
assert.match(sectionEditor, /usePositiveProjectId/, 'section editor should use shared project helper')
assert.match(sectionEditor, /usePositiveSectionId/, 'section editor should use shared section helper')
assert.doesNotMatch(sectionEditor, /const toPositiveInteger = \\(raw\\) =>/, 'section editor should not keep a local route parser')

const projectViews = [
  'ProjectOverview.vue',
  'ProjectMembers.vue',
  'PermissionMatrix.vue',
  'SectionList.vue',
  'FileManage.vue',
  'AiReport.vue',
  'ProjectStatistics.vue',
  'ProjectExport.vue',
  'ProjectReviews.vue',
  'SectionReviewPage.vue',
  'TaskBoard.vue',
  'ProgressRecord.vue',
  'ManagementDelivery.vue'
]

for (const file of projectViews) {
  const source = read(`teamflow-ai-frontend/src/views/${file}`)
  assert.doesNotMatch(
    source,
    /const\\s+projectId\\s*=\\s*Number\\((?:useRoute\\(\\)|route)\\.params\\.id\\)/,
    `${file} should not parse projectId once with Number(route.params.id)`
  )
  assert.doesNotMatch(
    source,
    /const\\s+sectionId\\s*=\\s*Number\\((?:useRoute\\(\\)|route)\\.params\\.sectionId\\)/,
    `${file} should not parse sectionId once with Number(route.params.sectionId)`
  )
}

console.log('route parameter safety checks passed')
```

- [ ] **Step 2: Run the new check and verify RED**

Run:

```bash
cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-frontend
node scripts/check-route-param-safety.mjs
```

Expected: FAIL because `src/utils/routeParams.js` does not exist yet.

- [ ] **Step 3: Add package scripts**

Modify `teamflow-ai-frontend/package.json` so the scripts block becomes:

```json
{
  "dev": "vite --host 127.0.0.1",
  "build": "vite build",
  "preview": "vite preview --host 127.0.0.1",
  "check:join-redirect": "node scripts/check-join-redirect.mjs",
  "check:member-access": "node scripts/check-member-access-boundary.mjs",
  "check:profile": "node scripts/check-profile-feature.mjs",
  "check:project-workspace": "node scripts/check-project-workspace-editor.mjs",
  "check:route-params": "node scripts/check-route-param-safety.mjs",
  "check": "npm run check:join-redirect && npm run check:member-access && npm run check:profile && npm run check:project-workspace && npm run check:route-params"
}
```

- [ ] **Step 4: Commit if Git is available**

Run from the Git checkout root:

```bash
git add teamflow-ai-frontend/scripts/check-route-param-safety.mjs teamflow-ai-frontend/package.json
git commit -m "test: add route parameter safety check"
```

Expected: commit succeeds. In the current local workspace without `.git`, record that the commit step was skipped.

## Task 2: Add Shared Route Param Helper And Router Guard

**Files:**
- Create: `teamflow-ai-frontend/src/utils/routeParams.js`
- Modify: `teamflow-ai-frontend/src/router/index.js`
- Modify: `teamflow-ai-frontend/src/views/Layout.vue`
- Modify: `teamflow-ai-frontend/src/views/SectionEditor.vue`

- [ ] **Step 1: Add the helper**

Create `teamflow-ai-frontend/src/utils/routeParams.js`:

```js
import { computed } from 'vue'

export const toPositiveInteger = (raw) => {
  if (Array.isArray(raw) || raw == null) return null
  const text = String(raw)
  if (!/^[1-9]\d*$/.test(text)) return null
  const value = Number(text)
  return Number.isSafeInteger(value) ? value : null
}

export const usePositiveRouteParam = (route, name) => computed(() => toPositiveInteger(route.params[name]))

export const usePositiveProjectId = (route) => usePositiveRouteParam(route, 'id')

export const usePositiveSectionId = (route) => usePositiveRouteParam(route, 'sectionId')

export const isManagementDeliverySection = (raw) => !Array.isArray(raw) && raw === 'management-delivery'
```

- [ ] **Step 2: Update router guard**

In `teamflow-ai-frontend/src/router/index.js`, add the import:

```js
import { isManagementDeliverySection, toPositiveInteger } from '../utils/routeParams'
```

Then update `router.beforeEach` so it validates project routes after auth redirects:

```js
router.beforeEach((to) => {
  const token = localStorage.getItem('teamflow_token')
  const isPublic = to.matched.some((record) => record.meta.public)
  if (!token && !isPublic) {
    return toLoginWithRedirect(to.fullPath)
  }
  if (token && ['/login', '/register'].includes(to.path)) {
    return '/dashboard'
  }

  if (to.path.startsWith('/projects/') && to.params.id != null) {
    const projectId = toPositiveInteger(to.params.id)
    if (!projectId) {
      return '/projects'
    }
    if (to.params.sectionId != null && !isManagementDeliverySection(to.params.sectionId) && !toPositiveInteger(to.params.sectionId)) {
      return `/projects/${projectId}/sections`
    }
  }

  return true
})
```

- [ ] **Step 3: Update Layout.vue**

In `teamflow-ai-frontend/src/views/Layout.vue`, change:

```vue
<RouterView />
```

To:

```vue
<RouterView :key="$route.fullPath" />
```

Replace the local `projectId` computed block with:

```js
const projectId = usePositiveProjectId(route)
```

Add:

```js
import { usePositiveProjectId } from '../utils/routeParams'
```

- [ ] **Step 4: Update SectionEditor.vue**

In `teamflow-ai-frontend/src/views/SectionEditor.vue`, remove the local `toPositiveInteger` function and add:

```js
import { usePositiveProjectId, usePositiveSectionId } from '../utils/routeParams'
```

Replace:

```js
const projectId = computed(() => toPositiveInteger(route.params.id))
const sectionId = computed(() => toPositiveInteger(route.params.sectionId))
```

With:

```js
const projectId = usePositiveProjectId(route)
const sectionId = usePositiveSectionId(route)
```

If `computed` becomes unused after this edit, remove it from the Vue import.

- [ ] **Step 5: Run partial RED-to-GREEN check**

Run:

```bash
cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-frontend
node scripts/check-route-param-safety.mjs
```

Expected: FAIL on the first remaining project view that still contains unsafe one-time route parsing. The helper, router, layout, and section editor assertions should pass.

- [ ] **Step 6: Commit if Git is available**

Run from the Git checkout root:

```bash
git add teamflow-ai-frontend/src/utils/routeParams.js teamflow-ai-frontend/src/router/index.js teamflow-ai-frontend/src/views/Layout.vue teamflow-ai-frontend/src/views/SectionEditor.vue
git commit -m "fix: add route parameter guard"
```

Expected: commit succeeds. In the current local workspace without `.git`, record that the commit step was skipped.

## Task 3: Migrate Read-Only Project Pages

**Files:**
- Modify: `teamflow-ai-frontend/src/views/ProjectOverview.vue`
- Modify: `teamflow-ai-frontend/src/views/PermissionMatrix.vue`
- Modify: `teamflow-ai-frontend/src/views/SectionList.vue`
- Modify: `teamflow-ai-frontend/src/views/ProjectStatistics.vue`
- Modify: `teamflow-ai-frontend/src/views/ProjectReviews.vue`

- [ ] **Step 1: Migrate ProjectOverview.vue**

Use `watch(projectId, load, { immediate: true })` instead of `onMounted`. The script setup should import `watch`, keep `useRoute`, and import `usePositiveProjectId`:

```js
import { reactive, watch } from 'vue'
import { useRoute } from 'vue-router'
import { usePositiveProjectId } from '../utils/routeParams'
```

Use:

```js
const route = useRoute()
const projectId = usePositiveProjectId(route)
let loadRequestId = 0
```

Inside `load`, use `currentProjectId` for all API calls, router-link strings in script, and localStorage keys:

```js
const load = async () => {
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  const requestId = ++loadRequestId
  const [detail, score, trend] = await Promise.all([
    projectDetail(currentProjectId),
    loadCompleteness(currentProjectId).catch(() => ({})),
    loadRiskTrend(currentProjectId).catch(() => ({ snapshots: [] }))
  ])
  if (requestId !== loadRequestId || projectId.value !== currentProjectId) return
  Object.assign(project, detail)
  Object.assign(completeness, score)
  Object.assign(riskTrend, trend)

  const manualProgress = localStorage.getItem(`teamflow_project_progress_${currentProjectId}`)
  if (manualProgress !== null) {
    const val = Number(manualProgress)
    project.completenessScore = val
    completeness.score = val
  }
}

watch(projectId, load, { immediate: true })
```

- [ ] **Step 2: Migrate PermissionMatrix.vue**

Import `watch` and `usePositiveProjectId`, then replace the mounted load with:

```js
const route = useRoute()
const projectId = usePositiveProjectId(route)
let loadRequestId = 0

const load = async () => {
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  const requestId = ++loadRequestId
  const result = await projectPermissions(currentProjectId)
  if (requestId !== loadRequestId || projectId.value !== currentProjectId) return
  Object.assign(matrix, result)
}

watch(projectId, load, { immediate: true })
```

- [ ] **Step 3: Migrate SectionList.vue**

Replace the one-time project id and mounted load with:

```js
const route = useRoute()
const projectId = usePositiveProjectId(route)
let loadRequestId = 0
const load = async () => {
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  const requestId = ++loadRequestId
  const result = await projectSections(currentProjectId)
  if (requestId !== loadRequestId || projectId.value !== currentProjectId) return
  sections.value = result || []
}
watch(projectId, load, { immediate: true })
```

- [ ] **Step 4: Migrate ProjectStatistics.vue**

Use `watch(projectId, load, { immediate: true })`, capture `currentProjectId`, and use it for `completeness`, `contribution`, and `riskTrend`. Dispose existing charts before applying new data:

```js
const route = useRoute()
const projectId = usePositiveProjectId(route)
let loadRequestId = 0

const load = async () => {
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  const requestId = ++loadRequestId
  const [scoreRes, contributionRes, riskRes] = await Promise.all([
    completeness(currentProjectId),
    contribution(currentProjectId),
    riskTrend(currentProjectId)
  ])
  if (requestId !== loadRequestId || projectId.value !== currentProjectId) return
  Object.assign(score, scoreRes)
  Object.assign(contributionData, contributionRes)
  Object.assign(riskData, riskRes)
  renderCharts()
}
```

- [ ] **Step 5: Migrate ProjectReviews.vue**

Use `usePositiveProjectId`, guard `goReview`, and watch project id:

```js
const projectId = usePositiveProjectId(route)
let loadRequestId = 0

const goReview = (sectionId) => {
  if (!projectId.value) return
  router.push(`/projects/${projectId.value}/reviews/${sectionId}`)
}

const load = async () => {
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  const requestId = ++loadRequestId
  const result = await projectReviews(currentProjectId)
  if (requestId !== loadRequestId || projectId.value !== currentProjectId) return
  reviews.value = result || []
}

watch(projectId, load, { immediate: true })
```

- [ ] **Step 6: Run check and build**

Run:

```bash
cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-frontend
node scripts/check-route-param-safety.mjs
npm run build
```

Expected: route safety check still FAILS because action-heavy pages are not migrated yet. Build should pass.

- [ ] **Step 7: Commit if Git is available**

Run from the Git checkout root:

```bash
git add teamflow-ai-frontend/src/views/ProjectOverview.vue teamflow-ai-frontend/src/views/PermissionMatrix.vue teamflow-ai-frontend/src/views/SectionList.vue teamflow-ai-frontend/src/views/ProjectStatistics.vue teamflow-ai-frontend/src/views/ProjectReviews.vue
git commit -m "fix: make read-only project pages route reactive"
```

Expected: commit succeeds. In the current local workspace without `.git`, record that the commit step was skipped.

## Task 4: Migrate Action Project Pages

**Files:**
- Modify: `teamflow-ai-frontend/src/views/ProjectMembers.vue`
- Modify: `teamflow-ai-frontend/src/views/FileManage.vue`
- Modify: `teamflow-ai-frontend/src/views/AiReport.vue`
- Modify: `teamflow-ai-frontend/src/views/ProjectExport.vue`
- Modify: `teamflow-ai-frontend/src/views/SectionReviewPage.vue`
- Modify: `teamflow-ai-frontend/src/views/TaskBoard.vue`

- [ ] **Step 1: Migrate ProjectMembers.vue**

Use `projectId = usePositiveProjectId(route)`. Capture `currentProjectId` inside `load`, `approve`, and `reject`. Use `currentProjectId` for `listMembers`, `joinApplies`, `projectInitResult`, `approveJoinApply`, and `rejectJoinApply`. Watch project id:

```js
watch(projectId, load, { immediate: true })
```

- [ ] **Step 2: Migrate FileManage.vue**

Use `projectId = usePositiveProjectId(route)`. In `load` and `upload`, return early when no current id. Append `currentProjectId` to `FormData`:

```js
const upload = async ({ file }) => {
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  const form = new FormData()
  form.append('projectId', currentProjectId)
  form.append('documentType', documentType.value)
  form.append('file', file)
  await uploadFile(form)
  ElMessage.success('上传成功')
  load()
}
```

- [ ] **Step 3: Migrate AiReport.vue**

Use `projectId = usePositiveProjectId(route)`. In `generate`, capture `currentProjectId` once and use it for all AI calls:

```js
const currentProjectId = projectId.value
if (!currentProjectId) return
```

Watch project id for `load`.

- [ ] **Step 4: Migrate ProjectExport.vue**

Use `projectId = usePositiveProjectId(route)`. In `load` and `create`, capture `currentProjectId`. Use `currentProjectId` for `projectSections`, `exportHistory`, and `createExport`.

- [ ] **Step 5: Migrate SectionReviewPage.vue**

Use both helpers:

```js
const projectId = usePositiveProjectId(route)
const sectionId = usePositiveSectionId(route)
```

In the template, computed refs unwrap automatically, so existing template strings can stay as `` `/projects/${projectId}/reviews` ``.

In script functions, use `.value`:

```js
const load = async () => {
  const currentSectionId = sectionId.value
  if (!currentSectionId) return
  const detail = await sectionDetail(currentSectionId)
  Object.assign(section, detail)
}
```

In `handleReview`, guard both ids and use `.value`.

- [ ] **Step 6: Migrate TaskBoard.vue**

Use `projectId = usePositiveProjectId(route)`. Change `blankForm` so it reads `projectId.value`:

```js
const blankForm = () => ({
  projectId: projectId.value,
  sectionId: null,
  title: '',
  description: '',
  assigneeId: null,
  priority: 'MEDIUM',
  status: 'TODO',
  startDate: '',
  dueDate: '',
  blockReason: ''
})
```

Watch project id and reload tasks/members/sections. In `submit`, set `form.projectId = projectId.value` before `createTask(form)`.

- [ ] **Step 7: Run check and build**

Run:

```bash
cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-frontend
node scripts/check-route-param-safety.mjs
npm run build
```

Expected: route safety check still FAILS only if `ProgressRecord.vue` or `ManagementDelivery.vue` still has unsafe project id parsing. Build should pass.

- [ ] **Step 8: Commit if Git is available**

Run from the Git checkout root:

```bash
git add teamflow-ai-frontend/src/views/ProjectMembers.vue teamflow-ai-frontend/src/views/FileManage.vue teamflow-ai-frontend/src/views/AiReport.vue teamflow-ai-frontend/src/views/ProjectExport.vue teamflow-ai-frontend/src/views/SectionReviewPage.vue teamflow-ai-frontend/src/views/TaskBoard.vue
git commit -m "fix: make action project pages route reactive"
```

Expected: commit succeeds. In the current local workspace without `.git`, record that the commit step was skipped.

## Task 5: Migrate Progress And Management Pages

**Files:**
- Modify: `teamflow-ai-frontend/src/views/ProgressRecord.vue`
- Modify: `teamflow-ai-frontend/src/views/ManagementDelivery.vue`

- [ ] **Step 1: Migrate ProgressRecord.vue**

Replace `const projectId = Number(useRoute().params.id)` with:

```js
const route = useRoute()
const projectId = usePositiveProjectId(route)
```

Use `.value` for all script-side project id reads:

- `listMembers(projectId.value)`
- `` `teamflow_wbs_${projectId.value}` ``
- `saveProjectWbs(projectId.value, payloadStr)`
- `` router.push(`/projects/${projectId.value}/sections/management-delivery`) ``
- `extractProgress(projectId.value, data)`
- `projectTasks(projectId.value)`
- `projectSections(projectId.value)`
- `progressStatus(projectId.value)`
- `listReports(projectId.value)`
- `submitReport(form)` after setting `form.projectId = projectId.value`

Change `blankReport` and the initial `form` to use current values:

```js
const blankReport = () => ({
  projectId: projectId.value,
  relatedTaskId: null,
  relatedSectionId: null,
  completedWork: '',
  problems: '',
  helpNeeded: '',
  nextPlan: '',
  reportPeriod: need.value?.reportPeriod || ''
})

const form = reactive(blankReport())
```

Use `watch(projectId, load, { immediate: true })` instead of `onMounted(load)`.

- [ ] **Step 2: Migrate ManagementDelivery.vue**

Replace `const projectId = Number(route.params.id)` with:

```js
import { usePositiveProjectId } from '../utils/routeParams'
const projectId = usePositiveProjectId(route)
```

For script-side reads, use `projectId.value`. Update localStorage keys and API calls:

- `projectDetail(projectId.value)`
- `listMembers(projectId.value)`
- `saveProjectWbs(projectId.value, payloadStr)`
- `` localStorage.getItem(`teamflow_wbs_${projectId.value}`) ``
- `` localStorage.setItem(`teamflow_wbs_${projectId.value}`, payloadStr) ``
- `projectSections(projectId.value)`

Before every async loader or save function, guard:

```js
if (!projectId.value) return
```

Keep the existing component structure and UI unchanged.

- [ ] **Step 3: Run route parameter check**

Run:

```bash
cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-frontend
node scripts/check-route-param-safety.mjs
```

Expected: PASS and print:

```text
route parameter safety checks passed
```

- [ ] **Step 4: Run all frontend checks**

Run:

```bash
cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-frontend
npm run check
```

Expected: PASS and include:

```text
join redirect checks passed
member access boundary checks passed
profile feature checks passed
project workspace editor checks passed
route parameter safety checks passed
```

- [ ] **Step 5: Commit if Git is available**

Run from the Git checkout root:

```bash
git add teamflow-ai-frontend/src/views/ProgressRecord.vue teamflow-ai-frontend/src/views/ManagementDelivery.vue
git commit -m "fix: make progress pages route reactive"
```

Expected: commit succeeds. In the current local workspace without `.git`, record that the commit step was skipped.

## Task 6: Final Verification

**Files:**
- Verify only; no planned file edits.

- [ ] **Step 1: Run full verification**

Run:

```bash
cd /Users/dexley/Documents/TeamFlowAI
bash scripts/verify.sh
```

Expected: exit `0`. Frontend checks pass, Vite build passes, backend tests run when Maven exists or print the known Maven warning when `mvn` is unavailable.

- [ ] **Step 2: Run focused route check**

Run:

```bash
cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-frontend
node scripts/check-route-param-safety.mjs
```

Expected:

```text
route parameter safety checks passed
```

- [ ] **Step 3: Record backend verification limitation**

If `mvn` is unavailable, record:

```text
Backend Maven tests were not executed locally because mvn is missing and the project has no Maven Wrapper. CI is configured to run mvn test.
```

## Completion Criteria

- Invalid `/projects/:id` routes redirect to `/projects` before project API calls.
- Invalid numeric section/review params redirect to the section list.
- The special `/projects/:id/sections/management-delivery` route remains usable.
- Project pages no longer contain one-time `Number(route.params.id)` or `Number(route.params.sectionId)` parsing.
- Project pages use computed route params or full-path remounting so navigation between project ids does not reuse stale ids.
- `npm run check` includes the route parameter safety check.
- `bash scripts/verify.sh` exits `0` with the known local Maven limitation if Maven is unavailable.

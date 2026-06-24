# Project Workspace And Section Editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace project pages with a project-specific sidebar and upgrade section editing with explicit edit mode, insert tools, previews, inline images, and unsaved-change protection.

**Architecture:** Keep the existing Spring Boot and Vue route structure. In `Layout.vue`, switch the left sidebar content based on whether the current route is inside `/projects/:id/*`; project routes render a new `ProjectSidebar` and non-project routes render the existing global menu. Keep section content stored as text, but parse fenced blocks for tables, charts, PlantUML, and images on the frontend.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis-Plus, Vue 3, Vue Router, Element Plus, ECharts, Axios.

---

## File Structure Map

### Backend

- Modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/controller/FileController.java`
  - Add optional `sectionId` upload parameter and `GET /api/files/inline/{id}`.
- Modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/FileBizService.java`
  - Persist `sectionId`, validate image MIME types for inline display, and return inline resources.
- Modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/SectionService.java`
  - Allow saving a new draft from an approved section while keeping reviewing sections locked.

### Frontend Checks

- Create: `teamflow-ai-frontend/scripts/check-project-workspace-editor.mjs`
  - Static regression check for project sidebar, section editor mode, insert tools, inline image API, and route guards.

### Frontend API

- Modify: `teamflow-ai-frontend/src/api/file.js`
  - Add `inlineFileUrl(id)` helper.

### Frontend Project Navigation

- Create: `teamflow-ai-frontend/src/components/ProjectSidebar.vue`
  - Project-specific sidebar with home, project detail group, and section list.
- Modify: `teamflow-ai-frontend/src/views/Layout.vue`
  - Render `ProjectSidebar` for project routes and global menu elsewhere.
- Modify: project pages currently importing `ProjectNav.vue`
  - Remove top horizontal `ProjectNav` usage from:
    - `teamflow-ai-frontend/src/views/ProjectOverview.vue`
    - `teamflow-ai-frontend/src/views/ProjectMembers.vue`
    - `teamflow-ai-frontend/src/views/PermissionMatrix.vue`
    - `teamflow-ai-frontend/src/views/SectionList.vue`
    - `teamflow-ai-frontend/src/views/SectionEditor.vue`
    - `teamflow-ai-frontend/src/views/FileManage.vue`
    - `teamflow-ai-frontend/src/views/TaskBoard.vue`
    - `teamflow-ai-frontend/src/views/ProgressRecord.vue`
    - `teamflow-ai-frontend/src/views/AiReport.vue`
    - `teamflow-ai-frontend/src/views/ProjectStatistics.vue`
    - `teamflow-ai-frontend/src/views/ProjectExport.vue`

### Frontend Section Editor

- Create: `teamflow-ai-frontend/src/utils/sectionContentBlocks.js`
  - Parse and insert Markdown tables, chart blocks, PlantUML blocks, and image markdown.
- Create: `teamflow-ai-frontend/src/utils/plantuml.js`
  - Build PlantUML preview URLs from configurable server base URL when possible; return source fallback when unavailable.
- Create: `teamflow-ai-frontend/src/components/section/SectionChartBlock.vue`
  - Render `teamflow-chart` blocks with ECharts.
- Create: `teamflow-ai-frontend/src/components/section/SectionContentRenderer.vue`
  - Render parsed text, tables, charts, PlantUML blocks, and images.
- Create: `teamflow-ai-frontend/src/components/section/ChartInsertDialog.vue`
  - Dialog for chart type, title, categories, and values.
- Create: `teamflow-ai-frontend/src/components/section/PlantUmlPanel.vue`
  - Small PlantUML editor and preview panel.
- Modify: `teamflow-ai-frontend/src/views/SectionEditor.vue`
  - Add view/edit mode, dirty tracking, insert tools, image upload, preview, and route-leave protection.

---

### Task 1: Feature Check Script

**Files:**
- Create: `teamflow-ai-frontend/scripts/check-project-workspace-editor.mjs`

- [ ] **Step 1: Write the failing check script**

Create `teamflow-ai-frontend/scripts/check-project-workspace-editor.mjs` with these assertions:

```js
import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const workspaceRoot = resolve(root, '..')

const read = (path) => readFileSync(resolve(workspaceRoot, path), 'utf8')
const exists = (path) => existsSync(resolve(workspaceRoot, path))

assert.ok(exists('teamflow-ai-frontend/src/components/ProjectSidebar.vue'), 'project sidebar component should exist')
assert.ok(exists('teamflow-ai-frontend/src/components/section/SectionContentRenderer.vue'), 'section renderer should exist')
assert.ok(exists('teamflow-ai-frontend/src/components/section/SectionChartBlock.vue'), 'chart block component should exist')
assert.ok(exists('teamflow-ai-frontend/src/components/section/ChartInsertDialog.vue'), 'chart insert dialog should exist')
assert.ok(exists('teamflow-ai-frontend/src/components/section/PlantUmlPanel.vue'), 'PlantUML panel should exist')
assert.ok(exists('teamflow-ai-frontend/src/utils/sectionContentBlocks.js'), 'content block utility should exist')
assert.ok(exists('teamflow-ai-frontend/src/utils/plantuml.js'), 'PlantUML utility should exist')

const layout = read('teamflow-ai-frontend/src/views/Layout.vue')
assert.match(layout, /ProjectSidebar/, 'Layout should render ProjectSidebar for project routes')
assert.match(layout, /isProjectRoute/, 'Layout should decide when route is inside a project')

const projectSidebar = read('teamflow-ai-frontend/src/components/ProjectSidebar.vue')
assert.match(projectSidebar, /首页/, 'project sidebar should include home link')
assert.match(projectSidebar, /项目详情/, 'project sidebar should include project detail group')
assert.match(projectSidebar, /具体章节/, 'project sidebar should include concrete sections')
assert.match(projectSidebar, /projectSections/, 'project sidebar should load sections')

const sectionEditor = read('teamflow-ai-frontend/src/views/SectionEditor.vue')
assert.match(sectionEditor, /isEditing/, 'section editor should have explicit edit mode')
assert.match(sectionEditor, /dirty/, 'section editor should track dirty state')
assert.match(sectionEditor, /onBeforeRouteLeave/, 'section editor should guard route changes')
assert.match(sectionEditor, /beforeunload/, 'section editor should guard browser unload')
assert.match(sectionEditor, /插入表格/, 'section editor should offer table insertion')
assert.match(sectionEditor, /插入图表/, 'section editor should offer chart insertion')
assert.match(sectionEditor, /插入 PlantUML/, 'section editor should offer PlantUML insertion')
assert.match(sectionEditor, /插入图片/, 'section editor should offer image insertion')
assert.match(sectionEditor, /uploadFile/, 'section editor should upload inline images')

const fileController = read('teamflow-ai-backend/src/main/java/com/example/teamflow/controller/FileController.java')
assert.match(fileController, /sectionId/, 'file upload should accept sectionId')
assert.match(fileController, /inline/, 'file controller should expose inline image endpoint')

const fileService = read('teamflow-ai-backend/src/main/java/com/example/teamflow/service/FileBizService.java')
assert.match(fileService, /setSectionId/, 'file upload should persist sectionId')
assert.match(fileService, /isInlineImage/, 'file service should validate inline image MIME types')

const sectionService = read('teamflow-ai-backend/src/main/java/com/example/teamflow/service/SectionService.java')
assert.match(sectionService, /APPROVED/, 'section service should handle approved sections when saving new drafts')
assert.doesNotMatch(sectionService, /APPROVED\\.equalsIgnoreCase\\(section\\.getStatus\\(\\)\\).*throw new BizException/s, 'approved sections should not be locked out of draft saving')
assert.match(sectionService, /REVIEWING\\.equalsIgnoreCase\\(section\\.getStatus\\(\\)\\)/, 'reviewing sections should remain locked')

for (const file of [
  'ProjectOverview.vue',
  'ProjectMembers.vue',
  'PermissionMatrix.vue',
  'SectionList.vue',
  'SectionEditor.vue',
  'FileManage.vue',
  'TaskBoard.vue',
  'ProgressRecord.vue',
  'AiReport.vue',
  'ProjectStatistics.vue',
  'ProjectExport.vue'
]) {
  const source = read(`teamflow-ai-frontend/src/views/${file}`)
  assert.doesNotMatch(source, /<ProjectNav/, `${file} should not render the old horizontal ProjectNav`)
}

console.log('project workspace editor checks passed')
```

- [ ] **Step 2: Run the check and confirm it fails**

Run:

```bash
cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-frontend
node scripts/check-project-workspace-editor.mjs
```

Expected: exit code `1`, failing first on missing `ProjectSidebar.vue`.

---

### Task 2: Backend Inline Images And Draft Saving

**Files:**
- Modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/controller/FileController.java`
- Modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/FileBizService.java`
- Modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/SectionService.java`

- [ ] **Step 1: Extend upload parameters**

In `FileController.upload`, add optional `sectionId`:

```java
@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public Result<FileVO> upload(@RequestParam Long projectId,
                             @RequestParam(required = false) Long sectionId,
                             @RequestParam(required = false) String documentType,
                             @RequestPart("file") MultipartFile file) {
    return Result.success(fileBizService.upload(projectId, sectionId, documentType, file));
}
```

- [ ] **Step 2: Add inline endpoint**

In `FileController`, add:

```java
@GetMapping("/inline/{id}")
public ResponseEntity<Resource> inline(@PathVariable Long id) {
    ProjectFile file = fileBizService.getInlineImageEntity(id);
    Resource resource = fileBizService.loadResource(file);
    MediaType mediaType = MediaType.parseMediaType(file.getFileType());
    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
            .contentType(mediaType)
            .body(resource);
}
```

- [ ] **Step 3: Update service signature, section validation, and persistence**

Add these imports to `FileBizService`:

```java
import com.example.teamflow.entity.ProjectSection;
import com.example.teamflow.mapper.ProjectSectionMapper;
```

Add this constructor dependency field near the other mapper fields:

```java
private final ProjectSectionMapper projectSectionMapper;
```

Change `FileBizService.upload` to this complete method:

```java
public FileVO upload(Long projectId, Long sectionId, String documentType, MultipartFile file) {
    projectService.getProject(projectId);
    if (sectionId != null) {
        ProjectSection section = projectSectionMapper.selectById(sectionId);
        if (section == null || !Objects.equals(projectId, section.getProjectId())) {
            throw new BizException(400, "章节不属于当前项目");
        }
    }
    if (file == null || file.isEmpty()) {
        throw new BizException(400, "上传文件不能为空");
    }
    try {
        Path base = Paths.get(uploadPath).toAbsolutePath().normalize();
        Files.createDirectories(base);
        String original = safeOriginalName(file.getOriginalFilename());
        String storedName = UUID.randomUUID() + "_" + original;
        Path target = base.resolve(storedName).normalize();
        if (!target.startsWith(base)) {
            throw new BizException(400, "文件名非法");
        }
        file.transferTo(target);
        SysUser current = authService.currentUser();
        ProjectFile projectFile = new ProjectFile();
        projectFile.setProjectId(projectId);
        projectFile.setSectionId(sectionId);
        projectFile.setUploaderId(current.getId());
        projectFile.setFileName(original);
        projectFile.setFileType(file.getContentType());
        projectFile.setFileUrl(target.toString());
        projectFile.setFileSize(file.getSize());
        projectFile.setDocumentType(StringUtils.hasText(documentType) ? documentType : "OTHER");
        projectFile.setCreateTime(LocalDateTime.now());
        projectFileMapper.insert(projectFile);
        return toVO(projectFile);
    } catch (IOException exception) {
        throw new BizException(500, "文件上传失败：" + exception.getMessage());
    }
}
```

- [ ] **Step 4: Add inline image validation**

In `FileBizService`, add:

```java
public ProjectFile getInlineImageEntity(Long id) {
    ProjectFile file = getEntity(id);
    if (!isInlineImage(file.getFileType())) {
        throw new BizException(400, "该文件不是可预览图片");
    }
    return file;
}

private boolean isInlineImage(String fileType) {
    return "image/png".equalsIgnoreCase(fileType)
            || "image/jpeg".equalsIgnoreCase(fileType)
            || "image/gif".equalsIgnoreCase(fileType)
            || "image/webp".equalsIgnoreCase(fileType);
}
```

- [ ] **Step 5: Allow approved sections to start a new draft**

In `SectionService.saveDraft`, replace the current lock condition with:

```java
if (REVIEWING.equalsIgnoreCase(section.getStatus())) {
    throw new BizException(400, "当前章节正在审核中，暂不可编辑");
}
if (latest != null && REVIEWING.equalsIgnoreCase(latest.getSubmitStatus())) {
    throw new BizException(400, "当前章节内容正在审核中，暂不可编辑");
}
```

Keep the existing insert-new-version behavior, so saving from `APPROVED` creates a new `DRAFT` version and sets `section.status` to `DRAFT`.

- [ ] **Step 6: Run static feature check**

Run:

```bash
cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-frontend
node scripts/check-project-workspace-editor.mjs
```

Expected: still fails because frontend files are not implemented yet, but backend-related assertions pass once frontend files exist. This task is complete after code inspection confirms `sectionId`, `inline`, `setSectionId`, `isInlineImage`, and approved draft saving are present.

---

### Task 3: Project Sidebar

**Files:**
- Create: `teamflow-ai-frontend/src/components/ProjectSidebar.vue`
- Modify: `teamflow-ai-frontend/src/views/Layout.vue`
- Modify: all project views listed in the File Structure Map to remove `ProjectNav`

- [ ] **Step 1: Create `ProjectSidebar.vue`**

Create a component with this behavior:

```vue
<template>
  <div class="project-sidebar">
    <div class="project-sidebar__brand">TeamFlowAI</div>
    <el-menu router :default-active="$route.path" background-color="#111827" text-color="#cbd5e1" active-text-color="#ffffff">
      <el-menu-item index="/dashboard">首页</el-menu-item>
      <el-sub-menu index="project-detail">
        <template #title>项目详情</template>
        <el-menu-item :index="base + '/overview'">概述</el-menu-item>
        <el-menu-item :index="base + '/members'">成员与申请</el-menu-item>
        <el-menu-item :index="base + '/permissions'">权限矩阵</el-menu-item>
        <el-menu-item :index="base + '/files'">附件</el-menu-item>
        <el-menu-item :index="base + '/tasks'">任务</el-menu-item>
        <el-menu-item :index="base + '/progress'">进度</el-menu-item>
        <el-menu-item :index="base + '/ai'">AI</el-menu-item>
        <el-menu-item :index="base + '/statistics'">统计</el-menu-item>
        <el-menu-item :index="base + '/export'">导出</el-menu-item>
      </el-sub-menu>
      <el-sub-menu index="project-sections">
        <template #title>具体章节</template>
        <el-menu-item v-for="section in sections" :key="section.id" :index="`${base}/sections/${section.id}`">
          <span>{{ section.sectionName }}</span>
          <el-tag size="small" :type="statusType(section.status)">{{ statusText(section.status) }}</el-tag>
        </el-menu-item>
      </el-sub-menu>
    </el-menu>
  </div>
</template>
```

Use `projectSections(projectId)` in `onMounted` and `watch(() => props.projectId, load, { immediate: true })`.

- [ ] **Step 2: Switch `Layout.vue` sidebar content**

In `Layout.vue`, import `useRoute` and `ProjectSidebar`. Add:

```js
const route = useRoute()
const projectId = computed(() => route.params.id ? Number(route.params.id) : null)
const isProjectRoute = computed(() => route.path.startsWith('/projects/') && projectId.value)
```

In template:

```vue
<el-aside width="236px" class="aside">
  <ProjectSidebar v-if="isProjectRoute" :project-id="projectId" />
  <template v-else>
    <div class="brand">TeamFlowAI</div>
    <el-menu router :default-active="$route.path" background-color="#111827" text-color="#cbd5e1" active-text-color="#ffffff">
      <!-- existing global menu -->
    </el-menu>
  </template>
</el-aside>
```

- [ ] **Step 3: Remove old `ProjectNav` from project pages**

For each listed project view:

- Delete `<ProjectNav :project-id="projectId" />`.
- Delete `import ProjectNav from '../components/ProjectNav.vue'`.
- Leave route parameter logic intact.

- [ ] **Step 4: Run frontend build**

Run:

```bash
cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-frontend
npm run build
```

Expected: exit code `0`.

---

### Task 4: Content Block Utilities And Renderer

**Files:**
- Create: `teamflow-ai-frontend/src/utils/sectionContentBlocks.js`
- Create: `teamflow-ai-frontend/src/utils/plantuml.js`
- Create: `teamflow-ai-frontend/src/components/section/SectionChartBlock.vue`
- Create: `teamflow-ai-frontend/src/components/section/SectionContentRenderer.vue`

- [ ] **Step 1: Add block parser utility**

Create `sectionContentBlocks.js` with exported functions:

```js
export const tableTemplate = `| 列名 A | 列名 B |
| --- | --- |
| 示例 A | 示例 B |`

export const chartTemplate = (type = 'bar') => `\`\`\`teamflow-chart
{
  "type": "${type}",
  "title": "图表标题",
  "categories": ["类别 A", "类别 B", "类别 C"],
  "series": [
    { "name": "数值", "data": [3, 5, 2] }
  ]
}
\`\`\``

export const plantUmlTemplate = `\`\`\`plantuml
@startuml
用户 -> 系统: 提交内容
系统 --> 用户: 返回结果
@enduml
\`\`\``

export const imageMarkdown = (alt, url) => `![${alt || '图片'}](${url})`

export const appendBlock = (body, block) => [body || '', block].filter(Boolean).join('\\n\\n')
```

Also export `parseSectionBlocks(body)` that detects fenced `teamflow-chart`, fenced `plantuml`, markdown image lines, markdown tables, and plain text chunks.

- [ ] **Step 2: Add PlantUML helper**

Create `plantuml.js` with:

```js
export const plantUmlServerBase = import.meta.env.VITE_PLANTUML_SERVER || ''

export const canRenderPlantUml = () => Boolean(plantUmlServerBase)

export const plantUmlPreviewState = (source) => {
  if (!canRenderPlantUml()) {
    return {
      available: false,
      source,
      message: '未配置 PlantUML 渲染服务，已保留源码。'
    }
  }
  return {
    available: true,
    source,
    message: 'PlantUML 渲染服务已配置。'
  }
}
```

First implementation keeps saving independent from rendering; a later enhancement can add encoded SVG URLs.

- [ ] **Step 3: Render ECharts chart blocks**

Create `SectionChartBlock.vue`. It should:

- Accept `chart` prop.
- Initialize ECharts on mount.
- Watch chart changes.
- Dispose chart on unmount.
- Render bar, line, and pie using the `chart.type`, `chart.title`, `chart.categories`, and `chart.series` fields.

- [ ] **Step 4: Render parsed section content**

Create `SectionContentRenderer.vue`. It should:

- Accept `body` prop.
- Use `parseSectionBlocks(body)`.
- Render:
  - text chunks in paragraphs.
  - table blocks as `<table>`.
  - chart blocks through `SectionChartBlock`.
  - PlantUML blocks as source fallback plus status message from `plantUmlPreviewState`.
  - image blocks as `<img :src="url" :alt="alt">`.

- [ ] **Step 5: Run build**

Run:

```bash
cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-frontend
npm run build
```

Expected: exit code `0`.

---

### Task 5: Insert Dialogs And Section Edit Mode

**Files:**
- Create: `teamflow-ai-frontend/src/components/section/ChartInsertDialog.vue`
- Create: `teamflow-ai-frontend/src/components/section/PlantUmlPanel.vue`
- Modify: `teamflow-ai-frontend/src/api/file.js`
- Modify: `teamflow-ai-frontend/src/views/SectionEditor.vue`

- [ ] **Step 1: Extend file API**

In `src/api/file.js`, add:

```js
export const inlineFileUrl = (id) => `/api/files/inline/${id}`
```

- [ ] **Step 2: Add chart insert dialog**

Create `ChartInsertDialog.vue` with:

- `visible` prop.
- `@update:visible`.
- `@insert`.
- Form fields:
  - `type`: `bar`, `line`, `pie`.
  - `title`.
  - `categoriesText`, comma-separated.
  - `valuesText`, comma-separated numbers.

On insert, emit a `teamflow-chart` block using `chartTemplate(type)` shape with the entered values.

- [ ] **Step 3: Add PlantUML panel**

Create `PlantUmlPanel.vue` with:

- `visible` prop.
- textarea for PlantUML source.
- preview area using `plantUmlPreviewState(source)`.
- insert button that emits a fenced `plantuml` block.

- [ ] **Step 4: Refactor `SectionEditor.vue` view/edit mode**

Add these state variables:

```js
const isEditing = ref(false)
const snapshot = reactive({ title: '', body: '' })
const dirty = computed(() => form.title !== snapshot.title || form.body !== snapshot.body)
const showChartDialog = ref(false)
const showPlantUmlPanel = ref(false)
```

Default view mode:

- Render `SectionContentRenderer :body="form.body"`.
- Show `编辑` button only when `canEdit && section.status !== 'REVIEWING'`.

Edit mode:

- Enable title/body inputs.
- Show insert toolbar.
- Show live preview.
- Save draft and submit buttons use existing APIs.

- [ ] **Step 5: Add insert tool handlers**

In `SectionEditor.vue`, add:

```js
const insertTable = () => {
  form.body = appendBlock(form.body, tableTemplate)
}

const insertChart = (block) => {
  form.body = appendBlock(form.body, block)
  showChartDialog.value = false
}

const insertPlantUml = (block) => {
  form.body = appendBlock(form.body, block)
  showPlantUmlPanel.value = false
}
```

- [ ] **Step 6: Add image upload insertion**

Use Element Plus upload with custom request. Handler:

```js
const uploadImage = async ({ file }) => {
  const formData = new FormData()
  formData.append('projectId', projectId)
  formData.append('sectionId', sectionId)
  formData.append('documentType', 'IMAGE')
  formData.append('file', file)
  const uploaded = await uploadFile(formData)
  form.body = appendBlock(form.body, imageMarkdown(uploaded.fileName, inlineFileUrl(uploaded.id)))
  ElMessage.success('图片已插入')
}
```

Restrict file picker with `accept="image/png,image/jpeg,image/gif,image/webp"`.

- [ ] **Step 7: Reset snapshot after load/save**

After `fillForm(content)`, call:

```js
snapshot.title = form.title
snapshot.body = form.body
```

After successful save draft:

```js
ElMessage.success('草稿已保存')
await load()
isEditing.value = false
```

Keep submit behavior tied to the latest saved content.

- [ ] **Step 8: Run feature check**

Run:

```bash
cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-frontend
node scripts/check-project-workspace-editor.mjs
```

Expected: route guard assertions may still fail until Task 6; insert tool assertions pass.

---

### Task 6: Unsaved Change Guard

**Files:**
- Modify: `teamflow-ai-frontend/src/views/SectionEditor.vue`

- [ ] **Step 1: Add save-or-discard helper**

Use Element Plus `ElMessageBox.confirm`:

```js
const confirmUnsavedChanges = async () => {
  if (!dirty.value) return true
  try {
    await ElMessageBox.confirm(
      '章节内容有未保存更新，是否保存并更新章节内容？',
      '未保存更新',
      {
        confirmButtonText: '保存并继续',
        cancelButtonText: '继续不保存',
        distinguishCancelAndClose: true,
        type: 'warning'
      }
    )
    await saveDraft()
    return true
  } catch (action) {
    if (action === 'cancel') {
      return true
    }
    return false
  }
}
```

- [ ] **Step 2: Guard route changes**

Import `onBeforeRouteLeave` and add:

```js
onBeforeRouteLeave(async () => {
  if (!isEditing.value || !dirty.value) return true
  return await confirmUnsavedChanges()
})
```

- [ ] **Step 3: Guard browser refresh and close**

Add:

```js
const beforeUnload = (event) => {
  if (!isEditing.value || !dirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

onMounted(() => {
  window.addEventListener('beforeunload', beforeUnload)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', beforeUnload)
})
```

If `onMounted(load)` already exists, combine the listener setup into the same mounted callback.

- [ ] **Step 4: Apply guard to local buttons**

For buttons that leave the page manually, such as return-to-section-list or exit-edit:

- Return-to-section-list should rely on router navigation and `onBeforeRouteLeave`.
- Exit-edit should call `confirmUnsavedChanges()` before leaving edit mode.

- [ ] **Step 5: Run checks**

Run:

```bash
cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-frontend
node scripts/check-project-workspace-editor.mjs
npm run build
```

Expected: both commands exit with code `0`.

---

### Task 7: Final Verification

**Files:**
- All files touched by previous tasks.

- [ ] **Step 1: Run all frontend feature checks**

Run:

```bash
cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-frontend
node scripts/check-project-workspace-editor.mjs
node scripts/check-profile-feature.mjs
node scripts/check-member-access-boundary.mjs
node scripts/check-join-redirect.mjs
```

Expected: every script exits with code `0`.

- [ ] **Step 2: Run frontend production build**

Run:

```bash
cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-frontend
npm run build
```

Expected: exit code `0`. Existing Vite chunk-size warnings are acceptable.

- [ ] **Step 3: Attempt backend compile when Maven is available**

Run:

```bash
cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-backend
mvn test -DskipTests
```

Expected when Maven is installed: exit code `0`.

If `mvn` is unavailable, record that backend compile was not run because the local environment has no Maven command and the project has no Maven wrapper.

- [ ] **Step 4: Manual browser smoke test**

Start the frontend:

```bash
cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-frontend
npm run dev
```

Smoke-test these pages:

- `/projects/1/overview`: left sidebar is project-specific and no horizontal project nav appears.
- `/projects/1/sections/1`: page opens in view mode.
- Click `编辑`: title/body become editable and insert buttons appear.
- Insert chart: preview renders an ECharts chart.
- Insert PlantUML: source block appears and preview fallback is visible when no renderer is configured.
- Insert image: uploaded image appears inline in preview.
- Change body, then navigate away: unsaved-change prompt appears.

- [ ] **Step 5: Record checkpoint**

Because `/Users/dexley/Documents/TeamFlowAI` is not a Git repository, do not run `git commit`. Record the verification commands and changed files in the task summary.

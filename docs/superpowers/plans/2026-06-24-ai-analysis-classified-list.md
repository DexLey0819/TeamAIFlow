# AI 智能分析分类列表 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 AI 智能分析改造成按类型分类的分页摘要列表，并通过详情弹窗展示完整 AI 内容。

**Architecture:** 使用一个纯 JavaScript 展示配置模块统一类型、用途、状态、分类和分页规则；`AiAnalysisList.vue` 负责分类标签与分页表格，`AiRecordDetailDialog.vue` 负责完整内容弹窗，`ManagementDelivery.vue` 仅负责请求、生成和组合状态。现有后端接口与数据结构保持不变。

**Tech Stack:** Vue 3 Composition API、Element Plus、Vite、Node.js `assert` 检查脚本。

---

## 文件结构

- Create: `teamflow-ai-frontend/src/utils/aiRecordPresentation.js` — AI 记录的类型配置、分类、用途、状态和分页纯函数。
- Create: `teamflow-ai-frontend/src/components/ai/AiAnalysisList.vue` — 分类标签、用途说明、分页表格、错误与空状态。
- Create: `teamflow-ai-frontend/src/components/ai/AiRecordDetailDialog.vue` — AI 记录详情弹窗。
- Create: `teamflow-ai-frontend/scripts/check-ai-analysis-list.mjs` — 纯函数行为测试与 Vue 组件结构检查。
- Modify: `teamflow-ai-frontend/src/views/ManagementDelivery.vue` — 接入列表、弹窗和生成后定位逻辑。
- Modify: `teamflow-ai-frontend/package.json` — 注册 AI 分类列表检查并纳入总检查。
- Preserve: `teamflow-ai-frontend/src/components/AiResultPanel.vue` — 当前文件已有未提交修改；本任务只移除页面引用，不覆盖或删除该文件。

### Task 1: AI 记录展示规则与分页纯函数

**Files:**
- Create: `teamflow-ai-frontend/src/utils/aiRecordPresentation.js`
- Create: `teamflow-ai-frontend/scripts/check-ai-analysis-list.mjs`

- [ ] **Step 1: 写入失败的纯函数行为测试**

创建 `teamflow-ai-frontend/scripts/check-ai-analysis-list.mjs`：

```js
import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  AI_RECORD_CATEGORIES,
  filterAiRecords,
  getAiCategoryCounts,
  getAiRecordPresentation,
  getAiStatusPresentation,
  paginateAiRecords
} from '../src/utils/aiRecordPresentation.js'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const read = (path) => readFileSync(resolve(root, path), 'utf8')
const exists = (path) => existsSync(resolve(root, path))

const records = [
  { id: 1, type: 'WEEKLY_REPORT' },
  { id: 2, type: 'RISK_ANALYSIS' },
  { id: 3, type: 'WEEKLY_REPORT' },
  { id: 4, type: 'FUTURE_ANALYSIS' }
]

assert.deepEqual(
  AI_RECORD_CATEGORIES.map((category) => category.key),
  ['ALL', 'WEEKLY_REPORT', 'RISK_ANALYSIS', 'DOC_CHECK', 'SUMMARY_REPORT', 'SECTION_GENERATE', 'OTHER']
)
assert.equal(getAiRecordPresentation('WEEKLY_REPORT').label, '项目周报')
assert.match(getAiRecordPresentation('RISK_ANALYSIS').purpose, /项目预警/)
assert.equal(getAiRecordPresentation('FUTURE_ANALYSIS').category, 'OTHER')
assert.equal(getAiRecordPresentation('FUTURE_ANALYSIS').label, 'FUTURE_ANALYSIS')
assert.equal(getAiRecordPresentation(null).label, '其他分析')

assert.deepEqual(getAiCategoryCounts(records), {
  ALL: 4,
  WEEKLY_REPORT: 2,
  RISK_ANALYSIS: 1,
  DOC_CHECK: 0,
  SUMMARY_REPORT: 0,
  SECTION_GENERATE: 0,
  OTHER: 1
})
assert.deepEqual(filterAiRecords(records, 'WEEKLY_REPORT').map((record) => record.id), [1, 3])
assert.deepEqual(filterAiRecords(records, 'OTHER').map((record) => record.id), [4])
assert.deepEqual(filterAiRecords(records, 'ALL').map((record) => record.id), [1, 2, 3, 4])

assert.deepEqual(paginateAiRecords(records, 2, 2), {
  items: records.slice(2, 4),
  total: 4,
  page: 2,
  pageCount: 2
})
assert.equal(paginateAiRecords(records, 99, 3).page, 2)
assert.equal(paginateAiRecords([], 3, 10).page, 1)

assert.deepEqual(getAiStatusPresentation('SUCCESS'), { label: '成功', type: 'success' })
assert.deepEqual(getAiStatusPresentation('GENERATED'), { label: '已生成', type: 'success' })
assert.deepEqual(getAiStatusPresentation('FAILED'), { label: '失败', type: 'danger' })
assert.deepEqual(getAiStatusPresentation('WAITING'), { label: 'WAITING', type: 'warning' })

console.log('AI analysis list checks passed')
```

- [ ] **Step 2: 运行测试并确认因模块缺失而失败**

Run: `cd teamflow-ai-frontend && node scripts/check-ai-analysis-list.mjs`

Expected: FAIL，包含 `ERR_MODULE_NOT_FOUND` 和 `aiRecordPresentation.js`。

- [ ] **Step 3: 实现最小展示配置、分类与分页函数**

创建 `teamflow-ai-frontend/src/utils/aiRecordPresentation.js`：

```js
const FALLBACK_META = {
  category: 'OTHER',
  label: '其他分析',
  purpose: '承接未识别的 AI 分析记录，便于后续核对。'
}

const RECORD_META = {
  WEEKLY_REPORT: {
    category: 'WEEKLY_REPORT',
    label: '项目周报',
    purpose: '汇总当前任务、进度和近期成果，用于阶段汇报。'
  },
  RISK_ANALYSIS: {
    category: 'RISK_ANALYSIS',
    label: '风险分析',
    purpose: '识别延期、阻塞和资源风险，用于项目预警。'
  },
  DOC_CHECK: {
    category: 'DOC_CHECK',
    label: '文档检查',
    purpose: '检查项目资料缺失与完整性，用于交付前补漏。'
  },
  SUMMARY_REPORT: {
    category: 'SUMMARY_REPORT',
    label: '总结报告',
    purpose: '归纳项目成果和整体情况，用于结项总结。'
  },
  SECTION_GENERATE: {
    category: 'SECTION_GENERATE',
    label: '智能体记录',
    purpose: '保存章节 AI 生成过程，用于追踪辅助创作记录。'
  }
}

export const AI_RECORD_CATEGORIES = [
  { key: 'ALL', label: '全部', purpose: '查看项目内的全部 AI 分析记录。' },
  ...Object.values(RECORD_META).map(({ category, label, purpose }) => ({ key: category, label, purpose })),
  { key: 'OTHER', label: '其他分析', purpose: FALLBACK_META.purpose }
]

export const getAiRecordPresentation = (type) => {
  const meta = RECORD_META[type]
  if (meta) return meta
  return {
    ...FALLBACK_META,
    label: type || FALLBACK_META.label
  }
}

export const getAiCategoryCounts = (records = []) => {
  const counts = Object.fromEntries(AI_RECORD_CATEGORIES.map(({ key }) => [key, 0]))
  counts.ALL = records.length
  records.forEach((record) => {
    counts[getAiRecordPresentation(record?.type).category] += 1
  })
  return counts
}

export const filterAiRecords = (records = [], category = 'ALL') => {
  if (category === 'ALL') return records
  return records.filter((record) => getAiRecordPresentation(record?.type).category === category)
}

export const paginateAiRecords = (records = [], requestedPage = 1, pageSize = 10) => {
  const total = records.length
  const pageCount = Math.max(1, Math.ceil(total / pageSize))
  const page = Math.min(Math.max(1, requestedPage), pageCount)
  const start = (page - 1) * pageSize
  return { items: records.slice(start, start + pageSize), total, page, pageCount }
}

export const getAiStatusPresentation = (status) => {
  if (status === 'SUCCESS') return { label: '成功', type: 'success' }
  if (status === 'GENERATED') return { label: '已生成', type: 'success' }
  if (status === 'FAILED') return { label: '失败', type: 'danger' }
  return { label: status || '未知', type: 'warning' }
}
```

- [ ] **Step 4: 运行测试并确认通过**

Run: `cd teamflow-ai-frontend && node scripts/check-ai-analysis-list.mjs`

Expected: PASS，输出 `AI analysis list checks passed`。

- [ ] **Step 5: 提交纯函数与测试**

```bash
git add teamflow-ai-frontend/src/utils/aiRecordPresentation.js teamflow-ai-frontend/scripts/check-ai-analysis-list.mjs
git commit -m "test: 定义 AI 分析分类与分页规则"
```

### Task 2: 分类分页列表组件

**Files:**
- Create: `teamflow-ai-frontend/src/components/ai/AiAnalysisList.vue`
- Modify: `teamflow-ai-frontend/scripts/check-ai-analysis-list.mjs`

- [ ] **Step 1: 为列表结构追加失败检查**

在 `console.log` 前追加：

```js
assert.ok(exists('src/components/ai/AiAnalysisList.vue'), 'AI analysis list component should exist')
const listSource = read('src/components/ai/AiAnalysisList.vue')
assert.match(listSource, /AI_RECORD_CATEGORIES/, 'list should render the shared categories')
assert.match(listSource, /getAiCategoryCounts/, 'list should show category counts')
assert.match(listSource, /filterAiRecords/, 'list should filter records by category')
assert.match(listSource, /paginateAiRecords/, 'list should paginate filtered records')
assert.match(listSource, /pageResetKey/, 'list should reset page after a newly generated record')
assert.match(listSource, /每页 10 条/, 'list should explain its fixed page size')
assert.match(listSource, /使用用途/, 'list should expose each analysis purpose')
assert.match(listSource, /生成者/, 'list should expose the generating user')
assert.match(listSource, /查看详情/, 'list should open details instead of expanding AI content')
assert.match(listSource, /emit\('view', row\)/, 'list should emit the selected record')
assert.doesNotMatch(listSource, /row\.result/, 'list should not render full AI results')
```

- [ ] **Step 2: 运行测试并确认因组件缺失而失败**

Run: `cd teamflow-ai-frontend && node scripts/check-ai-analysis-list.mjs`

Expected: FAIL，消息为 `AI analysis list component should exist`。

- [ ] **Step 3: 实现分类标签、摘要表格和分页**

创建 `teamflow-ai-frontend/src/components/ai/AiAnalysisList.vue`：

```vue
<template>
  <section class="ai-analysis-list">
    <div v-if="error" class="load-alert">
      <el-alert
        title="AI 分析记录加载失败"
        :description="error"
        type="error"
        show-icon
        :closable="false"
      />
      <el-button size="small" @click="emit('retry')">重新加载</el-button>
    </div>

    <el-tabs v-model="selectedCategory" class="category-tabs">
      <el-tab-pane v-for="item in AI_RECORD_CATEGORIES" :key="item.key" :name="item.key">
        <template #label>
          <span class="category-label">
            {{ item.label }}
            <span class="category-count">{{ categoryCounts[item.key] }}</span>
          </span>
        </template>
      </el-tab-pane>
    </el-tabs>

    <div class="category-purpose">
      <span>{{ currentCategory.label }}</span>
      {{ currentCategory.purpose }}
    </div>

    <el-table
      v-loading="loading"
      :data="pageResult.items"
      row-key="id"
      stripe
      class="records-table"
      empty-text="当前分类暂无分析记录"
    >
      <el-table-column label="分析名称" min-width="130">
        <template #default="{ row }">
          <el-tag effect="plain">{{ getAiRecordPresentation(row.type).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="使用用途" min-width="300">
        <template #default="{ row }">
          <span class="purpose-text">{{ getAiRecordPresentation(row.type).purpose }}</span>
        </template>
      </el-table-column>
      <el-table-column label="生成者" min-width="110">
        <template #default="{ row }">{{ row.username || '系统' }}</template>
      </el-table-column>
      <el-table-column label="生成时间" min-width="170">
        <template #default="{ row }">{{ row.createTime || '刚刚生成' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="getAiStatusPresentation(row.status).type">
            {{ getAiStatusPresentation(row.status).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="emit('view', row)">查看详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="pageResult.total" class="pagination-row">
      <span>共 {{ pageResult.total }} 条，每页 10 条</span>
      <el-pagination
        v-model:current-page="currentPage"
        background
        layout="prev, pager, next"
        :page-size="PAGE_SIZE"
        :total="pageResult.total"
      />
    </div>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import {
  AI_RECORD_CATEGORIES,
  filterAiRecords,
  getAiCategoryCounts,
  getAiRecordPresentation,
  getAiStatusPresentation,
  paginateAiRecords
} from '../../utils/aiRecordPresentation'

const PAGE_SIZE = 10
const props = defineProps({
  records: { type: Array, default: () => [] },
  category: { type: String, default: 'ALL' },
  pageResetKey: { type: Number, default: 0 },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' }
})
const emit = defineEmits(['update:category', 'view', 'retry'])
const currentPage = ref(1)

const selectedCategory = computed({
  get: () => props.category,
  set: (value) => emit('update:category', value)
})
const categoryCounts = computed(() => getAiCategoryCounts(props.records))
const currentCategory = computed(() => (
  AI_RECORD_CATEGORIES.find((item) => item.key === props.category) || AI_RECORD_CATEGORIES[0]
))
const filteredRecords = computed(() => filterAiRecords(props.records, props.category))
const pageResult = computed(() => paginateAiRecords(filteredRecords.value, currentPage.value, PAGE_SIZE))

watch(() => props.category, () => { currentPage.value = 1 })
watch(() => props.pageResetKey, () => { currentPage.value = 1 })
watch(() => filteredRecords.value.length, () => {
  currentPage.value = pageResult.value.page
})
</script>

<style scoped>
.ai-analysis-list { min-width: 0; }
.load-alert { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.load-alert :deep(.el-alert) { flex: 1; }
.category-tabs { margin-top: -4px; }
.category-label { display: inline-flex; align-items: center; gap: 6px; }
.category-count {
  min-width: 20px;
  padding: 1px 6px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 12px;
  line-height: 18px;
  text-align: center;
}
.category-purpose {
  margin: 2px 0 14px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f0fdfa;
  color: #475569;
  font-size: 13px;
}
.category-purpose span { margin-right: 8px; color: #0f766e; font-weight: 700; }
.records-table { width: 100%; }
.purpose-text { color: #475569; line-height: 1.55; }
.pagination-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 16px;
  color: #64748b;
  font-size: 13px;
}
@media (max-width: 720px) {
  .pagination-row { align-items: flex-start; flex-direction: column; }
}
</style>
```

- [ ] **Step 4: 运行检查并确认通过**

Run: `cd teamflow-ai-frontend && node scripts/check-ai-analysis-list.mjs`

Expected: PASS。

- [ ] **Step 5: 提交列表组件**

```bash
git add teamflow-ai-frontend/src/components/ai/AiAnalysisList.vue teamflow-ai-frontend/scripts/check-ai-analysis-list.mjs
git commit -m "feat: 新增 AI 分析分类分页列表"
```

### Task 3: AI 详情弹窗组件

**Files:**
- Create: `teamflow-ai-frontend/src/components/ai/AiRecordDetailDialog.vue`
- Modify: `teamflow-ai-frontend/scripts/check-ai-analysis-list.mjs`

- [ ] **Step 1: 为弹窗行为追加失败检查**

在 `console.log` 前追加：

```js
assert.ok(exists('src/components/ai/AiRecordDetailDialog.vue'), 'AI record detail dialog should exist')
const detailSource = read('src/components/ai/AiRecordDetailDialog.vue')
assert.match(detailSource, /el-dialog/, 'details should use a dialog')
assert.match(detailSource, /record\.result \|\| '暂无 AI 分析内容'/, 'dialog should show complete results with a fallback')
assert.match(detailSource, /record\.username \|\| '系统'/, 'dialog should identify the generating user')
assert.match(detailSource, /record\.prompt/, 'dialog should support prompt details')
assert.match(detailSource, /el-collapse-item/, 'prompt details should be collapsed by default')
assert.match(detailSource, /max-height: 52vh/, 'long AI results should scroll inside the dialog')
assert.match(detailSource, /emit\('closed'\)/, 'dialog should allow its parent to clear the selected record')
```

- [ ] **Step 2: 运行测试并确认因弹窗缺失而失败**

Run: `cd teamflow-ai-frontend && node scripts/check-ai-analysis-list.mjs`

Expected: FAIL，消息为 `AI record detail dialog should exist`。

- [ ] **Step 3: 实现详情弹窗**

创建 `teamflow-ai-frontend/src/components/ai/AiRecordDetailDialog.vue`：

```vue
<template>
  <el-dialog
    :model-value="modelValue"
    :title="record ? getAiRecordPresentation(record.type).label : 'AI 分析详情'"
    width="min(820px, 92vw)"
    top="7vh"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @closed="emit('closed')"
  >
    <div v-if="record" class="detail-content">
      <div class="metadata-grid">
        <div><span>生成者</span>{{ record.username || '系统' }}</div>
        <div><span>生成时间</span>{{ record.createTime || '刚刚生成' }}</div>
        <div>
          <span>状态</span>
          <el-tag size="small" :type="getAiStatusPresentation(record.status).type">
            {{ getAiStatusPresentation(record.status).label }}
          </el-tag>
        </div>
        <div v-if="record.modelName"><span>模型</span>{{ record.modelName }}</div>
        <div v-if="record.source"><span>来源</span>{{ record.source }}</div>
        <div v-if="record.riskLevel"><span>风险等级</span>{{ record.riskLevel }}</div>
      </div>

      <div class="section-title">AI 分析内容</div>
      <pre class="result-content">{{ record.result || '暂无 AI 分析内容' }}</pre>

      <el-collapse v-if="record.prompt" class="prompt-collapse">
        <el-collapse-item title="查看 AI 提示词与上下文" name="prompt">
          <pre class="prompt-content">{{ record.prompt }}</pre>
        </el-collapse-item>
      </el-collapse>
    </div>
  </el-dialog>
</template>

<script setup>
import { getAiRecordPresentation, getAiStatusPresentation } from '../../utils/aiRecordPresentation'

defineProps({
  modelValue: { type: Boolean, default: false },
  record: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'closed'])
</script>

<style scoped>
.detail-content { color: #334155; }
.metadata-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 20px;
  padding: 14px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
  font-size: 13px;
}
.metadata-grid div { display: flex; align-items: center; gap: 8px; min-width: 0; }
.metadata-grid span:first-child { color: #64748b; }
.section-title { margin: 20px 0 10px; color: #0f172a; font-weight: 700; }
.result-content,
.prompt-content {
  margin: 0;
  padding: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  color: #334155;
  font: inherit;
  line-height: 1.75;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.result-content { max-height: 52vh; overflow-y: auto; }
.prompt-collapse { margin-top: 16px; }
.prompt-content { max-height: 240px; overflow-y: auto; background: #f8fafc; font-size: 12px; }
@media (max-width: 640px) {
  .metadata-grid { grid-template-columns: 1fr; }
}
</style>
```

- [ ] **Step 4: 运行检查并确认通过**

Run: `cd teamflow-ai-frontend && node scripts/check-ai-analysis-list.mjs`

Expected: PASS。

- [ ] **Step 5: 提交详情弹窗**

```bash
git add teamflow-ai-frontend/src/components/ai/AiRecordDetailDialog.vue teamflow-ai-frontend/scripts/check-ai-analysis-list.mjs
git commit -m "feat: 新增 AI 分析详情弹窗"
```

### Task 4: 接入项目交付页面并处理加载、生成与刷新

**Files:**
- Modify: `teamflow-ai-frontend/src/views/ManagementDelivery.vue`
- Modify: `teamflow-ai-frontend/scripts/check-ai-analysis-list.mjs`

- [ ] **Step 1: 为页面接入追加失败检查**

在 `console.log` 前追加：

```js
const managementSource = read('src/views/ManagementDelivery.vue')
assert.match(managementSource, /AiAnalysisList/, 'management page should render the classified list')
assert.match(managementSource, /AiRecordDetailDialog/, 'management page should render the detail dialog')
assert.match(managementSource, /v-model:category="aiCategory"/, 'management page should own the selected category')
assert.match(managementSource, /:page-reset-key="aiListPageResetKey"/, 'generation should be able to reset the current page')
assert.match(managementSource, /@view="openAiRecord"/, 'list rows should open the detail dialog')
assert.match(managementSource, /@retry="loadAiRecords"/, 'load errors should be retryable')
assert.match(managementSource, /aiRecordsError/, 'record loading should expose an error without clearing successful data')
assert.match(managementSource, /generatedRecord\?\.type/, 'generation should prefer the returned record type')
assert.match(managementSource, /generatedCategoryByAction/, 'generation should fall back to its requested type')
assert.doesNotMatch(managementSource, /AiResultPanel/, 'management page should not expand AI records')
```

- [ ] **Step 2: 运行检查并确认旧页面结构导致失败**

Run: `cd teamflow-ai-frontend && node scripts/check-ai-analysis-list.mjs`

Expected: FAIL，最先提示 `management page should render the classified list`。

- [ ] **Step 3: 替换 AI 标签页中的整块结果展示**

在操作区标题和四个按钮下，用以下内容替换原 `aiLoading`、`latestRecord` 和 `olderRecords` 展示块：

```vue
<AiAnalysisList
  v-model:category="aiCategory"
  :records="aiRecordsList"
  :page-reset-key="aiListPageResetKey"
  :loading="aiRecordsLoading"
  :error="aiRecordsError"
  @view="openAiRecord"
  @retry="loadAiRecords"
/>
<AiRecordDetailDialog
  v-model="aiDetailVisible"
  :record="selectedAiRecord"
  @closed="selectedAiRecord = null"
/>
```

删除模板中只服务于整块加载提示的 `Loading` 图标。

- [ ] **Step 4: 更新组件与工具导入**

删除：

```js
import AiResultPanel from '../components/AiResultPanel.vue'
import { Loading } from '@element-plus/icons-vue'
```

新增：

```js
import AiAnalysisList from '../components/ai/AiAnalysisList.vue'
import AiRecordDetailDialog from '../components/ai/AiRecordDetailDialog.vue'
import { getAiRecordPresentation } from '../utils/aiRecordPresentation'
```

- [ ] **Step 5: 用可恢复、可防竞态的数据状态替换旧计算属性**

在请求计数器区域新增：

```js
let aiRecordsRequestId = 0
```

用以下状态替换 `latestRecord` 和 `olderRecords`：

```js
const aiRecordsList = ref([])
const aiRecordsLoading = ref(false)
const aiRecordsError = ref('')
const aiCategory = ref('ALL')
const aiListPageResetKey = ref(0)
const selectedAiRecord = ref(null)
const aiDetailVisible = ref(false)

const generatedCategoryByAction = {
  weekly: 'WEEKLY_REPORT',
  risk: 'RISK_ANALYSIS',
  doc: 'DOC_CHECK',
  summary: 'SUMMARY_REPORT'
}
```

在 `resetProjectState` 中加入，确保切换项目不会显示上个项目的数据：

```js
aiRecordsRequestId += 1
aiRecordsList.value = []
aiRecordsLoading.value = false
aiRecordsError.value = ''
aiCategory.value = 'ALL'
aiListPageResetKey.value += 1
selectedAiRecord.value = null
aiDetailVisible.value = false
```

- [ ] **Step 6: 实现保留已有数据的加载与详情开关**

替换 `loadAiRecords` 并新增详情方法：

```js
const loadAiRecords = async () => {
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  const requestId = ++aiRecordsRequestId
  aiRecordsLoading.value = true
  aiRecordsError.value = ''
  try {
    const list = await aiRecords(currentProjectId)
    if (requestId !== aiRecordsRequestId || projectId.value !== currentProjectId) return
    aiRecordsList.value = list || []
  } catch (err) {
    if (requestId !== aiRecordsRequestId || projectId.value !== currentProjectId) return
    aiRecordsError.value = err.message || '请稍后重试'
    console.error('Failed to load AI records', err)
  } finally {
    if (requestId === aiRecordsRequestId && projectId.value === currentProjectId) {
      aiRecordsLoading.value = false
    }
  }
}

const openAiRecord = (record) => {
  selectedAiRecord.value = record
  aiDetailVisible.value = true
}
```

- [ ] **Step 7: 生成后定位到对应分类第一页**

替换 `generateAiReport`：

```js
const generateAiReport = async (type) => {
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  aiLoading.value = true
  try {
    let generatedRecord = null
    if (type === 'weekly') generatedRecord = await weeklyReport(currentProjectId)
    if (type === 'risk') generatedRecord = await riskAnalysis(currentProjectId)
    if (type === 'doc') generatedRecord = await documentCheck(currentProjectId)
    if (type === 'summary') generatedRecord = await summaryReport(currentProjectId)

    if (projectId.value !== currentProjectId) return
    const generatedType = generatedRecord?.type || generatedCategoryByAction[type]
    aiCategory.value = getAiRecordPresentation(generatedType).category
    aiListPageResetKey.value += 1
    await loadAiRecords()
    ElMessage.success('AI 分析完成')
  } catch (err) {
    console.error('Failed to generate AI report', err)
    ElMessage.error(err.message || '生成失败')
  } finally {
    if (projectId.value === currentProjectId) aiLoading.value = false
  }
}
```

- [ ] **Step 8: 确认旧整块结果组件不再被页面引用并运行检查**

保留已有未提交改动的 `teamflow-ai-frontend/src/components/AiResultPanel.vue`，仅确认 `ManagementDelivery.vue` 不再导入或渲染它。

Run: `cd teamflow-ai-frontend && node scripts/check-ai-analysis-list.mjs`

Expected: PASS。

- [ ] **Step 9: 构建前端并验证模板与导入均可编译**

Run: `cd teamflow-ai-frontend && npm run build`

Expected: PASS，Vite 输出 `built in`，不出现 `AiResultPanel` 或 `Loading` 未定义错误。

- [ ] **Step 10: 提交页面接入**

```bash
git add teamflow-ai-frontend/src/views/ManagementDelivery.vue teamflow-ai-frontend/scripts/check-ai-analysis-list.mjs
git commit -m "feat: 接入 AI 分析分类列表与详情弹窗"
```

### Task 5: 纳入项目检查并完成回归验证

**Files:**
- Modify: `teamflow-ai-frontend/package.json`

- [ ] **Step 1: 在 `package.json` 注册独立检查**

在 `scripts` 中新增：

```json
"check:ai-analysis-list": "node scripts/check-ai-analysis-list.mjs"
```

并将总检查更新为：

```json
"check": "npm run check:join-redirect && npm run check:member-access && npm run check:profile && npm run check:project-workspace && npm run check:route-params && npm run check:ai-analysis-list"
```

- [ ] **Step 2: 运行完整前端检查**

Run: `cd teamflow-ai-frontend && npm run check`

Expected: PASS，最后包含 `AI analysis list checks passed`。

- [ ] **Step 3: 运行完整前端构建**

Run: `cd teamflow-ai-frontend && npm run build`

Expected: PASS，Vite 输出 `built in`。

- [ ] **Step 4: 运行工作区验证脚本**

Run: `bash scripts/verify.sh`

Expected: 前端检查、前端构建、后端架构检查和 Maven 测试全部 PASS；若本机没有 Maven，仅允许脚本明确输出既有的 Maven 跳过提示。

- [ ] **Step 5: 在浏览器中验证关键交互**

启动前端并进入项目交付管理的“AI 智能分析”标签，验证：

1. 页面未直接显示任何 AI 完整原文。
2. 分类标签数量正确，未知类型进入“其他分析”。
3. 每页最多 10 条，切换分类回到第一页。
4. 列表显示用途、生成者、生成时间和状态。
5. 点击“查看详情”弹出完整内容，提示词默认折叠，长内容在弹窗内滚动。
6. 生成任一报告后自动进入对应分类，并在第一页看到新记录。
7. 缩窄视口后弹窗元信息改为单列，分页区域不溢出。

- [ ] **Step 6: 提交检查入口**

```bash
git add teamflow-ai-frontend/package.json
git commit -m "test: 纳入 AI 分析列表回归检查"
```

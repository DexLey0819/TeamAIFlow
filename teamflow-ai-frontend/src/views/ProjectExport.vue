<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">成果导出</h1>
        <p class="page-subtitle">按章节、任务、进度、AI 与统计数据生成项目成果包</p>
      </div>
    </div>

    <div class="grid grid-2">
      <div class="panel">
        <h3>导出配置</h3>
        <el-form :model="form" label-position="top">
          <el-form-item label="导出范围">
            <el-select v-model="form.exportScope" style="width:100%">
              <el-option label="完整项目报告" value="FULL_REPORT" />
              <el-option label="课程答辩材料" value="DEFENSE_PACKAGE" />
              <el-option label="阶段检查材料" value="CHECKPOINT" />
            </el-select>
          </el-form-item>
          <el-form-item label="包含章节">
            <el-select v-model="form.includeSections" multiple collapse-tags style="width:100%">
              <el-option v-for="section in sections" :key="section.id" :label="section.sectionName" :value="section.id" />
            </el-select>
          </el-form-item>
          <el-checkbox v-model="form.includeTasks">包含任务记录</el-checkbox>
          <el-checkbox v-model="form.includeProgress">包含进度报告</el-checkbox>
          <el-checkbox v-model="form.includeAi">包含 AI 分析</el-checkbox>
          <el-checkbox v-model="form.includeStatistics">包含统计评分</el-checkbox>
        </el-form>
        <el-button type="primary" :loading="creating" class="export-button" @click="create">生成导出文件</el-button>
      </div>

      <div class="panel">
        <h3>导出说明</h3>
        <p class="muted">系统会检查项目完整度，并生成可下载的 HTML 成果文件。生成失败时可在历史记录查看失败原因。</p>
      </div>
    </div>

    <div class="panel" style="margin-top:16px">
      <h3>导出历史</h3>
      <el-table :data="history" border>
        <el-table-column prop="fileName" label="文件名" min-width="220" />
        <el-table-column prop="exportScope" label="范围" width="140" />
        <el-table-column prop="creatorName" label="创建人" width="120" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><el-tag :type="row.status === 'GENERATED' ? 'success' : 'danger'">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="failureReason" label="失败原因" min-width="180" />
        <el-table-column prop="createTime" label="时间" width="180" />
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="row.status !== 'GENERATED'" @click="download(row)">下载</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!history.length" description="暂无导出记录" />
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createExport, downloadExport, exportHistory } from '../api/export'
import { projectSections } from '../api/section'
import { usePositiveProjectId } from '../utils/routeParams'

const route = useRoute()
const projectId = usePositiveProjectId(route)
const sections = ref([])
const history = ref([])
const creating = ref(false)
let loadRequestId = 0
let createRequestId = 0
const defaultForm = () => ({
  exportScope: 'FULL_REPORT',
  includeSections: [],
  includeTasks: true,
  includeProgress: true,
  includeAi: true,
  includeStatistics: true
})
const form = reactive(defaultForm())

const load = async () => {
  const requestId = ++loadRequestId
  createRequestId += 1
  creating.value = false
  Object.assign(form, defaultForm())
  sections.value = []
  history.value = []
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  const [sectionRes, historyRes] = await Promise.all([projectSections(currentProjectId), exportHistory(currentProjectId)])
  if (requestId !== loadRequestId || projectId.value !== currentProjectId) return
  sections.value = sectionRes || []
  history.value = historyRes || []
  form.includeSections = sections.value.map(item => item.id)
}
const create = async () => {
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  const requestId = ++createRequestId
  creating.value = true
  try {
    await createExport(currentProjectId, {
      ...form,
      includeSections: [...form.includeSections]
    })
    if (requestId !== createRequestId || projectId.value !== currentProjectId) return
    ElMessage.success('导出任务已生成')
    load()
  } finally {
    if (requestId === createRequestId && projectId.value === currentProjectId) {
      creating.value = false
    }
  }
}
const download = async (row) => {
  const blob = await downloadExport(row.id)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = row.fileName || `teamflow-export-${row.id}.html`
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

watch(projectId, load, { immediate: true })
</script>

<style scoped>
h3 {
  margin-top: 0;
}
.export-button {
  margin-top: 14px;
}
</style>

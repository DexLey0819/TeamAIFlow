<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">{{ project.projectName || '项目概览' }}</h1>
        <p class="page-subtitle">{{ project.projectCode }}</p>
      </div>
      <div class="toolbar">
        <el-tag>{{ statusText(project.status) }}</el-tag>
        <el-button @click="$router.push(`/projects/${projectId}/export`)">导出成果</el-button>
      </div>
    </div>

    <div class="grid grid-4">
      <StatCard label="完整度" :value="`${project.completenessScore || 0}%`" />
      <StatCard label="成员数" :value="project.memberCount || 0" />
      <StatCard label="任务完成" :value="`${project.doneTaskCount || 0}/${project.taskCount || 0}`" />
      <StatCard label="章节通过" :value="`${project.approvedSectionCount || 0}/${project.sectionCount || 0}`" />
    </div>

    <div class="grid grid-2" style="margin-top:16px">
      <div class="panel">
        <h3>项目说明</h3>
        <p class="content">{{ project.description || '暂无项目说明' }}</p>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="加入链接">{{ project.joinLink || '-' }}</el-descriptions-item>
          <el-descriptions-item label="项目周期">{{ project.startDate || '-' }} 至 {{ project.endDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ project.creatorName || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <div class="panel">
        <h3>完整度评分</h3>
        <el-progress type="dashboard" :percentage="Number(completeness.score || project.completenessScore || 0)" />
        <el-descriptions :column="1" border>
          <el-descriptions-item label="必填章节">{{ completeness.requiredSectionScore ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="评审通过">{{ completeness.reviewPassScore ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="任务完成">{{ completeness.taskCompletionScore ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="缺陷闭环">{{ completeness.defectClosureScore ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="导出就绪">{{ completeness.exportReadinessScore ?? '-' }}</el-descriptions-item>
        </el-descriptions>
        <ul v-if="completeness.deductionReasons?.length" class="reasons">
          <li v-for="reason in completeness.deductionReasons" :key="reason">{{ reason }}</li>
        </ul>
      </div>
    </div>

    <div class="panel" style="margin-top:16px">
      <h3>风险概览</h3>
      <el-table :data="riskTrend.snapshots || []" border>
        <el-table-column prop="snapshotTime" label="时间" width="170" />
        <el-table-column prop="riskLevel" label="风险等级" width="120" />
        <el-table-column prop="riskScore" label="风险分" width="100" />
        <el-table-column prop="taskDelayCount" label="延期任务" width="100" />
        <el-table-column prop="blockedTaskCount" label="阻塞任务" width="100" />
        <el-table-column prop="missingReportCount" label="缺失周报" width="100" />
        <el-table-column prop="trend" label="趋势" />
      </el-table>
      <el-empty v-if="!riskTrend.snapshots?.length" description="暂无风险快照，可在 AI 页面生成风险分析" />
    </div>
  </div>
</template>

<script setup>
import { reactive, watch } from 'vue'
import { useRoute } from 'vue-router'
import StatCard from '../components/StatCard.vue'
import { projectDetail } from '../api/project'
import { completeness as loadCompleteness, riskTrend as loadRiskTrend } from '../api/statistics'
import { usePositiveProjectId } from '../utils/routeParams'

const route = useRoute()
const projectId = usePositiveProjectId(route)
const project = reactive({})
const completeness = reactive({})
const riskTrend = reactive({ snapshots: [] })
let loadRequestId = 0

const statusText = (status) => ({
  PLANNING: '规划中',
  DEVELOPING: '开发中',
  TESTING: '测试中',
  FINISHED: '已完成',
  ARCHIVED: '已归档'
}[status] || status || '未设置')

const resetState = () => {
  Object.keys(project).forEach(key => delete project[key])
  Object.keys(completeness).forEach(key => delete completeness[key])
  Object.keys(riskTrend).forEach(key => delete riskTrend[key])
  Object.assign(riskTrend, { snapshots: [] })
}

const load = async () => {
  const requestId = ++loadRequestId
  resetState()
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  const [detail, score, trend] = await Promise.all([
    projectDetail(currentProjectId),
    loadCompleteness(currentProjectId).catch(() => ({})),
    loadRiskTrend(currentProjectId).catch(() => ({ snapshots: [] }))
  ])
  if (requestId !== loadRequestId || projectId.value !== currentProjectId) return
  resetState()
  Object.assign(project, detail || {})
  Object.assign(completeness, score || {})
  Object.assign(riskTrend, { snapshots: [] }, trend || {})

  // Override completeness score if a manual/AI progress is saved in LocalStorage
  const manualProgress = localStorage.getItem(`teamflow_project_progress_${currentProjectId}`)
  if (manualProgress !== null) {
    const val = Number(manualProgress)
    project.completenessScore = val
    completeness.score = val
  }
}

watch(projectId, load, { immediate: true })
</script>

<style scoped>
h3 {
  margin-top: 0;
}
.content {
  color: #475467;
  line-height: 1.7;
}
.reasons {
  margin: 12px 0 0;
  padding-left: 18px;
  color: #b42318;
}
</style>

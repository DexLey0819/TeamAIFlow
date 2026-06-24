<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">项目统计</h1>
        <p class="page-subtitle">查看完整度评分、成员贡献和风险趋势</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>

    <div class="grid grid-4">
      <StatCard label="完整度" :value="`${score.score || 0}%`" />
      <StatCard label="必填章节" :value="score.requiredSectionScore || 0" />
      <StatCard label="任务完成" :value="score.taskCompletionScore || 0" />
      <StatCard label="导出就绪" :value="score.exportReadinessScore || 0" />
    </div>

    <div class="grid grid-2" style="margin-top:16px">
      <div class="panel">
        <h3>成员贡献</h3>
        <div ref="contributionRef" class="chart"></div>
      </div>
      <div class="panel">
        <h3>风险趋势</h3>
        <div ref="riskRef" class="chart"></div>
      </div>
    </div>

    <div class="panel" style="margin-top:16px">
      <h3>扣分原因</h3>
      <el-alert
        v-for="reason in score.deductionReasons || []"
        :key="reason"
        :title="reason"
        type="warning"
        show-icon
        :closable="false"
        class="reason"
      />
      <el-empty v-if="!score.deductionReasons?.length" description="暂无扣分项" />
    </div>
  </div>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts'
import StatCard from '../components/StatCard.vue'
import { completeness, contribution, riskTrend } from '../api/statistics'
import { usePositiveProjectId } from '../utils/routeParams'

const route = useRoute()
const projectId = usePositiveProjectId(route)
const score = reactive({})
const contributionData = reactive({ members: [] })
const riskData = reactive({ snapshots: [] })
const contributionRef = ref(null)
const riskRef = ref(null)
let contributionChart = null
let riskChart = null
let loadRequestId = 0

const disposeCharts = () => {
  contributionChart?.dispose()
  contributionChart = null
  riskChart?.dispose()
  riskChart = null
}

const resetState = () => {
  Object.keys(score).forEach(key => delete score[key])
  Object.keys(contributionData).forEach(key => delete contributionData[key])
  Object.keys(riskData).forEach(key => delete riskData[key])
  Object.assign(contributionData, { members: [] })
  Object.assign(riskData, { snapshots: [] })
  disposeCharts()
}

const renderCharts = async () => {
  await nextTick()
  if (contributionRef.value) {
    contributionChart?.dispose()
    contributionChart = echarts.init(contributionRef.value)
    const members = contributionData.members || []
    contributionChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 36, right: 12, top: 24, bottom: 64 },
      xAxis: { type: 'category', data: members.map(item => item.realName), axisLabel: { rotate: 30 } },
      yAxis: { type: 'value', min: 0 },
      series: [{ name: '贡献分', type: 'bar', data: members.map(item => Number(item.totalScore || 0)), itemStyle: { color: '#0f766e' } }]
    })
  }
  if (riskRef.value) {
    riskChart?.dispose()
    riskChart = echarts.init(riskRef.value)
    const snapshots = riskData.snapshots || []
    riskChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 36, right: 20, top: 24, bottom: 54 },
      xAxis: { type: 'category', data: snapshots.map(item => String(item.snapshotTime || '').slice(5, 16)) },
      yAxis: { type: 'value', min: 0 },
      series: [{ name: '风险分', type: 'line', smooth: true, data: snapshots.map(item => Number(item.riskScore || 0)), itemStyle: { color: '#dc2626' } }]
    })
  }
}

const load = async () => {
  const requestId = ++loadRequestId
  resetState()
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  const [scoreRes, contributionRes, riskRes] = await Promise.all([
    completeness(currentProjectId),
    contribution(currentProjectId),
    riskTrend(currentProjectId)
  ])
  if (requestId !== loadRequestId || projectId.value !== currentProjectId) return
  resetState()
  Object.assign(score, scoreRes || {})
  Object.assign(contributionData, { members: [] }, contributionRes || {})
  Object.assign(riskData, { snapshots: [] }, riskRes || {})
  renderCharts()
}

const resize = () => {
  contributionChart?.resize()
  riskChart?.resize()
}

onMounted(() => {
  window.addEventListener('resize', resize)
})
watch(projectId, load, { immediate: true })
onUnmounted(() => {
  disposeCharts()
  window.removeEventListener('resize', resize)
})
</script>

<style scoped>
h3 {
  margin-top: 0;
}
.chart {
  height: 320px;
}
.reason {
  margin-bottom: 8px;
}
</style>

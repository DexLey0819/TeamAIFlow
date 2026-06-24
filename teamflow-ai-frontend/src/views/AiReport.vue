<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">AI 分析</h1>
        <p class="page-subtitle">使用智谱 GLM 基于项目任务、进度和文档生成周报、风险、缺失检查与总结</p>
      </div>
      <div class="toolbar">
        <el-button type="primary" :loading="loading" @click="generate('weekly')">生成周报</el-button>
        <el-button type="warning" :loading="loading" @click="generate('risk')">识别风险</el-button>
        <el-button :loading="loading" @click="generate('doc')">文档检查</el-button>
        <el-button :loading="loading" @click="generate('summary')">生成总结</el-button>
      </div>
    </div>
    <AiResultPanel v-if="latest" :record="latest" />
    <div style="height:16px" />
    <div class="grid">
      <AiResultPanel v-for="record in records" :key="record.id" :record="record" />
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import AiResultPanel from '../components/AiResultPanel.vue'
import { aiRecords, documentCheck, riskAnalysis, summaryReport, weeklyReport } from '../api/ai'
import { usePositiveProjectId } from '../utils/routeParams'

const route = useRoute()
const projectId = usePositiveProjectId(route)
const records = ref([])
const loading = ref(false)
let loadRequestId = 0
let generateRequestId = 0
const latest = computed(() => records.value[0])
const load = async () => {
  const requestId = ++loadRequestId
  generateRequestId += 1
  loading.value = false
  records.value = []
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  const result = await aiRecords(currentProjectId)
  if (requestId !== loadRequestId || projectId.value !== currentProjectId) return
  records.value = result || []
}
const generate = async (type) => {
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  const requestId = ++generateRequestId
  loading.value = true
  try {
    if (type === 'weekly') await weeklyReport(currentProjectId)
    if (type === 'risk') await riskAnalysis(currentProjectId)
    if (type === 'doc') await documentCheck(currentProjectId)
    if (type === 'summary') await summaryReport(currentProjectId)
    if (requestId !== generateRequestId || projectId.value !== currentProjectId) return
    ElMessage.success('AI 内容已生成')
    load()
  } finally {
    if (requestId === generateRequestId && projectId.value === currentProjectId) {
      loading.value = false
    }
  }
}
watch(projectId, load, { immediate: true })
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">审核中心</h1>
        <p class="page-subtitle">处理和查看当前项目各章节的审核提交与历史</p>
      </div>
    </div>

    <div class="panel">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="待我审核" name="pending">
          <el-table :data="pendingReviews" border style="width: 100%">
            <el-table-column prop="sectionName" label="章节名称" min-width="150" />
            <el-table-column label="提交版本" width="100">
              <template #default="{ row }">
                <el-tag size="small">v{{ row.latestContent?.versionNo || 1 }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="latestContent.editorName" label="提交人" width="150" />
            <el-table-column label="提交时间" width="180">
              <template #default="{ row }">
                {{ formatTime(row.latestContent?.submitTime) }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag type="warning">待审核</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" size="small" @click="goReview(row.id)">去审核</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="已审核" name="resolved">
          <el-table :data="resolvedReviews" border style="width: 100%">
            <el-table-column prop="sectionName" label="章节名称" min-width="150" />
            <el-table-column label="审核版本" width="100">
              <template #default="{ row }">
                <el-tag size="small" type="info">v{{ row.latestContent?.versionNo || 1 }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="latestContent.editorName" label="提交人" width="150" />
            <el-table-column label="当前状态" width="120">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="更新时间" width="180">
              <template #default="{ row }">
                {{ formatTime(row.updateTime) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="goReview(row.id)">查看详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { projectReviews } from '../api/section'
import { usePositiveProjectId } from '../utils/routeParams'

const route = useRoute()
const router = useRouter()
const projectId = usePositiveProjectId(route)
const reviews = ref([])
const activeTab = ref('pending')
let loadRequestId = 0

const pendingReviews = computed(() => reviews.value.filter(r => r.status === 'REVIEWING'))
const resolvedReviews = computed(() => reviews.value.filter(r => r.status === 'APPROVED' || r.status === 'REJECTED'))

const formatTime = (timeStr) => {
  if (!timeStr) return '-'
  return timeStr.replace('T', ' ').substring(0, 19)
}

const statusText = (status) => ({
  APPROVED: '已通过',
  REJECTED: '已退回',
  REVIEWING: '待审核'
}[status] || status)

const statusType = (status) => ({
  APPROVED: 'success',
  REJECTED: 'danger',
  REVIEWING: 'warning'
}[status] || 'info')

const goReview = (sectionId) => {
  if (!projectId.value) return
  router.push(`/projects/${projectId.value}/reviews/${sectionId}`)
}

const load = async () => {
  const requestId = ++loadRequestId
  reviews.value = []
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  const result = await projectReviews(currentProjectId)
  if (requestId !== loadRequestId || projectId.value !== currentProjectId) return
  reviews.value = result || []
}

watch(projectId, load, { immediate: true })
</script>

<style scoped>
.panel {
  margin-top: 16px;
}
</style>

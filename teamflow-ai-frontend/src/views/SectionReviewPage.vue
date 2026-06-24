<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">审核详情 - {{ section.sectionName || '' }}</h1>
        <p class="page-subtitle">处理和反馈当前章节的审核意见</p>
      </div>
      <div class="toolbar">
        <el-button @click="$router.push(`/projects/${projectId}/reviews`)">返回审核中心</el-button>
      </div>
    </div>

    <div class="split-layout">
      <!-- Left side: Content Preview -->
      <div class="panel preview-pane">
        <div class="pane-title">提交内容预览</div>
        <div class="document-wrap" v-if="section.latestContent">
          <h2 class="document-title">{{ section.latestContent.title }}</h2>
          <div class="document-meta">
            <span>版本: v{{ section.latestContent.versionNo }}</span>
            <el-divider direction="vertical" />
            <span>提交人: {{ section.latestContent.editorName }}</span>
            <el-divider direction="vertical" />
            <span>提交时间: {{ formatTime(section.latestContent.submitTime) }}</span>
          </div>
          <el-divider />
          <div class="document-body">
            <SectionContentRenderer :body="section.latestContent.body" />
          </div>
        </div>
        <div class="empty-wrap" v-else>
          <el-empty description="暂无提交内容" />
        </div>
      </div>

      <!-- Right side: Review Form & Actions -->
      <div class="panel form-pane">
        <div class="pane-title">审核处理</div>
        
        <div class="status-card" v-if="section.status">
          <el-alert
            :title="`当前章节状态: ${statusText(section.status)}`"
            :type="statusType(section.status)"
            :closable="false"
            show-icon
          />
        </div>

        <template v-if="section.status === 'REVIEWING'">
          <div class="review-form">
            <el-form label-position="top">
              <el-form-item label="审核评语">
                <el-input
                  v-model="reviewComment"
                  type="textarea"
                  :rows="6"
                  placeholder="请输入对本章节内容的评价、修改建议或意见..."
                />
              </el-form-item>
            </el-form>
            <div class="form-actions">
              <el-button type="success" size="large" @click="handleReview('APPROVED')">审核通过</el-button>
              <el-button type="danger" size="large" @click="handleReview('REJECTED')">打回重做</el-button>
            </div>
          </div>
        </template>
        <template v-else>
          <div class="resolved-info">
            <el-alert
              title="审核已处理完成，不可重复审核"
              type="info"
              :closable="false"
              style="margin-bottom: 16px"
            />
            <div class="review-feedback" v-if="section.latestContent">
              <div class="feedback-item">
                <span class="label">最后处理人:</span>
                <span class="value">{{ section.latestContent.editorName || '系统' }}</span>
              </div>
              <div class="feedback-item">
                <span class="label">最后处理状态:</span>
                <el-tag :type="statusType(section.status)">{{ statusText(section.status) }}</el-tag>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { sectionDetail, reviewSection } from '../api/section'
import SectionContentRenderer from '../components/section/SectionContentRenderer.vue'
import { usePositiveProjectId, usePositiveSectionId } from '../utils/routeParams'

const route = useRoute()
const router = useRouter()
const projectId = usePositiveProjectId(route)
const sectionId = usePositiveSectionId(route)

const section = reactive({})
const reviewComment = ref('')
let loadRequestId = 0

const formatTime = (timeStr) => {
  if (!timeStr) return '-'
  return timeStr.replace('T', ' ').substring(0, 19)
}

const statusText = (status) => ({
  EMPTY: '未开始',
  DRAFT: '草稿',
  REVIEWING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已退回'
}[status] || status)

const statusType = (status) => ({
  APPROVED: 'success',
  REVIEWING: 'warning',
  REJECTED: 'danger',
  DRAFT: 'info',
  EMPTY: 'info'
}[status] || 'info')

const resetSection = () => {
  Object.keys(section).forEach(key => delete section[key])
  reviewComment.value = ''
}

const load = async () => {
  const requestId = ++loadRequestId
  resetSection()
  const currentProjectId = projectId.value
  const currentSectionId = sectionId.value
  if (!currentProjectId || !currentSectionId) return
  const detail = await sectionDetail(currentSectionId)
  if (
    requestId !== loadRequestId ||
    projectId.value !== currentProjectId ||
    sectionId.value !== currentSectionId
  ) return
  if (detail?.projectId != null && Number(detail.projectId) !== currentProjectId) return
  resetSection()
  Object.assign(section, detail || {})
}

const handleReview = async (reviewResult) => {
  const currentProjectId = projectId.value
  const currentSectionId = sectionId.value
  if (!currentProjectId || !currentSectionId) return
  if (section.projectId != null && Number(section.projectId) !== currentProjectId) {
    ElMessage.warning('章节不属于当前项目')
    return
  }
  try {
    await reviewSection(currentSectionId, {
      reviewResult,
      reviewComment: reviewComment.value
    })
    if (projectId.value !== currentProjectId || sectionId.value !== currentSectionId) return
    ElMessage.success(reviewResult === 'APPROVED' ? '章节审核已通过' : '章节已退回修改')
    router.push(`/projects/${currentProjectId}/reviews`)
  } catch (error) {
    console.error(error)
  }
}

watch([projectId, sectionId], load, { immediate: true })
</script>

<style scoped>
.split-layout {
  display: flex;
  gap: 24px;
  margin-top: 16px;
}
.preview-pane {
  flex: 3;
  min-width: 0;
  max-height: 800px;
  overflow-y: auto;
}
.form-pane {
  flex: 2;
  min-width: 0;
  height: fit-content;
}
.pane-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f2f6fc;
}
.document-title {
  margin-top: 0;
  font-size: 20px;
  font-weight: 700;
}
.document-meta {
  font-size: 13px;
  color: #909399;
  margin-top: 8px;
}
.status-card {
  margin-bottom: 20px;
}
.review-form {
  margin-top: 16px;
}
.form-actions {
  display: flex;
  gap: 16px;
  justify-content: flex-end;
  margin-top: 24px;
}
.resolved-info {
  padding: 8px 0;
}
.review-feedback {
  background: #f8fafc;
  padding: 16px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}
.feedback-item {
  display: flex;
  margin-bottom: 10px;
  align-items: center;
}
.feedback-item:last-child {
  margin-bottom: 0;
}
.feedback-item .label {
  font-weight: 600;
  color: #475569;
  width: 100px;
}
.feedback-item .value {
  color: #1e293b;
}
</style>

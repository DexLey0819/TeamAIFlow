<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">项目汇报</h1>
        <p class="page-subtitle">
          {{ isProjectManager ? '接收团队成员的汇报内容，并使用 AI 智能提取汇报进度更新计划' : '提交当前周期汇报内容、问题、求助事项和下一步计划' }}
        </p>
      </div>
    </div>

    <!-- 1. PROJECT MANAGER VIEW -->
    <div v-if="isProjectManager">
      <div class="grid grid-2" style="margin-bottom: 20px;">
        <!-- Left: Received Reports List -->
        <div class="panel" style="flex: 2;">
          <h3 style="margin-bottom: 16px;">收到的团队汇报</h3>
          <div v-if="memberReports.length" style="display: flex; flex-direction: column; gap: 16px;">
            <el-card v-for="report in memberReports" :key="report.id" shadow="hover" style="border-radius: 8px;">
              <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px;">
                <div>
                  <span style="font-weight: 700; font-size: 16px; color: #1e293b;">{{ report.realName }}</span>
                  <span style="margin-left: 8px; font-size: 13px; color: #64748b;">({{ report.roleName }})</span>
                </div>
                <div style="display: flex; gap: 8px; align-items: center;">
                  <el-tag size="small" type="info">{{ report.reportPeriod }}</el-tag>
                  <el-tag size="small" :type="report.submitStatus === 'NORMAL' || report.submitStatus === 'LATE' ? 'success' : 'warning'">
                    {{ submitText(report.submitStatus) }}
                  </el-tag>
                </div>
              </div>
              
              <div style="font-size: 14px; color: #334155; display: flex; flex-direction: column; gap: 8px; margin-bottom: 16px; line-height: 1.6;">
                <div v-if="report.relatedTaskTitle" style="background: #f1f5f9; padding: 6px 12px; border-radius: 4px; font-size: 13px; font-weight: 600; color: #0f172a;">
                  关联任务：{{ report.relatedTaskTitle }}
                </div>
                <div><strong>已完成工作：</strong>{{ report.completedWork || '无' }}</div>
                <div v-if="report.problems"><strong>遇到问题：</strong>{{ report.problems }}</div>
                <div v-if="report.helpNeeded"><strong>需要帮助：</strong>{{ report.helpNeeded }}</div>
                <div><strong>下一步计划：</strong>{{ report.nextPlan || '无' }}</div>
              </div>

              <div style="display: flex; justify-content: flex-end; border-top: 1px solid #f1f5f9; padding-top: 12px;">
                <el-button 
                  type="primary" 
                  size="small" 
                  :loading="aiExtractingIds.includes(report.id)"
                  @click="handleAiExtract(report)"
                >
                  AI 提取进度并更新
                </el-button>
              </div>
            </el-card>
          </div>
          <el-empty v-else description="本周期暂无收到团队成员的汇报" />
        </div>

        <!-- Right: Submission status of the team -->
        <div class="panel">
          <h3 style="margin-bottom: 16px;">全员提交进度</h3>
          <el-descriptions v-if="status.reportPeriod" :column="1" border style="margin-bottom: 12px;">
            <el-descriptions-item label="汇报周期">{{ status.reportPeriod }}</el-descriptions-item>
            <el-descriptions-item label="汇报规则">{{ status.reportRule?.frequency || '-' }} · {{ status.reportRule?.reportDay || '-' }} {{ status.reportRule?.reportTime || '' }}</el-descriptions-item>
          </el-descriptions>
          <el-table :data="status.memberStatuses || []" border size="small">
            <el-table-column prop="realName" label="成员" />
            <el-table-column prop="roleName" label="角色" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="submitType(row.submitStatus)">{{ submitText(row.submitStatus) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </div>

    <!-- 2. REGULAR MEMBER VIEW -->
    <div v-else>
      <div class="grid grid-2">
        <div class="panel">
          <h3>填写汇报</h3>
          <el-alert v-if="need" :type="need.overdue ? 'warning' : 'info'" show-icon :closable="false" class="need-alert">
            <template #title>
              {{ need.projectName }} · {{ need.reportPeriod }} · {{ need.overdue ? '已逾期' : '待提交' }}
            </template>
          </el-alert>
          <el-form :model="form" label-position="top">
            <el-form-item label="关联任务">
              <el-select v-model="form.relatedTaskId" clearable style="width:100%">
                <el-option v-for="task in tasks" :key="task.id" :label="task.title" :value="task.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="关联章节">
              <el-select v-model="form.relatedSectionId" clearable style="width:100%">
                <el-option v-for="section in sections" :key="section.id" :label="section.sectionName" :value="section.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="已完成工作"><el-input v-model="form.completedWork" type="textarea" :rows="4" /></el-form-item>
            <el-form-item label="遇到问题"><el-input v-model="form.problems" type="textarea" :rows="3" /></el-form-item>
            <el-form-item label="需要帮助"><el-input v-model="form.helpNeeded" type="textarea" :rows="2" /></el-form-item>
            <el-form-item label="下一步计划"><el-input v-model="form.nextPlan" type="textarea" :rows="3" /></el-form-item>
            <el-button type="primary" @click="submit">提交汇报</el-button>
          </el-form>
        </div>
        <div class="panel">
          <h3>本周期提交状态</h3>
          <el-descriptions v-if="status.reportPeriod" :column="1" border>
            <el-descriptions-item label="周期">{{ status.reportPeriod }}</el-descriptions-item>
            <el-descriptions-item label="规则">{{ status.reportRule?.frequency || '-' }} · {{ status.reportRule?.reportDay || '-' }} {{ status.reportRule?.reportTime || '' }}</el-descriptions-item>
          </el-descriptions>
          <el-table :data="status.memberStatuses || []" border style="margin-top:12px">
            <el-table-column prop="realName" label="成员" />
            <el-table-column prop="roleName" label="角色" />
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="submitType(row.submitStatus)">{{ submitText(row.submitStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="submitTime" label="提交时间" width="180" />
          </el-table>
          <el-empty v-if="!status.memberStatuses?.length" description="暂无提交要求" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listMembers, saveProjectWbs } from '../api/project'
import { projectSections } from '../api/section'
import { projectTasks } from '../api/task'
import { needSubmit, progressStatus, submitReport, listReports } from '../api/progress'
import { extractProgress } from '../api/ai'
import { useUserStore } from '../stores/user'
import { usePositiveProjectId } from '../utils/routeParams'

const route = useRoute()
const projectId = usePositiveProjectId(route)
const router = useRouter()
const userStore = useUserStore()
let loadRequestId = 0

const tasks = ref([])
const sections = ref([])
const need = ref(null)
const status = reactive({ memberStatuses: [] })
const memberReports = ref([])
const aiExtractingIds = ref([])

const form = reactive({
  projectId: projectId.value,
  relatedTaskId: null,
  relatedSectionId: null,
  completedWork: '',
  problems: '',
  helpNeeded: '',
  nextPlan: '',
  reportPeriod: ''
})

const isProjectManager = ref(false)

const hasReportFrequency = computed(() => {
  return !!status.reportRule && !!status.reportRule.frequency
})

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

const submitText = (value) => ({
  NORMAL: '已提交',
  LATE: '逾期提交',
  SUPPLEMENTED: '补交',
  NOT_SUBMITTED: '未提交'
}[value] || value)
const submitType = (value) => ({
  NORMAL: 'success',
  LATE: 'warning',
  SUPPLEMENTED: 'info',
  NOT_SUBMITTED: 'danger'
}[value] || 'info')

const resetStatus = () => {
  Object.keys(status).forEach(key => {
    delete status[key]
  })
  status.memberStatuses = []
}

const resetVisibleState = () => {
  tasks.value = []
  sections.value = []
  need.value = null
  resetStatus()
  memberReports.value = []
  aiExtractingIds.value = []
  isProjectManager.value = false
  Object.assign(form, blankReport())
}

const canViewStatus = async (currentProjectId) => {
  if (['ADMIN'].includes(userStore.user?.role)) return true
  const members = await listMembers(currentProjectId)
  return members.some(member => member.userId === userStore.user?.id && member.memberRole === 'PROJECT_MANAGER')
}

const applyProgressUpdates = async (updates, currentProjectId) => {
  const wbsKey = `teamflow_wbs_${currentProjectId}`
  const wbsDataStr = localStorage.getItem(wbsKey)
  if (!wbsDataStr) {
    ElMessage.warning('本地 WBS 数据不存在，请先在“项目管理”页面初始化计划。')
    return
  }
  try {
    const wbsData = JSON.parse(wbsDataStr)
    const tasksData = wbsData.wbs || []
    if (!tasksData.length) {
      ElMessage.warning('项目计划中暂无任务')
      return
    }

    let updatedCount = 0
    let messageDetails = []
    
    updates.forEach(up => {
      const task = tasksData.find(t => String(t.id) === String(up.taskId))
      if (task) {
        task.progress = up.progress
        updatedCount++
        messageDetails.push(`“${task.title}” 进度更新至 ${up.progress}%`)
      }
    })

    if (updatedCount === 0) {
      ElMessage.warning('AI 提取了进度，但未能与 WBS 任务表中的任务项匹配。')
      return
    }

    // Recalculate summary task progress
    const recalc = (taskList) => {
      let changed = true
      let iterations = 0
      while (changed && iterations < 5) {
        changed = false
        iterations++
        for (let i = taskList.length - 1; i >= 0; i--) {
          const task = taskList[i]
          if (task.isParent) {
            const getCode = (t) => t.outlineCode || t.wbsCode
            const children = taskList.filter(c => getCode(c).startsWith(getCode(task) + '.') && c.level === task.level + 1)
            if (children.length) {
              let totalDuration = 0
              let weightedProgress = 0
              children.forEach(c => {
                const dur = Number(c.duration) || 0
                const prog = Number(c.progress) || 0
                totalDuration += dur
                weightedProgress += prog * dur
              })
              const newProgress = totalDuration > 0 ? Math.round(weightedProgress / totalDuration) : 0
              if (task.progress !== newProgress) {
                task.progress = newProgress
                changed = true
              }
            }
          }
        }
      }
    }
    recalc(tasksData)

    // Save back to LocalStorage and sync to database
    wbsData.wbs = tasksData
    const payloadStr = JSON.stringify(wbsData)
    localStorage.setItem(wbsKey, payloadStr)
    try {
      await saveProjectWbs(currentProjectId, payloadStr)
    } catch (err) {
      console.error('Failed to sync WBS updates from AI progress extraction to backend', err)
    }
    if (projectId.value !== currentProjectId) return

    ElMessageBox.confirm(
      `AI 成功提取并更新了以下任务进度：\n\n${messageDetails.join('\n')}\n\n是否立即前往“项目管理”页面查看最新的甘特图？`,
      'WBS 进度更新成功',
      {
        confirmButtonText: '前往项目管理',
        cancelButtonText: '留在本页',
        type: 'success'
      }
    ).then(() => {
      if (projectId.value !== currentProjectId) return
      router.push(`/projects/${currentProjectId}/sections/management-delivery`)
    }).catch(() => {})

  } catch (err) {
    console.error(err)
    ElMessage.error('更新本地 WBS 数据失败')
  }
}

const handleAiExtract = async (report) => {
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  const wbsKey = `teamflow_wbs_${currentProjectId}`
  const wbsDataStr = localStorage.getItem(wbsKey)
  if (!wbsDataStr) {
    ElMessage.warning('本地 WBS 数据不存在，请先在“项目管理”页面初始化计划。')
    return
  }
  
  let wbsTasks = []
  try {
    const wbsData = JSON.parse(wbsDataStr)
    wbsTasks = wbsData.wbs || []
  } catch (e) {
    ElMessage.error('解析本地 WBS 数据失败')
    return
  }

  aiExtractingIds.value.push(report.id)
  try {
    const res = await extractProgress(currentProjectId, {
      reportText: `已完成工作: ${report.completedWork || ''}\n下一步计划: ${report.nextPlan || ''}`,
      tasks: wbsTasks.map(t => ({ id: t.id, title: t.title }))
    })
    if (projectId.value !== currentProjectId) return
    
    const updates = res.updates || []
    if (!updates.length) {
      ElMessage.warning('AI 未能在汇报中识别出明确的任务进度更新')
    } else {
      await applyProgressUpdates(updates, currentProjectId)
    }
  } catch (err) {
    console.error(err)
    if (projectId.value === currentProjectId) {
      ElMessage.error('AI 提取进度失败')
    }
  } finally {
    if (projectId.value === currentProjectId) {
      aiExtractingIds.value = aiExtractingIds.value.filter(id => id !== report.id)
    }
  }
}

const load = async () => {
  const currentProjectId = projectId.value
  const requestId = ++loadRequestId
  resetVisibleState()
  if (!currentProjectId) return

  const [taskRes, sectionRes, needRes] = await Promise.all([
    projectTasks(currentProjectId),
    projectSections(currentProjectId),
    needSubmit()
  ])
  if (requestId !== loadRequestId || projectId.value !== currentProjectId) return
  tasks.value = taskRes || []
  sections.value = sectionRes || []
  need.value = (needRes || []).find(item => item.projectId === currentProjectId) || null
  form.reportPeriod = need.value?.reportPeriod || ''
  
  const members = await listMembers(currentProjectId)
  if (requestId !== loadRequestId || projectId.value !== currentProjectId) return
  isProjectManager.value = members.some(member => member.userId === userStore.user?.id && member.memberRole === 'PROJECT_MANAGER')

  const viewStatus = await canViewStatus(currentProjectId)
  if (requestId !== loadRequestId || projectId.value !== currentProjectId) return
  if (viewStatus) {
    const statusRes = await progressStatus(currentProjectId)
    if (requestId !== loadRequestId || projectId.value !== currentProjectId) return
    resetStatus()
    Object.assign(status, statusRes)
    try {
      const reports = await listReports(currentProjectId)
      if (requestId !== loadRequestId || projectId.value !== currentProjectId) return
      memberReports.value = reports || []
    } catch (e) {
      console.error(e)
    }
  }
}

const submit = async () => {
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  form.projectId = currentProjectId
  await submitReport({ ...form, projectId: currentProjectId })
  if (projectId.value !== currentProjectId) return
  ElMessage.success('汇报已提交')
  Object.assign(form, blankReport())
  await load()
}
watch(projectId, load, { immediate: true })
</script>

<style scoped>
h3 {
  margin-top: 0;
}
.need-alert {
  margin-bottom: 12px;
}
</style>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">任务看板</h1>
        <p class="page-subtitle">按 TODO、进行中、检查、完成和阻塞状态流转课程项目任务</p>
      </div>
      <el-button type="primary" @click="dialog = true">新建任务</el-button>
    </div>
    <div class="kanban">
      <div v-for="column in columns" :key="column.value" class="kanban-column">
        <div class="kanban-title">
          <span>{{ column.label }}</span>
          <el-tag size="small">{{ grouped[column.value]?.length || 0 }}</el-tag>
        </div>
        <TaskCard v-for="task in grouped[column.value]" :key="task.id" :task="task" @status="changeStatus" />
      </div>
    </div>
    <el-dialog v-model="dialog" title="新建任务" width="560px">
      <el-form :model="form" label-position="top">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="关联章节">
          <el-select v-model="form.sectionId" clearable style="width:100%">
            <el-option v-for="section in sections" :key="section.id" :label="section.sectionName" :value="section.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="form.assigneeId" clearable style="width:100%">
            <el-option v-for="member in members" :key="member.userId" :label="member.realName || member.username" :value="member.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="form.priority" style="width:100%">
            <el-option label="紧急" value="URGENT" />
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width:100%">
            <el-option v-for="column in columns" :key="column.value" :label="column.label" :value="column.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.status === 'BLOCKED'" label="阻塞原因"><el-input v-model="form.blockReason" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="截止日期"><el-date-picker v-model="form.dueDate" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import TaskCard from '../components/TaskCard.vue'
import { listMembers } from '../api/project'
import { projectSections } from '../api/section'
import { createTask, projectTasks, updateTaskStatus } from '../api/task'
import { usePositiveProjectId } from '../utils/routeParams'

const route = useRoute()
const projectId = usePositiveProjectId(route)
const tasks = ref([])
const members = ref([])
const sections = ref([])
const dialog = ref(false)
let loadRequestId = 0
const columns = [
  { label: '待开始', value: 'TODO' },
  { label: '进行中', value: 'IN_PROGRESS' },
  { label: '待检查', value: 'REVIEW' },
  { label: '已完成', value: 'DONE' },
  { label: '阻塞', value: 'BLOCKED' }
]
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
const form = reactive(blankForm())
const grouped = computed(() => Object.fromEntries(columns.map(col => [col.value, tasks.value.filter(task => task.status === col.value)])))
const load = async () => {
  const requestId = ++loadRequestId
  tasks.value = []
  members.value = []
  sections.value = []
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  const [taskRes, memberRes, sectionRes] = await Promise.all([
    projectTasks(currentProjectId),
    listMembers(currentProjectId),
    projectSections(currentProjectId)
  ])
  if (requestId !== loadRequestId || projectId.value !== currentProjectId) return
  tasks.value = taskRes || []
  members.value = memberRes || []
  sections.value = sectionRes || []
}
const submit = async () => {
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  form.projectId = currentProjectId
  await createTask(form)
  if (projectId.value !== currentProjectId) return
  ElMessage.success('任务已创建')
  dialog.value = false
  Object.assign(form, blankForm())
  load()
}
const changeStatus = async (task, status) => {
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  const payload = { status, content: `任务状态调整为 ${status}` }
  if (status === 'BLOCKED') {
    const { value } = await ElMessageBox.prompt('请输入阻塞原因', '任务阻塞', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      inputValue: task.blockReason || ''
    })
    payload.blockReason = value
  }
  if (projectId.value !== currentProjectId) return
  await updateTaskStatus(task.id, payload)
  if (projectId.value !== currentProjectId) return
  ElMessage.success('状态已更新')
  load()
}
watch(projectId, () => {
  Object.assign(form, blankForm())
  load()
}, { immediate: true })
</script>

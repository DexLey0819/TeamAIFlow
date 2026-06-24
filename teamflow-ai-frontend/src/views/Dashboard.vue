<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">我的工作台</h1>
        <p class="page-subtitle">汇总我参与的项目、待提交周报和最近通知</p>
      </div>
    </div>
    <div class="grid grid-4">
      <StatCard label="参与项目" :value="projects.length" />
      <StatCard label="平均完整度" :value="`${averageScore}%`" />
      <StatCard label="待提交周报" :value="pendingReports.length" />
      <StatCard label="未读通知" :value="unread" />
    </div>
    <div class="grid grid-2" style="margin-top:16px">
      <div class="panel">
        <div class="panel-head">
          <h3>待处理周报</h3>
          <el-button text type="primary" @click="$router.push('/projects')">进入项目</el-button>
        </div>
        <el-table :data="pendingReports" border>
          <el-table-column prop="projectName" label="项目" />
          <el-table-column prop="reportPeriod" label="周期" width="150" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row.overdue ? 'danger' : 'warning'">{{ row.overdue ? '已逾期' : '待提交' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button link type="primary" @click="$router.push(`/projects/${row.projectId}/progress`)">填写</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!pendingReports.length" description="当前没有待提交周报" />
      </div>
      <div class="panel">
        <div class="panel-head">
          <h3>最近通知</h3>
          <el-button text type="primary" @click="$router.push('/notifications')">查看全部</el-button>
        </div>
        <el-timeline>
          <el-timeline-item v-for="item in recentNotifications" :key="item.id" :timestamp="formatTime(item.createTime)">
            <strong>{{ item.title }}</strong>
            <p class="muted">{{ item.content }}</p>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-if="!recentNotifications.length" description="暂无通知" />
      </div>
    </div>
    <div class="page-header project-section-head">
      <div>
        <h2 class="sub-title">我的项目</h2>
        <p class="page-subtitle">点击项目卡片进入概览、成员、章节、任务和 AI 分析</p>
      </div>
      <el-button type="primary" @click="$router.push('/projects/new')">初始化项目</el-button>
    </div>
    <div class="grid grid-2">
      <ProjectCard v-for="project in projects" :key="project.id" :project="project" />
    </div>
    <el-empty v-if="!projects.length" description="暂无项目，先创建一个项目吧" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import ProjectCard from '../components/ProjectCard.vue'
import StatCard from '../components/StatCard.vue'
import { myProjects } from '../api/project'
import { needSubmit } from '../api/progress'
import { notifications, unreadCount } from '../api/notification'

const projects = ref([])
const reports = ref([])
const notices = ref([])
const unread = ref(0)

const pendingReports = computed(() => reports.value.filter(item => item.needSubmit))
const recentNotifications = computed(() => notices.value.slice(0, 5))
const averageScore = computed(() => {
  if (!projects.value.length) return 0
  const total = projects.value.reduce((sum, project) => sum + Number(project.completenessScore || 0), 0)
  return Math.round(total / projects.value.length)
})

const formatTime = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : ''

onMounted(async () => {
  const [projectRes, reportRes, noticeRes, unreadRes] = await Promise.all([
    myProjects(),
    needSubmit(),
    notifications(),
    unreadCount()
  ])
  projects.value = projectRes || []
  reports.value = reportRes || []
  notices.value = noticeRes || []
  unread.value = Number(unreadRes || 0)
})
</script>

<style scoped>
.panel-head,
.project-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.panel-head h3,
.sub-title {
  margin: 0;
}
.project-section-head {
  margin-top: 20px;
}
</style>

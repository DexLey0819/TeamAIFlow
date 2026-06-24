<template>
  <div class="project-sidebar">
    <div class="brand">
      <span class="logo-dot"></span>
      <span v-show="!isCollapsed" class="brand-text">TeamFlowAI</span>
    </div>
    <el-menu
      router
      unique-opened
      class="project-menu"
      :default-active="$route.path"
      :default-openeds="defaultOpeneds"
      :collapse="isCollapsed"
      :collapse-transition="false"
    >
      <el-menu-item index="/dashboard">
        <el-icon><House /></el-icon>
        <template #title>首页</template>
      </el-menu-item>

      <!-- Category 1: 项目详情 (Collapsible Submenu) -->
      <el-sub-menu index="project-detail">
        <template #title>
          <el-icon><Briefcase /></el-icon>
          <span>项目详情</span>
        </template>
        <el-menu-item :index="`${base}/overview`">
          <el-icon><Grid /></el-icon>
          <template #title>概述</template>
        </el-menu-item>
        <el-menu-item :index="`${base}/members`">
          <el-icon><User /></el-icon>
          <template #title>成员与申请</template>
        </el-menu-item>
        <el-menu-item :index="`${base}/tasks`">
          <el-icon><Calendar /></el-icon>
          <template #title>任务看板</template>
        </el-menu-item>
        <el-menu-item :index="`${base}/files`">
          <el-icon><Paperclip /></el-icon>
          <template #title>附件</template>
        </el-menu-item>
        <el-menu-item :index="`${base}/progress`">
          <el-icon><ChatLineRound /></el-icon>
          <template #title>汇报</template>
        </el-menu-item>
        <el-menu-item :index="`${base}/reviews`">
          <el-icon><Checked /></el-icon>
          <template #title>审核中心</template>
        </el-menu-item>
        <el-menu-item :index="`${base}/export`">
          <el-icon><Download /></el-icon>
          <template #title>成果导出</template>
        </el-menu-item>
      </el-sub-menu>

      <!-- Category 2: 具体章节 (Flat List) -->
      <div v-show="!isCollapsed" class="menu-category-title">具体章节</div>
      
      <el-menu-item :index="`${base}/sections/management-delivery`">
        <el-icon><Document /></el-icon>
        <template #title>
          <span class="section-name">项目管理</span>
          <el-tag v-if="managementSection" class="status-tag" size="small" :type="statusType(managementSection.status)">
            {{ statusText(managementSection.status) }}
          </el-tag>
          <el-tag v-else class="status-tag" size="small" type="warning">管理</el-tag>
        </template>
      </el-menu-item>
      <el-menu-item v-for="section in filteredSections" :key="section.id" :index="`${base}/sections/${section.id}`">
        <el-icon><Document /></el-icon>
        <template #title>
          <span class="section-name">{{ section.sectionName }}</span>
          <el-tag class="status-tag" size="small" :type="statusType(section.status)">
            {{ statusText(section.status) }}
          </el-tag>
        </template>
      </el-menu-item>
    </el-menu>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch, inject } from 'vue'
import { useRoute } from 'vue-router'
import { 
  House, Grid, User, Calendar, Paperclip, 
  ChatLineRound, Checked, Download, Briefcase, Document 
} from '@element-plus/icons-vue'
import { projectSections } from '../api/section'

const props = defineProps({
  projectId: { type: [String, Number], required: true }
})

const route = useRoute()
const sections = ref([])
const filteredSections = computed(() => {
  return sections.value.filter(s => s.sectionCode !== 'MANAGEMENT_DELIVERY')
})
const managementSection = computed(() => {
  return sections.value.find(s => s.sectionCode === 'MANAGEMENT_DELIVERY')
})
const loading = ref(false)
const base = computed(() => `/projects/${props.projectId}`)
const defaultOpeneds = computed(() => {
  const detailPages = ['/members', '/files', '/progress', '/reviews', '/tasks', '/export']
  const isDetailPage = detailPages.some(page => route.path.endsWith(page))
  return isDetailPage ? ['project-detail'] : []
})
let loadRequestId = 0

// Inject collapsed state from Layout.vue parent
const isCollapsed = inject('isSidebarCollapsed', ref(false))

const statusText = (status) => ({
  EMPTY: '未开始',
  DRAFT: '草稿',
  REVIEWING: '审核中',
  APPROVED: '已通过',
  REJECTED: '已退回'
}[status] || status || '未设置')

const statusType = (status) => ({
  APPROVED: 'success',
  REVIEWING: 'warning',
  REJECTED: 'danger',
  DRAFT: 'info',
  EMPTY: 'info'
}[status] || 'info')

const normalizeProjectId = (value) => {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : null
}

const isCurrentLoad = (requestId, projectId) => (
  requestId === loadRequestId && normalizeProjectId(projectId) === normalizeProjectId(props.projectId)
)

const load = async ({ clearBeforeFetch = true } = {}) => {
  const requestId = ++loadRequestId

  if (clearBeforeFetch) {
    sections.value = []
  }

  if (!props.projectId) {
    sections.value = []
    loading.value = false
    return
  }

  const currentProjectId = props.projectId
  loading.value = true
  try {
    const result = await projectSections(currentProjectId)
    if (isCurrentLoad(requestId, currentProjectId)) {
      sections.value = result || []
    }
  } catch (error) {
    if (isCurrentLoad(requestId, currentProjectId)) {
      sections.value = []
    }
  } finally {
    if (isCurrentLoad(requestId, currentProjectId)) {
      loading.value = false
    }
  }
}

const handleProjectSectionsUpdated = (event) => {
  if (normalizeProjectId(event.detail?.projectId) === normalizeProjectId(props.projectId)) {
    load({ clearBeforeFetch: false })
  }
}

onMounted(() => {
  window.addEventListener('teamflow:project-sections-updated', handleProjectSectionsUpdated)
})

onBeforeUnmount(() => {
  window.removeEventListener('teamflow:project-sections-updated', handleProjectSectionsUpdated)
})

watch(
  () => props.projectId,
  () => {
    load({ clearBeforeFetch: true })
  },
  { immediate: true }
)
</script>

<style scoped>
.project-sidebar {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.brand {
  height: 64px;
  display: flex;
  align-items: center;
  padding: 0 24px;
  border-bottom: 1px solid var(--el-border-color);
  overflow: hidden;
  gap: 12px;
}
.logo-dot {
  width: 10px;
  height: 10px;
  background-color: #09090b;
  border-radius: 50%;
  flex-shrink: 0;
}
.brand-text {
  font-size: 16px;
  font-weight: 800;
  letter-spacing: -0.04em;
  color: #09090b;
  white-space: nowrap;
}
.project-menu {
  border-right: 0;
  flex: 1;
  overflow-y: auto;
}
.section-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.status-tag {
  margin-left: auto;
  flex-shrink: 0;
}
.menu-category-title {
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  color: #a1a1aa;
  padding: 16px 12px 6px 12px;
  letter-spacing: 0.05em;
  user-select: none;
  white-space: nowrap;
}
:deep(.el-menu-item) {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>

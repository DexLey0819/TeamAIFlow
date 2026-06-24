<template>
  <el-container class="layout">
    <el-aside :width="asideWidth" class="aside">
      <ProjectSidebar v-if="isProjectRoute" :project-id="projectId" />
      <div v-else class="sidebar-container">
        <div class="brand">
          <span class="logo-dot"></span>
          <span v-show="!isCollapsed" class="brand-text">TeamFlowAI</span>
        </div>
        <el-menu
          router
          :collapse="isCollapsed"
          :collapse-transition="false"
          :default-active="$route.path"
          class="sidebar-menu"
        >
          <el-menu-item index="/dashboard">
            <el-icon><House /></el-icon>
            <template #title>我的工作台</template>
          </el-menu-item>
          <el-menu-item index="/profile">
            <el-icon><User /></el-icon>
            <template #title>个人信息</template>
          </el-menu-item>
          <el-menu-item index="/projects">
            <el-icon><Folder /></el-icon>
            <template #title>项目管理</template>
          </el-menu-item>
          <el-menu-item index="/notifications">
            <el-icon><Bell /></el-icon>
            <template #title>通知中心</template>
          </el-menu-item>
          <el-menu-item v-if="isAdmin" index="/admin/templates">
            <el-icon><Setting /></el-icon>
            <template #title>模板管理</template>
          </el-menu-item>
        </el-menu>
      </div>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-button
            class="toggle-sidebar-btn"
            :icon="isCollapsed ? Expand : Fold"
            circle
            @click="toggleCollapse"
          />
          <div class="header-info">
            <strong>{{ userStore.user?.realName || userStore.user?.username || 'TeamFlowAI 用户' }}</strong>
            <p>项目协作、文档交付与 GLM 智能分析</p>
          </div>
        </div>
        <div class="toolbar">
          <el-tag size="small">{{ roleText(userStore.user?.role) }}</el-tag>
          <el-button class="logout-btn" @click="logout">退出登录</el-button>
        </div>
      </el-header>
      <el-main>
        <RouterView :key="$route.fullPath" />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, ref, provide } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { House, User, Folder, Bell, Setting, Fold, Expand } from '@element-plus/icons-vue'
import ProjectSidebar from '../components/ProjectSidebar.vue'
import { useUserStore } from '../stores/user'
import { usePositiveProjectId } from '../utils/routeParams'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
userStore.loadMe()

const projectId = usePositiveProjectId(route)
const isProjectRoute = computed(() => route.path.startsWith('/projects/') && projectId.value !== null)
const isAdmin = computed(() => userStore.user?.role === 'ADMIN')
const roleText = (role) => ({ USER: '用户', ADMIN: '管理员' }[role] || role || '未登录')

const logout = () => {
  userStore.logout()
  router.push('/login')
}

// Collapsible sidebar state management via Provide/Inject
const isCollapsed = ref(localStorage.getItem('teamflow_sidebar_collapsed') === 'true')
const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
  localStorage.setItem('teamflow_sidebar_collapsed', String(isCollapsed.value))
}
const asideWidth = computed(() => isCollapsed.value ? '64px' : '236px')

provide('isSidebarCollapsed', isCollapsed)
provide('toggleSidebarCollapse', toggleCollapse)
</script>

<style scoped>
.layout {
  min-height: 100vh;
}
.aside {
  transition: width 0.25s cubic-bezier(0.25, 1, 0.5, 1);
  overflow: hidden;
}
.sidebar-container {
  display: flex;
  flex-direction: column;
  height: 100%;
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
.sidebar-menu {
  flex: 1;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid var(--el-border-color);
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.toggle-sidebar-btn {
  border: none !important;
  font-size: 16px !important;
  color: #71717a !important;
  background: transparent !important;
  cursor: pointer;
  width: 32px;
  height: 32px;
}
.toggle-sidebar-btn:hover {
  color: #09090b !important;
  background: #f4f4f5 !important;
}
.header-info {
  display: flex;
  flex-direction: column;
}
.logout-btn {
  font-size: 12px !important;
  height: 30px !important;
  border: 1px solid #e4e4e7 !important;
  background: #ffffff !important;
  color: #27272a !important;
}
.logout-btn:hover {
  background: #f4f4f5 !important;
  color: #09090b !important;
}
.el-main {
  padding: 0;
}
</style>

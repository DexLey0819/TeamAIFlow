<template>
  <div class="project-nav">
    <el-menu mode="horizontal" router :default-active="$route.path">
      <el-menu-item :index="base + '/overview'">概览</el-menu-item>
      <el-menu-item :index="base + '/members'">成员与申请</el-menu-item>
      <el-menu-item :index="base + '/permissions'">权限矩阵</el-menu-item>
      <el-menu-item :index="base + '/sections'">章节</el-menu-item>
      <el-menu-item :index="base + '/files'">附件</el-menu-item>
      <el-menu-item :index="base + '/tasks'">任务</el-menu-item>
      <el-menu-item :index="base + '/progress'">进度</el-menu-item>
      <el-menu-item :index="base + '/ai'">AI</el-menu-item>
      <el-menu-item :index="base + '/statistics'">统计</el-menu-item>
      <el-menu-item :index="base + '/export'">导出</el-menu-item>
    </el-menu>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ projectId: { type: [String, Number], required: true } })
const base = computed(() => `/projects/${props.projectId}`)
</script>

<style scoped>
.project-nav {
  margin-bottom: 16px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow-x: auto;
}
</style>

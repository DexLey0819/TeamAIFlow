<template>
  <div class="panel project-card" @click="$router.push(`/projects/${project.id}/overview`)">
    <div class="project-top">
      <div>
        <h3>{{ project.projectName }}</h3>
      </div>
      <el-tag>{{ statusText(project.status) }}</el-tag>
    </div>
    <p class="desc">{{ project.description }}</p>
    <div class="meta">
      <span>成员 {{ project.memberCount || 0 }}</span>
      <span>任务 {{ project.taskCount || 0 }}</span>
      <span>完整度 {{ score }}%</span>
    </div>
    <el-progress :percentage="score" />
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ project: { type: Object, required: true } })
const score = computed(() => Number(props.project.completenessScore || 0))
const statusText = (status) => ({
  PLANNING: '规划中',
  DEVELOPING: '开发中',
  TESTING: '测试中',
  FINISHED: '已完成',
  ARCHIVED: '已归档'
}[status] || status || '未设置')
</script>

<style scoped>
.project-card {
  cursor: pointer;
  transition: border-color .2s, transform .2s;
}
.project-card:hover {
  border-color: #0f766e;
  transform: translateY(-2px);
}
.project-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
h3 {
  margin: 0;
  font-size: 17px;
}
p {
  margin: 6px 0 0;
  color: #667085;
}
.desc {
  min-height: 44px;
  line-height: 1.55;
}
.meta {
  display: flex;
  gap: 14px;
  margin: 12px 0;
  color: #475467;
  font-size: 13px;
}
</style>

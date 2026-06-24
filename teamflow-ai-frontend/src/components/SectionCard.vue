<template>
  <div class="panel section-card" @click="$router.push(`/projects/${section.projectId}/sections/${section.id}`)">
    <div class="section-top">
      <div>
        <h3>{{ section.sectionName }}</h3>
        <p>{{ section.description || '暂无说明' }}</p>
      </div>
      <el-tag :type="statusType(section.status)">{{ section.status }}</el-tag>
    </div>
    <div class="meta">
      <span>{{ section.requiredFlag ? '必填章节' : '可选章节' }}</span>
      <span>{{ section.ownerRoleName || '未分配角色' }}</span>
      <span>版本 {{ section.latestContent?.versionNo || 0 }}</span>
    </div>
  </div>
</template>

<script setup>
defineProps({ section: { type: Object, required: true } })
const statusType = (status) => ({
  APPROVED: 'success',
  REVIEWING: 'warning',
  REJECTED: 'danger',
  DRAFT: 'info',
  EMPTY: ''
}[status] || '')
</script>

<style scoped>
.section-card {
  cursor: pointer;
}
.section-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
h3 {
  margin: 0;
}
p {
  margin: 6px 0 0;
  color: #667085;
  line-height: 1.5;
}
.meta {
  display: flex;
  gap: 12px;
  margin-top: 12px;
  color: #667085;
  font-size: 13px;
}
</style>

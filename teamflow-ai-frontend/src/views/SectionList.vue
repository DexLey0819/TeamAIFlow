<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">项目章节</h1>
        <p class="page-subtitle">按模板章节维护文档内容，提交后由具备审核权限的角色确认</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>
    <div class="grid grid-4">
      <StatCard label="章节总数" :value="sections.length" />
      <StatCard label="待审核" :value="countByStatus('REVIEWING')" />
      <StatCard label="已通过" :value="countByStatus('APPROVED')" />
      <StatCard label="缺失必填" :value="sections.filter(item => item.missing).length" />
    </div>
    <div class="grid grid-2" style="margin-top:16px">
      <SectionCard v-for="section in sections" :key="section.id" :section="section" />
    </div>
    <el-empty v-if="!sections.length" description="暂无章节" />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import SectionCard from '../components/SectionCard.vue'
import StatCard from '../components/StatCard.vue'
import { projectSections } from '../api/section'
import { usePositiveProjectId } from '../utils/routeParams'

const route = useRoute()
const projectId = usePositiveProjectId(route)
const sections = ref([])
let loadRequestId = 0
const countByStatus = (status) => computed(() => sections.value.filter(item => item.status === status).length).value
const load = async () => {
  const requestId = ++loadRequestId
  sections.value = []
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  const result = await projectSections(currentProjectId)
  if (requestId !== loadRequestId || projectId.value !== currentProjectId) return
  sections.value = result || []
}
watch(projectId, load, { immediate: true })
</script>

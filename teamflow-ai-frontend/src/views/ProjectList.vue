<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">项目管理</h1>
        <p class="page-subtitle">查看我创建、参与或指导的课程项目</p>
      </div>
      <el-button type="primary" @click="$router.push('/projects/new')">初始化项目</el-button>
    </div>
    <div class="grid grid-2">
      <ProjectCard v-for="project in projects" :key="project.id" :project="project" />
    </div>
    <el-empty v-if="!projects.length" description="暂无项目" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import ProjectCard from '../components/ProjectCard.vue'
import { myProjects } from '../api/project'

const projects = ref([])
const load = async () => { projects.value = await myProjects() }
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">项目附件</h1>
        <p class="page-subtitle">上传、分类、下载课程项目资料，作为 AI 分析和成果导出的补充证据</p>
      </div>
    </div>
    <div class="panel">
      <div class="toolbar" style="margin-bottom:14px">
        <el-select v-model="documentType" style="width:180px">
          <el-option label="需求文档" value="REQUIREMENT" />
          <el-option label="设计文档" value="DESIGN" />
          <el-option label="测试材料" value="TEST" />
          <el-option label="报告材料" value="REPORT" />
          <el-option label="其他" value="OTHER" />
        </el-select>
        <el-upload :show-file-list="false" :http-request="upload">
          <el-button type="primary">上传文档</el-button>
        </el-upload>
      </div>
      <el-table :data="files" border>
        <el-table-column prop="fileName" label="文件名" />
        <el-table-column prop="documentType" label="分类" width="130" />
        <el-table-column prop="uploaderName" label="上传人" width="120" />
        <el-table-column label="大小" width="120">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="上传时间" width="180" />
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button size="small" @click="download(row)">下载</el-button>
            <el-button size="small" type="danger" @click="remove(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { deleteFile, downloadFile, projectFiles, uploadFile } from '../api/file'
import { usePositiveProjectId } from '../utils/routeParams'

const route = useRoute()
const projectId = usePositiveProjectId(route)
const files = ref([])
const documentType = ref('REPORT')
let loadRequestId = 0
const load = async () => {
  const requestId = ++loadRequestId
  files.value = []
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  const result = await projectFiles(currentProjectId)
  if (requestId !== loadRequestId || projectId.value !== currentProjectId) return
  files.value = result || []
}
const formatSize = (size) => {
  if (!size) return '0 B'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}
const upload = async ({ file }) => {
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  const form = new FormData()
  form.append('projectId', currentProjectId)
  form.append('documentType', documentType.value)
  form.append('file', file)
  await uploadFile(form)
  if (projectId.value !== currentProjectId) return
  ElMessage.success('上传成功')
  load()
}
const download = async (row) => {
  const blob = await downloadFile(row.id)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = row.fileName || `teamflow-file-${row.id}`
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
const remove = async (id) => {
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  await deleteFile(id)
  if (projectId.value !== currentProjectId) return
  ElMessage.success('已删除')
  load()
}
watch(projectId, load, { immediate: true })
</script>

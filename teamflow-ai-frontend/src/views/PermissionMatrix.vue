<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">权限矩阵</h1>
        <p class="page-subtitle">按项目角色查看各章节的查看、编辑、评论、审核、导出和 AI 权限</p>
      </div>
    </div>
    <div class="panel matrix-panel">
      <el-table :data="matrix.roleRows || []" border>
        <el-table-column prop="roleName" label="角色" fixed width="160" />
        <el-table-column
          v-for="section in matrix.sectionColumns || []"
          :key="section.sectionId"
          :label="section.sectionName"
          min-width="210"
        >
          <template #default="{ row }">
            <div class="permission-tags">
              <el-tag
                v-for="code in permissionFor(row.roleId, section.sectionId)"
                :key="code"
                size="small"
                :type="permissionType(code)"
              >
                {{ permissionText(code) }}
              </el-tag>
              <span v-if="!permissionFor(row.roleId, section.sectionId).length" class="muted">无权限</span>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { reactive, watch } from 'vue'
import { useRoute } from 'vue-router'
import { projectPermissions } from '../api/permission'
import { usePositiveProjectId } from '../utils/routeParams'

const route = useRoute()
const projectId = usePositiveProjectId(route)
const matrix = reactive({ roleRows: [], sectionColumns: [], permissions: [] })
let loadRequestId = 0

const permissionFor = (roleId, sectionId) => {
  const item = matrix.permissions.find(permission => permission.roleId === roleId && permission.sectionId === sectionId)
  return item?.permissionCodes || []
}

const permissionText = (code) => ({
  VIEW: '查看',
  EDIT: '编辑',
  COMMENT: '评论',
  REVIEW: '审核',
  MANAGE: '管理',
  EXPORT: '导出',
  AI_GENERATE: 'AI'
}[code] || code)

const permissionType = (code) => ({
  EDIT: 'success',
  REVIEW: 'warning',
  MANAGE: 'danger',
  EXPORT: 'info',
  AI_GENERATE: 'primary'
}[code] || 'info')

const resetMatrix = () => {
  Object.keys(matrix).forEach(key => delete matrix[key])
  Object.assign(matrix, { roleRows: [], sectionColumns: [], permissions: [] })
}

const load = async () => {
  const requestId = ++loadRequestId
  resetMatrix()
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  const result = await projectPermissions(currentProjectId)
  if (requestId !== loadRequestId || projectId.value !== currentProjectId) return
  resetMatrix()
  Object.assign(matrix, result || {})
}

watch(projectId, load, { immediate: true })
</script>

<style scoped>
.matrix-panel {
  overflow-x: auto;
}
.permission-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
</style>

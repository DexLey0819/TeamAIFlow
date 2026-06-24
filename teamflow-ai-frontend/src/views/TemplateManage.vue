<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">模板管理</h1>
        <p class="page-subtitle">维护课程项目模板的默认角色、章节、权限和 AI 检查规则</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>
    <div class="panel">
      <el-table :data="templates" border>
        <el-table-column prop="code" label="编码" width="150" />
        <el-table-column prop="name" label="名称" width="180" />
        <el-table-column prop="mode" label="模式" width="120" />
        <el-table-column prop="description" label="说明" min-width="260" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }"><el-button link type="primary" @click="edit(row)">编辑</el-button></template>
        </el-table-column>
      </el-table>
    </div>

    <el-drawer v-model="drawer" title="编辑模板" size="720px">
      <el-form :model="form" label-position="top">
        <el-form-item label="模板名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
        <el-form-item label="角色默认配置 JSON"><el-input v-model="form.roleDefaults" type="textarea" :rows="6" /></el-form-item>
        <el-form-item label="章节默认配置 JSON"><el-input v-model="form.sectionDefaults" type="textarea" :rows="8" /></el-form-item>
        <el-form-item label="权限默认配置 JSON"><el-input v-model="form.permissionDefaults" type="textarea" :rows="6" /></el-form-item>
        <el-form-item label="AI 检查规则 JSON"><el-input v-model="form.aiCheckRules" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawer = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminProjectTemplates, updateProjectTemplate } from '../api/templates'

const templates = ref([])
const drawer = ref(false)
const saving = ref(false)
const currentId = ref(null)
const form = reactive({
  name: '',
  description: '',
  roleDefaults: '',
  sectionDefaults: '',
  permissionDefaults: '',
  aiCheckRules: '',
  enabled: true
})

const pretty = (value) => {
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value || ''
  }
}

const compact = (value, label) => {
  try {
    return JSON.stringify(JSON.parse(value))
  } catch {
    throw new Error(`${label}不是合法 JSON`)
  }
}

const load = async () => {
  templates.value = await adminProjectTemplates()
}

const edit = (row) => {
  currentId.value = row.id
  Object.assign(form, {
    name: row.name,
    description: row.description,
    roleDefaults: pretty(row.roleDefaults),
    sectionDefaults: pretty(row.sectionDefaults),
    permissionDefaults: pretty(row.permissionDefaults),
    aiCheckRules: pretty(row.aiCheckRules),
    enabled: Boolean(row.enabled)
  })
  drawer.value = true
}

const save = async () => {
  saving.value = true
  try {
    await updateProjectTemplate(currentId.value, {
      ...form,
      roleDefaults: compact(form.roleDefaults, '角色默认配置'),
      sectionDefaults: compact(form.sectionDefaults, '章节默认配置'),
      permissionDefaults: compact(form.permissionDefaults, '权限默认配置'),
      aiCheckRules: compact(form.aiCheckRules, 'AI 检查规则')
    })
    ElMessage.success('模板已保存')
    drawer.value = false
    load()
  } catch (error) {
    ElMessage.error(error.message || '模板保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">项目初始化向导</h1>
        <p class="page-subtitle">配置项目基础信息、角色人数和周报规则，生成项目协作空间</p>
      </div>
    </div>

    <el-steps :active="active" finish-status="success" class="steps">
      <el-step title="基础信息" />
      <el-step title="角色人数" />
      <el-step title="周报规则" />
      <el-step title="完成" />
    </el-steps>

    <div v-if="active === 0" class="panel">
      <el-form :model="form" label-position="top">
        <el-form-item label="项目名称"><el-input v-model="form.projectName" /></el-form-item>
        <el-form-item label="项目描述"><el-input v-model="form.description" type="textarea" :rows="4" /></el-form-item>
        <div class="grid grid-2">
          <el-form-item label="开始日期"><el-date-picker v-model="form.startDate" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
          <el-form-item label="结束日期"><el-date-picker v-model="form.endDate" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        </div>
        <el-form-item label="允许成员申请加入">
          <el-switch v-model="form.allowJoinApply" active-text="需要项目经理审核" inactive-text="仅邀请加入" />
        </el-form-item>
      </el-form>
    </div>

    <div v-if="active === 1" class="panel">
      <div class="toolbar role-toolbar">
        <el-button type="primary" plain @click="addRole">新增角色</el-button>
        <span class="muted">项目经理会自动成为当前账号，其他成员可通过加入链接申请。</span>
      </div>
      <el-table :data="roles" border>
        <el-table-column label="角色编码" min-width="160">
          <template #default="{ row }"><el-input v-model="row.roleCode" /></template>
        </el-table-column>
        <el-table-column label="角色名称" min-width="160">
          <template #default="{ row }"><el-input v-model="row.roleName" /></template>
        </el-table-column>
        <el-table-column label="职责" min-width="220">
          <template #default="{ row }"><el-input v-model="row.responsibility" /></template>
        </el-table-column>
        <el-table-column label="人数上限" width="130">
          <template #default="{ row }"><el-input-number v-model="row.maxCount" :min="1" :max="20" /></template>
        </el-table-column>
        <el-table-column label="必需" width="90">
          <template #default="{ row }"><el-switch v-model="row.requiredFlag" /></template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ $index }">
            <el-button link type="danger" :disabled="roles.length <= 1" @click="roles.splice($index, 1)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-if="active === 2" class="panel">
      <el-form :model="reportRule" label-position="top">
        <div class="grid grid-2">
          <el-form-item label="频率">
            <el-select v-model="reportRule.frequency" style="width:100%">
              <el-option label="每周" value="WEEKLY" />
              <el-option label="每日" value="DAILY" />
            </el-select>
          </el-form-item>
          <el-form-item label="提交日">
            <el-select v-model="reportRule.reportDay" style="width:100%">
              <el-option label="周一" value="MONDAY" />
              <el-option label="周三" value="WEDNESDAY" />
              <el-option label="周五" value="FRIDAY" />
              <el-option label="周日" value="SUNDAY" />
            </el-select>
          </el-form-item>
        </div>
        <div class="grid grid-2">
          <el-form-item label="截止时间"><el-time-picker v-model="reportRule.reportTime" value-format="HH:mm:ss" style="width:100%" /></el-form-item>
          <el-form-item label="逾期策略">
            <el-select v-model="reportRule.overduePolicy" style="width:100%">
              <el-option label="提醒" value="REMIND" />
              <el-option label="提醒并计入风险" value="RISK" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="是否必交">
          <el-switch v-model="reportRule.requiredFlag" active-text="必交" inactive-text="选交" />
        </el-form-item>
      </el-form>
    </div>

    <div v-if="active === 3" class="panel result-panel">
      <h2>{{ result.project?.projectName }} 已初始化</h2>
      <p class="muted">项目编码：{{ result.projectCode }}</p>
      <p class="muted">加入链接：{{ result.joinLink }}</p>
      <div class="toolbar">
        <el-button type="primary" @click="$router.push(`/projects/${result.project?.id}/overview`)">进入项目</el-button>
        <el-button @click="$router.push('/projects')">返回列表</el-button>
      </div>
    </div>

    <div v-if="active < 3" class="wizard-actions">
      <el-button :disabled="active === 0" @click="active--">上一步</el-button>
      <el-button v-if="active < 2" type="primary" @click="next">下一步</el-button>
      <el-button v-else type="primary" :loading="submitting" @click="submit">创建项目</el-button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { initProject } from '../api/project'
import { projectTemplateDetail, projectTemplates } from '../api/templates'

const active = ref(0)
const templates = ref([])
const selectedCode = ref('FRONT_BACKEND')
const roles = ref([])
const submitting = ref(false)
const result = reactive({})
const form = reactive({
  projectName: '',
  description: '',
  startDate: new Date().toISOString().slice(0, 10),
  endDate: '',
  allowJoinApply: true
})
const reportRule = reactive({
  frequency: 'WEEKLY',
  reportDay: 'FRIDAY',
  reportTime: '18:00:00',
  requiredFlag: true,
  overduePolicy: 'RISK'
})

const parseJson = (value, fallback) => {
  try {
    return value ? JSON.parse(value) : fallback
  } catch {
    return fallback
  }
}

const normalizeRoles = (items) => items.map((item, index) => ({
  roleCode: item.roleCode || item.code,
  roleName: item.roleName || item.name,
  responsibility: item.responsibility || `${item.name || item.code}负责对应模块交付。`,
  maxCount: item.maxCount || 1,
  requiredFlag: item.requiredFlag ?? true,
  sortOrder: index + 1
}))

const selectTemplate = async (item) => {
  selectedCode.value = item.code
  const detail = await projectTemplateDetail(item.code)
  roles.value = normalizeRoles(parseJson(detail.roleDefaults, []))
  if (!form.projectName) {
    form.projectName = `${detail.name}项目`
  }
}

const addRole = () => {
  roles.value.push({
    roleCode: `ROLE_${roles.value.length + 1}`,
    roleName: '新角色',
    responsibility: '',
    maxCount: 1,
    requiredFlag: true,
    sortOrder: roles.value.length + 1
  })
}

const next = () => {
  if (active.value === 0 && (!form.projectName || !form.startDate || !form.endDate)) {
    ElMessage.warning('请填写项目名称、开始日期和结束日期')
    return
  }
  active.value += 1
}

const submit = async () => {
  submitting.value = true
  try {
    const payload = {
      ...form,
      templateCode: selectedCode.value,
      maxMembers: roles.value.reduce((sum, role) => sum + Number(role.maxCount || 0), 0),
      roles: roles.value,
      reportRule
    }
    const res = await initProject(payload)
    Object.assign(result, res)
    active.value = 3
    ElMessage.success('项目初始化完成')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  templates.value = await projectTemplates()
  const frontBackend = templates.value.find(t => t.code === 'FRONT_BACKEND') || templates.value[0]
  if (frontBackend) {
    await selectTemplate(frontBackend)
  }
})
</script>

<style scoped>
.steps {
  margin-bottom: 18px;
}
.template-card {
  cursor: pointer;
  transition: border-color .2s, transform .2s;
}
.template-card.active {
  border-color: #0f766e;
  box-shadow: 0 10px 26px rgba(15, 118, 110, .12);
}
.template-card:hover {
  transform: translateY(-2px);
}
.card-head,
.role-toolbar,
.wizard-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.card-head h3 {
  margin: 0;
}
.template-card p {
  margin: 10px 0 0;
  color: #667085;
  line-height: 1.6;
}
.wizard-actions {
  margin-top: 16px;
}
.result-panel h2 {
  margin-top: 0;
}
</style>

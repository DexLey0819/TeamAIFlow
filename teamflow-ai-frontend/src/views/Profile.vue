<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">个人信息</h1>
        <p class="page-subtitle">维护当前账号的邮箱和联系电话</p>
      </div>
    </div>

    <div class="grid grid-2 profile-grid">
      <div class="panel">
        <h3>账号信息</h3>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="用户名">{{ user.username || '-' }}</el-descriptions-item>
          <el-descriptions-item label="真实姓名">{{ user.realName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="系统角色">{{ roleText(user.role) }}</el-descriptions-item>
          <el-descriptions-item label="账号状态">{{ user.status === 1 ? '正常' : '停用' }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <div class="panel">
        <h3>联系方式</h3>
        <el-form :model="form" label-position="top" class="profile-form">
          <el-form-item label="邮箱">
            <el-input v-model="form.email" clearable placeholder="请输入邮箱" />
          </el-form-item>
          <el-form-item label="电话">
            <el-input v-model="form.phone" clearable placeholder="请输入电话" />
          </el-form-item>
          <div class="toolbar">
            <el-button type="primary" :loading="saving" @click="save">保存</el-button>
            <el-button @click="resetForm">重置</el-button>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'

const userStore = useUserStore()
const saving = ref(false)
const form = reactive({
  email: '',
  phone: ''
})

const user = computed(() => userStore.user || {})
const roleText = (role) => ({ USER: '用户', ADMIN: '管理员' }[role] || role || '-')

const fillForm = () => {
  form.email = user.value.email || ''
  form.phone = user.value.phone || ''
}

const resetForm = () => {
  fillForm()
}

const save = async () => {
  saving.value = true
  try {
    await userStore.updateProfile({
      email: form.email,
      phone: form.phone
    })
    fillForm()
    ElMessage.success('个人信息已更新')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  if (userStore.token && !userStore.user) {
    await userStore.loadMe()
  }
  fillForm()
})
</script>

<style scoped>
.profile-grid {
  align-items: start;
}
h3 {
  margin: 0 0 14px;
}
.profile-form {
  max-width: 520px;
}
</style>

<template>
  <div class="auth-page">
    <div class="grid-overlay"></div>
    <div class="auth-panel">
      <div class="logo-area">
        <span class="logo-dot"></span>
        <h1>注册账号</h1>
      </div>
      <p class="subtitle">填写以下信息以创建您的 TeamFlowAI 账号</p>
      
      <el-form :model="form" label-position="top" class="auth-form">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="建议使用拼音或英文" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入安全密码" />
        </el-form-item>
        <el-form-item label="真实姓名">
          <el-input v-model="form.realName" placeholder="我们将以此名称显示在项目中" />
        </el-form-item>

        <div class="actions">
          <el-button type="primary" class="submit-btn" :loading="loading" @click="submit">注册并登录</el-button>
          <el-button class="login-btn" @click="backToLogin">返回登录</el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { register } from '../api/auth'
import { resolveLoginRedirect, toLoginWithRedirect } from '../utils/authRedirect'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const form = reactive({ username: '', password: '', realName: '' })
const redirectTarget = computed(() => resolveLoginRedirect(route))

const submit = async () => {
  if (!form.username || !form.password || !form.realName) {
    ElMessage.warning('请填写所有必填字段')
    return
  }
  loading.value = true
  try {
    await register(form)
    ElMessage.success('注册成功，请登录')
    router.push(toLoginWithRedirect(redirectTarget.value))
  } finally {
    loading.value = false
  }
}

const backToLogin = () => {
  router.push(toLoginWithRedirect(redirectTarget.value))
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #fafafa;
  padding: 24px;
  position: relative;
  overflow: hidden;
}

/* Elegant minimalist grid background */
.grid-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-image: 
    radial-gradient(at 50% 50%, rgba(255, 255, 255, 0) 0, #fafafa 100%),
    linear-gradient(to right, #e4e4e7 1px, transparent 1px),
    linear-gradient(to bottom, #e4e4e7 1px, transparent 1px);
  background-size: 100% 100%, 24px 24px, 24px 24px;
  z-index: 0;
  pointer-events: none;
}

.auth-panel {
  width: min(400px, 100%);
  background: #ffffff;
  border: 1px solid #e4e4e7;
  border-radius: 12px;
  padding: 40px;
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.04), 0 4px 6px -2px rgba(0, 0, 0, 0.02);
  z-index: 1;
  animation: slideUp 0.4s cubic-bezier(0.16, 1, 0.3, 1);
}

.logo-area {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.logo-dot {
  width: 10px;
  height: 10px;
  background-color: #09090b;
  border-radius: 50%;
}

h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
  letter-spacing: -0.04em;
  color: #09090b;
}

.subtitle {
  color: #71717a;
  font-size: 13px;
  font-weight: 400;
  margin: 0 0 28px 0;
}

.auth-form :deep(.el-form-item__label) {
  font-size: 12px;
  font-weight: 600;
  color: #27272a;
  padding-bottom: 4px;
}

.auth-form :deep(.el-input__wrapper) {
  padding: 4px 12px;
  border-radius: 6px !important;
  box-shadow: 0 0 0 1px #e4e4e7 inset !important;
}

.auth-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #a1a1aa inset !important;
}

.auth-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #09090b inset !important;
}

.actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 24px;
}

.submit-btn {
  width: 100%;
  height: 38px;
  font-size: 13px;
  font-weight: 600;
  border-radius: 6px !important;
  background-color: #09090b !important;
  border: none !important;
  color: #ffffff !important;
  cursor: pointer;
  transition: opacity 0.15s ease;
}

.submit-btn:hover {
  opacity: 0.9;
}

.login-btn {
  width: 100%;
  height: 38px;
  font-size: 13px;
  font-weight: 600;
  border-radius: 6px !important;
  border: 1px solid #e4e4e7 !important;
  color: #27272a !important;
  background-color: #ffffff !important;
  cursor: pointer;
  transition: background-color 0.15s ease;
}

.login-btn:hover {
  background-color: #f4f4f5 !important;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>

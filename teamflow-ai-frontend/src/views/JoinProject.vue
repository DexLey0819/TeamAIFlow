<template>
  <div class="join-page">
    <div class="join-panel">
      <el-skeleton v-if="loading" :rows="6" animated />
      <template v-else>
        <div class="join-head">
          <div>
            <h1>{{ preview.project?.projectName || '项目加入申请' }}</h1>
            <p>项目小组协作空间</p>
          </div>
          <el-tag :type="preview.joinEnabled ? 'success' : 'info'">{{ preview.joinEnabled ? '开放申请' : '暂不开放' }}</el-tag>
        </div>
        <p class="desc">{{ preview.project?.description || '暂无项目说明' }}</p>

        <el-alert v-if="!isLogin" type="warning" show-icon :closable="false" title="请先登录后提交加入申请" />
        <el-form :model="form" label-position="top" class="join-form">
          <el-form-item label="申请角色">
            <el-select v-model="form.requestedRoleId" :disabled="!isLogin || !preview.joinEnabled" style="width:100%">
              <el-option
                v-for="role in preview.availableRoles || []"
                :key="role.id"
                :label="`${role.roleName}（剩余 ${role.remainingCount ?? role.maxCount}）`"
                :value="role.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="姓名"><el-input v-model="form.applicantName" :disabled="!isLogin" /></el-form-item>
          <el-form-item label="邮箱"><el-input v-model="form.applicantEmail" :disabled="!isLogin" /></el-form-item>
          <el-form-item label="申请说明"><el-input v-model="form.applyNote" type="textarea" :rows="4" :disabled="!isLogin" /></el-form-item>
        </el-form>
        <div class="toolbar">
          <el-button v-if="!isLogin" type="primary" @click="goLogin">去登录</el-button>
          <el-button v-else type="primary" :disabled="!preview.joinEnabled" :loading="submitting" @click="submit">提交申请</el-button>
          <el-button @click="$router.push('/projects')">返回项目</el-button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { joinPreview, submitJoinApply } from '../api/join'
import { useUserStore } from '../stores/user'
import { toLoginWithRedirect } from '../utils/authRedirect'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(true)
const submitting = ref(false)
const preview = reactive({})
const isLogin = computed(() => Boolean(userStore.token))
const form = reactive({
  requestedRoleId: null,
  applicantName: '',
  applicantEmail: '',
  applyNote: ''
})

const load = async () => {
  loading.value = true
  try {
    if (userStore.token && !userStore.user) {
      await userStore.loadMe()
    }
    const res = await joinPreview(route.params.projectCode)
    Object.assign(preview, res)
    form.requestedRoleId = res.availableRoles?.[0]?.id || null
    form.applicantName = userStore.user?.realName || userStore.user?.username || ''
    form.applicantEmail = userStore.user?.email || ''
  } finally {
    loading.value = false
  }
}

const submit = async () => {
  submitting.value = true
  try {
    await submitJoinApply(preview.project.id, form)
    ElMessage.success('申请已提交，请等待项目经理审核')
  } finally {
    submitting.value = false
  }
}

const goLogin = () => {
  router.push(toLoginWithRedirect(route.fullPath))
}

onMounted(load)
</script>

<style scoped>
.join-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: #f5f7fb;
}
.join-panel {
  width: min(720px, 100%);
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 28px;
}
.join-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
h1 {
  margin: 0;
  color: #162033;
}
p {
  color: #667085;
}
.desc {
  line-height: 1.7;
}
.join-form {
  margin-top: 16px;
}
</style>

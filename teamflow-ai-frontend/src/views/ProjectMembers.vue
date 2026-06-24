<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">成员与申请</h1>
        <p class="page-subtitle">查看项目成员；项目经理可审核公开加入申请并分配项目角色</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>

    <div class="panel">
      <h3>项目成员</h3>
      <MemberList :members="members" />
      <el-empty v-if="!members.length" description="暂无成员" />
    </div>

    <div v-if="canReviewApplications" class="panel" style="margin-top:16px">
      <h3>加入申请</h3>
      <el-table :data="applies" border>
        <el-table-column prop="applicantName" label="申请人" width="140" />
        <el-table-column prop="applicantEmail" label="邮箱" min-width="180" />
        <el-table-column prop="requestedRoleName" label="申请角色" width="140" />
        <el-table-column prop="applyNote" label="说明" min-width="220" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="调整角色" width="180">
          <template #default="{ row }">
            <el-select v-model="row.targetRoleId" :disabled="row.status !== 'PENDING'" size="small">
              <el-option v-for="role in roles" :key="role.id" :label="role.roleName" :value="role.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="row.status !== 'PENDING'" @click="approve(row)">通过</el-button>
            <el-button link type="danger" :disabled="row.status !== 'PENDING'" @click="reject(row)">拒绝</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!applies.length" description="暂无加入申请" />
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import MemberList from '../components/MemberList.vue'
import { approveJoinApply, joinApplies, rejectJoinApply } from '../api/join'
import { listMembers, projectInitResult } from '../api/project'
import { useUserStore } from '../stores/user'
import { usePositiveProjectId } from '../utils/routeParams'

const route = useRoute()
const projectId = usePositiveProjectId(route)
const userStore = useUserStore()
const members = ref([])
const applies = ref([])
const roles = ref([])
let loadRequestId = 0
const currentMember = computed(() => members.value.find(member => member.userId === userStore.user?.id))
const canReviewApplications = computed(() => (
  userStore.user?.role === 'ADMIN' || currentMember.value?.memberRole === 'PROJECT_MANAGER'
))

const statusText = (status) => ({ PENDING: '待审核', APPROVED: '已通过', REJECTED: '已拒绝' }[status] || status)
const statusType = (status) => ({ PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }[status] || 'info')

const load = async () => {
  const requestId = ++loadRequestId
  members.value = []
  roles.value = []
  applies.value = []
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  if (userStore.token && !userStore.user) {
    await userStore.loadMe()
  }
  if (requestId !== loadRequestId || projectId.value !== currentProjectId) return
  const memberRes = await listMembers(currentProjectId)
  if (requestId !== loadRequestId || projectId.value !== currentProjectId) return
  members.value = memberRes || []

  if (!canReviewApplications.value) {
    roles.value = []
    applies.value = []
    return
  }

  const [applyRes, initRes] = await Promise.all([
    joinApplies(currentProjectId),
    projectInitResult(currentProjectId)
  ])
  if (requestId !== loadRequestId || projectId.value !== currentProjectId) return
  roles.value = initRes.roles || []
  applies.value = (applyRes || []).map(item => ({
    ...item,
    targetRoleId: item.requestedRoleId
  }))
}

const approve = async (row) => {
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  await approveJoinApply(currentProjectId, row.id, {
    targetRoleId: row.targetRoleId || row.requestedRoleId,
    reviewComment: '同意加入项目'
  })
  if (projectId.value !== currentProjectId) return
  ElMessage.success('已通过加入申请')
  load()
}

const reject = async (row) => {
  const currentProjectId = projectId.value
  if (!currentProjectId) return
  const { value } = await ElMessageBox.prompt('请输入拒绝原因', '拒绝加入申请', {
    confirmButtonText: '确认拒绝',
    cancelButtonText: '取消',
    inputValue: '当前角色名额或项目安排不匹配'
  })
  if (projectId.value !== currentProjectId) return
  await rejectJoinApply(currentProjectId, row.id, { reviewComment: value })
  if (projectId.value !== currentProjectId) return
  ElMessage.success('已拒绝加入申请')
  load()
}

watch(projectId, load, { immediate: true })
</script>

<style scoped>
h3 {
  margin-top: 0;
}
</style>

<template>
  <el-table :data="members" border>
    <el-table-column prop="realName" label="姓名" />
    <el-table-column prop="username" label="用户名" />
    <el-table-column prop="email" label="邮箱" min-width="180" />
    <el-table-column prop="memberRole" label="项目角色" width="170">
      <template #default="scope">
        <el-tag size="small" :type="roleType(scope.row.memberRole)" effect="dark">
          {{ scope.row.memberTitle || roleLabel(scope.row.memberRole) }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="joinSource" label="来源" width="100" />
    <el-table-column prop="joinTime" label="加入时间" width="180" />
  </el-table>
</template>

<script setup>
defineProps({ members: { type: Array, default: () => [] } })

const roleLabel = (role) => {
  const map = {
    PROJECT_MANAGER: '项目经理 (PM)',
    PRODUCT_MANAGER: '产品经理 (PD)',
    FRONTEND_DEV: '前端开发',
    BACKEND_DEV: '后端开发',
    DEVOPS: '运维开发 (DevOps)',
    QA: '测试开发 (QA)',
    MEMBER: '组员'
  }
  return map[role] || role
}

const roleType = (role) => {
  const map = {
    PROJECT_MANAGER: '',
    PRODUCT_MANAGER: 'warning',
    FRONTEND_DEV: 'success',
    BACKEND_DEV: 'success',
    DEVOPS: 'info',
    QA: 'info',
    MEMBER: 'info'
  }
  return map[role] || ''
}
</script>

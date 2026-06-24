<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">通知中心</h1>
        <p class="page-subtitle">查看任务、章节审核、加入申请、AI 和导出相关通知</p>
      </div>
      <div class="toolbar">
        <el-radio-group v-model="filterStatus" size="default" style="margin-right: 16px;">
          <el-radio-button label="all">全部</el-radio-button>
          <el-radio-button label="unread">未读</el-radio-button>
          <el-radio-button label="read">已读</el-radio-button>
        </el-radio-group>
        <el-tag type="warning">未读 {{ unread }}</el-tag>
        <el-button @click="markAll">全部已读</el-button>
      </div>
    </div>
    <div class="panel">
      <el-table :data="filteredItems" border>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.readFlag ? 'info' : 'danger'">{{ row.readFlag ? '已读' : '未读' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" width="180" />
        <el-table-column prop="projectName" label="项目" width="180" />
        <el-table-column prop="content" label="内容" min-width="260" />
        <el-table-column prop="createTime" label="时间" width="180" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="row.readFlag === 1" @click="markOne(row)">已读</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!filteredItems.length" description="暂无通知" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { markAllNotificationsRead, markNotificationRead, notifications, unreadCount } from '../api/notification'

const items = ref([])
const unread = ref(0)
const filterStatus = ref('all')

const filteredItems = computed(() => {
  if (filterStatus.value === 'read') {
    return items.value.filter(item => item.readFlag === 1)
  }
  if (filterStatus.value === 'unread') {
    return items.value.filter(item => item.readFlag === 0)
  }
  return items.value
})

const load = async () => {
  const [list, count] = await Promise.all([notifications(), unreadCount()])
  items.value = list || []
  unread.value = Number(count || 0)
}
const markOne = async (row) => {
  await markNotificationRead(row.id)
  ElMessage.success('通知已标记为已读')
  load()
}
const markAll = async () => {
  await markAllNotificationsRead()
  ElMessage.success('全部通知已读')
  load()
}

onMounted(load)
</script>

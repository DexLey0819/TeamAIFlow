<template>
  <div class="task-card">
    <div class="task-title">{{ task.title }}</div>
    <p>{{ task.description || '暂无说明' }}</p>
    <div class="task-meta">
      <el-tag size="small" :type="priorityType(task.priority)">{{ task.priority || 'MEDIUM' }}</el-tag>
      <span>{{ task.assigneeName || '未分配' }}</span>
    </div>
    <p v-if="task.blockReason" class="block-reason">阻塞：{{ task.blockReason }}</p>
    <div class="task-footer">
      <span>截止：{{ task.dueDate || '未设置' }}</span>
      <el-dropdown @command="(status) => $emit('status', task, status)">
        <el-button size="small">流转</el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="TODO">待开始</el-dropdown-item>
            <el-dropdown-item command="IN_PROGRESS">进行中</el-dropdown-item>
            <el-dropdown-item command="REVIEW">待检查</el-dropdown-item>
            <el-dropdown-item command="DONE">已完成</el-dropdown-item>
            <el-dropdown-item command="BLOCKED">阻塞</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup>
defineProps({ task: { type: Object, required: true } })
defineEmits(['status'])
const priorityType = (priority) => ({ URGENT: 'danger', HIGH: 'danger', MEDIUM: 'warning', LOW: 'info' }[priority] || 'warning')
</script>

<style scoped>
.task-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 10px;
}
.task-title {
  font-weight: 700;
  color: #162033;
}
p {
  margin: 8px 0;
  color: #667085;
  font-size: 13px;
  line-height: 1.5;
}
.task-meta,
.task-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  color: #667085;
}
.task-footer {
  margin-top: 10px;
}
.block-reason {
  color: #b42318;
}
</style>

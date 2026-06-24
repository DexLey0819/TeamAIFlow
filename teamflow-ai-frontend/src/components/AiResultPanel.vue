<template>
  <div class="panel ai-panel">
    <div class="ai-head">
      <div>
        <strong>{{ titleMap[record.type] || record.type }}</strong>
        <p>{{ record.createTime || '刚刚生成' }}</p>
      </div>
      <div class="toolbar">
        <el-tag v-if="record.source" size="small" type="info">{{ record.source }}</el-tag>
        <el-tag v-if="record.modelName" size="small">{{ record.modelName }}</el-tag>
        <el-tag v-if="record.status" size="small" :type="record.status === 'SUCCESS' ? 'success' : 'warning'">{{ record.status }}</el-tag>
        <el-tag v-if="record.riskLevel" type="warning">风险：{{ record.riskLevel }}</el-tag>
      </div>
    </div>
    <pre>{{ record.result }}</pre>
  </div>
</template>

<script setup>
defineProps({ record: { type: Object, required: true } })
const titleMap = {
  WEEKLY_REPORT: 'AI 项目周报',
  RISK_ANALYSIS: 'AI 风险分析',
  DOC_CHECK: '文档缺失检查',
  SUMMARY_REPORT: 'AI 总结报告'
}
</script>

<style scoped>
.ai-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
p {
  margin: 4px 0 0;
  color: #667085;
  font-size: 12px;
}
pre {
  margin: 14px 0 0;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.7;
  color: #1f2937;
  font-family: inherit;
}
</style>

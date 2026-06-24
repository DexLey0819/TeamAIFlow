<template>
  <div class="panel ai-panel">
    <div class="ai-head">
      <div>
        <strong style="color: #0f766e; font-size: 15px;">{{ titleMap[record.type] || record.type }}</strong>
        <p>生成人: <span style="font-weight: 600; color: #334155;">{{ record.username || '系统' }}</span> &nbsp;&nbsp;&nbsp;&nbsp; 时间: {{ record.createTime || '刚刚生成' }}</p>
      </div>
      <div class="toolbar">
        <el-tag v-if="record.source" size="small" type="info">{{ record.source }}</el-tag>
        <el-tag v-if="record.modelName" size="small">{{ record.modelName }}</el-tag>
        <el-tag v-if="record.status" size="small" :type="record.status === 'SUCCESS' || record.status === 'GENERATED' ? 'success' : 'warning'">{{ record.status }}</el-tag>
        <el-tag v-if="record.riskLevel" type="warning">风险：{{ record.riskLevel }}</el-tag>
      </div>
    </div>
    <pre>{{ record.result }}</pre>

    <!-- Collapsible prompt and context info -->
    <div v-if="record.prompt" style="margin-top: 12px; border-top: 1px dashed #e2e8f0; padding-top: 8px;">
      <el-collapse>
        <el-collapse-item name="prompt">
          <template #title>
            <span style="font-size: 12px; color: #64748b; font-weight: 500;">查看 AI 提示词与上下文</span>
          </template>
          <pre class="prompt-text" style="font-size: 12px; color: #475569; background: #f8fafc; padding: 10px; border-radius: 6px; border: 1px solid #e2e8f0; white-space: pre-wrap; font-family: inherit; margin: 4px 0 0; max-height: 200px; overflow-y: auto;">{{ record.prompt }}</pre>
        </el-collapse-item>
      </el-collapse>
    </div>
  </div>
</template>

<script setup>
defineProps({ record: { type: Object, required: true } })
const titleMap = {
  WEEKLY_REPORT: 'AI 项目周报',
  RISK_ANALYSIS: 'AI 风险分析',
  DOC_CHECK: '文档缺失检查',
  SUMMARY_REPORT: 'AI 总结报告',
  SECTION_GENERATE: 'AI 智能体记录'
}
</script>

<style scoped>
.ai-panel {
  border: 1px solid #e2e8f0;
  box-shadow: 0 1px 3px 0 rgb(0 0 0 / 0.05);
}
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

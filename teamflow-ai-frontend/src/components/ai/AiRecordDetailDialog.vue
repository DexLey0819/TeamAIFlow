<template>
  <el-dialog
    :model-value="modelValue"
    :title="record ? getAiRecordPresentation(record.type).label : 'AI 分析详情'"
    width="min(820px, 92vw)"
    top="7vh"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @closed="emit('closed')"
  >
    <div v-if="record" class="detail-content">
      <div class="metadata-grid">
        <div><span>生成者</span>{{ record.username || '系统' }}</div>
        <div><span>生成时间</span>{{ record.createTime || '刚刚生成' }}</div>
        <div>
          <span>状态</span>
          <el-tag size="small" :type="getAiStatusPresentation(record.status).type">
            {{ getAiStatusPresentation(record.status).label }}
          </el-tag>
        </div>
        <div v-if="record.modelName"><span>模型</span>{{ record.modelName }}</div>
        <div v-if="record.source"><span>来源</span>{{ record.source }}</div>
        <div v-if="record.riskLevel"><span>风险等级</span>{{ record.riskLevel }}</div>
      </div>

      <div class="section-title">AI 分析内容</div>
      <pre class="result-content">{{ record.result || '暂无 AI 分析内容' }}</pre>

      <el-collapse v-if="record.prompt" class="prompt-collapse">
        <el-collapse-item title="查看 AI 提示词与上下文" name="prompt">
          <pre class="prompt-content">{{ record.prompt }}</pre>
        </el-collapse-item>
      </el-collapse>
    </div>
  </el-dialog>
</template>

<script setup>
import { getAiRecordPresentation, getAiStatusPresentation } from '../../utils/aiRecordPresentation'

defineProps({
  modelValue: { type: Boolean, default: false },
  record: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'closed'])
</script>

<style scoped>
.detail-content {
  color: #334155;
}

.metadata-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 20px;
  padding: 14px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
  font-size: 13px;
}

.metadata-grid div {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.metadata-grid span:first-child {
  color: #64748b;
}

.section-title {
  margin: 20px 0 10px;
  color: #0f172a;
  font-weight: 700;
}

.result-content,
.prompt-content {
  margin: 0;
  padding: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  color: #334155;
  font: inherit;
  line-height: 1.75;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.result-content {
  max-height: 52vh;
  overflow-y: auto;
}

.prompt-collapse {
  margin-top: 16px;
}

.prompt-content {
  max-height: 240px;
  overflow-y: auto;
  background: #f8fafc;
  font-size: 12px;
}

@media (max-width: 640px) {
  .metadata-grid {
    grid-template-columns: 1fr;
  }
}
</style>

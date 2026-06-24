<template>
  <el-dialog
    :model-value="visible"
    title="插入 PlantUML"
    width="600px"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form label-position="top">
      <el-form-item label="PlantUML 源码">
        <el-input
          v-model="source"
          type="textarea"
          :rows="10"
          placeholder="@startuml&#10;用户 -> 系统: 提交内容&#10;系统 --> 用户: 返回结果&#10;@enduml"
        />
      </el-form-item>
    </el-form>
    <div class="plantuml-preview">
      <div class="preview-status" :class="{ 'is-available': previewState.available }">
        {{ previewState.message }}
      </div>
      <div v-if="previewState.available && previewState.imageUrl" class="preview-image-wrap" style="margin-top: 8px; text-align: center;">
        <img :src="previewState.imageUrl" alt="PlantUML Preview" style="max-width: 100%; max-height: 240px; border: 1px solid #e5e7eb; border-radius: 4px; background: #fff; padding: 4px;" />
      </div>
      <pre v-else class="preview-fallback">{{ previewState.source }}</pre>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="emit('update:visible', false)">取消</el-button>
        <el-button type="primary" @click="handleInsert">插入</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { plantUmlPreviewState } from '../../utils/plantuml'

const props = defineProps({
  visible: {
    type: Boolean,
    required: true
  }
})

const emit = defineEmits(['update:visible', 'insert'])

const source = ref('')

const defaultTemplate = `@startuml
用户 -> 系统: 提交内容
系统 --> 用户: 返回结果
@enduml`

watch(
  () => props.visible,
  (newVal) => {
    if (newVal) {
      source.value = defaultTemplate
    }
  }
)

const previewState = computed(() => plantUmlPreviewState(source.value))

const handleInsert = () => {
  const block = `\`\`\`plantuml\n${source.value.trim()}\n\`\`\``
  emit('insert', block)
}
</script>

<style scoped>
.plantuml-preview {
  margin-top: 16px;
  padding: 12px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

.preview-status {
  color: #667085;
  font-size: 13px;
}

.preview-status.is-available {
  color: #047857;
}

.preview-fallback {
  margin: 8px 0 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  color: #1f2937;
  font-size: 13px;
  line-height: 1.5;
}
</style>

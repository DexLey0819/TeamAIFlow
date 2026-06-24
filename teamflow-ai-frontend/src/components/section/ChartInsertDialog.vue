<template>
  <el-dialog
    :model-value="visible"
    title="插入图表"
    width="500px"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form :model="form" label-width="80px">
      <el-form-item label="图表类型">
        <el-select v-model="form.type" placeholder="请选择图表类型">
          <el-option label="柱状图" value="bar" />
          <el-option label="折线图" value="line" />
          <el-option label="饼图" value="pie" />
        </el-select>
      </el-form-item>
      <el-form-item label="图表标题">
        <el-input v-model="form.title" placeholder="请输入图表标题" />
      </el-form-item>
      <el-form-item label="图表类别">
        <el-input v-model="form.categoriesText" placeholder="类别用英文逗号分隔，如：A, B, C" />
      </el-form-item>
      <el-form-item label="数值数据">
        <el-input v-model="form.valuesText" placeholder="数值用英文逗号分隔，如：3, 5, 2" />
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="emit('update:visible', false)">取消</el-button>
        <el-button type="primary" @click="handleInsert">插入</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, watch } from 'vue'

const props = defineProps({
  visible: {
    type: Boolean,
    required: true
  }
})

const emit = defineEmits(['update:visible', 'insert'])

const form = reactive({
  type: 'bar',
  title: '图表标题',
  categoriesText: '类别 A, 类别 B, 类别 C',
  valuesText: '3, 5, 2'
})

watch(
  () => props.visible,
  (newVal) => {
    if (newVal) {
      form.type = 'bar'
      form.title = '图表标题'
      form.categoriesText = '类别 A, 类别 B, 类别 C'
      form.valuesText = '3, 5, 2'
    }
  }
)

const handleInsert = () => {
  const categories = form.categoriesText
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
  const data = form.valuesText
    .split(',')
    .map((item) => Number(item.trim()))
    .filter((num) => Number.isFinite(num))

  const block = `\`\`\`teamflow-chart
{
  "type": "${form.type}",
  "title": "${form.title}",
  "categories": ${JSON.stringify(categories)},
  "series": [
    { "name": "数值", "data": ${JSON.stringify(data)} }
  ]
}
\`\`\``

  emit('insert', block)
}
</script>

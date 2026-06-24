<template>
  <section class="ai-analysis-list">
    <div v-if="error" class="load-alert">
      <el-alert
        title="AI 分析记录加载失败"
        :description="error"
        type="error"
        show-icon
        :closable="false"
      />
      <el-button size="small" @click="emit('retry')">重新加载</el-button>
    </div>

    <el-tabs v-model="selectedCategory" class="category-tabs">
      <el-tab-pane v-for="item in AI_RECORD_CATEGORIES" :key="item.key" :name="item.key">
        <template #label>
          <span class="category-label">
            {{ item.label }}
            <span class="category-count">{{ categoryCounts[item.key] }}</span>
          </span>
        </template>
      </el-tab-pane>
    </el-tabs>

    <div class="category-purpose">
      <span>{{ currentCategory.label }}</span>
      {{ currentCategory.purpose }}
    </div>

    <el-table
      v-loading="loading"
      :data="pageResult.items"
      row-key="id"
      stripe
      class="records-table"
      empty-text="当前分类暂无分析记录"
    >
      <el-table-column label="分析名称" min-width="130">
        <template #default="{ row }">
          <el-tag effect="plain">{{ getAiRecordPresentation(row.type).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="使用用途" min-width="300">
        <template #default="{ row }">
          <span class="purpose-text">{{ getAiRecordPresentation(row.type).purpose }}</span>
        </template>
      </el-table-column>
      <el-table-column label="生成者" min-width="110">
        <template #default="{ row }">{{ row.username || '系统' }}</template>
      </el-table-column>
      <el-table-column label="生成时间" min-width="170">
        <template #default="{ row }">{{ row.createTime || '刚刚生成' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="getAiStatusPresentation(row.status).type">
            {{ getAiStatusPresentation(row.status).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="emit('view', row)">查看详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="pageResult.total" class="pagination-row">
      <span>共 {{ pageResult.total }} 条，每页 10 条</span>
      <el-pagination
        v-model:current-page="currentPage"
        background
        layout="prev, pager, next"
        :page-size="PAGE_SIZE"
        :total="pageResult.total"
      />
    </div>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import {
  AI_RECORD_CATEGORIES,
  filterAiRecords,
  getAiCategoryCounts,
  getAiRecordPresentation,
  getAiStatusPresentation,
  paginateAiRecords
} from '../../utils/aiRecordPresentation'

const PAGE_SIZE = 10
const props = defineProps({
  records: { type: Array, default: () => [] },
  category: { type: String, default: 'ALL' },
  pageResetKey: { type: Number, default: 0 },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' }
})
const emit = defineEmits(['update:category', 'view', 'retry'])
const currentPage = ref(1)

const selectedCategory = computed({
  get: () => props.category,
  set: (value) => emit('update:category', value)
})
const categoryCounts = computed(() => getAiCategoryCounts(props.records))
const currentCategory = computed(() => (
  AI_RECORD_CATEGORIES.find((item) => item.key === props.category) || AI_RECORD_CATEGORIES[0]
))
const filteredRecords = computed(() => filterAiRecords(props.records, props.category))
const pageResult = computed(() => paginateAiRecords(filteredRecords.value, currentPage.value, PAGE_SIZE))

watch(() => props.category, () => { currentPage.value = 1 })
watch(() => props.pageResetKey, () => { currentPage.value = 1 })
watch(() => filteredRecords.value.length, () => {
  currentPage.value = pageResult.value.page
})
</script>

<style scoped>
.ai-analysis-list {
  min-width: 0;
}

.load-alert {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.load-alert :deep(.el-alert) {
  flex: 1;
}

.category-tabs {
  margin-top: -4px;
}

.category-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.category-count {
  min-width: 20px;
  padding: 1px 6px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 12px;
  line-height: 18px;
  text-align: center;
}

.category-purpose {
  margin: 2px 0 14px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f0fdfa;
  color: #475569;
  font-size: 13px;
}

.category-purpose span {
  margin-right: 8px;
  color: #0f766e;
  font-weight: 700;
}

.records-table {
  width: 100%;
}

.purpose-text {
  color: #475569;
  line-height: 1.55;
}

.pagination-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 16px;
  color: #64748b;
  font-size: 13px;
}

@media (max-width: 720px) {
  .pagination-row {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>

const FALLBACK_META = {
  category: 'OTHER',
  label: '其他分析',
  purpose: '承接未识别的 AI 分析记录，便于后续核对。'
}

const RECORD_META = {
  WEEKLY_REPORT: {
    category: 'WEEKLY_REPORT',
    label: '项目周报',
    purpose: '汇总当前任务、进度和近期成果，用于阶段汇报。'
  },
  RISK_ANALYSIS: {
    category: 'RISK_ANALYSIS',
    label: '风险分析',
    purpose: '识别延期、阻塞和资源风险，用于项目预警。'
  },
  DOC_CHECK: {
    category: 'DOC_CHECK',
    label: '文档检查',
    purpose: '检查项目资料缺失与完整性，用于交付前补漏。'
  },
  SUMMARY_REPORT: {
    category: 'SUMMARY_REPORT',
    label: '总结报告',
    purpose: '归纳项目成果和整体情况，用于结项总结。'
  },
  SECTION_GENERATE: {
    category: 'SECTION_GENERATE',
    label: '智能体记录',
    purpose: '保存章节 AI 生成过程，用于追踪辅助创作记录。'
  }
}

export const AI_RECORD_CATEGORIES = [
  { key: 'ALL', label: '全部', purpose: '查看项目内的全部 AI 分析记录。' },
  ...Object.values(RECORD_META).map(({ category, label, purpose }) => ({ key: category, label, purpose })),
  { key: 'OTHER', label: '其他分析', purpose: FALLBACK_META.purpose }
]

export const getAiRecordPresentation = (type) => {
  const meta = RECORD_META[type]
  if (meta) return meta
  return {
    ...FALLBACK_META,
    label: type || FALLBACK_META.label
  }
}

export const getAiCategoryCounts = (records = []) => {
  const counts = Object.fromEntries(AI_RECORD_CATEGORIES.map(({ key }) => [key, 0]))
  counts.ALL = records.length
  records.forEach((record) => {
    counts[getAiRecordPresentation(record?.type).category] += 1
  })
  return counts
}

export const filterAiRecords = (records = [], category = 'ALL') => {
  if (category === 'ALL') return records
  return records.filter((record) => getAiRecordPresentation(record?.type).category === category)
}

export const paginateAiRecords = (records = [], requestedPage = 1, pageSize = 10) => {
  const total = records.length
  const pageCount = Math.max(1, Math.ceil(total / pageSize))
  const page = Math.min(Math.max(1, requestedPage), pageCount)
  const start = (page - 1) * pageSize
  return { items: records.slice(start, start + pageSize), total, page, pageCount }
}

export const getAiStatusPresentation = (status) => {
  if (status === 'SUCCESS') return { label: '成功', type: 'success' }
  if (status === 'GENERATED') return { label: '已生成', type: 'success' }
  if (status === 'FAILED') return { label: '失败', type: 'danger' }
  return { label: status || '未知', type: 'warning' }
}

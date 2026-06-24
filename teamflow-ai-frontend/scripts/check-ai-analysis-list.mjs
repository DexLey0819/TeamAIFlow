import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  AI_RECORD_CATEGORIES,
  filterAiRecords,
  getAiCategoryCounts,
  getAiRecordPresentation,
  getAiStatusPresentation,
  paginateAiRecords
} from '../src/utils/aiRecordPresentation.js'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const read = (path) => readFileSync(resolve(root, path), 'utf8')
const exists = (path) => existsSync(resolve(root, path))

const records = [
  { id: 1, type: 'WEEKLY_REPORT' },
  { id: 2, type: 'RISK_ANALYSIS' },
  { id: 3, type: 'WEEKLY_REPORT' },
  { id: 4, type: 'FUTURE_ANALYSIS' }
]

assert.deepEqual(
  AI_RECORD_CATEGORIES.map((category) => category.key),
  ['ALL', 'WEEKLY_REPORT', 'RISK_ANALYSIS', 'DOC_CHECK', 'SUMMARY_REPORT', 'SECTION_GENERATE', 'OTHER']
)
assert.equal(getAiRecordPresentation('WEEKLY_REPORT').label, '项目周报')
assert.match(getAiRecordPresentation('RISK_ANALYSIS').purpose, /项目预警/)
assert.equal(getAiRecordPresentation('FUTURE_ANALYSIS').category, 'OTHER')
assert.equal(getAiRecordPresentation('FUTURE_ANALYSIS').label, 'FUTURE_ANALYSIS')
assert.equal(getAiRecordPresentation(null).label, '其他分析')

assert.deepEqual(getAiCategoryCounts(records), {
  ALL: 4,
  WEEKLY_REPORT: 2,
  RISK_ANALYSIS: 1,
  DOC_CHECK: 0,
  SUMMARY_REPORT: 0,
  SECTION_GENERATE: 0,
  OTHER: 1
})
assert.deepEqual(filterAiRecords(records, 'WEEKLY_REPORT').map((record) => record.id), [1, 3])
assert.deepEqual(filterAiRecords(records, 'OTHER').map((record) => record.id), [4])
assert.deepEqual(filterAiRecords(records, 'ALL').map((record) => record.id), [1, 2, 3, 4])

assert.deepEqual(paginateAiRecords(records, 2, 2), {
  items: records.slice(2, 4),
  total: 4,
  page: 2,
  pageCount: 2
})
assert.equal(paginateAiRecords(records, 99, 3).page, 2)
assert.equal(paginateAiRecords([], 3, 10).page, 1)

assert.deepEqual(getAiStatusPresentation('SUCCESS'), { label: '成功', type: 'success' })
assert.deepEqual(getAiStatusPresentation('GENERATED'), { label: '已生成', type: 'success' })
assert.deepEqual(getAiStatusPresentation('FAILED'), { label: '失败', type: 'danger' })
assert.deepEqual(getAiStatusPresentation('WAITING'), { label: 'WAITING', type: 'warning' })

console.log('AI analysis list checks passed')

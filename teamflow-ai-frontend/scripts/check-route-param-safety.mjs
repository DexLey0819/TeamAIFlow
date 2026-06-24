import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const workspaceRoot = resolve(root, '..')
const read = (path) => readFileSync(resolve(workspaceRoot, path), 'utf8')
const exists = (path) => existsSync(resolve(workspaceRoot, path))

assert.ok(exists('teamflow-ai-frontend/src/utils/routeParams.js'), 'route param helper should exist')

const helper = read('teamflow-ai-frontend/src/utils/routeParams.js')
assert.match(helper, /export const toPositiveInteger/, 'helper should export toPositiveInteger')
assert.match(helper, /export const usePositiveProjectId/, 'helper should export usePositiveProjectId')
assert.match(helper, /export const usePositiveSectionId/, 'helper should export usePositiveSectionId')
assert.match(helper, /export const isManagementDeliverySection/, 'helper should recognize management delivery section route')

const router = read('teamflow-ai-frontend/src/router/index.js')
assert.match(router, /toPositiveInteger/, 'router guard should use route param helper')
assert.match(router, /return '\/projects'/, 'router guard should redirect invalid project ids')
assert.match(router, /return `\/projects\/\$\{projectId\}\/sections`/, 'router guard should redirect invalid section ids')

const layout = read('teamflow-ai-frontend/src/views/Layout.vue')
assert.match(layout, /<RouterView\s+:key="\$route\.fullPath"\s*\/>/, 'layout should key nested route view by full path')
assert.match(layout, /usePositiveProjectId/, 'layout should use shared project id helper')

const sectionEditor = read('teamflow-ai-frontend/src/views/SectionEditor.vue')
assert.match(sectionEditor, /usePositiveProjectId/, 'section editor should use shared project helper')
assert.match(sectionEditor, /usePositiveSectionId/, 'section editor should use shared section helper')
assert.doesNotMatch(sectionEditor, /const toPositiveInteger = \(raw\) =>/, 'section editor should not keep a local route parser')

const projectViews = [
  'SectionEditor.vue',
  'ProjectOverview.vue',
  'ProjectMembers.vue',
  'SectionList.vue',
  'FileManage.vue',
  'ProjectExport.vue',
  'ProjectReviews.vue',
  'SectionReviewPage.vue',
  'TaskBoard.vue',
  'ProgressRecord.vue',
  'ManagementDelivery.vue'
]

const routeParamExpression = (name) =>
  String.raw`(?:useRoute\(\)|route)\.params(?:\.${name}|\?\.\s*${name}|\s*\[\s*['"]${name}['"]\s*\]|\?\.\s*\[\s*['"]${name}['"]\s*\])`

const unsafeRouteParamParsers = (name) => [
  new RegExp(String.raw`Number\s*\(\s*${routeParamExpression(name)}\s*\)`),
  new RegExp(String.raw`parseInt\s*\(\s*${routeParamExpression(name)}\b`),
  new RegExp(String.raw`(?:^|[^\w$])\+\s*${routeParamExpression(name)}\b`)
]

for (const file of projectViews) {
  const source = read(`teamflow-ai-frontend/src/views/${file}`)
  for (const parser of unsafeRouteParamParsers('id')) {
    assert.doesNotMatch(
      source,
      parser,
      `${file} should not parse projectId directly from route params`
    )
  }
  for (const parser of unsafeRouteParamParsers('sectionId')) {
    assert.doesNotMatch(
      source,
      parser,
      `${file} should not parse sectionId directly from route params`
    )
  }
}

console.log('route parameter safety checks passed')

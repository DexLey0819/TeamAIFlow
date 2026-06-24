import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const workspaceRoot = resolve(root, '..')

const read = (path) => readFileSync(resolve(workspaceRoot, path), 'utf8')
const exists = (path) => existsSync(resolve(workspaceRoot, path))

const closingBraceIndex = (source, openBraceIndex, message) => {
  let depth = 0
  for (let index = openBraceIndex; index < source.length; index += 1) {
    if (source[index] === '{') {
      depth += 1
    } else if (source[index] === '}') {
      depth -= 1
      if (depth === 0) {
        return index
      }
    }
  }
  assert.fail(`${message} closing brace should exist`)
}

const methodBlock = (source, signaturePattern, message) => {
  const start = source.search(signaturePattern)
  assert.notEqual(start, -1, message)
  const openBraceIndex = source.indexOf('{', start)
  assert.notEqual(openBraceIndex, -1, `${message} body should exist`)
  const end = closingBraceIndex(source, openBraceIndex, message)
  return source.slice(start, end + 1)
}

const annotatedMethodBlock = (source, annotationPattern, message) => {
  const annotationStart = source.search(annotationPattern)
  assert.notEqual(annotationStart, -1, message)
  const afterAnnotation = source.slice(annotationStart)
  const signatureMatch = afterAnnotation.match(/\n\s*(?:public|private|protected)\s+[\s\S]*?\(/)
  assert.notEqual(signatureMatch, null, `${message} signature should exist`)
  const signatureStart = annotationStart + signatureMatch.index
  const openBraceIndex = source.indexOf('{', signatureStart)
  assert.notEqual(openBraceIndex, -1, `${message} body should exist`)
  const end = closingBraceIndex(source, openBraceIndex, message)
  return source.slice(annotationStart, end + 1)
}

const blockStatements = (source, statementPattern, message) => {
  const blocks = []
  for (const match of source.matchAll(statementPattern)) {
    const openBraceIndex = source.indexOf('{', match.index)
    if (openBraceIndex !== -1) {
      const end = closingBraceIndex(source, openBraceIndex, message)
      blocks.push(source.slice(match.index, end + 1))
    }
  }
  return blocks
}

assert.ok(exists('teamflow-ai-frontend/src/components/ProjectSidebar.vue'), 'project sidebar component should exist')
assert.ok(exists('teamflow-ai-frontend/src/components/section/SectionContentRenderer.vue'), 'section renderer should exist')
assert.ok(exists('teamflow-ai-frontend/src/components/section/SectionChartBlock.vue'), 'chart block component should exist')
assert.ok(exists('teamflow-ai-frontend/src/components/section/ChartInsertDialog.vue'), 'chart insert dialog should exist')
assert.ok(exists('teamflow-ai-frontend/src/components/section/PlantUmlPanel.vue'), 'PlantUML panel should exist')
assert.ok(exists('teamflow-ai-frontend/src/utils/sectionContentBlocks.js'), 'content block utility should exist')
assert.ok(exists('teamflow-ai-frontend/src/utils/plantuml.js'), 'PlantUML utility should exist')

const layout = read('teamflow-ai-frontend/src/views/Layout.vue')
assert.match(layout, /ProjectSidebar/, 'Layout should render ProjectSidebar for project routes')
assert.match(layout, /isProjectRoute/, 'Layout should decide when route is inside a project')

const projectSidebar = read('teamflow-ai-frontend/src/components/ProjectSidebar.vue')
assert.match(projectSidebar, /首页/, 'project sidebar should include home link')
assert.match(projectSidebar, /项目详情/, 'project sidebar should include project detail group')
assert.match(projectSidebar, /具体章节/, 'project sidebar should include concrete sections')
assert.match(projectSidebar, /projectSections/, 'project sidebar should load sections')

const sectionEditor = read('teamflow-ai-frontend/src/views/SectionEditor.vue')
assert.match(sectionEditor, /isEditing/, 'section editor should have explicit edit mode')
assert.match(sectionEditor, /dirty/, 'section editor should track dirty state')
assert.match(sectionEditor, /onBeforeRouteLeave/, 'section editor should guard route changes')
assert.match(sectionEditor, /beforeunload/, 'section editor should guard browser unload')
assert.match(sectionEditor, /插入表格/, 'section editor should offer table insertion')
assert.match(sectionEditor, /插入图表/, 'section editor should offer chart insertion')
assert.match(sectionEditor, /插入 PlantUML/, 'section editor should offer PlantUML insertion')
assert.match(sectionEditor, /插入图片/, 'section editor should offer image insertion')
assert.match(sectionEditor, /uploadFile/, 'section editor should upload inline images')

const fileController = read('teamflow-ai-backend/src/main/java/com/example/teamflow/controller/FileController.java')
const fileControllerUpload = annotatedMethodBlock(
  fileController,
  /@PostMapping\(value = "\/upload"/,
  'file controller upload method should exist'
)
assert.match(fileControllerUpload, /@RequestParam\s*\(\s*required\s*=\s*false\s*\)\s+Long\s+sectionId/, 'file upload should accept sectionId')
assert.match(fileControllerUpload, /fileBizService\.upload\(projectId,\s*sectionId,\s*documentType,\s*file\)/, 'file upload should pass sectionId to the service')
const fileControllerInline = annotatedMethodBlock(
  fileController,
  /@GetMapping\("\/inline\/\{id\}"\)/,
  'file controller inline image endpoint should exist'
)
assert.match(fileControllerInline, /fileBizService\.getInlineImageEntity\(id\)/, 'file controller inline endpoint should load inline image entity')

const fileService = read('teamflow-ai-backend/src/main/java/com/example/teamflow/service/FileBizService.java')
const fileServiceUpload = methodBlock(
  fileService,
  /public FileVO upload\(/,
  'file service upload method should exist'
)
assert.match(fileServiceUpload, /public FileVO upload\([^)]*Long sectionId[^)]*\)/, 'file service upload should accept sectionId')
assert.match(fileServiceUpload, /ProjectFile projectFile = new ProjectFile\(\)[\s\S]*?projectFile\.setSectionId\(sectionId\)[\s\S]*?projectFileMapper\.insert\(projectFile\)/, 'file upload should persist sectionId')
const fileServiceInline = methodBlock(
  fileService,
  /getInlineImageEntity\(Long id\)/,
  'file service should define inline image lookup'
)
assert.match(fileServiceInline, /!isInlineImage\(file\.getFileType\(\)\)/, 'file service should validate inline image MIME types')
assert.match(fileServiceInline, /!isInlineImage\(file\.getFileType\(\)\)[\s\S]*?throw new BizException/, 'file service should reject non-image inline files')

const sectionService = read('teamflow-ai-backend/src/main/java/com/example/teamflow/service/SectionService.java')
const saveDraft = methodBlock(
  sectionService,
  /public SectionContentVO saveDraft\(Long sectionId, SectionContentDTO dto\)/,
  'section service saveDraft method should exist'
)
const saveDraftIfBlocks = blockStatements(saveDraft, /\bif\s*\(/g, 'saveDraft if block')
assert.match(saveDraft, /APPROVED/, 'section service should handle approved sections when saving new drafts')
assert.ok(
  !saveDraftIfBlocks.some((block) => /APPROVED\.equalsIgnoreCase\(section\.getStatus\(\)\)/.test(block) && /throw new BizException/.test(block)),
  'approved sections should not be locked out of draft saving'
)
assert.ok(
  saveDraftIfBlocks.some((block) => /REVIEWING\.equalsIgnoreCase\(section\.getStatus\(\)\)/.test(block) && /throw new BizException/.test(block)),
  'reviewing sections should remain locked'
)

for (const file of [
  'ProjectOverview.vue',
  'ProjectMembers.vue',
  'SectionList.vue',
  'SectionEditor.vue',
  'FileManage.vue',
  'TaskBoard.vue',
  'ProgressRecord.vue',
  'ProjectExport.vue'
]) {
  const source = read(`teamflow-ai-frontend/src/views/${file}`)
  assert.doesNotMatch(source, /<ProjectNav/, `${file} should not render the old horizontal ProjectNav`)
}

console.log('project workspace editor checks passed')

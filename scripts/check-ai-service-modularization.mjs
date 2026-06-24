import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(fileURLToPath(new URL('..', import.meta.url)))
const read = (path) => readFileSync(resolve(root, path), 'utf8')
const exists = (path) => existsSync(resolve(root, path))
const escapeRegex = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

const aiServiceDir = 'teamflow-ai-backend/src/main/java/com/example/teamflow/service/ai'
const requiredFiles = [
  'ProjectAiContext.java',
  'AiContextBuilder.java',
  'AiRecordService.java',
  'AiTextSupport.java',
  'ProjectAiService.java',
  'SectionAgentService.java',
  'ProgressExtractionAiService.java'
]

for (const file of requiredFiles) {
  assert.ok(exists(`${aiServiceDir}/${file}`), `${file} should exist`)
}

const facade = read('teamflow-ai-backend/src/main/java/com/example/teamflow/service/AiBizService.java')
assert.match(facade, /private final ProjectAiService projectAiService;/, 'AiBizService should delegate project AI behavior')
assert.match(facade, /private final SectionAgentService sectionAgentService;/, 'AiBizService should delegate section agent behavior')
assert.match(facade, /private final ProgressExtractionAiService progressExtractionAiService;/, 'AiBizService should delegate progress extraction')
assert.match(facade, /private final AiRecordService aiRecordService;/, 'AiBizService should delegate records')
assert.doesNotMatch(facade, /private\s+final\s+\w*Mapper\s+\w+;/, 'AiBizService facade should not inject mappers directly')
assert.doesNotMatch(facade, /private\s+final\s+AiClient\s+\w+;/, 'AiBizService facade should not call AiClient directly')
assert.doesNotMatch(facade, /\b(buildPrompt|fallback|loadContext|saveRiskSnapshot|getLocalFallbackForSection|getLocalFallbackChatResponse)\s*\(/, 'prompt/context internals should move out of facade')

const facadeDelegations = [
  ['weeklyReport', 'projectAiService', 'projectId'],
  ['riskAnalysis', 'projectAiService', 'projectId'],
  ['documentCheck', 'projectAiService', 'projectId'],
  ['summaryReport', 'projectAiService', 'projectId'],
  ['records', 'aiRecordService', 'projectId'],
  ['extractProgress', 'progressExtractionAiService', 'projectId\\s*,\\s*reportText\\s*,\\s*tasks'],
  ['generateSectionContent', 'sectionAgentService', 'projectId\\s*,\\s*sectionId'],
  ['chatSectionContent', 'sectionAgentService', 'projectId\\s*,\\s*sectionId\\s*,\\s*messages']
]

for (const [method, service, argumentsPattern] of facadeDelegations) {
  const delegationPattern = new RegExp(
    `public\\s+[^;{}]+\\b${method}\\s*\\([^)]*\\)\\s*\\{\\s*return\\s+${service}\\s*\\.\\s*${method}\\s*\\(\\s*${argumentsPattern}\\s*\\)\\s*;\\s*\\}`,
    's'
  )
  assert.match(facade, delegationPattern, `AiBizService.${method} should delegate to ${service}.${method}`)
}

const controller = read('teamflow-ai-backend/src/main/java/com/example/teamflow/controller/AiController.java')
assert.match(
  controller,
  /@RequestMapping\s*\(\s*"\/api\/ai\/projects\/\{id\}"\s*\)/,
  'AiController should keep the project-scoped base mapping'
)

const pathVariableId = '@PathVariable\\s*\\(\\s*"id"\\s*\\)\\s+Long\\s+id'
const pathVariableSectionId = '@PathVariable\\s*\\(\\s*"sectionId"\\s*\\)\\s+Long\\s+sectionId'
const stringMapResult = 'Result\\s*<\\s*Map\\s*<\\s*String\\s*,\\s*String\\s*>\\s*>'
const aiRecordResult = 'Result\\s*<\\s*AiRecordVO\\s*>'
const controllerEndpoints = [
  ['PostMapping', '/sections/{sectionId}/generate', stringMapResult, 'generateSectionContent', `${pathVariableId}\\s*,\\s*${pathVariableSectionId}`],
  ['PostMapping', '/sections/{sectionId}/chat', stringMapResult, 'chatSectionContent', `${pathVariableId}\\s*,\\s*${pathVariableSectionId}\\s*,\\s*@RequestBody\\s+Map\\s*<\\s*String\\s*,\\s*Object\\s*>\\s+body`],
  ['PostMapping', '/weekly-report', aiRecordResult, 'weeklyReport', pathVariableId],
  ['PostMapping', '/risk-analysis', aiRecordResult, 'riskAnalysis', pathVariableId],
  ['PostMapping', '/document-check', aiRecordResult, 'documentCheck', pathVariableId],
  ['PostMapping', '/summary-report', aiRecordResult, 'summaryReport', pathVariableId],
  ['GetMapping', '/records', 'Result\\s*<\\s*List\\s*<\\s*AiRecordVO\\s*>\\s*>', 'records', pathVariableId],
  ['PostMapping', '/extract-progress', 'Result\\s*<\\s*Map\\s*<\\s*String\\s*,\\s*Object\\s*>\\s*>', 'extractProgress', `${pathVariableId}\\s*,\\s*@RequestBody\\s+Map\\s*<\\s*String\\s*,\\s*Object\\s*>\\s+body`]
]

for (const [mapping, path, returnType, method, parameters] of controllerEndpoints) {
  const endpointPattern = new RegExp(
    `@${mapping}\\s*\\(\\s*"${escapeRegex(path)}"\\s*\\)\\s*public\\s+${returnType}\\s+${method}\\s*\\(\\s*${parameters}\\s*\\)\\s*\\{`,
    's'
  )
  assert.match(controller, endpointPattern, `AiController.${method} should keep its mapping and signature`)
}

const projectContext = read(`${aiServiceDir}/ProjectAiContext.java`)
assert.match(projectContext, /class ProjectAiContext/, 'ProjectAiContext should define the context data object')
assert.match(projectContext, /List<ProjectRole>\s+roles/, 'ProjectAiContext should expose roles')
assert.match(projectContext, /List<ProjectMember>\s+members/, 'ProjectAiContext should expose members')
assert.match(projectContext, /List<ProjectSection>\s+sections/, 'ProjectAiContext should expose sections')
assert.match(projectContext, /List<Task>\s+tasks/, 'ProjectAiContext should expose tasks')
assert.match(projectContext, /Map<Long, ProjectRole>\s+rolesById/, 'ProjectAiContext should expose role lookup')

const textSupport = read(`${aiServiceDir}/AiTextSupport.java`)
assert.match(textSupport, /class AiTextSupport/, 'AiTextSupport should define shared text helpers')
assert.match(textSupport, /firstNonBlank\(String\.\.\. values\)/, 'AiTextSupport should keep firstNonBlank helper')
assert.match(textSupport, /nullSafe\(String value\)/, 'AiTextSupport should keep nullSafe helper')
assert.match(textSupport, /currentPeriod\(\)/, 'AiTextSupport should keep currentPeriod helper')
assert.match(textSupport, /truncate\(String value, int maxLength\)/, 'AiTextSupport should keep truncate helper')

const contextBuilder = read(`${aiServiceDir}/AiContextBuilder.java`)
assert.match(contextBuilder, /class AiContextBuilder/, 'AiContextBuilder should define the context builder')
assert.match(contextBuilder, /ProjectAiContext build\(Project project\)/, 'AiContextBuilder should build context from project')

const recordService = read(`${aiServiceDir}/AiRecordService.java`)
assert.match(recordService, /class AiRecordService/, 'AiRecordService should exist')
assert.match(recordService, /List<AiRecordVO> records\(Long projectId\)/, 'AiRecordService should read records')
assert.match(recordService, /AiRecord saveGeneratedRecord/, 'AiRecordService should centralize generated record persistence')

const projectService = read(`${aiServiceDir}/ProjectAiService.java`)
assert.match(projectService, /class ProjectAiService/, 'ProjectAiService should exist')
assert.match(projectService, /weeklyReport\(Long projectId\)/, 'ProjectAiService should handle weekly report')
assert.match(projectService, /riskAnalysis\(Long projectId\)/, 'ProjectAiService should handle risk analysis')
assert.match(projectService, /documentCheck\(Long projectId\)/, 'ProjectAiService should handle document check')
assert.match(projectService, /summaryReport\(Long projectId\)/, 'ProjectAiService should handle summary report')
assert.match(
  projectService,
  /result\s*=\s*aiClient\s*\.\s*chat\s*\(\s*prompt\s*\)\s*;\s*if\s*\(\s*StringUtils\s*\.\s*hasText\s*\(\s*result\s*\)\s*\)\s*\{\s*source\s*=\s*AiRecord\s*\.\s*SOURCE_ZHIPU_GLM\s*;[^{}]*\}/s,
  'ProjectAiService should preserve the remote source for a non-blank AI result'
)
assert.match(
  projectService,
  /result\s*=\s*aiClient\s*\.\s*chat\s*\(\s*prompt\s*\)\s*;\s*if\s*\(\s*StringUtils\s*\.\s*hasText\s*\(\s*result\s*\)\s*\)\s*\{\s*source\s*=\s*AiRecord\s*\.\s*SOURCE_ZHIPU_GLM\s*;[^{}]*\}\s*else\s*\{\s*result\s*=\s*fallback\s*\(\s*project\s*,\s*context\s*,\s*type\s*\)\s*;\s*source\s*=\s*AiRecord\s*\.\s*SOURCE_LOCAL_FALLBACK\s*;\s*modelName\s*=\s*"local-fallback"\s*;\s*\}/s,
  'ProjectAiService should use the local fallback for a blank AI result'
)

const sectionService = read(`${aiServiceDir}/SectionAgentService.java`)
assert.match(sectionService, /class SectionAgentService/, 'SectionAgentService should exist')
assert.match(sectionService, /generateSectionContent\(Long projectId, Long sectionId\)/, 'SectionAgentService should generate section content')
assert.match(sectionService, /chatSectionContent\(Long projectId, Long sectionId, List<Map<String, String>> messages\)/, 'SectionAgentService should chat for section content')
assert.match(
  sectionService,
  /resultText\s*=\s*aiClient\s*\.\s*chat\s*\(\s*prompt\s*\.\s*toString\s*\(\s*\)\s*\)\s*;\s*if\s*\(\s*StringUtils\s*\.\s*hasText\s*\(\s*resultText\s*\)\s*\)\s*\{\s*source\s*=\s*AiRecord\s*\.\s*SOURCE_ZHIPU_GLM\s*;[^{}]*\}/s,
  'SectionAgentService should preserve the remote source for non-blank generated section content'
)
assert.match(
  sectionService,
  /resultText\s*=\s*aiClient\s*\.\s*chat\s*\(\s*prompt\s*\.\s*toString\s*\(\s*\)\s*\)\s*;\s*if\s*\(\s*StringUtils\s*\.\s*hasText\s*\(\s*resultText\s*\)\s*\)\s*\{\s*source\s*=\s*AiRecord\s*\.\s*SOURCE_ZHIPU_GLM\s*;[^{}]*\}\s*else\s*\{\s*resultText\s*=\s*getLocalFallbackForSection\s*\(\s*code\s*,\s*project\s*\.\s*getProjectName\s*\(\s*\)\s*,\s*roleName\s*\)\s*;\s*source\s*=\s*AiRecord\s*\.\s*SOURCE_LOCAL_FALLBACK\s*;\s*modelName\s*=\s*"local-fallback"\s*;\s*\}/s,
  'SectionAgentService should use the local fallback for blank generated section content'
)
assert.match(
  sectionService,
  /rawResult\s*=\s*aiClient\s*\.\s*chat\s*\(\s*apiMessages\s*\)\s*;\s*if\s*\(\s*StringUtils\s*\.\s*hasText\s*\(\s*rawResult\s*\)\s*\)\s*\{\s*source\s*=\s*AiRecord\s*\.\s*SOURCE_ZHIPU_GLM\s*;[^{}]*\}/s,
  'SectionAgentService should preserve the remote source for a non-blank chat result'
)
assert.match(
  sectionService,
  /rawResult\s*=\s*aiClient\s*\.\s*chat\s*\(\s*apiMessages\s*\)\s*;\s*if\s*\(\s*StringUtils\s*\.\s*hasText\s*\(\s*rawResult\s*\)\s*\)\s*\{\s*source\s*=\s*AiRecord\s*\.\s*SOURCE_ZHIPU_GLM\s*;[^{}]*\}\s*else\s*\{\s*rawResult\s*=\s*getLocalFallbackChatResponse\s*\(\s*code\s*,\s*project\s*\.\s*getProjectName\s*\(\s*\)\s*,\s*roleName\s*,\s*messages\s*\)\s*;\s*source\s*=\s*AiRecord\s*\.\s*SOURCE_LOCAL_FALLBACK\s*;\s*modelName\s*=\s*"local-fallback"\s*;\s*\}/s,
  'SectionAgentService should use the local fallback for a blank chat result'
)

const progressService = read(`${aiServiceDir}/ProgressExtractionAiService.java`)
assert.match(progressService, /class ProgressExtractionAiService/, 'ProgressExtractionAiService should exist')
assert.match(progressService, /extractProgress\(Long projectId, String reportText, List<Map<String, Object>> tasks\)/, 'ProgressExtractionAiService should extract progress')

console.log('ai service modularization checks passed')

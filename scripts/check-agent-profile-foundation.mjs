import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('..', import.meta.url))
const absolutePath = (path) => resolve(root, path)
const exists = (path) => existsSync(absolutePath(path))
const read = (path) => readFileSync(absolutePath(path), 'utf8')
const occurrences = (source, pattern) => [...source.matchAll(pattern)].length

const boundedSource = (source, startMarker, endMarker, label) => {
  const start = source.indexOf(startMarker)
  const end = source.indexOf(endMarker, start + startMarker.length)
  assert.ok(start >= 0, `${label} start marker should exist`)
  assert.ok(end > start, `${label} end marker should follow its start marker`)
  return source.slice(start, end)
}

const assertOrdered = (source, contracts, label) => {
  let offset = 0
  for (const [description, pattern] of contracts) {
    const match = pattern.exec(source.slice(offset))
    assert.ok(match, `${label} should contain ${description} in the required order`)
    offset += match.index + match[0].length
  }
}

const sectionProfileFlowPatterns = {
  resolve: /EffectiveAgentProfile\s+profile\s*=\s*agentProfileService\s*\.\s*resolveEffective\s*\(\s*projectId\s*,\s*section\s*\.\s*getOwnerRoleId\s*\(\s*\)\s*\)\s*;/,
  disabledCheck: /if\s*\(\s*!\s*profile\s*\.\s*isEnabled\s*\(\s*\)\s*\)/,
  disabledThrow: /throw\s+new\s+BizException\s*\(\s*409\s*,\s*"该角色智能体已停用"\s*\)\s*;/,
  disabledBlock: /if\s*\(\s*!\s*profile\s*\.\s*isEnabled\s*\(\s*\)\s*\)\s*\{\s*throw\s+new\s+BizException\s*\(\s*409\s*,\s*"该角色智能体已停用"\s*\)\s*;\s*\}/,
  context: /String\s+context\s*=\s*sectionAgentContextBuilder\s*\.\s*build\s*\(\s*project\s*,\s*section\s*,\s*profile\s*\)\s*;/,
  roleName: /String\s+roleName\s*=\s*StringUtils\s*\.\s*hasText\s*\(\s*profile\s*\.\s*getRoleName\s*\(\s*\)\s*\)\s*\?\s*profile\s*\.\s*getRoleName\s*\(\s*\)\s*:\s*ownerRole\s*\.\s*getRoleName\s*\(\s*\)\s*;/
}

const generationComposerPattern = /agentPromptComposer\s*\.\s*composeGeneration\s*\(\s*project\s*,\s*section\s*,\s*profile\s*,\s*context\s*\)/
const chatComposerPattern = /agentPromptComposer\s*\.\s*composeChatSystem\s*\(\s*project\s*,\s*section\s*,\s*profile\s*,\s*context\s*\)/
const generationAuditPattern = /buildAuditPrompt\s*\(\s*section\s*,\s*profile\s*,\s*0\s*\)/
const chatAuditPattern = /buildAuditPrompt\s*\(\s*section\s*,\s*profile\s*,\s*messages\s*!=\s*null\s*\?\s*messages\s*\.\s*size\s*\(\s*\)\s*:\s*0\s*\)/

const assertSectionProfileFlow = (methodSource, composerPattern, label) => {
  assertOrdered(methodSource, [
    ['resolveEffective(projectId, section.getOwnerRoleId())', sectionProfileFlowPatterns.resolve],
    ['if (!profile.isEnabled())', sectionProfileFlowPatterns.disabledCheck],
    ['the exact disabled-profile BizException', sectionProfileFlowPatterns.disabledThrow],
    ['sectionAgentContextBuilder.build(project, section, profile)', sectionProfileFlowPatterns.context],
    ['the profile-aware prompt composer call', composerPattern]
  ], label)
  assert.match(
    methodSource,
    sectionProfileFlowPatterns.disabledBlock,
    `${label} should keep the exact disabled check and throw in one block`
  )
  assert.match(
    methodSource,
    sectionProfileFlowPatterns.roleName,
    `${label} should prefer profile.getRoleName() before the owner-role fallback`
  )
}

const assertGenerationCompatibility = (methodSource) => {
  assert.match(
    methodSource,
    /^public\s+Map\s*<\s*String\s*,\s*String\s*>\s+generateSectionContent\s*\(\s*Long\s+projectId\s*,\s*Long\s+sectionId\s*\)\s*\{/,
    'generation should keep its exact public signature'
  )
  assertSectionProfileFlow(methodSource, generationComposerPattern, 'generation')
  assert.match(
    methodSource,
    /response\s*\.\s*put\s*\(\s*"title"\s*,/,
    'generation should keep the title response key'
  )
  assert.match(
    methodSource,
    /response\s*\.\s*put\s*\(\s*"body"\s*,/,
    'generation should keep the body response key'
  )
  assert.match(methodSource, generationAuditPattern, 'generation should persist only safe audit metadata')
  assert.doesNotMatch(
    methodSource,
    /saveGeneratedRecord\s*\([\s\S]*?prompt\s*\.\s*toString\s*\(\s*\)/,
    'generation should not persist the composed prompt'
  )
}

const assertChatCompatibility = (methodSource) => {
  assert.match(
    methodSource,
    /^public\s+Map\s*<\s*String\s*,\s*String\s*>\s+chatSectionContent\s*\(\s*Long\s+projectId\s*,\s*Long\s+sectionId\s*,\s*List\s*<\s*Map\s*<\s*String\s*,\s*String\s*>\s*>\s+messages\s*\)\s*\{/,
    'chat should keep its exact public signature'
  )
  assertSectionProfileFlow(methodSource, chatComposerPattern, 'chat')
  assert.match(
    methodSource,
    /if\s*\(\s*msg\s*==\s*null\s*\)\s*\{\s*continue\s*;\s*\}/,
    'chat should skip null client message maps'
  )
  assert.match(
    methodSource,
    /String\s+role\s*=\s*normalizeClientRole\s*\(\s*msg\s*\.\s*get\s*\(\s*"role"\s*\)\s*\)\s*;/,
    'chat should normalize and allowlist client roles'
  )
  assert.match(
    methodSource,
    /if\s*\(\s*role\s*!=\s*null\s*&&\s*StringUtils\s*\.\s*hasText\s*\(\s*content\s*\)\s*\)/,
    'chat should require an allowed role and non-blank content'
  )
  assertOrdered(methodSource, [
    ['the ```json opening-fence check', /cleanJson\s*\.\s*startsWith\s*\(\s*"```json"\s*\)/],
    ['the ```json opening-fence removal', /cleanJson\s*=\s*cleanJson\s*\.\s*substring\s*\(\s*7\s*\)\s*\.\s*trim\s*\(\s*\)\s*;/],
    ['the generic ``` opening-fence check', /cleanJson\s*\.\s*startsWith\s*\(\s*"```"\s*\)/],
    ['the generic ``` opening-fence removal', /cleanJson\s*=\s*cleanJson\s*\.\s*substring\s*\(\s*3\s*\)\s*\.\s*trim\s*\(\s*\)\s*;/],
    ['the closing ``` fence check', /cleanJson\s*\.\s*endsWith\s*\(\s*"```"\s*\)/],
    ['the closing ``` fence removal', /cleanJson\s*=\s*cleanJson\s*\.\s*substring\s*\(\s*0\s*,\s*cleanJson\s*\.\s*length\s*\(\s*\)\s*-\s*3\s*\)\s*\.\s*trim\s*\(\s*\)\s*;/]
  ], 'chat code-fence cleanup')
  assert.match(
    methodSource,
    /return\s+parseChatResponse\s*\(\s*cleanJson\s*,\s*rawResult\s*,\s*section\s*\)\s*;/,
    'chat should delegate to the strict three-key parser helper'
  )
  assert.match(methodSource, chatAuditPattern, 'chat should persist only safe audit metadata')
  assert.doesNotMatch(
    methodSource,
    /saveGeneratedRecord\s*\([\s\S]*?systemPrompt\s*\.\s*toString\s*\(\s*\)/,
    'chat should not persist the composed system prompt'
  )
}

const assertFallbackChatCompatibility = (methodSource) => {
  for (const key of ['explanation', 'title', 'body']) {
    assert.match(
      methodSource,
      new RegExp(`map\\s*\\.\\s*put\\s*\\(\\s*"${key}"\\s*,`),
      `chat fallback should keep the ${key} response key`
    )
  }
  assert.match(
    methodSource,
    /Map\s*<\s*String\s*,\s*String\s*>\s+message\s*=\s*messages\s*\.\s*get\s*\(\s*i\s*\)\s*;/,
    'chat fallback should inspect the last messages through a null-safe local variable'
  )
  assert.match(
    methodSource,
    /if\s*\(\s*message\s*==\s*null\s*\)\s*\{\s*continue\s*;\s*\}/,
    'chat fallback should skip null message maps'
  )
  assert.match(
    methodSource,
    /"user"\s*\.\s*equals\s*\(\s*normalizeClientRole\s*\(\s*message\s*\.\s*get\s*\(\s*"role"\s*\)\s*\)\s*\)/,
    'chat fallback should accept only normalized user messages'
  )
  assert.match(
    methodSource,
    /objectMapper\s*\.\s*writeValueAsString\s*\(\s*map\s*\)/,
    'chat fallback should serialize with the injected ObjectMapper'
  )
  assert.match(
    methodSource,
    /catch\s*\(\s*Exception\s+\w+\s*\)\s*\{\s*return\s+SAFE_FALLBACK_CHAT_JSON\s*;\s*\}/,
    'chat fallback serialization failure should return fixed safe JSON'
  )
}

const assertStrictParserCompatibility = (methodSource) => {
  assert.match(
    methodSource,
    /^private\s+Map\s*<\s*String\s*,\s*String\s*>\s+parseChatResponse\s*\(/,
    'strict parser helper should keep its private typed signature'
  )
  assert.match(
    methodSource,
    /JsonNode\s+root\s*=\s*objectMapper\s*\.\s*readTree\s*\(\s*cleanJson\s*\)\s*;/,
    'strict parser should use the injected ObjectMapper readTree'
  )
  assert.match(methodSource, /!\s*root\s*\.\s*isObject\s*\(\s*\)/, 'strict parser should require an object')
  for (const key of ['explanation', 'title', 'body']) {
    assert.match(
      methodSource,
      new RegExp(`JsonNode\\s+${key}\\s*=\\s*root\\s*==\\s*null\\s*\\?\\s*null\\s*:\\s*root\\s*\\.\\s*get\\s*\\(\\s*"${key}"\\s*\\)\\s*;`),
      `strict parser should read the ${key} field`
    )
    assert.match(
      methodSource,
      new RegExp(`${key}\\s*==\\s*null\\s*\\|\\|\\s*!\\s*${key}\\s*\\.\\s*isTextual\\s*\\(\\s*\\)`),
      `strict parser should require textual ${key}`
    )
    assert.match(
      methodSource,
      new RegExp(`response\\s*\\.\\s*put\\s*\\(\\s*"${key}"\\s*,\\s*${key}\\s*\\.\\s*textValue\\s*\\(\\s*\\)\\s*\\)`),
      `strict parser should return textual ${key}`
    )
    assert.match(
      methodSource,
      new RegExp(`errMap\\s*\\.\\s*put\\s*\\(\\s*"${key}"\\s*,`),
      `chat parse failure should keep the ${key} response key`
    )
  }
  assert.equal(
    occurrences(methodSource, /response\s*\.\s*put\s*\(/g),
    3,
    'strict parser success should return exactly three keys'
  )
  assert.doesNotMatch(methodSource, /\.\s*readValue\s*\(/, 'strict parser should not deserialize an untyped map')
}

const assertAuditPromptCompatibility = (methodSource) => {
  const normalizedMethod = methodSource.trim()
  assert.match(
    normalizedMethod,
    /^private\s+String\s+buildAuditPrompt\s*\(\s*ProjectSection\s+section\s*,\s*EffectiveAgentProfile\s+profile\s*,\s*int\s+chatMessageCount\s*\)\s*\{\s*return\s+"sectionId="\s*\+\s*section\s*\.\s*getId\s*\(\s*\)\s*\+\s*";profileSource="\s*\+\s*profile\s*\.\s*getSource\s*\(\s*\)\s*\+\s*";roleCode="\s*\+\s*profile\s*\.\s*getRoleCode\s*\(\s*\)\s*\+\s*";chatMessageCount="\s*\+\s*chatMessageCount\s*;\s*\}$/,
    'audit prompt helper should contain only the whitelisted metadata expression'
  )
  assert.deepEqual(
    [...normalizedMethod.matchAll(/"([^"]*)"/g)].map((match) => match[1]),
    ['sectionId=', ';profileSource=', ';roleCode=', ';chatMessageCount='],
    'audit prompt helper should contain exactly the fixed safe metadata labels'
  )
}

const assertClientRoleNormalizerCompatibility = (methodSource) => {
  const normalizedMethod = methodSource.trim()
  assert.match(
    normalizedMethod,
    /^private\s+String\s+normalizeClientRole\s*\(\s*String\s+role\s*\)\s*\{\s*if\s*\(\s*!\s*StringUtils\s*\.\s*hasText\s*\(\s*role\s*\)\s*\)\s*\{\s*return\s+null\s*;\s*\}\s*String\s+normalized\s*=\s*role\s*\.\s*trim\s*\(\s*\)\s*\.\s*toLowerCase\s*\(\s*Locale\s*\.\s*ROOT\s*\)\s*;\s*return\s+"user"\s*\.\s*equals\s*\(\s*normalized\s*\)\s*\|\|\s*"assistant"\s*\.\s*equals\s*\(\s*normalized\s*\)\s*\?\s*normalized\s*:\s*null\s*;\s*\}$/,
    'client role normalizer should be the complete user/assistant-only policy'
  )
  assert.deepEqual(
    [...normalizedMethod.matchAll(/"([^"]*)"/g)].map((match) => match[1]),
    ['user', 'assistant'],
    'client role normalizer should contain exactly the user and assistant role literals'
  )
}

const removeFirstMatch = (source, pattern, label) => {
  const match = pattern.exec(source)
  assert.ok(match, `${label} mutation target should exist`)
  return source.slice(0, match.index) + source.slice(match.index + match[0].length)
}

const replaceFirstMatch = (source, pattern, replacement, label) => {
  const match = pattern.exec(source)
  assert.ok(match, `${label} mutation target should exist`)
  return source.slice(0, match.index) + replacement + source.slice(match.index + match[0].length)
}

const swapMatchedFragments = (source, firstPattern, secondPattern, label) => {
  const first = firstPattern.exec(source)
  const second = secondPattern.exec(source)
  assert.ok(first, `${label} first mutation target should exist`)
  assert.ok(second, `${label} second mutation target should exist`)
  assert.ok(first.index < second.index, `${label} mutation targets should start in production order`)
  return source.slice(0, first.index)
    + second[0]
    + source.slice(first.index + first[0].length, second.index)
    + first[0]
    + source.slice(second.index + second[0].length)
}

const assertMutationRejected = (label, contract, mutatedSource) => {
  assert.throws(
    () => contract(mutatedSource),
    (error) => error instanceof assert.AssertionError,
    `${label} mutation should be rejected`
  )
}

const runSectionAgentMutationProbes = ({
  generationMethod,
  chatMethod,
  fallbackChatMethod,
  strictParserMethod,
  auditPromptMethod,
  clientRoleNormalizerMethod
}) => {
  for (const [label, methodSource, contract] of [
    ['generation reordered profile flow', generationMethod, assertGenerationCompatibility],
    ['chat reordered profile flow', chatMethod, assertChatCompatibility]
  ]) {
    const reordered = swapMatchedFragments(
      methodSource,
      sectionProfileFlowPatterns.disabledBlock,
      sectionProfileFlowPatterns.context,
      label
    )
    assertMutationRejected(label, contract, reordered)
  }

  for (const [label, methodSource, contract] of [
    ['generation missing profile resolution', generationMethod, assertGenerationCompatibility],
    ['chat missing profile resolution', chatMethod, assertChatCompatibility]
  ]) {
    assertMutationRejected(
      label,
      contract,
      removeFirstMatch(methodSource, sectionProfileFlowPatterns.resolve, label)
    )
  }

  assertMutationRejected(
    'generation missing title response key',
    assertGenerationCompatibility,
    removeFirstMatch(
      generationMethod,
      /response\s*\.\s*put\s*\(\s*"title"[^;]*;/,
      'generation title response key'
    )
  )
  assertMutationRejected(
    'generation persists composed prompt directly',
    assertGenerationCompatibility,
    replaceFirstMatch(
      generationMethod,
      generationAuditPattern,
      'prompt.toString()',
      'generation safe audit prompt'
    )
  )
  assertMutationRejected(
    'chat missing null message guard',
    assertChatCompatibility,
    removeFirstMatch(
      chatMethod,
      /if\s*\(\s*msg\s*==\s*null\s*\)\s*\{\s*continue\s*;\s*\}/,
      'chat null message guard'
    )
  )
  assertMutationRejected(
    'chat missing allowed-role filter',
    assertChatCompatibility,
    removeFirstMatch(
      chatMethod,
      /String\s+role\s*=\s*normalizeClientRole\s*\(\s*msg\s*\.\s*get\s*\(\s*"role"\s*\)\s*\)\s*;/,
      'chat allowed-role filter'
    )
  )
  assertMutationRejected(
    'chat missing ```json cleanup',
    assertChatCompatibility,
    removeFirstMatch(
      chatMethod,
      /cleanJson\s*\.\s*startsWith\s*\(\s*"```json"\s*\)/,
      'chat ```json cleanup'
    )
  )
  assertMutationRejected(
    'chat missing strict parser delegation',
    assertChatCompatibility,
    removeFirstMatch(
      chatMethod,
      /return\s+parseChatResponse\s*\(\s*cleanJson\s*,\s*rawResult\s*,\s*section\s*\)\s*;/,
      'chat strict parser delegation'
    )
  )
  assertMutationRejected(
    'chat persists composed system prompt directly',
    assertChatCompatibility,
    replaceFirstMatch(
      chatMethod,
      chatAuditPattern,
      'systemPrompt.toString()',
      'chat safe audit prompt'
    )
  )

  for (const [label, methodSource, contract] of [
    ['generation missing profile-role fallback', generationMethod, assertGenerationCompatibility],
    ['chat missing profile-role fallback', chatMethod, assertChatCompatibility]
  ]) {
    assertMutationRejected(
      label,
      contract,
      removeFirstMatch(methodSource, sectionProfileFlowPatterns.roleName, label)
    )
  }

  assertMutationRejected(
    'chat parse failure missing explanation response key',
    assertStrictParserCompatibility,
    removeFirstMatch(
      strictParserMethod,
      /errMap\s*\.\s*put\s*\(\s*"explanation"[^;]*;/,
      'chat parse-failure explanation key'
    )
  )
  assertMutationRejected(
    'strict parser missing textual explanation validation',
    assertStrictParserCompatibility,
    removeFirstMatch(
      strictParserMethod,
      /explanation\s*==\s*null\s*\|\|\s*!\s*explanation\s*\.\s*isTextual\s*\(\s*\)/,
      'strict parser explanation validation'
    )
  )
  assertMutationRejected(
    'chat fallback missing body response key',
    assertFallbackChatCompatibility,
    removeFirstMatch(
      fallbackChatMethod,
      /map\s*\.\s*put\s*\(\s*"body"[^;]*;/,
      'chat fallback body key'
    )
  )
  assertMutationRejected(
    'chat fallback missing null message guard',
    assertFallbackChatCompatibility,
    removeFirstMatch(
      fallbackChatMethod,
      /if\s*\(\s*message\s*==\s*null\s*\)\s*\{\s*continue\s*;\s*\}/,
      'chat fallback null message guard'
    )
  )
  assertMutationRejected(
    'audit prompt missing profile source',
    assertAuditPromptCompatibility,
    removeFirstMatch(auditPromptMethod, /\.\s*getSource\s*\(\s*\)/, 'audit profile source')
  )
  assertMutationRejected(
    'client role normalizer allows no assistant role',
    assertClientRoleNormalizerCompatibility,
    removeFirstMatch(
      clientRoleNormalizerMethod,
      /\|\|\s*"assistant"\s*\.\s*equals\s*\(\s*normalized\s*\)/,
      'assistant role allowance'
    )
  )
  assertMutationRejected(
    'client role normalizer accepts system role',
    assertClientRoleNormalizerCompatibility,
    replaceFirstMatch(
      clientRoleNormalizerMethod,
      /"assistant"\s*\.\s*equals\s*\(\s*normalized\s*\)/,
      '"assistant".equals(normalized) || "system".equals(normalized)',
      'system role branch'
    )
  )
  assertMutationRejected(
    'audit prompt appends profile toString',
    assertAuditPromptCompatibility,
    replaceFirstMatch(
      auditPromptMethod,
      /\+\s*";chatMessageCount="\s*\+\s*chatMessageCount/,
      '+ ";chatMessageCount=" + chatMessageCount + profile.toString()',
      'audit profile toString concatenation'
    )
  )
}

const toSearchableJavaSource = (source) => {
  source = source.replace(/\\u+([0-9a-fA-F]{4})/g, (_, hex) => String.fromCharCode(Number.parseInt(hex, 16)))

  let result = ''
  let state = 'code'

  for (let index = 0; index < source.length;) {
    const current = source[index]
    const next = source[index + 1]
    const nextThree = source.slice(index, index + 3)

    if (state === 'code') {
      if (nextThree === '"""') {
        result += nextThree
        state = 'text-block'
        index += 3
      } else if (current === '"') {
        result += current
        state = 'string'
        index += 1
      } else if (current === "'") {
        result += current
        state = 'character'
        index += 1
      } else if (current === '/' && next === '/') {
        result += '  '
        state = 'line-comment'
        index += 2
      } else if (current === '/' && next === '*') {
        result += '  '
        state = 'block-comment'
        index += 2
      } else {
        result += current
        index += 1
      }
    } else if (state === 'line-comment') {
      if (current === '\n' || current === '\r') {
        result += current
        state = 'code'
      } else {
        result += ' '
      }
      index += 1
    } else if (state === 'block-comment') {
      if (current === '*' && next === '/') {
        result += '  '
        state = 'code'
        index += 2
      } else {
        result += current === '\n' || current === '\r' ? current : ' '
        index += 1
      }
    } else if (state === 'text-block') {
      if (nextThree === '"""') {
        result += nextThree
        state = 'code'
        index += 3
      } else if (current === '\\' && next !== undefined) {
        result += next === '\n' || next === '\r' ? ` ${next}` : '  '
        index += 2
      } else {
        result += current === '\n' || current === '\r' ? current : ' '
        index += 1
      }
    } else if (current === '\\' && next !== undefined) {
      result += current + next
      index += 2
    } else {
      result += current
      if ((state === 'string' && current === '"') || (state === 'character' && current === "'")) {
        state = 'code'
      }
      index += 1
    }
  }

  return result
}

const javaRoot = 'teamflow-ai-backend/src/main/java/com/example/teamflow'
const requiredJavaFiles = [
  'entity/AgentProfile.java',
  'mapper/AgentProfileMapper.java',
  'dto/AgentProfileUpdateDTO.java',
  'vo/AgentProfileVO.java',
  'service/ai/EffectiveAgentProfile.java',
  'service/ai/AgentProfileService.java'
]

for (const file of requiredJavaFiles) {
  assert.ok(exists(`${javaRoot}/${file}`), `${file} should exist`)
}

const initSqlPath = 'database/teamflow_ai.sql'
const migrationSqlPath = 'database/migrations/2026-06-22-agent-profile.sql'
const profileControllerPath = `${javaRoot}/controller/AgentProfileController.java`
const agentPromptComposerPath = `${javaRoot}/service/ai/AgentPromptComposer.java`
const sectionAgentContextBuilderPath = `${javaRoot}/service/ai/SectionAgentContextBuilder.java`
const sectionAgentServicePath = `${javaRoot}/service/ai/SectionAgentService.java`
const aiControllerPath = `${javaRoot}/controller/AiController.java`

assert.ok(exists(initSqlPath), `${initSqlPath} should exist`)
assert.ok(exists(migrationSqlPath), 'additive migration should exist')
assert.ok(exists(sectionAgentServicePath), 'SectionAgentService.java should exist')
assert.ok(exists(aiControllerPath), 'AiController.java should exist')

const initSql = read(initSqlPath)
const migrationSql = read(migrationSqlPath)
const profileService = toSearchableJavaSource(read(`${javaRoot}/service/ai/AgentProfileService.java`))

assert.match(
  initSql,
  /CREATE\s+TABLE\s+agent_profile(?=\s|\()/,
  'fresh-install schema should create agent_profile'
)
assert.match(
  migrationSql,
  /CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+agent_profile(?=\s|\()/,
  'additive migration should create agent_profile when absent'
)

const roleCodes = [
  'PROJECT_MANAGER',
  'PRODUCT_MANAGER',
  'TECHNICAL_DIRECTOR',
  'FRONTEND_DEV',
  'BACKEND_DEV',
  'QA'
]

for (const roleCode of roleCodes) {
  assert.match(initSql, new RegExp(`'${roleCode}'`), `${roleCode} should be seeded`)
  assert.match(migrationSql, new RegExp(`'${roleCode}'`), `${roleCode} should be seeded in migration`)
}

assert.match(
  profileService,
  /resolveEffective\(Long projectId, Long projectRoleId\)/,
  'AgentProfileService should expose resolveEffective(Long projectId, Long projectRoleId)'
)
assert.match(
  profileService,
  /(?<![A-Za-z0-9_$])SOURCE_PROJECT(?![A-Za-z0-9_$])/,
  'AgentProfileService should define the project source'
)
assert.match(
  profileService,
  /(?<![A-Za-z0-9_$])SOURCE_GLOBAL(?![A-Za-z0-9_$])/,
  'AgentProfileService should define the global source'
)
assert.match(
  profileService,
  /(?<![A-Za-z0-9_$])SOURCE_SYNTHETIC(?![A-Za-z0-9_$])/,
  'AgentProfileService should define the synthetic source'
)
assert.match(
  profileService,
  /requireProjectMember\(projectId\)/,
  'AgentProfileService should require project membership for reads'
)
assert.match(
  profileService,
  /requireProjectManage\(projectId\)/,
  'AgentProfileService should require project management for writes'
)

for (const setter of [
  'setSystemPrompt',
  'setTaskPromptTemplate',
  'setMemoryPolicy',
  'setMultimodalConfig'
]) {
  assert.match(
    profileService,
    new RegExp(`${setter}\\(null\\)`),
    `AgentProfileService should redact restricted fields with ${setter}(null)`
  )
}

assert.ok(exists(profileControllerPath), 'AgentProfileController.java should exist')

const profileController = toSearchableJavaSource(read(profileControllerPath))

assert.match(
  profileController,
  /@RestController\s+(?:@[A-Za-z_$][\w$]*(?:\s*\([^)]*\))?\s+)*public\s+class\s+AgentProfileController\b/,
  'AgentProfileController should be annotated with @RestController'
)

const profileEndpointContracts = [
  {
    mapping: 'GetMapping',
    path: '/api/projects/{projectId}/agent-profiles',
    method: 'list',
    resultType: 'Result<List<AgentProfileVO>>',
    parameters: '@PathVariable\\s+Long\\s+projectId',
    serviceMethod: 'listVisible',
    arguments: 'projectId'
  },
  {
    mapping: 'GetMapping',
    path: '/api/projects/{projectId}/roles/{roleId}/agent-profile',
    method: 'detail',
    resultType: 'Result<AgentProfileVO>',
    parameters: '@PathVariable\\s+Long\\s+projectId\\s*,\\s*@PathVariable\\s+Long\\s+roleId',
    serviceMethod: 'getVisible',
    arguments: 'projectId\\s*,\\s*roleId'
  },
  {
    mapping: 'PutMapping',
    path: '/api/projects/{projectId}/roles/{roleId}/agent-profile',
    method: 'save',
    resultType: 'Result<AgentProfileVO>',
    parameters: '@PathVariable\\s+Long\\s+projectId\\s*,\\s*@PathVariable\\s+Long\\s+roleId\\s*,\\s*@Valid\\s+@RequestBody\\s+AgentProfileUpdateDTO\\s+dto',
    serviceMethod: 'saveProjectSnapshot',
    arguments: 'projectId\\s*,\\s*roleId\\s*,\\s*dto'
  },
  {
    mapping: 'DeleteMapping',
    path: '/api/projects/{projectId}/roles/{roleId}/agent-profile',
    method: 'reset',
    resultType: 'Result<AgentProfileVO>',
    parameters: '@PathVariable\\s+Long\\s+projectId\\s*,\\s*@PathVariable\\s+Long\\s+roleId',
    serviceMethod: 'resetProjectSnapshot',
    arguments: 'projectId\\s*,\\s*roleId'
  }
]

for (const contract of profileEndpointContracts) {
  const { mapping, path, method, resultType, parameters, serviceMethod, arguments: args } = contract
  const escapedPath = path.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const escapedResultType = resultType.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  assert.match(
    profileController,
    new RegExp(
      `@${mapping}\\s*\\(\\s*"${escapedPath}"\\s*\\)`
        + `\\s*public\\s+${escapedResultType}\\s+${method}\\s*\\(\\s*${parameters}\\s*\\)`
        + `\\s*\\{\\s*return\\s+Result\\.success\\s*\\(\\s*agentProfileService\\.${serviceMethod}`
        + `\\s*\\(\\s*${args}\\s*\\)\\s*\\)\\s*;\\s*\\}`,
      's'
    ),
    `AgentProfileController.${method} should preserve its exact mapping, signature, and delegation contract`
  )
}

assert.ok(exists(agentPromptComposerPath), 'AgentPromptComposer.java should exist')
assert.ok(exists(sectionAgentContextBuilderPath), 'SectionAgentContextBuilder.java should exist')

const sectionAgentService = toSearchableJavaSource(read(sectionAgentServicePath))
const aiController = toSearchableJavaSource(read(aiControllerPath))
const generationMethod = boundedSource(
  sectionAgentService,
  'public Map<String, String> generateSectionContent',
  'public Map<String, String> chatSectionContent',
  'SectionAgentService.generateSectionContent'
)
const chatMethod = boundedSource(
  sectionAgentService,
  'public Map<String, String> chatSectionContent',
  'private String getLocalFallbackChatResponse',
  'SectionAgentService.chatSectionContent'
)
const fallbackChatMethod = boundedSource(
  sectionAgentService,
  'private String getLocalFallbackChatResponse',
  'private Map<String, String> parseChatResponse',
  'SectionAgentService.getLocalFallbackChatResponse'
)
const strictParserMethod = boundedSource(
  sectionAgentService,
  'private Map<String, String> parseChatResponse',
  'private String getSectionDefaultTitle',
  'SectionAgentService.parseChatResponse'
)
const auditPromptMethod = boundedSource(
  sectionAgentService,
  'private String buildAuditPrompt',
  'private String normalizeClientRole',
  'SectionAgentService.buildAuditPrompt'
)
const clientRoleNormalizerMethod = boundedSource(
  sectionAgentService,
  'private String normalizeClientRole',
  'private String getLocalFallbackForSection',
  'SectionAgentService.normalizeClientRole'
)
const fallbackMethods = sectionAgentService.slice(
  sectionAgentService.indexOf('private String getLocalFallbackChatResponse')
)

const collaboratorFields = [
  ['AgentProfileService', 'agentProfileService'],
  ['SectionAgentContextBuilder', 'sectionAgentContextBuilder'],
  ['AgentPromptComposer', 'agentPromptComposer'],
  ['ObjectMapper', 'objectMapper']
]

for (const [type, field] of collaboratorFields) {
  const fieldPattern = new RegExp(`private\\s+final\\s+${type}\\s+${field}\\s*;`, 'g')
  assert.equal(
    occurrences(sectionAgentService, fieldPattern),
    1,
    `SectionAgentService should inject exactly one private final ${type} ${field} field`
  )
}

assertGenerationCompatibility(generationMethod)
assertChatCompatibility(chatMethod)
assertFallbackChatCompatibility(fallbackChatMethod)
assertStrictParserCompatibility(strictParserMethod)
assertAuditPromptCompatibility(auditPromptMethod)
assertClientRoleNormalizerCompatibility(clientRoleNormalizerMethod)
assert.doesNotMatch(
  sectionAgentService,
  /new\s+(?:com\s*\.\s*fasterxml\s*\.\s*jackson\s*\.\s*databind\s*\.\s*)?ObjectMapper\s*\(/,
  'SectionAgentService should use only the injected ObjectMapper'
)

for (const [method, label] of [[generationMethod, 'generation'], [chatMethod, 'chat']]) {
  assert.match(method, /"SECTION_GENERATE"/, `${label} should keep SECTION_GENERATE records`)
  assert.match(method, /AiRecord\s*\.\s*SOURCE_ZHIPU_GLM/, `${label} should keep the GLM source`)
  assert.match(method, /AiRecord\s*\.\s*SOURCE_LOCAL_FALLBACK/, `${label} should keep the local source`)
  assert.match(method, /"local-fallback"/, `${label} should keep the local-fallback model`)
}

assert.match(
  generationMethod,
  /if\s*\(\s*StringUtils\s*\.\s*hasText\s*\(\s*resultText\s*\)\s*\)[\s\S]*?else\s*\{[\s\S]*?getLocalFallbackForSection/,
  'generation should keep the blank-response fallback branch'
)
assert.match(
  generationMethod,
  /catch\s*\(\s*Exception\s+\w+\s*\)\s*\{[\s\S]*?getLocalFallbackForSection/,
  'generation should keep the exception fallback branch'
)
assert.match(
  chatMethod,
  /if\s*\(\s*StringUtils\s*\.\s*hasText\s*\(\s*rawResult\s*\)\s*\)[\s\S]*?else\s*\{[\s\S]*?getLocalFallbackChatResponse/,
  'chat should keep the blank-response fallback branch'
)
assert.match(
  chatMethod,
  /catch\s*\(\s*Exception\s+\w+\s*\)\s*\{[\s\S]*?getLocalFallbackChatResponse/,
  'chat should keep the exception fallback branch'
)

assert.doesNotMatch(
  sectionAgentService,
  /private\s+final\s+SectionContentMapper\s+\w+\s*;/,
  'SectionAgentService should not inject the legacy SectionContentMapper'
)
assert.doesNotMatch(generationMethod, /case\s+"REQUIREMENT"/, 'generation should not duplicate prompt switches')
assert.doesNotMatch(chatMethod, /case\s+"REQUIREMENT"/, 'chat should not duplicate prompt switches')
assert.equal(
  occurrences(sectionAgentService, /case\s+"REQUIREMENT"\s*:/g),
  1,
  'SectionAgentService should keep exactly one REQUIREMENT case for fallback document bodies'
)
assert.match(
  fallbackMethods,
  /getLocalFallbackForSection[\s\S]*case\s+"REQUIREMENT"\s*:/,
  'the remaining REQUIREMENT case should belong to fallback document generation'
)

for (const path of ['/sections/{sectionId}/generate', '/sections/{sectionId}/chat']) {
  const escapedPath = path.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  assert.match(
    aiController,
    new RegExp(`@PostMapping\\s*\\(\\s*"${escapedPath}"\\s*\\)`),
    `AiController should preserve ${path}`
  )
}

runSectionAgentMutationProbes({
  generationMethod,
  chatMethod,
  fallbackChatMethod,
  strictParserMethod,
  auditPromptMethod,
  clientRoleNormalizerMethod
})

console.log('agent profile foundation checks passed')

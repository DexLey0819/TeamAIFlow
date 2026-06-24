import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const helperPath = resolve(root, 'src/utils/authRedirect.js')

assert.ok(existsSync(helperPath), 'auth redirect helper should exist')

const { resolveLoginRedirect, toLoginWithRedirect } = await import(pathToFileURL(helperPath))

assert.deepEqual(toLoginWithRedirect('/join/TF-DEMO-001'), {
  path: '/login',
  query: { redirect: '/join/TF-DEMO-001' }
})
assert.equal(resolveLoginRedirect({ query: { redirect: '/join/TF-DEMO-001' } }), '/join/TF-DEMO-001')
assert.equal(resolveLoginRedirect({ query: { redirect: 'https://example.com' } }), '/dashboard')
assert.equal(resolveLoginRedirect({ query: { redirect: '/login' } }), '/dashboard')

const joinSource = readFileSync(resolve(root, 'src/views/JoinProject.vue'), 'utf8')
const loginSource = readFileSync(resolve(root, 'src/views/Login.vue'), 'utf8')
const registerSource = readFileSync(resolve(root, 'src/views/Register.vue'), 'utf8')
const routerSource = readFileSync(resolve(root, 'src/router/index.js'), 'utf8')
const readmeSource = readFileSync(resolve(root, '../README.md'), 'utf8')
const databaseSource = readFileSync(resolve(root, '../database/teamflow_ai.sql'), 'utf8')

assert.match(joinSource, /toLoginWithRedirect\(route\.fullPath\)/, 'join page should send users to login with redirect')
assert.match(joinSource, /await userStore\.loadMe\(\)/, 'join page should load current user before prefilling applicant information')
assert.match(loginSource, /resolveLoginRedirect\(route\)/, 'login page should return to requested page after login')
assert.match(loginSource, /isJoinRedirect/, 'login page should use a clean applicant account for join-link demos')
assert.match(registerSource, /toLoginWithRedirect\(redirectTarget\.value\)/, 'register page should preserve the requested page')
assert.match(routerSource, /toLoginWithRedirect\(to\.fullPath\)/, 'router guard should preserve private route redirects')
assert.match(databaseSource, /'applicant'/, 'database seed should include a clean applicant account')
assert.doesNotMatch(databaseSource, /project_join_apply[\s\S]*\b9,\s*1,\s*9\b/, 'clean applicant should not have a pending join application')
assert.doesNotMatch(databaseSource, /project_member[\s\S]*\b9,\s*1,\s*9\b/, 'clean applicant should not already be a project member')
assert.match(readmeSource, /\| applicant \| USER \| 新申请演示账号 \|/, 'README should document the clean applicant account as USER')
assert.doesNotMatch(readmeSource, /\| applicant \| STUDENT \|/, 'README should not refer to the removed STUDENT role')
assert.match(
  databaseSource,
  /\(9,\s*'applicant'[\s\S]*?'USER',\s*1\)/,
  'database seed should create the clean applicant account as USER'
)
assert.doesNotMatch(databaseSource, /\bSTUDENT\b/, 'database seed should not use the removed STUDENT role')

console.log('join redirect checks passed')

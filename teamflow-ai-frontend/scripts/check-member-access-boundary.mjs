import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const projectMembersSource = readFileSync(resolve(root, 'src/views/ProjectMembers.vue'), 'utf8')
const joinApplyServiceSource = readFileSync(
  resolve(root, '../teamflow-ai-backend/src/main/java/com/example/teamflow/service/JoinApplyService.java'),
  'utf8'
)
const memberServiceSource = readFileSync(
  resolve(root, '../teamflow-ai-backend/src/main/java/com/example/teamflow/service/MemberService.java'),
  'utf8'
)

assert.match(
  joinApplyServiceSource,
  /public List<JoinApplyVO> list\(Long projectId\) \{\s*permissionService\.requireProjectManage\(projectId\)/,
  'join application list must remain manager/admin only'
)
assert.match(
  memberServiceSource,
  /public List<ProjectMemberVO> list\(Long projectId\) \{\s*projectService\.getProject\(projectId\)/,
  'project members should remain visible to users who can access the project'
)
assert.match(
  projectMembersSource,
  /canReviewApplications/,
  'member page should explicitly decide whether the current user can review applications'
)
assert.doesNotMatch(
  projectMembersSource,
  /Promise\.all\(\[\s*listMembers\(projectId\),\s*joinApplies\(projectId\),/,
  'member list loading must not be blocked by the manager-only join applications API'
)
assert.match(
  projectMembersSource,
  /v-if="canReviewApplications"/,
  'join applications panel should be hidden from ordinary project members'
)

console.log('member access boundary checks passed')

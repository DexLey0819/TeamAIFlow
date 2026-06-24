import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const workspaceRoot = resolve(root, '..')

const profileDtoPath = resolve(
  workspaceRoot,
  'teamflow-ai-backend/src/main/java/com/example/teamflow/dto/ProfileUpdateDTO.java'
)
const profileViewPath = resolve(root, 'src/views/Profile.vue')

assert.ok(existsSync(profileDtoPath), 'backend should define ProfileUpdateDTO')
assert.ok(existsSync(profileViewPath), 'frontend should define Profile.vue')

const authController = readFileSync(
  resolve(workspaceRoot, 'teamflow-ai-backend/src/main/java/com/example/teamflow/controller/AuthController.java'),
  'utf8'
)
const authService = readFileSync(
  resolve(workspaceRoot, 'teamflow-ai-backend/src/main/java/com/example/teamflow/service/AuthService.java'),
  'utf8'
)
const authApi = readFileSync(resolve(root, 'src/api/auth.js'), 'utf8')
const userStore = readFileSync(resolve(root, 'src/stores/user.js'), 'utf8')
const router = readFileSync(resolve(root, 'src/router/index.js'), 'utf8')
const layout = readFileSync(resolve(root, 'src/views/Layout.vue'), 'utf8')
const profileView = readFileSync(profileViewPath, 'utf8')

assert.match(authController, /@PutMapping\("\/me"\)/, 'auth controller should expose PUT /api/auth/me')
assert.match(authController, /ProfileUpdateDTO/, 'auth controller should accept ProfileUpdateDTO')
assert.match(authService, /UserVO updateMe\(ProfileUpdateDTO dto\)/, 'auth service should update current user profile')
assert.match(authService, /setEmail\(dto\.getEmail\(\)\)/, 'auth service should update email')
assert.match(authService, /setPhone\(dto\.getPhone\(\)\)/, 'auth service should update phone')
assert.match(authApi, /updateMe/, 'auth API should export updateMe')
assert.match(userStore, /updateProfile/, 'user store should expose updateProfile action')
assert.match(router, /path: 'profile'/, 'router should register /profile inside Layout')
assert.match(layout, /index="\/profile"/, 'layout sidebar should link to profile page')
assert.match(profileView, /个人信息/, 'profile page should be titled personal information')
assert.match(profileView, /v-model="form\.email"/, 'profile page should edit email')
assert.match(profileView, /v-model="form\.phone"/, 'profile page should edit phone')
assert.match(profileView, /updateProfile/, 'profile page should save through user store')

console.log('profile feature checks passed')

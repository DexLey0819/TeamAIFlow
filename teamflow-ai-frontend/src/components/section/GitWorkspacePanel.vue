<template>
  <div class="git-workspace-panel" v-loading="loading">
    <!-- Case 1: GitHub Repository Not Configured -->
    <div v-if="!project.githubRepo" class="empty-repo-card">
      <div class="card-icon">🐙</div>
      <h2>未关联 GitHub 代码仓库</h2>
      <p class="subtitle">通过将此项目与 GitHub 仓库绑定，开发人员可以直接在看板中管理研发分支、追踪提交并触发 AI 自动代码审查。</p>
      
      <div v-if="isManager" class="repo-setup-form">
        <el-input 
          v-model="newRepo" 
          placeholder="例如: google/teamflow-ai" 
          class="repo-input"
        >
          <template #prepend>github.com/</template>
        </el-input>
        <el-button type="primary" class="setup-btn" @click="saveRepo">
          关联 GitHub 仓库
        </el-button>
      </div>
      <div v-else class="waiting-prompt">
        <el-alert
          title="等待项目经理关联 GitHub 仓库以开启 Git 研发面板。"
          type="info"
          show-icon
          :closable="false"
        />
      </div>
    </div>

    <!-- Case 2: GitHub Repository Configured -->
    <div v-else class="workspace-layout">
      <!-- Top Overview Bar -->
      <div class="overview-bar">
        <div class="repo-meta">
          <span class="github-icon">🐙</span>
          <span class="repo-name">{{ project.githubRepo }}</span>
          <el-tag size="small" type="info" class="meta-tag">主分支: {{ gitStatus.branch?.defaultBranch || 'main' }}</el-tag>
        </div>
        <div class="action-buttons">
          <el-button size="small" @click="fetchStatus">刷新状态</el-button>
          <el-button 
            v-if="isManager" 
            size="small" 
            type="danger" 
            plain 
            @click="changeRepoPrompt"
          >
            修改关联仓库
          </el-button>
        </div>
      </div>

      <!-- Action Panel when branch does not exist -->
      <div v-if="!gitStatus.branch?.exists" class="init-branch-banner">
        <div class="banner-content">
          <h3>🌱 暂无开发分支</h3>
          <p>当前章节（{{ section.sectionName }}）尚未创建专用的开发分支。开发负责人需要在 GitHub 上基于主分支创建并开发 <strong>{{ gitStatus.branch?.branchName }}</strong>，或直接点击下方按钮一键自动初始化分支。</p>
          <el-button type="primary" class="init-btn" @click="initBranch">
            一键创建开发分支 {{ gitStatus.branch?.branchName }}
          </el-button>
        </div>
      </div>

      <!-- Main Workspace Grid (Only when branch exists) -->
      <div v-else class="main-grid">
        <!-- Left: Commits & Actions Status -->
        <div class="grid-col left-col">
          <div class="col-section">
            <h3 class="section-title">🌱 开发分支: {{ gitStatus.branch?.branchName }}</h3>
            <p class="section-desc">已成功监测到开发分支，任何向此分支推送（Push）的代码提交都会在此处自动更新显示。</p>
          </div>

          <div class="col-section">
            <h4 class="sub-title">📝 提交日志 (Commit History)</h4>
            <div class="commit-list">
              <div v-for="commit in gitStatus.commits" :key="commit.sha" class="commit-item">
                <div class="commit-line-left">
                  <span class="dot"></span>
                  <span class="line"></span>
                </div>
                <div class="commit-detail">
                  <div class="commit-msg">
                    <span class="msg-text">{{ commit.message }}</span>
                    <a :href="commit.url" target="_blank" class="sha-link">#{{ commit.shortSha }}</a>
                  </div>
                  <div class="commit-meta">
                    <span>{{ commit.author }}</span>
                    <span class="separator">•</span>
                    <span>{{ formatCommitTime(commit.date) }}</span>
                  </div>
                </div>
              </div>
              <div v-if="!gitStatus.commits || !gitStatus.commits.length" class="empty-timeline">
                暂无提交记录
              </div>
            </div>
          </div>

          <!-- CI/CD workflow status -->
          <div class="col-section">
            <h4 class="sub-title">⚡ CI/CD 自动化流水线</h4>
            <div class="workflows-list">
              <div v-for="run in gitStatus.workflows" :key="run.id" class="workflow-item">
                <div class="run-info">
                  <span class="run-name">{{ run.name }}</span>
                  <span class="run-event">({{ run.event }})</span>
                </div>
                <div class="run-status">
                  <el-tag :type="workflowStatusType(run.conclusion)" size="small" effect="dark">
                    {{ workflowStatusText(run.conclusion) }}
                  </el-tag>
                  <a :href="run.htmlUrl" target="_blank" class="detail-link">查看详情</a>
                </div>
              </div>
              <div v-if="!gitStatus.workflows || !gitStatus.workflows.length" class="empty-timeline">
                暂无 CI/CD 流水线运行记录
              </div>
            </div>
          </div>
        </div>

        <!-- Right: Pull Request & AI Code Review -->
        <div class="grid-col right-col">
          <!-- Case A: No open Pull Request -->
          <div v-if="!gitStatus.pullRequest?.exists" class="pr-creator-card">
            <h3 class="section-title">🔀 发起合并与评审 (Create Pull Request)</h3>
            <p class="section-desc">当您在此开发分支上完成编码后，请创建一个 Pull Request。这将自动将项目进度流转至<strong>“审核中”</strong>，并允许启动 AI 自动代码评审。</p>
            
            <el-form label-position="top" :model="prForm" class="pr-form">
              <el-form-item label="合并标题" required>
                <el-input v-model="prForm.title" placeholder="如: feat(frontend): 完成用户设置面板交互" />
              </el-form-item>
              <el-form-item label="合并详情说明">
                <el-input 
                  v-model="prForm.body" 
                  type="textarea" 
                  :rows="4" 
                  placeholder="请输入本次代码合并的功能详情、实现逻辑与需要注意的事项..." 
                />
              </el-form-item>
              <el-button type="success" :disabled="!prForm.title" @click="createPR" class="submit-pr-btn">
                创建 Pull Request 并提交审核
              </el-button>
            </el-form>
          </div>

          <!-- Case B: Open Pull Request exists -->
          <div v-else class="pr-details-card">
            <div class="pr-header">
              <div class="pr-title-row">
                <el-tag size="small" type="warning" class="pr-badge">Open PR</el-tag>
                <span class="pr-number">#{{ gitStatus.pullRequest?.number }}</span>
                <span class="pr-title">{{ gitStatus.pullRequest?.title }}</span>
              </div>
              <a :href="gitStatus.pullRequest?.url" target="_blank" class="pr-link-btn">
                在 GitHub 中打开 ↗
              </a>
            </div>

            <!-- Changed Files List -->
            <div class="pr-section">
              <h4 class="sub-title">📁 变更文件列表 ({{ gitStatus.pullRequest?.files?.length || 0 }})</h4>
              <div class="files-tree">
                <div v-for="file in gitStatus.pullRequest?.files" :key="file.filename" class="file-item">
                  <span class="file-icon">📄</span>
                  <span class="file-name" :title="file.filename">{{ getFilenameOnly(file.filename) }}</span>
                  <span class="file-path">{{ getPathOnly(file.filename) }}</span>
                  <div class="file-stats">
                    <span class="additions">+{{ file.additions }}</span>
                    <span class="deletions">-{{ file.deletions }}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- AI Code Review Controller -->
            <div class="pr-section ai-review-box">
              <div class="review-header">
                <h4>🤖 GLM 自动代码审查助手</h4>
                <el-button 
                  type="warning" 
                  size="small" 
                  :loading="aiReviewing" 
                  @click="runAiReview"
                >
                  运行 AI 深度代码评审
                </el-button>
              </div>
              <p class="review-desc">点击按钮，GLM-4 智能体将自动抓取本次 PR 修改的增量差异，分析并发、安全及架构合规性，并自动在 GitHub PR 评论区中输出评审建议。</p>

              <!-- Markdown Review Result -->
              <div v-if="aiReviewResult" class="review-result-container">
                <div class="review-result-title">📢 最新 AI 评审意见：</div>
                <div class="review-result-body markdown-body" v-html="renderMarkdown(aiReviewResult)"></div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { projectDetail, updateProjectGithubRepo } from '../../api/project'
import { getGitStatus, createGitBranch, createGitPullRequest, triggerGitAiReview } from '../../api/github'
import { useUserStore } from '../../stores/user'

const props = defineProps({
  section: { type: Object, required: true },
  projectId: { type: Number, required: true },
  sectionId: { type: Number, required: true }
})

const loading = ref(false)
const aiReviewing = ref(false)
const userStore = useUserStore()
const project = reactive({})
const gitStatus = reactive({ branch: {}, commits: [], pullRequest: {}, workflows: [] })
const newRepo = ref('')
const aiReviewResult = ref('')

const prForm = reactive({
  title: '',
  body: ''
})

const isManager = computed(() => {
  return userStore.user?.role === 'ADMIN' || userStore.user?.id === project.creatorId
})

const loadProject = async () => {
  try {
    const detail = await projectDetail(props.projectId)
    Object.assign(project, detail || {})
    if (project.githubRepo) {
      newRepo.value = project.githubRepo
      await fetchStatus()
    }
  } catch (e) {
    ElMessage.error(e.message || '加载项目详情失败')
  }
}

const fetchStatus = async () => {
  if (!project.githubRepo) return
  loading.value = true
  try {
    const res = await getGitStatus(props.projectId, props.section.sectionCode)
    Object.assign(gitStatus, res || { branch: {}, commits: [], pullRequest: {}, workflows: [] })
    
    // Auto populate PR form with default values if empty
    if (!prForm.title) {
      prForm.title = `feat(${props.section.sectionCode.toLowerCase()}): 完成代码开发 - ${props.section.sectionName}`
      prForm.body = `## 交付章节：${props.section.sectionName}\n\n此 PR 交付了项目的前后端开发代码，特申请合并与审核。`
    }
  } catch (e) {
    ElMessage.error(e.message || '获取 GitHub 状态失败')
  } finally {
    loading.value = false
  }
}

const saveRepo = async () => {
  if (!newRepo.value || !newRepo.value.includes('/')) {
    ElMessage.warning('请输入正确的 GitHub 仓库格式 (owner/repo)')
    return
  }
  loading.value = true
  try {
    await updateProjectGithubRepo(props.projectId, newRepo.value.trim())
    ElMessage.success('成功绑定 GitHub 仓库')
    await loadProject()
  } catch (e) {
    ElMessage.error(e.message || '绑定 GitHub 仓库失败')
  } finally {
    loading.value = false
  }
}

const changeRepoPrompt = () => {
  ElMessageBox.prompt('请输入新的 GitHub 仓库名称 (如: owner/repo)', '修改关联仓库', {
    confirmButtonText: '保存',
    cancelButtonText: '取消',
    inputValue: project.githubRepo,
    inputPattern: /.+\/.+/,
    inputErrorMessage: '仓库格式必须是 owner/repo'
  }).then(async ({ value }) => {
    loading.value = true
    try {
      await updateProjectGithubRepo(props.projectId, value.trim())
      ElMessage.success('成功更新 GitHub 仓库')
      await loadProject()
    } catch (e) {
      ElMessage.error(e.message || '修改失败')
    } finally {
      loading.value = false
    }
  }).catch(() => {})
}

const initBranch = async () => {
  loading.value = true
  try {
    await createGitBranch(props.projectId, props.section.sectionCode)
    ElMessage.success(`成功创建分支 ${gitStatus.branch.branchName}!`)
    await fetchStatus()
  } catch (e) {
    ElMessage.error(e.message || '创建分支失败')
  } finally {
    loading.value = false
  }
}

const createPR = async () => {
  loading.value = true
  try {
    const res = await createGitPullRequest(props.projectId, props.section.sectionCode, prForm.title, prForm.body)
    ElMessage.success('成功发起 Pull Request，状态已自动切换为审核中')
    // Trigger sections update event in the browser
    window.dispatchEvent(new CustomEvent('teamflow:project-sections-updated', {
      detail: { projectId: props.projectId }
    }))
    await fetchStatus()
  } catch (e) {
    ElMessage.error(e.message || '创建 PR 失败')
  } finally {
    loading.value = false
  }
}

const runAiReview = async () => {
  aiReviewing.value = true
  aiReviewResult.value = ''
  try {
    const res = await triggerGitAiReview(props.projectId, props.section.sectionCode)
    ElMessage.success('AI 自动代码评审运行成功，已将评审意见同步发表至 GitHub PR!')
    aiReviewResult.value = res
  } catch (e) {
    ElMessage.error(e.message || '运行 AI 评审失败')
  } finally {
    aiReviewing.value = false
  }
}

// Helpers
const getFilenameOnly = (fullPath) => {
  if (!fullPath) return ''
  const parts = fullPath.split('/')
  return parts[parts.length - 1]
}

const getPathOnly = (fullPath) => {
  if (!fullPath) return ''
  const idx = fullPath.lastIndexOf('/')
  return idx === -1 ? '' : fullPath.substring(0, idx + 1)
}

const formatCommitTime = (timeStr) => {
  if (!timeStr) return ''
  const d = new Date(timeStr)
  return d.toLocaleString()
}

const workflowStatusType = (conclusion) => {
  switch (conclusion) {
    case 'SUCCESS': return 'success'
    case 'FAILURE': return 'danger'
    case 'RUNNING': return 'primary'
    default: return 'warning'
  }
}

const workflowStatusText = (conclusion) => {
  switch (conclusion) {
    case 'SUCCESS': return '构建成功'
    case 'FAILURE': return '构建失败'
    case 'RUNNING': return '进行中'
    default: return conclusion || '未知'
  }
}

// Minimalistic markdown parser
const renderMarkdown = (text) => {
  if (!text) return ''
  let html = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  
  // Headers
  html = html.replace(/^### (.*$)/gim, '<h5>$1</h5>')
  html = html.replace(/^## (.*$)/gim, '<h4>$1</h4>')
  html = html.replace(/^# (.*$)/gim, '<h3>$1</h3>')
  
  // Bold
  html = html.replace(/\*\*(.*?)\*\*/gim, '<strong>$1</strong>')
  
  // Lists
  html = html.replace(/^\s*\-\s+(.*$)/gim, '<li>$1</li>')
  
  // Code block
  html = html.replace(/```([\s\S]*?)```/gim, '<pre><code>$1</code></pre>')
  
  // Inline code
  html = html.replace(/`(.*?)`/gim, '<code>$1</code>')
  
  // Line breaks
  html = html.replace(/\n/g, '<br/>')
  
  return html
}

onMounted(() => {
  loadProject()
})

watch(() => props.projectId, loadProject)
watch(() => props.sectionId, loadProject)
</script>

<style scoped>
.git-workspace-panel {
  min-height: 400px;
  color: #1e293b;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
}

.empty-repo-card {
  text-align: center;
  padding: 60px 40px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  box-shadow: 0 4px 6px -1px rgb(0 0 0 / 0.05);
  max-width: 600px;
  margin: 40px auto;
}

.card-icon {
  font-size: 54px;
  margin-bottom: 20px;
}

h2 {
  font-size: 20px;
  font-weight: 800;
  color: #0f172a;
  margin: 0 0 8px;
}

.subtitle {
  font-size: 14px;
  color: #64748b;
  line-height: 1.6;
  margin: 0 0 24px;
}

.repo-setup-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
}

.repo-input {
  width: 100%;
  max-width: 400px;
}

.setup-btn {
  width: 100%;
  max-width: 400px;
  font-weight: 600;
}

.waiting-prompt {
  max-width: 400px;
  margin: 0 auto;
}

.workspace-layout {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.overview-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #ffffff;
  padding: 16px 20px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}

.repo-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.github-icon {
  font-size: 20px;
}

.repo-name {
  font-weight: 700;
  font-size: 16px;
  color: #0f172a;
}

.meta-tag {
  font-weight: 600;
}

.init-branch-banner {
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
  border-radius: 12px;
  padding: 30px;
  text-align: center;
}

.banner-content h3 {
  margin: 0 0 10px;
  font-size: 16px;
  color: #0f172a;
}

.banner-content p {
  font-size: 14px;
  color: #64748b;
  line-height: 1.6;
  margin: 0 0 20px;
}

.init-btn {
  font-weight: 600;
}

.main-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.grid-col {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.col-section {
  background: #ffffff;
  padding: 20px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}

.section-title {
  font-size: 16px;
  font-weight: 800;
  color: #0f172a;
  margin: 0 0 6px;
}

.section-desc {
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
  margin: 0;
}

.sub-title {
  font-size: 14px;
  font-weight: 700;
  color: #334155;
  margin: 0 0 16px;
  border-bottom: 1px solid #f1f5f9;
  padding-bottom: 8px;
}

.commit-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.commit-item {
  display: flex;
  gap: 12px;
}

.commit-line-left {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
}

.commit-line-left .dot {
  width: 8px;
  height: 8px;
  background: #cbd5e1;
  border-radius: 50%;
  z-index: 2;
  margin-top: 6px;
}

.commit-line-left .line {
  width: 2px;
  flex-grow: 1;
  background: #f1f5f9;
  z-index: 1;
}

.commit-item:last-child .commit-line-left .line {
  display: none;
}

.commit-detail {
  padding-bottom: 16px;
  flex-grow: 1;
}

.commit-msg {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.msg-text {
  font-size: 13px;
  font-weight: 500;
  color: #1e293b;
  line-height: 1.4;
}

.sha-link {
  font-size: 11px;
  color: #0284c7;
  font-family: monospace;
  text-decoration: none;
  background: #f0f9ff;
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px solid #e0f2fe;
}

.commit-meta {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 4px;
}

.commit-meta .separator {
  margin: 0 4px;
}

.workflows-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.workflow-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #f8fafc;
  padding: 10px 14px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.run-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.run-name {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.run-event {
  font-size: 11px;
  color: #94a3b8;
}

.run-status {
  display: flex;
  align-items: center;
  gap: 10px;
}

.detail-link {
  font-size: 12px;
  color: #0284c7;
  text-decoration: none;
}

.pr-creator-card, .pr-details-card {
  background: #ffffff;
  padding: 20px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  box-shadow: 0 4px 6px -1px rgb(0 0 0 / 0.02);
}

.pr-form {
  margin-top: 16px;
}

.submit-pr-btn {
  width: 100%;
  font-weight: 600;
}

.pr-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #f1f5f9;
  padding-bottom: 16px;
  margin-bottom: 20px;
}

.pr-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pr-badge {
  font-weight: 600;
}

.pr-number {
  font-family: monospace;
  font-weight: 700;
  color: #64748b;
  font-size: 15px;
}

.pr-title {
  font-weight: 800;
  font-size: 16px;
  color: #0f172a;
}

.pr-link-btn {
  font-size: 13px;
  color: #0284c7;
  text-decoration: none;
  font-weight: 600;
}

.pr-section {
  margin-bottom: 20px;
}

.files-tree {
  max-height: 200px;
  overflow-y: auto;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.file-item {
  display: flex;
  align-items: center;
  padding: 6px;
  font-size: 12px;
  border-radius: 4px;
}

.file-item:hover {
  background: #f8fafc;
}

.file-icon {
  margin-right: 6px;
}

.file-name {
  font-weight: 600;
  color: #334155;
  margin-right: 6px;
}

.file-path {
  color: #94a3b8;
  flex-grow: 1;
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;
}

.file-stats {
  font-size: 11px;
  display: flex;
  gap: 6px;
  font-weight: 700;
}

.additions {
  color: #16a34a;
}

.deletions {
  color: #dc2626;
}

.ai-review-box {
  background: #fffbeb;
  border: 1px solid #fef08a;
  border-radius: 8px;
  padding: 16px;
}

.review-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.review-header h4 {
  margin: 0;
  color: #854d0e;
  font-size: 14px;
  font-weight: 800;
}

.review-desc {
  font-size: 12px;
  color: #713f12;
  line-height: 1.5;
  margin: 0 0 14px;
}

.review-result-container {
  margin-top: 16px;
  background: #ffffff;
  border: 1px solid #fef08a;
  border-radius: 6px;
  padding: 12px;
}

.review-result-title {
  font-weight: 700;
  font-size: 13px;
  color: #854d0e;
  margin-bottom: 8px;
}

.review-result-body {
  font-size: 13px;
  line-height: 1.6;
  color: #334155;
}

.markdown-body :deep(h3),
.markdown-body :deep(h4),
.markdown-body :deep(h5) {
  margin-top: 12px;
  margin-bottom: 6px;
  font-weight: 700;
  color: #0f172a;
}

.markdown-body :deep(pre) {
  background: #f1f5f9;
  padding: 10px;
  border-radius: 6px;
  overflow-x: auto;
  border: 1px solid #e2e8f0;
}

.markdown-body :deep(code) {
  font-family: monospace;
  font-size: 12px;
}

.empty-timeline {
  text-align: center;
  color: #94a3b8;
  font-size: 12px;
  padding: 20px 0;
}
</style>

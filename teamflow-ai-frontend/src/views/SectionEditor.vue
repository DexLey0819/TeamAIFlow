<template>
  <div class="page">
    <template v-if="route.params.sectionId === 'management-delivery'">
      <ManagementDelivery />
    </template>
    <template v-else-if="section.sectionCode === 'FRONTEND' || section.sectionCode === 'BACKEND'">
      <div class="page-header">
        <div>
          <h1 class="page-title">{{ section.sectionName || '研发看板' }}</h1>
          <p class="page-subtitle">{{ section.description || '研发代码、Git 提交与自动代码审查' }}</p>
        </div>
        <div class="toolbar">
          <el-button @click="$router.push(`/projects/${projectId}/sections`)">返回章节</el-button>
        </div>
      </div>
      <GitWorkspacePanel :section="section" :projectId="projectId" :sectionId="sectionId" />
    </template>
    <template v-else>
      <div class="page-header">
        <div>
          <h1 class="page-title">{{ section.sectionName || '章节编辑' }}</h1>
          <p class="page-subtitle">{{ section.description || '维护章节内容、版本与审核意见' }}</p>
        </div>
        <div class="toolbar">
          <el-tag
            :type="statusType(section.status)"
            class="status-badge clickable"
            @click="showVersions = true"
          >
            {{ statusText(section.status) }} (查看版本记录)
          </el-tag>
          <el-button @click="$router.push(`/projects/${projectId}/sections`)">返回章节</el-button>
        </div>
      </div>

      <div class="panel">
        <div class="panel-head">
          <h3>章节内容</h3>
          <div class="toolbar">
            <template v-if="!isEditing">
              <el-button v-if="canEdit && section.status !== 'REVIEWING'" type="primary" @click="startEditing">编辑</el-button>
              <el-button v-if="canEdit" type="success" :disabled="!form.title" @click="submit">提交审核</el-button>
            </template>
          </div>
        </div>

        <!-- Document Preview Mode -->
        <div v-if="!isEditing" class="document-preview-wrap">
          <h2 class="document-title">{{ form.title || '（暂无标题）' }}</h2>
          <el-divider />
          <div class="document-body">
            <SectionContentRenderer :body="form.body" />
          </div>
        </div>

        <!-- Split Editor Layout -->
        <div v-else class="split-editor-layout">
          <!-- Left side: Live Preview -->
          <div class="split-pane left-pane">
            <div class="pane-title">内容预览</div>
            <div class="preview-scroll">
              <h2 class="document-title">{{ form.title || '（暂无标题）' }}</h2>
              <el-divider />
              <SectionContentRenderer :body="form.body" />
            </div>
          </div>

          <!-- Right side: Edit form -->
          <div class="split-pane right-pane">
            <div class="pane-title">编辑内容</div>
            <el-form :model="form" label-position="top">
              <el-form-item label="标题">
                <el-input v-model="form.title" placeholder="请输入章节标题..." />
              </el-form-item>
              <el-form-item label="正文">
                <div class="editor-toolbar" style="margin-bottom: 8px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap;">
                  <el-select
                    v-model="currentHeadingStyle"
                    placeholder="标题样式"
                    size="small"
                    style="width: 140px;"
                    @change="handleStyleChange"
                  >
                    <el-option label="正文/段落" :value="0" />
                    <el-option label="二级标题 (H2)" :value="2" />
                    <el-option label="三级标题 (H3)" :value="3" />
                    <el-option label="四级标题 (H4)" :value="4" />
                  </el-select>
                  <el-button size="small" @click="insertTable">插入表格</el-button>
                  <el-button size="small" @click="showChartDialog = true">插入图表</el-button>
                  <el-button size="small" @click="showPlantUmlPanel = true">插入 PlantUML</el-button>
                  <el-upload
                    action=""
                    :http-request="uploadImage"
                    :show-file-list="false"
                    accept="image/png,image/jpeg,image/gif,image/webp"
                    style="display: inline-block;"
                  >
                    <el-button size="small">插入图片</el-button>
                  </el-upload>
                  <el-button
                    v-if="canAiGenerate"
                    type="warning"
                    plain
                    size="small"
                    @click="openChatDrawer"
                  >
                    🤖 智能体协同设计
                  </el-button>
                </div>
                <el-input
                  ref="bodyInputRef"
                  v-model="form.body"
                  type="textarea"
                  :autosize="{ minRows: 18 }"
                  placeholder="请输入章节正文内容，支持 Markdown、表格、图表、PlantUML 和图片..."
                  @click="detectCurrentLineStyle"
                  @keyup="detectCurrentLineStyle"
                  @focus="detectCurrentLineStyle"
                />
              </el-form-item>
            </el-form>

            <div class="toolbar editor-actions" style="margin-top: 16px; justify-content: flex-end;">
              <el-button type="primary" @click="saveDraft">保存草稿</el-button>
              <el-button type="success" :disabled="!form.title" @click="submit">提交审核</el-button>
              <el-button @click="cancelEditing">取消</el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- Bottom Comments Feed -->
      <div class="panel comments-panel" style="margin-top: 24px;">
        <h3>章节评论</h3>
        <div class="comments-list" v-if="comments.length">
          <div v-for="c in comments" :key="c.id" class="comment-item">
            <div class="comment-header">
              <span class="username">{{ c.userName }}</span>
              <span class="time">{{ formatTime(c.createTime) }}</span>
            </div>
            <div class="comment-content">{{ c.commentText }}</div>
          </div>
        </div>
        <div class="empty-comments" v-else>
          <span class="muted">暂无评论</span>
        </div>

        <el-divider />

        <div class="comment-form" v-if="canComment">
          <el-input
            v-model="commentText"
            type="textarea"
            :rows="3"
            placeholder="添加章节评论，进行意见交流..."
          />
          <el-button
            type="primary"
            plain
            :disabled="!commentText || !section.latestContent"
            class="comment-button"
            @click="comment"
          >
            提交评论
          </el-button>
        </div>
      </div>

      <!-- Collapsible Version History Drawer -->
      <el-drawer v-model="showVersions" title="版本记录" size="520px" direction="rtl">
        <el-table :data="versions" border style="width: 100%">
          <el-table-column prop="versionNo" label="版本" width="60">
            <template #default="{ row }">v{{ row.versionNo }}</template>
          </el-table-column>
          <el-table-column prop="editorName" label="编辑人" width="90" />
          <el-table-column prop="submitStatus" label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="statusType(row.submitStatus)">
                {{ statusText(row.submitStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="更新时间" min-width="140">
            <template #default="{ row }">
              {{ formatTime(row.updateTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="useVersion(row)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-drawer>
    </template>

    <ChartInsertDialog v-model:visible="showChartDialog" @insert="insertChart" />
    <PlantUmlPanel v-model:visible="showPlantUmlPanel" @insert="insertPlantUml" />

    <!-- Chat Drawer for Interactive AI Co-design -->
    <el-drawer
      v-model="showChatDrawer"
      size="540px"
      direction="rtl"
      destroy-on-close
      :with-header="true"
    >
      <template #header>
        <div class="chat-drawer-header">
          <span class="title">🤖 智能体协同设计</span>
          <div class="subtitle">与负责该章节的【{{ section.ownerRoleName || '数智助理' }}】对话，打磨章节正文</div>
        </div>
      </template>
      
      <div class="chat-container">
        <!-- Chat Message Area -->
        <div class="chat-messages" ref="chatScrollRef">
          <div class="chat-welcome">
            <div class="welcome-card">
              <h4>你好！我是项目的【{{ section.ownerRoleName || '数智助理' }}】智能体。</h4>
              <p>我将协助您协同起草并调整项目的【{{ section.sectionName }}】章节。</p>
              <p class="muted">您可以随时在下方提出修改要求（例如：“帮我增加对于双因子认证（2FA）的安全需求说明” 或 “数据库表的主键修改为 UUID”），我会结合前置章节上下文为您生成和优化 Markdown 格式正文草稿。</p>
            </div>
          </div>
          
          <div
            v-for="(msg, index) in chatMessages"
            :key="index"
            :class="['message-item', msg.role === 'user' ? 'message-user' : 'message-agent']"
          >
            <div class="message-avatar">
              {{ msg.role === 'user' ? '👤' : '🤖' }}
            </div>
            <div class="message-bubble">
              <div class="message-sender">{{ msg.role === 'user' ? '您' : (section.ownerRoleName || '智能体') }}</div>
              <div class="message-text">
                <span v-if="msg.role === 'user'">{{ msg.content }}</span>
                <span v-else>{{ msg.explanation }}</span>
              </div>
              
              <!-- If agent response has generated content, show a compact preview -->
              <div v-if="msg.role === 'assistant' && msg.body" class="draft-preview-box">
                <div class="preview-header">
                  <span>📄 生成的建议草稿</span>
                  <el-button
                    type="success"
                    size="small"
                    plain
                    @click="importDraft(msg.title, msg.body)"
                  >
                    一键导入
                  </el-button>
                </div>
                <div class="preview-title">标题：{{ msg.title }}</div>
                <div class="preview-body-collapsed">
                  {{ msg.body }}
                </div>
              </div>
            </div>
          </div>
          
          <!-- Loading message indicator -->
          <div v-if="chatLoading" class="message-item message-agent">
            <div class="message-avatar">🤖</div>
            <div class="message-bubble">
              <div class="message-sender">{{ section.ownerRoleName || '智能体' }}</div>
              <div class="message-text chat-loading-dots">
                <span>思考与修改中</span>
                <span class="dot">.</span><span class="dot">.</span><span class="dot">.</span>
              </div>
            </div>
          </div>
        </div>
        
        <!-- Input Area -->
        <div class="chat-input-area">
          <el-input
            v-model="userMessage"
            type="textarea"
            :rows="3"
            placeholder="请输入您的修改或生成要求，Ctrl+Enter 发送..."
            @keydown.ctrl.enter="sendChatMessage"
            :disabled="chatLoading"
          />
          <div class="input-actions">
            <span class="muted font-12">Ctrl+Enter 快速发送</span>
            <el-button
              type="primary"
              :disabled="!userMessage.trim() || chatLoading"
              @click="sendChatMessage"
            >
              发送指令
            </el-button>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mySectionPermissions } from '../api/permission'
import { commentSection, saveSectionContent, sectionDetail, sectionVersions, submitSection, sectionComments } from '../api/section'
import { uploadFile, inlineFileUrl } from '../api/file'
import { generateSectionContent, chatSectionContent } from '../api/ai'
import { appendBlock, imageMarkdown, tableTemplate } from '../utils/sectionContentBlocks'
import SectionContentRenderer from '../components/section/SectionContentRenderer.vue'
import ChartInsertDialog from '../components/section/ChartInsertDialog.vue'
import PlantUmlPanel from '../components/section/PlantUmlPanel.vue'
import { usePositiveProjectId, usePositiveSectionId } from '../utils/routeParams'
import ManagementDelivery from './ManagementDelivery.vue'
import GitWorkspacePanel from '../components/section/GitWorkspacePanel.vue'

const route = useRoute()
const projectId = usePositiveProjectId(route)
const sectionId = usePositiveSectionId(route)
const section = reactive({})
const form = reactive({ title: '', body: '' })
const versions = ref([])
const permissions = ref([])
const comments = ref([])
const showVersions = ref(false)
const commentText = ref('')
const bodyInputRef = ref(null)
const currentHeadingStyle = ref(0)
let loadRequestId = 0

const canEdit = computed(() => permissions.value.includes('EDIT'))
const canComment = computed(() => permissions.value.includes('COMMENT'))
const canAiGenerate = computed(() => permissions.value.includes('AI_GENERATE'))
const aiGenerating = ref(false)

// Chat Drawer for Interactive AI Co-design
const showChatDrawer = ref(false)
const chatMessages = ref([])
const userMessage = ref('')
const chatLoading = ref(false)
const chatScrollRef = ref(null)

// View/Edit mode states
const isEditing = ref(false)
const snapshot = reactive({ title: '', body: '' })
const dirty = computed(() => form.title !== snapshot.title || form.body !== snapshot.body)
const showChartDialog = ref(false)
const showPlantUmlPanel = ref(false)

const startEditing = () => {
  isEditing.value = true
  snapshot.title = form.title
  snapshot.body = form.body
}

const cancelEditing = async () => {
  if (dirty.value) {
    try {
      await ElMessageBox.confirm('取消编辑将丢弃所有未保存的修改，是否确认？', '取消编辑', {
        confirmButtonText: '确认丢弃',
        cancelButtonText: '继续编辑',
        type: 'warning'
      })
    } catch {
      return
    }
  }
  form.title = snapshot.title
  form.body = snapshot.body
  isEditing.value = false
}

const statusText = (status) => ({
  EMPTY: '未开始',
  DRAFT: '草稿',
  REVIEWING: '审核中',
  APPROVED: '已通过',
  REJECTED: '已退回'
}[status] || status || '未设置')
const statusType = (status) => ({
  APPROVED: 'success',
  REVIEWING: 'warning',
  REJECTED: 'danger',
  DRAFT: 'info',
  EMPTY: 'info'
}[status] || 'info')

const formatTime = (timeStr) => {
  if (!timeStr) return '-'
  return timeStr.replace('T', ' ').substring(0, 19)
}

const notifyProjectSectionsUpdatedFor = (targetProjectId) => {
  if (targetProjectId === null) return

  window.dispatchEvent(new CustomEvent('teamflow:project-sections-updated', {
    detail: { projectId: targetProjectId }
  }))
}

const resetEditorState = () => {
  Object.keys(section).forEach((key) => {
    delete section[key]
  })
  versions.value = []
  permissions.value = []
  comments.value = []
  chatMessages.value = []
  userMessage.value = ''
  chatLoading.value = false
  showChatDrawer.value = false
  form.title = ''
  form.body = ''
  isEditing.value = false
  snapshot.title = ''
  snapshot.body = ''
}

const fillForm = (content) => {
  form.title = content?.title || section.sectionName || ''
  form.body = content?.body || ''
}

const isCurrentLoad = (requestId, currentProjectId, currentSectionId) => (
  requestId === loadRequestId &&
  projectId.value === currentProjectId &&
  sectionId.value === currentSectionId
)

const isCurrentRoute = (currentProjectId, currentSectionId) => (
  projectId.value === currentProjectId && sectionId.value === currentSectionId
)

const load = async ({ resetBeforeFetch = false } = {}) => {
  const currentProjectId = projectId.value
  const currentSectionId = sectionId.value
  const requestId = ++loadRequestId

  if (resetBeforeFetch) {
    resetEditorState()
  }

  if (currentProjectId === null || currentSectionId === null) {
    resetEditorState()
    return
  }

  const [detail, versionRes, permissionRes, commentsRes] = await Promise.all([
    sectionDetail(currentSectionId),
    sectionVersions(currentSectionId),
    mySectionPermissions(currentSectionId),
    sectionComments(currentSectionId)
  ])

  if (!isCurrentLoad(requestId, currentProjectId, currentSectionId)) {
    return
  }

  Object.assign(section, detail)
  versions.value = versionRes || []
  permissions.value = permissionRes || []
  comments.value = commentsRes || []
  fillForm(detail.latestContent)
  snapshot.title = form.title
  snapshot.body = form.body
}

const saveDraft = async () => {
  const currentProjectId = projectId.value
  const currentSectionId = sectionId.value
  if (currentProjectId === null || currentSectionId === null) return

  await saveSectionContent(currentSectionId, { title: form.title, body: form.body })
  ElMessage.success('草稿已保存')
  notifyProjectSectionsUpdatedFor(currentProjectId)
  if (isCurrentRoute(currentProjectId, currentSectionId)) {
    await load()
  }
  isEditing.value = false
}

const submit = async () => {
  const currentProjectId = projectId.value
  const currentSectionId = sectionId.value
  if (currentProjectId === null || currentSectionId === null) return

  // Auto-save draft first if dirty
  if (dirty.value) {
    await saveSectionContent(currentSectionId, { title: form.title, body: form.body })
  }

  await submitSection(currentSectionId)
  ElMessage.success('已提交审核')
  notifyProjectSectionsUpdatedFor(currentProjectId)
  if (isCurrentRoute(currentProjectId, currentSectionId)) {
    await load()
  }
  isEditing.value = false
}

const comment = async () => {
  const currentProjectId = projectId.value
  const currentSectionId = sectionId.value
  const contentId = section.latestContent?.id
  const text = commentText.value
  if (currentProjectId === null || currentSectionId === null || !contentId || !text) return

  await commentSection(currentSectionId, {
    contentId,
    commentText: text
  })
  if (!isCurrentRoute(currentProjectId, currentSectionId)) return
  ElMessage.success('评论已提交')
  commentText.value = ''
  const latestComments = await sectionComments(currentSectionId)
  if (!isCurrentRoute(currentProjectId, currentSectionId)) return
  comments.value = latestComments
}

const useVersion = (row) => {
  fillForm(row)
  if (isEditing.value) {
    snapshot.title = form.title
    snapshot.body = form.body
  }
  showVersions.value = false
  ElMessage.success(`已切换至版本 v${row.versionNo}`)
}

const openChatDrawer = () => {
  showChatDrawer.value = true
}

const sendChatMessage = async () => {
  if (!userMessage.value.trim() || chatLoading.value) return
  const currentProjectId = projectId.value
  const currentSectionId = sectionId.value
  if (currentProjectId === null || currentSectionId === null) return
  
  const text = userMessage.value.trim()
  userMessage.value = ''
  
  // Push user message
  chatMessages.value.push({
    role: 'user',
    content: text
  })
  
  // Scroll to bottom
  scrollToBottom()
  
  chatLoading.value = true
  try {
    const history = chatMessages.value.map(m => ({
      role: m.role,
      content: m.content || m.explanation
    }))
    
    const res = await chatSectionContent(currentProjectId, currentSectionId, history)
    if (!isCurrentRoute(currentProjectId, currentSectionId)) return
    
    // Push assistant message
    chatMessages.value.push({
      role: 'assistant',
      explanation: res.explanation || '我已为您生成最新设计。',
      title: res.title || '',
      body: res.body || ''
    })
  } catch (error) {
    console.error(error)
    if (!isCurrentRoute(currentProjectId, currentSectionId)) return
    chatMessages.value.push({
      role: 'assistant',
      explanation: '抱歉，智能体遇到了一点问题：' + (error.message || '生成失败'),
      title: '',
      body: ''
    })
  } finally {
    if (isCurrentRoute(currentProjectId, currentSectionId)) {
      chatLoading.value = false
      scrollToBottom()
    }
  }
}

const scrollToBottom = () => {
  nextTick(() => {
    if (chatScrollRef.value) {
      chatScrollRef.value.scrollTop = chatScrollRef.value.scrollHeight
    }
  })
}

const importDraft = async (title, body) => {
  if (!title && !body) return
  
  if (form.title || form.body) {
    try {
      await ElMessageBox.confirm(
        '导入智能体设计的草稿将覆盖您当前左侧编辑器中的内容，是否确认？',
        '确认覆盖',
        {
          confirmButtonText: '确认覆盖',
          cancelButtonText: '取消',
          type: 'warning'
        }
      )
    } catch {
      return
    }
  }
  
  form.title = title || form.title
  form.body = body || form.body
  ElMessage.success('智能体协同设计的草稿已成功导入编辑器')
}



// Unsaved change confirmation
const confirmUnsavedChanges = async () => {
  if (!dirty.value) return true
  try {
    await ElMessageBox.confirm(
      '章节内容有未保存更新，是否保存并更新章节内容？',
      '未保存更新',
      {
        confirmButtonText: '保存并继续',
        cancelButtonText: '继续不保存',
        distinguishCancelAndClose: true,
        type: 'warning'
      }
    )
    await saveDraft()
    return true
  } catch (action) {
    if (action === 'cancel') {
      return true
    }
    return false
  }
}

onBeforeRouteLeave(async () => {
  if (!isEditing.value || !dirty.value) return true
  return await confirmUnsavedChanges()
})

const beforeUnload = (event) => {
  if (!isEditing.value || !dirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

onMounted(() => {
  window.addEventListener('beforeunload', beforeUnload)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', beforeUnload)
})

watch(
  () => [projectId.value, sectionId.value],
  () => {
    load({ resetBeforeFetch: true })
  },
  { immediate: true }
)

// Word-like heading styles handling
const detectCurrentLineStyle = () => {
  const textarea = bodyInputRef.value?.$el.querySelector('textarea')
  if (!textarea) return
  const start = textarea.selectionStart
  const text = form.body || ''
  const lineStart = text.lastIndexOf('\n', start - 1) + 1
  let lineEnd = text.indexOf('\n', start)
  if (lineEnd === -1) {
    lineEnd = text.length
  }
  const lineText = text.substring(lineStart, lineEnd)
  const headingMatch = lineText.match(/^\s*(#{1,6})\s+/)
  if (headingMatch) {
    currentHeadingStyle.value = headingMatch[1].length
  } else {
    currentHeadingStyle.value = 0
  }
}

const applyHeadingStyle = (level) => {
  const textarea = bodyInputRef.value?.$el.querySelector('textarea')
  if (!textarea) return
  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const text = form.body || ''

  const lineStart = text.lastIndexOf('\n', start - 1) + 1
  let lineEnd = text.indexOf('\n', end)
  if (lineEnd === -1) {
    lineEnd = text.length
  }

  const lineText = text.substring(lineStart, lineEnd)
  const cleanLine = lineText.replace(/^\s*#+\s*/, '')

  let newPrefix = ''
  if (level === 2) newPrefix = '## '
  else if (level === 3) newPrefix = '### '
  else if (level === 4) newPrefix = '#### '

  const newLineText = newPrefix + cleanLine
  form.body = text.substring(0, lineStart) + newLineText + text.substring(lineEnd)

  nextTick(() => {
    textarea.focus()
    const difference = newLineText.length - lineText.length
    textarea.setSelectionRange(start + (difference > 0 ? difference : 0), end + difference)
  })
}

const handleStyleChange = (value) => {
  applyHeadingStyle(value)
}

// Insert functions
const insertTable = () => {
  form.body = appendBlock(form.body, tableTemplate)
}

const insertChart = (block) => {
  form.body = appendBlock(form.body, block)
  showChartDialog.value = false
}

const insertPlantUml = (block) => {
  form.body = appendBlock(form.body, block)
  showPlantUmlPanel.value = false
}

const uploadImage = async ({ file }) => {
  const currentProjectId = projectId.value
  const currentSectionId = sectionId.value
  if (currentProjectId === null || currentSectionId === null) return
  try {
    const formData = new FormData()
    formData.append('projectId', currentProjectId)
    formData.append('sectionId', currentSectionId)
    formData.append('documentType', 'IMAGE')
    formData.append('file', file)
    const uploaded = await uploadFile(formData)
    if (!isCurrentRoute(currentProjectId, currentSectionId)) return
    form.body = appendBlock(form.body, imageMarkdown(uploaded.fileName, inlineFileUrl(uploaded.id)))
    ElMessage.success('图片已插入')
  } catch (error) {
    console.error(error)
    if (isCurrentRoute(currentProjectId, currentSectionId)) {
      ElMessage.error('图片插入失败')
    }
  }
}
</script>

<style scoped>
.clickable {
  cursor: pointer;
}
.status-badge {
  margin-right: 8px;
  font-weight: 600;
  transition: all 0.2s;
}
.status-badge:hover {
  opacity: 0.85;
}
.split-editor-layout {
  display: flex;
  gap: 24px;
  margin-top: 16px;
}
.split-pane {
  flex: 1;
  min-width: 0;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 20px;
  background-color: #fff;
  display: flex;
  flex-direction: column;
}
.left-pane {
  height: auto;
}
.right-pane {
  justify-content: space-between;
}
.pane-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f2f6fc;
}
.document-preview-wrap {
  background-color: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 32px;
  min-height: 400px;
}
.document-title {
  margin-top: 0;
  font-size: 24px;
  font-weight: 700;
  color: #1a202c;
}
.comments-panel {
  padding: 24px;
}
.comment-item {
  background: #f8fafc;
  padding: 16px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  margin-bottom: 12px;
}
.comment-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
  font-size: 13px;
}
.comment-header .username {
  font-weight: 600;
  color: #475569;
}
.comment-header .time {
  color: #94a3b8;
}
.comment-content {
  color: #1e293b;
  font-size: 14px;
  line-height: 1.5;
}
.comment-button {
  margin-top: 12px;
}
.editor-actions {
  display: flex;
  gap: 12px;
}
h3 {
  margin-top: 0;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.editor-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
}
.chat-drawer-header {
  display: flex;
  flex-direction: column;
}
.chat-drawer-header .title {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}
.chat-drawer-header .subtitle {
  font-size: 12px;
  color: #64748b;
  margin-top: 2px;
}
.chat-container {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 100px);
  margin: -24px;
}
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background-color: #f8fafc;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.chat-welcome {
  margin-bottom: 8px;
}
.welcome-card {
  background-color: #fff;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  padding: 16px;
}
.welcome-card h4 {
  margin-top: 0;
  margin-bottom: 8px;
  color: #0f172a;
}
.welcome-card p {
  margin: 4px 0;
  font-size: 13px;
  color: #475569;
  line-height: 1.5;
}
.welcome-card .muted {
  color: #64748b;
  font-size: 12px;
}
.message-item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  max-width: 85%;
}
.message-user {
  align-self: flex-end;
  flex-direction: row-reverse;
}
.message-agent {
  align-self: flex-start;
}
.message-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background-color: #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
  flex-shrink: 0;
}
.message-user .message-avatar {
  background-color: #dbeafe;
}
.message-bubble {
  background-color: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 10px 14px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.02);
  min-width: 80px;
}
.message-user .message-bubble {
  background-color: #3b82f6;
  border-color: #3b82f6;
  color: #fff;
}
.message-sender {
  font-size: 10px;
  font-weight: 600;
  color: #64748b;
  margin-bottom: 2px;
}
.message-user .message-sender {
  color: #93c5fd;
  text-align: right;
}
.message-text {
  font-size: 13.5px;
  line-height: 1.5;
  white-space: pre-wrap;
}
.draft-preview-box {
  margin-top: 10px;
  background-color: #f1f5f9;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 10px;
  color: #334155;
}
.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  border-bottom: 1px solid #cbd5e1;
  padding-bottom: 4px;
  font-size: 11px;
  font-weight: 600;
}
.preview-title {
  font-weight: 600;
  font-size: 12px;
  margin-bottom: 4px;
}
.preview-body-collapsed {
  font-size: 11px;
  color: #475569;
  max-height: 120px;
  overflow-y: auto;
  white-space: pre-wrap;
  background: #fff;
  padding: 6px;
  border-radius: 4px;
  border: 1px solid #e2e8f0;
}
.chat-input-area {
  padding: 12px 16px;
  background-color: #fff;
  border-top: 1px solid #e2e8f0;
}
.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 6px;
}
.font-12 {
  font-size: 12px;
}
.chat-loading-dots {
  display: flex;
  align-items: center;
  gap: 2px;
}
.chat-loading-dots .dot {
  animation: wave 1.3s infinite;
}
.chat-loading-dots .dot:nth-child(2) {
  animation-delay: 0.15s;
}
.chat-loading-dots .dot:nth-child(3) {
  animation-delay: 0.3s;
}
@keyframes wave {
  0%, 60%, 100% { transform: translateY(0); }
  30% { transform: translateY(-3px); }
}
</style>

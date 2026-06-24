import { createRouter, createWebHistory } from 'vue-router'
import { toLoginWithRedirect } from '../utils/authRedirect'
import { isManagementDeliverySection, toPositiveInteger } from '../utils/routeParams'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue'), meta: { public: true } },
  { path: '/register', component: () => import('../views/Register.vue'), meta: { public: true } },
  { path: '/join/:projectCode', component: () => import('../views/JoinProject.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('../views/Layout.vue'),
    children: [
      { path: '', redirect: '/dashboard' },
      { path: 'dashboard', component: () => import('../views/Dashboard.vue') },
      { path: 'profile', component: () => import('../views/Profile.vue') },
      { path: 'projects', component: () => import('../views/ProjectList.vue') },
      { path: 'projects/new', component: () => import('../views/ProjectWizard.vue') },
      { path: 'projects/:id', redirect: (to) => `/projects/${to.params.id}/overview` },
      { path: 'projects/:id/overview', component: () => import('../views/ProjectOverview.vue') },
      { path: 'projects/:id/members', component: () => import('../views/ProjectMembers.vue') },
      { path: 'projects/:id/sections', component: () => import('../views/SectionList.vue') },
      { path: 'projects/:id/sections/:sectionId', component: () => import('../views/SectionEditor.vue') },
      { path: 'projects/:id/reviews', component: () => import('../views/ProjectReviews.vue') },
      { path: 'projects/:id/reviews/:sectionId', component: () => import('../views/SectionReviewPage.vue') },
      { path: 'projects/:id/files', component: () => import('../views/FileManage.vue') },
      { path: 'projects/:id/tasks', component: () => import('../views/TaskBoard.vue') },
      { path: 'projects/:id/progress', component: () => import('../views/ProgressRecord.vue') },
      { path: 'projects/:id/export', component: () => import('../views/ProjectExport.vue') },
      { path: 'notifications', component: () => import('../views/NotificationCenter.vue') },
      { path: 'admin/templates', component: () => import('../views/TemplateManage.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const token = localStorage.getItem('teamflow_token')
  const isPublic = to.matched.some((record) => record.meta.public)
  if (!token && !isPublic) {
    return toLoginWithRedirect(to.fullPath)
  }
  if (token && ['/login', '/register'].includes(to.path)) {
    return '/dashboard'
  }

  if (to.path.startsWith('/projects/') && to.params.id != null) {
    const projectId = toPositiveInteger(to.params.id)
    if (!projectId) {
      return '/projects'
    }
    const isSectionEditorRoute = to.path.startsWith(`/projects/${projectId}/sections/`)
    if (
      to.params.sectionId != null &&
      !(isSectionEditorRoute && isManagementDeliverySection(to.params.sectionId)) &&
      !toPositiveInteger(to.params.sectionId)
    ) {
      return `/projects/${projectId}/sections`
    }
  }

  return true
})

export default router

import request from '../utils/request'

export const initProject = (data) => request.post('/projects/init', data)
export const projectInitResult = (id) => request.get(`/projects/${id}/init-result`)
export const myProjects = () => request.get('/projects/my')
export const projectDetail = (id) => request.get(`/projects/${id}`)
export const listMembers = (projectId) => request.get(`/projects/${projectId}/members`)

export const createProject = initProject

export const saveProjectWbs = (id, wbsData) => request.post(`/projects/${id}/wbs`, wbsData, { headers: { 'Content-Type': 'text/plain' } })
export const updateProjectGithubRepo = (id, githubRepo) => request.post(`/projects/${id}/github-repo`, { githubRepo })

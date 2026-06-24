import request from '../utils/request'

export const needSubmit = () => request.get('/progress/need-submit')
export const submitReport = (data) => request.post('/progress/report', data)
export const progressStatus = (projectId) => request.get(`/projects/${projectId}/progress-status`)

export const createProgress = submitReport
export const projectProgress = progressStatus
export const weekProgress = needSubmit
export const listReports = (projectId) => request.get(`/projects/${projectId}/progress-records`)

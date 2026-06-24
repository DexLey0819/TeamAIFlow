import request from '../utils/request'

export const weeklyReport = (projectId) => request.post(`/ai/projects/${projectId}/weekly-report`, {}, { timeout: 120000 })
export const riskAnalysis = (projectId) => request.post(`/ai/projects/${projectId}/risk-analysis`, {}, { timeout: 120000 })
export const documentCheck = (projectId) => request.post(`/ai/projects/${projectId}/document-check`, {}, { timeout: 120000 })
export const summaryReport = (projectId) => request.post(`/ai/projects/${projectId}/summary-report`, {}, { timeout: 120000 })
export const aiRecords = (projectId) => request.get(`/ai/projects/${projectId}/records`)
export const extractProgress = (projectId, data) => request.post(`/ai/projects/${projectId}/extract-progress`, data, { timeout: 120000 })
export const generateSectionContent = (projectId, sectionId) => request.post(`/ai/projects/${projectId}/sections/${sectionId}/generate`, {}, { timeout: 120000 })
export const chatSectionContent = (projectId, sectionId, messages) => request.post(`/ai/projects/${projectId}/sections/${sectionId}/chat`, { messages }, { timeout: 120000 })



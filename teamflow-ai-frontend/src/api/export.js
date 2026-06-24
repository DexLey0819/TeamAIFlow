import request from '../utils/request'

export const createExport = (projectId, data) => request.post(`/projects/${projectId}/export`, data)
export const exportHistory = (projectId) => request.get(`/projects/${projectId}/export/history`)
export const downloadExport = (id) => request.get(`/exports/${id}/download`, { responseType: 'blob' })

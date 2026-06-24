import request from '../utils/request'
export const uploadFile = (formData) => request.post('/files/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
export const projectFiles = (projectId) => request.get(`/files/project/${projectId}`)
export const deleteFile = (id) => request.delete(`/files/${id}`)
export const downloadFile = (id) => request.get(`/files/download/${id}`, { responseType: 'blob' })
export const inlineFileUrl = (id) => `/api/files/inline/${id}`


import request from '../utils/request'

export const joinPreview = (projectCode) => request.get(`/projects/join/${projectCode}`)
export const submitJoinApply = (projectId, data) => request.post(`/projects/${projectId}/join-apply`, data)
export const joinApplies = (projectId) => request.get(`/projects/${projectId}/join-applies`)
export const approveJoinApply = (projectId, applyId, data) => request.put(`/projects/${projectId}/join-applies/${applyId}/approve`, data)
export const rejectJoinApply = (projectId, applyId, data) => request.put(`/projects/${projectId}/join-applies/${applyId}/reject`, data)

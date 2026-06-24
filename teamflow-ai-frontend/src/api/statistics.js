import request from '../utils/request'

export const completeness = (projectId) => request.get(`/projects/${projectId}/completeness`)
export const contribution = (projectId) => request.get(`/projects/${projectId}/contribution`)
export const riskTrend = (projectId) => request.get(`/projects/${projectId}/risk-trend`)

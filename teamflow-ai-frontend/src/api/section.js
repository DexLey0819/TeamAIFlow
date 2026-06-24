import request from '../utils/request'

export const projectSections = (projectId) => request.get(`/projects/${projectId}/sections`)
export const sectionDetail = (sectionId) => request.get(`/sections/${sectionId}`)
export const saveSectionContent = (sectionId, data) => request.put(`/sections/${sectionId}/content`, data)
export const submitSection = (sectionId) => request.post(`/sections/${sectionId}/submit`)
export const reviewSection = (sectionId, data) => request.post(`/sections/${sectionId}/review`, data)
export const commentSection = (sectionId, data) => request.post(`/sections/${sectionId}/comments`, data)
export const sectionVersions = (sectionId) => request.get(`/sections/${sectionId}/versions`)
export const sectionComments = (sectionId) => request.get(`/sections/${sectionId}/comments`)
export const sectionReviews = (sectionId) => request.get(`/sections/${sectionId}/reviews`)
export const projectReviews = (projectId) => request.get(`/projects/${projectId}/reviews/me`)

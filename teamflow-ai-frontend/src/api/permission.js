import request from '../utils/request'

export const projectPermissions = (projectId) => request.get(`/projects/${projectId}/permissions`)
export const mySectionPermissions = (sectionId) => request.get(`/sections/${sectionId}/permissions/me`)

import request from '../utils/request'

export const mySectionPermissions = (sectionId) => request.get(`/sections/${sectionId}/permissions/me`)

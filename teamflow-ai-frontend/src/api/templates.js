import request from '../utils/request'

export const projectTemplates = () => request.get('/project-templates')
export const projectTemplateDetail = (code) => request.get(`/project-templates/${code}`)
export const adminProjectTemplates = () => request.get('/admin/project-templates')
export const updateProjectTemplate = (id, data) => request.put(`/admin/project-templates/${id}`, data)

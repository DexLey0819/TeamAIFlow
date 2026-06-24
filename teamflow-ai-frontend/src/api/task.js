import request from '../utils/request'

export const projectTasks = (projectId) => request.get(`/projects/${projectId}/tasks`)
export const createTask = (data) => request.post('/tasks', data)
export const updateTask = (id, data) => request.put(`/tasks/${id}`, data)
export const updateTaskStatus = (id, data) => request.put(`/tasks/${id}/status`, data)
export const taskLogs = (id) => request.get(`/tasks/${id}/logs`)

export const myTasks = () => Promise.resolve([])
export const deleteTask = () => Promise.reject(new Error('任务删除接口未开放'))
export const initTemplateTasks = () => Promise.resolve()

import request from '../utils/request'

export const getGitStatus = (projectId, sectionCode) => request.get(`/github/${projectId}/status`, { params: { sectionCode } })
export const createGitBranch = (projectId, sectionCode) => request.post(`/github/${projectId}/branch`, { sectionCode })
export const createGitPullRequest = (projectId, sectionCode, title, body) => request.post(`/github/${projectId}/pull-request`, { sectionCode, title, body })
export const triggerGitAiReview = (projectId, sectionCode) => request.post(`/github/${projectId}/ai-review`, { sectionCode })

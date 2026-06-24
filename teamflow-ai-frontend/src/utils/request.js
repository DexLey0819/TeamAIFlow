import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('teamflow_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res && typeof res.code !== 'undefined') {
      if (res.code !== 200) {
        ElMessage.error(res.message || '请求失败')
        if (res.code === 401) {
          localStorage.removeItem('teamflow_token')
          router.push('/login')
        }
        return Promise.reject(res)
      }
      return res.data
    }
    return response.data
  },
  (error) => {
    ElMessage.error(error.response?.data?.message || '服务器异常')
    return Promise.reject(error)
  }
)

export default request

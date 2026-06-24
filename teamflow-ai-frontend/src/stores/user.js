import { defineStore } from 'pinia'
import { login, me, updateMe } from '../api/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('teamflow_token') || '',
    user: JSON.parse(localStorage.getItem('teamflow_user') || 'null')
  }),
  getters: {
    isLogin: (state) => Boolean(state.token),
    role: (state) => state.user?.role || ''
  },
  actions: {
    async loginAction(payload) {
      const data = await login(payload)
      this.token = data.token
      this.user = data.user
      localStorage.setItem('teamflow_token', data.token)
      localStorage.setItem('teamflow_user', JSON.stringify(data.user))
    },
    async loadMe() {
      if (!this.token) return
      this.user = await me()
      localStorage.setItem('teamflow_user', JSON.stringify(this.user))
    },
    async updateProfile(payload) {
      const data = await updateMe(payload)
      this.user = data
      localStorage.setItem('teamflow_user', JSON.stringify(data))
      return data
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('teamflow_token')
      localStorage.removeItem('teamflow_user')
    }
  }
})

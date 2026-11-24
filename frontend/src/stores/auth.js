import { defineStore } from 'pinia'

import * as authService from '@/services/auth'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: authService.getSessionUser(),
    // 비밀번호 찾기에서 본인 확인을 마친 대상입니다. 새로고침하면 사라집니다.
    resetTarget: null,
  }),

  getters: {
    isLoggedIn: (state) => Boolean(state.user),
  },

  actions: {
    async login(credentials) {
      this.user = await authService.login(credentials)
    },

    async logout() {
      await authService.logout()
      this.user = null
    },

    async verifyResetTarget(form) {
      this.resetTarget = await authService.verifyResetTarget(form)
    },

    async resetPassword(password, code) {
      await authService.resetPassword({
        userId: this.resetTarget?.userId,
        password,
        code,
      })
      this.resetTarget = null
    },

    async updateProfile(form) {
      this.user = await authService.updateProfile(form)
    },

    async withdraw(password) {
      await authService.withdraw(password)
      this.user = null
    },
  },
})

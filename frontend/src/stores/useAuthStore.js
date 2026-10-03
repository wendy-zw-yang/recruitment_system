import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { decodeJwt } from '@/utils/jwt'

const STORAGE_KEY = 'rs-auth'

export const useAuthStore = defineStore('auth', () => {
  const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null')
  const token = ref(stored?.token || '')
  const userInfo = ref(stored?.userInfo || null)

  const role = computed(() => userInfo.value?.role || '')
  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => role.value === 'ADMIN')
  const isHR = computed(() => role.value === 'HR')
  const isCandidate = computed(() => role.value === 'CANDIDATE')

  function persist() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      token: token.value,
      userInfo: userInfo.value
    }))
  }

  function setSession(newToken, info) {
    token.value = newToken
    userInfo.value = info
    persist()
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem(STORAGE_KEY)
  }

  function bootstrapFromToken() {
    if (!token.value) return
    const payload = decodeJwt(token.value)
    if (!payload || payload.exp * 1000 < Date.now()) {
      logout()
    }
  }

  return {
    token,
    userInfo,
    role,
    isLoggedIn,
    isAdmin,
    isHR,
    isCandidate,
    setSession,
    logout,
    bootstrapFromToken
  }
})

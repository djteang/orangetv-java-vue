import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, logout as logoutApi, getCurrentUser as getCurrentUserApi } from '@/api/auth'
import type { LoginRequest, LoginResponse } from '@/api/auth'

export interface AuthUser {
  username: string
  role: string
  avatar?: string
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const user = ref<AuthUser | null>(null)
  const justLoggedIn = ref(false) // 标记是否刚刚登录

  // 从 localStorage 恢复用户信息
  const savedUser = localStorage.getItem('user')
  if (savedUser) {
    try {
      user.value = JSON.parse(savedUser)
    } catch {
      // ignore
    }
  }

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'admin' || user.value?.role === 'owner')
  const isOwner = computed(() => user.value?.role === 'owner')

  async function login(credentials: LoginRequest) {
    const res = await loginApi(credentials) as unknown as LoginResponse
    token.value = res.token
    user.value = {
      username: res.username,
      role: res.role,
      avatar: res.avatar,
    }
    localStorage.setItem('token', res.token)
    localStorage.setItem('user', JSON.stringify(user.value))
    justLoggedIn.value = true // 设置刚刚登录标志
    return res
  }

  function clearJustLoggedIn() {
    justLoggedIn.value = false
  }

  function setJustLoggedIn() {
    justLoggedIn.value = true
  }

  function logout() {
    logoutApi().catch(() => {
      // ignore
    })
    token.value = null
    user.value = null
    justLoggedIn.value = false
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  function setToken(newToken: string) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  async function fetchCurrentUser() {
    try {
      const userInfo = await getCurrentUserApi()
      user.value = {
        username: userInfo.username,
        role: userInfo.role,
        avatar: userInfo.avatar,
      }
      localStorage.setItem('user', JSON.stringify(user.value))
    } catch (error) {
      console.error('Failed to fetch current user:', error)
      throw error
    }
  }

  return {
    token,
    user,
    isLoggedIn,
    isAdmin,
    isOwner,
    justLoggedIn,
    login,
    logout,
    setToken,
    setJustLoggedIn,
    clearJustLoggedIn,
    fetchCurrentUser,
  }
})

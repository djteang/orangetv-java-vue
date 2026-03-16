import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import { showToast } from '@/composables/useToast'

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

const instance: AxiosInstance = axios.create({
  baseURL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 请求拦截器
instance.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 从 localStorage 直接获取 token，避免 Pinia 循环依赖
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    // 如果是 FormData，删除 Content-Type 让浏览器自动设置
    if (config.data instanceof FormData) {
      delete config.headers['Content-Type']
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
instance.interceptors.response.use(
  (response) => {
    const res = response.data
    // 统一 { code, message, data } 格式
    if (res && typeof res === 'object' && 'code' in res) {
      if (res.code === 200) {
        return res.data
      }
      // 业务错误
      if (res.code === 401) {
        localStorage.removeItem('token')
        localStorage.removeItem('user')
        if (!window.location.pathname.includes('/login')) {
          showToast('登录已过期，请重新登录', 'warning')
          window.location.href = '/login'
        }
      }
      return Promise.reject(res)
    }
    // 非标准格式（流式/二进制等），直接返回
    return res
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (!window.location.pathname.includes('/login')) {
        showToast('登录已过期，请重新登录', 'warning')
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

export default instance

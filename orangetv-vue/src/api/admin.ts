import request from './index'
import axios from 'axios'
import type { AdminConfig, User, VideoSource, LiveSource } from '@/types'

// ─── 管理配置 ─────────────────────────────────────────────────────────────

export function getAdminConfig(): Promise<AdminConfig> {
  return request.get('/admin/config')
}

// ─── 统计数据 ─────────────────────────────────────────────────────────────

export function getStats(): Promise<{
  totalUsers: number
  todayNewUsers: number
  totalSearches: number
  todaySearches: number
  userTrend: { date: string; count: number }[]
  searchTrend: { date: string; count: number }[]
}> {
  return request.get('/admin/stats')
}

// ─── 站点配置 ─────────────────────────────────────────────────────────────

export function updateSiteConfig(config: Record<string, unknown>): Promise<void> {
  return request.post('/admin/site', config)
}

// ─── 用户管理 ──────────────────────────────────────────────────────────────

export function getUsers(): Promise<User[]> {
  return request.get('/admin/user')
}

export function addUser(username: string, password: string, userGroup?: string): Promise<void> {
  return request.post('/admin/user', { action: 'add', targetUsername: username, targetPassword: password, userGroup })
}

export function deleteUser(username: string): Promise<void> {
  return request.post('/admin/user', { action: 'deleteUser', targetUsername: username })
}

export function banUser(username: string): Promise<void> {
  return request.post('/admin/user', { action: 'ban', targetUsername: username })
}

export function unbanUser(username: string): Promise<void> {
  return request.post('/admin/user', { action: 'unban', targetUsername: username })
}

export function setAdmin(username: string): Promise<void> {
  return request.post('/admin/user', { action: 'setAdmin', targetUsername: username })
}

export function cancelAdmin(username: string): Promise<void> {
  return request.post('/admin/user', { action: 'cancelAdmin', targetUsername: username })
}

export function changePassword(username: string, password: string): Promise<void> {
  return request.post('/admin/user', { action: 'changePassword', targetUsername: username, targetPassword: password })
}

// ─── 视频源管理 ────────────────────────────────────────────────────────────

export function getVideoSources(): Promise<VideoSource[]> {
  return request.get('/admin/source')
}

export function addVideoSource(key: string, name: string, api: string, detail?: string): Promise<void> {
  return request.post('/admin/source', { action: 'add', key, name, api, detail: detail || '' })
}

export function editVideoSource(key: string, name: string, api: string, detail?: string): Promise<void> {
  return request.post('/admin/source', { action: 'edit', key, name, api, detail: detail || '' })
}

export function deleteVideoSource(key: string): Promise<void> {
  return request.post('/admin/source', { action: 'delete', key })
}

export function enableVideoSource(key: string): Promise<void> {
  return request.post('/admin/source', { action: 'enable', key })
}

export function disableVideoSource(key: string): Promise<void> {
  return request.post('/admin/source', { action: 'disable', key })
}

export function validateVideoSource(api: string): Promise<{ valid: boolean; message: string }> {
  return request.post('/admin/source/validate', { api })
}

export function speedTestSource(key: string, api: string): Promise<{ responseTime: number; statusCode: number; success: boolean; error?: string }> {
  return request.get('/admin/source/speedtest', { params: { key, api } })
}

// ─── 直播源管理 ────────────────────────────────────────────────────────────

export function getLiveSources(): Promise<LiveSource[]> {
  return request.get('/admin/live')
}

export function addLiveSource(name: string, key: string, url: string, epg?: string, ua?: string): Promise<void> {
  return request.post('/admin/live', { action: 'add', name, key, url, epg: epg || '', ua: ua || '' })
}

export function editLiveSource(id: number, name: string, key: string, url: string, epg?: string, ua?: string): Promise<void> {
  return request.post('/admin/live', { action: 'edit', id, name, key, url, epg: epg || '', ua: ua || '' })
}

export function deleteLiveSource(id: number): Promise<void> {
  return request.post('/admin/live', { action: 'delete', id })
}

export function enableLiveSource(id: number): Promise<void> {
  return request.post('/admin/live', { action: 'enable', id })
}

export function disableLiveSource(id: number): Promise<void> {
  return request.post('/admin/live', { action: 'disable', id })
}

export function refreshLiveSources(): Promise<void> {
  return request.post('/admin/live/refresh')
}

// ─── 分类管理 ──────────────────────────────────────────────────────────────

export function addCategory(name: string, type: string, query: string): Promise<void> {
  return request.post('/admin/category', { action: 'add', name, type, query })
}

export function deleteCategory(index: number): Promise<void> {
  return request.post('/admin/category', { action: 'delete', index })
}

export function enableCategory(index: number): Promise<void> {
  return request.post('/admin/category', { action: 'enable', index })
}

export function disableCategory(index: number): Promise<void> {
  return request.post('/admin/category', { action: 'disable', index })
}

// ─── 配置文件 ──────────────────────────────────────────────────────────────

export function fetchConfigSubscription(url: string): Promise<string> {
  return request.post('/admin/config_subscription/fetch', { url })
}

export function saveConfigFile(data: {
  config_file?: string
  config_subscription_url?: string
  config_subscription_auto_update?: boolean
}): Promise<void> {
  return request.post('/admin/config_file', data)
}

// ─── 数据迁移 ──────────────────────────────────────────────────────────────

export function exportData(password: string): Promise<Blob> {
  const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'
  const token = localStorage.getItem('token')
  return axios.post(`${baseURL}/admin/data_migration/export`, { password }, {
    responseType: 'blob',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  }).then(res => res.data)
}

export function importData(file: File, password: string): Promise<void> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('password', password)
  return request.post('/admin/data_migration/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

import request from './index'
import type { PlayRecord, Favorite, SkipConfig } from '@/types'

// 播放记录 - 后端路径: /api/playrecords
export function getPlayRecords(): Promise<Record<string, PlayRecord>> {
  return request.get('/playrecords')
}

export function savePlayRecord(key: string, record: PlayRecord): Promise<void> {
  // 后端支持扁平格式，直接展开 record 字段
  return request.post('/playrecords', { key, ...record })
}

export function deletePlayRecord(key: string): Promise<void> {
  return request.delete('/playrecords', { params: { key } })
}

// 收藏 - 后端路径: /api/favorites
export function getFavorites(): Promise<Record<string, Favorite>> {
  return request.get('/favorites')
}

export function saveFavorite(key: string, favorite: Favorite): Promise<void> {
  // 后端支持扁平格式，直接展开 favorite 字段
  return request.post('/favorites', { key, ...favorite })
}

export function deleteFavorite(key: string): Promise<void> {
  return request.delete('/favorites', { params: { key } })
}

// 搜索历史 - 后端路径: /api/searchhistory
export function getSearchHistory(): Promise<string[]> {
  return request.get('/searchhistory')
}

export function addSearchHistory(keyword: string): Promise<void> {
  return request.post('/searchhistory', { keyword })
}

export function deleteSearchHistory(keyword?: string): Promise<void> {
  return request.delete('/searchhistory', { params: keyword ? { keyword } : {} })
}

// 跳过配置 - 后端路径: /api/skipconfigs
export function getSkipConfigs(): Promise<Record<string, SkipConfig>> {
  return request.get('/skipconfigs')
}

export function saveSkipConfig(source: string, id: string, config: SkipConfig): Promise<void> {
  const key = `${source}+${id}`
  // 后端支持扁平格式，直接展开 config 字段
  return request.post('/skipconfigs', { key, ...config })
}

export function deleteSkipConfig(source: string, id: string): Promise<void> {
  const key = `${source}+${id}`
  return request.delete('/skipconfigs', { params: { key } })
}

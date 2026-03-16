import request from './index'
import type { LiveSource } from '@/types'

export function getLiveSources(): Promise<LiveSource[]> {
  return request.get('/live/sources')
}

export function getLiveCategories(): Promise<string[]> {
  return request.get('/live/categories')
}

export function getLiveSourcesByCategory(category: string): Promise<LiveSource[]> {
  return request.get(`/live/sources/${encodeURIComponent(category)}`)
}

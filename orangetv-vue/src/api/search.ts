import request from './index'
import type { SearchResult } from '@/types'

export interface SearchParams {
  q: string
  source?: string
  page?: number
  pageSize?: number
}

// search 返回 { results: SearchResult[] }，拦截器解包后得到该对象
export function search(params: SearchParams): Promise<{ results: SearchResult[] }> {
  return request.get('/search', { params })
}

export function getVideoDetail(source: string, id: string): Promise<SearchResult> {
  return request.get(`/search/detail/${encodeURIComponent(source)}/${encodeURIComponent(id)}`)
}

export function getVideoPlayUrl(source: string, id: string, episode: number): Promise<string> {
  return request.get(`/search/play-url/${encodeURIComponent(source)}/${encodeURIComponent(id)}/${episode}`)
}

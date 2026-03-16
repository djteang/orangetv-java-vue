import request from './index'

export interface ShortDramaItem {
  id: number
  vod_id?: number
  name: string
  cover: string
  score?: number
  update_time?: string
  total_episodes?: string
  vod_class?: string
  vod_tag?: string
}

export interface ShortDramaListResponse {
  list: ShortDramaItem[]
  totalPages: number
}

export interface ShortDramaCategory {
  id: string
  name: string
}

export function getShortDramaCategories(): Promise<ShortDramaCategory[]> {
  return request.get('/shortdrama/categories')
}

export function getShortDramaLatest(params: { page: string }): Promise<ShortDramaItem[]> {
  return request.get('/shortdrama/latest', { params })
}

export function getShortDramaList(params: { categoryId: string; page: string }): Promise<ShortDramaListResponse> {
  return request.get('/shortdrama/list', { params })
}

export function searchShortDrama(keyword: string): Promise<ShortDramaItem[]> {
  return request.get('/shortdrama/search', { params: { keyword } })
}

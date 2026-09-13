import request from './index'
import type { SearchResult } from '@/types'
import { createServerEventParser } from '@/utils/serverEvents'
import { readDisableYellowFilter } from '@/utils/yellowFilter'

export interface SearchParams {
  q: string
  source?: string
  page?: number
  pageSize?: number
  disableYellowFilter?: boolean
}

// search 返回 { results: SearchResult[] }，拦截器解包后得到该对象
export function search(params: SearchParams, signal?: AbortSignal): Promise<{ results: SearchResult[] }> {
  return request.get('/search', { params: { ...params, disableYellowFilter: params.disableYellowFilter ?? readDisableYellowFilter() }, signal })
}

export interface SearchProgress {
  totalSources: number
  completedSources: number
  failedSources: number
  timedOut: boolean
}

export interface SearchStreamOptions {
  disableYellowFilter?: boolean
  signal: AbortSignal
  onResults: (results: SearchResult[]) => void
  onProgress: (progress: SearchProgress) => void
}

export async function streamSearch(query: string, options: SearchStreamOptions): Promise<SearchProgress> {
  const { signal, onResults, onProgress, disableYellowFilter = readDisableYellowFilter() } = options
  const fallback = async () => {
    const response = await search({ q: query, disableYellowFilter }, signal)
    onResults(Array.isArray(response) ? response : response.results || [])
    const summary = { totalSources: 1, completedSources: 1, failedSources: 0, timedOut: false }
    onProgress(summary)
    return summary
  }
  let token: string | null = null
  try {
    token = localStorage.getItem('token')
    if (localStorage.getItem('fluidSearch') === 'false') return fallback()
  } catch { /* 禁用存储时仍可搜索。 */ }
  const base = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
  let response: Response
  try {
    response = await fetch(`${base}/search/stream?${new URLSearchParams({ q: query, disableYellowFilter: String(disableYellowFilter) })}`, {
      signal,
      headers: { Accept: 'text/event-stream', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      credentials: 'same-origin',
    })
  } catch (cause) {
    if (signal.aborted) throw cause
    throw new Error('暂时无法连接片库，请检查网络后重试')
  }
  // 前后端滚动更新期间兼容尚未提供 SSE 的后端。
  if ([404, 405, 406].includes(response.status)) { await response.body?.cancel(); return fallback() }
  if (!response.ok) {
    await response.body?.cancel()
    throw new Error(response.status === 401 ? '登录已过期，请重新登录后搜索' : '搜索服务暂时不可用，请稍后重试')
  }
  if (!response.body || !response.headers.get('content-type')?.includes('text/event-stream')) {
    await response.body?.cancel()
    throw new Error('搜索服务返回异常，请稍后重试')
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let summary: SearchProgress | null = null
  const parser = createServerEventParser(({ event, data }) => {
    if (!['init', 'results', 'progress', 'done'].includes(event)) return
    const payload = JSON.parse(data)
    if (event === 'results') {
      if (Array.isArray(payload.results)) onResults(payload.results)
      return
    }
    const progress: SearchProgress = {
      totalSources: Math.max(0, Number(payload.totalSources) || 0),
      completedSources: Math.max(0, Number(payload.completedSources) || 0),
      failedSources: Math.max(0, Number(payload.failedSources) || 0),
      timedOut: payload.timedOut === true,
    }
    onProgress(progress)
    if (event === 'done') summary = progress
  })
  try {
    while (!summary) {
      const { value, done } = await reader.read()
      if (done) { parser.push(decoder.decode()); parser.finish(); break }
      parser.push(decoder.decode(value, { stream: true }))
    }
    if (signal.aborted) throw new DOMException('Search aborted', 'AbortError')
    if (!summary) throw new Error('搜索连接中断，请重试')
    return summary
  } catch (cause) {
    if (signal.aborted) throw cause
    if (cause instanceof TypeError) throw new Error('搜索连接中断，请重试')
    if (cause instanceof SyntaxError) throw new Error('搜索数据暂时无法读取，请重试')
    throw cause
  } finally {
    await reader.cancel().catch(() => {})
    reader.releaseLock()
  }
}

export function getVideoDetail(source: string, id: string, signal?: AbortSignal): Promise<SearchResult> {
  return request.get(`/search/detail/${encodeURIComponent(source)}/${encodeURIComponent(id)}`, { signal })
}

export function getVideoPlayUrl(source: string, id: string, episode: number): Promise<string> {
  return request.get(`/search/play-url/${encodeURIComponent(source)}/${encodeURIComponent(id)}/${episode}`)
}

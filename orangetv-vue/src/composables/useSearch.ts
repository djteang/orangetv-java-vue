import { onScopeDispose, ref, shallowRef } from 'vue'
import { streamSearch, type SearchProgress } from '@/api/search'
import type { SearchResult } from '@/types'

interface CachedSearch { results: SearchResult[]; progress: SearchProgress; expires: number }
const recentSearches = new Map<string, CachedSearch>()
let cacheOwner = ''
const emptyProgress = (): SearchProgress => ({ totalSources: 0, completedSources: 0, failedSources: 0, timedOut: false })

function canCache(items: SearchResult[]) {
  if (items.length > 500) return false
  let size = 0
  for (const item of items) {
    size += JSON.stringify(item).length
    if (size > 1_000_000) return false
  }
  return true
}

export function useSearch(searcher = streamSearch) {
  const results = shallowRef<SearchResult[]>([])
  const query = ref('')
  const loading = ref(false)
  const error = ref('')
  const interrupted = ref(false)
  const fromCache = ref(false)
  const progress = shallowRef<SearchProgress>(emptyProgress())
  let controller: AbortController | undefined
  let generation = 0
  let flushTimer: ReturnType<typeof setTimeout> | undefined
  let deadline: ReturnType<typeof setTimeout> | undefined
  const collected = new Map<string, SearchResult>()

  function flush() {
    clearTimeout(flushTimer)
    flushTimer = undefined
    results.value = Array.from(collected.values())
  }

  function stop() {
    generation++
    controller?.abort()
    controller = undefined
    clearTimeout(deadline)
    if (loading.value) { flush(); interrupted.value = true }
    loading.value = false
  }

  function clear() {
    stop()
    collected.clear()
    results.value = []
    query.value = ''
    error.value = ''
    interrupted.value = false
    fromCache.value = false
    progress.value = emptyProgress()
  }

  async function run(value: string, force = false) {
    const nextQuery = value.trim()
    if (!nextQuery || (!force && loading.value && query.value === nextQuery)) return
    clear()
    query.value = nextQuery
    const version = generation
    let owner = ''
    try { owner = localStorage.getItem('token') || '' } catch { /* 无存储模式。 */ }
    if (owner !== cacheOwner) { recentSearches.clear(); cacheOwner = owner }
    const cached = recentSearches.get(nextQuery)
    if (!force && cached && cached.expires > Date.now()) {
      results.value = cached.results
      progress.value = cached.progress
      fromCache.value = true
      return
    }
    recentSearches.delete(nextQuery)
    loading.value = true
    controller = new AbortController()
    const request = controller
    let exceededDeadline = false
    deadline = setTimeout(() => { exceededDeadline = true; request.abort() }, 26000)
    try {
      const summary = await searcher(nextQuery, {
        signal: request.signal,
        onResults(items) {
          if (version !== generation || request.signal.aborted) return
          for (const item of items) {
            if (!item || typeof item.id !== 'string' || typeof item.source !== 'string' || typeof item.title !== 'string') continue
            collected.set(JSON.stringify([item.source, item.id]), item)
          }
          // 首批立即显示；后续数据合并刷新，减少筛选、排序和卡片的重复渲染。
          if (!results.value.length) flush()
          else if (!flushTimer) flushTimer = setTimeout(flush, 80)
        },
        onProgress(value) { if (version === generation) progress.value = value },
      })
      if (version !== generation) return
      flush()
      progress.value = summary
      interrupted.value = summary.timedOut || summary.failedSources > 0 || summary.completedSources < summary.totalSources
      if (!results.value.length && interrupted.value) error.value = '部分来源暂时没有响应，请稍后重试'
      if (!interrupted.value && canCache(results.value)) {
        recentSearches.set(nextQuery, { results: results.value, progress: summary, expires: Date.now() + 60000 })
        if (recentSearches.size > 8) recentSearches.delete(recentSearches.keys().next().value!)
      }
    } catch (cause) {
      if (version !== generation) return
      flush()
      interrupted.value = true
      error.value = exceededDeadline ? '搜索等待超时，请重试' : cause instanceof Error ? cause.message : '搜索失败，请稍后重试'
    } finally {
      if (version === generation) {
        clearTimeout(deadline)
        loading.value = false
        controller = undefined
      }
    }
  }

  onScopeDispose(() => { stop(); clearTimeout(flushTimer) })
  return { results, query, loading, error, interrupted, fromCache, progress, run, stop, clear }
}

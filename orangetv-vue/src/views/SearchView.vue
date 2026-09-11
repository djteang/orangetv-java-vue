<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageLayout from '@/components/PageLayout.vue'
import VideoCard from '@/components/VideoCard.vue'
import SearchResultFilter from '@/components/SearchResultFilter.vue'
import DiscoveryState from '@/components/DiscoveryState.vue'
import DiscoveryPortal from '@/components/DiscoveryPortal.vue'
import type { SearchFilterCategory, SearchFilterKey } from '@/components/SearchResultFilter.vue'
import { useUserStore } from '@/stores/user'
import { useToast } from '@/composables/useToast'
import { useSearch } from '@/composables/useSearch'
import type { SearchResult } from '@/types'
import { Search, X, ChevronUp, CheckCircle2, Loader2 } from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const toast = useToast()

const keyword = ref('')
const searchState = useSearch()
const { results, loading, query: activeQuery, error: searchError, progress, interrupted, fromCache } = searchState
const searched = ref(false)
const inputRef = ref<HTMLInputElement | null>(null)
const RESULTS_BATCH_SIZE = 48
const visibleLimit = ref(RESULTS_BATCH_SIZE)
const loadMoreRef = ref<HTMLDivElement | null>(null)
const progressPercent = computed(() => progress.value.totalSources ? Math.min(100, progress.value.completedSources / progress.value.totalSources * 100) : 0)

// 聚合设置
const aggregateSearch = ref(true)

// 筛选状态
const filterValues = ref<Partial<Record<SearchFilterKey, string>>>({
  source: 'all',
  title: 'all',
  year: 'all',
  yearOrder: 'none',
})

// Back to top
const showBackToTop = ref(false)
const scrollProgress = ref(0)
const isHoveringBackToTop = ref(false)

// 构建筛选选项 - 基于搜索结果动态生成
const filterCategories = computed<SearchFilterCategory[]>(() => {
  const sourcesMap = new Map<string, string>()
  const titlesSet = new Set<string>()
  const yearsSet = new Set<string>()

  results.value.forEach((item) => {
    if (item.source && item.source_name) {
      sourcesMap.set(item.source, item.source_name)
    }
    if (item.title) titlesSet.add(item.title)
    if (item.year) yearsSet.add(item.year)
  })

  const sourceOptions = [
    { label: '全部来源', value: 'all' },
    ...Array.from(sourcesMap.entries())
      .sort((a, b) => a[1].localeCompare(b[1]))
      .map(([value, label]) => ({ label, value })),
  ]

  const titleOptions = [
    { label: '全部标题', value: 'all' },
    ...Array.from(titlesSet.values())
      .sort((a, b) => a.localeCompare(b))
      .map((t) => ({ label: t, value: t })),
  ]

  // 年份：将 unknown 放末尾
  const years = Array.from(yearsSet.values())
  const knownYears = years.filter((y) => y !== 'unknown').sort((a, b) => parseInt(b) - parseInt(a))
  const hasUnknown = years.includes('unknown')
  const yearOptions = [
    { label: '全部年份', value: 'all' },
    ...knownYears.map((y) => ({ label: y, value: y })),
    ...(hasUnknown ? [{ label: '未知', value: 'unknown' }] : []),
  ]

  return [
    { key: 'source' as SearchFilterKey, label: '来源', options: sourceOptions },
    { key: 'title' as SearchFilterKey, label: '标题', options: titleOptions },
    { key: 'year' as SearchFilterKey, label: '年份', options: yearOptions },
  ]
})

// 年份比较函数
function compareYear(a: string, b: string, order: string): number {
  const aNum = a === 'unknown' ? -Infinity : parseInt(a)
  const bNum = b === 'unknown' ? -Infinity : parseInt(b)
  return order === 'asc' ? aNum - bNum : bNum - aNum
}

// 聚合后的结果类型
interface AggregatedResult extends SearchResult {
  sources: { source: string; source_name: string; id: string }[]
  sourceCount: number
}

// 应用筛选与排序后的结果
const filteredResults = computed(() => {
  const { source, title, year, yearOrder } = filterValues.value

  let filtered = results.value.filter((item) => {
    if (source !== 'all' && item.source !== source) return false
    if (title !== 'all' && item.title !== title) return false
    if (year !== 'all' && item.year !== year) return false
    return true
  })

  // 如果是无排序状态，直接返回过滤后的原始顺序
  if (yearOrder === 'none') {
    return filtered
  }

  // 按年份排序
  return [...filtered].sort((a, b) => {
    const yearComp = compareYear(a.year || 'unknown', b.year || 'unknown', yearOrder!)
    if (yearComp !== 0) return yearComp

    // 年份相同时，精确匹配在前
    const query = activeQuery.value
    const aExactMatch = a.title === query
    const bExactMatch = b.title === query
    if (aExactMatch && !bExactMatch) return -1
    if (!aExactMatch && bExactMatch) return 1

    // 最后按标题排序
    return yearOrder === 'asc'
      ? a.title.localeCompare(b.title)
      : b.title.localeCompare(a.title)
  })
})

// 聚合后的结果 - 按标题和年份分组
const aggregatedResults = computed<AggregatedResult[]>(() => {
  if (!aggregateSearch.value) {
    // 不聚合时，直接返回原结果，附加空的sources
    return filteredResults.value.map(item => ({
      ...item,
      sources: [{ source: item.source, source_name: item.source_name, id: item.id }],
      sourceCount: 1
    }))
  }

  // 按标题+年份聚合
  const groupMap = new Map<string, AggregatedResult>()

  for (const item of filteredResults.value) {
    const key = `${item.title}|${item.year || 'unknown'}`

    if (groupMap.has(key)) {
      const existing = groupMap.get(key)!
      // 添加到来源列表（避免重复）
      if (!existing.sources.some(s => s.source === item.source)) {
        existing.sources.push({
          source: item.source,
          source_name: item.source_name,
          id: item.id
        })
        existing.sourceCount = existing.sources.length
      }
    } else {
      groupMap.set(key, {
        ...item,
        sources: [{ source: item.source, source_name: item.source_name, id: item.id }],
        sourceCount: 1
      })
    }
  }

  return Array.from(groupMap.values())
})

// 显示用的结果（聚合或原始）
const displayResults = computed(() => aggregatedResults.value)
const visibleResults = computed(() => displayResults.value.slice(0, visibleLimit.value))

// 计算筛选后的搜索结果数量
const resultCount = computed(() => displayResults.value.length)
const totalResultCount = computed(() => filteredResults.value.length)
const hasMoreResults = computed(() => resultCount.value > visibleLimit.value)

function loadMoreResults() {
  if (!hasMoreResults.value) return
  visibleLimit.value = Math.min(visibleLimit.value + RESULTS_BATCH_SIZE, resultCount.value)
}

// 每批渲染后重新检测底部，也覆盖流式结果追加和筛选后列表变短的情况。
watch([loadMoreRef, resultCount, visibleLimit], ([target], _previous, onCleanup) => {
  if (!target || !hasMoreResults.value || !('IntersectionObserver' in window)) return

  let active = true
  const observer = new IntersectionObserver((entries) => {
    if (active && entries.some(entry => entry.isIntersecting)) loadMoreResults()
  }, { rootMargin: '0px 0px 400px 0px' })
  observer.observe(target)

  onCleanup(() => {
    active = false
    observer.disconnect()
  })
}, { flush: 'post' })

function handleFilterChange(values: Record<SearchFilterKey, string>) {
  filterValues.value = values
  visibleLimit.value = RESULTS_BATCH_SIZE
}

function resetFilters() {
  filterValues.value = { source: 'all', title: 'all', year: 'all', yearOrder: 'none' }
  visibleLimit.value = RESULTS_BATCH_SIZE
}

function handleSearch(force = false) {
  const query = keyword.value.trim()
  if (!query) {
    toast.warning('请输入搜索关键词')
    return
  }

  if (!force && loading.value && activeQuery.value === query) return
  searched.value = true
  resetFilters()
  void searchState.run(query, force)
  void router.replace({ query: { ...route.query, keyword: query } })
  // 历史记录独立保存，失败或响应缓慢都不阻塞片库搜索。
  void userStore.addSearchHistory(query).catch(() => {})
}

function handleHistoryClick(item: string) {
  keyword.value = item
  handleSearch()
}

async function handleClearHistory() {
  await userStore.clearSearchHistory()
}

async function handleDeleteHistoryItem(item: string) {
  await userStore.deleteSearchHistoryItem(item)
}

function clearKeyword() {
  keyword.value = ''
  searchState.clear()
  searched.value = false
  router.replace({ query: {} })
}

function focusKeyword() { inputRef.value?.focus(); inputRef.value?.select() }

function scrollToTop() {
  try {
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } catch {
    window.scrollTo(0, 0)
  }
}

function handleScroll() {
  const scrollTop = window.scrollY || document.documentElement.scrollTop
  const scrollHeight = document.documentElement.scrollHeight - document.documentElement.clientHeight
  showBackToTop.value = scrollTop > 300
  scrollProgress.value = scrollHeight > 0 ? (scrollTop / scrollHeight) * 100 : 0
}

onMounted(() => {
  void userStore.fetchSearchHistory().catch(() => {})
  window.addEventListener('scroll', handleScroll)

  // 加载聚合搜索设置
  try { aggregateSearch.value = localStorage.getItem('defaultAggregateSearch') !== 'false' } catch { /* 使用默认设置。 */ }

  const urlKeyword = route.query.keyword as string
  if (urlKeyword) {
    keyword.value = urlKeyword
    handleSearch()
  }
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
})

watch(
  () => route.query.keyword,
  (newKeyword) => {
    const value = typeof newKeyword === 'string' ? newKeyword.trim() : ''
    if (value === activeQuery.value) return
    keyword.value = value
    if (value) handleSearch()
    else { searchState.clear(); searched.value = false }
  }
)
</script>

<template>
  <PageLayout>
    <div class="px-4 sm:px-10 py-4 sm:py-8 overflow-visible mb-10">
      <!-- 搜索框 -->
      <div class="mb-8">
        <form @submit.prevent="handleSearch()" class="max-w-2xl mx-auto">
          <div class="relative">
            <Search class="absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-gray-400 dark:text-gray-500" />
            <input
              ref="inputRef"
              v-model="keyword"
              type="text"
              aria-label="搜索影片"
              maxlength="200"
              placeholder="搜索电影、电视剧、短剧..."
              autocomplete="off"
              class="w-full h-12 rounded-lg bg-gray-50/80 py-3 pl-10 pr-12 text-sm text-gray-700 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-400 focus:bg-white border border-gray-200/50 shadow-sm dark:bg-gray-800 dark:text-gray-300 dark:placeholder-gray-500 dark:focus:bg-gray-700 dark:border-gray-700"
            />
            <button
              v-if="keyword"
              type="button"
              aria-label="清空搜索"
              @click="clearKeyword"
              class="absolute right-3 top-1/2 h-5 w-5 -translate-y-1/2 text-gray-400 hover:text-gray-600 transition-colors dark:text-gray-500 dark:hover:text-gray-300"
            >
              <X class="h-5 w-5" />
            </button>
          </div>
        </form>
      </div>

      <!-- 搜索结果或搜索历史 -->
      <div class="max-w-[95%] mx-auto mt-12 overflow-visible">
        <!-- 搜索结果 -->
        <section v-if="searched" class="mb-12">
          <!-- 标题 -->
          <div class="mb-4">
            <h2 class="text-xl font-bold text-gray-800 dark:text-gray-200">
              搜索结果
              <span v-if="resultCount > 0" class="ml-2 text-sm font-normal text-gray-500 dark:text-gray-400">
                共 {{ resultCount }} 条<span v-if="aggregateSearch && resultCount !== totalResultCount">（{{ totalResultCount }} 个来源）</span>
              </span>
            </h2>
          </div>

          <div v-if="loading && results.length" class="search-signal">
            <svg class="search-signal__portal" viewBox="75 10 155 190" fill="none" aria-hidden="true" focusable="false">
              <DiscoveryPortal mode="loading" />
            </svg>
            <div class="search-signal__copy">
              <span>新结果正在陆续抵达</span>
              <small v-if="progress.totalSources">已完成 {{ progress.completedSources }} / {{ progress.totalSources }} 个来源</small>
            </div>
            <button type="button" @click="searchState.stop">停止搜索</button>
            <div class="search-signal__track" role="progressbar" aria-label="搜索来源进度" :aria-valuenow="progress.totalSources ? progress.completedSources : undefined" :aria-valuemax="progress.totalSources || undefined" aria-valuemin="0"><i :style="{ width: `${progressPercent}%` }"></i></div>
          </div>
          <div v-else-if="!loading && results.length" class="search-summary">
            <span>{{ interrupted ? '已保留当前结果，部分来源未完成。' : fromCache ? '已显示最近的搜索结果。' : '片库搜索已完成。' }}</span>
            <button type="button" @click="handleSearch(true)">重新搜索</button>
          </div>

          <!-- 筛选器 -->
          <div v-if="results.length > 0" class="mb-8 flex items-center justify-between gap-3">
            <div class="flex-1 min-w-0">
              <SearchResultFilter
                :categories="filterCategories"
                :values="filterValues"
                @change="handleFilterChange"
              />
            </div>
          </div>

          <!-- 加载中 -->
          <DiscoveryState v-if="results.length === 0 && loading" mode="loading" variant="search" :title="`正在寻找「${activeQuery}」`" description="传送门正在连接片库，找到的影片会陆续出现在这里。" action-label="停止搜索" @action="searchState.stop">
            <p v-if="progress.totalSources" class="search-source-count">已完成 {{ progress.completedSources }} / {{ progress.totalSources }} 个来源</p>
          </DiscoveryState>

          <!-- 无结果 -->
          <DiscoveryState v-else-if="results.length === 0" :mode="searchError ? 'error' : 'empty'" variant="search" :title="searchError ? '片库信号暂时中断' : interrupted ? '搜索已停止' : '未找到相关结果'" :description="searchError || (interrupted ? '随时可以重新出发，继续寻找想看的影片。' : '试试更短的片名，或换一个关键词，新的故事也许就在下一站。')" :action-label="interrupted ? '重新搜索' : '换个关键词'" @action="interrupted ? handleSearch(true) : focusKeyword()" />

          <!-- 筛选后无结果 -->
          <DiscoveryState v-else-if="displayResults.length === 0" :mode="loading ? 'loading' : 'empty'" variant="search" :title="loading ? '正在寻找符合筛选的影片' : '没有符合筛选条件的结果'" description="放宽来源、标题或年份条件，再看看其他好故事。" action-label="清除筛选" @action="resetFilters" />

          <!-- 结果网格 -->
          <div
            v-else
            class="justify-start grid grid-cols-3 gap-x-2 gap-y-14 sm:gap-y-20 px-0 sm:px-2 sm:grid-cols-[repeat(auto-fill,_minmax(11rem,_1fr))] sm:gap-x-8"
          >
            <div v-for="item in visibleResults" :key="`${item.source}-${item.id}`" class="w-full search-result-card">
              <VideoCard
                :id="item.id"
                :source="item.source"
                :title="item.title"
                :poster="item.poster"
                :episodes="item.episodes?.length || 1"
                :source-name="item.source_name"
                :year="item.year"
                from="search"
                :type="(item.episodes?.length || 1) > 1 ? 'tv' : ''"
                :source-count="aggregateSearch ? item.sourceCount : undefined"
                :sources="aggregateSearch ? item.sources : undefined"
              />
            </div>
          </div>
          <div v-if="hasMoreResults" ref="loadMoreRef" class="search-more">
            <button type="button" @click="loadMoreResults">显示更多结果<span>还有 {{ resultCount - visibleLimit }} 条</span></button>
          </div>
          <div
            v-else-if="resultCount > 0"
            class="mt-16 px-4 py-6 text-center"
            role="status"
            aria-live="polite"
            aria-atomic="true"
          >
            <div class="flex items-center justify-center gap-3 text-sm font-medium text-theme-text-secondary">
              <span class="h-px w-10 bg-theme-border" aria-hidden="true"></span>
              <Loader2 v-if="loading" class="h-4 w-4 shrink-0 animate-spin text-theme-accent motion-reduce:animate-none" aria-hidden="true" />
              <CheckCircle2 v-else-if="!interrupted" class="h-4 w-4 shrink-0 text-theme-accent" aria-hidden="true" />
              <span>{{ loading ? '正在寻找更多结果…' : interrupted ? '当前结果已全部显示' : '已经到底啦' }}</span>
              <span class="h-px w-10 bg-theme-border" aria-hidden="true"></span>
            </div>
            <p class="mt-2 text-xs leading-relaxed text-theme-text-secondary">
              <template v-if="loading">其他来源仍在搜索中，请稍候。</template>
              <template v-else-if="interrupted">已展示当前 {{ resultCount }} 条结果，部分来源未完成，可以重新搜索。</template>
              <template v-else>已展示全部 {{ resultCount }} 条结果，换个关键词发现更多精彩。</template>
            </p>
          </div>
        </section>
        <!-- 搜索历史 -->
        <section v-else-if="userStore.searchHistory.length > 0" class="mb-12">
          <h2 class="mb-4 text-xl font-bold text-gray-800 text-left dark:text-gray-200">
            搜索历史
            <button
              @click="handleClearHistory"
              class="ml-3 text-sm text-gray-500 hover:text-red-500 transition-colors dark:text-gray-400 dark:hover:text-red-500"
            >
              清空
            </button>
          </h2>
          <div class="flex flex-wrap gap-2">
            <div v-for="item in userStore.searchHistory" :key="item" class="relative group">
              <button
                @click="handleHistoryClick(item)"
                class="px-4 py-2 bg-gray-500/10 hover:bg-gray-300 rounded-full text-sm text-gray-700 transition-colors duration-200 dark:bg-gray-700/50 dark:hover:bg-gray-600 dark:text-gray-300"
              >
                {{ item }}
              </button>
              <button
                @click.stop="handleDeleteHistoryItem(item)"
                class="absolute -top-1 -right-1 w-4 h-4 opacity-0 group-hover:opacity-100 bg-gray-400 hover:bg-red-500 text-white rounded-full flex items-center justify-center text-[10px] transition-colors"
              >
                <X class="w-3 h-3" />
              </button>
            </div>
          </div>
        </section>
        <DiscoveryState v-else mode="empty" variant="search" title="下一部好片，从这里出发" description="输入电影、剧集或动漫的名字，一起穿过片库传送门。" action-label="开始搜索" @action="focusKeyword" />
      </div>
    </div>

    <!-- 返回顶部按钮 -->
    <div
      :class="[
        'fixed bottom-20 md:bottom-6 right-6 z-[500] transition-all duration-300 ease-in-out',
        showBackToTop
          ? 'opacity-100 translate-y-0 pointer-events-auto'
          : 'opacity-0 translate-y-4 pointer-events-none',
      ]"
    >
      <button
        type="button"
        aria-label="回到顶部"
        @click="scrollToTop"
        @mouseenter="isHoveringBackToTop = true"
        @mouseleave="isHoveringBackToTop = false"
        class="relative w-14 h-14 backdrop-blur-xl rounded-full shadow-2xl transition-all duration-300 ease-out group hover:scale-110 hover:shadow-theme-accent/40 focus:outline-none focus:ring-2 focus:ring-theme-accent/50 border border-theme-accent/30 bg-theme-surface/90 bg-gradient-to-br from-theme-accent/20 via-primary-400/15 to-primary-600/20"
      >
        <div class="absolute inset-1 bg-gradient-to-br from-theme-accent/15 to-primary-400/20 rounded-full backdrop-blur-sm flex items-center justify-center transition-all duration-300 group-hover:from-theme-accent/25 group-hover:to-primary-400/30">
          <!-- 悬停时显示进度百分比，否则显示箭头图标 -->
          <span
            v-if="isHoveringBackToTop"
            class="text-primary-700 dark:text-primary-300 font-semibold text-sm transition-all duration-200"
          >
            {{ Math.round(scrollProgress) }}%
          </span>
          <ChevronUp
            v-else
            class="w-6 h-6 text-primary-700 dark:text-primary-300 transition-all duration-300 group-hover:scale-110"
          />
        </div>
        <svg class="absolute inset-0 w-full h-full -rotate-90" viewBox="0 0 56 56">
          <circle cx="28" cy="28" r="25" fill="none" stroke="rgb(var(--color-theme-accent) / 0.18)" stroke-width="2" />
          <circle
            cx="28" cy="28" r="25" fill="none" stroke="url(#progressGradient)" stroke-width="2"
            stroke-linecap="round"
            :stroke-dasharray="`${(scrollProgress / 100) * 157} 157`"
            class="transition-all duration-300 ease-out"
          />
          <defs>
            <linearGradient id="progressGradient" x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" stop-color="rgb(var(--color-theme-accent))" />
              <stop offset="50%" stop-color="rgb(var(--color-primary-400))" />
              <stop offset="100%" stop-color="rgb(var(--color-primary-600))" />
            </linearGradient>
          </defs>
        </svg>
      </button>
    </div>
  </PageLayout>
</template>

<style scoped>
.search-signal { position: relative; display: flex; align-items: center; gap: 12px; margin-bottom: 20px; padding: 12px 14px 15px; overflow: hidden; border: 1px solid rgb(var(--color-theme-border) / .75); border-radius: 15px; background: rgb(var(--color-theme-surface) / .8); }
.search-signal__portal { display: block; flex-shrink: 0; width: 30px; height: 36px; overflow: visible; }
.search-signal__copy { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 3px; color: rgb(var(--color-theme-text)); font-size: 12px; }
.search-signal__copy small { color: rgb(var(--color-theme-text-secondary)); font-size: 10px; font-variant-numeric: tabular-nums; }
.search-signal button, .search-summary button { flex-shrink: 0; min-height: 36px; padding: 4px 8px; border-radius: 8px; color: rgb(var(--color-theme-accent)); font-size: 12px; }
.search-signal button:hover, .search-summary button:hover { background: rgb(var(--color-theme-accent) / .07); }
.search-signal__track { position: absolute; right: 0; bottom: 0; left: 0; height: 2px; background: rgb(var(--color-theme-accent) / .12); }
.search-signal__track i { display: block; height: 100%; background: rgb(var(--color-theme-accent)); transition: width 180ms ease; }
.search-source-count { font-size: 11px !important; font-variant-numeric: tabular-nums; }
.search-summary { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin: -4px 0 16px; color: rgb(var(--color-theme-text-secondary)); font-size: 12px; }
.search-more { display: flex; justify-content: center; margin-top: 64px; }
.search-more button { display: flex; align-items: center; gap: 12px; min-height: 44px; padding: 10px 22px; border: 1px solid rgb(var(--color-theme-border)); border-radius: 24px; color: rgb(var(--color-theme-text)); background: rgb(var(--color-theme-surface)); font-size: 13px; }
.search-more span { color: rgb(var(--color-theme-text-secondary)); font-size: 11px; }
.search-signal button:focus-visible, .search-summary button:focus-visible, .search-more button:focus-visible { outline: 2px solid rgb(var(--color-theme-accent)); outline-offset: 3px; }
.search-result-card { min-width: 0; }
@media (prefers-reduced-motion: reduce) { .search-signal__track i { transition: none; } }
</style>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageLayout from '@/components/PageLayout.vue'
import VideoCard from '@/components/VideoCard.vue'
import SearchResultFilter from '@/components/SearchResultFilter.vue'
import type { SearchFilterCategory, SearchFilterKey } from '@/components/SearchResultFilter.vue'
import { useUserStore } from '@/stores/user'
import { useToast } from '@/composables/useToast'
import { search } from '@/api/search'
import type { SearchResult } from '@/types'
import { Search, X, ChevronUp } from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const toast = useToast()

const keyword = ref('')
const results = ref<SearchResult[]>([])
const loading = ref(false)
const searched = ref(false)

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
    const query = keyword.value.trim()
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

// 计算筛选后的搜索结果数量
const resultCount = computed(() => displayResults.value.length)
const totalResultCount = computed(() => filteredResults.value.length)

function handleFilterChange(values: Record<SearchFilterKey, string>) {
  filterValues.value = values
}

async function handleSearch() {
  const query = keyword.value.trim()
  if (!query) {
    toast.warning('请输入搜索关键词')
    return
  }

  loading.value = true
  searched.value = true
  results.value = []
  // 重置筛选条件
  filterValues.value = {
    source: 'all',
    title: 'all',
    year: 'all',
    yearOrder: 'none',
  }

  try {
    await userStore.addSearchHistory(query)
    router.replace({ query: { keyword: query } })

    const res = await search({ q: query }) as any
    // 拦截器已解包 data，res 就是 { results: [...] }
    if (res.results && Array.isArray(res.results)) {
      results.value = res.results
    } else if (Array.isArray(res)) {
      results.value = res
    } else {
      results.value = []
    }
  } catch (error) {
    console.error('搜索失败:', error)
    toast.error('搜索失败，请稍后重试')
    results.value = []
  } finally {
    loading.value = false
  }
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
  results.value = []
  searched.value = false
  router.replace({ query: {} })
}

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

onMounted(async () => {
  await userStore.fetchSearchHistory()
  window.addEventListener('scroll', handleScroll)

  // 加载聚合搜索设置
  const savedAggregate = localStorage.getItem('defaultAggregateSearch')
  if (savedAggregate !== null) {
    aggregateSearch.value = JSON.parse(savedAggregate)
  }

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
    if (newKeyword && newKeyword !== keyword.value) {
      keyword.value = newKeyword as string
      handleSearch()
    }
  }
)
</script>

<template>
  <PageLayout>
    <div class="px-4 sm:px-10 py-4 sm:py-8 overflow-visible mb-10">
      <!-- 搜索框 -->
      <div class="mb-8">
        <form @submit.prevent="handleSearch" class="max-w-2xl mx-auto">
          <div class="relative">
            <Search class="absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-gray-400 dark:text-gray-500" />
            <input
              v-model="keyword"
              type="text"
              placeholder="搜索电影、电视剧、短剧..."
              autocomplete="off"
              class="w-full h-12 rounded-lg bg-gray-50/80 py-3 pl-10 pr-12 text-sm text-gray-700 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-400 focus:bg-white border border-gray-200/50 shadow-sm dark:bg-gray-800 dark:text-gray-300 dark:placeholder-gray-500 dark:focus:bg-gray-700 dark:border-gray-700"
              @keyup.enter="handleSearch"
            />
            <button
              v-if="keyword"
              type="button"
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
              <span v-if="loading" class="ml-2 inline-block align-middle">
                <span class="inline-block h-3 w-3 border-2 border-gray-300 border-t-blue-500 rounded-full animate-spin"></span>
              </span>
            </h2>
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
          <div v-if="results.length === 0 && loading" class="flex justify-center items-center h-40">
            <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500"></div>
          </div>

          <!-- 无结果 -->
          <div v-else-if="results.length === 0 && !loading" class="text-center text-gray-500 py-8 dark:text-gray-400">
            未找到相关结果
          </div>

          <!-- 筛选后无结果 -->
          <div v-else-if="displayResults.length === 0 && !loading" class="text-center text-gray-500 py-8 dark:text-gray-400">
            没有符合筛选条件的结果
          </div>

          <!-- 结果网格 -->
          <div
            v-else
            class="justify-start grid grid-cols-3 gap-x-2 gap-y-14 sm:gap-y-20 px-0 sm:px-2 sm:grid-cols-[repeat(auto-fill,_minmax(11rem,_1fr))] sm:gap-x-8"
          >
            <div v-for="item in displayResults" :key="`${item.source}-${item.id}`" class="w-full">
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
        @click="scrollToTop"
        @mouseenter="isHoveringBackToTop = true"
        @mouseleave="isHoveringBackToTop = false"
        class="relative w-14 h-14 backdrop-blur-xl rounded-full shadow-2xl transition-all duration-300 ease-out group hover:scale-110 hover:shadow-blue-500/50 focus:outline-none focus:ring-2 focus:ring-blue-400/50 border border-white/20 bg-gradient-to-br from-blue-500/20 via-cyan-500/20 to-purple-500/20"
      >
        <div class="absolute inset-1 bg-gradient-to-br from-blue-500/30 to-cyan-500/30 rounded-full backdrop-blur-sm flex items-center justify-center transition-all duration-300 group-hover:from-blue-400/40 group-hover:to-cyan-400/40">
          <!-- 悬停时显示进度百分比，否则显示箭头图标 -->
          <span
            v-if="isHoveringBackToTop"
            class="text-white font-semibold text-sm drop-shadow-lg transition-all duration-200"
          >
            {{ Math.round(scrollProgress) }}%
          </span>
          <ChevronUp
            v-else
            class="w-6 h-6 text-white/90 transition-all duration-300 group-hover:scale-110 group-hover:text-white drop-shadow-lg"
          />
        </div>
        <svg class="absolute inset-0 w-full h-full -rotate-90" viewBox="0 0 56 56">
          <circle cx="28" cy="28" r="25" fill="none" stroke="rgba(255,255,255,0.1)" stroke-width="2" />
          <circle
            cx="28" cy="28" r="25" fill="none" stroke="url(#progressGradient)" stroke-width="2"
            stroke-linecap="round"
            :stroke-dasharray="`${(scrollProgress / 100) * 157} 157`"
            class="transition-all duration-300 ease-out"
          />
          <defs>
            <linearGradient id="progressGradient" x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" stop-color="#3b82f6" />
              <stop offset="50%" stop-color="#06b6d4" />
              <stop offset="100%" stop-color="#8b5cf6" />
            </linearGradient>
          </defs>
        </svg>
      </button>
    </div>
  </PageLayout>
</template>

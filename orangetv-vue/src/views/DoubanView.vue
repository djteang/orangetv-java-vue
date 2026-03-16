<script setup lang="ts">
/* eslint-disable no-console */
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import PageLayout from '@/components/PageLayout.vue'
import VideoCard from '@/components/VideoCard.vue'
import DoubanSelector from '@/components/DoubanSelector.vue'
import { getDoubanCategories, getDoubanRecommends } from '@/api/douban'
import { getBangumiCalendarData } from '@/api/bangumi'
import type { DoubanItem } from '@/types'

const route = useRoute()

const items = ref<DoubanItem[]>([])
const loading = ref(true)
const isLoadingMore = ref(false)
const currentPage = ref(0)
const hasMore = ref(true)
const selectorsReady = ref(false)

const loadMoreRef = ref<HTMLDivElement | null>(null)
let observer: IntersectionObserver | null = null
let debounceTimer: ReturnType<typeof setTimeout> | null = null
let selectorsTimer: ReturnType<typeof setTimeout> | null = null

const type = computed(() => (route.query.type as string) || 'movie')

// 一级选择器默认值（与 page.tsx 一致）
function getDefaultPrimary(t: string): string {
  if (t === 'movie') return '热门'
  if (t === 'tv' || t === 'show') return '最近热门'
  if (t === 'anime') return '每日放送'
  return ''
}

// 二级选择器默认值
function getDefaultSecondary(t: string): string {
  if (t === 'movie') return '全部'
  if (t === 'tv') return 'tv'
  if (t === 'show') return 'show'
  return '全部'
}

const primarySelection = ref(getDefaultPrimary(type.value))
const secondarySelection = ref(getDefaultSecondary(type.value))
const multiLevelValues = ref<Record<string, string>>({
  type: 'all', region: 'all', year: 'all',
  platform: 'all', label: 'all', sort: 'T',
})
const selectedWeekday = ref('')

// 骨架屏数据
const skeletonData = Array.from({ length: 25 }, (_, i) => i)

const pageTitle = computed(() => {
  const map: Record<string, string> = { movie: '电影', tv: '电视剧', anime: '动漫', show: '综艺' }
  return map[type.value] || '分类'
})

const pageDescription = computed(() => {
  if (type.value === 'anime' && primarySelection.value === '每日放送') {
    return '来自 Bangumi 番组计划的精选内容'
  }
  return '来自豆瓣的精选内容'
})

// 生成 API 请求参数（getDoubanCategories 使用）
function getRequestParams(pageStart: number) {
  if (type.value === 'tv' || type.value === 'show') {
    return {
      kind: 'tv' as const,
      category: type.value,
      type: secondarySelection.value,
      pageLimit: 25,
      pageStart,
    }
  }
  return {
    kind: type.value as 'tv' | 'movie',
    category: primarySelection.value,
    type: secondarySelection.value,
    pageLimit: 25,
    pageStart,
  }
}

// 加载初始数据（与 page.tsx loadInitialData 一致）
async function loadInitialData() {
  // 快照，用于防止过期数据覆盖
  const snap = {
    type: type.value,
    primarySelection: primarySelection.value,
    secondarySelection: secondarySelection.value,
    multiLevelValues: JSON.stringify(multiLevelValues.value),
    selectedWeekday: selectedWeekday.value,
    currentPage: 0,
  }

  try {
    loading.value = true
    items.value = []
    currentPage.value = 0
    hasMore.value = true
    isLoadingMore.value = false

    let list: DoubanItem[]

    if (type.value === 'anime' && primarySelection.value === '每日放送') {
      const calendarData = await getBangumiCalendarData()
      const weekdayData = calendarData.find(d => d.weekday.en === selectedWeekday.value)
      if (weekdayData) {
        list = weekdayData.items.map(item => ({
          id: item.id?.toString() || '',
          title: item.name_cn || item.name,
          poster: item.images?.large || item.images?.common || item.images?.medium || item.images?.small || item.images?.grid || '',
          rate: item.rating?.score?.toFixed(1) || '',
          year: item.air_date?.split('-')?.[0] || '',
        }))
      } else {
        throw new Error('没有找到对应的日期')
      }
    } else if (type.value === 'anime') {
      const data = await getDoubanRecommends({
        kind: primarySelection.value === '番剧' ? 'tv' : 'movie',
        pageLimit: 25,
        pageStart: 0,
        category: '动画',
        format: primarySelection.value === '番剧' ? '电视剧' : '',
        region: multiLevelValues.value.region,
        year: multiLevelValues.value.year,
        platform: multiLevelValues.value.platform,
        sort: multiLevelValues.value.sort,
        label: multiLevelValues.value.label,
      })
      if (data.code !== 200) throw new Error(data.message)
      list = data.list
    } else if (primarySelection.value === '全部') {
      const data = await getDoubanRecommends({
        kind: type.value === 'show' ? 'tv' : (type.value as 'tv' | 'movie'),
        pageLimit: 25,
        pageStart: 0,
        category: multiLevelValues.value.type,
        format: type.value === 'show' ? '综艺' : type.value === 'tv' ? '电视剧' : '',
        region: multiLevelValues.value.region,
        year: multiLevelValues.value.year,
        platform: multiLevelValues.value.platform,
        sort: multiLevelValues.value.sort,
        label: multiLevelValues.value.label,
      })
      if (data.code !== 200) throw new Error(data.message)
      list = data.list
    } else {
      const data = await getDoubanCategories(getRequestParams(0))
      if (data.code !== 200) throw new Error(data.message)
      list = data.list
    }

    // 检查参数快照是否仍然一致
    const current = {
      type: type.value,
      primarySelection: primarySelection.value,
      secondarySelection: secondarySelection.value,
      multiLevelValues: JSON.stringify(multiLevelValues.value),
      selectedWeekday: selectedWeekday.value,
      currentPage: 0,
    }
    const isMatch = JSON.stringify(snap) === JSON.stringify(current)

    if (isMatch) {
      items.value = list
      hasMore.value = list.length !== 0
      loading.value = false
    } else {
      console.log('参数不一致，不执行任何操作，避免设置过期数据')
    }
  } catch (err) {
    console.error(err)
    loading.value = false
  }
}

// 加载更多
async function loadMore() {
  if (isLoadingMore.value || !hasMore.value) return

  const page = currentPage.value + 1
  const snap = {
    type: type.value,
    primarySelection: primarySelection.value,
    secondarySelection: secondarySelection.value,
    multiLevelValues: JSON.stringify(multiLevelValues.value),
    selectedWeekday: selectedWeekday.value,
    currentPage: page,
  }

  isLoadingMore.value = true
  currentPage.value = page

  try {
    let list: DoubanItem[]

    if (type.value === 'anime' && primarySelection.value === '每日放送') {
      list = []
    } else if (type.value === 'anime') {
      const data = await getDoubanRecommends({
        kind: primarySelection.value === '番剧' ? 'tv' : 'movie',
        pageLimit: 25,
        pageStart: page * 25,
        category: '动画',
        format: primarySelection.value === '番剧' ? '电视剧' : '',
        region: multiLevelValues.value.region,
        year: multiLevelValues.value.year,
        platform: multiLevelValues.value.platform,
        sort: multiLevelValues.value.sort,
        label: multiLevelValues.value.label,
      })
      if (data.code !== 200) throw new Error(data.message)
      list = data.list
    } else if (primarySelection.value === '全部') {
      const data = await getDoubanRecommends({
        kind: type.value === 'show' ? 'tv' : (type.value as 'tv' | 'movie'),
        pageLimit: 25,
        pageStart: page * 25,
        category: multiLevelValues.value.type,
        format: type.value === 'show' ? '综艺' : type.value === 'tv' ? '电视剧' : '',
        region: multiLevelValues.value.region,
        year: multiLevelValues.value.year,
        platform: multiLevelValues.value.platform,
        sort: multiLevelValues.value.sort,
        label: multiLevelValues.value.label,
      })
      if (data.code !== 200) throw new Error(data.message)
      list = data.list
    } else {
      const data = await getDoubanCategories(getRequestParams(page * 25))
      if (data.code !== 200) throw new Error(data.message)
      list = data.list
    }

    const current = {
      type: type.value,
      primarySelection: primarySelection.value,
      secondarySelection: secondarySelection.value,
      multiLevelValues: JSON.stringify(multiLevelValues.value),
      selectedWeekday: selectedWeekday.value,
      currentPage: page,
    }
    if (JSON.stringify(snap) === JSON.stringify(current)) {
      items.value = [...items.value, ...list]
      hasMore.value = list.length !== 0
    } else {
      console.log('参数不一致，不执行任何操作，避免设置过期数据')
    }
  } catch (err) {
    console.error(err)
  } finally {
    isLoadingMore.value = false
  }
}

// 设置滚动监听
function setupObserver() {
  if (observer) observer.disconnect()
  if (!loadMoreRef.value || !hasMore.value) return
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0].isIntersecting && hasMore.value && !isLoadingMore.value && !loading.value) {
        loadMore()
      }
    },
    { threshold: 0.1 }
  )
  observer.observe(loadMoreRef.value)
}

// 防抖触发加载
function triggerLoad() {
  if (!selectorsReady.value) return
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    loadInitialData().then(() => setupObserver())
  }, 100)
}

// type 变化时重置所有状态
watch(type, (newType) => {
  selectorsReady.value = false
  loading.value = true
  if (observer) observer.disconnect()

  primarySelection.value = getDefaultPrimary(newType)
  secondarySelection.value = getDefaultSecondary(newType)
  multiLevelValues.value = { type: 'all', region: 'all', year: 'all', platform: 'all', label: 'all', sort: 'T' }

  if (selectorsTimer) clearTimeout(selectorsTimer)
  selectorsTimer = setTimeout(() => {
    selectorsReady.value = true
  }, 50)
})

// 参数变化时触发防抖加载
watch(
  [() => selectorsReady.value, primarySelection, secondarySelection, multiLevelValues, selectedWeekday],
  () => triggerLoad(),
  { deep: true }
)

// 处理选择器事件
function handlePrimaryChange(value: string) {
  if (value === primarySelection.value) return
  loading.value = true
  currentPage.value = 0
  items.value = []
  hasMore.value = true
  isLoadingMore.value = false
  multiLevelValues.value = { type: 'all', region: 'all', year: 'all', platform: 'all', label: 'all', sort: 'T' }

  if ((type.value === 'tv' || type.value === 'show') && value === '最近热门') {
    primarySelection.value = value
    secondarySelection.value = type.value === 'tv' ? 'tv' : 'show'
  } else {
    primarySelection.value = value
  }
}

function handleSecondaryChange(value: string) {
  if (value === secondarySelection.value) return
  loading.value = true
  currentPage.value = 0
  items.value = []
  hasMore.value = true
  isLoadingMore.value = false
  secondarySelection.value = value
}

function handleMultiLevelChange(values: Record<string, string>) {
  if (JSON.stringify(values) === JSON.stringify(multiLevelValues.value)) return
  loading.value = true
  currentPage.value = 0
  items.value = []
  hasMore.value = true
  isLoadingMore.value = false
  multiLevelValues.value = values
}

function handleWeekdayChange(weekday: string) {
  selectedWeekday.value = weekday
}

onMounted(() => {
  if (selectorsTimer) clearTimeout(selectorsTimer)
  selectorsTimer = setTimeout(() => {
    selectorsReady.value = true
  }, 50)
})

onUnmounted(() => {
  if (observer) observer.disconnect()
  if (debounceTimer) clearTimeout(debounceTimer)
  if (selectorsTimer) clearTimeout(selectorsTimer)
})
</script>

<template>
  <PageLayout>
    <div class="px-4 sm:px-10 py-4 sm:py-8 overflow-visible">
      <!-- 页面标题和选择器 -->
      <div class="mb-6 sm:mb-8 space-y-4 sm:space-y-6">
        <div>
          <h1 class="text-2xl sm:text-3xl font-bold text-gray-800 mb-1 sm:mb-2 dark:text-gray-200">
            {{ pageTitle }}
          </h1>
          <p class="text-sm sm:text-base text-gray-600 dark:text-gray-400">
            {{ pageDescription }}
          </p>
        </div>

        <!-- 选择器容器 -->
        <div class="bg-white/60 dark:bg-gray-800/40 rounded-2xl p-4 sm:p-6 border border-gray-200/30 dark:border-gray-700/30 backdrop-blur-sm">
          <DoubanSelector
            :type="type as 'movie' | 'tv' | 'show' | 'anime'"
            :primary-selection="primarySelection"
            :secondary-selection="secondarySelection"
            @primary-change="handlePrimaryChange"
            @secondary-change="handleSecondaryChange"
            @multi-level-change="handleMultiLevelChange"
            @weekday-change="handleWeekdayChange"
          />
        </div>
      </div>

      <!-- 内容展示区域 -->
      <div class="max-w-[95%] mx-auto mt-8 overflow-visible">
        <div class="justify-start grid grid-cols-3 gap-x-2 gap-y-12 px-0 sm:px-2 sm:grid-cols-[repeat(auto-fill,minmax(160px,1fr))] sm:gap-x-8 sm:gap-y-20">
          <template v-if="loading || !selectorsReady">
            <div v-for="i in skeletonData" :key="i" class="w-full">
              <div class="relative aspect-[2/3] w-full overflow-hidden rounded-lg bg-gray-200 animate-pulse dark:bg-gray-800">
                <div class="absolute inset-0 bg-gray-300 dark:bg-gray-700"></div>
              </div>
              <div class="mt-2 h-4 bg-gray-200 rounded animate-pulse dark:bg-gray-800"></div>
            </div>
          </template>
          <template v-else>
            <div v-for="(item, index) in items" :key="`${item.title}-${index}`" class="w-full">
              <VideoCard
                from="douban"
                :title="item.title"
                :poster="item.poster"
                :douban-id="Number(item.id)"
                :rate="item.rate"
                :year="item.year"
                :type="type === 'movie' ? 'movie' : ''"
              />
            </div>
          </template>
        </div>

        <div
          v-if="hasMore && !loading"
          :ref="(el) => { if (el && (el as HTMLElement).offsetParent !== null) loadMoreRef = el as HTMLDivElement }"
          class="flex justify-center mt-12 py-8"
        >
          <div v-if="isLoadingMore" class="flex items-center gap-2">
            <div class="animate-spin rounded-full h-6 w-6 border-b-2 border-blue-500"></div>
            <span class="text-gray-600 dark:text-gray-400">加载中...</span>
          </div>
        </div>

        <div v-if="!hasMore && items.length > 0" class="text-center text-gray-500 dark:text-gray-400 py-8">
          已加载全部内容
        </div>

        <div v-if="!loading && items.length === 0" class="text-center text-gray-500 dark:text-gray-400 py-8">
          暂无相关内容
        </div>
      </div>
    </div>
  </PageLayout>
</template>

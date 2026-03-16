<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import PageLayout from '@/components/PageLayout.vue'
import VideoCard from '@/components/VideoCard.vue'
import { getShortDramaLatest, getShortDramaList, getShortDramaCategories } from '@/api/shortdrama'
import type { ShortDramaItem, ShortDramaCategory } from '@/api/shortdrama'

const items = ref<ShortDramaItem[]>([])
const loading = ref(false)
const isLoadingMore = ref(false)
const currentPage = ref(1)
const hasMore = ref(true)
const totalPages = ref(1)
const selectedCategory = ref('0')
const categories = ref<ShortDramaCategory[]>([])

const loadMoreRef = ref<HTMLDivElement | null>(null)
let observer: IntersectionObserver | null = null

async function fetchCategories() {
  try {
    const res = await getShortDramaCategories()
    if (Array.isArray(res)) {
      categories.value = res
    }
  } catch {
    // ignore
  }
}

async function loadInitialData() {
  loading.value = true
  items.value = []
  currentPage.value = 1
  hasMore.value = true

  try {
    if (selectedCategory.value === '0') {
      const data = await getShortDramaLatest({ page: '1' })
      items.value = Array.isArray(data) ? data : []
      totalPages.value = 10
    } else {
      const response = await getShortDramaList({
        categoryId: selectedCategory.value,
        page: '1',
      })
      items.value = Array.isArray(response?.list) ? response.list : []
      totalPages.value = response?.totalPages || 1
    }
    hasMore.value = items.value.length > 0 && currentPage.value < totalPages.value
  } catch (error) {
    console.error('加载短剧数据失败:', error)
  } finally {
    loading.value = false
    await nextTick()
    setupObserver()
  }
}

async function loadMore() {
  if (isLoadingMore.value || !hasMore.value) return
  isLoadingMore.value = true
  currentPage.value++

  try {
    let data: ShortDramaItem[] = []
    if (selectedCategory.value === '0') {
      const res = await getShortDramaLatest({ page: currentPage.value.toString() })
      data = Array.isArray(res) ? res : []
    } else {
      const response = await getShortDramaList({
        categoryId: selectedCategory.value,
        page: currentPage.value.toString(),
      })
      data = Array.isArray(response?.list) ? response.list : []
    }
    items.value = [...items.value, ...data]
    hasMore.value = data.length > 0 && currentPage.value < totalPages.value
  } catch (error) {
    console.error('加载更多短剧数据失败:', error)
  } finally {
    isLoadingMore.value = false
  }
}

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

function selectCategory(categoryId: string) {
  if (categoryId === selectedCategory.value) return
  selectedCategory.value = categoryId
}

watch(selectedCategory, () => {
  loadInitialData()
})

onMounted(() => {
  fetchCategories()
  loadInitialData()
})

onUnmounted(() => {
  if (observer) observer.disconnect()
})
</script>

<template>
  <PageLayout>
    <div class="px-4 sm:px-10 py-4 sm:py-8 overflow-visible">
      <!-- 页面标题和选择器 -->
      <div class="mb-6 sm:mb-8 space-y-4 sm:space-y-6">
        <div>
          <h1 class="text-2xl sm:text-3xl font-bold text-gray-800 mb-1 sm:mb-2 dark:text-gray-200">
            短剧
          </h1>
          <p class="text-sm sm:text-base text-gray-600 dark:text-gray-400">
            精彩短剧，尽在掌握
          </p>
        </div>

        <!-- 分类选择器 -->
        <div class="bg-white/60 dark:bg-gray-800/40 rounded-2xl p-4 sm:p-6 border border-gray-200/30 dark:border-gray-700/30 backdrop-blur-sm">
          <div class="flex flex-wrap gap-2">
            <button
              @click="selectCategory('0')"
              :class="[
                'px-4 py-2 rounded-full text-sm font-medium whitespace-nowrap transition-colors',
                selectedCategory === '0'
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-700',
              ]"
            >
              全部
            </button>
            <button
              v-for="cat in categories"
              :key="cat.id"
              @click="selectCategory(cat.id)"
              :class="[
                'px-4 py-2 rounded-full text-sm font-medium whitespace-nowrap transition-colors',
                selectedCategory === cat.id
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-700',
              ]"
            >
              {{ cat.name }}
            </button>
          </div>
        </div>
      </div>

      <!-- 内容展示区域 -->
      <div class="max-w-[95%] mx-auto mt-8 overflow-visible">
        <!-- 骨架屏 -->
        <div
          v-if="loading"
          class="justify-start grid grid-cols-3 gap-x-2 gap-y-12 px-0 sm:px-2 sm:grid-cols-[repeat(auto-fill,minmax(160px,1fr))] sm:gap-x-8 sm:gap-y-20"
        >
          <div v-for="i in 25" :key="i" class="w-full">
            <div class="relative aspect-[2/3] w-full overflow-hidden rounded-lg bg-gray-200 animate-pulse dark:bg-gray-800">
              <div class="absolute inset-0 bg-gray-300 dark:bg-gray-700"></div>
            </div>
            <div class="mt-2 h-4 bg-gray-200 rounded animate-pulse dark:bg-gray-800"></div>
          </div>
        </div>

        <!-- 内容网格 -->
        <div
          v-else-if="items.length > 0"
          class="justify-start grid grid-cols-3 gap-x-2 gap-y-12 px-0 sm:px-2 sm:grid-cols-[repeat(auto-fill,minmax(160px,1fr))] sm:gap-x-8 sm:gap-y-20"
        >
          <div v-for="(item, index) in items" :key="`${item.name}-${item.id}-${index}`" class="w-full">
            <VideoCard
              from="shortdrama"
              :id="(item.vod_id || item.id).toString()"
              :title="item.name"
              :poster="item.cover"
              :rate="item.score ? item.score.toString() : ''"
              :year="item.update_time ? new Date(item.update_time).getFullYear().toString() : ''"
              type="tv"
              source="shortdrama"
              source-name="短剧"
              :episodes="item.total_episodes ? parseInt(item.total_episodes) || 1 : 1"
            />
          </div>
        </div>

        <!-- 空状态 -->
        <div v-else class="text-center text-gray-500 dark:text-gray-400 py-8">
          暂无相关内容
        </div>

        <!-- 加载更多指示器 -->
        <div v-if="hasMore && !loading" ref="loadMoreRef" class="flex justify-center mt-12 py-8">
          <div v-if="isLoadingMore" class="flex items-center gap-2">
            <div class="animate-spin rounded-full h-6 w-6 border-b-2 border-blue-500"></div>
            <span class="text-gray-600 dark:text-gray-400">加载中...</span>
          </div>
        </div>

        <!-- 已加载全部 -->
        <div v-if="!hasMore && items.length > 0" class="text-center text-gray-500 dark:text-gray-400 py-8">
          已加载全部内容
        </div>
      </div>
    </div>
  </PageLayout>
</template>

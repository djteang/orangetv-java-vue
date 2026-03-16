<script setup lang="ts">
import { ref, computed, watch, nextTick, reactive } from 'vue'
import { useRouter } from 'vue-router'
import type { SearchResult } from '@/types'

interface VideoInfo {
  quality: string
  loadSpeed: string
  pingTime: number
  hasError?: boolean
}

const props = withDefaults(
  defineProps<{
    totalEpisodes: number
    episodesTitles: string[]
    episodesPerPage?: number
    modelValue?: number
    currentSource?: string
    currentId?: string
    videoTitle?: string
    availableSources?: SearchResult[]
    sourceSearchLoading?: boolean
    sourceSearchError?: string | null
  }>(),
  {
    episodesPerPage: 50,
    modelValue: 1,
    availableSources: () => [],
    sourceSearchLoading: false,
    sourceSearchError: null,
  }
)

const emit = defineEmits<{
  'update:modelValue': [episodeNumber: number]
  sourceChange: [source: string, id: string, title: string]
}>()

const router = useRouter()

// Tab state
const activeTab = ref<'episodes' | 'sources'>(props.totalEpisodes > 1 ? 'episodes' : 'sources')

// Pagination
const pageCount = computed(() => Math.ceil(props.totalEpisodes / props.episodesPerPage))
const initialPage = Math.floor((props.modelValue - 1) / props.episodesPerPage)
const currentPage = ref(initialPage)
const descending = ref(false)

const displayPage = computed(() => {
  if (descending.value) return pageCount.value - 1 - currentPage.value
  return currentPage.value
})

const categoriesAsc = computed(() =>
  Array.from({ length: pageCount.value }, (_, i) => {
    const start = i * props.episodesPerPage + 1
    const end = Math.min(start + props.episodesPerPage - 1, props.totalEpisodes)
    return { start, end }
  })
)

const categories = computed(() => {
  if (descending.value) {
    return [...categoriesAsc.value].reverse().map(({ start, end }) => `${end}-${start}`)
  }
  return categoriesAsc.value.map(({ start, end }) => `${start}-${end}`)
})

const currentStart = computed(() => currentPage.value * props.episodesPerPage + 1)
const currentEnd = computed(() =>
  Math.min(currentStart.value + props.episodesPerPage - 1, props.totalEpisodes)
)

const currentEpisodes = computed(() => {
  const len = currentEnd.value - currentStart.value + 1
  return Array.from({ length: len }, (_, i) =>
    descending.value ? currentEnd.value - i : currentStart.value + i
  )
})

// Scroll refs
const categoryContainerRef = ref<HTMLDivElement | null>(null)
const buttonRefs = ref<(HTMLButtonElement | null)[]>([])

function setButtonRef(el: unknown, idx: number) {
  buttonRefs.value[idx] = el as HTMLButtonElement | null
}

watch(displayPage, async () => {
  await nextTick()
  const btn = buttonRefs.value[displayPage.value]
  const container = categoryContainerRef.value
  if (btn && container) {
    const containerRect = container.getBoundingClientRect()
    const btnRect = btn.getBoundingClientRect()
    const scrollLeft = container.scrollLeft
    const btnLeft = btnRect.left - containerRect.left + scrollLeft
    const btnWidth = btnRect.width
    const containerWidth = containerRect.width
    const targetScrollLeft = btnLeft - (containerWidth - btnWidth) / 2
    container.scrollTo({ left: targetScrollLeft, behavior: 'smooth' })
  }
})

// ===== 测速逻辑 =====
const videoInfoMap = reactive<Map<string, VideoInfo>>(new Map())
const attemptedSources = reactive<Set<string>>(new Set())

async function getVideoInfoFromM3u8(url: string): Promise<VideoInfo> {
  const startTime = performance.now()

  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), 8000)

  try {
    const response = await fetch(url, {
      method: 'GET',
      signal: controller.signal,
      mode: 'cors',
    })
    clearTimeout(timeoutId)

    const pingTime = Math.round(performance.now() - startTime)
    // const contentLength = response.headers.get('content-length')
    const text = await response.text()

    const downloadTime = performance.now() - startTime
    const bytes = text.length
    const speedBps = (bytes * 8) / (downloadTime / 1000)
    let loadSpeed: string
    if (speedBps > 1000000) {
      loadSpeed = `${(speedBps / 1000000).toFixed(1)} Mbps`
    } else if (speedBps > 1000) {
      loadSpeed = `${(speedBps / 1000).toFixed(0)} Kbps`
    } else {
      loadSpeed = `${Math.round(speedBps)} bps`
    }

    // 解析 m3u8 获取分辨率
    let quality = '未知'
    const resolutionMatch = text.match(/RESOLUTION=(\d+)x(\d+)/)
    if (resolutionMatch) {
      const height = parseInt(resolutionMatch[2])
      if (height >= 2160) quality = '4K'
      else if (height >= 1440) quality = '2K'
      else if (height >= 1080) quality = '1080p'
      else if (height >= 720) quality = '720p'
      else if (height >= 480) quality = '480p'
      else quality = `${height}p`
    } else if (text.includes('#EXTINF')) {
      // 非 master playlist，尝试从 bandwidth 判断
      const bandwidthMatch = text.match(/BANDWIDTH=(\d+)/)
      if (bandwidthMatch) {
        const bw = parseInt(bandwidthMatch[1])
        if (bw > 8000000) quality = '4K'
        else if (bw > 4000000) quality = '1080p'
        else if (bw > 2000000) quality = '720p'
        else quality = '480p'
      } else {
        // 估算：如果 content-length 大，说明分辨率可能较高
        quality = 'SD'
      }
    }

    return { quality, loadSpeed, pingTime }
  } catch (err) {
    clearTimeout(timeoutId)
    const pingTime = Math.round(performance.now() - startTime)

    const errMsg = err instanceof Error ? err.message : String(err)
    const isRestricted =
      errMsg.includes('CORS') ||
      errMsg.includes('Forbidden') ||
      errMsg.includes('NetworkError') ||
      errMsg.includes('Failed to fetch') ||
      errMsg.includes('abort')

    return {
      quality: isRestricted ? '受限' : '未知',
      loadSpeed: '未知',
      pingTime,
      hasError: true,
    }
  }
}

async function testSource(source: SearchResult) {
  const sourceKey = `${source.source}-${source.id}`
  if (attemptedSources.has(sourceKey)) return
  if (!source.episodes || source.episodes.length === 0) return

  attemptedSources.add(sourceKey)

  const episodeUrl = source.episodes.length > 1 ? source.episodes[1] : source.episodes[0]
  try {
    const info = await getVideoInfoFromM3u8(episodeUrl)
    videoInfoMap.set(sourceKey, info)
  } catch {
    videoInfoMap.set(sourceKey, {
      quality: '未知',
      loadSpeed: '未知',
      pingTime: 0,
      hasError: true,
    })
  }
}

// 当切换到换源 tab 且有源数据时，批量测速
watch(
  [activeTab, () => props.availableSources],
  async () => {
    if (activeTab.value !== 'sources' || props.availableSources.length === 0) return

    const pending = props.availableSources.filter((s) => {
      const key = `${s.source}-${s.id}`
      return !attemptedSources.has(key)
    })

    if (pending.length === 0) return

    // 分批并发测速（每批3个）
    const batchSize = 3
    for (let i = 0; i < pending.length; i += batchSize) {
      const batch = pending.slice(i, i + batchSize)
      await Promise.all(batch.map(testSource))
    }
  },
  { immediate: true }
)

function getQualityColor(quality: string): string {
  if (['4K', '2K'].includes(quality))
    return 'text-purple-600 dark:text-purple-400'
  if (['1080p', '720p'].includes(quality))
    return 'text-blue-600 dark:text-blue-400'
  return 'text-yellow-600 dark:text-yellow-400'
}

// ===== Handlers =====
function handleCategoryClick(index: number) {
  if (descending.value) {
    currentPage.value = pageCount.value - 1 - index
  } else {
    currentPage.value = index
  }
}

function handleEpisodeClick(episodeNumber: number) {
  emit('update:modelValue', episodeNumber - 1)
}

function handleSourceClick(source: SearchResult) {
  emit('sourceChange', source.source, source.id, source.title)
}

function getEpisodeLabel(episodeNumber: number): string {
  const title = props.episodesTitles?.[episodeNumber - 1]
  if (!title) return String(episodeNumber)
  const match = title.match(/(?:第)?(\d+)(?:集|话)/)
  if (match) return match[1]
  return title
}

function proxyImage(url: string): string {
  if (!url) return ''
  return `/api/image-proxy?url=${encodeURIComponent(url)}`
}

function goToSearch() {
  if (props.videoTitle) {
    router.push(`/search?q=${encodeURIComponent(props.videoTitle)}`)
  }
}
</script>

<template>
  <div
    class="md:ml-2 px-4 py-0 h-full rounded-xl bg-black/10 dark:bg-white/5 flex flex-col border border-white/0 dark:border-white/30 overflow-hidden"
  >
    <!-- Tab bar -->
    <div class="flex mb-1 -mx-6 flex-shrink-0">
      <div
        v-if="totalEpisodes > 1"
        @click="activeTab = 'episodes'"
        :class="[
          'flex-1 py-3 px-6 text-center cursor-pointer transition-all duration-200 font-medium',
          activeTab === 'episodes'
            ? 'text-blue-600 dark:text-blue-400'
            : 'text-gray-700 hover:text-blue-600 bg-black/5 dark:bg-white/5 dark:text-gray-300 dark:hover:text-blue-400 hover:bg-black/3 dark:hover:bg-white/3',
        ]"
      >
        选集
      </div>
      <div
        @click="activeTab = 'sources'"
        :class="[
          'flex-1 py-3 px-6 text-center cursor-pointer transition-all duration-200 font-medium',
          activeTab === 'sources'
            ? 'text-blue-600 dark:text-blue-400'
            : 'text-gray-700 hover:text-blue-600 bg-black/5 dark:bg-white/5 dark:text-gray-300 dark:hover:text-blue-400 hover:bg-black/3 dark:hover:bg-white/3',
        ]"
      >
        换源
      </div>
    </div>

    <!-- Episodes tab -->
    <template v-if="activeTab === 'episodes'">
      <div
        class="flex items-center gap-4 mb-4 border-b border-gray-300 dark:border-gray-700 -mx-6 px-6 flex-shrink-0"
      >
        <div class="flex-1 overflow-x-auto" ref="categoryContainerRef">
          <div class="flex gap-2 min-w-max">
            <button
              v-for="(label, idx) in categories"
              :key="label"
              :ref="(el) => setButtonRef(el, idx)"
              @click="handleCategoryClick(idx)"
              :class="[
                'w-20 relative py-2 text-sm font-medium transition-colors whitespace-nowrap flex-shrink-0 text-center',
                idx === displayPage
                  ? 'text-blue-500 dark:text-blue-400'
                  : 'text-gray-700 hover:text-blue-600 dark:text-gray-300 dark:hover:text-blue-400',
              ]"
            >
              {{ label }}
              <div
                v-if="idx === displayPage"
                class="absolute bottom-0 left-0 right-0 h-0.5 bg-blue-500 dark:bg-blue-400"
              ></div>
            </button>
          </div>
        </div>
        <button
          class="flex-shrink-0 w-8 h-8 rounded-md flex items-center justify-center text-gray-700 hover:text-blue-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:text-blue-400 dark:hover:bg-white/20 transition-colors transform translate-y-[-4px]"
          @click="descending = !descending"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M7 16V4m0 0L3 8m4-4l4 4m6 0v12m0 0l4-4m-4 4l-4-4"
            />
          </svg>
        </button>
      </div>

      <div class="flex flex-wrap gap-3 overflow-y-auto flex-1 content-start pb-4">
        <button
          v-for="episodeNumber in currentEpisodes"
          :key="episodeNumber"
          @click="handleEpisodeClick(episodeNumber)"
          :class="[
            'h-10 min-w-10 px-3 py-2 flex items-center justify-center text-sm font-medium rounded-md transition-all duration-200 whitespace-nowrap font-mono',
            episodeNumber === modelValue
              ? 'bg-blue-500 text-white shadow-lg shadow-blue-500/25 dark:bg-blue-600'
              : 'bg-gray-200 text-gray-700 hover:bg-gray-300 hover:scale-105 dark:bg-white/10 dark:text-gray-300 dark:hover:bg-white/20',
          ]"
        >
          {{ getEpisodeLabel(episodeNumber) }}
        </button>
      </div>
    </template>

    <!-- Sources tab -->
    <template v-if="activeTab === 'sources'">
      <div class="flex flex-col h-full mt-4">
        <div v-if="sourceSearchLoading" class="flex items-center justify-center py-8">
          <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500"></div>
          <span class="ml-2 text-sm text-gray-600 dark:text-gray-300">搜索中...</span>
        </div>

        <div v-else-if="sourceSearchError" class="flex items-center justify-center py-8">
          <div class="text-center">
            <div class="text-red-500 text-2xl mb-2">⚠️</div>
            <p class="text-sm text-red-600 dark:text-red-400">{{ sourceSearchError }}</p>
          </div>
        </div>

        <div
          v-else-if="availableSources.length === 0"
          class="flex items-center justify-center py-8"
        >
          <div class="text-center">
            <div class="text-gray-400 text-2xl mb-2">📺</div>
            <p class="text-sm text-gray-600 dark:text-gray-300">暂无可用的换源</p>
          </div>
        </div>

        <template v-else>
          <div class="flex-1 overflow-y-auto space-y-2 pb-20">
            <div
              v-for="source in [...availableSources].sort((a, b) => {
                const aIsCurrent =
                  a.source?.toString() === currentSource?.toString() &&
                  a.id?.toString() === currentId?.toString()
                const bIsCurrent =
                  b.source?.toString() === currentSource?.toString() &&
                  b.id?.toString() === currentId?.toString()
                if (aIsCurrent && !bIsCurrent) return -1
                if (!aIsCurrent && bIsCurrent) return 1
                return 0
              })"
              :key="`${source.source}-${source.id}`"
              @click="
                !(
                  source.source?.toString() === currentSource?.toString() &&
                  source.id?.toString() === currentId?.toString()
                ) && handleSourceClick(source)
              "
              :class="[
                'flex items-start gap-3 px-2 py-3 rounded-lg transition-all select-none duration-200 relative',
                source.source?.toString() === currentSource?.toString() &&
                source.id?.toString() === currentId?.toString()
                  ? 'bg-blue-500/10 dark:bg-blue-500/20 border-blue-500/30 border'
                  : 'hover:bg-gray-200/50 dark:hover:bg-white/10 hover:scale-[1.02] cursor-pointer',
              ]"
            >
              <!-- Poster -->
              <div
                class="flex-shrink-0 w-12 h-20 bg-gray-300 dark:bg-gray-600 rounded overflow-hidden"
              >
                <img
                  v-if="source.poster"
                  :src="proxyImage(source.poster)"
                  :alt="source.title"
                  class="w-full h-full object-cover"
                  @error="($event.target as HTMLImageElement).style.display = 'none'"
                />
              </div>

              <!-- Info -->
              <div class="flex-1 min-w-0 flex flex-col justify-between h-20">
                <!-- 标题 + 分辨率 -->
                <div class="flex items-start justify-between gap-3 h-6">
                  <h3
                    class="font-medium text-base truncate text-gray-900 dark:text-gray-100 leading-none"
                  >
                    {{ source.title }}
                  </h3>
                  <!-- 分辨率标签 -->
                  <template v-if="videoInfoMap.get(`${source.source}-${source.id}`)">
                    <div
                      v-if="videoInfoMap.get(`${source.source}-${source.id}`)!.hasError"
                      class="bg-gray-500/10 dark:bg-gray-400/20 text-red-600 dark:text-red-400 px-1.5 py-0 rounded text-xs flex-shrink-0 min-w-[50px] text-center"
                    >
                      检测失败
                    </div>
                    <div
                      v-else-if="videoInfoMap.get(`${source.source}-${source.id}`)!.quality !== '未知'"
                      :class="[
                        'bg-gray-500/10 dark:bg-gray-400/20 px-1.5 py-0 rounded text-xs flex-shrink-0 min-w-[50px] text-center',
                        getQualityColor(videoInfoMap.get(`${source.source}-${source.id}`)!.quality),
                      ]"
                    >
                      {{ videoInfoMap.get(`${source.source}-${source.id}`)!.quality }}
                    </div>
                  </template>
                  <!-- 测速中 -->
                  <div
                    v-else-if="attemptedSources.has(`${source.source}-${source.id}`)"
                    class="bg-gray-500/10 dark:bg-gray-400/20 text-gray-500 px-1.5 py-0 rounded text-xs flex-shrink-0 min-w-[50px] text-center"
                  >
                    检测中...
                  </div>
                </div>

                <!-- 源名称 + 集数 -->
                <div class="flex items-center justify-between">
                  <span
                    class="text-xs px-2 py-1 border border-gray-500/60 rounded text-gray-700 dark:text-gray-300"
                  >
                    {{ source.source_name }}
                  </span>
                  <span
                    v-if="source.episodes && source.episodes.length > 1"
                    class="text-xs text-gray-500 dark:text-gray-400 font-medium"
                  >
                    {{ source.episodes.length }} 集
                  </span>
                </div>

                <!-- 测速数据 -->
                <div class="flex items-end h-6">
                  <template v-if="videoInfoMap.get(`${source.source}-${source.id}`)">
                    <div
                      v-if="!videoInfoMap.get(`${source.source}-${source.id}`)!.hasError"
                      class="flex items-end gap-3 text-xs"
                    >
                      <div class="text-blue-600 dark:text-blue-400 font-medium">
                        {{ videoInfoMap.get(`${source.source}-${source.id}`)!.loadSpeed }}
                      </div>
                      <div class="text-orange-600 dark:text-orange-400 font-medium">
                        {{ videoInfoMap.get(`${source.source}-${source.id}`)!.pingTime }}ms
                      </div>
                    </div>
                    <div
                      v-else
                      class="text-red-500/90 dark:text-red-400 font-medium text-xs"
                    >
                      无测速数据
                    </div>
                  </template>
                </div>
              </div>
            </div>

            <!-- Bottom link -->
            <div
              class="flex-shrink-0 mt-auto pt-2 border-t border-gray-400 dark:border-gray-700"
            >
              <button
                @click="goToSearch"
                class="w-full text-center text-xs text-gray-500 dark:text-gray-400 hover:text-blue-500 dark:hover:text-blue-400 transition-colors py-2"
              >
                影片匹配有误？点击去搜索
              </button>
            </div>
          </div>
        </template>
      </div>
    </template>
  </div>
</template>

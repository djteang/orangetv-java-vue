<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import PageLayout from '@/components/PageLayout.vue'
import { getLiveCategories, getLiveSourcesByCategory } from '@/api/live'
import type { LiveSource } from '@/types'
import { Radio, Tv } from 'lucide-vue-next'
import Artplayer from 'artplayer'
import Hls from 'hls.js'

const categories = ref<string[]>([])
const sources = ref<LiveSource[]>([])
const currentCategory = ref('')
const currentSource = ref<LiveSource | null>(null)
const loading = ref(true)
const loadingStage = ref<'loading' | 'fetching' | 'ready'>('loading')
const loadingMessage = ref('正在加载直播源...')
const loadingSources = ref(false)
const isChannelListCollapsed = ref(false)

const playerRef = ref<HTMLDivElement | null>(null)
const artInstance = ref<Artplayer | null>(null)
const channelListRef = ref<HTMLDivElement | null>(null)
const groupContainerRef = ref<HTMLDivElement | null>(null)

// 获取代理 URL - 使用 URL 编码（与参考项目保持一致）
function getProxyUrl(url: string): string {
  // 如果是外部 URL，通过后端代理访问以避免 CORS 问题
  if (url.startsWith('http://') || url.startsWith('https://')) {
    // 使用 URL 编码
    const encodedUrl = encodeURIComponent(url)
    return `/api/proxy/m3u8?url=${encodedUrl}`
  }
  return url
}

function cleanupPlayer() {
  if (artInstance.value) {
    try {
      if (artInstance.value.video) {
        artInstance.value.video.pause()
        artInstance.value.video.src = ''
        artInstance.value.video.load()
      }
      artInstance.value.destroy()
    } catch (err) {
      console.warn('清理播放器资源时出错:', err)
    }
    artInstance.value = null
  }
}

async function fetchCategories() {
  loading.value = true
  loadingStage.value = 'fetching'
  loadingMessage.value = '正在获取直播源...'
  try {
    const res = await getLiveCategories() as any
    if (Array.isArray(res)) {
      categories.value = res
      if (res.length > 0) {
        await selectCategory(res[0])
      }
    }
    loadingStage.value = 'ready'
    loadingMessage.value = '准备就绪...'
    setTimeout(() => { loading.value = false }, 500)
  } catch (error) {
    console.error('获取分类失败:', error)
    categories.value = []
    loading.value = false
  }
}
async function selectCategory(category: string) {
  currentCategory.value = category
  loadingSources.value = true
  try {
    const res = await getLiveSourcesByCategory(category) as any
    if (Array.isArray(res)) {
      sources.value = res
      // Auto-select first source if none selected or current not in new list
      if (sources.value.length > 0) {
        if (!currentSource.value || !sources.value.find(s => s.id === currentSource.value?.id)) {
          selectSource(sources.value[0])
        }
      }
    }
  } catch (error) {
    console.error('获取直播源失败:', error)
  } finally {
    loadingSources.value = false
  }
}

function selectSource(source: LiveSource) {
  cleanupPlayer()
  currentSource.value = source
  // 使用代理 URL 初始化播放器
  nextTick(() => initPlayer(getProxyUrl(source.url)))
  // Scroll to selected channel
  setTimeout(() => scrollToChannel(source), 100)
}

function scrollToChannel(source: LiveSource) {
  if (!channelListRef.value) return
  const el = channelListRef.value.querySelector(`[data-channel-id="${source.id}"]`) as HTMLElement
  if (el) {
    const container = channelListRef.value
    const containerRect = container.getBoundingClientRect()
    const elRect = el.getBoundingClientRect()
    const scrollTop = container.scrollTop + (elRect.top - containerRect.top) - (containerRect.height / 2) + (elRect.height / 2)
    container.scrollTo({ top: Math.max(0, scrollTop), behavior: 'smooth' })
  }
}

function initPlayer(url: string) {
  if (!playerRef.value) return

  artInstance.value = new Artplayer({
    container: playerRef.value,
    url,
    type: 'm3u8',
    autoplay: true,
    isLive: true,
    pip: true,
    fullscreen: true,
    fullscreenWeb: true,
    mutex: true,
    playsInline: true,
    theme: '#3b82f6',
    volume: 0.7,
    muted: false,
    autoSize: false,
    setting: false,
    loop: false,
    playbackRate: false,
    aspectRatio: false,
    miniProgressBar: false,
    autoPlayback: false,
    lang: navigator.language.startsWith('zh') ? 'zh-cn' : 'en',
    lock: true,
    fastForward: false,
    autoOrientation: true,
    customType: {
      m3u8: function (video: HTMLVideoElement, url: string) {
        if (Hls.isSupported()) {
          const hls = new Hls({
            debug: false,
            enableWorker: true,
            lowLatencyMode: true,
            maxBufferLength: 30,
            backBufferLength: 30,
          })
          hls.loadSource(url)
          hls.attachMedia(video)
          hls.on(Hls.Events.MANIFEST_PARSED, () => {
            video.play().catch(err => console.warn('Autoplay failed:', err))
          })
          hls.on(Hls.Events.ERROR, (_event: string, data: { fatal: boolean; type: string; details: string }) => {
            if (data.fatal) {
              console.error('HLS Error:', data.type, data.details)
              if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
                hls.startLoad()
              } else {
                hls.destroy()
              }
            }
          })
        } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
          video.src = url
        }
      },
    },
  })
}

function handleKeyboardShortcuts(e: KeyboardEvent) {
  const tag = (e.target as HTMLElement).tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA') return

  if (e.key === 'ArrowUp' && artInstance.value && artInstance.value.volume < 1) {
    artInstance.value.volume = Math.round((artInstance.value.volume + 0.1) * 10) / 10
    e.preventDefault()
  }
  if (e.key === 'ArrowDown' && artInstance.value && artInstance.value.volume > 0) {
    artInstance.value.volume = Math.round((artInstance.value.volume - 0.1) * 10) / 10
    e.preventDefault()
  }
  if (e.key === ' ' && artInstance.value) {
    artInstance.value.toggle()
    e.preventDefault()
  }
  if ((e.key === 'f' || e.key === 'F') && artInstance.value) {
    artInstance.value.fullscreen = !artInstance.value.fullscreen
    e.preventDefault()
  }
}

onMounted(() => {
  fetchCategories()
  document.addEventListener('keydown', handleKeyboardShortcuts)
})

onUnmounted(() => {
  cleanupPlayer()
  document.removeEventListener('keydown', handleKeyboardShortcuts)
})
</script>
<template>
  <PageLayout>
    <!-- 加载状态 -->
    <div v-if="loading" class="flex items-center justify-center min-h-screen bg-transparent">
      <div class="text-center max-w-md mx-auto px-6">
        <div class="relative mb-8">
          <div class="relative mx-auto w-24 h-24 bg-gradient-to-r from-blue-300 to-blue-700 rounded-2xl shadow-2xl flex items-center justify-center">
            <div class="text-white text-4xl">📺</div>
            <div class="absolute -inset-2 bg-gradient-to-r from-blue-300 to-blue-700 rounded-2xl opacity-20 animate-spin"></div>
          </div>
        </div>
        <div class="mb-6 w-80 mx-auto">
          <div class="flex justify-center space-x-2 mb-4">
            <div :class="['w-3 h-3 rounded-full transition-all duration-500', loadingStage === 'loading' ? 'bg-blue-500 scale-125' : 'bg-blue-500']"></div>
            <div :class="['w-3 h-3 rounded-full transition-all duration-500', loadingStage === 'fetching' ? 'bg-blue-500 scale-125' : 'bg-blue-500']"></div>
            <div :class="['w-3 h-3 rounded-full transition-all duration-500', loadingStage === 'ready' ? 'bg-blue-500 scale-125' : 'bg-gray-300']"></div>
          </div>
          <div class="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2 overflow-hidden">
            <div
              class="h-full bg-gradient-to-r from-blue-300 to-blue-700 rounded-full transition-all duration-1000 ease-out"
              :style="{ width: loadingStage === 'loading' ? '33%' : loadingStage === 'fetching' ? '66%' : '100%' }"
            ></div>
          </div>
        </div>
        <p class="text-xl font-semibold text-gray-800 dark:text-gray-200 animate-pulse">
          {{ loadingMessage }}
        </p>
      </div>
    </div>

    <!-- 主内容 -->
    <div v-else class="flex flex-col gap-3 py-4 px-5 lg:px-[3rem] 2xl:px-20">
      <!-- 页面标题 -->
      <div class="py-1 md:pt-3 md:pl-10 lg:pl-14 2xl:pl-6">
        <h1 class="text-xl font-semibold text-gray-900 dark:text-gray-100 flex items-center gap-2 max-w-[80%]">
          <Radio class="w-5 h-5 text-blue-500 flex-shrink-0" />
          <div class="min-w-0 flex-1">
            <div class="truncate">
              {{ currentCategory }}
              <span v-if="currentSource" class="text-gray-500 dark:text-gray-400">
                &gt; {{ currentSource.name }}
              </span>
            </div>
          </div>
        </h1>
      </div>

      <!-- 播放器和频道列表 -->
      <div class="space-y-2">
        <!-- 折叠控制 -->
        <div class="hidden lg:flex justify-end">
          <button
            @click="isChannelListCollapsed = !isChannelListCollapsed"
            class="group relative flex items-center space-x-1.5 px-3 py-1.5 rounded-full bg-white/80 hover:bg-white dark:bg-gray-800/80 dark:hover:bg-gray-800 backdrop-blur-sm border border-gray-200/50 dark:border-gray-700/50 shadow-sm hover:shadow-md transition-all duration-200"
          >
            <svg
              :class="['w-3.5 h-3.5 text-gray-500 dark:text-gray-400 transition-transform duration-200', isChannelListCollapsed ? 'rotate-180' : 'rotate-0']"
              fill="none" stroke="currentColor" viewBox="0 0 24 24"
            >
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
            </svg>
            <span class="text-xs font-medium text-gray-600 dark:text-gray-300">
              {{ isChannelListCollapsed ? '显示' : '隐藏' }}
            </span>
            <div :class="['absolute -top-0.5 -right-0.5 w-2 h-2 rounded-full transition-all duration-200', isChannelListCollapsed ? 'bg-orange-400 animate-pulse' : 'bg-blue-400']"></div>
          </button>
        </div>
        <div :class="['grid gap-4 lg:h-[500px] xl:h-[650px] 2xl:h-[750px] transition-all duration-300 ease-in-out', isChannelListCollapsed ? 'grid-cols-1' : 'grid-cols-1 md:grid-cols-4']">
          <!-- 播放器 -->
          <div :class="['h-full transition-all duration-300 ease-in-out', isChannelListCollapsed ? 'col-span-1' : 'md:col-span-3']">
            <div class="relative w-full h-[300px] lg:h-full">
              <div ref="playerRef" class="bg-black w-full h-full rounded-xl overflow-hidden shadow-lg border border-white/0 dark:border-white/30"></div>
            </div>
          </div>

          <!-- 频道列表 -->
          <div :class="['h-[300px] lg:h-full md:overflow-hidden transition-all duration-300 ease-in-out', isChannelListCollapsed ? 'md:col-span-1 lg:hidden lg:opacity-0 lg:scale-95' : 'md:col-span-1 lg:opacity-100 lg:scale-100']">
            <div class="md:ml-2 px-4 py-0 h-full rounded-xl bg-black/10 dark:bg-white/5 flex flex-col border border-white/0 dark:border-white/30 overflow-hidden">
              <!-- 分组标签 -->
              <div class="flex items-center gap-4 mb-1 border-b border-gray-300 dark:border-gray-700 -mx-6 px-6 flex-shrink-0">
                <div class="flex-1 overflow-x-auto" ref="groupContainerRef">
                  <div class="flex gap-4 min-w-max">
                    <button
                      v-for="category in categories"
                      :key="category"
                      @click="selectCategory(category)"
                      :class="[
                        'w-20 relative py-2 text-sm font-medium transition-colors flex-shrink-0 text-center overflow-hidden',
                        currentCategory === category
                          ? 'text-blue-500 dark:text-blue-400'
                          : 'text-gray-700 hover:text-blue-600 dark:text-gray-300 dark:hover:text-blue-400',
                      ]"
                    >
                      <div class="px-1 overflow-hidden whitespace-nowrap" :title="category">{{ category }}</div>
                      <div v-if="currentCategory === category" class="absolute bottom-0 left-0 right-0 h-0.5 bg-blue-500 dark:bg-blue-400"></div>
                    </button>
                  </div>
                </div>
              </div>

              <!-- 频道列表内容 -->
              <div ref="channelListRef" class="flex-1 overflow-y-auto space-y-2 pb-4">
                <div v-if="loadingSources" class="flex justify-center py-10">
                  <div class="animate-spin rounded-full h-6 w-6 border-b-2 border-blue-500"></div>
                </div>
                <template v-else-if="sources.length > 0">
                  <button
                    v-for="source in sources"
                    :key="source.id"
                    :data-channel-id="source.id"
                    @click="selectSource(source)"
                    :class="[
                      'w-full p-3 rounded-lg text-left transition-all duration-200',
                      currentSource?.id === source.id
                        ? 'bg-blue-100 dark:bg-blue-900/30 border border-blue-300 dark:border-blue-700'
                        : 'hover:bg-gray-100 dark:hover:bg-gray-700',
                    ]"
                  >
                    <div class="flex items-center gap-3">
                      <div class="w-10 h-10 bg-gray-300 dark:bg-gray-700 rounded-lg flex items-center justify-center flex-shrink-0 overflow-hidden">
                        <img v-if="(source as any).logo" :src="(source as any).logo" :alt="source.name" class="w-full h-full rounded object-contain" loading="lazy" />
                        <Tv v-else class="w-5 h-5 text-gray-500" />
                      </div>
                      <div class="flex-1 min-w-0">
                        <div class="text-sm font-medium text-gray-900 dark:text-gray-100 truncate" :title="source.name">{{ source.name }}</div>
                        <div v-if="(source as any).category" class="text-xs text-gray-500 dark:text-gray-400 mt-1">{{ (source as any).category }}</div>
                      </div>
                    </div>
                  </button>
                </template>
                <div v-else class="flex flex-col items-center justify-center py-12 text-center">
                  <div class="w-16 h-16 bg-gray-100 dark:bg-gray-800 rounded-full flex items-center justify-center mb-4">
                    <Tv class="w-8 h-8 text-gray-400 dark:text-gray-600" />
                  </div>
                  <p class="text-gray-500 dark:text-gray-400 font-medium">暂无可用频道</p>
                  <p class="text-sm text-gray-400 dark:text-gray-500 mt-1">请选择其他分类或稍后再试</p>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 当前频道信息 -->
        <div v-if="currentSource" class="pt-4">
          <div class="flex items-center gap-4">
            <div class="w-20 h-20 bg-gray-300 dark:bg-gray-700 rounded-lg flex items-center justify-center flex-shrink-0 overflow-hidden">
              <img v-if="(currentSource as any).logo" :src="(currentSource as any).logo" :alt="currentSource.name" class="w-full h-full rounded object-contain" loading="lazy" />
              <Tv v-else class="w-10 h-10 text-gray-500" />
            </div>
            <div class="flex-1 min-w-0">
              <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100 truncate">{{ currentSource.name }}</h3>
              <p class="text-sm text-gray-500 dark:text-gray-400 truncate">{{ currentCategory }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </PageLayout>
</template>

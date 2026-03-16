<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, computed, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageLayout from '@/components/PageLayout.vue'
import EpisodeSelector from '@/components/EpisodeSelector.vue'
import { useUserStore } from '@/stores/user'
import { useToast } from '@/composables/useToast'
import { getVideoDetail, getVideoPlayUrl, search } from '@/api/search'
import type { SearchResult } from '@/types'
import { Heart, Users, Check, X, Share2, UserPlus, Link } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import request from '@/api/index'
import { useWebSocket } from '@/services/websocket'
import Artplayer from 'artplayer'
import Hls from 'hls.js'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const authStore = useAuthStore()
const toast = useToast()

const playerRef = ref<HTMLDivElement | null>(null)
const artInstance = ref<Artplayer | null>(null)

const videoDetail = ref<SearchResult | null>(null)
const currentEpisode = ref(0)
const playUrl = ref('')
const loading = ref(true)
const loadingStage = ref<'searching' | 'fetching' | 'preferring' | 'ready'>('searching')
const loadingMessage = ref('🔍 正在搜索播放源...')
const error = ref<string | null>(null)

// 视频基本信息
const videoTitle = ref('' as string)
const videoYear = ref('' as string)
const videoCover = ref('')

// 折叠选集面板
const isEpisodeSelectorCollapsed = ref(false)

// 视频加载蒙层
const isVideoLoading = ref(true)
const videoLoadingStage = ref<'initing' | 'sourceChanging'>('initing')

// 换源相关
const availableSources = ref<SearchResult[]>([])
const sourceSearchLoading = ref(false)
const sourceSearchError = ref<string | null>(null)

// 共同观影
const showInviteModal = ref(false)
const inviteLink = ref('')
const inviteCopied = ref(false)
const creatingRoom = ref(false)
const currentRoomId = ref('')

// 分享好友选择
const showShareModal = ref(false)
const friends = ref<{ username: string; avatar?: string }[]>([])
const selectedFriends = ref<string[]>([])
const loadingFriends = ref(false)
const sendingInvite = ref(false)
const { sendMessage: wsSendMessage } = useWebSocket()

// 加载好友列表
async function loadFriends() {
  loadingFriends.value = true
  try {
    const data = await request.get('/chat/friends')
    friends.value = (data as unknown as any[]) || []
  } catch {
    friends.value = []
  } finally {
    loadingFriends.value = false
  }
}

// 切换好友选择
function toggleFriendSelection(username: string) {
  const index = selectedFriends.value.indexOf(username)
  if (index === -1) {
    selectedFriends.value.push(username)
  } else {
    selectedFriends.value.splice(index, 1)
  }
}

// 发送邀请给选中的好友
async function sendInviteToFriends() {
  if (selectedFriends.value.length === 0) return
  sendingInvite.value = true
  try {
    const videoInfoForInvite = {
      title: videoDetail.value?.title || '',
      source: actualSource.value,
      id: actualId.value,
      episode_index: currentEpisode.value,
      video_url: playUrl.value,
      cover: videoDetail.value?.poster || '',
    }

    // 给每个选中的好友发送邀请 (使用 snake_case 匹配后端 DTO)
    for (const friendUsername of selectedFriends.value) {
      wsSendMessage('/watch-together', {
        type: 'watch_invite',
        target_user_id: friendUsername,
        room_id: currentRoomId.value,
        video_info: videoInfoForInvite,
      })
    }

    toast.success(`已向 ${selectedFriends.value.length} 位好友发送邀请`)
    showShareModal.value = false
    selectedFriends.value = []

    // 进入房间
    enterWatchRoom()
  } catch {
    toast.error('发送邀请失败')
  } finally {
    sendingInvite.value = false
  }
}

// 打开分享弹窗
function openShareModal() {
  showShareModal.value = true
  selectedFriends.value = []
  loadFriends()
}

async function handleWatchTogether() {
  if (!videoDetail.value || !actualSource.value || !actualId.value) return
  if (creatingRoom.value) return
  creatingRoom.value = true
  try {
    const videoInfoForRoom = {
      title: videoDetail.value.title,
      source: actualSource.value,
      id: actualId.value,
      episode_index: currentEpisode.value,
      video_url: playUrl.value,
      cover: videoDetail.value.poster || '',
    }
    // 通过 REST API 创建房间
    const res = await request.post('/watch-together/rooms', videoInfoForRoom) as any
    const roomId = res.roomId
    currentRoomId.value = roomId
    localStorage.setItem(`watch-together-${roomId}`, JSON.stringify(videoInfoForRoom))
    const host = authStore.user?.username || ''
    // 将视频信息编码到 URL 中，使被邀请者可以直接获取
    const videoInfoEncoded = encodeURIComponent(JSON.stringify(videoInfoForRoom))
    inviteLink.value = `${window.location.origin}/watch-together/${roomId}?host=${encodeURIComponent(host)}&v=${videoInfoEncoded}`
    showInviteModal.value = true
    inviteCopied.value = false
  } catch {
    toast.error('创建房间失败')
  } finally {
    creatingRoom.value = false
  }
}

function enterWatchRoom() {
  const path = inviteLink.value.replace(window.location.origin, '')
  showInviteModal.value = false
  router.push(path)
}

async function copyInviteLink() {
  try {
    await navigator.clipboard.writeText(inviteLink.value)
    inviteCopied.value = true
    toast.success('链接已复制')
    setTimeout(() => { inviteCopied.value = false }, 2000)
  } catch {
    toast.error('复制失败，请手动复制')
  }
}

const source = computed(() => route.query.source as string)
const videoId = computed(() => route.query.id as string)
const titleParam = computed(() => route.query.title as string)
const sourcesParam = computed(() => route.query.sources as string)

// 实际使用的 source/id
const actualSource = ref('')
const actualId = ref('')

const totalEpisodes = computed(() => videoDetail.value?.episodes?.length || 0)

const isFavorite = computed(() => {
  if (!actualSource.value || !actualId.value) return false
  return userStore.isFavorite(`${actualSource.value}+${actualId.value}`)
})

function proxyImage(url: string): string {
  if (!url) return ''
  return `/api/image-proxy?url=${encodeURIComponent(url)}`
}

/**
 * 从 API 响应中提取 SearchResult 数据
 * 拦截器已解包 data，res 直接就是视频详情对象
 */
function extractDetail(raw: any): SearchResult | null {
  if (!raw) return null
  if (raw.id && raw.title && raw.episodes) {
    return raw as SearchResult
  }
  return null
}

function extractSearchResults(raw: any): SearchResult[] {
  if (!raw) return []
  if (raw.results && Array.isArray(raw.results)) return raw.results
  if (Array.isArray(raw)) return raw
  return []
}

async function fetchVideoDetail() {
  const hasSourceId = source.value && videoId.value
  const hasTitle = titleParam.value
  const hasSources = sourcesParam.value

  if (!hasSourceId && !hasTitle) {
    error.value = '缺少必要参数'
    loading.value = false
    return
  }

  loading.value = true
  error.value = null

  try {
    if (hasSourceId) {
      loadingStage.value = 'fetching'
      loadingMessage.value = '🎬 正在获取视频详情...'

      const rawRes = await getVideoDetail(source.value, videoId.value)
      const detail = extractDetail(rawRes)

      if (detail) {
        videoDetail.value = detail
        actualSource.value = source.value
        actualId.value = videoId.value
        videoTitle.value = detail.title || (titleParam.value ?? '')
        videoYear.value = detail.year || ''
        videoCover.value = detail.poster || ''
      } else {
        console.warn('getVideoDetail 返回了无法解析的数据:', rawRes)
        error.value = '获取视频详情失败'
        return
      }
    } else if (hasSources) {
      // 从聚合搜索结果传入的来源列表
      loadingStage.value = 'preferring'
      loadingMessage.value = '🎯 正在加载聚合来源...'

      try {
        const parsedSources = JSON.parse(hasSources) as { source: string; source_name: string; id: string }[]
        if (parsedSources.length > 0) {
          // 使用第一个来源
          const firstSource = parsedSources[0]
          actualSource.value = firstSource.source
          actualId.value = firstSource.id

          // 预填充可用来源（基本信息）
          availableSources.value = parsedSources.map(s => ({
            id: s.id,
            source: s.source,
            source_name: s.source_name,
            title: hasTitle || '',
            poster: '',
            year: route.query.year as string || '',
          })) as SearchResult[]

          loadingStage.value = 'fetching'
          loadingMessage.value = '🎬 正在获取视频详情...'

          try {
            const rawDetailRes = await getVideoDetail(firstSource.source, firstSource.id)
            const detail = extractDetail(rawDetailRes)
            if (detail) {
              videoDetail.value = detail
            } else {
              videoDetail.value = availableSources.value[0]
            }
          } catch {
            videoDetail.value = availableSources.value[0]
          }

          videoTitle.value = videoDetail.value?.title || hasTitle || ''
          videoYear.value = videoDetail.value?.year || ''
          videoCover.value = videoDetail.value?.poster || ''

          skipNextWatch.value = true
          const newQuery = {
            ...route.query,
            source: actualSource.value,
            id: actualId.value,
            title: videoDetail.value?.title || hasTitle,
          }
          // 移除 sources 参数，避免 URL 过长
          delete (newQuery as any).sources
          router.replace({ path: '/play', query: newQuery })
        }
      } catch {
        // 解析失败，回退到标题搜索
        console.warn('Failed to parse sources param, falling back to search')
      }
    }

    // 如果还没获取到视频详情，进行标题搜索
    if (!videoDetail.value && hasTitle) {
      loadingStage.value = 'searching'
      loadingMessage.value = '🔍 正在搜索播放源...'

      const rawSearchRes = await search({ q: hasTitle! })
      const searchResults = extractSearchResults(rawSearchRes)

      if (searchResults.length === 0) {
        error.value = '未找到匹配结果'
        return
      }

      // 保存所有搜索结果供换源使用
      availableSources.value = searchResults

      const firstResult = searchResults[0]
      actualSource.value = firstResult.source
      actualId.value = firstResult.id

      loadingStage.value = 'fetching'
      loadingMessage.value = '🎬 正在获取视频详情...'

      try {
        const rawDetailRes = await getVideoDetail(firstResult.source, firstResult.id)
        const detail = extractDetail(rawDetailRes)
        if (detail) {
          videoDetail.value = detail
        } else {
          // 搜索结果本身可能已包含足够信息
          videoDetail.value = firstResult
        }
      } catch {
        // detail 接口失败时，直接用搜索结果
        videoDetail.value = firstResult
      }

      videoTitle.value = videoDetail.value?.title || hasTitle || ''
      videoYear.value = videoDetail.value?.year || ''
      videoCover.value = videoDetail.value?.poster || ''

      skipNextWatch.value = true
      const newQuery = {
        ...route.query,
        source: actualSource.value,
        id: actualId.value,
        title: videoDetail.value?.title || hasTitle,
      }
      router.replace({ path: '/play', query: newQuery })
    }

    // 从 URL 或播放记录获取集数
    const urlEpisode = route.query.episode as string
    if (urlEpisode) {
      currentEpisode.value = parseInt(urlEpisode)
    } else {
      const record = userStore.playRecords[`${actualSource.value}+${actualId.value}`]
      if (record) {
        currentEpisode.value = record.index
      }
    }

    loadingStage.value = 'preferring'
    loadingMessage.value = '⚡ 正在获取播放地址...'
    await fetchPlayUrl()

    loadingStage.value = 'ready'
    loadingMessage.value = '✨ 准备就绪，即将开始播放...'
    await new Promise((r) => setTimeout(r, 500))

    // 加载完成后异步搜索换源
    if (availableSources.value.length === 0) {
      searchAvailableSources()
    }
  } catch (err) {
    console.error('加载失败:', err)
    error.value = '加载失败，请稍后重试'
    toast.error('加载失败')
  } finally {
    loading.value = false
  }
}

async function searchAvailableSources() {
  if (!videoTitle.value) return
  sourceSearchLoading.value = true
  sourceSearchError.value = null
  try {
    const rawSearchRes = await search({ q: videoTitle.value })
    const results = extractSearchResults(rawSearchRes)
    if (results.length > 0) {
      availableSources.value = results
    }
  } catch (err) {
    sourceSearchError.value = '搜索换源失败'
  } finally {
    sourceSearchLoading.value = false
  }
}

async function fetchPlayUrl() {
  if (!actualSource.value || !actualId.value) return
  if (!videoDetail.value?.episodes?.length) return

  // 直接从已有的 episodes 数组取 URL
  const url = videoDetail.value.episodes[currentEpisode.value]
  if (url) {
    playUrl.value = url
    // 如果 DOM 已就绪（非初始加载），直接初始化播放器
    if (!loading.value && playerRef.value) {
      await nextTick()
      initPlayer()
    }
    return
  }

  // fallback: 调用后端接口
  try {
    const rawRes = await getVideoPlayUrl(
      actualSource.value,
      actualId.value,
      currentEpisode.value
    )
    // 拦截器已解包，rawRes 直接就是播放 URL 字符串
    const playUrlData = typeof rawRes === 'string' ? rawRes : null
    if (playUrlData) {
      playUrl.value = playUrlData
      if (!loading.value && playerRef.value) {
        await nextTick()
        initPlayer()
      }
    }
  } catch (err) {
    console.error('获取播放地址失败:', err)
    toast.error('获取播放地址失败')
  }
}

function initPlayer() {
  if (!playerRef.value || !playUrl.value) return

  // 如果已有播放器实例，用 switch 切换
  if (artInstance.value) {
    artInstance.value.switchUrl(playUrl.value)
    isVideoLoading.value = false
    return
  }

  artInstance.value = new Artplayer({
    container: playerRef.value,
    url: playUrl.value,
    poster: videoCover.value ? proxyImage(videoCover.value) : '',
    volume: 0.7,
    autoplay: true,
    pip: true,
    autoSize: false,
    autoMini: false,
    setting: true,
    loop: false,
    playbackRate: true,
    fullscreen: true,
    fullscreenWeb: true,
    mutex: true,
    playsInline: true,
    autoPlayback: false,
    airplay: true,
    theme: '#3b82f6',
    lang: 'zh-cn',
    hotkey: false,
    fastForward: true,
    autoOrientation: true,
    lock: true,
    moreVideoAttr: {
      crossOrigin: 'anonymous',
    },
    customType: {
      m3u8: function (video: HTMLVideoElement, url: string) {
        if (Hls.isSupported()) {
          if ((video as any).hls) {
            ;(video as any).hls.destroy()
          }
          const hls = new Hls()
          hls.loadSource(url)
          hls.attachMedia(video)
          ;(video as any).hls = hls
        } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
          video.src = url
        }
      },
    },
  })

  artInstance.value.on('video:timeupdate', () => {
    savePlayRecord()
  })

  artInstance.value.on('video:ended', () => {
    if (videoDetail.value && currentEpisode.value < videoDetail.value.episodes.length - 1) {
      handleEpisodeChange(currentEpisode.value + 1)
    }
  })

  artInstance.value.on('ready', () => {
    isVideoLoading.value = false
  })

  artInstance.value.on('video:playing', () => {
    isVideoLoading.value = false
  })
}

function savePlayRecord() {
  if (!artInstance.value || !videoDetail.value || !actualSource.value || !actualId.value) return

  const player = artInstance.value
  const currentTime = player.currentTime || 0
  const duration = player.duration || 0
  if (currentTime < 1 || !duration) return

  const key = `${actualSource.value}+${actualId.value}`
  userStore.savePlayRecord(key, {
    title: videoDetail.value.title,
    source_name: videoDetail.value.source_name,
    cover: videoDetail.value.poster,
    year: videoDetail.value.year,
    index: currentEpisode.value,
    total_episodes: videoDetail.value.episodes.length,
    play_time: Math.floor(currentTime),
    total_time: Math.floor(duration),
    save_time: Date.now(),
    search_title: videoDetail.value.title,
  })
}

function handleEpisodeChange(episodeNumber: number) {
  if (episodeNumber >= 0 && episodeNumber < totalEpisodes.value) {
    savePlayRecord()
    currentEpisode.value = episodeNumber
    isVideoLoading.value = true
    videoLoadingStage.value = 'sourceChanging'
    fetchPlayUrl()
  }
}

function handleSourceChange(newSource: string, newId: string, newTitle: string) {
  isVideoLoading.value = true
  videoLoadingStage.value = 'sourceChanging'

  const newDetail = availableSources.value.find(
    (s) => s.source === newSource && s.id === newId
  )
  if (!newDetail) {
    isVideoLoading.value = false
    return
  }

  // 尝试保持当前集数
  let targetIndex = currentEpisode.value
  if (!newDetail.episodes || targetIndex >= newDetail.episodes.length) {
    targetIndex = 0
  }

  // 更新 URL
  skipNextWatch.value = true
  router.replace({
    path: '/play',
    query: {
      ...route.query,
      source: newSource,
      id: newId,
    },
  })

  videoTitle.value = newDetail.title || newTitle
  videoYear.value = newDetail.year
  videoCover.value = newDetail.poster
  actualSource.value = newSource
  actualId.value = newId
  videoDetail.value = newDetail
  currentEpisode.value = targetIndex

  fetchPlayUrl()
}

async function toggleFavorite() {
  if (!videoDetail.value || !actualSource.value || !actualId.value) return

  const key = `${actualSource.value}+${actualId.value}`

  if (isFavorite.value) {
    await userStore.deleteFavorite(key)
    toast.success('已取消收藏')
  } else {
    await userStore.saveFavorite(key, {
      source_name: videoDetail.value.source_name,
      total_episodes: videoDetail.value.episodes.length,
      title: videoDetail.value.title,
      year: videoDetail.value.year,
      cover: videoDetail.value.poster,
      save_time: Date.now(),
      search_title: videoDetail.value.title,
    })
    toast.success('已添加收藏')
  }
}

function reloadPage() {
  window.location.reload()
}

// 键盘快捷键
function handleKeyboardShortcuts(e: KeyboardEvent) {
  if ((e.target as HTMLElement).tagName === 'INPUT' || (e.target as HTMLElement).tagName === 'TEXTAREA') return

  if (e.altKey && e.key === 'ArrowLeft') {
    if (currentEpisode.value > 0) {
      handleEpisodeChange(currentEpisode.value - 1)
      e.preventDefault()
    }
  }
  if (e.altKey && e.key === 'ArrowRight') {
    if (currentEpisode.value < totalEpisodes.value - 1) {
      handleEpisodeChange(currentEpisode.value + 1)
      e.preventDefault()
    }
  }
  if (!e.altKey && e.key === 'ArrowLeft' && artInstance.value) {
    artInstance.value.currentTime -= 10
    e.preventDefault()
  }
  if (!e.altKey && e.key === 'ArrowRight' && artInstance.value) {
    artInstance.value.currentTime += 10
    e.preventDefault()
  }
  if (e.key === 'ArrowUp' && artInstance.value) {
    artInstance.value.volume = Math.min(1, Math.round((artInstance.value.volume + 0.1) * 10) / 10)
    e.preventDefault()
  }
  if (e.key === 'ArrowDown' && artInstance.value) {
    artInstance.value.volume = Math.max(0, Math.round((artInstance.value.volume - 0.1) * 10) / 10)
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

onMounted(async () => {
  videoTitle.value = (route.query.title as string) || ''
  videoYear.value = (route.query.year as string) || ''
  document.addEventListener('keydown', handleKeyboardShortcuts)
  await userStore.fetchPlayRecords()
  await userStore.fetchFavorites()
  await fetchVideoDetail()
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeyboardShortcuts)
  if (artInstance.value) {
    savePlayRecord()
    if (artInstance.value.video && (artInstance.value.video as any).hls) {
      ;(artInstance.value.video as any).hls.destroy()
    }
    artInstance.value.destroy()
    artInstance.value = null
  }
})

const skipNextWatch = ref(false)
watch([source, videoId, titleParam], () => {
  if (skipNextWatch.value) {
    skipNextWatch.value = false
    return
  }
  actualSource.value = ''
  actualId.value = ''
  fetchVideoDetail()
})

// 关键：当 loading 从 true 变为 false 时，DOM 才渲染出 playerRef
// 此时再初始化播放器
watch(loading, async (newVal, oldVal) => {
  if (oldVal && !newVal && playUrl.value && !artInstance.value) {
    await nextTick()
    // 等待 DOM 完全渲染
    await new Promise((r) => setTimeout(r, 100))
    if (playerRef.value) {
      initPlayer()
    }
  }
})

// 安全兜底：如果 isVideoLoading 超过 8 秒还没消失，强制关闭
watch(isVideoLoading, (val) => {
  if (val) {
    const timer = setTimeout(() => {
      if (isVideoLoading.value) {
        isVideoLoading.value = false
      }
    }, 8000)
    const stop = watch(isVideoLoading, (v) => {
      if (!v) {
        clearTimeout(timer)
        stop()
      }
    })
  }
})
</script>

<template>
  <PageLayout>
    <div class="min-h-screen">
      <!-- 加载状态 -->
      <div v-if="loading" class="flex items-center justify-center min-h-screen bg-transparent">
        <div class="text-center max-w-md mx-auto px-6">
          <!-- 动画影院图标 -->
          <div class="relative mb-8">
            <div
              class="relative mx-auto w-24 h-24 bg-gradient-to-r from-blue-500 to-blue-600 rounded-2xl shadow-2xl flex items-center justify-center transform hover:scale-105 transition-transform duration-300"
            >
              <div class="text-white text-4xl">
                <span v-if="loadingStage === 'searching'">🔍</span>
                <span v-else-if="loadingStage === 'fetching'">🎬</span>
                <span v-else-if="loadingStage === 'preferring'">⚡</span>
                <span v-else-if="loadingStage === 'ready'">✨</span>
              </div>
              <div
                class="absolute -inset-2 bg-gradient-to-r from-blue-500 to-blue-600 rounded-2xl opacity-20 animate-spin"
              ></div>
            </div>
            <div class="absolute top-0 left-0 w-full h-full pointer-events-none">
              <div class="absolute top-2 left-2 w-2 h-2 bg-blue-400 rounded-full animate-bounce"></div>
              <div
                class="absolute top-4 right-4 w-1.5 h-1.5 bg-blue-400 rounded-full animate-bounce"
                style="animation-delay: 0.5s"
              ></div>
              <div
                class="absolute bottom-3 left-6 w-1 h-1 bg-blue-300 rounded-full animate-bounce"
                style="animation-delay: 1s"
              ></div>
            </div>
          </div>
          <!-- 进度指示器 -->
          <div class="mb-6 w-80 mx-auto">
            <div class="flex justify-center space-x-2 mb-4">
              <div
                :class="[
                  'w-3 h-3 rounded-full transition-all duration-500',
                  loadingStage === 'searching' || loadingStage === 'fetching'
                    ? 'bg-blue-500 scale-125'
                    : loadingStage === 'preferring' || loadingStage === 'ready'
                      ? 'bg-blue-500'
                      : 'bg-gray-300',
                ]"
              ></div>
              <div
                :class="[
                  'w-3 h-3 rounded-full transition-all duration-500',
                  loadingStage === 'preferring'
                    ? 'bg-blue-500 scale-125'
                    : loadingStage === 'ready'
                      ? 'bg-blue-500'
                      : 'bg-gray-300',
                ]"
              ></div>
              <div
                :class="[
                  'w-3 h-3 rounded-full transition-all duration-500',
                  loadingStage === 'ready' ? 'bg-blue-500 scale-125' : 'bg-gray-300',
                ]"
              ></div>
            </div>
            <div class="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2 overflow-hidden">
              <div
                class="h-full bg-gradient-to-r from-blue-500 to-blue-600 rounded-full transition-all duration-1000 ease-out"
                :style="{
                  width:
                    loadingStage === 'searching' || loadingStage === 'fetching'
                      ? '33%'
                      : loadingStage === 'preferring'
                        ? '66%'
                        : '100%',
                }"
              ></div>
            </div>
          </div>
          <div class="space-y-2">
            <p class="text-xl font-semibold text-gray-800 dark:text-gray-200 animate-pulse">
              {{ loadingMessage }}
            </p>
          </div>
        </div>
      </div>

      <!-- 错误状态 -->
      <div v-else-if="error" class="flex items-center justify-center min-h-screen bg-transparent">
        <div class="text-center max-w-md mx-auto px-6">
          <!-- 错误图标 -->
          <div class="relative mb-8">
            <div
              class="relative mx-auto w-24 h-24 bg-gradient-to-r from-red-500 to-orange-500 rounded-2xl shadow-2xl flex items-center justify-center transform hover:scale-105 transition-transform duration-300"
            >
              <div class="text-white text-4xl">😵</div>
              <div
                class="absolute -inset-2 bg-gradient-to-r from-red-500 to-orange-500 rounded-2xl opacity-20 animate-pulse"
              ></div>
            </div>
            <!-- 浮动错误粒子 -->
            <div class="absolute top-0 left-0 w-full h-full pointer-events-none">
              <div class="absolute top-2 left-2 w-2 h-2 bg-red-400 rounded-full animate-bounce"></div>
              <div
                class="absolute top-4 right-4 w-1.5 h-1.5 bg-orange-400 rounded-full animate-bounce"
                style="animation-delay: 0.5s"
              ></div>
              <div
                class="absolute bottom-3 left-6 w-1 h-1 bg-yellow-400 rounded-full animate-bounce"
                style="animation-delay: 1s"
              ></div>
            </div>
          </div>
          <!-- 错误信息 -->
          <div class="space-y-4 mb-8">
            <h2 class="text-2xl font-bold text-gray-800 dark:text-gray-200">
              哎呀，出现了一些问题
            </h2>
            <div
              class="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-4"
            >
              <p class="text-red-600 dark:text-red-400 font-medium">{{ error }}</p>
            </div>
            <p class="text-sm text-gray-500 dark:text-gray-400">请检查网络连接或尝试刷新页面</p>
          </div>
          <!-- 操作按钮 -->
          <div class="space-y-3">
            <button
              @click="
                videoTitle
                  ? router.push(`/search?q=${encodeURIComponent(videoTitle)}`)
                  : router.back()
              "
              class="w-full px-6 py-3 bg-gradient-to-r from-blue-500 to-blue-600 text-white rounded-xl font-medium hover:from-blue-600 hover:to-blue-700 transform hover:scale-105 transition-all duration-200 shadow-lg hover:shadow-xl"
            >
              {{ videoTitle ? '🔍 返回搜索' : '← 返回上页' }}
            </button>
            <button
              @click="reloadPage"
              class="w-full px-6 py-3 bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded-xl font-medium hover:bg-gray-200 dark:hover:bg-gray-600 transition-colors duration-200"
            >
              🔄 重新尝试
            </button>
          </div>
        </div>
      </div>

      <!-- 播放页面主体 -->
      <template v-else-if="videoDetail">
        <div class="flex flex-col gap-3 py-4 px-5 lg:px-[3rem] 2xl:px-20">
          <!-- 第一行：影片标题 -->
          <div class="py-1 md:pl-8">
            <h1 class="text-xl font-semibold text-gray-900 dark:text-gray-100">
              {{ videoTitle || '影片标题' }}
              <span v-if="totalEpisodes > 1" class="text-gray-500 dark:text-gray-400">
                {{
                  ` > ${videoDetail.episodes_titles?.[currentEpisode] || `第 ${currentEpisode + 1} 集`}`
                }}
              </span>
            </h1>
          </div>

          <!-- 第二行：播放器和选集 -->
          <div class="space-y-2">
            <!-- 折叠控制 - 仅在 lg 及以上屏幕显示 -->
            <div class="hidden lg:flex justify-end">
              <button
                @click="isEpisodeSelectorCollapsed = !isEpisodeSelectorCollapsed"
                class="group relative flex items-center space-x-1.5 px-3 py-1.5 rounded-full bg-white/80 hover:bg-white dark:bg-gray-800/80 dark:hover:bg-gray-800 backdrop-blur-sm border border-gray-200/50 dark:border-gray-700/50 shadow-sm hover:shadow-md transition-all duration-200"
                :title="isEpisodeSelectorCollapsed ? '显示选集面板' : '隐藏选集面板'"
              >
                <svg
                  :class="[
                    'w-3.5 h-3.5 text-gray-500 dark:text-gray-400 transition-transform duration-200',
                    isEpisodeSelectorCollapsed ? 'rotate-180' : 'rotate-0',
                  ]"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M9 5l7 7-7 7"
                  />
                </svg>
                <span class="text-xs font-medium text-gray-600 dark:text-gray-300">
                  {{ isEpisodeSelectorCollapsed ? '显示' : '隐藏' }}
                </span>
                <div
                  :class="[
                    'absolute -top-0.5 -right-0.5 w-2 h-2 rounded-full transition-all duration-200',
                    isEpisodeSelectorCollapsed ? 'bg-orange-400 animate-pulse' : 'bg-blue-400',
                  ]"
                ></div>
              </button>
            </div>

            <div
              :class="[
                'grid gap-4 lg:h-[500px] xl:h-[650px] 2xl:h-[750px] transition-all duration-300 ease-in-out',
                isEpisodeSelectorCollapsed ? 'grid-cols-1' : 'grid-cols-1 md:grid-cols-4',
              ]"
            >
              <!-- 播放器 -->
              <div
                :class="[
                  'h-full transition-all duration-300 ease-in-out rounded-xl border border-white/0 dark:border-white/30',
                  isEpisodeSelectorCollapsed ? 'col-span-1' : 'md:col-span-3',
                ]"
              >
                <div class="relative w-full h-[300px] lg:h-full">
                  <div
                    ref="playerRef"
                    class="bg-black w-full h-full rounded-xl overflow-hidden shadow-lg"
                  ></div>

                  <!-- 换源/加载蒙层 -->
                  <div
                    v-if="isVideoLoading"
                    class="absolute inset-0 bg-black/85 backdrop-blur-sm rounded-xl flex items-center justify-center z-[500] transition-all duration-300"
                  >
                    <div class="text-center max-w-md mx-auto px-6">
                      <div class="relative mb-8">
                        <div
                          class="relative mx-auto w-24 h-24 bg-gradient-to-r from-blue-500 to-blue-600 rounded-2xl shadow-2xl flex items-center justify-center transform hover:scale-105 transition-transform duration-300"
                        >
                          <div class="text-white text-4xl">🎬</div>
                          <div
                            class="absolute -inset-2 bg-gradient-to-r from-blue-500 to-blue-600 rounded-2xl opacity-20 animate-spin"
                          ></div>
                        </div>
                        <div class="absolute top-0 left-0 w-full h-full pointer-events-none">
                          <div
                            class="absolute top-2 left-2 w-2 h-2 bg-blue-400 rounded-full animate-bounce"
                          ></div>
                          <div
                            class="absolute top-4 right-4 w-1.5 h-1.5 bg-blue-400 rounded-full animate-bounce"
                            style="animation-delay: 0.5s"
                          ></div>
                          <div
                            class="absolute bottom-3 left-6 w-1 h-1 bg-blue-300 rounded-full animate-bounce"
                            style="animation-delay: 1s"
                          ></div>
                        </div>
                      </div>
                      <div class="space-y-2">
                        <p class="text-xl font-semibold text-white animate-pulse">
                          {{
                            videoLoadingStage === 'sourceChanging'
                              ? '🔄 切换播放源...'
                              : '🔄 视频加载中...'
                          }}
                        </p>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 选集和换源面板 -->
              <div
                :class="[
                  'h-[300px] lg:h-full md:overflow-hidden transition-all duration-300 ease-in-out',
                  isEpisodeSelectorCollapsed
                    ? 'md:col-span-1 lg:hidden lg:opacity-0 lg:scale-95'
                    : 'md:col-span-1 lg:opacity-100 lg:scale-100',
                ]"
              >
                <EpisodeSelector
                  :total-episodes="totalEpisodes"
                  :episodes-titles="videoDetail.episodes_titles || []"
                  :model-value="currentEpisode + 1"
                  :current-source="actualSource"
                  :current-id="actualId"
                  :video-title="videoTitle"
                  :available-sources="availableSources"
                  :source-search-loading="sourceSearchLoading"
                  :source-search-error="sourceSearchError"
                  @update:model-value="handleEpisodeChange"
                  @source-change="handleSourceChange"
                />
              </div>
            </div>
          </div>

          <!-- 详情展示 -->
          <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
            <!-- 封面展示 (桌面端显示在左侧) -->
            <div class="hidden md:block md:col-span-1 md:order-first">
              <div class="pl-0 py-4 pr-6">
                <div
                  class="relative bg-gray-300 dark:bg-gray-700 aspect-[2/3] flex items-center justify-center rounded-xl overflow-hidden"
                >
                  <img
                    v-if="videoCover"
                    :src="proxyImage(videoCover)"
                    :alt="videoTitle"
                    class="w-full h-full object-cover"
                  />
                  <span v-else class="text-gray-600 dark:text-gray-400">封面图片</span>
                </div>
              </div>
            </div>

            <!-- 文字区 -->
            <div class="md:col-span-3">
              <div class="p-6 flex flex-col min-h-0 text-gray-900 dark:text-gray-100">
                <!-- 标题 + 收藏 -->
                <h1
                  class="text-3xl font-bold mb-2 tracking-wide flex items-center flex-shrink-0 text-center md:text-left w-full text-gray-900 dark:text-white"
                >
                  {{ videoTitle || '影片标题' }}
                  <button
                    @click.stop="toggleFavorite"
                    class="ml-3 flex-shrink-0 hover:opacity-80 transition-opacity"
                  >
                    <!-- 已收藏：实心红心 -->
                    <svg
                      v-if="isFavorite"
                      class="h-7 w-7"
                      viewBox="0 0 24 24"
                      xmlns="http://www.w3.org/2000/svg"
                    >
                      <path
                        d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"
                        fill="#ef4444"
                        stroke="#ef4444"
                        stroke-width="2"
                        stroke-linecap="round"
                        stroke-linejoin="round"
                      />
                    </svg>
                    <!-- 未收藏：空心心形 -->
                    <Heart
                      v-else
                      class="h-7 w-7 stroke-[1] text-gray-600 dark:text-gray-300"
                    />
                  </button>
                  <!-- 共同观影按钮 -->
                  <button
                    v-if="authStore.isLoggedIn"
                    @click.stop="handleWatchTogether"
                    class="ml-2 flex-shrink-0 flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 hover:bg-blue-100 dark:hover:bg-blue-900/50 transition-colors text-sm font-medium"
                    title="共同观影"
                  >
                    <Users class="h-4 w-4" />
                    <span class="hidden sm:inline">共同观影</span>
                  </button>
                </h1>

                <!-- 关键信息行 -->
                <div
                  class="flex flex-wrap items-center gap-3 text-base mb-4 opacity-80 flex-shrink-0 text-gray-700 dark:text-gray-300"
                >
                  <span v-if="videoDetail.class" class="text-blue-600 dark:text-blue-400 font-semibold">
                    {{ videoDetail.class }}
                  </span>
                  <span v-if="videoDetail.year || videoYear">
                    {{ videoDetail.year || videoYear }}
                  </span>
                  <span
                    v-if="videoDetail.source_name"
                    class="border border-gray-500/60 px-2 py-[1px] rounded text-gray-700 dark:text-gray-300"
                  >
                    {{ videoDetail.source_name }}
                  </span>
                  <span v-if="videoDetail.type_name">{{ videoDetail.type_name }}</span>
                </div>

                <!-- 剧情简介 -->
                <div
                  v-if="videoDetail.desc"
                  class="mt-0 text-base leading-relaxed opacity-90 overflow-y-auto pr-2 flex-1 min-h-0 scrollbar-hide text-gray-700 dark:text-gray-300"
                  style="white-space: pre-line"
                >
                  {{ videoDetail.desc }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>

      <!-- 无数据 -->
      <div v-else class="flex items-center justify-center h-96">
        <p class="text-gray-500 dark:text-gray-400">视频不存在或已下架</p>
      </div>
    </div>

    <!-- 共同观影邀请链接弹窗 -->
    <Teleport to="body">
      <div
        v-if="showInviteModal"
        class="fixed inset-0 z-[9999] flex items-center justify-center bg-black/50 backdrop-blur-sm"
        @click.self="showInviteModal = false"
      >
        <div class="bg-white dark:bg-gray-800 rounded-2xl shadow-2xl p-6 mx-4 w-full max-w-md">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-lg font-semibold text-gray-900 dark:text-white flex items-center gap-2">
              <Users class="w-5 h-5 text-blue-500" />
              共同观影
            </h3>
            <button @click="showInviteModal = false" class="p-1 rounded-full hover:bg-gray-100 dark:hover:bg-gray-700">
              <X class="w-5 h-5 text-gray-500" />
            </button>
          </div>
          <p class="text-sm text-gray-500 dark:text-gray-400 mb-4">
            邀请好友一起观看，享受共同观影的乐趣
          </p>
          <div class="flex gap-3">
            <button
              @click="openShareModal"
              class="flex-1 flex items-center justify-center gap-2 px-4 py-3 bg-blue-500 text-white rounded-xl hover:bg-blue-600 transition-colors"
            >
              <Share2 class="w-5 h-5" />
              <span class="font-medium">分享给好友</span>
            </button>
          </div>
          <button
            @click="enterWatchRoom"
            class="mt-4 w-full px-4 py-2.5 bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded-lg text-sm font-medium hover:bg-gray-200 dark:hover:bg-gray-600 transition-colors"
          >
            直接进入房间
          </button>
        </div>
      </div>
    </Teleport>

    <!-- 分享好友选择弹窗 -->
    <Teleport to="body">
      <div
        v-if="showShareModal"
        class="fixed inset-0 z-[10000] flex items-center justify-center bg-black/50 backdrop-blur-sm"
        @click.self="showShareModal = false"
      >
        <div class="bg-white dark:bg-gray-800 rounded-2xl shadow-2xl mx-4 w-full max-w-md overflow-hidden">
          <!-- 头部 -->
          <div class="flex items-center justify-between px-4 py-3 border-b border-gray-200 dark:border-gray-700">
            <button @click="showShareModal = false" class="text-sm text-gray-500 hover:text-gray-700 dark:hover:text-gray-300">
              取消
            </button>
            <h3 class="text-base font-semibold text-gray-900 dark:text-white">选择好友</h3>
            <button
              @click="sendInviteToFriends"
              :disabled="selectedFriends.length === 0 || sendingInvite"
              class="text-sm font-medium text-blue-500 hover:text-blue-600 disabled:text-gray-400 disabled:cursor-not-allowed"
            >
              {{ sendingInvite ? '发送中...' : `确定${selectedFriends.length > 0 ? `(${selectedFriends.length})` : ''}` }}
            </button>
          </div>

          <!-- 好友列表 -->
          <div class="max-h-[50vh] overflow-y-auto">
            <!-- 加载中 -->
            <div v-if="loadingFriends" class="flex items-center justify-center py-12">
              <div class="w-6 h-6 border-2 border-blue-500 border-t-transparent rounded-full animate-spin"></div>
            </div>

            <!-- 无好友 -->
            <div v-else-if="friends.length === 0" class="flex flex-col items-center justify-center py-12 px-4">
              <div class="w-16 h-16 rounded-full bg-gray-100 dark:bg-gray-700 flex items-center justify-center mb-4">
                <UserPlus class="w-8 h-8 text-gray-400" />
              </div>
              <p class="text-gray-500 dark:text-gray-400 text-sm mb-4 text-center">你还没有好友，快去添加好友吧</p>
              <button
                @click="copyInviteLink(); showShareModal = false"
                class="flex items-center gap-2 px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 transition-colors text-sm font-medium"
              >
                <Link class="w-4 h-4" />
                复制邀请链接
              </button>
            </div>

            <!-- 好友列表 -->
            <div v-else>
              <div
                v-for="friend in friends"
                :key="friend.username"
                @click="toggleFriendSelection(friend.username)"
                class="flex items-center gap-3 px-4 py-3 hover:bg-gray-50 dark:hover:bg-gray-700/50 cursor-pointer transition-colors"
              >
                <!-- 选择框 -->
                <div
                  :class="[
                    'w-5 h-5 rounded-full border-2 flex items-center justify-center transition-colors flex-shrink-0',
                    selectedFriends.includes(friend.username)
                      ? 'bg-blue-500 border-blue-500'
                      : 'border-gray-300 dark:border-gray-600'
                  ]"
                >
                  <Check v-if="selectedFriends.includes(friend.username)" class="w-3 h-3 text-white" />
                </div>
                <!-- 头像 -->
                <img
                  v-if="friend.avatar"
                  :src="friend.avatar"
                  class="w-10 h-10 rounded-md object-cover flex-shrink-0"
                />
                <div v-else class="w-10 h-10 rounded-md bg-gradient-to-br from-blue-400 to-purple-500 flex items-center justify-center text-white text-sm font-semibold flex-shrink-0">
                  {{ friend.username.charAt(0).toUpperCase() }}
                </div>
                <!-- 用户名 -->
                <span class="text-sm text-gray-800 dark:text-gray-200 truncate">{{ friend.username }}</span>
              </div>
            </div>
          </div>

          <!-- 底部复制链接 -->
          <div v-if="friends.length > 0" class="px-4 py-3 border-t border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900">
            <button
              @click="copyInviteLink"
              class="w-full flex items-center justify-center gap-2 px-4 py-2.5 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
            >
              <Link class="w-4 h-4" />
              {{ inviteCopied ? '已复制' : '复制邀请链接' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </PageLayout>
</template>

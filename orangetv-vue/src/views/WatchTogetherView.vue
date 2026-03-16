<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageLayout from '@/components/PageLayout.vue'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useWebSocket } from '@/services/websocket'
import request from '@/api/index'
import type { WatchRoom, WatchSyncState, WatchChatMessage, WatchEffect, WatchEffectType } from '@/types'
import { LogOut, Send, MessageCircle } from 'lucide-vue-next'
import Artplayer from 'artplayer'
import Hls from 'hls.js'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const toast = useToast()
const { isConnected, connect, sendMessage, subscribe } = useWebSocket()

const roomId = computed(() => route.params.roomId as string)
const hostUsername = computed(() => route.query.host as string)

const playerRef = ref<HTMLDivElement | null>(null)
const artInstance = ref<Artplayer | null>(null)
const chatContainerRef = ref<HTMLDivElement | null>(null)

const videoInfo = ref<WatchRoom['video_info'] | null>(null)
const partnerId = ref('')
const partnerOnline = ref(false)
const partnerAvatar = ref<string | null>(null)
const myAvatar = ref<string | null>(null)
const isHost = ref(false)
const roomMembers = ref<string[]>([])
const chatMessages = ref<WatchChatMessage[]>([])
const chatInput = ref('')
const showChat = ref(true)
const isMobile = ref(false)
const effects = ref<WatchEffect[]>([])
const floatingEmojis = ref<{ id: string; emoji: string; x: number; delay: number }[]>([])
const roomLoading = ref(true)
const roomError = ref('')

const effectTypes: { type: WatchEffectType; emoji: string; label: string }[] = [
  { type: 'heart', emoji: '❤️', label: '爱心' },
  { type: 'like', emoji: '👍', label: '点赞' },
  { type: 'clap', emoji: '👏', label: '鼓掌' },
  { type: 'fire', emoji: '🔥', label: '火热' },
  { type: 'laugh', emoji: '😂', label: '大笑' },
  { type: 'wow', emoji: '😮', label: '惊叹' },
]

function checkMobile() { isMobile.value = window.innerWidth < 768 }

function initPlayer(url: string, cover?: string) {
  if (!playerRef.value) return
  if (artInstance.value) { artInstance.value.destroy(); artInstance.value = null }
  artInstance.value = new Artplayer({
    container: playerRef.value, url, poster: cover || '',
    autoplay: true, pip: true, fullscreen: true, fullscreenWeb: true,
    mutex: true, backdrop: true, playsInline: true, theme: '#0ea5e9',
    customType: {
      m3u8(video: HTMLVideoElement, url: string) {
        if (Hls.isSupported()) { const hls = new Hls(); hls.loadSource(url); hls.attachMedia(video) }
        else if (video.canPlayType('application/vnd.apple.mpegurl')) { video.src = url }
      },
    },
  })
  artInstance.value.on('play', () => sendSyncState(true))
  artInstance.value.on('pause', () => sendSyncState(false))
  artInstance.value.on('seek', () => sendSyncState(artInstance.value!.playing))

  // 非房主：在播放器就绪后拦截视频元素点击
  if (!isHost.value) {
    artInstance.value.on('ready', () => {
      if (artInstance.value?.video) {
        artInstance.value.video.addEventListener('click', (e) => {
          e.stopPropagation()
          e.preventDefault()
        }, true)
      }
    })
  }
}

// 防抖：忽略远程同步触发的本地事件
let ignoreSyncUntil = 0

function sendSyncState(isPlaying: boolean) {
  if (!artInstance.value || !authStore.user) return
  if (!isHost.value) return
  if (Date.now() < ignoreSyncUntil) return
  sendMessage('/watch-together', {
    type: 'watch_sync',
    room_id: roomId.value,
    sync_state: { is_playing: isPlaying, current_time: artInstance.value.currentTime, volume: artInstance.value.volume, updated_by: authStore.user.username, timestamp: Date.now() },
  })
}

function handleRemoteSync(syncState: WatchSyncState) {
  if (!artInstance.value || syncState.updated_by === authStore.user?.username) return
  ignoreSyncUntil = Date.now() + 500
  if (Math.abs(artInstance.value.currentTime - syncState.current_time) > 2) artInstance.value.currentTime = syncState.current_time
  if (syncState.is_playing && artInstance.value.playing === false) artInstance.value.play()
  else if (!syncState.is_playing && artInstance.value.playing) artInstance.value.pause()
}


function handleSendChat() {
  if (!chatInput.value.trim() || !authStore.user) return
  sendMessage('/watch-together', {
    type: 'watch_chat',
    room_id: roomId.value,
    message: { content: chatInput.value.trim(), sender: authStore.user.username },
  })
  chatMessages.value.push({ id: Date.now().toString(), sender: authStore.user.username, content: chatInput.value.trim(), timestamp: Date.now() })
  chatInput.value = ''
  nextTick(() => { if (chatContainerRef.value) chatContainerRef.value.scrollTop = chatContainerRef.value.scrollHeight })
}

function handleSendEffect(type: WatchEffectType) {
  if (!authStore.user) return
  sendMessage('/watch-together', {
    type: 'watch_effect',
    room_id: roomId.value,
    effect: { type, sender: authStore.user.username },
  })
  addEffect({ id: Date.now().toString(), type, sender: authStore.user.username, timestamp: Date.now() })
}

function addEffect(effect: WatchEffect) {
  effects.value.push(effect)
  // 生成多个飘动的 emoji 粒子
  const emoji = effectTypes.find(e => e.type === effect.type)?.emoji || '❤️'
  const particleCount = 8 + Math.floor(Math.random() * 5) // 8-12 个粒子
  for (let i = 0; i < particleCount; i++) {
    const particle = {
      id: `${effect.id}-${i}`,
      emoji,
      x: 60 + Math.random() * 35, // 从右侧 60%-95% 的位置飘出
      delay: i * 0.1, // 依次延迟出现
    }
    floatingEmojis.value.push(particle)
  }
  // 清理粒子
  setTimeout(() => {
    effects.value = effects.value.filter(e => e.id !== effect.id)
    floatingEmojis.value = floatingEmojis.value.filter(p => !p.id.startsWith(effect.id))
  }, 4000)
}

function handleLeave() {
  sendMessage('/watch-together', { type: 'watch_leave', room_id: roomId.value })
  if (artInstance.value) { artInstance.value.destroy(); artInstance.value = null }
  localStorage.removeItem(`watch-together-${roomId.value}`)
  router.push('/')
}

async function fetchPartnerAvatar(username: string) {
  try {
    const res = await fetch(`/api/avatar?user=${encodeURIComponent(username)}`)
    if (res.ok) {
      const data = await res.json()
      // 兼容两种格式：{ avatar } 或 { data: { avatar } }
      partnerAvatar.value = data?.data?.avatar || data?.avatar || data?.url || null
    }
  } catch { /* ignore */ }
}

async function fetchMyAvatar() {
  if (!authStore.user?.username) return
  try {
    const res = await fetch(`/api/avatar?user=${encodeURIComponent(authStore.user.username)}`)
    if (res.ok) {
      const data = await res.json()
      // 兼容两种格式：{ avatar } 或 { data: { avatar } }
      myAvatar.value = data?.data?.avatar || data?.avatar || data?.url || null
    }
  } catch { /* ignore */ }
}

function parseVideoInfoFromUrl(): WatchRoom['video_info'] | null {
  const encodedInfo = route.query.v as string
  if (!encodedInfo) return null
  try {
    return JSON.parse(decodeURIComponent(encodedInfo))
  } catch {
    return null
  }
}

let unsubscribers: (() => void)[] = []
let syncInterval: ReturnType<typeof setInterval> | null = null

function setupSubscriptions() {
  unsubscribers.push(
    subscribe('watch_sync', (msg) => {
      const data = msg.data as any
      const syncState = data.sync_state || data.syncState
      if (syncState) handleRemoteSync(syncState)
    }),
    subscribe('watch_chat', (msg) => {
      const data = msg.data as any
      const chatMsg = data.message || data
      if (chatMsg && chatMsg.sender !== authStore.user?.username) {
        chatMessages.value.push({ id: chatMsg.id || Date.now().toString(), sender: chatMsg.sender, content: chatMsg.content, timestamp: chatMsg.timestamp || Date.now() })
        nextTick(() => { if (chatContainerRef.value) chatContainerRef.value.scrollTop = chatContainerRef.value.scrollHeight })
      }
    }),
    subscribe('watch_effect', (msg) => {
      const data = msg.data as any
      const effect = data.effect || data
      if (effect && effect.sender !== authStore.user?.username) addEffect({ id: effect.id || Date.now().toString(), type: effect.type, sender: effect.sender, timestamp: effect.timestamp || Date.now() })
    }),
    subscribe('watch_invite_accept', (msg) => {
      const data = msg.data as any
      const guestId = data.guest_id || data.guestId
      if (guestId) {
        partnerId.value = guestId
        partnerOnline.value = true
        if (!roomMembers.value.includes(guestId)) roomMembers.value.push(guestId)
        fetchPartnerAvatar(guestId)
        toast.success(`${guestId} 已加入房间`)
      }
    }),
    subscribe('watch_leave', () => {
      partnerOnline.value = false
      roomMembers.value = roomMembers.value.filter(m => m !== partnerId.value)
      toast.info('对方已离开房间')
    }),
  )
}

// 定期同步进度（每5秒）
function startPeriodicSync() {
  syncInterval = setInterval(() => {
    if (artInstance.value && artInstance.value.playing) {
      sendSyncState(true)
    }
  }, 5000)
}


async function initRoom() {
  roomLoading.value = true
  roomError.value = ''
  try {
    // 首先尝试从 URL 参数获取视频信息
    const urlVideoInfo = parseVideoInfoFromUrl()
    if (urlVideoInfo) {
      videoInfo.value = urlVideoInfo
      localStorage.setItem(`watch-together-${roomId.value}`, JSON.stringify(urlVideoInfo))
    }

    // 从后端获取房间信息
    const res = await request.get(`/watch-together/rooms/${roomId.value}`) as any
    if (!res || res.code === 404) {
      // 如果后端没有房间信息，尝试从 localStorage 获取（可能已通过 URL 保存）
      if (!videoInfo.value) {
        const stored = localStorage.getItem(`watch-together-${roomId.value}`)
        if (stored) {
          videoInfo.value = JSON.parse(stored)
        } else {
          roomError.value = '房间不存在或已过期'
          return
        }
      }
    } else {
      const vi = res.videoInfo || res.video_info
      if (vi && !urlVideoInfo) {
        // 只有在 URL 中没有视频信息时才使用后端返回的
        videoInfo.value = {
          title: vi.title,
          source: vi.source,
          id: vi.id,
          episode_index: vi.episode_index ?? vi.episodeIndex ?? 0,
          video_url: vi.video_url ?? vi.videoUrl ?? '',
          cover: vi.cover || '',
        }
        localStorage.setItem(`watch-together-${roomId.value}`, JSON.stringify(videoInfo.value))
      }
      const members: string[] = []
      if (res.hostId) members.push(res.hostId)
      if (res.guestId) members.push(res.guestId)
      if (members.length > 0) roomMembers.value = members
      if (res.guestId && res.guestId !== authStore.user?.username) {
        partnerId.value = res.guestId
        partnerOnline.value = res.status === 'active'
        fetchPartnerAvatar(res.guestId)
      } else if (res.hostId && res.hostId !== authStore.user?.username) {
        partnerId.value = res.hostId
        partnerOnline.value = true
        fetchPartnerAvatar(res.hostId)
      }
    }

    isHost.value = authStore.user?.username === (hostUsername.value || (roomMembers.value[0]))
    if (authStore.user?.username && !roomMembers.value.includes(authStore.user.username)) {
      roomMembers.value.push(authStore.user.username)
    }

    // 被邀请者通过 REST API 加入房间
    if (!isHost.value) {
      try {
        await request.post(`/watch-together/rooms/${roomId.value}/accept`)
        if (hostUsername.value && !roomMembers.value.includes(hostUsername.value)) {
          roomMembers.value.push(hostUsername.value)
        }
        partnerId.value = hostUsername.value || roomMembers.value.find(m => m !== authStore.user?.username) || ''
        partnerOnline.value = true
        if (partnerId.value) fetchPartnerAvatar(partnerId.value)
        toast.success('已加入共同观影房间')
      } catch { /* room may already be accepted */ }
    }

    // 初始化播放器在 finally 中 roomLoading=false 后执行
    startPeriodicSync()
  } catch (err) {
    console.error('初始化房间失败:', err)
    roomError.value = '加载房间失败'
  } finally {
    roomLoading.value = false
    // 等 DOM 渲染出 playerRef 后再初始化播放器
    await nextTick()
    if (videoInfo.value && playerRef.value) {
      initPlayer(videoInfo.value.video_url, videoInfo.value.cover)
    }
  }
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
  connect()
  setupSubscriptions()
  fetchMyAvatar()
  initRoom()
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
  unsubscribers.forEach(fn => fn())
  if (syncInterval) clearInterval(syncInterval)
  if (artInstance.value) { artInstance.value.destroy(); artInstance.value = null }
})
</script>

<template>
  <PageLayout>
    <!-- 未登录 -->
    <div v-if="!authStore.isLoggedIn" class="flex flex-col items-center justify-center min-h-[60vh] text-center">
      <h1 class="text-2xl font-bold mb-4 text-gray-800 dark:text-white">请先登录</h1>
      <p class="text-gray-500 mb-6">共同观影功能需要登录后使用</p>
      <router-link to="/" class="px-6 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600">返回首页</router-link>
    </div>

    <!-- 无视频信息 -->
    <div v-else-if="roomLoading" class="flex flex-col items-center justify-center min-h-[60vh] text-center">
      <div class="w-8 h-8 border-3 border-blue-500 border-t-transparent rounded-full animate-spin mb-4"></div>
      <p class="text-gray-500">正在加载房间...</p>
    </div>

    <div v-else-if="roomError || !videoInfo" class="flex flex-col items-center justify-center min-h-[60vh] text-center">
      <h1 class="text-2xl font-bold mb-4 text-gray-800 dark:text-white">{{ roomError || '房间不存在或已过期' }}</h1>
      <p class="text-gray-500 mb-6">请重新创建共同观影房间</p>
      <router-link to="/" class="px-6 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600">返回首页</router-link>
    </div>

    <!-- 主内容 -->
    <div v-else class="min-h-screen bg-gray-50 dark:bg-gray-900">
      <!-- 顶部栏 -->
      <div class="sticky top-0 z-30 bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 px-4 py-3">
        <div class="flex items-center justify-between max-w-7xl mx-auto">
          <div class="flex items-center gap-3">
            <div>
              <h1 class="font-semibold text-gray-900 dark:text-white truncate max-w-[200px] md:max-w-none">{{ videoInfo.title }}</h1>
              <p class="text-xs text-gray-500">第 {{ videoInfo.episode_index + 1 }} 集 · 共同观影</p>
            </div>
          </div>
          <div class="flex items-center gap-3">
            <!-- 群聊风格头像组 -->
            <div class="flex items-center gap-2">
              <!-- 头像堆叠容器 -->
              <div class="relative flex items-center">
                <!-- 自己的头像 -->
                <div class="relative z-10">
                  <div class="w-9 h-9 rounded-full overflow-hidden ring-2 ring-white dark:ring-gray-800 shadow-md">
                    <img v-if="myAvatar" :src="myAvatar" :alt="authStore.user?.username" class="w-full h-full object-cover" />
                    <div v-else class="w-full h-full bg-gradient-to-br from-blue-400 to-blue-600 flex items-center justify-center text-white text-sm font-semibold">
                      {{ authStore.user?.username?.charAt(0).toUpperCase() }}
                    </div>
                  </div>
                  <!-- 自己的在线状态点 -->
                  <div class="absolute -bottom-0.5 -right-0.5 w-3 h-3 bg-green-500 rounded-full ring-2 ring-white dark:ring-gray-800"></div>
                </div>
                <!-- 对方的头像 (重叠显示) -->
                <div v-if="partnerId" class="relative -ml-3 z-0">
                  <div class="w-9 h-9 rounded-full overflow-hidden ring-2 ring-white dark:ring-gray-800 shadow-md">
                    <img v-if="partnerAvatar" :src="partnerAvatar" :alt="partnerId" class="w-full h-full object-cover" />
                    <div v-else class="w-full h-full bg-gradient-to-br from-purple-400 to-pink-500 flex items-center justify-center text-white text-sm font-semibold">
                      {{ partnerId.charAt(0).toUpperCase() }}
                    </div>
                  </div>
                  <!-- 对方的在线状态点 -->
                  <div :class="['absolute -bottom-0.5 -right-0.5 w-3 h-3 rounded-full ring-2 ring-white dark:ring-gray-800', partnerOnline ? 'bg-green-500' : 'bg-gray-400']"></div>
                </div>
                <!-- 等待中的占位头像 -->
                <div v-else class="relative -ml-3 z-0">
                  <div class="w-9 h-9 rounded-full overflow-hidden ring-2 ring-white dark:ring-gray-800 shadow-md bg-gray-200 dark:bg-gray-700 flex items-center justify-center">
                    <span class="text-gray-400 text-xs">?</span>
                  </div>
                </div>
              </div>
              <!-- 房间信息 -->
              <div class="hidden md:block">
                <p class="text-sm font-medium text-gray-900 dark:text-white">
                  {{ authStore.user?.username }}{{ partnerId ? ` & ${partnerId}` : '' }}
                </p>
                <p class="text-xs text-gray-500">
                  {{ roomMembers.length }} 人在线{{ partnerId ? (partnerOnline ? '' : ' · 对方离线') : ' · 等待加入' }}
                </p>
              </div>
            </div>
            <button @click="handleLeave" class="flex items-center gap-1 px-3 py-1.5 text-sm text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-colors">
              <LogOut class="w-4 h-4" /><span class="hidden md:inline">退出</span>
            </button>
          </div>
        </div>
      </div>

      <!-- 主内容区 -->
      <div class="max-w-7xl mx-auto p-4">
        <div :class="['grid gap-4', isMobile ? 'grid-cols-1' : 'grid-cols-3']">
          <!-- 播放器区域 -->
          <div :class="[isMobile ? '' : 'col-span-2']">
            <div class="aspect-video bg-black rounded-xl overflow-hidden relative">
              <div ref="playerRef" class="w-full h-full"></div>
              <!-- 特效覆盖层 - 精美飘动效果 -->
              <div class="pointer-events-none absolute inset-0 z-10 overflow-hidden">
                <!-- 飘动的 emoji 粒子 - 从中间向上飘动 -->
                <div
                  v-for="particle in floatingEmojis"
                  :key="particle.id"
                  class="absolute floating-emoji"
                  :style="{
                    left: particle.x + '%',
                    top: '50%',
                    animationDelay: particle.delay + 's',
                    '--float-x': (Math.random() - 0.5) * 80 + 'px',
                    '--float-y': (Math.random() - 0.5) * 40 + 'px',
                  }"
                >
                  <span class="text-3xl md:text-4xl drop-shadow-lg">{{ particle.emoji }}</span>
                </div>
                <!-- 特效通知横幅 -->
                <TransitionGroup name="effect-banner">
                  <div
                    v-for="effect in effects"
                    :key="effect.id"
                    class="absolute top-4 left-1/2 -translate-x-1/2 effect-banner"
                  >
                    <div class="flex items-center gap-2 px-4 py-2 bg-gradient-to-r from-pink-500/90 via-purple-500/90 to-indigo-500/90 backdrop-blur-sm rounded-full shadow-lg shadow-purple-500/30">
                      <span class="text-2xl animate-pulse">{{ effectTypes.find(e => e.type === effect.type)?.emoji }}</span>
                      <span class="text-white font-medium text-sm">{{ effect.sender }} 送出了 {{ effectTypes.find(e => e.type === effect.type)?.label }}</span>
                    </div>
                  </div>
                </TransitionGroup>
              </div>
              <!-- 非房主：遮挡底部控制栏，阻止操作 -->
              <div v-if="!isHost" class="absolute bottom-0 left-0 right-0 h-12 z-20 cursor-not-allowed" @click.stop.prevent="toast.info('仅房主可控制播放')"></div>
            </div>
            <!-- 移动端特效按钮 -->
            <div v-if="isMobile" class="mt-3 bg-white dark:bg-gray-800 rounded-xl p-2">
              <div class="flex justify-around">
                <button
                  v-for="et in effectTypes" :key="et.type"
                  @click="handleSendEffect(et.type)"
                  class="flex flex-col items-center gap-1 p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
                >
                  <span class="text-xl">{{ et.emoji }}</span>
                  <span class="text-[10px] text-gray-500">{{ et.label }}</span>
                </button>
              </div>
            </div>
          </div>

          <!-- 桌面端侧边栏 -->
          <div v-if="!isMobile" class="flex flex-col gap-4">
            <!-- 特效按钮 -->
            <div class="bg-white dark:bg-gray-800 rounded-xl p-3">
              <h3 class="text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">互动特效</h3>
              <div class="grid grid-cols-3 gap-2">
                <button
                  v-for="et in effectTypes" :key="et.type"
                  @click="handleSendEffect(et.type)"
                  class="flex flex-col items-center gap-1 p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
                >
                  <span class="text-xl">{{ et.emoji }}</span>
                  <span class="text-xs text-gray-500">{{ et.label }}</span>
                </button>
              </div>
            </div>

            <!-- 聊天区域 -->
            <div class="bg-white dark:bg-gray-800 rounded-xl flex flex-col" :class="showChat ? 'flex-1 min-h-[300px]' : ''">
              <div class="flex items-center justify-between p-3" :class="showChat ? 'border-b border-gray-200 dark:border-gray-700' : ''">
                <div class="flex items-center gap-2">
                  <MessageCircle class="w-4 h-4 text-blue-500" />
                  <span class="text-sm font-medium text-gray-800 dark:text-white">聊天</span>
                </div>
                <button @click="showChat = !showChat" class="text-xs text-gray-500 hover:text-gray-700 dark:hover:text-gray-300">{{ showChat ? '收起' : '展开' }}</button>
              </div>
              <div v-if="showChat" class="flex flex-col flex-1">
                <div ref="chatContainerRef" class="flex-1 overflow-y-auto p-3 space-y-2 max-h-[250px]">
                  <p v-if="chatMessages.length === 0" class="text-center text-gray-400 text-sm py-8">暂无消息，发送第一条吧～</p>
                  <div v-for="msg in chatMessages" :key="msg.id" :class="['flex items-end gap-2', msg.sender === authStore.user?.username ? 'flex-row-reverse' : 'flex-row']">
                    <!-- 头像 -->
                    <img v-if="msg.sender === authStore.user?.username ? myAvatar : partnerAvatar"
                         :src="msg.sender === authStore.user?.username ? myAvatar! : partnerAvatar!"
                         :alt="msg.sender"
                         class="w-7 h-7 rounded-full object-cover flex-shrink-0" />
                    <div v-else class="w-7 h-7 rounded-full bg-gradient-to-br from-blue-400 to-purple-500 flex items-center justify-center text-white text-xs font-semibold flex-shrink-0">
                      {{ msg.sender.charAt(0).toUpperCase() }}
                    </div>
                    <!-- 消息气泡 -->
                    <div :class="['max-w-[70%] px-3 py-2 rounded-2xl text-sm', msg.sender === authStore.user?.username ? 'bg-blue-500 text-white' : 'bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white']">
                      {{ msg.content }}
                    </div>
                  </div>
                </div>
                <div class="p-3 border-t border-gray-200 dark:border-gray-700">
                  <div class="flex gap-2">
                    <input v-model="chatInput" type="text" placeholder="发送消息..." @keyup.enter="handleSendChat" class="flex-1 px-3 py-2 bg-gray-100 dark:bg-gray-700 rounded-lg text-sm text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500" />
                    <button @click="handleSendChat" :disabled="!chatInput.trim()" class="p-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed">
                      <Send class="w-4 h-4" />
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 移动端聊天区域 -->
        <div v-if="isMobile" class="mt-4 bg-white dark:bg-gray-800 rounded-xl">
          <div class="flex items-center justify-between p-3" :class="showChat ? 'border-b border-gray-200 dark:border-gray-700' : ''">
            <div class="flex items-center gap-2">
              <MessageCircle class="w-4 h-4 text-blue-500" />
              <span class="text-sm font-medium text-gray-800 dark:text-white">聊天</span>
            </div>
            <button @click="showChat = !showChat" class="text-xs text-gray-500">{{ showChat ? '收起' : '展开' }}</button>
          </div>
          <div v-if="showChat" class="flex flex-col">
            <div ref="chatContainerRef" class="max-h-[200px] overflow-y-auto p-3 space-y-2">
              <p v-if="chatMessages.length === 0" class="text-center text-gray-400 text-sm py-4">暂无消息</p>
              <div v-for="msg in chatMessages" :key="msg.id" :class="['flex items-end gap-2', msg.sender === authStore.user?.username ? 'flex-row-reverse' : 'flex-row']">
                <!-- 头像 -->
                <img v-if="msg.sender === authStore.user?.username ? myAvatar : partnerAvatar"
                     :src="msg.sender === authStore.user?.username ? myAvatar! : partnerAvatar!"
                     :alt="msg.sender"
                     class="w-7 h-7 rounded-full object-cover flex-shrink-0" />
                <div v-else class="w-7 h-7 rounded-full bg-gradient-to-br from-blue-400 to-purple-500 flex items-center justify-center text-white text-xs font-semibold flex-shrink-0">
                  {{ msg.sender.charAt(0).toUpperCase() }}
                </div>
                <!-- 消息气泡 -->
                <div :class="['max-w-[70%] px-3 py-2 rounded-2xl text-sm', msg.sender === authStore.user?.username ? 'bg-blue-500 text-white' : 'bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white']">
                  {{ msg.content }}
                </div>
              </div>
            </div>
            <div class="p-3 border-t border-gray-200 dark:border-gray-700">
              <div class="flex gap-2">
                <input v-model="chatInput" type="text" placeholder="发送消息..." @keyup.enter="handleSendChat" class="flex-1 px-3 py-2 bg-gray-100 dark:bg-gray-700 rounded-lg text-sm text-gray-900 dark:text-white focus:outline-none" />
                <button @click="handleSendChat" :disabled="!chatInput.trim()" class="p-2 bg-blue-500 text-white rounded-lg disabled:opacity-50">
                  <Send class="w-4 h-4" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- WebSocket 连接状态 -->
      <div v-if="!isConnected" class="fixed bottom-4 left-4 bg-yellow-500 text-white px-4 py-2 rounded-lg text-sm z-50">
        正在连接服务器...
      </div>
    </div>
  </PageLayout>
</template>

<style scoped>
/* 飘动的 emoji 动画 - 从中间向上散开 */
.floating-emoji {
  animation: floatUp 3s ease-out forwards;
  opacity: 0;
  transform: translate(-50%, -50%);
}

@keyframes floatUp {
  0% {
    transform: translate(-50%, -50%) translateX(0) translateY(var(--float-y, 0)) scale(0.3) rotate(0deg);
    opacity: 0;
  }
  15% {
    opacity: 1;
    transform: translate(-50%, -50%) translateX(calc(var(--float-x, 0) * 0.3)) translateY(calc(var(--float-y, 0) - 20px)) scale(1.2) rotate(-5deg);
  }
  40% {
    opacity: 1;
    transform: translate(-50%, -50%) translateX(calc(var(--float-x, 0) * 0.7)) translateY(-60px) scale(1) rotate(5deg);
  }
  70% {
    opacity: 0.7;
    transform: translate(-50%, -50%) translateX(var(--float-x, 0)) translateY(-100px) scale(0.9) rotate(-3deg);
  }
  100% {
    transform: translate(-50%, -50%) translateX(calc(var(--float-x, 0) * 1.2)) translateY(-150px) scale(0.5) rotate(0deg);
    opacity: 0;
  }
}

/* 特效横幅动画 */
.effect-banner {
  animation: bannerPop 0.5s cubic-bezier(0.68, -0.55, 0.265, 1.55);
}

@keyframes bannerPop {
  0% {
    transform: translateX(-50%) scale(0) translateY(-20px);
    opacity: 0;
  }
  60% {
    transform: translateX(-50%) scale(1.1) translateY(0);
  }
  100% {
    transform: translateX(-50%) scale(1) translateY(0);
    opacity: 1;
  }
}

/* TransitionGroup 动画 */
.effect-banner-enter-active {
  animation: bannerPop 0.5s cubic-bezier(0.68, -0.55, 0.265, 1.55);
}

.effect-banner-leave-active {
  animation: bannerFadeOut 0.3s ease-out forwards;
}

@keyframes bannerFadeOut {
  0% {
    transform: translateX(-50%) scale(1) translateY(0);
    opacity: 1;
  }
  100% {
    transform: translateX(-50%) scale(0.8) translateY(-10px);
    opacity: 0;
  }
}
</style>

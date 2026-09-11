<script setup lang="ts">
import { ref, shallowRef, onMounted, onUnmounted, computed, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageLayout from '@/components/PageLayout.vue'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useWatchReactions } from '@/composables/useWatchReactions'
import WatchReactionDock from '@/components/watch-together/WatchReactionDock.vue'
import WatchReactionStage from '@/components/watch-together/WatchReactionStage.vue'
import { isWatchEffectType } from '@/utils/watchReactions'
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
const reactionTarget = shallowRef<HTMLElement | null>(null)
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
const { bursts, activity, addEffect } = useWatchReactions()
const effectsEnabled = ref(true)
const roomLoading = ref(true)
const roomError = ref('')

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
  // 挂到播放器内部，网页全屏和浏览器全屏时仍能看到互动。
  reactionTarget.value = artInstance.value.template.$player
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

let effectSequence = 0
function createEffectId() { return `effect-${Date.now().toString(36)}-${++effectSequence}` }

function handleSendEffect(type: WatchEffectType) {
  if (!authStore.user) return
  const effect: WatchEffect = { id: createEffectId(), type, sender: authStore.user.username, timestamp: Date.now() }
  try {
    const sent = sendMessage('/watch-together', { type: 'watch_effect', room_id: roomId.value, effect })
    if (!sent) {
      toast.error('互动发送失败，请连接恢复后重试')
      return
    }
    addEffect(effect)
  } catch {
    toast.error('互动发送失败，请稍后重试')
  }
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
      if (!data || typeof data !== 'object') return
      const effectRoomId = data.room_id ?? data.roomId
      if (effectRoomId && effectRoomId !== roomId.value) return
      const effect = data.effect || data
      if (isWatchEffectType(effect?.type) && typeof effect.sender === 'string' && effect.sender !== authStore.user?.username) {
        addEffect({ id: effect.id || createEffectId(), type: effect.type, sender: effect.sender, timestamp: effect.timestamp || Date.now() })
      }
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
    <div v-else class="scene-page-surface min-h-screen bg-gray-50 dark:bg-gray-900">
      <!-- 顶部栏 -->
      <div class="watch-room-header sticky top-0 z-30 bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 px-4 py-3">
        <div class="flex items-center justify-between max-w-7xl mx-auto">
          <div class="flex min-w-0 flex-1 items-center gap-3">
            <div class="min-w-0">
              <h1 class="font-semibold text-gray-900 dark:text-white truncate max-w-[200px] md:max-w-none">{{ videoInfo.title }}</h1>
              <p class="text-xs text-gray-500">第 {{ videoInfo.episode_index + 1 }} 集 · 共同观影</p>
            </div>
          </div>
          <div class="flex flex-shrink-0 items-center gap-3">
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
            <button @click="handleLeave" aria-label="退出共同观影房间" class="flex items-center gap-1 px-3 py-1.5 text-sm text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-colors">
              <LogOut class="w-4 h-4" /><span class="hidden md:inline">退出</span>
            </button>
          </div>
        </div>
      </div>

      <!-- 主内容区 -->
      <div class="max-w-7xl mx-auto p-4">
        <div :class="['grid gap-4', isMobile ? 'grid-cols-1' : 'grid-cols-3']">
          <!-- 播放器区域 -->
          <div class="min-w-0" :class="[isMobile ? '' : 'col-span-2']">
            <div class="watch-player aspect-video bg-black rounded-xl overflow-hidden relative">
              <div ref="playerRef" class="w-full h-full"></div>
              <Teleport :to="reactionTarget || 'body'" :disabled="!reactionTarget">
                <WatchReactionStage v-if="effectsEnabled" :bursts="bursts" :current-user="authStore.user?.username || ''" />
              </Teleport>
              <!-- 非房主：遮挡底部控制栏，阻止操作 -->
              <div v-if="!isHost" class="absolute bottom-0 left-0 right-0 h-12 z-20 cursor-not-allowed" @click.stop.prevent="toast.info('仅房主可控制播放')"></div>
            </div>
            <WatchReactionDock
              v-model:effects-enabled="effectsEnabled"
              :connected="isConnected"
              :activity="activity"
              :current-user="authStore.user?.username || ''"
              @send="handleSendEffect"
            />
          </div>

          <!-- 桌面端侧边栏 -->
          <div v-if="!isMobile" class="flex min-w-0 flex-col gap-4">
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
                    <input v-model="chatInput" type="text" placeholder="发送消息..." @keyup.enter="handleSendChat" class="min-w-0 flex-1 px-3 py-2 bg-gray-100 dark:bg-gray-700 rounded-lg text-sm text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500" />
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
                <input v-model="chatInput" type="text" placeholder="发送消息..." @keyup.enter="handleSendChat" class="min-w-0 flex-1 px-3 py-2 bg-gray-100 dark:bg-gray-700 rounded-lg text-sm text-gray-900 dark:text-white focus:outline-none" />
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
.watch-player { container-type: inline-size; }
@container (max-width: 360px) {
  .watch-player :deep(.art-control:not(.art-control-time)) { min-width: 32px; width: 32px; }
  .watch-player :deep(.art-control-time) { padding: 0 3px; font-size: 11px; }
}
:global(html[data-theme='chinese-red'] .watch-room-header) { top: calc(var(--china-controls-height) + 16px); }
@media (max-width: 767px) {
  :global(html[data-theme='chinese-red'] .watch-room-header) { top: calc(3rem + var(--china-mobile-controls-row)); }
}

</style>

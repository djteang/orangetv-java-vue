<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { X, Smile, Image as ImageIcon, Loader2, Wallpaper, Mic, Play, Pause } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { useWebSocket } from '@/services/websocket'
import { useToast } from '@/composables/useToast'
import request from '@/api/index'

interface ChatMessage {
  id: number
  conversation_id: string
  sender_id: number
  sender_name: string
  content: string
  message_type: string
  timestamp: number
  is_read: boolean
  voice_duration?: number
}

const props = defineProps<{
  friendUsername: string
  friendAvatar?: string
  conversationId?: string
}>()

const emit = defineEmits<{
  close: []
}>()

const authStore = useAuthStore()
const { subscribe } = useWebSocket()
const toast = useToast()

const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const showEmojiPicker = ref(false)
const uploading = ref(false)
const convId = ref(props.conversationId || '')
const loading = ref(true)
const sending = ref(false)
const messagesContainer = ref<HTMLElement | null>(null)
const previewImageUrl = ref<string | null>(null)

// Voice recording state
const showRecordingOverlay = ref(false) // 是否显示录音界面
const isRecording = ref(false)
const recordingTime = ref(0)
const mediaRecorder = ref<MediaRecorder | null>(null)
const audioChunks = ref<Blob[]>([])
const recordingTimer = ref<number | null>(null)
const playingVoiceId = ref<number | null>(null)
const currentAudio = ref<HTMLAudioElement | null>(null)
const voiceProgress = ref<Record<number, number>>({}) // 每条语音的播放进度
const recordingStartY = ref(0)
const recordingCurrentY = ref(0)
const recordingStatus = ref<'recording' | 'send' | 'cancel'>('recording') // 录音状态
const isPressingVoiceButton = ref(false) // 是否正在按住语音按钮
const longPressTimer = ref<number | null>(null) // 长按延迟计时器

// Drag state
const chatWindowRef = ref<HTMLElement | null>(null)
const isDragging = ref(false)
const dragOffset = ref({ x: 0, y: 0 })
const windowPosition = ref({ x: 0, y: 0 })
const hasBeenDragged = ref(false)

// Background settings (per friend, stored in backend)
const showBgSettings = ref(false)
const chatBackground = ref<string | null>(null)
const uploadingBg = ref(false)
const loadingBg = ref(false)
const selectingBgIndex = ref<number | null>(null)

// Preset backgrounds (external URLs)
const presetBackgrounds = [
  '',  // 默认无背景
  'https://images.unsplash.com/photo-1529626455594-4ff0802cfb7e?w=400',
  'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400',
  'https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=400',
  'https://images.unsplash.com/photo-1517841905240-472988babdf9?w=400',
  'https://images.unsplash.com/photo-1488426862026-3ee34a7d66df?w=400',
]

// Emoji list
const emojiList = [
  '😀', '😃', '😄', '😁', '😅', '😂', '🤣', '😊', '😇', '🙂', '😉', '😍',
  '🥰', '😘', '😗', '😋', '😛', '🤔', '🤨', '😐', '😑', '😶', '🙄', '😏',
  '😣', '😥', '😮', '🤐', '😯', '😪', '😫', '🥱', '😴', '😌', '😜', '🤤',
  '😒', '😓', '😔', '😕', '🙃', '🤑', '😲', '🙁', '😖', '😞', '😟', '😤',
  '😢', '😭', '😦', '😧', '😨', '😩', '🤯', '😱', '🥵', '🥶', '😳', '👍',
  '👎', '👏', '🙌', '🤝', '❤️', '🧡', '💛', '💚', '💙', '💜', '🖤', '🔥',
  '✨', '🎉', '🎊', '💯', '💪', '🙏', '💕', '💖', '💗', '💘', '💝', '😎'
]

// Drag functions
function startDrag(e: MouseEvent) {
  if ((e.target as HTMLElement).closest('button')) return
  isDragging.value = true
  const rect = chatWindowRef.value?.getBoundingClientRect()
  if (rect) {
    dragOffset.value = {
      x: e.clientX - rect.left,
      y: e.clientY - rect.top
    }
  }
  document.addEventListener('mousemove', onDrag)
  document.addEventListener('mouseup', stopDrag)
}

function onDrag(e: MouseEvent) {
  if (!isDragging.value) return
  hasBeenDragged.value = true
  const newX = e.clientX - dragOffset.value.x
  const newY = e.clientY - dragOffset.value.y

  // Keep within viewport bounds
  const maxX = window.innerWidth - (chatWindowRef.value?.offsetWidth || 400)
  const maxY = window.innerHeight - (chatWindowRef.value?.offsetHeight || 600)

  windowPosition.value = {
    x: Math.max(0, Math.min(newX, maxX)),
    y: Math.max(0, Math.min(newY, maxY))
  }
}

function stopDrag() {
  isDragging.value = false
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', stopDrag)
}

// Background functions
async function loadChatBackground() {
  loadingBg.value = true
  try {
    const res = await request.get(`/chat/background/${props.friendUsername}`) as any
    chatBackground.value = res?.background || null
  } catch (e) {
    console.error('Load background failed:', e)
  } finally {
    loadingBg.value = false
  }
}

async function saveChatBackgroundToServer(url: string) {
  try {
    await request.post(`/chat/background/${props.friendUsername}`, {
      background: url
    })
  } catch (e) {
    console.error('Save background failed:', e)
  }
}

async function selectBackground(bg: string, index: number) {
  if (!bg) {
    // Clear background
    chatBackground.value = null
    await saveChatBackgroundToServer('')
    return
  }

  // If it's already a local URL (starts with /uploads), just save it
  if (bg.startsWith('/uploads')) {
    chatBackground.value = bg
    await saveChatBackgroundToServer(bg)
    return
  }

  // Download external image to server
  selectingBgIndex.value = index
  try {
    const res = await request.post('/upload/download-image', { url: bg }) as any
    if (res && res.url) {
      chatBackground.value = res.url
      await saveChatBackgroundToServer(res.url)
    }
  } catch (e) {
    console.error('Failed to download background:', e)
  } finally {
    selectingBgIndex.value = null
  }
}

async function uploadBackground(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  // Validate file type
  if (!file.type.startsWith('image/')) {
    console.error('Invalid file type')
    input.value = ''
    return
  }

  // Validate file size (5MB)
  if (file.size > 5 * 1024 * 1024) {
    console.error('File too large')
    input.value = ''
    return
  }

  uploadingBg.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    const res = await request.post('/upload/chat-image', formData) as any
    if (res && res.url) {
      chatBackground.value = res.url
      await saveChatBackgroundToServer(res.url)
    }
  } catch (e) {
    console.error('Failed to upload background:', e)
  } finally {
    uploadingBg.value = false
    input.value = ''
  }
}

// Format time like WeChat (adjust for China timezone)
function formatMessageTime(timestamp: number): string {
  // 如果服务器时区是 UTC，但数据库存储的是中国时间的时间戳
  // 需要加上 8 小时的偏移（8 * 60 * 60 * 1000 = 28800000）
  const adjustedTimestamp = timestamp + (8 * 60 * 60 * 1000)
  const date = new Date(adjustedTimestamp)
  const now = new Date()

  const hours = date.getHours().toString().padStart(2, '0')
  const minutes = date.getMinutes().toString().padStart(2, '0')
  const timeStr = `${hours}:${minutes}`

  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const yesterday = new Date(today.getTime() - 24 * 60 * 60 * 1000)
  const dayBeforeYesterday = new Date(today.getTime() - 2 * 24 * 60 * 60 * 1000)
  const msgDay = new Date(date.getFullYear(), date.getMonth(), date.getDate())

  if (msgDay.getTime() === today.getTime()) {
    return timeStr
  } else if (msgDay.getTime() === yesterday.getTime()) {
    return `昨天 ${timeStr}`
  } else if (msgDay.getTime() === dayBeforeYesterday.getTime()) {
    return `前天 ${timeStr}`
  } else if (date.getFullYear() === now.getFullYear()) {
    return `${date.getMonth() + 1}月${date.getDate()}日 ${timeStr}`
  } else {
    return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日 ${timeStr}`
  }
}

// Check if should show time separator (more than 5 minutes apart)
function shouldShowTime(currentMsg: ChatMessage, prevMsg: ChatMessage | null): boolean {
  if (!prevMsg) return true
  const diff = currentMsg.timestamp - prevMsg.timestamp
  return diff > 5 * 60 * 1000 // 5 minutes
}

// Compute messages with time separators
const messagesWithTime = computed(() => {
  return messages.value.map((msg, index) => ({
    ...msg,
    showTime: shouldShowTime(msg, index > 0 ? messages.value[index - 1] : null)
  }))
})

// Check if message is from current user
function isOwnMessage(msg: ChatMessage): boolean {
  return msg.sender_name === authStore.user?.username
}

// Get avatar for message sender
function getMessageAvatar(msg: ChatMessage): string | null {
  if (isOwnMessage(msg)) {
    return authStore.user?.avatar || null
  }
  return props.friendAvatar || null
}

// Initialize conversation
async function initConversation() {
  loading.value = true
  try {
    if (!convId.value) {
      const res = await request.post('/chat/conversations', {
        participants: [props.friendUsername],
        type: 'private'
      }) as any
      convId.value = res.id
    }
    await loadMessages()
  } finally {
    loading.value = false
  }
}

// Load messages
async function loadMessages() {
  if (!convId.value) return
  const data = await request.get(`/chat/messages?conversationId=${convId.value}&limit=50`) as any[]
  messages.value = (data || []).reverse()
  // 使用 nextTick 确保 DOM 更新后再滚动
  await nextTick()
  // 添加小延迟确保渲染完成
  setTimeout(() => {
    scrollToBottom()
  }, 100)
}

// Send message
async function sendMessage(type: 'text' | 'image' | 'voice' = 'text', content?: string, voiceDuration?: number) {
  const msgContent = content || inputText.value.trim()
  if (!msgContent || !convId.value) return

  sending.value = true
  try {
    const payload: any = {
      conversationId: convId.value,
      content: msgContent,
      messageType: type
    }

    if (type === 'voice' && voiceDuration) {
      payload.voiceDuration = voiceDuration
    }

    const newMsg = await request.post('/chat/messages', payload) as ChatMessage
    messages.value.push(newMsg)
    inputText.value = ''
    showEmojiPicker.value = false
    scrollToBottom()
  } finally {
    sending.value = false
  }
}

// Insert emoji
function insertEmoji(emoji: string) {
  inputText.value += emoji
}

// Voice recording functions
function openRecordingOverlay() {
  showRecordingOverlay.value = true
}

function closeRecordingOverlay() {
  showRecordingOverlay.value = false
  // 如果正在录音，停止录音
  if (isRecording.value) {
    stopRecording()
  }
  // 清除长按计时器
  if (longPressTimer.value) {
    clearTimeout(longPressTimer.value)
    longPressTimer.value = null
  }
  isPressingVoiceButton.value = false
}

function onPressStart(e: MouseEvent | TouchEvent) {
  isPressingVoiceButton.value = true

  // 记录起始位置
  if (e instanceof MouseEvent) {
    recordingStartY.value = e.clientY
    recordingCurrentY.value = e.clientY
  } else if (e instanceof TouchEvent && e.touches.length > 0) {
    recordingStartY.value = e.touches[0].clientY
    recordingCurrentY.value = e.touches[0].clientY
  }

  // 设置长按延迟（500ms后开始录音）
  longPressTimer.value = window.setTimeout(() => {
    startRecording(e)
  }, 500)

  // 添加松开监听
  document.addEventListener('mouseup', onPressEnd)
  document.addEventListener('touchend', onPressEnd)
}

function onPressEnd() {
  // 清除长按计时器
  if (longPressTimer.value) {
    clearTimeout(longPressTimer.value)
    longPressTimer.value = null
  }

  // 如果正在录音，停止录音
  if (isRecording.value) {
    stopRecording()
  } else {
    // 如果没有开始录音（长按时间不足），关闭录音界面
    closeRecordingOverlay()
  }

  isPressingVoiceButton.value = false

  // 移除监听
  document.removeEventListener('mouseup', onPressEnd)
  document.removeEventListener('touchend', onPressEnd)
}

async function startRecording(e: MouseEvent | TouchEvent) {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    mediaRecorder.value = new MediaRecorder(stream)
    audioChunks.value = []

    // 设置光标
    if (e instanceof MouseEvent) {
      document.body.style.cursor = 'grabbing'
    }

    mediaRecorder.value.ondataavailable = (event) => {
      audioChunks.value.push(event.data)
    }

    mediaRecorder.value.onstop = async () => {
      const audioBlob = new Blob(audioChunks.value, { type: 'audio/webm' })

      // 根据状态决定是否发送
      if (recordingStatus.value === 'send' && recordingTime.value >= 1) {
        await sendVoiceMessage(audioBlob, recordingTime.value)
      } else if (recordingStatus.value === 'recording' && recordingTime.value < 1) {
        toast.error('录音时间太短')
      }

      stream.getTracks().forEach(track => track.stop())
      document.body.style.cursor = ''
    }

    mediaRecorder.value.start()
    isRecording.value = true
    recordingTime.value = 0
    recordingStatus.value = 'recording'

    recordingTimer.value = window.setInterval(() => {
      recordingTime.value++
      if (recordingTime.value >= 60) {
        stopRecording()
      }
    }, 1000)

    // 添加移动监听
    document.addEventListener('mousemove', onRecordingMove)
    document.addEventListener('touchmove', onRecordingMove)
  } catch (error) {
    console.error('Failed to start recording:', error)
    toast.error('无法访问麦克风，请检查权限设置')
    isPressingVoiceButton.value = false
    document.body.style.cursor = ''
  }
}

function onRecordingMove(e: MouseEvent | TouchEvent) {
  if (!isRecording.value) return

  let currentY = 0
  if (e instanceof MouseEvent) {
    currentY = e.clientY
  } else if (e instanceof TouchEvent && e.touches.length > 0) {
    currentY = e.touches[0].clientY
  }

  recordingCurrentY.value = currentY
  const deltaY = recordingStartY.value - currentY

  // 上滑超过50px表示发送
  if (deltaY > 50) {
    recordingStatus.value = 'send'
  }
  // 下滑超过50px表示取消
  else if (deltaY < -50) {
    recordingStatus.value = 'cancel'
  } else {
    recordingStatus.value = 'recording'
  }
}

function stopRecording() {
  if (mediaRecorder.value && isRecording.value) {
    mediaRecorder.value.stop()
    isRecording.value = false
    isPressingVoiceButton.value = false
    if (recordingTimer.value) {
      clearInterval(recordingTimer.value)
      recordingTimer.value = null
    }

    // 移除移动监听
    document.removeEventListener('mousemove', onRecordingMove)
    document.removeEventListener('touchmove', onRecordingMove)
    document.body.style.cursor = ''

    // 关闭录音界面
    setTimeout(() => {
      closeRecordingOverlay()
    }, 300)
  }
}

async function sendVoiceMessage(audioBlob: Blob, duration: number) {
  try {
    const formData = new FormData()
    formData.append('file', audioBlob, 'voice.webm')

    const uploadRes = await request.post('/upload/voice', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }) as { url: string }

    await sendMessage('voice', uploadRes.url, duration)
  } catch (error) {
    console.error('Failed to send voice message:', error)
    toast.error('语音发送失败')
  }
}

function playVoice(messageId: number, voiceUrl: string, duration: number) {
  // 如果正在播放同一条语音，暂停
  if (playingVoiceId.value === messageId && currentAudio.value) {
    currentAudio.value.pause()
    playingVoiceId.value = null
    return
  }

  // 停止之前的音频
  if (currentAudio.value) {
    currentAudio.value.pause()
    currentAudio.value = null
  }

  const audio = new Audio(voiceUrl)
  currentAudio.value = audio
  playingVoiceId.value = messageId

  // 初始化进度
  if (!voiceProgress.value[messageId]) {
    voiceProgress.value[messageId] = 0
  }

  // 设置开始播放位置（如果之前暂停过）
  const startProgress = voiceProgress.value[messageId]
  if (startProgress > 0 && duration > 0) {
    audio.currentTime = (startProgress / 100) * duration
  }

  // 更新进度
  const updateProgress = () => {
    if (audio.currentTime && duration > 0) {
      voiceProgress.value[messageId] = (audio.currentTime / duration) * 100
    }
  }

  audio.ontimeupdate = updateProgress

  audio.onended = () => {
    playingVoiceId.value = null
    currentAudio.value = null
    voiceProgress.value[messageId] = 0
  }

  audio.onerror = () => {
    playingVoiceId.value = null
    currentAudio.value = null
    toast.error('语音播放失败')
  }

  audio.play().catch(() => {
    playingVoiceId.value = null
    currentAudio.value = null
    toast.error('语音播放失败')
  })
}

function formatRecordingTime(seconds: number): string {
  return `${Math.floor(seconds / 60)}:${(seconds % 60).toString().padStart(2, '0')}`
}

// Handle image upload
async function handleImageUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  // Validate file type
  if (!file.type.startsWith('image/')) {
    console.error('Invalid file type')
    input.value = ''
    return
  }

  // Validate file size (5MB)
  if (file.size > 5 * 1024 * 1024) {
    console.error('File too large')
    input.value = ''
    return
  }

  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    // FormData will be handled by axios interceptor (Content-Type auto-set)
    const res = await request.post('/upload/chat-image', formData) as any
    if (res && res.url) {
      await sendMessage('image', res.url)
    } else {
      console.error('Upload response missing url:', res)
    }
  } catch (e) {
    console.error('Failed to upload image:', e)
  } finally {
    uploading.value = false
    input.value = ''
  }
}

// Preview image
function previewImage(url: string) {
  previewImageUrl.value = url
}

// Scroll to bottom
function scrollToBottom() {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

// WebSocket subscription
let unsubscribe: (() => void) | null = null

function subscribeToMessages() {
  unsubscribe = subscribe('chat_message', (msg) => {
    const data = msg.data as any
    if (data.conversation_id === convId.value && data.sender_name !== authStore.user?.username) {
      messages.value.push(data)
      scrollToBottom()
    }
  })
}

onMounted(() => {
  initConversation()
  subscribeToMessages()
  loadChatBackground()
})

onUnmounted(() => {
  if (unsubscribe) {
    unsubscribe()
  }
})

watch(() => props.conversationId, (newId) => {
  if (newId) {
    convId.value = newId
    loadMessages()
  }
})
</script>

<template>
  <div class="fixed inset-0 z-[10000] flex items-center justify-center bg-black/50 backdrop-blur-sm" @wheel.prevent @touchmove.prevent>
    <!-- Image Preview Modal -->
    <div v-if="previewImageUrl" class="fixed inset-0 z-[10001] flex items-center justify-center bg-black/80 backdrop-blur-sm" @click="previewImageUrl = null">
      <img :src="previewImageUrl" class="max-w-[90vw] max-h-[90vh] object-contain rounded-lg" />
      <button @click="previewImageUrl = null" class="absolute top-4 right-4 p-2 bg-black/50 rounded-full text-white hover:bg-black/70 transition-colors">
        <X class="w-6 h-6" />
      </button>
    </div>

    <!-- Background Settings Modal -->
    <div v-if="showBgSettings" class="fixed inset-0 z-[10001] flex items-center justify-center bg-black/50 backdrop-blur-sm" @click.self="showBgSettings = false">
      <div class="bg-white dark:bg-gray-800 rounded-2xl shadow-2xl p-5 w-80">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-base font-semibold text-gray-800 dark:text-gray-200">聊天背景</h3>
          <button @click="showBgSettings = false" class="p-1 rounded-full hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors">
            <X class="w-5 h-5 text-gray-500" />
          </button>
        </div>

        <!-- Preset backgrounds -->
        <div class="grid grid-cols-3 gap-2 mb-4">
          <button
            v-for="(bg, index) in presetBackgrounds"
            :key="index"
            @click="selectBackground(bg, index)"
            :disabled="selectingBgIndex !== null"
            :class="[
              'aspect-square rounded-lg border-2 overflow-hidden transition-all relative',
              chatBackground === bg || (!chatBackground && !bg)
                ? 'border-blue-500 ring-2 ring-blue-500/30'
                : 'border-gray-200 dark:border-gray-700 hover:border-gray-300',
              selectingBgIndex !== null ? 'opacity-60' : ''
            ]"
          >
            <div v-if="!bg" class="w-full h-full bg-[#EDEDED] dark:bg-gray-900 flex items-center justify-center text-xs text-gray-400">
              默认
            </div>
            <img v-else :src="bg" class="w-full h-full object-cover" />
            <!-- Loading overlay -->
            <div v-if="selectingBgIndex === index" class="absolute inset-0 bg-black/50 flex items-center justify-center">
              <Loader2 class="w-5 h-5 text-white animate-spin" />
            </div>
          </button>
        </div>

        <!-- Custom upload -->
        <label class="flex items-center justify-center gap-2 px-4 py-3 border-2 border-dashed border-gray-300 dark:border-gray-600 rounded-lg cursor-pointer hover:border-blue-500 dark:hover:border-blue-400 transition-colors">
          <Wallpaper v-if="!uploadingBg" class="w-5 h-5 text-gray-400" />
          <Loader2 v-else class="w-5 h-5 text-gray-400 animate-spin" />
          <span class="text-sm text-gray-500 dark:text-gray-400">上传自定义背景</span>
          <input type="file" accept="image/*" class="hidden" @change="uploadBackground" :disabled="uploadingBg || selectingBgIndex !== null" />
        </label>
      </div>
    </div>

    <!-- Chat Window -->
    <div
      ref="chatWindowRef"
      :style="hasBeenDragged ? {
        position: 'fixed',
        left: windowPosition.x + 'px',
        top: windowPosition.y + 'px',
        transform: 'none'
      } : {}"
      :class="[
        'w-full max-w-md h-[80vh] max-h-[600px] bg-white dark:bg-gray-800 rounded-2xl shadow-2xl flex flex-col overflow-hidden',
        isDragging ? 'cursor-grabbing select-none' : ''
      ]"
    >
      <!-- Header (Draggable Area) -->
      <div
        @mousedown="startDrag"
        :class="[
          'flex items-center justify-between px-4 py-3 bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700',
          isDragging ? 'cursor-grabbing' : 'cursor-grab'
        ]"
      >
        <div class="flex items-center gap-3">
          <img
            v-if="friendAvatar"
            :src="friendAvatar"
            class="w-10 h-10 rounded-full object-cover"
          />
          <div v-else class="w-10 h-10 rounded-full flex items-center justify-center bg-blue-600 text-white text-lg font-semibold">
            {{ friendUsername.charAt(0).toUpperCase() }}
          </div>
          <span class="font-medium text-gray-800 dark:text-gray-200">{{ friendUsername }}</span>
        </div>
        <div class="flex items-center gap-1">
          <!-- Background Settings Button -->
          <button @click="showBgSettings = true" class="p-2 rounded-full hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors text-gray-500 dark:text-gray-400" title="聊天背景">
            <Wallpaper class="w-5 h-5" />
          </button>
          <!-- Close Button -->
          <button @click="emit('close')" class="p-2 rounded-full hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors text-gray-500 dark:text-gray-400">
            <X class="w-5 h-5" />
          </button>
        </div>
      </div>

      <!-- Messages -->
      <div
        ref="messagesContainer"
        :class="['flex-1 overflow-y-auto px-3 py-4 overscroll-contain']"
        :style="{
          backgroundColor: chatBackground ? 'transparent' : undefined,
          backgroundImage: chatBackground ? `url(${chatBackground})` : undefined,
          backgroundSize: 'cover',
          backgroundPosition: 'center',
        }"
        @wheel.stop
        @touchmove.stop
      >
        <!-- Background overlay for better text readability -->
        <div v-if="chatBackground" class="absolute inset-0 bg-black/10 dark:bg-black/30 pointer-events-none" style="z-index: 0;"></div>

        <!-- Loading -->
        <div v-if="loading" class="flex items-center justify-center h-full relative z-10">
          <Loader2 class="w-8 h-8 text-blue-500 animate-spin" />
        </div>

        <!-- Empty State -->
        <div v-else-if="messages.length === 0" class="flex flex-col items-center justify-center h-full text-gray-400 relative z-10">
          <p class="text-sm">开始和 {{ friendUsername }} 聊天吧</p>
        </div>

        <!-- Message List -->
        <template v-else>
          <div v-for="msg in messagesWithTime" :key="msg.id" class="mb-3 relative z-10">
            <!-- Time Separator -->
            <div v-if="msg.showTime" class="flex justify-center mb-3">
              <span class="px-2 py-1 text-xs text-gray-500 dark:text-gray-400 bg-gray-200/60 dark:bg-gray-700/60 rounded">
                {{ formatMessageTime(msg.timestamp) }}
              </span>
            </div>

            <!-- Message Row -->
            <div :class="['flex items-start gap-2', isOwnMessage(msg) ? 'flex-row-reverse' : 'flex-row']">
              <!-- Avatar -->
              <img
                v-if="getMessageAvatar(msg)"
                :src="getMessageAvatar(msg)!"
                class="w-10 h-10 rounded-md object-cover flex-shrink-0"
              />
              <div v-else class="w-10 h-10 rounded-md flex items-center justify-center bg-blue-600 text-white text-sm font-semibold flex-shrink-0">
                {{ msg.sender_name?.charAt(0).toUpperCase() }}
              </div>

              <!-- Message Bubble -->
              <div class="relative max-w-[70%]">
                <!-- Bubble Arrow -->
                <div
                  :class="[
                    'absolute top-3 w-0 h-0 border-solid border-[6px]',
                    isOwnMessage(msg)
                      ? 'right-[-10px] border-l-blue-500 border-t-transparent border-r-transparent border-b-transparent'
                      : 'left-[-10px] border-r-white dark:border-r-gray-700 border-t-transparent border-l-transparent border-b-transparent'
                  ]"
                />
                <!-- Bubble Content -->
                <div
                  :class="[
                    'rounded-md overflow-hidden shadow-sm',
                    isOwnMessage(msg)
                      ? 'bg-blue-500'
                      : 'bg-white dark:bg-gray-700'
                  ]"
                >
                  <!-- Text Message -->
                  <p
                    v-if="msg.message_type === 'text' || !msg.message_type"
                    :class="[
                      'px-3 py-2 text-sm whitespace-pre-wrap break-words leading-relaxed',
                      isOwnMessage(msg) ? 'text-white' : 'text-gray-800 dark:text-gray-200'
                    ]"
                  >{{ msg.content }}</p>
                  <!-- Image Message -->
                  <img
                    v-else-if="msg.message_type === 'image'"
                    :src="msg.content"
                    class="max-w-full max-h-48 object-cover cursor-pointer"
                    @click="previewImage(msg.content)"
                    @error="() => console.error('Image load error:', msg.content)"
                  />
                  <!-- Voice Message -->
                  <div
                    v-else-if="msg.message_type === 'voice'"
                    @click="playVoice(msg.id, msg.content, msg.voice_duration || 0)"
                    :class="[
                      'relative flex items-center gap-2 px-3 py-2 cursor-pointer min-w-[120px]',
                      isOwnMessage(msg) ? 'text-white' : 'text-gray-800 dark:text-gray-200'
                    ]"
                  >
                    <!-- 进度条背景 -->
                    <div
                      v-if="voiceProgress[msg.id] > 0"
                      class="absolute inset-0 bg-white/20 dark:bg-black/20 transition-all duration-100"
                      :style="{ width: `${voiceProgress[msg.id]}%` }"
                    ></div>

                    <component
                      :is="playingVoiceId === msg.id ? Pause : Play"
                      :class="['w-5 h-5 relative z-10', isOwnMessage(msg) ? 'text-white' : 'text-blue-500']"
                    />
                    <div class="flex-1 flex items-center gap-1 relative z-10">
                      <div class="flex gap-0.5">
                        <div
                          v-for="i in 3"
                          :key="i"
                          :class="[
                            'w-0.5 rounded-full transition-all',
                            playingVoiceId === msg.id ? 'animate-pulse' : '',
                            isOwnMessage(msg) ? 'bg-white' : 'bg-blue-500'
                          ]"
                          :style="{ height: `${8 + i * 2}px` }"
                        ></div>
                      </div>
                      <span class="text-xs ml-1">{{ msg.voice_duration || 0 }}"</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </div>

      <!-- Input Area -->
      <div class="relative border-t border-gray-200 dark:border-gray-700 p-3 bg-[#F7F7F7] dark:bg-gray-800">
        <!-- Recording Overlay (只在聊天窗口内) -->
        <div
          v-if="showRecordingOverlay"
          class="absolute inset-0 z-50 bg-white/95 dark:bg-gray-800/95 backdrop-blur-sm flex flex-col items-center justify-center"
        >
          <div class="flex flex-col items-center gap-4 -mt-48">
            <!-- 麦克风图标 with 环形进度条 -->
            <div class="relative">
              <!-- 环形进度条 (只在录音时显示) -->
              <svg
                v-if="isRecording"
                class="absolute inset-0 w-full h-full -rotate-90"
                viewBox="0 0 88 88"
              >
                <circle
                  cx="44"
                  cy="44"
                  r="40"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="3"
                  :stroke-dasharray="251.2"
                  :stroke-dashoffset="251.2 - (251.2 * recordingTime / 60)"
                  class="text-blue-500 transition-all duration-1000"
                  stroke-linecap="round"
                />
              </svg>

              <div
                @mousedown.prevent="onPressStart"
                @touchstart.prevent="onPressStart"
                :class="[
                  'w-20 h-20 rounded-full flex items-center justify-center transition-all duration-200 cursor-pointer',
                  isRecording ? (recordingStatus === 'send' ? 'bg-blue-500 scale-110' : recordingStatus === 'cancel' ? 'bg-red-500 scale-110' : 'bg-gray-700') : 'bg-gray-700'
                ]"
              >
                <!-- 未录音时显示麦克风图标 -->
                <Mic v-if="!isRecording" class="w-10 h-10 text-white" />
                <!-- 录音时显示时间 -->
                <span v-else class="text-2xl font-bold text-white">{{ formatRecordingTime(recordingTime) }}</span>
              </div>

              <!-- 按钮下方提示 -->
              <div class="absolute -bottom-10 left-1/2 -translate-x-1/2 text-center whitespace-nowrap">
                <!-- 未录音时显示"长按录音" -->
                <p v-if="!isRecording" class="text-xs text-gray-400 dark:text-gray-500">
                  长按录音
                </p>
                <!-- 录音时显示状态和操作提示 -->
                <template v-else>
                  <p :class="[
                    'text-sm font-medium mb-1',
                    recordingStatus === 'send' ? 'text-blue-500' : recordingStatus === 'cancel' ? 'text-red-500' : 'text-gray-700 dark:text-gray-200'
                  ]">
                    {{ recordingStatus === 'send' ? '松开发送' : recordingStatus === 'cancel' ? '松开取消' : '正在录音' }}
                  </p>
                  <p v-if="recordingStatus === 'recording'" class="text-gray-400 dark:text-gray-500 text-xs">
                    上滑发送 · 下滑取消
                  </p>
                </template>
              </div>
            </div>
          </div>
        </div>

        <!-- Emoji Picker -->
        <div v-if="showEmojiPicker && !showRecordingOverlay" class="mb-2 p-2 bg-white dark:bg-gray-900 rounded-lg max-h-32 overflow-y-auto border border-gray-200 dark:border-gray-700">
          <div class="grid grid-cols-8 gap-1">
            <button
              v-for="emoji in emojiList"
              :key="emoji"
              @click="insertEmoji(emoji)"
              class="text-xl hover:bg-gray-100 dark:hover:bg-gray-700 rounded p-1 transition-colors"
            >
              {{ emoji }}
            </button>
          </div>
        </div>

        <!-- Input Row -->
        <div v-if="!showRecordingOverlay" class="flex items-center gap-2">
          <!-- Emoji Button -->
          <button
            @click="showEmojiPicker = !showEmojiPicker"
            :class="[
              'p-2 rounded-full transition-colors',
              showEmojiPicker ? 'bg-gray-200 dark:bg-gray-600 text-gray-700 dark:text-gray-200' : 'hover:bg-gray-200 dark:hover:bg-gray-700 text-gray-500'
            ]"
          >
            <Smile class="w-5 h-5" />
          </button>

          <!-- Voice Button - 点击打开录音界面 -->
          <button
            @click="openRecordingOverlay"
            class="p-2 rounded-full transition-colors hover:bg-gray-200 dark:hover:bg-gray-700"
            title="点击录音"
          >
            <Mic class="w-5 h-5 text-gray-500" />
          </button>

          <!-- Image Upload Button -->
          <label class="p-2 hover:bg-gray-200 dark:hover:bg-gray-700 rounded-full cursor-pointer transition-colors">
            <ImageIcon v-if="!uploading" class="w-5 h-5 text-gray-500" />
            <Loader2 v-else class="w-5 h-5 text-gray-500 animate-spin" />
            <input type="file" accept="image/*" class="hidden" @change="handleImageUpload" :disabled="uploading" />
          </label>

          <!-- Text Input -->
          <input
            v-model="inputText"
            @keyup.enter="sendMessage('text')"
            placeholder="输入消息..."
            class="flex-1 px-4 py-2 bg-white dark:bg-gray-700 rounded-md text-sm text-gray-800 dark:text-gray-200 placeholder-gray-400 border border-gray-300 dark:border-gray-600 focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none"
          />

          <!-- Send Button -->
          <button
            @click="sendMessage('text')"
            :disabled="!inputText.trim() || sending"
            class="px-4 py-2 bg-blue-500 text-white rounded-md hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed transition-colors text-sm font-medium"
          >
            <span v-if="!sending">发送</span>
            <Loader2 v-else class="w-4 h-4 animate-spin" />
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { X, Smile, Image as ImageIcon, Loader2, Wallpaper } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { useWebSocket } from '@/services/websocket'
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

const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const showEmojiPicker = ref(false)
const uploading = ref(false)
const convId = ref(props.conversationId || '')
const loading = ref(true)
const sending = ref(false)
const messagesContainer = ref<HTMLElement | null>(null)
const previewImageUrl = ref<string | null>(null)

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
  scrollToBottom()
}

// Send message
async function sendMessage(type: 'text' | 'image' = 'text', content?: string) {
  const msgContent = content || inputText.value.trim()
  if (!msgContent || !convId.value) return

  sending.value = true
  try {
    const newMsg = await request.post('/chat/messages', {
      conversationId: convId.value,
      content: msgContent,
      messageType: type
    }) as ChatMessage
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
          <div v-else class="w-10 h-10 rounded-full flex items-center justify-center text-white text-lg font-semibold" style="background-color: #2563EB">
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
              <div v-else class="w-10 h-10 rounded-md flex items-center justify-center text-white text-sm font-semibold flex-shrink-0" style="background-color: #2563EB">
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
                </div>
              </div>
            </div>
          </div>
        </template>
      </div>

      <!-- Input Area -->
      <div class="border-t border-gray-200 dark:border-gray-700 p-3 bg-[#F7F7F7] dark:bg-gray-800">
        <!-- Emoji Picker -->
        <div v-if="showEmojiPicker" class="mb-2 p-2 bg-white dark:bg-gray-900 rounded-lg max-h-32 overflow-y-auto border border-gray-200 dark:border-gray-700">
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
        <div class="flex items-center gap-2">
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

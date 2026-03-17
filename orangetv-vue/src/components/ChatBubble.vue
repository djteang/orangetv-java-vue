<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { MessageCircle, Users, Check, X, UserPlus, Search, ArrowLeft, Loader2 } from 'lucide-vue-next'
// import { useAuthStore } from '@/stores/auth'
import { useWebSocket } from '@/services/websocket'
import { useToast } from '@/composables/useToast'
import request from '@/api/index'
import ChatWindow from './ChatWindow.vue'

const router = useRouter()
// const authStore = useAuthStore()
const { connect, subscribe } = useWebSocket()
const toast = useToast()

const isOpen = ref(false)
const activeTab = ref<'friends' | 'requests' | 'invites'>('friends')

interface Friend {
  username: string
  avatar?: string
  status?: 'online' | 'offline'
}
interface FriendReq {
  id: number
  from_user: string
  from_user_avatar?: string
  to_user: string
  to_user_avatar?: string
  message?: string
  status: string
  direction: 'sent' | 'received'
  created_at: number
  updated_at?: number
}
interface WatchInvite {
  id: string
  from: string
  to: string
  roomId: string
  videoTitle: string
  timestamp: number
  expired?: boolean
  direction: 'sent' | 'received'
}

const friends = ref<Friend[]>([])
const friendRequests = ref<FriendReq[]>([])
const watchInvites = ref<WatchInvite[]>([])
const loading = ref(false)
const checkingRoom = ref<string | null>(null)

// Load watch invites from backend API
async function loadWatchInvites() {
  try {
    const data = await request.get('/watch-together/invites') as any[]
    watchInvites.value = (data || []).map(inv => ({
      id: inv.id,
      from: inv.from,
      to: inv.to,
      roomId: inv.roomId,
      videoTitle: inv.videoTitle || '未知视频',
      timestamp: inv.timestamp,
      expired: inv.expired || false,
      direction: inv.direction || 'received',
    }))
  } catch { /* ignore */ }
}

// Add friend search state
const showAddFriend = ref(false)
const searchKeyword = ref('')
const searchResults = ref<{ id: number; username: string; avatar?: string; isFriend?: boolean }[]>([])
const searching = ref(false)
const sendingRequest = ref<number | null>(null)
const hasSearched = ref(false) // 标记是否已进行过搜索

// Chat window state
const activeChatFriend = ref<string | null>(null)
const activeChatFriendAvatar = ref<string | null>(null)
const activeConversationId = ref<string | null>(null)

const unreadCount = computed(() => {
  // 只计算收到的 pending 申请和收到的未过期邀请
  return friendRequests.value.filter(r => r.status === 'pending' && r.direction === 'received').length
    + watchInvites.value.filter(i => !i.expired && i.direction === 'received').length
})


async function loadFriends() {
  try {
    const data = await request.get('/chat/friends')
    friends.value = (data as unknown as Friend[]) || []
  } catch { /* ignore */ }
}

async function loadFriendRequests() {
  try {
    const data = await request.get('/chat/friend-requests')
    friendRequests.value = (data as unknown as FriendReq[]) || []
  } catch { /* ignore */ }
}

async function handleFriendRequest(id: number, accept: boolean) {
  try {
    await request.post(`/chat/friend-requests/${id}/${accept ? 'accept' : 'reject'}`)
    // 更新本地状态而不是移除
    const req = friendRequests.value.find(r => r.id === id)
    if (req) {
      req.status = accept ? 'accepted' : 'rejected'
      req.updated_at = Date.now()
    }
    toast.success(accept ? '已接受好友申请' : '已拒绝好友申请')
    if (accept) loadFriends()
  } catch {
    toast.error('操作失败')
  }
}

async function handleWatchInvite(invite: WatchInvite, accept: boolean) {
  if (accept) {
    // Check if room still exists
    checkingRoom.value = invite.id
    try {
      const data = await request.get(`/watch-together/room/${invite.roomId}`) as any
      if (data && data.id) {
        // Room exists, join it (don't delete invite, keep it as history)
        router.push(`/watch-together/${invite.roomId}?host=${encodeURIComponent(invite.from)}`)
      } else {
        // Room doesn't exist
        toast.error('房间已失效')
        await request.post(`/watch-together/invites/${invite.id}/expire`)
        invite.expired = true
      }
    } catch {
      // Room doesn't exist or error
      toast.error('房间已失效')
      try {
        await request.post(`/watch-together/invites/${invite.id}/expire`)
      } catch { /* ignore */ }
      invite.expired = true
    } finally {
      checkingRoom.value = null
    }
  }
}

// Search users for adding friends
async function searchUsers() {
  if (!searchKeyword.value.trim()) return
  searching.value = true
  hasSearched.value = false
  try {
    const data = await request.get(`/chat/search-users?q=${encodeURIComponent(searchKeyword.value)}`)
    // 后端已经过滤自己并返回好友状态
    searchResults.value = (data as unknown as any[]) || []
    hasSearched.value = true
  } catch {
    searchResults.value = []
    hasSearched.value = true
  } finally {
    searching.value = false
  }
}

// Send friend request
async function sendFriendRequest(userId: number, username: string) {
  sendingRequest.value = userId
  try {
    await request.post('/chat/friend-requests', { to_user: username })
    toast.success('好友申请已发送')
    searchResults.value = searchResults.value.filter(u => u.id !== userId)
  } catch {
    toast.error('发送失败')
  } finally {
    sendingRequest.value = null
  }
}

// Open chat with friend
function openChat(friend: Friend) {
  activeChatFriend.value = friend.username
  activeChatFriendAvatar.value = friend.avatar || null
  closePanel()
}

// Close chat window
function closeChat() {
  activeChatFriend.value = null
  activeChatFriendAvatar.value = null
  activeConversationId.value = null
}

function togglePanel() {
  isOpen.value = !isOpen.value
  if (isOpen.value) {
    loading.value = true
    Promise.all([loadFriends(), loadFriendRequests(), loadWatchInvites()]).finally(() => { loading.value = false })
  }
}

function closePanel() {
  isOpen.value = false
}

let unsubscribers: (() => void)[] = []

onMounted(() => {
  connect()
  // Load invites from backend API
  loadWatchInvites()

  unsubscribers.push(
    subscribe('friend_request', () => {
      loadFriendRequests()
    }),
    subscribe('watch_invite', () => {
      // Reload invites from backend when receiving new invite
      loadWatchInvites()
    }),
  )
  // Initial load
  loadFriendRequests()
})

onUnmounted(() => {
  unsubscribers.forEach(fn => fn())
})
</script>

<template>
  <div class="relative">
    <!-- 气泡按钮 -->
    <button
      @click="togglePanel"
      class="relative w-10 h-10 p-2 rounded-full flex items-center justify-center hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
      title="消息"
    >
      <MessageCircle class="w-5 h-5 text-gray-600 dark:text-gray-300" />
      <!-- 未读红点 -->
      <span
        v-if="unreadCount > 0"
        class="absolute -top-0.5 -right-0.5 min-w-[18px] h-[18px] flex items-center justify-center px-1 text-[10px] font-bold text-white bg-red-500 rounded-full"
      >
        {{ unreadCount > 99 ? '99+' : unreadCount }}
      </span>
    </button>

    <!-- 下拉面板 -->
    <Teleport to="body">
      <div v-if="isOpen" class="fixed inset-0 z-[9998]" @click="closePanel"></div>
      <div
        v-if="isOpen"
        class="fixed right-4 top-14 md:right-16 md:top-12 z-[9999] w-80 max-h-[70vh] bg-white dark:bg-gray-800 rounded-xl shadow-2xl border border-gray-200 dark:border-gray-700 overflow-hidden flex flex-col"
      >
        <!-- Tab 栏 -->
        <div class="flex border-b border-gray-200 dark:border-gray-700 flex-shrink-0">
          <button
            v-for="tab in ([
              { key: 'friends', label: '好友' },
              { key: 'requests', label: '申请' },
              { key: 'invites', label: '邀请' },
            ] as const)"
            :key="tab.key"
            @click="activeTab = tab.key"
            :class="[
              'flex-1 py-2.5 text-sm font-medium transition-colors relative',
              activeTab === tab.key
                ? 'text-blue-600 dark:text-blue-400'
                : 'text-gray-500 hover:text-gray-700 dark:hover:text-gray-300',
            ]"
          >
            {{ tab.label }}
            <span
              v-if="tab.key === 'requests' && friendRequests.filter(r => r.status === 'pending' && r.direction === 'received').length > 0"
              class="ml-1 inline-flex items-center justify-center w-4 h-4 text-[10px] font-bold text-white bg-red-500 rounded-full"
            >{{ friendRequests.filter(r => r.status === 'pending' && r.direction === 'received').length }}</span>
            <span
              v-if="tab.key === 'invites' && watchInvites.filter(i => !i.expired && i.direction === 'received').length > 0"
              class="ml-1 inline-flex items-center justify-center w-4 h-4 text-[10px] font-bold text-white bg-orange-500 rounded-full"
            >{{ watchInvites.filter(i => !i.expired && i.direction === 'received').length }}</span>
            <div
              v-if="activeTab === tab.key"
              class="absolute bottom-0 left-1/4 right-1/4 h-0.5 bg-blue-500 rounded-full"
            ></div>
          </button>
        </div>
        <!-- 内容区 -->
        <div class="flex-1 overflow-y-auto">
          <!-- 加载中 -->
          <div v-if="loading" class="flex items-center justify-center py-8">
            <div class="w-5 h-5 border-2 border-blue-500 border-t-transparent rounded-full animate-spin"></div>
          </div>

          <!-- 好友列表 -->
          <div v-else-if="activeTab === 'friends'">
            <!-- Add Friend Button / Search Panel -->
            <div v-if="showAddFriend" class="p-3 border-b border-gray-200 dark:border-gray-700">
              <div class="flex items-center gap-2 mb-3">
                <button @click="showAddFriend = false; searchKeyword = ''; searchResults = []; hasSearched = false" class="p-1.5 rounded-full hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors">
                  <ArrowLeft class="w-4 h-4 text-gray-500" />
                </button>
                <span class="text-sm font-medium text-gray-700 dark:text-gray-300">添加好友</span>
              </div>
              <div class="flex gap-2">
                <input
                  v-model="searchKeyword"
                  placeholder="输入用户名搜索"
                  class="flex-1 px-3 py-2 text-sm bg-gray-100 dark:bg-gray-700 text-gray-800 dark:text-gray-200 placeholder-gray-400 dark:placeholder-gray-500 rounded-lg border-none focus:ring-2 focus:ring-blue-500 outline-none"
                />
                <button @click="searchUsers" :disabled="searching || !searchKeyword.trim()" class="px-3 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 disabled:opacity-50 transition-colors">
                  <Search v-if="!searching" class="w-4 h-4" />
                  <Loader2 v-else class="w-4 h-4 animate-spin" />
                </button>
              </div>
              <!-- Search Results -->
              <div v-if="searchResults.length > 0" class="mt-3 space-y-2">
                <div v-for="user in searchResults" :key="user.id" class="flex items-center gap-3 p-2 bg-gray-50 dark:bg-gray-700/50 rounded-lg">
                  <img
                    v-if="user.avatar"
                    :src="user.avatar"
                    class="w-8 h-8 rounded-full object-cover flex-shrink-0"
                  />
                  <div v-else class="w-8 h-8 rounded-full flex items-center justify-center text-white text-sm font-semibold flex-shrink-0" style="background-color: #2563EB">
                    {{ user.username.charAt(0).toUpperCase() }}
                  </div>
                  <span class="flex-1 text-sm text-gray-800 dark:text-gray-200 truncate">{{ user.username }}</span>
                  <!-- 已是好友显示标识 -->
                  <span v-if="user.isFriend" class="px-2 py-1 text-xs font-medium text-green-600 dark:text-green-400 bg-green-100 dark:bg-green-900/30 rounded-lg">
                    已是好友
                  </span>
                  <!-- 非好友显示添加按钮 -->
                  <button
                    v-else
                    @click="sendFriendRequest(user.id, user.username)"
                    :disabled="sendingRequest === user.id"
                    class="px-3 py-1 text-xs font-medium bg-blue-500 text-white rounded-lg hover:bg-blue-600 disabled:opacity-50 transition-colors"
                  >
                    <Loader2 v-if="sendingRequest === user.id" class="w-3 h-3 animate-spin" />
                    <span v-else>添加</span>
                  </button>
                </div>
              </div>
              <div v-else-if="hasSearched && !searching" class="mt-3 text-center text-sm text-gray-400">
                未找到用户
              </div>
            </div>
            <!-- Add Friend Button -->
            <div v-if="!showAddFriend" class="p-3 border-b border-gray-200 dark:border-gray-700">
              <button @click="showAddFriend = true" class="w-full flex items-center justify-center gap-2 px-4 py-2 text-sm font-medium text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-900/20 rounded-lg hover:bg-blue-100 dark:hover:bg-blue-900/30 transition-colors">
                <UserPlus class="w-4 h-4" />
                添加好友
              </button>
            </div>
            <div v-if="friends.length === 0 && !showAddFriend" class="py-8 text-center text-sm text-gray-400">
              暂无好友
            </div>
            <div
              v-for="friend in friends"
              :key="friend.username"
              v-show="!showAddFriend"
              @click="openChat(friend)"
              class="flex items-center gap-3 px-4 py-3 hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors cursor-pointer"
            >
              <!-- 头像容器，带在线状态指示器 -->
              <div class="relative flex-shrink-0">
                <img
                  v-if="friend.avatar"
                  :src="friend.avatar"
                  class="w-8 h-8 rounded-full object-cover"
                />
                <div v-else class="w-8 h-8 rounded-full flex items-center justify-center text-white text-sm font-semibold" style="background-color: #2563EB">
                  {{ friend.username.charAt(0).toUpperCase() }}
                </div>
                <!-- 在线状态指示器 -->
                <span
                  v-if="friend.status === 'online'"
                  class="absolute bottom-0 right-0 w-2.5 h-2.5 bg-green-500 border-2 border-white dark:border-gray-800 rounded-full"
                  title="在线"
                ></span>
              </div>
              <span class="flex-1 text-sm text-gray-800 dark:text-gray-200 truncate">{{ friend.username }}</span>
              <!-- 在线文字标识 -->
              <span
                v-if="friend.status === 'online'"
                class="text-xs text-green-500 flex-shrink-0"
              >在线</span>
              <span
                v-else
                class="text-xs text-gray-400 flex-shrink-0"
              >离线</span>
            </div>
          </div>

          <!-- 好友申请 -->
          <div v-else-if="activeTab === 'requests'">
            <div v-if="friendRequests.length === 0" class="py-8 text-center text-sm text-gray-400">
              暂无好友申请
            </div>
            <div v-for="req in friendRequests" :key="req.id" class="px-4 py-3 hover:bg-gray-50 dark:hover:bg-gray-700/50">
              <div class="flex items-center gap-3">
                <!-- 显示对方头像：收到的显示发送者，发出的显示接收者 -->
                <img
                  v-if="req.direction === 'received' ? req.from_user_avatar : req.to_user_avatar"
                  :src="req.direction === 'received' ? req.from_user_avatar : req.to_user_avatar"
                  :class="['w-8 h-8 rounded-full object-cover flex-shrink-0', req.status !== 'pending' ? 'opacity-60' : '']"
                />
                <div v-else :class="[
                  'w-8 h-8 rounded-full flex items-center justify-center text-white text-sm font-semibold flex-shrink-0',
                  req.status === 'pending' ? 'bg-gradient-to-br from-green-400 to-teal-500' : 'bg-gray-400'
                ]">
                  {{ (req.direction === 'received' ? req.from_user : req.to_user).charAt(0).toUpperCase() }}
                </div>
                <div class="flex-1 min-w-0">
                  <div class="flex items-center gap-1.5">
                    <p :class="['text-sm font-medium truncate', req.status !== 'pending' ? 'text-gray-400' : 'text-gray-800 dark:text-gray-200']">
                      {{ req.direction === 'received' ? req.from_user : req.to_user }}
                    </p>
                    <!-- 方向标识 -->
                    <span :class="[
                      'px-1.5 py-0.5 text-[10px] rounded',
                      req.direction === 'sent' ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400' : 'bg-orange-100 dark:bg-orange-900/30 text-orange-600 dark:text-orange-400'
                    ]">
                      {{ req.direction === 'sent' ? '发出' : '收到' }}
                    </span>
                  </div>
                  <p v-if="req.message" class="text-xs text-gray-400 truncate">{{ req.message }}</p>
                  <p class="text-xs text-gray-400 mt-0.5">
                    {{ new Date(req.created_at).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) }}
                  </p>
                </div>
                <!-- 收到的 pending 状态显示操作按钮 -->
                <div v-if="req.status === 'pending' && req.direction === 'received'" class="flex gap-1.5 flex-shrink-0">
                  <button @click="handleFriendRequest(req.id, true)" class="p-1.5 rounded-full bg-green-100 dark:bg-green-900/30 text-green-600 hover:bg-green-200 dark:hover:bg-green-900/50 transition-colors">
                    <Check class="w-3.5 h-3.5" />
                  </button>
                  <button @click="handleFriendRequest(req.id, false)" class="p-1.5 rounded-full bg-red-100 dark:bg-red-900/30 text-red-500 hover:bg-red-200 dark:hover:bg-red-900/50 transition-colors">
                    <X class="w-3.5 h-3.5" />
                  </button>
                </div>
                <!-- 发出的 pending 状态显示等待中 -->
                <span v-else-if="req.status === 'pending' && req.direction === 'sent'" class="text-xs text-blue-500 flex-shrink-0">等待中</span>
                <!-- accepted/rejected 状态显示文字 -->
                <span v-else-if="req.status === 'accepted'" class="text-xs text-green-500 flex-shrink-0">已同意</span>
                <span v-else-if="req.status === 'rejected'" class="text-xs text-gray-400 flex-shrink-0">已拒绝</span>
              </div>
            </div>
          </div>

          <!-- 观影邀请 -->
          <div v-else-if="activeTab === 'invites'">
            <div v-if="watchInvites.length === 0" class="py-8 text-center text-sm text-gray-400">
              暂无观影邀请
            </div>
            <div v-for="invite in watchInvites" :key="invite.id" class="px-4 py-3 hover:bg-gray-50 dark:hover:bg-gray-700/50">
              <div class="flex items-center gap-3">
                <div :class="[
                  'w-8 h-8 rounded-full flex items-center justify-center text-white text-sm font-semibold flex-shrink-0',
                  invite.expired ? 'bg-gray-400' : 'bg-gradient-to-br from-orange-400 to-pink-500'
                ]">
                  <Users class="w-4 h-4" />
                </div>
                <div class="flex-1 min-w-0">
                  <div class="flex items-center gap-1.5">
                    <p :class="['text-sm font-medium truncate', invite.expired ? 'text-gray-400' : 'text-gray-800 dark:text-gray-200']">
                      {{ invite.direction === 'sent' ? `邀请 ${invite.to} 一起看` : `${invite.from} 邀请你一起看` }}
                    </p>
                    <!-- 方向标识 -->
                    <span :class="[
                      'px-1.5 py-0.5 text-[10px] rounded flex-shrink-0',
                      invite.direction === 'sent' ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400' : 'bg-orange-100 dark:bg-orange-900/30 text-orange-600 dark:text-orange-400'
                    ]">
                      {{ invite.direction === 'sent' ? '发出' : '收到' }}
                    </span>
                  </div>
                  <p class="text-xs text-gray-400 truncate">{{ invite.videoTitle }}</p>
                  <p class="text-xs text-gray-400 mt-0.5">
                    {{ new Date(invite.timestamp).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) }}
                  </p>
                </div>
                <span v-if="invite.expired" class="text-xs text-gray-400 flex-shrink-0">已失效</span>
              </div>
              <!-- 只有收到的未失效邀请才能加入 -->
              <div v-if="!invite.expired && invite.direction === 'received'" class="flex gap-2 mt-2 ml-11">
                <button
                  @click="handleWatchInvite(invite, true)"
                  :disabled="checkingRoom === invite.id"
                  class="flex-1 px-3 py-1.5 text-xs font-medium bg-blue-500 text-white rounded-lg hover:bg-blue-600 disabled:opacity-50 transition-colors flex items-center justify-center gap-1"
                >
                  <Loader2 v-if="checkingRoom === invite.id" class="w-3 h-3 animate-spin" />
                  <span>{{ checkingRoom === invite.id ? '检查中' : '加入' }}</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Chat Window -->
    <Teleport to="body">
      <ChatWindow
        v-if="activeChatFriend"
        :friend-username="activeChatFriend"
        :friend-avatar="activeChatFriendAvatar || undefined"
        :conversation-id="activeConversationId || undefined"
        @close="closeChat"
      />
    </Teleport>
  </div>
</template>

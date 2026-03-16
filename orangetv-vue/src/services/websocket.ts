import { Client, type IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { ref } from 'vue'
import { useAuthStore } from '@/stores/auth'

export type ConnectionStatus = 'disconnected' | 'connecting' | 'connected'

export interface WebSocketMessage {
  type: string
  data?: unknown
  timestamp: number
}

const client = ref<Client | null>(null)
const isConnected = ref(false)
const connectionStatus = ref<ConnectionStatus>('disconnected')
const messageHandlers = new Map<string, Set<(message: WebSocketMessage) => void>>()
const onConnectCallbacks: (() => void)[] = []
const stompSubscriptions: { unsubscribe: () => void }[] = []

function getWebSocketUrl(): string {
  const protocol = window.location.protocol === 'https:' ? 'https:' : 'http:'
  const host = window.location.host
  return `${protocol}//${host}/ws`
}

export function connect(): void {
  if (client.value?.connected || connectionStatus.value === 'connecting') {
    return
  }

  connectionStatus.value = 'connecting'

  const authStore = useAuthStore()
  const wsUrl = getWebSocketUrl()

  client.value = new Client({
    webSocketFactory: () => new SockJS(wsUrl),
    connectHeaders: authStore.token ? { Authorization: `Bearer ${authStore.token}` } : {},
    debug: (str) => {
      if (import.meta.env.DEV) {
        console.log('[STOMP]', str)
      }
    },
    reconnectDelay: 5000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,
    onConnect: () => {
      console.log('[WebSocket] Connected')
      isConnected.value = true
      connectionStatus.value = 'connected'

      // 清理旧的 STOMP 订阅（防止重连时重复订阅）
      stompSubscriptions.forEach((s) => { try { s.unsubscribe() } catch { /* ignore */ } })
      stompSubscriptions.length = 0

      // 订阅用户私有队列（Spring STOMP user destination 不需要带用户名）
      if (authStore.user?.username) {
        stompSubscriptions.push(client.value!.subscribe(`/user/queue/messages`, handleMessage))
        stompSubscriptions.push(client.value!.subscribe(`/user/queue/watch-together`, handleMessage))
        stompSubscriptions.push(client.value!.subscribe(`/user/queue/chat`, handleMessage))
      }

      // 订阅公共主题
      stompSubscriptions.push(client.value!.subscribe('/topic/broadcast', handleMessage))

      // 触发连接就绪回调
      onConnectCallbacks.forEach((cb) => cb())
      onConnectCallbacks.length = 0
    },
    onDisconnect: () => {
      console.log('[WebSocket] Disconnected')
      isConnected.value = false
      connectionStatus.value = 'disconnected'
    },
    onStompError: (frame) => {
      console.error('[WebSocket] STOMP error:', frame.headers['message'])
      connectionStatus.value = 'disconnected'
    },
  })

  client.value.activate()
}

export function disconnect(): void {
  if (client.value) {
    client.value.deactivate()
    client.value = null
  }
  isConnected.value = false
  connectionStatus.value = 'disconnected'
}

function handleMessage(message: IMessage): void {
  try {
    const raw = JSON.parse(message.body)
    // 后端 WatchTogetherMessageDto 直接有 type 字段，将其余字段包装到 data 中
    const parsed: WebSocketMessage = raw.data !== undefined
      ? raw
      : { type: raw.type, data: raw, timestamp: raw.timestamp || Date.now() }
    const handlers = messageHandlers.get(parsed.type)
    if (handlers) {
      handlers.forEach((handler) => handler(parsed))
    }
    // 也通知通用处理器
    const allHandlers = messageHandlers.get('*')
    if (allHandlers) {
      allHandlers.forEach((handler) => handler(parsed))
    }
  } catch (e) {
    console.error('[WebSocket] Failed to parse message:', e)
  }
}

export function sendMessage(destination: string, body: unknown): boolean {
  if (!client.value?.connected) {
    console.warn('[WebSocket] Not connected, cannot send message')
    return false
  }

  client.value.publish({
    destination: `/app${destination}`,
    body: JSON.stringify(body),
  })
  return true
}

export function subscribe(type: string, handler: (message: WebSocketMessage) => void): () => void {
  if (!messageHandlers.has(type)) {
    messageHandlers.set(type, new Set())
  }
  messageHandlers.get(type)!.add(handler)

  return () => {
    messageHandlers.get(type)?.delete(handler)
  }
}

export function useWebSocket() {
  return {
    isConnected,
    connectionStatus,
    connect,
    disconnect,
    sendMessage,
    subscribe,
    onReady,
  }
}

/**
 * 在 WebSocket 连接就绪后执行回调，如果已连接则立即执行
 */
export function onReady(callback: () => void): void {
  if (client.value?.connected) {
    callback()
  } else {
    onConnectCallbacks.push(callback)
  }
}

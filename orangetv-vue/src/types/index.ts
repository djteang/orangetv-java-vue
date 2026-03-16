// 播放记录数据结构
export interface PlayRecord {
  title: string
  source_name: string
  cover: string
  year: string
  index: number
  total_episodes: number
  play_time: number
  total_time: number
  save_time: number
  search_title: string
}

// 收藏数据结构
export interface Favorite {
  source_name: string
  total_episodes: number
  title: string
  year: string
  cover: string
  save_time: number
  search_title: string
  origin?: 'vod' | 'live'
}

// 搜索结果数据结构
export interface SearchResult {
  id: string
  title: string
  poster: string
  episodes: string[]
  episodes_titles: string[]
  source: string
  source_name: string
  class?: string
  tag?: string
  year: string
  desc?: string
  type_name?: string
  douban_id?: number
}

// 豆瓣数据结构
export interface DoubanItem {
  id: string
  title: string
  poster: string
  rate: string
  year: string
}

export interface DoubanResult {
  code: number
  message: string
  list: DoubanItem[]
}

// 跳过片头片尾配置数据结构
export interface SkipConfig {
  enable: boolean
  intro_time: number
  outro_time: number
}

// 聊天消息数据结构
export interface ChatMessage {
  id: string
  conversation_id: string
  sender_id: string
  sender_name: string
  content: string
  message_type: 'text' | 'image' | 'file'
  timestamp: number
  is_read: boolean
}

// 对话数据结构
export interface Conversation {
  id: string
  name: string
  participants: string[]
  type: 'private' | 'group'
  created_at: number
  updated_at: number
  last_message?: ChatMessage
  is_group?: boolean
}

// 好友数据结构
export interface Friend {
  id: string
  username: string
  nickname?: string
  status: 'online' | 'offline'
  added_at: number
}

// 好友申请数据结构
export interface FriendRequest {
  id: string
  from_user: string
  to_user: string
  message?: string
  status: 'pending' | 'accepted' | 'rejected'
  created_at: number
  updated_at: number
}

// WebSocket 消息类型
export type WebSocketMessageType =
  | 'message'
  | 'friend_request'
  | 'friend_accepted'
  | 'user_status'
  | 'online_users'
  | 'connection_confirmed'
  | 'user_connect'
  | 'ping'
  | 'pong'
  | 'watch_invite'
  | 'watch_invite_accept'
  | 'watch_invite_reject'
  | 'watch_sync'
  | 'watch_effect'
  | 'watch_leave'
  | 'watch_chat'

export interface WebSocketMessage {
  type: WebSocketMessageType
  data?: unknown
  timestamp: number
}

// 共同观影房间
export interface WatchRoom {
  id: string
  host_id: string
  guest_id: string
  video_info: {
    title: string
    source: string
    id: string
    episode_index: number
    video_url: string
    cover?: string
  }
  status: 'waiting' | 'active' | 'ended'
  created_at: number
}

// 播放同步状态
export interface WatchSyncState {
  is_playing: boolean
  current_time: number
  volume: number
  updated_by: string
  timestamp: number
}

// 互动特效类型
export type WatchEffectType = 'heart' | 'like' | 'clap' | 'fire' | 'laugh' | 'wow'

// 互动特效
export interface WatchEffect {
  id: string
  type: WatchEffectType
  sender: string
  timestamp: number
}

// 共同观影聊天消息
export interface WatchChatMessage {
  id: string
  sender: string
  content: string
  timestamp: number
}

// 用户信息
export interface User {
  id: number
  username: string
  role: 'owner' | 'admin' | 'user'
  enabled: boolean
  banned: boolean
  groups: string[]
  avatar?: string
  createdAt?: string
  lastLoginAt?: string
}

// 登录响应
export interface LoginResponse {
  token: string
  user: User
}

// API 响应
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

// 自定义分类
export interface CustomCategory {
  name?: string
  type: 'movie' | 'tv'
  query: string
  disabled?: boolean
  from: 'config' | 'custom'
}

// 配置订阅
export interface ConfigSubscription {
  URL: string
  AutoUpdate: boolean
  LastCheck: string
}

// 站点配置（后端返回的 Config 对象，key 为 snake_case）
export interface SiteConfig {
  site_name?: string
  announcement?: string
  require_device_code?: boolean
  disable_yellow_filter?: boolean
  fluid_search?: boolean
  search_downstream_max_page?: number
  site_interface_cache_time?: number
  ConfigFile?: string
  ConfigSubscribtion?: ConfigSubscription
  CustomCategories?: CustomCategory[]
}

// 管理员配置（/api/admin/config 返回）
export interface AdminConfig {
  Role: 'owner' | 'admin' | 'user' | 'guest'
  Config: SiteConfig
  ConfigFile?: string
  ConfigSubscribtion?: ConfigSubscription
  CustomCategories?: CustomCategory[]
}

// 直播源
export interface LiveSource {
  id: number
  key: string
  name: string
  url: string
  epg?: string
  ua?: string
  channelCount?: number
  enabled: boolean
  disabled: boolean
  from?: string
}

// 视频源
export interface VideoSource {
  id: number
  key: string
  name: string
  api: string
  type?: string
  detail?: string
  enabled: boolean
  disabled: boolean
  from?: string
}

// 番剧日历数据
export interface BangumiCalendarItem {
  id: number
  name: string
  name_cn?: string
  air_date?: string
  images?: {
    large?: string
    common?: string
    medium?: string
    small?: string
    grid?: string
  }
  rating?: {
    score?: number
  }
}

export interface BangumiCalendarData {
  weekday: {
    en: string
    cn: string
    ja: string
    id: number
  }
  items: BangumiCalendarItem[]
}

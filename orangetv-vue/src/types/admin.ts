export interface AdminTrendPoint {
  date: string
  count: number
}

export interface ConfigFilePreview {
  content: string
  format: 'json' | 'm3u' | 'txt'
  channelCount: number
  unsupportedChannelCount: number
  videoSources: number
  liveSources: number
  warnings: string[]
}

export interface ConfigSyncResult {
  videoSources: number
  liveSources: number
  warnings: string[]
}

export interface AdminStats {
  totalUsers: number
  todayNewUsers: number
  totalPlayRecords: number
  todayPlayRecords: number
  totalSearches: number
  todaySearches: number
  totalVideoSources: number
  totalLiveSources: number
  activeUsers: number
  userTrend: AdminTrendPoint[]
  searchTrend: AdminTrendPoint[]
}

export interface AdminHistoryPage<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface AdminSearchHistoryEntry {
  id: number
  username?: string
  keyword: string
  searchCount: number | null
  createdAt: string | null
  updatedAt: string | null
}

export interface AdminPlayHistoryEntry {
  id: number
  username?: string
  title: string | null
  cover: string | null
  sourceName: string | null
  year: string | null
  episodeIndex: number | null
  episodeName: string | null
  totalEpisodes: number | null
  progress: number | null
  duration: number | null
  updatedAt: string | null
}

export interface AdminTrendPoint {
  date: string
  count: number
}

export interface AdminStats {
  totalUsers: number
  todayNewUsers: number
  totalSearches: number
  todaySearches: number
  totalVideoSources: number
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
  keyword: string
  searchCount: number | null
  createdAt: string | null
  updatedAt: string | null
}

export interface AdminPlayHistoryEntry {
  id: number
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

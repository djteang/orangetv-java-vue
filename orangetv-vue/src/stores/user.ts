import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { PlayRecord, Favorite, SkipConfig } from '@/types'
import * as userApi from '@/api/user'

export const useUserStore = defineStore('user', () => {
  const playRecords = ref<Record<string, PlayRecord>>({})
  const favorites = ref<Record<string, Favorite>>({})
  const searchHistory = ref<string[]>([])
  const skipConfigs = ref<Record<string, SkipConfig>>({})
  const loading = ref(false)

  // 播放记录
  async function fetchPlayRecords() {
    loading.value = true
    try {
      const data = await userApi.getPlayRecords() as unknown as Record<string, PlayRecord>
      if (data) {
        playRecords.value = data
      }
    } finally {
      loading.value = false
    }
  }

  async function savePlayRecord(key: string, record: PlayRecord) {
    await userApi.savePlayRecord(key, record)
    playRecords.value[key] = record
  }

  async function deletePlayRecord(key: string) {
    await userApi.deletePlayRecord(key)
    delete playRecords.value[key]
  }

  // 收藏
  async function fetchFavorites() {
    loading.value = true
    try {
      const data = await userApi.getFavorites() as unknown as Record<string, Favorite>
      if (data) {
        favorites.value = data
      }
    } finally {
      loading.value = false
    }
  }

  async function saveFavorite(key: string, favorite: Favorite) {
    await userApi.saveFavorite(key, favorite)
    favorites.value[key] = favorite
  }

  async function deleteFavorite(key: string) {
    await userApi.deleteFavorite(key)
    delete favorites.value[key]
  }

  function isFavorite(key: string): boolean {
    return key in favorites.value
  }

  // 搜索历史
  async function fetchSearchHistory() {
    const data = await userApi.getSearchHistory() as unknown as string[]
    if (data) {
      searchHistory.value = data
    }
  }

  async function addSearchHistory(keyword: string) {
    await userApi.addSearchHistory(keyword)
    if (!searchHistory.value.includes(keyword)) {
      searchHistory.value.unshift(keyword)
      if (searchHistory.value.length > 20) {
        searchHistory.value.pop()
      }
    }
  }

  async function clearSearchHistory() {
    await userApi.deleteSearchHistory()
    searchHistory.value = []
  }

  async function deleteSearchHistoryItem(keyword: string) {
    await userApi.deleteSearchHistory(keyword)
    searchHistory.value = searchHistory.value.filter((item) => item !== keyword)
  }

  // 跳过配置
  async function fetchSkipConfigs() {
    const data = await userApi.getSkipConfigs() as unknown as Record<string, SkipConfig>
    if (data) {
      skipConfigs.value = data
    }
  }

  async function saveSkipConfig(source: string, id: string, config: SkipConfig) {
    await userApi.saveSkipConfig(source, id, config)
    skipConfigs.value[`${source}+${id}`] = config
  }

  function getSkipConfig(source: string, id: string): SkipConfig | null {
    return skipConfigs.value[`${source}+${id}`] || null
  }

  return {
    playRecords,
    favorites,
    searchHistory,
    skipConfigs,
    loading,
    fetchPlayRecords,
    savePlayRecord,
    deletePlayRecord,
    fetchFavorites,
    saveFavorite,
    deleteFavorite,
    isFavorite,
    fetchSearchHistory,
    addSearchHistory,
    clearSearchHistory,
    deleteSearchHistoryItem,
    fetchSkipConfigs,
    saveSkipConfig,
    getSkipConfig,
  }
})

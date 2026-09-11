import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import request from '@/api/index'
import type { SiteAnnouncement } from '@/types'
import { normalizeAnnouncements } from '@/utils/announcements'

interface ServerConfig {
  siteName: string
  requireDeviceCode: boolean
  allowRegistration: boolean
  storageType: string
  announcement?: string
  announcements?: SiteAnnouncement[]
  enableLinuxDoLogin?: boolean
  enableDanmu?: boolean
  danmuApiUrl?: string
}

export const useSiteStore = defineStore('site', () => {
  const siteName = ref('OrangeTV')
  const announcements = ref<SiteAnnouncement[]>([])
  const announcement = computed(() => announcements.value[0]?.content || null)
  const logo = ref<string | null>(null)
  const requireDeviceCode = ref(false)
  const allowRegistration = ref(true)
  const enableLinuxDoLogin = ref(false)
  const enableDanmu = ref(false)
  const danmuApiUrl = ref('')
  const loading = ref(false)
  const loaded = ref(false)
  let configRequest: Promise<void> | null = null

  function fetchConfig(): Promise<void> {
    if (configRequest) return configRequest
    loading.value = true
    configRequest = request.get<ServerConfig, ServerConfig>('/server-config')
      .then((config) => {
        if (!config) return
        siteName.value = config.siteName || 'OrangeTV'
        announcements.value = normalizeAnnouncements(config.announcements, config.announcement)
        requireDeviceCode.value = config.requireDeviceCode ?? false
        allowRegistration.value = config.allowRegistration ?? true
        enableLinuxDoLogin.value = config.enableLinuxDoLogin ?? false
        enableDanmu.value = config.enableDanmu ?? false
        danmuApiUrl.value = config.danmuApiUrl ?? ''
        loaded.value = true
      })
      .catch(() => {
        // 暂时加载失败时保留已有配置。
      })
      .finally(() => {
        loading.value = false
        configRequest = null
      })
    return configRequest
  }

  return {
    siteName,
    announcement,
    announcements,
    logo,
    requireDeviceCode,
    allowRegistration,
    enableLinuxDoLogin,
    enableDanmu,
    danmuApiUrl,
    loading,
    loaded,
    fetchConfig,
  }
})

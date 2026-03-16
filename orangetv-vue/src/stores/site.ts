import { defineStore } from 'pinia'
import { ref } from 'vue'
import request from '@/api/index'

interface ServerConfig {
  siteName: string
  requireDeviceCode: boolean
  allowRegistration: boolean
  storageType: string
  announcement?: string
  enableLinuxDoLogin?: boolean
}

export const useSiteStore = defineStore('site', () => {
  const siteName = ref('OrangeTV')
  const announcement = ref<string | null>(null)
  const logo = ref<string | null>(null)
  const requireDeviceCode = ref(false)
  const allowRegistration = ref(true)
  const enableLinuxDoLogin = ref(false)
  const loading = ref(false)

  async function fetchConfig() {
    loading.value = true
    try {
      const config = await request.get('/server-config') as ServerConfig
      if (config) {
        siteName.value = config.siteName || 'OrangeTV'
        announcement.value = config.announcement || null
        requireDeviceCode.value = config.requireDeviceCode ?? false
        allowRegistration.value = config.allowRegistration ?? true
        enableLinuxDoLogin.value = config.enableLinuxDoLogin ?? false
      }
    } catch {
      // use defaults
    } finally {
      loading.value = false
    }
  }

  return {
    siteName,
    announcement,
    logo,
    requireDeviceCode,
    allowRegistration,
    enableLinuxDoLogin,
    loading,
    fetchConfig,
  }
})

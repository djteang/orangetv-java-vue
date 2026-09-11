<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { RouterView } from 'vue-router'
import Toast from '@/components/Toast.vue'
import AnnouncementDialog from '@/components/AnnouncementDialog.vue'
import RedLanternEasterEgg from '@/components/RedLanternEasterEgg.vue'
import { useLanternStore } from '@/stores/lantern'
import { useAuthStore } from '@/stores/auth'
import { useSiteStore } from '@/stores/site'
import type { SiteAnnouncement } from '@/types'

const authStore = useAuthStore()
const siteStore = useSiteStore()
const lanternStore = useLanternStore()
const announcementQueue = ref<SiteAnnouncement[]>([])

watch(
  [() => authStore.justLoggedIn, () => siteStore.loaded, () => siteStore.loading, () => siteStore.announcements],
  () => {
    if (!authStore.justLoggedIn) {
      announcementQueue.value = []
      return
    }
    if (!siteStore.loaded || siteStore.loading || announcementQueue.value.length) return

    // 本次登录使用独立快照，避免刷新配置时跳回第一条或改变阅读顺序。
    announcementQueue.value = siteStore.announcements.map(item => ({ ...item }))
    if (!announcementQueue.value.length) authStore.clearJustLoggedIn()
  },
  { immediate: true },
)

onMounted(() => { void siteStore.fetchConfig() })

function closeAnnouncement() {
  announcementQueue.value = []
  authStore.clearJustLoggedIn()
}
</script>

<template>
  <div class="app-shell min-h-screen bg-theme-bg text-theme-text" :data-chinese-scene="lanternStore.activeMoment?.id">
    <RedLanternEasterEgg />
    <div class="app-content">
      <RouterView />
    </div>
    <Toast />

    <AnnouncementDialog v-if="announcementQueue.length" :announcements="announcementQueue" @close="closeAnnouncement" />
  </div>
</template>

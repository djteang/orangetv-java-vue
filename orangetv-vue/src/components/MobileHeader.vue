<script setup lang="ts">
import { useSiteStore } from '@/stores/site'
import { useAuthStore } from '@/stores/auth'
import BackButton from './BackButton.vue'
import ThemeToggle from './ThemeToggle.vue'
import ChineseRedControls from './ChineseRedControls.vue'
import { useThemeStore } from '@/stores/theme'
import ChatBubble from './ChatBubble.vue'
import UserMenu from './UserMenu.vue'

interface Props {
  showBackButton?: boolean
}

withDefaults(defineProps<Props>(), {
  showBackButton: false,
})

const siteStore = useSiteStore()
const authStore = useAuthStore()
const themeStore = useThemeStore()
</script>

<template>
  <header
    class="fixed top-0 left-0 right-0 z-30 md:hidden bg-white/80 dark:bg-gray-900/80 backdrop-blur-xl border-b border-gray-200/50 dark:border-gray-700/50"
    style="backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px)"
  >
    <div class="flex items-center justify-between h-12 px-4">
      <!-- 左侧：返回按钮或 Logo -->
      <div class="flex items-center gap-2">
        <BackButton v-if="showBackButton" />
        <template v-else>
          <router-link to="/" class="flex items-center gap-2">
            <img src="/logo.png" :alt="siteStore.siteName" class="w-7 h-7 rounded-lg" />
            <span class="text-lg font-bold text-blue-600">{{ siteStore.siteName }}</span>
          </router-link>
        </template>
      </div>

      <!-- 右侧：主题切换和用户菜单 -->
      <div class="flex items-center gap-2">
        <ThemeToggle />
        <ChatBubble v-if="authStore.isLoggedIn" />
        <UserMenu />
      </div>
    </div>
    <div v-if="themeStore.mode === 'chinese-red'" class="mobile-scene-controls">
      <ChineseRedControls />
    </div>
  </header>
</template>

<style scoped>
.mobile-scene-controls { display: flex; justify-content: flex-end; height: var(--china-mobile-controls-row); padding: 4px 12px 8px; }
</style>

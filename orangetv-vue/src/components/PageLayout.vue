<script setup lang="ts">
import Sidebar from './Sidebar.vue'
import MobileHeader from './MobileHeader.vue'
import MobileBottomNav from './MobileBottomNav.vue'
import ThemeToggle from './ThemeToggle.vue'
import ChatBubble from './ChatBubble.vue'
import UserMenu from './UserMenu.vue'
import BackButton from './BackButton.vue'
import { useRoute } from 'vue-router'
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const authStore = useAuthStore()

const activePath = computed(() => {
  const queryString = new URLSearchParams(route.query as Record<string, string>).toString()
  return queryString ? `${route.path}?${queryString}` : route.path
})
const showBackButton = computed(() => ['/play', '/live'].includes(route.path))
</script>

<template>
  <div class="w-full min-h-screen">
    <!-- 移动端头部 -->
    <MobileHeader :show-back-button="showBackButton" />

    <!-- 主要布局容器 -->
    <div class="flex md:grid md:grid-cols-[auto_1fr] w-full min-h-screen md:min-h-auto">
      <!-- 侧边栏 - 桌面端显示，移动端隐藏 -->
      <div class="hidden md:block">
        <Sidebar :active-path="activePath" />
      </div>

      <!-- 主内容区域 -->
      <div class="relative min-w-0 flex-1 transition-all duration-300">
        <!-- 桌面端左上角返回按钮 -->
        <div v-if="showBackButton" class="absolute top-[1.35rem] left-1 lg:left-6 2xl:left-16 z-20 hidden md:flex">
          <BackButton />
        </div>

        <!-- 桌面端顶部按钮 -->
        <div class="absolute top-2 right-4 z-20 hidden md:flex items-center gap-2">
          <ThemeToggle />
          <ChatBubble v-if="authStore.isLoggedIn" />
          <UserMenu />
        </div>

        <!-- 主内容 -->
        <main
          class="flex-1 md:min-h-0 mb-14 md:mb-0 md:mt-0 mt-12"
          :style="{ paddingBottom: 'calc(3.5rem + env(safe-area-inset-bottom))' }"
        >
          <slot />
        </main>
      </div>
    </div>

    <!-- 移动端底部导航 -->
    <div class="md:hidden">
      <MobileBottomNav :active-path="activePath" />
    </div>
  </div>
</template>

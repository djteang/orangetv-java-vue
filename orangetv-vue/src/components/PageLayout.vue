<script setup lang="ts">
import Sidebar from './Sidebar.vue'
import MobileHeader from './MobileHeader.vue'
import MobileBottomNav from './MobileBottomNav.vue'
import ThemeToggle from './ThemeToggle.vue'
import ChineseRedControls from './ChineseRedControls.vue'
import ChatBubble from './ChatBubble.vue'
import UserMenu from './UserMenu.vue'
import BackButton from './BackButton.vue'
import { useRoute } from 'vue-router'
import { computed, ref, watchEffect, type CSSProperties } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'

const route = useRoute()
const authStore = useAuthStore()
const themeStore = useThemeStore()
const isChineseRed = computed(() => themeStore.mode === 'chinese-red')
const desktopToolbarAnchor = ref<HTMLElement | null>(null)
const toolbarBounds = ref({ left: 0, width: 0 })
const toolbarStyle = computed<CSSProperties | undefined>(() => isChineseRed.value ? {
  left: `${toolbarBounds.value.left}px`,
  width: `${toolbarBounds.value.width}px`,
  visibility: toolbarBounds.value.width > 0 ? 'visible' : 'hidden',
} : undefined)

// 控制栏移到 body 顶层后，仍跟随内容列对齐，包括侧边栏收放和窗口缩放。
watchEffect((onCleanup) => {
  const anchor = desktopToolbarAnchor.value
  if (!isChineseRed.value || !anchor) return

  const updateBounds = () => {
    const { left, width } = anchor.getBoundingClientRect()
    toolbarBounds.value = { left, width }
  }
  updateBounds()
  const observer = new ResizeObserver(updateBounds)
  observer.observe(anchor)
  window.addEventListener('resize', updateBounds)
  onCleanup(() => {
    observer.disconnect()
    window.removeEventListener('resize', updateBounds)
  })
}, { flush: 'post' })

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
        <div
          ref="desktopToolbarAnchor"
          class="desktop-toolbar-anchor hidden md:block"
          :class="{ 'has-scene-controls': isChineseRed }"
        >
          <!-- 脱离页面的堆叠上下文，保持在页面内容之上、弹窗及其遮罩之下。 -->
          <Teleport to="body" :disabled="!isChineseRed">
            <div
              class="desktop-page-toolbar hidden md:block"
              :class="{ 'desktop-scene-controls': isChineseRed, 'has-back-button': showBackButton }"
              :style="toolbarStyle"
            >
              <div v-if="showBackButton" class="absolute top-[1.35rem] left-1 lg:left-6 2xl:left-16 z-20 hidden md:flex">
                <BackButton />
              </div>

              <div class="absolute top-2 right-4 z-20 hidden md:flex items-center gap-2">
                <ThemeToggle />
                <ChatBubble v-if="authStore.isLoggedIn" />
                <UserMenu />
              </div>

              <ChineseRedControls v-if="isChineseRed" />
            </div>
          </Teleport>
        </div>

        <!-- 主内容 -->
        <main
          class="flex-1 md:min-h-0 mb-14 md:mb-0 md:mt-0 mt-12"
          :class="{ 'scene-controls-visible': isChineseRed }"
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

<style scoped>
.desktop-toolbar-anchor.has-scene-controls {
  height: calc(var(--china-controls-height, 54px) + 16px);
}
.desktop-scene-controls {
  position: fixed;
  top: 0;
  /* 页面内容在下，弹窗及其遮罩（最低 z-50）在上。 */
  z-index: 40;
  padding: 8px 10rem 8px 16px;
  /* 与侧边栏使用相同的半透明白底和模糊效果，让彩蛋背景自然透出。 */
  background: rgb(255 255 255 / .4);
  -webkit-backdrop-filter: blur(20px);
  backdrop-filter: blur(20px);
}
.desktop-scene-controls.has-back-button { padding-left: 56px; }
@media (min-width: 1024px) { .desktop-scene-controls.has-back-button { padding-left: 72px; } }
@media (min-width: 1536px) { .desktop-scene-controls.has-back-button { padding-left: 112px; } }
@media (max-width: 767px) {
  .scene-controls-visible { margin-top: calc(3rem + var(--china-mobile-controls-row)); }
}
</style>

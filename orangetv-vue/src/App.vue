<script setup lang="ts">
import { ref, watch, onMounted, nextTick } from 'vue'
import { RouterView, useRouter } from 'vue-router'
import Toast from '@/components/Toast.vue'
import { useAuthStore } from '@/stores/auth'
import { useSiteStore } from '@/stores/site'

const authStore = useAuthStore()
const siteStore = useSiteStore()
const router = useRouter()
const showAnnouncement = ref(false)

// 检查并显示公告
async function checkAndShowAnnouncement() {
  // 确保配置已加载
  if (!siteStore.announcement && !siteStore.loading) {
    await siteStore.fetchConfig()
  }

  await nextTick()

  if (authStore.justLoggedIn && siteStore.announcement) {
    showAnnouncement.value = true
  }
}

// 监听登录状态变化
watch(() => authStore.justLoggedIn, (justLoggedIn) => {
  if (justLoggedIn) {
    checkAndShowAnnouncement()
  }
}, { immediate: true })

// 监听公告加载完成
watch(() => siteStore.announcement, (announcement) => {
  if (announcement) {
    checkAndShowAnnouncement()
  }
}, { immediate: true })

// 监听路由变化（处理登录后跳转的情况）
router.afterEach(() => {
  if (authStore.justLoggedIn) {
    checkAndShowAnnouncement()
  }
})

// 页面加载时获取站点配置
onMounted(async () => {
  await siteStore.fetchConfig()
  // 配置加载完成后检查是否需要显示公告
  checkAndShowAnnouncement()
})

function closeAnnouncement() {
  showAnnouncement.value = false
  authStore.clearJustLoggedIn()
}
</script>

<template>
  <div class="min-h-screen bg-theme-bg text-theme-text">
    <RouterView />
    <Toast />

    <!-- 登录公告弹窗 -->
    <Teleport to="body">
      <div
        v-if="showAnnouncement"
        class="fixed inset-0 z-[10000] flex items-center justify-center bg-black/50 backdrop-blur-sm"
      >
        <div class="bg-white dark:bg-gray-800 rounded-2xl shadow-2xl mx-4 w-full max-w-md overflow-hidden">
          <!-- 头部 -->
          <div class="flex items-center px-5 py-4 border-b border-gray-200 dark:border-gray-700 bg-gradient-to-r from-blue-500 to-blue-600">
            <h3 class="text-lg font-semibold text-white flex items-center gap-2">
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5.882V19.24a1.76 1.76 0 01-3.417.592l-2.147-6.15M18 13a3 3 0 100-6M5.436 13.683A4.001 4.001 0 017 6h1.832c4.1 0 7.625-1.234 9.168-3v14c-1.543-1.766-5.067-3-9.168-3H7a3.988 3.988 0 01-1.564-.317z" />
              </svg>
              站点公告
            </h3>
          </div>
          <!-- 内容 -->
          <div class="px-5 py-6 max-h-[60vh] overflow-y-auto">
            <p class="text-gray-700 dark:text-gray-300 text-sm leading-relaxed whitespace-pre-wrap">
              {{ siteStore.announcement }}
            </p>
          </div>
          <!-- 底部 -->
          <div class="px-5 py-4 border-t border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900">
            <button
              @click="closeAnnouncement"
              class="w-full px-4 py-2.5 bg-blue-500 text-white rounded-lg text-sm font-medium hover:bg-blue-600 transition-colors"
            >
              我知道了
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useSiteStore } from '@/stores/site'
import {
  Home,
  Search,
  Film,
  Tv,
  Cat,
  Clover,
  Menu,
  ExternalLink,
} from 'lucide-vue-next'

function openExternal(url: string) {
  window.open(url, '_blank')
}

interface Props {
  activePath?: string
}

const props = withDefaults(defineProps<Props>(), {
  activePath: '/',
})

const router = useRouter()
const siteStore = useSiteStore()

const isCollapsed = ref(false)

onMounted(() => {
  const saved = localStorage.getItem('sidebarCollapsed')
  if (saved !== null) {
    isCollapsed.value = JSON.parse(saved)
  }
})

function handleToggle() {
  isCollapsed.value = !isCollapsed.value
  localStorage.setItem('sidebarCollapsed', JSON.stringify(isCollapsed.value))
}

function handleSearchClick() {
  router.push('/search')
}

const menuItems = [
  { icon: Film, label: '电影', href: '/douban?type=movie' },
  // { icon: PlayCircle, label: '短剧', href: '/shortdrama' },
  { icon: Tv, label: '剧集', href: '/douban?type=tv' },
  { icon: Cat, label: '动漫', href: '/douban?type=anime' },
  { icon: Clover, label: '综艺', href: '/douban?type=show' },
  // { icon: Radio, label: '直播', href: '/live' },
]

function isActive(href: string): boolean {
  const decodedActive = decodeURIComponent(props.activePath)
  const decodedHref = decodeURIComponent(href)

  if (decodedActive === decodedHref) return true

  const typeMatch = href.match(/type=([^&]+)/)?.[1]
  if (typeMatch && decodedActive.startsWith('/douban') && decodedActive.includes(`type=${typeMatch}`)) {
    return true
  }

  if (href === '/shortdrama' && decodedActive.startsWith('/shortdrama')) {
    return true
  }

  return false
}
</script>

<template>
  <div class="hidden md:flex">
    <aside
      :class="[
        'fixed top-0 left-0 h-screen bg-white/40 backdrop-blur-xl transition-all duration-300 border-r border-gray-200/50 z-10 shadow-lg dark:bg-gray-900/70 dark:border-gray-700/50',
        isCollapsed ? 'w-16' : 'w-64',
      ]"
      style="backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px)"
    >
      <div class="flex h-full flex-col">
        <!-- 顶部 Logo 区域 -->
        <div class="relative h-16">
          <div class="absolute inset-0 flex items-center justify-center transition-all duration-200">
            <template v-if="isCollapsed">
              <button
                @click="handleToggle"
                class="flex items-center justify-center w-12 h-12 hover:opacity-80 transition-opacity duration-200 cursor-pointer"
                title="点击展开侧边栏"
              >
                <img src="/logo.png" :alt="siteStore.siteName" class="w-8 h-8 rounded-lg" />
              </button>
            </template>
            <template v-else>
              <div class="w-[calc(100%-4rem)] flex justify-center">
                <router-link
                  to="/"
                  class="flex items-center justify-center h-16 select-none hover:opacity-80 transition-opacity duration-200"
                >
                  <div class="flex items-center gap-2">
                    <img src="/logo.png" :alt="siteStore.siteName" class="w-10 h-10 rounded-lg" />
                    <span class="text-2xl font-bold text-blue-600 tracking-tight">
                      {{ siteStore.siteName }}
                    </span>
                  </div>
                </router-link>
              </div>
            </template>
          </div>
          <button
            v-if="!isCollapsed"
            @click="handleToggle"
            class="absolute top-1/2 -translate-y-1/2 right-2 flex items-center justify-center w-8 h-8 rounded-lg text-gray-500 hover:text-gray-700 hover:bg-gray-100/50 transition-colors duration-200 z-10 dark:text-gray-400 dark:hover:text-gray-200 dark:hover:bg-gray-700/50"
            title="收起侧边栏"
          >
            <Menu class="h-4 w-4" />
          </button>
        </div>

        <!-- 首页和搜索导航 -->
        <nav class="px-2 mt-4 space-y-1">
          <router-link
            to="/"
            :class="[
              'group flex items-center rounded-lg px-2 py-2 pl-4 text-gray-700 hover:bg-gray-100/30 hover:text-blue-600 font-medium transition-colors duration-200 min-h-[40px] dark:text-gray-300 dark:hover:text-blue-400 gap-3 justify-start',
              activePath === '/' ? 'bg-blue-500/20 text-blue-700 dark:bg-blue-500/10 dark:text-blue-400' : '',
            ]"
          >
            <div class="w-4 h-4 flex items-center justify-center">
              <Home
                :class="[
                  'h-4 w-4 group-hover:text-blue-600 dark:group-hover:text-blue-400',
                  activePath === '/' ? 'text-blue-700 dark:text-blue-400' : 'text-gray-500 dark:text-gray-400',
                ]"
              />
            </div>
            <span v-if="!isCollapsed" class="whitespace-nowrap transition-opacity duration-200 opacity-100">
              首页
            </span>
          </router-link>

          <a
            href="/search"
            @click.prevent="handleSearchClick"
            :class="[
              'group flex items-center rounded-lg px-2 py-2 pl-4 text-gray-700 hover:bg-gray-100/30 hover:text-blue-600 font-medium transition-colors duration-200 min-h-[40px] dark:text-gray-300 dark:hover:text-blue-400 gap-3 justify-start',
              activePath === '/search' ? 'bg-blue-500/20 text-blue-700 dark:bg-blue-500/10 dark:text-blue-400' : '',
            ]"
          >
            <div class="w-4 h-4 flex items-center justify-center">
              <Search
                :class="[
                  'h-4 w-4 group-hover:text-blue-600 dark:group-hover:text-blue-400',
                  activePath === '/search' ? 'text-blue-700 dark:text-blue-400' : 'text-gray-500 dark:text-gray-400',
                ]"
              />
            </div>
            <span v-if="!isCollapsed" class="whitespace-nowrap transition-opacity duration-200 opacity-100">
              搜索
            </span>
          </a>
        </nav>

        <!-- 菜单项 -->
        <div class="flex-1 overflow-y-auto px-2 pt-4">
          <div class="space-y-1">
            <router-link
              v-for="item in menuItems"
              :key="item.label"
              :to="item.href"
              :class="[
                'group flex items-center rounded-lg px-2 py-2 pl-4 text-sm text-gray-700 hover:bg-gray-100/30 hover:text-blue-600 font-medium transition-colors duration-200 min-h-[40px] dark:text-gray-300 dark:hover:text-blue-400 gap-3 justify-start',
                isActive(item.href) ? 'bg-blue-500/20 text-blue-700 dark:bg-blue-500/10 dark:text-blue-400' : '',
              ]"
            >
              <div class="w-4 h-4 flex items-center justify-center">
                <component
                  :is="item.icon"
                  :class="[
                    'h-4 w-4 group-hover:text-blue-600 dark:group-hover:text-blue-400',
                    isActive(item.href) ? 'text-blue-700 dark:text-blue-400' : 'text-gray-500 dark:text-gray-400',
                  ]"
                />
              </div>
              <span v-if="!isCollapsed" class="whitespace-nowrap transition-opacity duration-200 opacity-100">
                {{ item.label }}
              </span>
            </router-link>
          </div>
        </div>

        <!-- 致谢信息 -->
        <div class="px-2 pb-4">
          <div class="border-t border-gray-200/50 dark:border-gray-700/50 pt-3">
            <template v-if="!isCollapsed">
              <div class="text-xs text-gray-500 dark:text-gray-400 text-center px-2 leading-relaxed">
                <span>本项目基于 </span>
                <button
                  @click="() => openExternal('https://github.com/MoonTechLab/LunaTV')"
                  class="text-blue-600 dark:text-blue-400 hover:text-blue-700 dark:hover:text-blue-300 font-medium transition-colors"
                >
                  MoonTV
                </button>
                <button
                  @click="() => openExternal('https://github.com/MoonTechLab/LunaTV')"
                  class="text-blue-600 dark:text-blue-400 hover:text-blue-700 dark:hover:text-blue-300 transition-colors ml-1"
                  title="访问 MoonTV 项目"
                >
                  <ExternalLink class="h-3 w-3 inline" />
                </button>
                <span> 的二次开发</span>
              </div>
            </template>
            <template v-else>
              <div class="flex justify-center">
                <button
                  @click="() => openExternal('https://github.com/MoonTechLab/LunaTV')"
                  class="text-blue-600 dark:text-blue-400 hover:text-blue-700 dark:hover:text-blue-300 transition-colors p-1"
                  title="基于 MoonTV 的二次开发"
                >
                  <ExternalLink class="h-4 w-4" />
                </button>
              </div>
            </template>
          </div>
        </div>
      </div>
    </aside>
    <div :class="['transition-all duration-300 sidebar-offset', isCollapsed ? 'w-16' : 'w-64']"></div>
  </div>
</template>

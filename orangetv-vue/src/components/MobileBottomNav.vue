<script setup lang="ts">
import { Home, Search, Film, User } from 'lucide-vue-next'

interface Props {
  activePath?: string
}

const props = withDefaults(defineProps<Props>(), {
  activePath: '/',
})

const navItems = [
  { icon: Home, label: '首页', href: '/' },
  { icon: Search, label: '搜索', href: '/search' },
  { icon: Film, label: '分类', href: '/douban?type=movie' },
  // { icon: Radio, label: '直播', href: '/live' },
  { icon: User, label: '我的', href: '/admin' },
]

function isActive(href: string): boolean {
  if (href === '/') return props.activePath === '/'
  if (href === '/search') return props.activePath === '/search'
  if (href.startsWith('/douban')) return props.activePath.startsWith('/douban') || props.activePath.startsWith('/shortdrama')
  if (href === '/live') return props.activePath === '/live'
  if (href === '/admin') return props.activePath === '/admin'
  return false
}
</script>

<template>
  <nav
    class="fixed bottom-0 left-0 right-0 z-30 bg-white/80 dark:bg-gray-900/80 backdrop-blur-xl border-t border-gray-200/50 dark:border-gray-700/50 safe-area-bottom"
    style="backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px)"
  >
    <div class="flex items-center justify-around h-14">
      <router-link
        v-for="item in navItems"
        :key="item.href"
        :to="item.href"
        :class="[
          'flex flex-col items-center justify-center flex-1 h-full transition-colors',
          isActive(item.href)
            ? 'text-blue-600 dark:text-blue-400'
            : 'text-gray-500 dark:text-gray-400',
        ]"
      >
        <component :is="item.icon" class="w-5 h-5" />
        <span class="text-xs mt-1">{{ item.label }}</span>
      </router-link>
    </div>
  </nav>
</template>

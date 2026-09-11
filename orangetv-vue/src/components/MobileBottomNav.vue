<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, useId, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Home, Search, Sparkles, Film, User, Tv, Cat, Clover, X, Check } from 'lucide-vue-next'

interface Props {
  activePath?: string
}

const props = withDefaults(defineProps<Props>(), {
  activePath: '/',
})

const route = useRoute()
const categoryDialog = ref<HTMLDialogElement | null>(null)
const categoriesOpen = ref(false)
const dialogId = `mobile-categories-${useId()}`
let desktopQuery: MediaQueryList | null = null
let previousOverflow: string | null = null

const categories = [
  { type: 'movie', label: '电影', icon: Film },
  { type: 'tv', label: '剧集', icon: Tv },
  { type: 'anime', label: '动漫', icon: Cat },
  { type: 'show', label: '综艺', icon: Clover },
]

const selectedCategory = computed(() => route.path === '/douban' ? (route.query.type || 'movie') : '')

const navItems = [
  { icon: Home, label: '首页', href: '/' },
  { icon: Search, label: '搜索', href: '/search' },
  { icon: Sparkles, label: '推荐', href: '/recommend' },
  { icon: Film, label: '分类', href: '/douban' },
  // { icon: Radio, label: '直播', href: '/live' },
  { icon: User, label: '我的', href: '/admin' },
]

function isActive(href: string): boolean {
  if (href === '/') return props.activePath === '/'
  if (href === '/search') return props.activePath === '/search'
  if (href === '/recommend') return props.activePath.startsWith('/recommend')
  if (href.startsWith('/douban')) return props.activePath.startsWith('/douban') || props.activePath.startsWith('/shortdrama')
  if (href === '/live') return props.activePath === '/live'
  if (href === '/admin') return props.activePath === '/admin'
  return false
}

async function openCategories() {
  if (!categoryDialog.value || categoryDialog.value.open) return
  previousOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  categoryDialog.value.showModal()
  categoriesOpen.value = true
  await nextTick()
  const selectedLink = categoryDialog.value?.querySelector<HTMLElement>('a[aria-current="page"]')
  const focusTarget = selectedLink || categoryDialog.value?.querySelector<HTMLElement>('a')
  focusTarget?.focus()
}

function restoreScroll() {
  if (previousOverflow !== null) {
    document.body.style.overflow = previousOverflow
    previousOverflow = null
  }
}

function closeCategories() {
  categoryDialog.value?.close()
  categoriesOpen.value = false
  restoreScroll()
}

function handleDialogClose() {
  categoriesOpen.value = false
  restoreScroll()
}

function closeOnDesktop(event: MediaQueryListEvent) {
  if (event.matches) closeCategories()
}

watch(() => route.fullPath, closeCategories)
onMounted(() => {
  desktopQuery = window.matchMedia('(min-width: 768px)')
  desktopQuery.addEventListener('change', closeOnDesktop)
})
onUnmounted(() => {
  closeCategories()
  desktopQuery?.removeEventListener('change', closeOnDesktop)
})
</script>

<template>
  <nav
    class="fixed bottom-0 left-0 right-0 z-30 bg-white/80 dark:bg-gray-900/80 backdrop-blur-xl border-t border-gray-200/50 dark:border-gray-700/50 safe-area-bottom"
    style="backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px)"
  >
    <div class="flex items-center justify-around h-14">
      <template v-for="item in navItems" :key="item.href">
        <button
          v-if="item.href === '/douban'"
          type="button"
          aria-haspopup="dialog"
          :aria-controls="dialogId"
          :aria-expanded="categoriesOpen"
          :class="[
            'flex flex-col items-center justify-center flex-1 h-full transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-blue-500',
            categoriesOpen || isActive(item.href) ? 'text-blue-600 dark:text-blue-400' : 'text-gray-500 dark:text-gray-400',
          ]"
          @click="openCategories"
        >
          <component :is="item.icon" class="w-5 h-5" aria-hidden="true" />
          <span class="text-xs mt-1">{{ item.label }}</span>
        </button>
        <router-link
          v-else
          :to="item.href"
          :aria-current="isActive(item.href) ? 'page' : undefined"
          :class="[
            'flex flex-col items-center justify-center flex-1 h-full transition-colors',
            isActive(item.href) ? 'text-blue-600 dark:text-blue-400' : 'text-gray-500 dark:text-gray-400',
          ]"
        >
          <component :is="item.icon" class="w-5 h-5" aria-hidden="true" />
          <span class="text-xs mt-1">{{ item.label }}</span>
        </router-link>
      </template>
    </div>
  </nav>

  <Teleport to="body">
    <dialog
      :id="dialogId"
      ref="categoryDialog"
      :aria-labelledby="`${dialogId}-title`"
      class="fixed inset-x-0 bottom-0 top-auto m-0 w-full max-w-none max-h-[80vh] overflow-y-auto rounded-t-2xl border border-theme-border bg-theme-surface p-4 pb-[calc(1rem+env(safe-area-inset-bottom))] text-theme-text shadow-2xl backdrop:bg-black/40 backdrop:backdrop-blur-sm md:hidden"
      @click.self="closeCategories"
      @close="handleDialogClose"
    >
      <div class="mb-4 flex items-center justify-between">
        <h2 :id="`${dialogId}-title`" class="text-lg font-semibold">选择分类</h2>
        <button type="button" aria-label="关闭分类菜单" class="flex h-10 w-10 items-center justify-center rounded-full text-theme-text-secondary hover:bg-gray-100 dark:hover:bg-gray-700" @click="closeCategories">
          <X class="h-5 w-5" aria-hidden="true" />
        </button>
      </div>
      <div class="grid grid-cols-2 gap-3">
        <router-link
          v-for="category in categories"
          :key="category.type"
          :to="{ path: '/douban', query: { type: category.type } }"
          :aria-current="selectedCategory === category.type ? 'page' : undefined"
          :class="[
            'flex min-h-20 items-center gap-3 rounded-xl border p-4 font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500',
            selectedCategory === category.type
              ? 'border-blue-500 bg-blue-50 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300'
              : 'border-theme-border hover:border-blue-300 hover:bg-gray-100 dark:hover:bg-gray-700',
          ]"
          @click="closeCategories"
        >
          <component :is="category.icon" class="h-6 w-6 shrink-0" aria-hidden="true" />
          <span class="flex-1">{{ category.label }}</span>
          <Check v-if="selectedCategory === category.type" class="h-4 w-4 shrink-0" aria-hidden="true" />
        </router-link>
      </div>
    </dialog>
  </Teleport>
</template>

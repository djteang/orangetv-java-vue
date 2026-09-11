<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, useId } from 'vue'
import { Check, Monitor, Moon, Sun } from 'lucide-vue-next'
import ChineseRedIcon from './ChineseRedIcon.vue'
import { useThemeStore, type ThemeMode } from '@/stores/theme'
import { useLanternStore } from '@/stores/lantern'

const themeStore = useThemeStore()
const lanternStore = useLanternStore()
const isOpen = ref(false)
const containerRef = ref<HTMLDivElement | null>(null)
const triggerRef = ref<HTMLButtonElement | null>(null)
const menuRef = ref<HTMLDivElement | null>(null)
const menuId = `theme-menu-${useId()}`
const triggerId = `${menuId}-trigger`
const hintId = `${menuId}-hint`

const options = [
  { value: 'light', label: '明亮', icon: Sun },
  { value: 'dark', label: '黑夜', icon: Moon },
  { value: 'chinese-red', label: '中国红', icon: ChineseRedIcon },
  { value: 'system', label: '跟随系统', icon: Monitor },
] as const

const currentOption = computed(() => options.find(option => option.value === themeStore.mode) || options[3])
const isChineseRed = computed(() => themeStore.mode === 'chinese-red')

function getMenuButtons() {
  return Array.from(menuRef.value?.querySelectorAll<HTMLButtonElement>('[role="menuitemradio"]') || [])
}

async function openMenu(index = options.findIndex(option => option.value === themeStore.mode)) {
  isOpen.value = true
  await nextTick()
  const buttons = getMenuButtons()
  if (isOpen.value) buttons[index < 0 ? buttons.length - 1 : index]?.focus()
}

function closeMenu(restoreFocus = true) {
  isOpen.value = false
  if (restoreFocus) triggerRef.value?.focus()
}

function toggleMenu() {
  if (isOpen.value) closeMenu()
  else openMenu()
}

function selectTheme(mode: ThemeMode) {
  closeMenu()
  themeStore.setMode(mode)
}

function handleKeydown(event: KeyboardEvent) {
  if (!isOpen.value) return
  if (event.key === 'Tab') {
    closeMenu()
    return
  }

  const buttons = getMenuButtons()
  const currentIndex = buttons.indexOf(document.activeElement as HTMLButtonElement)
  let nextIndex = currentIndex
  switch (event.key) {
    case 'ArrowDown': nextIndex = (currentIndex + 1) % buttons.length; break
    case 'ArrowUp': nextIndex = (currentIndex - 1 + buttons.length) % buttons.length; break
    case 'Home': nextIndex = 0; break
    case 'End': nextIndex = buttons.length - 1; break
    case 'Escape': closeMenu(); break
    default: return
  }
  event.preventDefault()
  event.stopPropagation()
  if (event.key !== 'Escape') buttons[nextIndex]?.focus()
}

function handleOutsideClick(event: PointerEvent) {
  if (isOpen.value && !containerRef.value?.contains(event.target as Node)) closeMenu(false)
}

function handleFocusOut(event: FocusEvent) {
  if (isOpen.value && !containerRef.value?.contains(event.relatedTarget as Node | null)) closeMenu(false)
}

onMounted(() => document.addEventListener('pointerdown', handleOutsideClick))
onUnmounted(() => document.removeEventListener('pointerdown', handleOutsideClick))
</script>

<template>
  <div ref="containerRef" class="relative z-50" @keydown="handleKeydown" @focusout="handleFocusOut">
    <button
      :id="triggerId"
      ref="triggerRef"
      type="button"
      class="relative w-10 h-10 p-2 rounded-full flex items-center justify-center text-gray-600 hover:bg-gray-200/50 dark:text-gray-300 dark:hover:bg-gray-700/50 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
      :class="{ 'chinese-red-trigger': isChineseRed && !!lanternStore.activeMoment }"
      :aria-label="`切换主题，当前：${currentOption.label}`"
      :title="`切换主题，当前：${currentOption.label}`"
      aria-haspopup="menu"
      :aria-expanded="isOpen"
      :aria-controls="isOpen ? menuId : undefined"
      :aria-describedby="isChineseRed ? hintId : undefined"
      @click="toggleMenu"
      @keydown.down.stop.prevent="openMenu(0)"
      @keydown.up.stop.prevent="openMenu(-1)"
    >
      <component :is="currentOption.icon" class="theme-icon w-5 h-5" aria-hidden="true" />
    </button>
    <span v-if="isChineseRed" :id="hintId" class="sr-only">中国红彩蛋可以通过页面上的独立操作栏开启、切换、暂停或收起。</span>

    <Transition
      enter-active-class="transition duration-150 ease-out motion-reduce:transition-none"
      enter-from-class="opacity-0 -translate-y-1"
      enter-to-class="opacity-100 translate-y-0"
      leave-active-class="transition duration-100 ease-in motion-reduce:transition-none"
      leave-from-class="opacity-100 translate-y-0"
      leave-to-class="opacity-0 -translate-y-1"
    >
      <div
        v-if="isOpen"
        :id="menuId"
        ref="menuRef"
        role="menu"
        :aria-labelledby="triggerId"
        class="absolute right-0 top-full mt-2 w-44 max-h-[calc(100dvh-5.5rem)] overflow-y-auto overscroll-contain rounded-xl border border-theme-border bg-theme-surface p-1.5 shadow-xl"
      >
        <button
          v-for="option in options"
          :key="option.value"
          type="button"
          role="menuitemradio"
          :aria-checked="themeStore.mode === option.value"
          tabindex="-1"
          :class="[
            'flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-blue-500',
            themeStore.mode === option.value
              ? 'bg-blue-50 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300'
              : 'text-theme-text hover:bg-gray-100 dark:hover:bg-gray-700',
          ]"
          @click="selectTheme(option.value)"
        >
          <component :is="option.icon" class="w-4 h-4 shrink-0" aria-hidden="true" />
          <span class="flex-1 text-left">{{ option.label }}</span>
          <Check v-if="themeStore.mode === option.value" class="w-4 h-4" aria-hidden="true" />
        </button>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.chinese-red-trigger { background: rgb(var(--color-theme-surface) / .92); backdrop-filter: blur(8px); }
</style>

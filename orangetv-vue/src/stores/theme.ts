import { defineStore } from 'pinia'
import { computed, onScopeDispose, ref, watch } from 'vue'

const themeModes = ['light', 'dark', 'chinese-red', 'system'] as const
export type ThemeMode = (typeof themeModes)[number]
type ResolvedTheme = Exclude<ThemeMode, 'system'>

const themeColors: Record<ResolvedTheme, string> = {
  light: '#ffffff',
  dark: '#111827',
  'chinese-red': '#fff8f0',
}

function readStoredMode(): ThemeMode {
  try {
    const saved = localStorage.getItem('theme')
    if (themeModes.includes(saved as ThemeMode)) return saved as ThemeMode
  } catch {
    // 禁用本地存储时，仍可在当前页面切换主题。
  }
  return 'system'
}

export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>(readStoredMode())
  const systemPreference = window.matchMedia('(prefers-color-scheme: dark)')
  const systemIsDark = ref(systemPreference.matches)
  const resolvedMode = computed<ResolvedTheme>(() => mode.value === 'system'
    ? (systemIsDark.value ? 'dark' : 'light')
    : mode.value)
  const isDark = computed(() => resolvedMode.value === 'dark')
  let initialized = false

  function applyTheme() {
    const root = document.documentElement
    root.classList.toggle('dark', isDark.value)
    root.dataset.theme = resolvedMode.value
    root.style.colorScheme = isDark.value ? 'dark' : 'light'

    let meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]')
    if (!meta) {
      meta = document.createElement('meta')
      meta.name = 'theme-color'
      document.head.appendChild(meta)
    }
    meta.content = themeColors[resolvedMode.value]
  }

  function setMode(newMode: ThemeMode) {
    if (mode.value === newMode) return

    const update = () => {
      mode.value = newMode
      try {
        localStorage.setItem('theme', newMode)
      } catch {
        // 本地存储不可用不应阻止主题生效。
      }
    }

    const transitionDocument = document as Document & {
      startViewTransition?: (callback: () => void) => unknown
    }
    if (transitionDocument.startViewTransition && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      transitionDocument.startViewTransition(update)
    } else {
      update()
    }
  }

  function toggle() {
    const themes: ResolvedTheme[] = ['light', 'dark', 'chinese-red']
    setMode(themes[(themes.indexOf(resolvedMode.value) + 1) % themes.length])
  }

  function handleSystemChange(event: MediaQueryListEvent) {
    systemIsDark.value = event.matches
  }

  function init() {
    if (initialized) return
    initialized = true
    applyTheme()
    systemPreference.addEventListener('change', handleSystemChange)
  }

  watch(resolvedMode, applyTheme, { flush: 'sync' })
  onScopeDispose(() => systemPreference.removeEventListener('change', handleSystemChange))

  return { mode, resolvedMode, isDark, setMode, toggle, init }
})

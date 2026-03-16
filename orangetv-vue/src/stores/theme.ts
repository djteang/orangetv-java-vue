import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export type ThemeMode = 'light' | 'dark' | 'system'

export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>((localStorage.getItem('theme') as ThemeMode) || 'system')
  const isDark = ref(false)

  function updateDarkClass() {
    if (mode.value === 'dark') {
      isDark.value = true
    } else if (mode.value === 'light') {
      isDark.value = false
    } else {
      isDark.value = window.matchMedia('(prefers-color-scheme: dark)').matches
    }

    if (isDark.value) {
      document.documentElement.classList.add('dark')
    } else {
      document.documentElement.classList.remove('dark')
    }

    // 更新 meta theme-color
    const meta = document.querySelector('meta[name="theme-color"]')
    const color = isDark.value ? '#0c111c' : '#f9fbfe'
    if (meta) {
      meta.setAttribute('content', color)
    } else {
      const newMeta = document.createElement('meta')
      newMeta.name = 'theme-color'
      newMeta.content = color
      document.head.appendChild(newMeta)
    }
  }

  function setMode(newMode: ThemeMode) {
    mode.value = newMode
    localStorage.setItem('theme', newMode)
    updateDarkClass()
  }

  function toggle() {
    const targetMode: ThemeMode = isDark.value ? 'light' : 'dark'

    // 使用 View Transitions API（如果支持）
    if ((document as any).startViewTransition) {
      (document as any).startViewTransition(() => {
        setMode(targetMode)
      })
    } else {
      setMode(targetMode)
    }
  }

  // 监听系统主题变化
  function init() {
    updateDarkClass()
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
      if (mode.value === 'system') {
        updateDarkClass()
      }
    })
  }

  watch(mode, updateDarkClass)

  return {
    mode,
    isDark,
    setMode,
    toggle,
    init,
  }
})

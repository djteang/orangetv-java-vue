import { defineStore } from 'pinia'
import { computed, onScopeDispose, ref, shallowRef, watch } from 'vue'
import { useThemeStore } from './theme'

const moments = [
  {
    id: 'landscape', chapter: '壹', title: '锦绣山河',
    message: '青绿群山、蜿蜒长城与熊猫旅人，在奇境中相遇。',
    motto: '一卷青绿，万里中国',
  },
  {
    id: 'heritage', chapter: '贰', title: '文脉千年',
    message: '醒狮踏着灯火起舞，让千年的中国之美在今天生长。',
    motto: '承古意，开新境',
  },
  {
    id: 'space', chapter: '叁', title: '星河逐梦',
    message: '月兔穿上航天服，与长征火箭一起，把想象写进星河。',
    motto: '自信，生于创造',
  },
] as const

const storageKey = 'orangetv:lantern-next'

function readNextMoment() {
  try {
    const value = Number(localStorage.getItem(storageKey))
    if (Number.isInteger(value) && value >= 0 && value < moments.length) return value
  } catch {
    // 禁用存储时仍可在本次访问中探索。
  }
  return 0
}

export const useLanternStore = defineStore('lantern', () => {
  const themeStore = useThemeStore()
  const activeMoment = shallowRef<(typeof moments)[number] | null>(null)
  const motionPaused = ref(false)
  const motionPreference = window.matchMedia('(prefers-reduced-motion: reduce)')
  const prefersReducedMotion = ref(motionPreference.matches)
  const isMotionPaused = computed(() => motionPaused.value || prefersReducedMotion.value)
  let nextMoment = readNextMoment()

  function dismiss() {
    activeMoment.value = null
  }

  function discover() {
    if (themeStore.mode !== 'chinese-red') return false
    activeMoment.value = moments[nextMoment]
    nextMoment = (nextMoment + 1) % moments.length
    try {
      localStorage.setItem(storageKey, String(nextMoment))
    } catch {
      // 探索记录不是使用彩蛋的前提。
    }
    return true
  }

  function toggleMotion() {
    if (!prefersReducedMotion.value) motionPaused.value = !motionPaused.value
  }

  function updateMotionPreference(event: MediaQueryListEvent) {
    prefersReducedMotion.value = event.matches
  }

  motionPreference.addEventListener('change', updateMotionPreference)
  watch(() => themeStore.mode, mode => {
    if (mode !== 'chinese-red') dismiss()
  }, { flush: 'sync' })
  onScopeDispose(() => {
    motionPreference.removeEventListener('change', updateMotionPreference)
    dismiss()
  })

  return { activeMoment, isMotionPaused, prefersReducedMotion, discover, dismiss, toggleMotion }
})

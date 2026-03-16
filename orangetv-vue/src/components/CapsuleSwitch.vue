<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, nextTick, computed } from 'vue'

interface Option {
  label: string
  value: string
}

interface Props {
  options: Option[]
  active: string
}

const props = defineProps<Props>()
const emit = defineEmits<{
  change: [value: string]
}>()

const containerRef = ref<HTMLDivElement | null>(null)
const buttonRefs = ref<(HTMLButtonElement | null)[]>([])
const indicatorLeft = ref(0)
const indicatorWidth = ref(0)

const activeIndex = computed(() => props.options.findIndex(opt => opt.value === props.active))

function updateIndicatorPosition() {
  const idx = activeIndex.value
  if (idx < 0 || !buttonRefs.value[idx] || !containerRef.value) return
  const button = buttonRefs.value[idx]
  const container = containerRef.value
  if (!button || !container) return
  const buttonRect = button.getBoundingClientRect()
  const containerRect = container.getBoundingClientRect()
  if (buttonRect.width > 0) {
    indicatorLeft.value = buttonRect.left - containerRect.left
    indicatorWidth.value = buttonRect.width
  }
}

function setButtonRef(el: any, index: number) {
  buttonRefs.value[index] = el as HTMLButtonElement | null
}

watch(activeIndex, () => {
  nextTick(() => setTimeout(updateIndicatorPosition, 0))
})

onMounted(() => {
  setTimeout(updateIndicatorPosition, 0)
  window.addEventListener('resize', updateIndicatorPosition)
})

onUnmounted(() => {
  window.removeEventListener('resize', updateIndicatorPosition)
})
</script>

<template>
  <div
    ref="containerRef"
    class="relative inline-flex bg-gray-300/80 rounded-full p-1 dark:bg-gray-700"
  >
    <!-- 滑动的白色背景指示器 -->
    <div
      v-if="indicatorWidth > 0"
      class="absolute top-1 bottom-1 bg-white dark:bg-gray-500 rounded-full shadow-sm transition-all duration-300 ease-out"
      :style="{ left: indicatorLeft + 'px', width: indicatorWidth + 'px' }"
    />
    <button
      v-for="(opt, index) in options"
      :key="opt.value"
      :ref="(el) => setButtonRef(el, index)"
      @click="emit('change', opt.value)"
      :class="[
        'relative z-10 w-16 px-3 py-1 text-xs sm:w-20 sm:py-2 sm:text-sm rounded-full font-medium transition-all duration-200 cursor-pointer',
        active === opt.value
          ? 'text-gray-900 dark:text-gray-100'
          : 'text-gray-700 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100',
      ]"
    >
      {{ opt.label }}
    </button>
  </div>
</template>

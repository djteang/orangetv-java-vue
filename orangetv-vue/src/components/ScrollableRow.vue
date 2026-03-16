<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'

interface Props {
  scrollDistance?: number
}

const props = withDefaults(defineProps<Props>(), {
  scrollDistance: 1000,
})

const containerRef = ref<HTMLElement | null>(null)
const showLeftScroll = ref(false)
const showRightScroll = ref(false)
const isHovered = ref(false)

let resizeObserver: ResizeObserver | null = null
let mutationObserver: MutationObserver | null = null

function checkScroll() {
  if (!containerRef.value) return
  const { scrollWidth, clientWidth, scrollLeft } = containerRef.value
  const threshold = 1
  showRightScroll.value = scrollWidth - (scrollLeft + clientWidth) > threshold
  showLeftScroll.value = scrollLeft > threshold
}

function handleScrollLeft() {
  containerRef.value?.scrollBy({ left: -props.scrollDistance, behavior: 'smooth' })
}

function handleScrollRight() {
  containerRef.value?.scrollBy({ left: props.scrollDistance, behavior: 'smooth' })
}

function handleMouseEnter() {
  isHovered.value = true
  checkScroll()
}

function handleMouseLeave() {
  isHovered.value = false
}

onMounted(() => {
  checkScroll()
  window.addEventListener('resize', checkScroll)

  if (containerRef.value) {
    resizeObserver = new ResizeObserver(() => checkScroll())
    resizeObserver.observe(containerRef.value)

    mutationObserver = new MutationObserver(() => setTimeout(checkScroll, 100))
    mutationObserver.observe(containerRef.value, {
      childList: true, subtree: true, attributes: true,
      attributeFilter: ['style', 'class'],
    })
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', checkScroll)
  resizeObserver?.disconnect()
  mutationObserver?.disconnect()
})
</script>

<template>
  <div
    class="relative"
    @mouseenter="handleMouseEnter"
    @mouseleave="handleMouseLeave"
  >
    <div
      ref="containerRef"
      class="flex space-x-6 overflow-x-auto scrollbar-hide py-1 sm:py-2 pb-12 sm:pb-14 px-4 sm:px-6"
      @scroll="checkScroll"
    >
      <slot />
    </div>

    <!-- 左滚动按钮 -->
    <div
      v-if="showLeftScroll"
      :class="[
        'hidden sm:flex absolute left-0 top-0 bottom-0 w-16 items-center justify-center z-[600] transition-opacity duration-200',
        isHovered ? 'opacity-100' : 'opacity-0',
      ]"
      :style="{ background: 'transparent', pointerEvents: 'none' }"
    >
      <div
        class="absolute inset-0 flex items-center justify-center"
        :style="{ top: '40%', bottom: '60%', left: '-4.5rem', pointerEvents: 'auto' }"
      >
        <button
          @click="handleScrollLeft"
          class="w-12 h-12 bg-white/95 rounded-full shadow-lg flex items-center justify-center hover:bg-white border border-gray-200 transition-transform hover:scale-105 dark:bg-gray-800/90 dark:hover:bg-gray-700 dark:border-gray-600"
        >
          <ChevronLeft class="w-6 h-6 text-gray-600 dark:text-gray-300" />
        </button>
      </div>
    </div>

    <!-- 右滚动按钮 -->
    <div
      v-if="showRightScroll"
      :class="[
        'hidden sm:flex absolute right-0 top-0 bottom-0 w-16 items-center justify-center z-[600] transition-opacity duration-200',
        isHovered ? 'opacity-100' : 'opacity-0',
      ]"
      :style="{ background: 'transparent', pointerEvents: 'none' }"
    >
      <div
        class="absolute inset-0 flex items-center justify-center"
        :style="{ top: '40%', bottom: '60%', right: '-4.5rem', pointerEvents: 'auto' }"
      >
        <button
          @click="handleScrollRight"
          class="w-12 h-12 bg-white/95 rounded-full shadow-lg flex items-center justify-center hover:bg-white border border-gray-200 transition-transform hover:scale-105 dark:bg-gray-800/90 dark:hover:bg-gray-700 dark:border-gray-600"
        >
          <ChevronRight class="w-6 h-6 text-gray-600 dark:text-gray-300" />
        </button>
      </div>
    </div>
  </div>
</template>

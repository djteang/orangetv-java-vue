<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, onUnmounted, ref, watch } from 'vue'
import { useLanternStore } from '@/stores/lantern'

const lanternStore = useLanternStore()
const pageHidden = ref(document.hidden)
const isPaused = computed(() => lanternStore.isMotionPaused || pageHidden.value)
const transitions = new Map<Element, Animation>()
const scenes = {
  landscape: defineAsyncComponent(() => import('./chinese-red/MountainsScene.vue')),
  heritage: defineAsyncComponent(() => import('./chinese-red/HeritageScene.vue')),
  space: defineAsyncComponent(() => import('./chinese-red/SpaceScene.vue')),
}

function handleVisibilityChange() {
  pageHidden.value = document.hidden
}

function cancelTransition(element: Element) {
  const animation = transitions.get(element)
  if (!animation) return
  // 场景被替换时，从当前透明度开始退场，避免跳回过渡起点。
  const background = element as HTMLElement
  background.style.opacity = getComputedStyle(element).opacity
  transitions.delete(element)
  animation.cancel()
}

function fadeBackground(element: Element, from: string, to: string, done: () => void) {
  cancelTransition(element)
  const background = element as HTMLElement
  background.style.opacity = to

  // 暂停时仍可主动切换和收起场景，新场景直接呈现静止画面。
  if (isPaused.value || !background.animate) {
    done()
    return
  }

  const animation = background.animate([{ opacity: from }, { opacity: to }], {
    duration: 850,
    easing: 'ease',
  })
  transitions.set(element, animation)
  void animation.finished.then(() => {
    if (transitions.get(element) !== animation) return
    transitions.delete(element)
    animation.cancel()
    done()
  }, () => {
    // 快速切换、收起或卸载会主动取消过渡。
  })
}

function enterBackground(element: Element, done: () => void) {
  fadeBackground(element, '0', '1', done)
}

function leaveBackground(element: Element, done: () => void) {
  fadeBackground(element, getComputedStyle(element).opacity, '0', done)
}

function finishTransitions() {
  for (const animation of transitions.values()) animation.finish()
}

watch([isPaused, () => lanternStore.prefersReducedMotion], ([paused, reducedMotion]) => {
  for (const animation of transitions.values()) {
    if (reducedMotion) animation.finish()
    else if (paused) animation.pause()
    else animation.play()
  }
}, { flush: 'sync' })

watch(() => lanternStore.activeMoment, moment => {
  if (!moment || isPaused.value) finishTransitions()
}, { flush: 'sync' })

onMounted(() => document.addEventListener('visibilitychange', handleVisibilityChange))
onUnmounted(() => {
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  for (const animation of transitions.values()) animation.cancel()
  transitions.clear()
  lanternStore.dismiss()
})
</script>

<template>
  <p class="sr-only" role="status" aria-atomic="true">
    <template v-if="lanternStore.activeMoment">
      已发现中国红背景：{{ lanternStore.activeMoment.title }}。{{ lanternStore.activeMoment.message }}
      可以使用页面上的彩蛋操作栏探索下一幕、暂停动画或收起背景。
    </template>
  </p>
  <div class="china-background-layer" :class="{ 'is-paused': isPaused }" aria-hidden="true">
    <Transition
      :css="false"
      @enter="enterBackground"
      @leave="leaveBackground"
      @enter-cancelled="cancelTransition"
      @leave-cancelled="cancelTransition"
    >
      <div
        v-if="lanternStore.activeMoment"
        :key="lanternStore.activeMoment.id"
        class="china-background"
        :class="'background-' + lanternStore.activeMoment.id"
        :data-scene="lanternStore.activeMoment.id"
      >
        <component :is="scenes[lanternStore.activeMoment.id]" />
        <div class="china-background__reading-light" />
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.china-background-layer {
  --china-animation-play-state: running;
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  user-select: none;
}
.china-background-layer.is-paused { --china-animation-play-state: paused; }
.china-background {
  position: absolute;
  inset: 0;
  overflow: hidden;
  contain: layout paint;
}
.background-landscape { background: radial-gradient(ellipse at 8% 18%, #e5eed5, transparent 58%), linear-gradient(155deg, #fff4dc 16%, #e2edde 72%, #b9d2b5); }
.background-heritage { background: radial-gradient(ellipse at 85% 6%, #ffdc9f, transparent 56%), linear-gradient(135deg, #fff0d9, #f5c9ab); }
.background-space { background: radial-gradient(ellipse at 91% 18%, #c2bce9, transparent 56%), linear-gradient(130deg, #e9f1e3, #ece5f2 58%, #b2c5d4); }
.china-background :deep(.scene-artwork) { position: absolute; inset: 0; }
.china-background :deep(svg) { display: block; width: 100%; height: 100%; overflow: visible; }
.china-background__reading-light {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 54% 86% at 50% 36%, rgb(var(--color-theme-bg) / .99) 0%, rgb(var(--color-theme-bg) / .92) 38%, rgb(var(--color-theme-bg) / .16) 82%, transparent 100%),
    linear-gradient(0deg, rgb(var(--color-theme-bg) / .12), rgb(var(--color-theme-bg) / .08));
}
.china-background-layer :deep(*),
.china-background-layer :deep(*::before),
.china-background-layer :deep(*::after) { animation-play-state: var(--china-animation-play-state, running) !important; }
@media (max-width: 767px) {
  .china-background__reading-light {
    background: radial-gradient(ellipse 100% 47% at 50% 45%, rgb(var(--color-theme-bg) / .97) 0%, rgb(var(--color-theme-bg) / .9) 42%, rgb(var(--color-theme-bg) / .18) 100%);
  }
}
@media (prefers-reduced-motion: reduce) {
  .china-background-layer, .china-background-layer :deep(*) { animation: none !important; transition: none !important; }
}
</style>

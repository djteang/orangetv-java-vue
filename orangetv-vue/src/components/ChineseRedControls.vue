<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { ArrowRight, EyeOff, Pause, Play, Sparkles } from 'lucide-vue-next'
import { useThemeStore } from '@/stores/theme'
import { useLanternStore } from '@/stores/lantern'

const themeStore = useThemeStore()
const lanternStore = useLanternStore()
const exploreRef = ref<HTMLButtonElement | null>(null)
const nextRef = ref<HTMLButtonElement | null>(null)

async function startExploring() {
  if (!lanternStore.discover()) return
  await nextTick()
  nextRef.value?.focus({ preventScroll: true })
}

function showNextScene() {
  lanternStore.discover()
}

function toggleAnimation() {
  lanternStore.toggleMotion()
}

async function dismissScene() {
  lanternStore.dismiss()
  await nextTick()
  exploreRef.value?.focus({ preventScroll: true })
}
</script>

<template>
  <div v-if="themeStore.mode === 'chinese-red'" class="chinese-red-controls" :class="{ 'is-active': !!lanternStore.activeMoment }" role="group" aria-label="中国红彩蛋" @click.stop>
    <template v-if="lanternStore.activeMoment">
      <div class="scene-control-actions">
        <button ref="nextRef" type="button" class="scene-control-action" aria-label="探索下一幕彩蛋" @click="showNextScene()">
          <ArrowRight :size="14" aria-hidden="true" /><span>下一幕</span>
        </button>
        <button
          type="button"
          class="scene-control-action"
          :aria-pressed="lanternStore.isMotionPaused"
          :aria-label="lanternStore.prefersReducedMotion ? '系统已开启减少动态效果，背景保持静止' : lanternStore.isMotionPaused ? '继续背景动画' : '暂停背景动画'"
          :title="lanternStore.prefersReducedMotion ? '已跟随系统的减少动态效果设置' : undefined"
          :disabled="lanternStore.prefersReducedMotion"
          @click="toggleAnimation()"
        >
          <Play v-if="lanternStore.isMotionPaused && !lanternStore.prefersReducedMotion" :size="14" aria-hidden="true" />
          <Pause v-else :size="14" aria-hidden="true" />
          <span>{{ lanternStore.prefersReducedMotion ? '静止' : lanternStore.isMotionPaused ? '继续' : '暂停' }}</span>
        </button>
        <button type="button" class="scene-control-action" aria-label="收起彩蛋背景" @click="dismissScene">
          <EyeOff :size="14" aria-hidden="true" /><span>收起</span>
        </button>
      </div>
    </template>
    <button v-else ref="exploreRef" type="button" class="scene-explore-button" @click="startExploring">
      <Sparkles :size="16" aria-hidden="true" />探索彩蛋<ArrowRight :size="14" aria-hidden="true" />
    </button>
  </div>
</template>

<style scoped>
.chinese-red-controls {
  display: inline-flex;
  align-items: center;
  width: max-content;
  max-width: 100%;
  min-width: 0;
  height: var(--china-controls-height, 54px);
  pointer-events: auto;
}
.chinese-red-controls.is-active {
  padding: 4px 6px;
  border: 1px solid rgb(var(--color-theme-border));
  border-radius: 14px;
  color: rgb(var(--color-theme-text));
  background: rgb(var(--color-theme-surface) / .97);
}
.scene-control-actions { display: flex; flex-shrink: 0; align-items: center; gap: 2px; }
.scene-control-action, .scene-explore-button {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 44px;
  padding: 0 6px;
  border-radius: 10px;
  font-size: 12px;
  white-space: nowrap;
  touch-action: manipulation;
  transition: background-color 160ms ease;
}
.scene-control-action { color: rgb(var(--color-primary-700)); }
.scene-control-action:hover { background: rgb(var(--color-primary-100) / .75); }
.scene-control-action[aria-pressed='true'] { background: rgb(var(--color-primary-50)); }
.scene-control-action:disabled { color: rgb(var(--color-theme-text-secondary)); cursor: default; }
.scene-explore-button { gap: 7px; padding: 0 12px; color: #fff; background: rgb(var(--color-primary-600)); font-weight: 600; }
.scene-explore-button:hover { background: rgb(var(--color-primary-700)); }
.scene-control-action:focus-visible, .scene-explore-button:focus-visible { outline: 2px solid rgb(var(--color-primary-500)); outline-offset: 1px; }
@media (prefers-reduced-motion: reduce) { .scene-control-action, .scene-explore-button { transition: none; } }
</style>

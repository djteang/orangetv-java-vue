<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, useId } from 'vue'
import { ArrowRight, RotateCw, Square } from 'lucide-vue-next'
import DiscoveryPortal from './DiscoveryPortal.vue'

withDefaults(defineProps<{
  mode?: 'loading' | 'empty' | 'error'
  title: string
  description?: string
  actionLabel?: string
  compact?: boolean
  variant?: 'search' | 'movie' | 'tv' | 'anime' | 'show' | 'recommend' | 'favorites'
}>(), { mode: 'empty', variant: 'search', compact: false })
defineEmits<{ action: [] }>()

const id = useId().replace(/:/g, '')
const root = ref<HTMLElement | null>(null)
const inView = ref(true)
const tabVisible = ref(true)
const animate = computed(() => inView.value && tabVisible.value)
let observer: IntersectionObserver | undefined
const updateVisibility = () => { tabVisible.value = !document.hidden }
onMounted(() => {
  updateVisibility()
  document.addEventListener('visibilitychange', updateVisibility)
  if ('IntersectionObserver' in window && root.value) {
    observer = new IntersectionObserver(([entry]) => { inView.value = entry.isIntersecting }, { rootMargin: '80px' })
    observer.observe(root.value)
  }
})
onUnmounted(() => { observer?.disconnect(); document.removeEventListener('visibilitychange', updateVisibility) })
</script>

<template>
  <div ref="root" class="discovery-state" :class="[`discovery-state--${mode}`, { 'is-compact': compact, 'is-paused': !animate }]" :data-variant="variant">
    <svg class="discovery-art" viewBox="0 0 360 230" fill="none" aria-hidden="true" focusable="false">
      <defs>
        <linearGradient :id="`${id}-visor`" x1="246" y1="105" x2="294" y2="149" gradientUnits="userSpaceOnUse"><stop stop-color="#506d79" /><stop offset="1" stop-color="#203a43" /></linearGradient>
      </defs>
      <ellipse class="discovery-halo" cx="176" cy="112" rx="143" ry="96" />
      <g class="discovery-stars" stroke="#456b67" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="m48 103 2.5-7 2.5 7 7 2.5-7 2.5-2.5 7-2.5-7-7-2.5Zm260-61 2-5 2 5 5 2-5 2-2 5-2-5-5-2Z" />
        <path d="M222 31v8m-4-4h8M75 167v6m-3-3h6" opacity=".5" />
        <circle cx="102" cy="27" r="2" fill="#92dcb2" /><circle cx="308" cy="173" r="2" fill="#92dcb2" />
      </g>
      <g class="discovery-moon" stroke="#344952" stroke-width="2.2">
        <circle cx="275" cy="48" r="16" fill="#efadbe" />
        <path d="M260 44c-22 8-13 18 9 15s40-18 22-19" stroke="#749bb0" stroke-width="4" stroke-linecap="round" />
        <circle cx="279" cy="42" r="3" fill="#d77f99" stroke="none" />
      </g>
      <ellipse class="discovery-ground" cx="155" cy="206" rx="92" ry="7" />
      <path d="m79 190 28-10 70 3 33 10-19 10-83-1Z" fill="#c4d8c2" stroke="#3c5750" stroke-width="2.5" stroke-linejoin="round" />
      <path d="m107 185 7 6m59-3 8 7m-60 1 18 1" stroke="#6a9480" stroke-width="2" stroke-linecap="round" />
      <DiscoveryPortal :mode="mode" :paused="!animate" />
      <g class="discovery-film" stroke="#334e54" stroke-width="2.5" stroke-linejoin="round">
        <path d="m63 43 37 10-13 47-37-10Z" fill="#fff4da" />
        <path d="m68 55 21 6-6 22-21-6Z" fill="#95c9c1" />
        <path d="m69 75 6-8 8 12" stroke="#4b8985" />
        <path d="m59 51 3 1m-6 10 3 1m-6 10 3 1m-6 9 3 1m37-20 3 1m-6 10 3 1m-6 10 3 1" stroke-width="3" />
      </g>
      <path v-if="mode === 'loading'" class="discovery-scan" d="m250 122-45-38v65Z" fill="#c1ef9655" />
      <g class="discovery-explorer" stroke="#344c55" stroke-width="2.7" stroke-linecap="round" stroke-linejoin="round">
        <ellipse cx="270" cy="207" rx="31" ry="4" fill="#344c5514" stroke="none" />
        <g class="discovery-explorer-body">
          <path d="m251 178-5 17h15l3-13m16-3 7 16h13l-11-23" fill="#9bb9b8" />
          <path d="m238 146-12 11-10-4m79-7 12 9 7-6" fill="none" />
          <path d="M253 104v-9l12-8" /><circle cx="267" cy="85" r="5" fill="#e7b278" />
          <path d="M245 102c14-7 36-6 47 7 7 9 7 51 0 64-9 13-38 13-49 1-9-11-10-61 2-72Z" fill="#f3e9d2" />
          <path d="M245 154h49v13c-8 16-38 17-49 3Z" fill="#a7c9c2" stroke="none" />
          <path d="M246 112c12-7 28-6 38 2l2 28c-11 8-25 9-38 1Z" :fill="`url(#${id}-visor)`" />
          <path d="m251 116 10-2" stroke="#b9e4dc" stroke-width="2" />
          <g class="discovery-eyes" fill="#d6f4ba" stroke="none"><ellipse cx="258" cy="131" rx="3" ry="5" /><ellipse cx="276" cy="130" rx="3" ry="5" /></g>
          <path :d="mode === 'error' ? 'M263 140q4-3 8 0' : 'M263 139q4 4 8 0'" stroke="#d6f4ba" stroke-width="1.8" />
          <rect x="259" y="159" width="19" height="13" rx="3" fill="#e9f4d5" stroke-width="1.8" />
          <path v-if="variant === 'favorites'" d="m268 169-5-4c-3-4 2-6 5-2 3-4 8-2 5 2Z" fill="#d6838e" stroke="none" />
          <path v-else-if="variant === 'anime' || variant === 'recommend'" d="m268 160 2 4 4 1-3 2v4l-3-2-4 2 1-4-3-2 4-1Z" fill="#c59456" stroke="none" />
          <path v-else d="m265 162 7 4-7 4Z" fill="#588b84" stroke="none" />
        </g>
      </g>
      <g v-if="mode !== 'loading'" class="discovery-thought" stroke="#456663" stroke-width="2" stroke-linecap="round">
        <path d="M303 101c18-20 39-9 29 4-5 7-11 6-19 4l-7 6v-9" fill="#f0f5df" />
        <path v-if="mode === 'error'" d="m318 97 8 7m0-7-8 7" stroke="#c66d64" />
        <path v-else d="M316 98c0-6 10-5 9 0-1 3-6 2-6 5m0 3v.5" />
      </g>
    </svg>
    <div class="discovery-copy">
      <div class="discovery-eyebrow" aria-hidden="true"><span></span>{{ mode === 'loading' ? '正在接收片库信号' : mode === 'error' ? '信号暂时偏离航线' : '下一段故事，正在路上' }}</div>
      <div role="status" aria-live="polite" aria-atomic="true">
        <h3>{{ title }}</h3>
        <p v-if="description">{{ description }}</p>
      </div>
      <div v-if="mode === 'loading'" class="discovery-loading-track" aria-hidden="true"><i></i><i></i><i></i></div>
      <button v-if="actionLabel" type="button" class="discovery-action" @click="$emit('action')"><RotateCw v-if="mode === 'error'" :size="15" aria-hidden="true" /><Square v-else-if="mode === 'loading'" :size="12" aria-hidden="true" />{{ actionLabel }}<ArrowRight v-if="mode === 'empty'" :size="15" aria-hidden="true" /></button>
      <slot />
    </div>
  </div>
</template>

<style scoped>
.discovery-state { display: flex; align-items: center; justify-content: center; gap: 28px; min-height: 300px; padding: 30px 28px; border: 1px solid rgb(var(--color-theme-border) / .6); border-radius: 24px; background: radial-gradient(ellipse at 26% 50%, rgb(137 194 117 / .07), transparent 65%), rgb(var(--color-theme-surface) / .45); }
.discovery-art { display: block; flex: 0 1 330px; width: 330px; max-width: 48%; height: auto; overflow: visible; }
.discovery-halo { fill: #8bb79b0c; }
.discovery-ground { fill: rgb(var(--color-theme-text) / .05); }
.discovery-copy { min-width: 0; max-width: 350px; }
.discovery-eyebrow { display: flex; align-items: center; gap: 7px; margin-bottom: 10px; color: rgb(var(--color-theme-text-secondary)); font-size: 10px; letter-spacing: .13em; }
.discovery-eyebrow > span { width: 5px; height: 5px; flex-shrink: 0; border-radius: 50%; background: #65a98a; box-shadow: 0 0 0 3px #65a98a13; }
.discovery-copy h3 { margin: 0; color: rgb(var(--color-theme-text)); font-size: 20px; font-weight: 650; line-height: 1.5; letter-spacing: .025em; overflow-wrap: anywhere; }
.discovery-copy p { margin-top: 10px; color: rgb(var(--color-theme-text-secondary)); font-size: 13px; line-height: 1.85; text-wrap: pretty; }
.discovery-action { display: inline-flex; align-items: center; justify-content: center; gap: 8px; min-height: 42px; padding: 8px 16px; margin-top: 18px; border: 1px solid rgb(var(--color-theme-border)); border-radius: 24px; color: rgb(var(--color-theme-text)); background: rgb(var(--color-theme-bg) / .85); font-size: 12px; transition: border-color 180ms, background 180ms, transform 180ms; touch-action: manipulation; }
.discovery-action:hover { border-color: rgb(var(--color-theme-accent) / .5); background: rgb(var(--color-theme-accent) / .06); transform: translateY(-1px); }
.discovery-action:focus-visible { outline: 2px solid rgb(var(--color-theme-accent)); outline-offset: 3px; }
.discovery-film { transform-origin: 76px 72px; animation: discovery-drift 6s ease-in-out infinite; }
.discovery-state--loading .discovery-film { animation: discovery-film-signal 3.6s ease-in-out infinite; }
.discovery-explorer-body { transform-origin: 270px 170px; animation: discovery-bob 5s ease-in-out infinite; }
.discovery-eyes { transform-origin: 267px 131px; animation: discovery-blink 7s ease-in-out infinite; }
.discovery-moon { animation: discovery-drift 8s -3s ease-in-out infinite; }
.discovery-thought { animation: discovery-drift 5s -1s ease-in-out infinite; }
.discovery-stars { animation: discovery-shimmer 6s ease-in-out infinite; }
.discovery-scan { transform-origin: 250px 122px; animation: discovery-scan 2.8s ease-in-out infinite; }
.discovery-loading-track { display: flex; align-items: center; gap: 5px; margin-top: 19px; }
.discovery-loading-track i { width: 25px; height: 3px; border-radius: 3px; background: #79b395; animation: discovery-shimmer 1.6s ease-in-out infinite; }
.discovery-loading-track i:nth-child(2) { animation-delay: 200ms; }
.discovery-loading-track i:nth-child(3) { animation-delay: 400ms; }
.discovery-state.is-compact { min-height: 214px; padding: 17px 24px; gap: 22px; border-radius: 20px; }
.is-compact .discovery-art { flex-basis: 244px; width: 244px; }
.is-compact .discovery-copy h3 { font-size: 17px; }
.is-compact .discovery-eyebrow { margin-bottom: 6px; font-size: 9px; }
.is-compact .discovery-copy p { margin-top: 6px; font-size: 12px; }
.discovery-state.is-paused * { animation-play-state: paused !important; }
@keyframes discovery-drift { 0%, 100% { transform: translateY(0) rotate(-2deg); } 50% { transform: translateY(-6px) rotate(2deg); } }
@keyframes discovery-bob { 0%, 100% { transform: translateY(0) rotate(2deg); } 50% { transform: translateY(-5px) rotate(-2deg); } }
@keyframes discovery-blink { 0%, 42%, 46%, 100% { transform: scaleY(1); } 44% { transform: scaleY(.12); } }
@keyframes discovery-shimmer { 0%, 100% { opacity: .35; } 50% { opacity: .9; } }
@keyframes discovery-film-signal { 0%, 100% { transform: translate(0, 0) rotate(-4deg); opacity: .85; } 50% { transform: translate(10px, 6px) rotate(6deg); opacity: 1; } }
@keyframes discovery-scan { 0%, 100% { opacity: .2; transform: scaleY(.65); } 50% { opacity: .9; transform: scaleY(1); } }
@media (max-width: 639px) {
  .discovery-state, .discovery-state.is-compact { flex-direction: column; min-height: 0; gap: 2px; padding: 16px 18px 26px; text-align: center; }
  .discovery-art, .is-compact .discovery-art { flex-basis: auto; width: 250px; max-width: 100%; }
  .is-compact .discovery-art { width: 215px; }
  .discovery-copy { max-width: 300px; }
  .discovery-copy h3 { font-size: 18px; }
  .discovery-copy p { font-size: 12px; }
  .discovery-eyebrow, .discovery-loading-track { justify-content: center; }
}
@media (prefers-reduced-motion: reduce) { .discovery-state *, .discovery-state *::before, .discovery-state *::after { animation: none !important; transition: none !important; } }
</style>

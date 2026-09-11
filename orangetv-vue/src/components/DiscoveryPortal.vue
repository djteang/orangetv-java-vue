<script setup lang="ts">
import { useId } from 'vue'

withDefaults(defineProps<{
  mode?: 'loading' | 'empty' | 'error'
  paused?: boolean
}>(), { mode: 'empty', paused: false })

const id = useId().replace(/:/g, '')
</script>

<template>
  <g fill="none">
    <defs>
      <radialGradient :id="`${id}-portal`"><stop stop-color="#e3ff9c" /><stop offset=".5" stop-color="#8fdc6e" /><stop offset="1" stop-color="#309e78" /></radialGradient>
      <clipPath :id="`${id}-clip`"><ellipse cx="152" cy="107" rx="54" ry="67" /></clipPath>
    </defs>
    <g class="discovery-portal" :class="[`discovery-portal--${mode}`, { 'is-paused': paused }]">
      <path d="M142 24c11-7 19 2 27 0 10-2 13 12 22 14s8 16 14 22 0 19 6 27-2 18 1 27-8 16-7 26-12 14-14 23-14 10-18 17-16 5-23 9-17-5-25-4-12-13-20-16-7-17-14-23 0-18-5-27 4-17 1-26 9-14 9-24 13-12 16-21 16-7 21-15Z" fill="#c7ef8a" stroke="#365d48" stroke-width="3" stroke-linejoin="round" />
      <ellipse cx="152" cy="107" rx="57" ry="71" :fill="`url(#${id}-portal)`" stroke="#4c9a64" stroke-width="2.5" />
      <g :clip-path="`url(#${id}-clip)`">
        <g class="discovery-vortex" stroke-linecap="round">
          <path d="M152 44c40-4 68 62 37 91-28 26-62 7-58-19 4-25 35-24 39-7 3 13-16 22-20 11" stroke="#edffb0" stroke-width="6" />
          <path d="M105 80c-20 36 12 93 48 85s46-44 25-57c-14-9-31-1-27 12" stroke="#dcff9d" stroke-width="4" />
          <path d="M191 65c-26-18-66-3-68 28m32 60c-27-1-40-24-34-43M142 63l7-2m36 83 6-7" stroke="#67bc71" stroke-width="4" />
          <ellipse cx="153" cy="114" rx="9" ry="12" fill="#f1ffc2" stroke="none" />
        </g>
      </g>
      <g stroke="#f0ffc0" stroke-width="3" stroke-linecap="round"><path d="m122 41-6 7m65-1 6 9m13 30 1 9m-16 62-6 5m-62-10-5-7M96 97l1-8" /></g>
    </g>
  </g>
</template>

<style scoped>
.discovery-portal { transform-origin: 152px 110px; animation: discovery-breathe 6s ease-in-out infinite; }
.discovery-vortex { transform-origin: 152px 107px; animation: discovery-orbit 24s linear infinite; }
.discovery-portal--loading .discovery-vortex { animation-duration: 7s; }
.discovery-portal--error { opacity: .68; }
.discovery-portal--error .discovery-vortex { animation-duration: 40s; }
.discovery-portal.is-paused, .discovery-portal.is-paused .discovery-vortex { animation-play-state: paused; }
@keyframes discovery-orbit { to { transform: rotate(360deg); } }
@keyframes discovery-breathe { 0%, 100% { transform: rotate(-6deg) scale(.97, 1); } 50% { transform: rotate(-2deg) scale(1, .98); } }
@media (prefers-reduced-motion: reduce) { .discovery-portal, .discovery-vortex { animation: none !important; } }
</style>

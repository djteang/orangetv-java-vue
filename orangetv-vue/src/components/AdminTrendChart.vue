<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import type { AdminTrendPoint } from '@/types/admin'

const props = defineProps<{
  title: string
  description: string
  summaryLabel: string
  unit: string
  points: AdminTrendPoint[]
  kind: 'line' | 'bar'
}>()
const id = useId()
const selected = ref<number | null>(null)
const points = computed(() => props.points.map(point => ({
  ...point, count: Number.isFinite(point.count) ? Math.max(0, point.count) : 0,
})))
const total = computed(() => points.value.reduce((sum, point) => sum + point.count, 0))
const peak = computed(() => points.value.reduce<AdminTrendPoint | null>((best, point) =>
  !best || point.count > best.count ? point : best, null))
const step = computed(() => {
  const rough = (peak.value?.count || 1) / 4
  const magnitude = 10 ** Math.floor(Math.log10(rough))
  return Math.max(1, ([1, 2, 5, 10].find(value => value * magnitude >= rough) || 10) * magnitude)
})
const ceiling = computed(() => step.value * 4)
const ticks = computed(() => Array.from({ length: 5 }, (_, index) => ceiling.value - step.value * index))
const coordinates = computed(() => points.value.map((point, index) => ({
  ...point, x: (index + .5) * 600 / points.value.length, y: 180 - point.count / ceiling.value * 180,
})))
const line = computed(() => coordinates.value.map((point, index) => `${index ? 'L' : 'M'} ${point.x} ${point.y}`).join(' '))
const area = computed(() => coordinates.value.length
  ? `${line.value} L ${coordinates.value[coordinates.value.length - 1].x} 180 L ${coordinates.value[0].x} 180 Z`
  : '')
const activeIndex = computed(() => selected.value ?? Math.max(0, points.value.length - 1))
const activePoint = computed(() => coordinates.value[activeIndex.value])
const barWidth = computed(() => Math.min(42, 600 / Math.max(1, points.value.length) * .48))
const average = computed(() => points.value.length ? (total.value / points.value.length).toLocaleString('zh-CN', { maximumFractionDigits: 1 }) : '0')
const number = (value: number) => value.toLocaleString('zh-CN')
watch(() => props.points, () => { selected.value = null })
</script>

<template>
  <section class="admin-trend" :class="`admin-trend--${kind}`" :aria-labelledby="`${id}-title`">
    <header class="trend-heading">
      <div><h3 :id="`${id}-title`">{{ title }}</h3><p>{{ description }}</p></div>
      <span class="trend-period">最近 7 天</span>
    </header>
    <div class="trend-summary">
      <div><strong>{{ number(total) }}</strong><span>{{ unit }}<small>{{ summaryLabel }}</small></span></div>
      <div v-if="activePoint" class="trend-readout"><span>{{ activePoint.date }}</span><b>{{ number(activePoint.count) }} {{ unit }}</b></div>
    </div>
    <div v-if="points.length" class="trend-chart" @mouseleave="selected = null">
      <div class="trend-y-axis" aria-hidden="true"><span v-for="tick in ticks" :key="tick">{{ number(tick) }}</span></div>
      <div class="trend-plot">
        <svg viewBox="0 0 600 180" preserveAspectRatio="none" aria-hidden="true" focusable="false">
          <defs><linearGradient :id="`${id}-area`" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="currentColor" stop-opacity=".24" /><stop offset="100%" stop-color="currentColor" stop-opacity=".015" /></linearGradient></defs>
          <line v-for="(_, index) in ticks" :key="index" x1="0" x2="600" :y1="index * 45" :y2="index * 45" class="trend-grid" vector-effect="non-scaling-stroke" />
          <template v-if="kind === 'line'">
            <path :d="area" :fill="`url(#${id}-area)`" />
            <path :d="line" class="trend-line" vector-effect="non-scaling-stroke" />
            <circle v-for="point in coordinates" :key="point.date" :cx="point.x" :cy="point.y" r="3.5" class="trend-dot" vector-effect="non-scaling-stroke" />
          </template>
          <template v-else>
            <rect v-for="(point, index) in coordinates" :key="point.date" :x="point.x - barWidth / 2" :y="point.y" :width="barWidth" :height="180 - point.y" rx="5" fill="currentColor" :opacity="index === activeIndex ? 1 : .5" />
          </template>
          <line v-if="activePoint" :x1="activePoint.x" :x2="activePoint.x" y1="0" y2="180" class="trend-guide" vector-effect="non-scaling-stroke" />
        </svg>
        <button v-for="(point, index) in points" :key="point.date" type="button" class="trend-hit"
          :style="{ left: `${index / points.length * 100}%`, width: `${100 / points.length}%` }"
          :aria-label="`${point.date}，${title} ${point.count} ${unit}`"
          @pointerenter="selected = index" @focus="selected = index" @click="selected = index"></button>
        <span v-if="total === 0" class="trend-zero">这 7 天暂无{{ kind === 'line' ? '新增用户' : '搜索记录' }}</span>
      </div>
      <div class="trend-dates" :style="{ gridTemplateColumns: `repeat(${points.length}, minmax(0, 1fr))` }" aria-hidden="true"><span v-for="point in points" :key="point.date">{{ point.date }}</span></div>
    </div>
    <div v-else class="trend-empty" role="status">暂无趋势数据</div>
    <footer class="trend-footer"><span>日均 <b>{{ average }}</b> {{ unit }}</span><span v-if="peak">最高 <b>{{ number(peak.count) }}</b> {{ unit }}<span v-if="peak.count"> · {{ peak.date }}</span></span></footer>
  </section>
</template>

<style scoped>
.admin-trend { --chart-color: var(--color-theme-accent); min-width: 0; padding: 24px; border: 1px solid rgb(var(--color-theme-border) / .8); border-radius: 20px; background: rgb(var(--color-theme-surface) / .9); }
.admin-trend--bar { --chart-color: var(--color-primary-600); }
:global(.dark) .admin-trend--bar { --chart-color: var(--color-primary-400); }
.trend-heading { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
.trend-heading h3 { color: rgb(var(--color-theme-text)); font-size: 15px; font-weight: 650; }
.trend-heading p { margin-top: 5px; color: rgb(var(--color-theme-text-secondary)); font-size: 11px; line-height: 1.6; }
.trend-period { flex-shrink: 0; padding: 5px 9px; border: 1px solid rgb(var(--color-theme-border) / .8); border-radius: 7px; color: rgb(var(--color-theme-text-secondary)); font-size: 10px; }
.trend-summary { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: 23px 0 24px; }
.trend-summary > div:first-child { display: flex; align-items: center; gap: 9px; }
.trend-summary strong { font-size: 30px; font-weight: 650; letter-spacing: -.04em; color: rgb(var(--color-theme-text)); font-variant-numeric: tabular-nums; }
.trend-summary > div > span { color: rgb(var(--color-theme-text-secondary)); font-size: 11px; }
.trend-summary small { display: block; margin-top: 2px; font-size: 10px; }
.trend-readout { display: flex; flex-direction: column; align-items: flex-end; gap: 3px; padding-left: 12px; border-left: 2px solid rgb(var(--chart-color) / .5); }
.trend-readout b { color: rgb(var(--chart-color)); font-size: 13px; font-weight: 600; font-variant-numeric: tabular-nums; }
.trend-chart { display: grid; grid-template-columns: 34px minmax(0, 1fr); gap: 12px 8px; }
.trend-y-axis { display: flex; height: 180px; flex-direction: column; justify-content: space-between; align-items: flex-end; color: rgb(var(--color-theme-text-secondary)); font-size: 10px; line-height: 1; font-variant-numeric: tabular-nums; }
.trend-plot { position: relative; height: 180px; min-width: 0; }
.trend-plot svg { display: block; width: 100%; height: 100%; overflow: visible; color: rgb(var(--chart-color)); }
.trend-grid { stroke: rgb(var(--color-theme-border)); stroke-width: 1; stroke-dasharray: 3 5; }
.trend-line { fill: none; stroke: currentColor; stroke-width: 2.5; stroke-linejoin: round; stroke-linecap: round; }
.trend-dot { fill: rgb(var(--color-theme-surface)); stroke: currentColor; stroke-width: 2; }
.trend-guide { stroke: currentColor; stroke-width: 1; stroke-dasharray: 3 4; opacity: .25; }
.trend-hit { position: absolute; top: -6px; bottom: -6px; border-radius: 6px; cursor: crosshair; }
.trend-hit:focus-visible { outline: 2px solid rgb(var(--chart-color)); outline-offset: 2px; background: rgb(var(--chart-color) / .04); }
.trend-zero { position: absolute; top: 40%; left: 50%; transform: translate(-50%, -50%); white-space: nowrap; padding: 6px 10px; border-radius: 8px; background: rgb(var(--color-theme-surface) / .9); color: rgb(var(--color-theme-text-secondary)); font-size: 12px; pointer-events: none; }
.trend-dates { grid-column: 2; display: grid; color: rgb(var(--color-theme-text-secondary)); font-size: 10px; text-align: center; font-variant-numeric: tabular-nums; }
.trend-footer { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8px; margin-top: 20px; padding-top: 15px; border-top: 1px solid rgb(var(--color-theme-border) / .65); color: rgb(var(--color-theme-text-secondary)); font-size: 11px; }
.trend-footer b { font-weight: 500; color: rgb(var(--color-theme-text)); }
.trend-empty { display: grid; min-height: 207px; place-items: center; color: rgb(var(--color-theme-text-secondary)); font-size: 13px; }
@media (max-width: 639px) { .admin-trend { padding: 18px 14px; } .trend-chart { grid-template-columns: 28px minmax(0, 1fr); gap: 12px 4px; } .trend-period { padding: 4px 6px; } }
</style>

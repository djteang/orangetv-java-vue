<script setup lang="ts">
import { computed, ref } from 'vue'
import { Activity, ArrowUpRight, Film, Radio, RefreshCw, Search, Users, Video } from 'lucide-vue-next'
import type { AdminStats } from '@/types/admin'
import AdminTrendChart from './AdminTrendChart.vue'
import AdminUserHistoryDialog from './AdminUserHistoryDialog.vue'

const props = defineProps<{ stats: AdminStats | null; loading: boolean; error: string; updatedAt: string }>()
defineEmits<{ refresh: [] }>()
const historyType = ref<'search' | 'play' | null>(null)
const number = (value: number | undefined) => value === undefined ? '—' : value.toLocaleString('zh-CN')
const metrics = computed(() => [
  { label: '总用户数', value: props.stats?.totalUsers, icon: Users, detail: '已注册账户', note: props.stats ? `今日新增 ${number(props.stats.todayNewUsers)} 人` : '今日新增', featured: true },
  { label: '活跃用户', value: props.stats?.activeUsers, icon: Activity, detail: '近 7 天登录过的用户', note: '最近 7 天', featured: false },
  { label: '观影记录', value: props.stats?.totalPlayRecords, icon: Film, detail: '点击查看所有用户的观影记录', note: props.stats ? `今日更新 ${number(props.stats.todayPlayRecords)} 条` : '今日更新', featured: false, historyType: 'play' as const },
  { label: '搜索记录', value: props.stats?.totalSearches, icon: Search, detail: '点击查看所有用户的搜索记录', note: props.stats ? `今日更新 ${number(props.stats.todaySearches)} 条` : '今日更新', featured: false, historyType: 'search' as const },
  { label: '视频源数量', value: props.stats?.totalVideoSources, icon: Video, detail: '已配置的视频数据源', note: '资源概况', featured: false },
  { label: '直播源数量', value: props.stats?.totalLiveSources, icon: Radio, detail: '已配置的直播数据源', note: '资源概况', featured: false },
])
</script>

<template>
  <section class="admin-overview" aria-label="管理面板数据概览" :aria-busy="loading">
    <header class="overview-heading">
      <div><span class="overview-eyebrow">站点运行概况</span><h2>数据概览</h2></div>
      <div class="overview-tools">
        <span v-if="updatedAt">更新于 {{ updatedAt }}</span>
        <button type="button" :disabled="loading" @click="$emit('refresh')"><RefreshCw :size="14" :class="{ 'animate-spin': loading }" aria-hidden="true" />{{ loading ? '更新中' : '刷新数据' }}</button>
      </div>
    </header>
    <p v-if="error" class="overview-error" role="alert">{{ error }}</p>
    <div class="overview-metrics">
      <article v-for="metric in metrics" :key="metric.label" class="metric-card" :class="{ 'metric-card--featured': metric.featured, 'metric-card--interactive': metric.historyType }">
        <div class="metric-label"><span class="metric-icon"><component :is="metric.icon" :size="18" aria-hidden="true" /></span><h3>{{ metric.label }}</h3></div>
        <strong class="metric-value">{{ number(metric.value) }}</strong>
        <div class="metric-bottom"><span>{{ metric.detail }}</span><ArrowUpRight v-if="metric.featured || metric.historyType" :size="15" aria-hidden="true" /></div>
        <span class="metric-note">{{ metric.note }}</span>
        <button v-if="metric.historyType" type="button" class="metric-action" :aria-label="'查看所有用户的' + metric.label" aria-haspopup="dialog" @click="historyType = metric.historyType"></button>
      </article>
    </div>
    <div v-if="stats" class="overview-trends">
      <AdminTrendChart title="用户增长趋势" description="每天新加入的用户" summary-label="7 日新增用户" unit="人" :points="stats.userTrend" kind="line" />
      <AdminTrendChart title="搜索趋势" description="每天更新的搜索记录" summary-label="7 日搜索记录" unit="条" :points="stats.searchTrend" kind="bar" />
    </div>
    <div v-else class="overview-placeholder" role="status">{{ loading ? '正在读取站点趋势…' : '趋势数据暂不可用，请刷新重试。' }}</div>
    <AdminUserHistoryDialog v-if="historyType" all-users :initial-tab="historyType" @close="historyType = null" />
  </section>
</template>

<style scoped>
.admin-overview { margin-bottom: 32px; }
.overview-heading { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 14px; margin-bottom: 18px; }
.overview-eyebrow { color: rgb(var(--color-theme-text-secondary)); font-size: 11px; }
.overview-heading h2 { margin-top: 3px; color: rgb(var(--color-theme-text)); font-size: 22px; font-weight: 650; letter-spacing: -.025em; }
.overview-tools { display: flex; align-items: center; gap: 12px; color: rgb(var(--color-theme-text-secondary)); font-size: 11px; }
.overview-tools button { display: inline-flex; min-height: 36px; align-items: center; justify-content: center; gap: 7px; padding: 7px 12px; border: 1px solid rgb(var(--color-theme-border)); border-radius: 9px; background: rgb(var(--color-theme-surface)); color: rgb(var(--color-theme-text)); font-size: 12px; }
.overview-tools button:hover { border-color: rgb(var(--color-theme-accent) / .55); }
.overview-tools button:focus-visible { outline: 2px solid rgb(var(--color-theme-accent)); outline-offset: 3px; }
.overview-tools button:disabled { opacity: .6; cursor: wait; }
.overview-metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.metric-card { position: relative; min-width: 0; padding: 21px 20px 18px; border: 1px solid rgb(var(--color-theme-border) / .8); border-radius: 18px; background: rgb(var(--color-theme-surface) / .9); }
.metric-card--featured { border-color: rgb(var(--color-theme-accent) / .3); background: linear-gradient(125deg, rgb(var(--color-theme-accent) / .13), rgb(var(--color-theme-accent) / .025)), rgb(var(--color-theme-surface)); }
.metric-card--interactive:hover, .metric-card--interactive:focus-within { border-color: rgb(var(--color-theme-accent) / .6); background: rgb(var(--color-theme-accent) / .06); }
.metric-action { position: absolute; inset: 0; width: 100%; height: 100%; border-radius: inherit; cursor: pointer; }
.metric-action:focus-visible { outline: 2px solid rgb(var(--color-theme-accent)); outline-offset: 3px; }
.metric-label { display: flex; align-items: center; gap: 9px; }
.metric-label h3 { color: rgb(var(--color-theme-text-secondary)); font-size: 12px; font-weight: 500; }
.metric-icon { display: grid; width: 33px; height: 33px; flex-shrink: 0; place-items: center; border: 1px solid rgb(var(--color-theme-accent) / .1); border-radius: 10px; color: rgb(var(--color-theme-accent)); background: rgb(var(--color-theme-accent) / .07); }
.metric-value { display: block; margin: 16px 0 7px; color: rgb(var(--color-theme-text)); font-size: clamp(24px, 2.7vw, 36px); font-weight: 650; font-variant-numeric: tabular-nums; letter-spacing: -.045em; line-height: 1.15; overflow-wrap: anywhere; }
.metric-bottom { display: flex; align-items: center; justify-content: space-between; gap: 5px; min-height: 18px; color: rgb(var(--color-theme-text-secondary)); font-size: 10px; line-height: 1.6; }
.metric-bottom svg { color: rgb(var(--color-theme-accent)); }
.metric-note { display: inline-block; margin-top: 16px; padding: 4px 8px; border-radius: 6px; background: rgb(var(--color-theme-accent) / .07); color: rgb(var(--color-theme-accent)); font-size: 10px; font-weight: 500; }
.overview-trends { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; margin-top: 20px; }
.overview-error { margin-bottom: 14px; padding: 12px 15px; border: 1px solid rgb(var(--color-theme-error) / .2); border-radius: 12px; color: rgb(var(--color-theme-error)); background: rgb(var(--color-theme-error) / .05); font-size: 12px; }
.overview-placeholder { display: grid; min-height: 240px; place-items: center; margin-top: 20px; border: 1px dashed rgb(var(--color-theme-border)); border-radius: 20px; color: rgb(var(--color-theme-text-secondary)); font-size: 13px; }
@media (min-width: 1536px) { .overview-metrics { grid-template-columns: repeat(6, minmax(0, 1fr)); } }
@media (max-width: 1100px) { .overview-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 900px) { .overview-trends { grid-template-columns: minmax(0, 1fr); } }
@media (max-width: 639px) { .overview-metrics { gap: 10px; } .metric-card { padding: 16px 13px; border-radius: 14px; } .metric-label { gap: 6px; } .metric-icon { width: 28px; height: 28px; border-radius: 8px; } .metric-value { font-size: 29px; } .overview-tools > span { display: none; } }
@media (prefers-reduced-motion: reduce) { .overview-tools svg { animation: none; } }
</style>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import { ChevronLeft, ChevronRight, Film, History, Loader2, Search, X } from 'lucide-vue-next'
import type { User } from '@/types'
import type { AdminHistoryPage, AdminPlayHistoryEntry, AdminSearchHistoryEntry } from '@/types/admin'
import { getUserPlayHistory, getUserSearchHistory } from '@/api/admin'

const props = defineProps<{ user: User }>()
const emit = defineEmits<{ close: [] }>()
const id = useId()
const dialogRef = ref<HTMLDialogElement | null>(null)
const contentRef = ref<HTMLElement | null>(null)
const tabsRef = ref<HTMLElement | null>(null)
const activeTab = ref<'search' | 'play'>('search')
const page = ref(0)
const pageSize = 20
const searchEntries = ref<AdminSearchHistoryEntry[]>([])
const playEntries = ref<AdminPlayHistoryEntry[]>([])
const total = ref(0)
const totalPages = ref(0)
const loading = ref(true)
const error = ref('')
const failedCovers = ref(new Set<number>())
const tabLabel = computed(() => activeTab.value === 'search' ? '搜索记录' : '观影记录')
const rangeStart = computed(() => total.value ? page.value * pageSize + 1 : 0)
const rangeEnd = computed(() => Math.min((page.value + 1) * pageSize, total.value))
let controller: AbortController | undefined
let requestId = 0
let previousOverflow: string | null = null
let trigger: HTMLElement | null = null

function applyPage(data: AdminHistoryPage<unknown>) {
  total.value = data.totalElements
  totalPages.value = data.totalPages
}

async function fetchHistory() {
  const currentRequest = ++requestId
  controller?.abort()
  const nextController = new AbortController()
  controller = nextController
  const username = props.user.username
  const requestedPage = page.value
  loading.value = true
  error.value = ''
  searchEntries.value = []
  playEntries.value = []
  failedCovers.value = new Set()
  total.value = 0
  totalPages.value = 0
  if (contentRef.value) contentRef.value.scrollTop = 0

  try {
    if (activeTab.value === 'search') {
      const data = await getUserSearchHistory(username, requestedPage, pageSize, nextController.signal)
      if (currentRequest !== requestId || nextController.signal.aborted) return
      searchEntries.value = data.items
      applyPage(data)
    } else {
      const data = await getUserPlayHistory(username, requestedPage, pageSize, nextController.signal)
      if (currentRequest !== requestId || nextController.signal.aborted) return
      playEntries.value = data.items
      applyPage(data)
    }
    // 记录在翻页期间被用户清理时，退回最后一个有效页。
    if (requestedPage > 0 && requestedPage >= totalPages.value) page.value = Math.max(0, totalPages.value - 1)
  } catch {
    if (currentRequest !== requestId || nextController.signal.aborted) return
    error.value = '记录加载失败，请稍后重试。'
  } finally {
    if (currentRequest === requestId && !nextController.signal.aborted) loading.value = false
  }
}

function selectTab(tab: 'search' | 'play') {
  if (activeTab.value === tab) return
  activeTab.value = tab
  page.value = 0
}

async function handleTabKey(event: KeyboardEvent) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const tab = event.key === 'Home' ? 'search' : event.key === 'End' ? 'play' : activeTab.value === 'search' ? 'play' : 'search'
  selectTab(tab)
  await nextTick()
  tabsRef.value?.querySelector<HTMLButtonElement>('[aria-selected="true"]')?.focus()
}

function formatDate(value: string | null) {
  if (!value) return '时间未知'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '时间未知' : date.toLocaleString('zh-CN', { hour12: false })
}

function formatDuration(value: number | null) {
  if (value === null || !Number.isFinite(value) || value < 0) return '--:--'
  const seconds = Math.floor(value)
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor(seconds % 3600 / 60)
  return (hours ? String(hours) + ':' + String(minutes).padStart(2, '0') : String(minutes)) + ':' + String(seconds % 60).padStart(2, '0')
}

function progressPercent(record: AdminPlayHistoryEntry) {
  if (!record.duration || !Number.isFinite(record.duration) || record.duration <= 0 ||
      record.progress === null || !Number.isFinite(record.progress)) return null
  return Math.min(100, Math.max(0, Math.round(record.progress / record.duration * 100)))
}

function episodeLabel(record: AdminPlayHistoryEntry) {
  const name = record.episodeName?.trim() || (record.episodeIndex !== null && record.episodeIndex >= 0 ? '第 ' + (record.episodeIndex + 1) + ' 集' : '集数未记录')
  return name + (record.totalEpisodes && record.totalEpisodes > 1 ? ' / 共 ' + record.totalEpisodes + ' 集' : '')
}

function closeOnBackdrop(event: MouseEvent) {
  if (!dialogRef.value || event.target !== dialogRef.value) return
  const rect = dialogRef.value.getBoundingClientRect()
  if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) emit('close')
}

watch(() => props.user.username, () => { page.value = 0 }, { flush: 'sync' })
watch([() => props.user.username, activeTab, page], () => { void fetchHistory() }, { immediate: true })

onMounted(() => {
  trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
  previousOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  dialogRef.value?.showModal()
})

onBeforeUnmount(() => {
  ++requestId
  controller?.abort()
  dialogRef.value?.close()
  if (previousOverflow !== null) document.body.style.overflow = previousOverflow
  if (trigger?.isConnected) trigger.focus({ preventScroll: true })
})
</script>

<template>
  <Teleport to="body">
    <dialog ref="dialogRef" class="user-history-dialog" :aria-labelledby="id + '-title'" :aria-describedby="id + '-user'" aria-modal="true" @cancel.prevent="emit('close')" @click="closeOnBackdrop">
      <header class="history-heading">
        <span class="history-heading-icon"><History :size="22" aria-hidden="true" /></span>
        <div class="history-heading-text"><h2 :id="id + '-title'">用户记录</h2><p :id="id + '-user'">{{ user.username }}</p></div>
        <button type="button" class="history-close" aria-label="关闭用户记录" autofocus @click="emit('close')"><X :size="20" aria-hidden="true" /></button>
      </header>

      <div ref="tabsRef" class="history-tabs" role="tablist" aria-label="记录类型" @keydown="handleTabKey">
        <button :id="id + '-search-tab'" type="button" role="tab" :aria-selected="activeTab === 'search'" :aria-controls="id + '-panel'" :tabindex="activeTab === 'search' ? 0 : -1" @click="selectTab('search')"><Search :size="16" aria-hidden="true" />搜索记录</button>
        <button :id="id + '-play-tab'" type="button" role="tab" :aria-selected="activeTab === 'play'" :aria-controls="id + '-panel'" :tabindex="activeTab === 'play' ? 0 : -1" @click="selectTab('play')"><Film :size="16" aria-hidden="true" />观影记录</button>
      </div>

      <div ref="contentRef" :id="id + '-panel'" class="history-content" role="tabpanel" :aria-labelledby="id + '-' + activeTab + '-tab'" :aria-busy="loading" tabindex="0">
        <div v-if="loading" class="history-state" role="status"><Loader2 :size="28" class="animate-spin" aria-hidden="true" /><p>正在加载{{ tabLabel }}…</p></div>
        <div v-else-if="error" class="history-state" role="alert"><History :size="30" aria-hidden="true" /><p>{{ error }}</p><button type="button" class="history-retry" @click="fetchHistory">重新加载</button></div>
        <div v-else-if="!total" class="history-state" role="status"><component :is="activeTab === 'search' ? Search : Film" :size="32" aria-hidden="true" /><h3>暂无{{ tabLabel }}</h3><p>该用户还没有保存的{{ tabLabel }}。</p></div>

        <template v-else-if="activeTab === 'search'">
          <p class="history-description">按最近搜索时间排序，同一关键词合并显示。</p>
          <ul class="search-history-list" aria-label="搜索记录">
            <li v-for="record in searchEntries" :key="record.id" class="search-history-item">
              <span class="history-record-icon"><Search :size="16" aria-hidden="true" /></span>
              <div class="search-record-body"><h3>{{ record.keyword }}</h3><p>最近搜索 <time :datetime="record.updatedAt || undefined">{{ formatDate(record.updatedAt) }}</time></p></div>
              <span class="search-count">{{ record.searchCount?.toLocaleString('zh-CN') ?? '—' }} 次</span>
            </li>
          </ul>
        </template>

        <template v-else>
          <p class="history-description">按最近观看时间排序，展示各影片保存的最新进度。</p>
          <ul class="play-history-list" aria-label="观影记录">
            <li v-for="record in playEntries" :key="record.id" class="play-history-item">
              <div class="play-cover">
                <img v-if="record.cover && !failedCovers.has(record.id)" :src="record.cover" alt="" loading="lazy" referrerpolicy="no-referrer" @error="failedCovers.add(record.id)" />
                <Film v-else :size="24" aria-hidden="true" />
              </div>
              <div class="play-record-body">
                <div class="play-record-heading"><h3>{{ record.title || '未命名影片' }}</h3><span v-if="record.year" class="play-year">{{ record.year }}</span></div>
                <p class="play-source">{{ record.sourceName || '来源未记录' }}<span> · </span>{{ episodeLabel(record) }}</p>
                <div class="play-progress-label"><span>{{ formatDuration(record.progress) }} / {{ formatDuration(record.duration) }}</span><span>{{ progressPercent(record) === null ? '进度未知' : '已看 ' + progressPercent(record) + '%' }}</span></div>
                <div v-if="progressPercent(record) !== null" class="play-progress" role="progressbar" :aria-label="(record.title || '影片') + '观看进度'" :aria-valuenow="progressPercent(record) ?? undefined" :aria-valuemin="0" :aria-valuemax="100"><span :style="{ width: progressPercent(record) + '%' }"></span></div>
                <p class="play-date">最近观看 <time :datetime="record.updatedAt || undefined">{{ formatDate(record.updatedAt) }}</time></p>
              </div>
            </li>
          </ul>
        </template>
      </div>

      <footer class="history-footer">
        <p aria-live="polite">{{ loading ? '正在读取记录…' : error ? '暂未获取到记录' : '第 ' + rangeStart + '–' + rangeEnd + ' 条，共 ' + total.toLocaleString('zh-CN') + ' 条' }}</p>
        <nav aria-label="用户记录分页">
          <button type="button" aria-label="上一页记录" :disabled="loading || !!error || page === 0" @click="page--"><ChevronLeft :size="18" aria-hidden="true" /></button>
          <span>{{ loading || error || !totalPages ? '—' : (page + 1) + ' / ' + totalPages }}</span>
          <button type="button" aria-label="下一页记录" :disabled="loading || !!error || page + 1 >= totalPages" @click="page++"><ChevronRight :size="18" aria-hidden="true" /></button>
        </nav>
      </footer>
    </dialog>
  </Teleport>
</template>

<style scoped>
.user-history-dialog { width: calc(100% - 32px); max-width: 760px; max-height: min(820px, calc(100dvh - 48px)); margin: auto; padding: 0; overflow: hidden; border: 1px solid rgb(var(--color-theme-border)); border-radius: 22px; background: rgb(var(--color-theme-surface)); color: rgb(var(--color-theme-text)); box-shadow: 0 24px 90px rgb(0 0 0 / .22); }
.user-history-dialog[open] { display: flex; flex-direction: column; }
.user-history-dialog::backdrop { background: rgb(0 0 0 / .45); backdrop-filter: blur(5px); }
.history-heading { display: flex; flex-shrink: 0; align-items: center; gap: 13px; padding: 24px 26px 20px; }
.history-heading-icon { display: grid; width: 45px; height: 45px; flex-shrink: 0; place-items: center; border-radius: 14px; background: rgb(var(--color-theme-accent) / .1); color: rgb(var(--color-theme-accent)); }
.history-heading-text { flex: 1; min-width: 0; }
.history-heading h2 { font-size: 19px; font-weight: 650; }
.history-heading p { margin-top: 3px; font-size: 12px; color: rgb(var(--color-theme-text-secondary)); overflow-wrap: anywhere; }
.history-close { display: grid; width: 40px; height: 40px; flex-shrink: 0; place-items: center; border-radius: 10px; color: rgb(var(--color-theme-text-secondary)); }
.history-close:hover { background: rgb(var(--color-theme-border) / .5); color: rgb(var(--color-theme-text)); }
.history-tabs { display: flex; flex-shrink: 0; gap: 6px; margin: 0 26px; padding: 5px; border: 1px solid rgb(var(--color-theme-border) / .7); border-radius: 12px; background: rgb(var(--color-theme-bg)); }
.history-tabs button { display: flex; flex: 1; min-height: 40px; align-items: center; justify-content: center; gap: 8px; padding: 8px 12px; border-radius: 8px; color: rgb(var(--color-theme-text-secondary)); font-size: 13px; }
.history-tabs button[aria-selected="true"] { background: rgb(var(--color-theme-accent) / .1); color: rgb(var(--color-theme-accent)); font-weight: 600; }
.user-history-dialog button:focus-visible, .history-content:focus-visible { outline: 2px solid rgb(var(--color-theme-accent)); outline-offset: -2px; }
.history-content { flex: 1; min-height: 0; overflow-y: auto; overscroll-behavior: contain; padding: 20px 26px 24px; }
.history-description { margin-bottom: 15px; color: rgb(var(--color-theme-text-secondary)); font-size: 11px; line-height: 1.7; }
.history-state { display: flex; min-height: 280px; flex-direction: column; align-items: center; justify-content: center; gap: 13px; padding: 24px; text-align: center; color: rgb(var(--color-theme-text-secondary)); font-size: 13px; }
.history-state > svg { color: rgb(var(--color-theme-accent) / .7); }
.history-state h3 { font-size: 15px; font-weight: 600; color: rgb(var(--color-theme-text)); }
.history-retry { min-height: 40px; padding: 8px 18px; border: 1px solid rgb(var(--color-theme-accent) / .3); border-radius: 9px; color: rgb(var(--color-theme-accent)); }
.search-history-list, .play-history-list { display: grid; gap: 10px; }
.search-history-item { display: flex; align-items: center; gap: 12px; min-width: 0; padding: 15px; border: 1px solid rgb(var(--color-theme-border) / .75); border-radius: 12px; }
.history-record-icon { display: grid; width: 34px; height: 34px; flex-shrink: 0; place-items: center; border-radius: 10px; background: rgb(var(--color-theme-accent) / .06); color: rgb(var(--color-theme-accent)); }
.search-record-body { flex: 1; min-width: 0; }
.search-record-body h3 { overflow-wrap: anywhere; font-size: 14px; font-weight: 550; }
.search-record-body p { margin-top: 6px; color: rgb(var(--color-theme-text-secondary)); font-size: 11px; line-height: 1.7; }
.search-count { flex-shrink: 0; border-radius: 6px; padding: 4px 8px; background: rgb(var(--color-theme-accent) / .08); color: rgb(var(--color-theme-accent)); font-size: 11px; font-variant-numeric: tabular-nums; }
.play-history-item { display: flex; align-items: flex-start; gap: 16px; min-width: 0; padding: 16px; border: 1px solid rgb(var(--color-theme-border) / .75); border-radius: 14px; }
.play-cover { display: grid; width: 70px; height: 100px; flex-shrink: 0; place-items: center; overflow: hidden; border-radius: 9px; background: rgb(var(--color-theme-accent) / .07); color: rgb(var(--color-theme-accent) / .55); }
.play-cover img { width: 100%; height: 100%; object-fit: cover; }
.play-record-body { flex: 1; min-width: 0; }
.play-record-heading { display: flex; flex-wrap: wrap; align-items: baseline; gap: 5px 10px; }
.play-record-heading h3 { font-size: 14px; font-weight: 600; overflow-wrap: anywhere; }
.play-year { font-size: 11px; color: rgb(var(--color-theme-text-secondary)); }
.play-source { margin-top: 5px; color: rgb(var(--color-theme-text-secondary)); font-size: 11px; line-height: 1.7; overflow-wrap: anywhere; }
.play-progress-label { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 5px; margin-top: 14px; color: rgb(var(--color-theme-text-secondary)); font-size: 10px; font-variant-numeric: tabular-nums; }
.play-progress { height: 4px; margin-top: 6px; overflow: hidden; border-radius: 4px; background: rgb(var(--color-theme-border)); }
.play-progress span { display: block; height: 100%; border-radius: inherit; background: rgb(var(--color-theme-accent)); }
.play-date { margin-top: 10px; color: rgb(var(--color-theme-text-secondary)); font-size: 10px; line-height: 1.6; }
.history-footer { display: flex; flex-shrink: 0; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px; padding: 15px 26px; border-top: 1px solid rgb(var(--color-theme-border)); background: rgb(var(--color-theme-bg) / .6); }
.history-footer p { color: rgb(var(--color-theme-text-secondary)); font-size: 11px; }
.history-footer nav { display: flex; align-items: center; gap: 6px; }
.history-footer nav span { min-width: 58px; text-align: center; font-size: 12px; font-variant-numeric: tabular-nums; }
.history-footer nav button { display: grid; width: 36px; height: 36px; place-items: center; border: 1px solid rgb(var(--color-theme-border)); border-radius: 9px; background: rgb(var(--color-theme-surface)); }
.history-footer nav button:hover:not(:disabled) { border-color: rgb(var(--color-theme-accent)); color: rgb(var(--color-theme-accent)); }
.history-footer nav button:disabled { cursor: not-allowed; opacity: .35; }
@media (max-width: 639px) {
  .user-history-dialog { width: calc(100% - 20px); max-height: calc(100dvh - 24px); border-radius: 17px; }
  .history-heading { gap: 10px; padding: 18px 16px 16px; }
  .history-tabs { margin: 0 16px; }
  .history-content { padding: 16px; }
  .history-footer { padding: 12px 16px; }
  .search-history-item { padding: 12px; gap: 9px; }
  .history-record-icon { display: none; }
  .play-history-item { padding: 12px; gap: 11px; }
  .play-cover { width: 52px; height: 76px; }
}
@media (prefers-reduced-motion: reduce) { .history-state svg { animation: none; } }
</style>

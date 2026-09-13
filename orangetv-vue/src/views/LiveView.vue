<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, useId, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Clock3, ListVideo, Loader2, Radio, RefreshCw, Search, Tv, X } from 'lucide-vue-next'
import PageLayout from '@/components/PageLayout.vue'
import LivePlayer from '@/components/LivePlayer.vue'
import ThemeSelect from '@/components/ThemeSelect.vue'
import { getLiveChannels, getLiveEpg, getLiveSources } from '@/api/live'
import type { LiveChannel, LiveSchedule, LiveSource } from '@/types/live'
import { formatDateTime } from '@/utils/datetime'
import {
  LIVE_PLAYBACK_MODE_EVENT, liveProgrammesToday, liveProxyUrl, parseLiveSchedule,
  readLiveDirectConnect, resolveLiveUrl, writeLiveDirectConnect,
} from '@/utils/live'

const route = useRoute()
const router = useRouter()
const panelId = 'live-panel-' + useId()
const apiBase = import.meta.env.VITE_API_BASE_URL || '/api'
const sources = ref<LiveSource[]>([])
const selectedSourceKey = ref('')
const currentSource = computed(() => sources.value.find(source => source.key === selectedSourceKey.value) ?? null)
const channels = ref<LiveChannel[]>([])
const currentChannel = ref<LiveChannel | null>(null)
const sourcesLoading = ref(true)
const channelsLoading = ref(false)
const sourcesError = ref('')
const channelsError = ref('')
const activeTab = ref<'channels' | 'sources'>('channels')
const selectedGroup = ref('')
const search = ref('')
const collapsed = ref(false)
const directConnect = ref(readLiveDirectConnect())
const failedLogos = ref(new Set<string>())
const channelListRef = ref<HTMLElement | null>(null)
const livePlayerRef = ref<InstanceType<typeof LivePlayer> | null>(null)
const epgLoading = ref(false)
const epgError = ref('')
const schedule = shallowRef<LiveSchedule>(new Map())
const now = ref(Date.now())
let clockTimer: ReturnType<typeof setInterval> | undefined
let sourceController: AbortController | undefined
let channelController: AbortController | undefined
let epgController: AbortController | undefined
let sourceRequest = 0
let channelRequest = 0
let epgRequest = 0

const groups = computed(() => Array.from(new Set(channels.value.map(channel => channel.group || '其他'))))
const groupOptions = computed(() => [{ value: '', label: '全部频道' }, ...groups.value.map(group => ({ value: group, label: group }))])
const playbackOptions = [{ value: 'proxy', label: '代理播放' }, { value: 'direct', label: '浏览器直连' }] as const
const filteredChannels = computed(() => {
  const keyword = search.value.trim().toLocaleLowerCase()
  return channels.value.filter(channel =>
    (!selectedGroup.value || channel.group === selectedGroup.value) &&
    (!keyword || channel.name.toLocaleLowerCase().includes(keyword) || channel.group?.toLocaleLowerCase().includes(keyword)),
  )
})
const todayProgrammes = computed(() => {
  const channel = currentChannel.value
  const programmes = channel ? schedule.value.get(channel.tvgId?.trim() || '') ?? schedule.value.get(channel.name.trim()) ?? [] : []
  return liveProgrammesToday(programmes, now.value)
})
const currentProgramme = computed(() => todayProgrammes.value.find(programme => programme.start <= now.value && programme.end > now.value))
const queryString = (value: unknown) => typeof value === 'string' ? value : ''

function updateLocation(channel: LiveChannel) {
  if (route.query.source === selectedSourceKey.value && route.query.id === channel.id) return
  void router.replace({ query: { ...route.query, source: selectedSourceKey.value, id: channel.id } })
}

function chooseChannel(channel: LiveChannel) {
  if (currentChannel.value?.id === channel.id) {
    livePlayerRef.value?.reconnect()
    return
  }
  currentChannel.value = channel
  updateLocation(channel)
}

function applyChannelLocation() {
  const channel = channels.value.find(item => item.id === queryString(route.query.id)) ?? channels.value[0]
  if (!channel) return
  if (currentChannel.value?.id !== channel.id) currentChannel.value = channel
  updateLocation(channel)
}

async function loadEpg(source: LiveSource) {
  const request = ++epgRequest
  epgController?.abort()
  const controller = new AbortController()
  epgController = controller
  schedule.value = new Map()
  epgError.value = ''
  epgLoading.value = false
  if (!source.epg?.trim()) return
  epgLoading.value = true
  try {
    const response = await getLiveEpg(source.key, controller.signal)
    if (request !== epgRequest || controller.signal.aborted) return
    if (response.error) throw new Error(response.error)
    schedule.value = response.epg?.trim() ? parseLiveSchedule(response.epg) : new Map()
  } catch {
    if (request === epgRequest && !controller.signal.aborted) epgError.value = '节目单加载失败，不影响观看直播。'
  } finally {
    if (request === epgRequest && !controller.signal.aborted) epgLoading.value = false
  }
}

async function loadChannels(source: LiveSource) {
  const request = ++channelRequest
  channelController?.abort()
  const controller = new AbortController()
  channelController = controller
  selectedSourceKey.value = source.key
  channels.value = []
  currentChannel.value = null
  channelsError.value = ''
  channelsLoading.value = true
  selectedGroup.value = ''
  search.value = ''
  failedLogos.value = new Set()
  void loadEpg(source)
  try {
    const response = await getLiveChannels(source.key, controller.signal)
    if (request !== channelRequest || controller.signal.aborted) return
    if (response.error || !Array.isArray(response.channels)) throw new Error(response.error || '频道加载失败')
    channels.value = response.channels
      .filter(channel => typeof channel.id === 'string' && typeof channel.name === 'string' && typeof channel.url === 'string' && channel.url.trim())
      .map(channel => ({ ...channel, group: channel.group?.trim() || '其他' }))
    source.channelCount = channels.value.length
    if (!source.epg && response.epgUrl) {
      source.epg = response.epgUrl
      void loadEpg(source)
    }
    applyChannelLocation()
  } catch (reason) {
    if (request === channelRequest && !controller.signal.aborted) channelsError.value = reason instanceof Error && reason.message
      ? reason.message : '频道加载失败，请重试或选择其他直播源。'
  } finally {
    if (request === channelRequest && !controller.signal.aborted) channelsLoading.value = false
  }
}

function applyLocation() {
  if (sourcesLoading.value || !sources.value.length) return
  const source = sources.value.find(item => item.key === queryString(route.query.source)) ?? sources.value[0]
  if (source.key !== selectedSourceKey.value) {
    void loadChannels(source)
  } else if (!channelsLoading.value) {
    applyChannelLocation()
  }
}

async function loadSources() {
  const request = ++sourceRequest
  sourceController?.abort()
  channelController?.abort()
  epgController?.abort()
  ++channelRequest
  ++epgRequest
  const controller = new AbortController()
  sourceController = controller
  sourcesLoading.value = true
  sourcesError.value = ''
  channelsError.value = ''
  sources.value = []
  channels.value = []
  currentChannel.value = null
  selectedSourceKey.value = ''
  channelsLoading.value = false
  epgLoading.value = false
  schedule.value = new Map()
  try {
    const response = await getLiveSources(controller.signal)
    if (request !== sourceRequest || controller.signal.aborted) return
    if (!Array.isArray(response)) throw new Error('直播源加载失败')
    sources.value = response
    sourcesLoading.value = false
    applyLocation()
  } catch {
    if (request === sourceRequest && !controller.signal.aborted) sourcesError.value = '直播源加载失败，请稍后重试。'
  } finally {
    if (request === sourceRequest && !controller.signal.aborted) sourcesLoading.value = false
  }
}

function chooseSource(source: LiveSource) {
  if (source.key === selectedSourceKey.value) {
    if (channelsError.value || !channels.value.length) void loadChannels(source)
    activeTab.value = 'channels'
    return
  }
  void router.push({ query: { ...route.query, source: source.key, id: undefined } })
  activeTab.value = 'channels'
}

function channelLogo(channel: LiveChannel) {
  if (!channel.logo || failedLogos.value.has(channel.id) || !currentSource.value) return ''
  const url = resolveLiveUrl(channel.logo, currentSource.value.url)
  try { return liveProxyUrl(url, 'logo', apiBase) } catch { return '' }
}

function setPlaybackMode(value: boolean) {
  directConnect.value = value
  writeLiveDirectConnect(value)
}

function syncPlaybackMode(event: Event) {
  directConnect.value = event instanceof CustomEvent && typeof event.detail === 'boolean' ? event.detail : readLiveDirectConnect()
}

function chooseNextChannel() {
  if (channelsLoading.value || !channels.value.length) return
  if (!filteredChannels.value.length) {
    selectedGroup.value = ''
    search.value = ''
  }
  const list = filteredChannels.value
  const index = list.findIndex(channel => channel.id === currentChannel.value?.id)
  const nextChannel = list[(index + 1) % list.length]
  if (nextChannel) chooseChannel(nextChannel)
  activeTab.value = 'channels'
}

function programmeTime(value: number) {
  return formatDateTime(value, { hour: '2-digit', minute: '2-digit' })
}

watch(() => [route.query.source, route.query.id], applyLocation)
watch([filteredChannels, currentChannel, activeTab, collapsed], async () => {
  await nextTick()
  const list = channelListRef.value
  const selected = list && Array.from(list.querySelectorAll<HTMLElement>('[data-channel-id]'))
    .find(element => element.dataset.channelId === currentChannel.value?.id)
  if (!list || !selected) return
  const listRect = list.getBoundingClientRect()
  const selectedRect = selected.getBoundingClientRect()
  if (selectedRect.top < listRect.top) list.scrollTop -= listRect.top - selectedRect.top
  else if (selectedRect.bottom > listRect.bottom) list.scrollTop += selectedRect.bottom - listRect.bottom
})

onMounted(() => {
  void loadSources()
  clockTimer = setInterval(() => { now.value = Date.now() }, 30000)
  window.addEventListener(LIVE_PLAYBACK_MODE_EVENT, syncPlaybackMode)
  window.addEventListener('storage', syncPlaybackMode)
})
onBeforeUnmount(() => {
  ++sourceRequest
  ++channelRequest
  ++epgRequest
  sourceController?.abort()
  channelController?.abort()
  epgController?.abort()
  clearInterval(clockTimer)
  window.removeEventListener(LIVE_PLAYBACK_MODE_EVENT, syncPlaybackMode)
  window.removeEventListener('storage', syncPlaybackMode)
})
</script>

<template>
  <PageLayout>
    <div class="live-page px-4 sm:px-10 py-4 sm:py-8 overflow-visible">
      <header class="live-heading mb-6 sm:mb-8">
        <h1 class="text-2xl sm:text-3xl font-bold text-gray-800 mb-1 sm:mb-2 dark:text-gray-200">电视直播</h1>
        <p class="text-sm sm:text-base text-gray-600 dark:text-gray-400">选择频道，即刻观看。</p>
      </header>

      <section v-if="sourcesLoading || sourcesError || !sources.length" class="live-empty" :role="sourcesError ? 'alert' : 'status'">
        <Loader2 v-if="sourcesLoading" :size="32" class="animate-spin" aria-hidden="true" /><Tv v-else :size="38" aria-hidden="true" />
        <h2>{{ sourcesLoading ? '正在加载直播源…' : sourcesError ? '暂时无法获取直播源' : '暂无可用直播源' }}</h2>
        <p>{{ sourcesLoading ? '频道即将准备就绪。' : sourcesError || '请先由管理员在站点配置中添加并启用直播源。' }}</p>
        <button v-if="sourcesError" type="button" class="live-button" @click="loadSources">重新加载直播源</button>
      </section>

      <template v-else>
        <div class="hidden lg:flex justify-end mb-2">
          <button
            type="button"
            class="live-collapse group relative flex items-center space-x-1.5 px-3 py-1.5 rounded-full bg-white/80 hover:bg-white dark:bg-gray-800/80 dark:hover:bg-gray-800 backdrop-blur-sm border border-gray-200/50 dark:border-gray-700/50 shadow-sm hover:shadow-md transition-all duration-200"
            :title="collapsed ? '显示频道列表' : '隐藏频道列表'"
            :aria-label="collapsed ? '显示频道列表' : '隐藏频道列表'"
            :aria-expanded="!collapsed"
            :aria-controls="panelId"
            @click="collapsed = !collapsed"
          >
            <svg
              :class="['w-3.5 h-3.5 text-gray-500 dark:text-gray-400 transition-transform duration-200', collapsed ? 'rotate-180' : 'rotate-0']"
              fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true"
            >
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
            </svg>
            <span class="text-xs font-medium text-gray-600 dark:text-gray-300">{{ collapsed ? '显示' : '隐藏' }}</span>
            <span
              :class="['absolute -top-0.5 -right-0.5 w-2 h-2 rounded-full transition-all duration-200', collapsed ? 'bg-orange-400 animate-pulse' : 'bg-blue-400']"
              aria-hidden="true"
            ></span>
          </button>
        </div>
        <div class="live-workspace" :class="{ 'live-workspace--collapsed': collapsed }">
          <div class="live-screen-column">
            <LivePlayer v-if="currentChannel && currentSource" ref="livePlayerRef"
              :key="currentSource.key + ':' + currentChannel.id" :channel="currentChannel" :source="currentSource" :direct-connect="directConnect"
              @mode-change="setPlaybackMode" @choose-channel="chooseNextChannel" />
            <div v-else class="live-screen-placeholder" :role="channelsError ? 'alert' : 'status'">
              <Loader2 v-if="channelsLoading" :size="30" class="animate-spin" aria-hidden="true" /><Tv v-else :size="34" aria-hidden="true" />
              <h2>{{ channelsLoading ? '正在加载频道…' : channelsError ? '暂时无法获取频道' : '此直播源暂无频道' }}</h2>
              <p>{{ channelsError || (channelsLoading ? currentSource?.name : '可切换其他直播源后继续观看。') }}</p>
              <button v-if="channelsError && currentSource" type="button" class="live-button" @click="loadChannels(currentSource)">重新加载频道</button>
            </div>

            <section class="live-now" aria-label="当前直播">
              <div class="live-now-title"><span class="live-tag"><span></span>直播</span><h2>{{ currentChannel?.name || currentSource?.name }}</h2></div>
              <p class="live-now-meta">{{ currentSource?.name }}<template v-if="currentChannel"> · {{ currentChannel.group }}</template></p>
              <p v-if="currentProgramme" class="live-now-programme"><Clock3 :size="14" aria-hidden="true" />正在播出：{{ currentProgramme.title }}</p>
              <div class="live-now-actions">
                <div class="live-mode"><span>播放方式</span><ThemeSelect :model-value="directConnect ? 'direct' : 'proxy'" :options="playbackOptions" label="直播播放方式" @update:model-value="setPlaybackMode($event === 'direct')" /></div>
              </div>
              <p class="live-mode-hint">{{ directConnect ? '由浏览器直接连接直播源，无法播放时可切换代理播放。' : '通过站点转发直播，连接失败时可重试或切换频道。' }}</p>
            </section>
          </div>

          <aside :id="panelId" class="live-channel-panel" :class="{ 'live-channel-panel--collapsed': collapsed }" aria-label="直播频道与直播源">
            <div class="live-panel-tabs" aria-label="直播列表">
              <button type="button" :aria-pressed="activeTab === 'channels'" @click="activeTab = 'channels'"><ListVideo :size="16" aria-hidden="true" />频道<span>{{ channels.length }}</span></button>
              <button type="button" :aria-pressed="activeTab === 'sources'" @click="activeTab = 'sources'"><Radio :size="16" aria-hidden="true" />直播源<span>{{ sources.length }}</span></button>
            </div>
            <template v-if="activeTab === 'channels'">
              <div class="live-channel-tools">
                <label class="live-search"><Search :size="15" aria-hidden="true" /><input v-model="search" type="search" aria-label="搜索直播频道" placeholder="搜索频道名称或分组" /><button v-if="search" type="button" aria-label="清空频道搜索" @click="search = ''"><X :size="14" aria-hidden="true" /></button></label>
                <div class="live-group-label"><span>频道分组</span><ThemeSelect v-model="selectedGroup" :options="groupOptions" label="直播频道分组" /></div>
                <p>{{ filteredChannels.length }} 个频道<span v-if="currentSource"> · {{ currentSource.name }}</span></p>
              </div>
              <div ref="channelListRef" class="live-channel-list" :aria-busy="channelsLoading">
                <div v-if="channelsLoading" class="live-list-state" role="status"><Loader2 :size="22" class="animate-spin" aria-hidden="true" />正在加载频道…</div>
                <div v-else-if="channelsError" class="live-list-state" role="alert"><p>{{ channelsError }}</p><button type="button" class="live-button" @click="activeTab = 'sources'">选择其他直播源</button></div>
                <div v-else-if="!filteredChannels.length" class="live-list-state" role="status"><Tv :size="27" aria-hidden="true" /><p>{{ channels.length ? '没有匹配的频道' : '暂无频道' }}</p><button v-if="channels.length" type="button" class="live-button" @click="search = ''; selectedGroup = ''">显示全部频道</button><button v-else type="button" class="live-button" @click="activeTab = 'sources'">选择其他直播源</button></div>
                <button v-for="channel in filteredChannels" v-else :key="channel.id" type="button" class="live-channel" :data-channel-id="channel.id" :aria-pressed="currentChannel?.id === channel.id" @click="chooseChannel(channel)">
                  <span class="live-channel-logo"><img v-if="channelLogo(channel)" :src="channelLogo(channel)" alt="" loading="lazy" @error="failedLogos.add(channel.id)" /><Tv v-else :size="20" aria-hidden="true" /></span>
                  <span class="live-channel-copy"><strong>{{ channel.name }}</strong><span>{{ channel.group }}</span></span>
                  <span v-if="currentChannel?.id === channel.id" class="live-channel-current" aria-label="当前频道"><i></i><i></i><i></i></span>
                </button>
              </div>
            </template>
            <div v-else class="live-source-list">
              <p class="live-source-hint">切换直播源，发现更多频道。</p>
              <button v-for="source in sources" :key="source.key" type="button" class="live-source" :aria-pressed="selectedSourceKey === source.key" @click="chooseSource(source)">
                <Radio :size="20" aria-hidden="true" /><span><strong>{{ source.name }}</strong><small>{{ source.channelCount == null ? '点击加载频道' : source.channelCount + ' 个频道' }}</small></span><span v-if="selectedSourceKey === source.key" class="live-source-selected">当前</span>
              </button>
            </div>
          </aside>
        </div>

        <section v-if="currentChannel" class="live-epg" aria-label="今日节目单">
          <header><div><h2><Clock3 :size="17" aria-hidden="true" />今日节目单</h2><p>{{ currentChannel.name }} · 北京时间</p></div><button v-if="currentSource?.epg" type="button" class="live-button" :disabled="epgLoading" @click="loadEpg(currentSource)"><RefreshCw :size="14" :class="{ 'animate-spin': epgLoading }" aria-hidden="true" />刷新节目单</button></header>
          <div v-if="epgLoading" class="live-epg-state" role="status"><Loader2 :size="18" class="animate-spin" aria-hidden="true" />正在加载节目单…</div>
          <p v-else-if="epgError" class="live-epg-state" role="status">{{ epgError }}</p>
          <p v-else-if="!todayProgrammes.length" class="live-epg-state">{{ currentSource?.epg ? '暂无该频道的今日节目单。' : '该直播源暂未提供节目单。' }}</p>
          <ol v-else class="live-programme-list">
            <li v-for="programme in todayProgrammes" :key="programme.start + ':' + programme.end + ':' + programme.title" class="live-programme" :class="{ 'live-programme--current': programme.start <= now && programme.end > now, 'live-programme--past': programme.end <= now }" :aria-current="programme.start <= now && programme.end > now ? 'true' : undefined">
              <div><time :datetime="new Date(programme.start).toISOString()">{{ programmeTime(programme.start) }}</time><span>–</span><time :datetime="new Date(programme.end).toISOString()">{{ programmeTime(programme.end) }}</time><span v-if="programme.start <= now && programme.end > now" class="live-programme-badge">正在播出</span></div>
              <h3>{{ programme.title }}</h3>
            </li>
          </ol>
        </section>
      </template>
    </div>
  </PageLayout>
</template>

<style scoped>
.live-page { color: rgb(var(--color-theme-text)); }
.live-button { display: inline-flex; min-height: 38px; align-items: center; justify-content: center; gap: 7px; padding: 8px 12px; border: 1px solid rgb(var(--color-theme-border)); border-radius: 9px; background: rgb(var(--color-theme-surface)); color: rgb(var(--color-theme-text)); font-size: 12px; }
.live-button:hover:not(:disabled) { border-color: rgb(var(--color-theme-accent) / .6); color: rgb(var(--color-theme-accent)); }
.live-button:disabled { cursor: not-allowed; opacity: .5; }
.live-page button:focus-visible, .live-page input:focus-visible { outline: 2px solid rgb(var(--color-theme-accent)); outline-offset: 3px; }
.live-empty { display: flex; min-height: 380px; flex-direction: column; align-items: center; justify-content: center; gap: 16px; padding: 32px; border: 1px solid rgb(var(--color-theme-border)); border-radius: 18px; background: rgb(var(--color-theme-surface) / .8); text-align: center; }
.live-empty > svg { color: rgb(var(--color-theme-accent)); }
.live-empty h2 { font-size: 18px; font-weight: 600; }
.live-empty p { color: rgb(var(--color-theme-text-secondary)); font-size: 13px; line-height: 1.8; }
.live-workspace { display: grid; grid-template-columns: minmax(0, 1fr) 310px; align-items: start; gap: 20px; }
.live-workspace--collapsed { grid-template-columns: minmax(0, 1fr); }
.live-screen-column { min-width: 0; }
.live-screen-placeholder { display: flex; aspect-ratio: 16 / 9; min-height: 240px; flex-direction: column; align-items: center; justify-content: center; gap: 14px; padding: 28px; border-radius: 18px; background: #080f1c; color: #e2e8f0; text-align: center; }
.live-screen-placeholder h2 { font-size: 16px; font-weight: 600; }
.live-screen-placeholder p { max-width: 380px; color: #a4b2c8; font-size: 12px; line-height: 1.8; }
.live-now { padding: 20px 2px 4px; }
.live-now-title { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
.live-now-title h2 { min-width: 0; font-size: 19px; font-weight: 600; overflow-wrap: anywhere; }
.live-tag { display: inline-flex; align-items: center; flex-shrink: 0; gap: 5px; padding: 4px 8px; border: 1px solid rgb(var(--color-theme-accent) / .2); border-radius: 6px; color: rgb(var(--color-theme-accent)); background: rgb(var(--color-theme-accent) / .07); font-size: 10px; }
.live-tag > span { width: 5px; height: 5px; border-radius: 50%; background: currentColor; }
.live-now-meta { margin-top: 8px; color: rgb(var(--color-theme-text-secondary)); font-size: 12px; overflow-wrap: anywhere; }
.live-now-programme { display: flex; align-items: center; gap: 6px; margin-top: 10px; color: rgb(var(--color-theme-accent)); font-size: 12px; line-height: 1.7; }
.live-now-programme svg { flex-shrink: 0; }
.live-now-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-top: 18px; }
.live-mode { display: flex; align-items: center; gap: 8px; margin-right: auto; font-size: 11px; color: rgb(var(--color-theme-text-secondary)); }
.live-mode > .theme-select { min-width: 135px; }
.live-mode-hint { margin-top: 10px; font-size: 10px; line-height: 1.8; color: rgb(var(--color-theme-text-secondary)); }
.live-channel-panel { display: flex; height: clamp(490px, 68vh, 740px); min-width: 0; flex-direction: column; overflow: hidden; border: 1px solid rgb(var(--color-theme-border)); border-radius: 18px; background: rgb(var(--color-theme-surface) / .95); }
.live-channel-panel--collapsed { display: none; }
.live-panel-tabs { display: flex; flex-shrink: 0; padding: 8px; gap: 6px; border-bottom: 1px solid rgb(var(--color-theme-border) / .6); }
.live-panel-tabs button { display: flex; flex: 1; min-height: 42px; align-items: center; justify-content: center; gap: 7px; border-radius: 9px; font-size: 13px; color: rgb(var(--color-theme-text-secondary)); }
.live-panel-tabs button > span { font-size: 10px; opacity: .7; }
.live-panel-tabs button[aria-pressed="true"] { background: rgb(var(--color-theme-accent) / .09); color: rgb(var(--color-theme-accent)); font-weight: 600; }
.live-channel-tools { flex-shrink: 0; padding: 14px 14px 10px; }
.live-search { display: flex; align-items: center; gap: 8px; padding: 0 10px; border: 1px solid rgb(var(--color-theme-border)); border-radius: 9px; color: rgb(var(--color-theme-text-secondary)); background: rgb(var(--color-theme-bg) / .55); }
.live-search svg { flex-shrink: 0; }
.live-search input { width: 100%; min-width: 0; height: 38px; padding: 0; border: 0; background: transparent; font-size: 12px; box-shadow: none; color: rgb(var(--color-theme-text)); }
.live-search input::-webkit-search-cancel-button { display: none; }
.live-search button { display: grid; width: 24px; height: 28px; flex-shrink: 0; place-items: center; }
.live-group-label { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: 10px; color: rgb(var(--color-theme-text-secondary)); font-size: 11px; }
.live-group-label > .theme-select { width: 68%; }
.live-channel-tools > p { margin-top: 11px; font-size: 10px; color: rgb(var(--color-theme-text-secondary)); overflow-wrap: anywhere; }
.live-channel-list, .live-source-list { flex: 1; min-height: 0; overflow-y: auto; overscroll-behavior: contain; padding: 0 8px 10px; }
.live-channel { display: flex; align-items: center; gap: 10px; width: 100%; min-height: 70px; margin-top: 5px; padding: 12px 10px; border: 1px solid transparent; border-radius: 11px; text-align: left; }
.live-channel:hover, .live-source:hover { background: rgb(var(--color-theme-accent) / .045); }
.live-channel[aria-pressed="true"], .live-source[aria-pressed="true"] { border-color: rgb(var(--color-theme-accent) / .25); background: rgb(var(--color-theme-accent) / .08); }
.live-channel-logo { display: grid; width: 40px; height: 40px; flex-shrink: 0; place-items: center; padding: 5px; overflow: hidden; border-radius: 9px; background: rgb(var(--color-theme-accent) / .07); color: rgb(var(--color-theme-accent)); }
.live-channel-logo img { width: 100%; height: 100%; object-fit: contain; }
.live-channel-copy { flex: 1; min-width: 0; }
.live-channel-copy strong { display: block; font-size: 13px; font-weight: 550; overflow-wrap: anywhere; }
.live-channel-copy > span { display: block; margin-top: 4px; font-size: 10px; color: rgb(var(--color-theme-text-secondary)); }
.live-channel-current { display: flex; align-items: center; gap: 2px; padding: 5px; color: rgb(var(--color-theme-accent)); }
.live-channel-current i { width: 3px; height: 8px; border-radius: 2px; background: currentColor; }
.live-channel-current i:nth-child(2) { height: 16px; }
.live-channel-current i:nth-child(3) { height: 12px; }
.live-list-state { display: flex; min-height: 180px; flex-direction: column; align-items: center; justify-content: center; gap: 12px; padding: 20px; font-size: 12px; line-height: 1.8; color: rgb(var(--color-theme-text-secondary)); text-align: center; }
.live-source-hint { padding: 16px 8px 10px; font-size: 11px; color: rgb(var(--color-theme-text-secondary)); }
.live-source { display: flex; width: 100%; min-height: 78px; align-items: center; gap: 12px; margin-bottom: 7px; padding: 14px 12px; border: 1px solid transparent; border-radius: 11px; text-align: left; }
.live-source > svg { flex-shrink: 0; color: rgb(var(--color-theme-accent)); }
.live-source > span:first-of-type { flex: 1; min-width: 0; }
.live-source strong { display: block; font-size: 13px; font-weight: 550; overflow-wrap: anywhere; }
.live-source small { display: block; margin-top: 5px; color: rgb(var(--color-theme-text-secondary)); font-size: 10px; }
.live-source-selected { color: rgb(var(--color-theme-accent)); font-size: 10px; }
.live-epg { margin-top: 26px; padding: 22px; border: 1px solid rgb(var(--color-theme-border)); border-radius: 18px; background: rgb(var(--color-theme-surface) / .8); }
.live-epg header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.live-epg h2 { display: flex; align-items: center; gap: 8px; font-size: 16px; font-weight: 600; }
.live-epg header p { margin-top: 6px; color: rgb(var(--color-theme-text-secondary)); font-size: 11px; }
.live-epg-state { display: flex; align-items: center; gap: 8px; padding: 22px 0 6px; font-size: 12px; color: rgb(var(--color-theme-text-secondary)); }
.live-programme-list { display: grid; grid-auto-flow: column; grid-auto-columns: minmax(200px, 240px); gap: 12px; overflow-x: auto; overscroll-behavior-x: contain; margin-top: 18px; padding-bottom: 8px; }
.live-programme { padding: 16px; border: 1px solid rgb(var(--color-theme-border)); border-radius: 11px; }
.live-programme > div { display: flex; flex-wrap: wrap; align-items: center; gap: 5px; color: rgb(var(--color-theme-text-secondary)); font-size: 10px; font-variant-numeric: tabular-nums; }
.live-programme h3 { margin-top: 12px; font-size: 13px; line-height: 1.7; overflow-wrap: anywhere; }
.live-programme--current { border-color: rgb(var(--color-theme-accent) / .5); background: rgb(var(--color-theme-accent) / .06); }
.live-programme--past { opacity: .65; }
.live-programme-badge { margin-left: auto; color: rgb(var(--color-theme-accent)); }
@media (max-width: 1023px) { .live-workspace { grid-template-columns: minmax(0, 1fr); } .live-channel-panel { height: 480px; } .live-channel-panel--collapsed { display: flex; } }
@media (max-width: 767px) { .live-workspace { gap: 18px; } .live-now { padding-top: 16px; } .live-now-title h2 { font-size: 17px; } .live-channel-panel { height: min(560px, 72vh); height: min(560px, 72dvh); min-height: 340px; border-radius: 14px; } .live-epg { padding: 16px; border-radius: 14px; } .live-mode { flex: 1; } .live-epg h2 { font-size: 15px; } .live-programme-list { grid-auto-columns: minmax(190px, 220px); } }
@media (prefers-reduced-motion: reduce) { .live-page .animate-spin, .live-page .animate-pulse { animation: none; } }
</style>

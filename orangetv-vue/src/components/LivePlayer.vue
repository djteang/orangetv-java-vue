<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import Artplayer from 'artplayer'
import type Mpegts from 'mpegts.js'
import { resolveLivePlayback } from '@/api/live'
import Hls, { type LoaderCallbacks, type LoaderConfiguration, type LoaderContext } from 'hls.js'
import { CircleAlert, Loader2, Play, RefreshCw, Tv } from 'lucide-vue-next'
import type { LiveChannel, LiveSource } from '@/types/live'
import { liveHeadersForUrl, liveNetworkError, livePlaybackHeaders, liveProxyUrl, liveStreamType, resolveLiveUrl } from '@/utils/live'

const props = defineProps<{ channel: LiveChannel; source: LiveSource; directConnect: boolean }>()
const emit = defineEmits<{ 'mode-change': [value: boolean]; 'choose-channel': [] }>()
const container = ref<HTMLDivElement | null>(null)
const overlayTarget = shallowRef<HTMLElement | null>(null)
const state = ref<'loading' | 'playing' | 'paused' | 'blocked' | 'error' | 'unsupported'>('loading')
const error = ref('')
const apiBase = import.meta.env.VITE_API_BASE_URL || '/api'
let player: Artplayer | null = null
let hls: Hls | null = null
let transmuxer: Mpegts.Player | null = null
let lookup: AbortController | null = null
let events: AbortController | null = null
let timeout: ReturnType<typeof setTimeout> | undefined
let generation = 0
let autoplayAttempted = false

function clearTimeoutGuard() {
  clearTimeout(timeout)
  timeout = undefined
}

function dispose() {
  overlayTarget.value = null
  ++generation
  clearTimeoutGuard()
  lookup?.abort()
  lookup = null
  transmuxer?.destroy()
  transmuxer = null
  events?.abort()
  events = null
  hls?.destroy()
  hls = null
  if (player) {
    const video = player.video
    video.pause()
    video.removeAttribute('src')
    video.load()
    player.destroy()
    player = null
  }
}

function fail(message: string, current: number) {
  if (current !== generation || state.value === 'error') return
  state.value = 'error'
  error.value = message
  clearTimeoutGuard()
  lookup?.abort()
  hls?.stopLoad()
  transmuxer?.unload()
  player?.video.pause()
}

function armTimeout(current: number, delay = 15000) {
  if (timeout) return
  timeout = setTimeout(() => fail('连接直播超时，请重试或手动切换频道。', current), delay)
}

async function requestPlay(video: HTMLVideoElement, current: number) {
  if (current !== generation) return
  state.value = 'loading'
  armTimeout(current)
  try {
    await video.play()
  } catch (reason) {
    if (current !== generation) return
    if (reason instanceof DOMException && reason.name === 'NotAllowedError') {
      state.value = 'blocked'
      clearTimeoutGuard()
    } else if (!(reason instanceof DOMException && reason.name === 'AbortError')) {
      fail('当前频道无法播放，请重试或选择其他频道。', current)
    }
  }
}

function autoplay(video: HTMLVideoElement, current: number) {
  if (autoplayAttempted || current !== generation || state.value === 'error') return
  autoplayAttempted = true
  void requestPlay(video, current)
}

// 使用 Java 的原始分片代理，保留上游 URL 供 HLS.js 解析相对路径。
// 清单、密钥、分片均携带该直播源的 UA；有字节范围时使用支持 Range 的代理。
function proxyLoader(headers: Record<string, string>, originalUrl: string) {
  return class extends Hls.DefaultConfig.loader {
    load(context: LoaderContext, config: LoaderConfiguration, callbacks: LoaderCallbacks<LoaderContext>) {
      const scopedHeaders = liveHeadersForUrl(headers, originalUrl, context.url)
      const proxyContext = { ...context, url: liveProxyUrl(context.url, 'stream', apiBase, undefined, scopedHeaders) }
      super.load(proxyContext, config, {
        ...callbacks,
        onSuccess: (response, stats, _context, details) => {
          const finalUrl = details instanceof XMLHttpRequest ? details.getResponseHeader('X-Upstream-Url') : null
          callbacks.onSuccess({ ...response, url: finalUrl || context.url }, stats, context, details)
        },
        onError: (reason, _context, details, stats) => callbacks.onError(reason, context, details, stats),
        onTimeout: (stats, _context, details) => callbacks.onTimeout(stats, context, details),
        onAbort: (stats, _context, details) => callbacks.onAbort?.(stats, context, details),
        onProgress: callbacks.onProgress
          ? (stats, _context, data, details) => callbacks.onProgress?.(stats, context, data, details)
          : undefined,
      })
    }
  }
}

async function startPlayback() {
  dispose()
  if (!container.value) return
  const current = generation
  autoplayAttempted = false
  error.value = ''
  state.value = 'loading'
  let url = resolveLiveUrl(props.channel.url, props.source.url)
  let type = liveStreamType(url)
  if (type === 'unsupported' || props.channel.unsupportedReason) {
    state.value = 'unsupported'
    error.value = props.channel.unsupportedReason || (/^(rtp|udp):/i.test(url)
      ? '当前频道使用 RTP/UDP 组播，浏览器无法直接播放。请使用已转换为 HTTP/HLS 的直播源。'
      : '当前频道的地址或直播格式不受浏览器支持，请选择其他频道。')
    return
  }
  const direct = props.directConnect
  let headers = livePlaybackHeaders(props.source, props.channel)
  let transport: typeof import('mpegts.js').default | null = null
  let mediaRecoveries = 0
  // 格式探测与后续播放分别计时，让后端有机会返回具体的连接错误。
  armTimeout(current, type === 'auto' && !direct ? 30000 : 15000)

  try {
    if (type === 'auto' && !direct) {
      const controller = new AbortController()
      lookup = controller
      const result = await resolveLivePlayback(url, headers, controller.signal)
      if (current !== generation || controller.signal.aborted) return
      lookup = null
      if (!result.ok || !result.url || !result.type || ['auto', 'unsupported'].includes(result.type)) {
        fail(result.error || '未能识别该频道的直播格式，请更换频道。', current)
        return
      }
      headers = liveHeadersForUrl(headers, url, result.url)
      url = result.url
      type = result.type
      clearTimeoutGuard()
      armTimeout(current)
    } else if (type === 'auto') {
      type = 'hls'
    }
    if (type === 'flv' || type === 'mpegts') {
      transport = (await import('mpegts.js')).default
      if (current !== generation) return
      if (!transport.isSupported()) {
        state.value = 'unsupported'
        error.value = '当前浏览器不支持此直播流，请使用支持 MediaSource 的浏览器，或选择 HLS 频道。'
        clearTimeoutGuard()
        return
      }
    }
    if (current !== generation || ['error', 'unsupported'].includes(state.value)) return
    // mpegts.js 在 Blob Worker 中拉流，必须传入完整地址。
    const targetUrl = new URL(type !== 'hls' && !direct
      ? liveProxyUrl(url, 'stream', apiBase, undefined, headers) : url, window.location.href).href
    const attachTransport = (video: HTMLVideoElement) => {
      if (!transport || current !== generation) return
      transmuxer = transport.createPlayer({ type: type === 'flv' ? 'flv' : 'mpegts', isLive: true, url: targetUrl },
        { enableWorker: true, enableStashBuffer: false, autoCleanupSourceBuffer: true, liveBufferLatencyChasing: true })
      transmuxer.on(transport.Events.ERROR, (kind: string, _details: string, info?: { code?: number }) => {
        fail(kind === 'NetworkError' ? liveNetworkError(direct, info?.code)
          : '浏览器无法解码该频道的音视频，请重试或更换频道。', current)
      })
      transmuxer.attachMediaElement(video)
      transmuxer.load()
    }
    player = new Artplayer({
      container: container.value,
      url: targetUrl,
      type: type === 'hls' ? 'm3u8' : type === 'flv' || type === 'mpegts' ? type : '',
      autoplay: false,
      isLive: true,
      playsInline: true,
      pip: true,
      fullscreen: true,
      fullscreenWeb: true,
      autoOrientation: true,
      mutex: true,
      hotkey: true,
      lock: true,
      volume: 0.7,
      theme: '#38bdf8',
      lang: 'zh-cn',
      moreVideoAttr: { preload: 'auto' },
      customType: {
        flv: attachTransport,
        mpegts: attachTransport,
        m3u8(video, streamUrl) {
          if (current !== generation) return
          if (Hls.isSupported()) {
            hls = new Hls({
              enableWorker: true, lowLatencyMode: true,
              maxBufferLength: 30, backBufferLength: 30,
              manifestLoadingTimeOut: 10000, manifestLoadingMaxRetry: 1,
              levelLoadingTimeOut: 10000, levelLoadingMaxRetry: 1,
              fragLoadingTimeOut: 15000, fragLoadingMaxRetry: 2,
              ...(direct ? {} : { loader: proxyLoader(headers, url) }),
            })
            hls.on(Hls.Events.MANIFEST_PARSED, () => autoplay(video, current))
            hls.on(Hls.Events.ERROR, (_event, data) => {
              if (current !== generation || !data.fatal) return
              if (data.type === Hls.ErrorTypes.MEDIA_ERROR && mediaRecoveries++ === 0) {
                hls?.recoverMediaError()
                armTimeout(current)
              } else {
                fail(data.type === Hls.ErrorTypes.NETWORK_ERROR ? liveNetworkError(direct, data.response?.code)
                  : '浏览器无法解码该频道的音视频，请重试或更换频道。', current)
              }
            })
            hls.loadSource(streamUrl)
            hls.attachMedia(video)
          } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
            video.src = direct ? streamUrl : liveProxyUrl(streamUrl, 'manifest', apiBase, undefined, headers)
          } else {
            state.value = 'unsupported'
            error.value = '当前浏览器不支持 HLS 直播，请使用支持视频播放的浏览器。'
            clearTimeoutGuard()
          }
        },
      },
    })
    overlayTarget.value = player.template.$player
    const video = player.video
    events = new AbortController()
    const options = { signal: events.signal }
    video.addEventListener('canplay', () => autoplay(video, current), options)
    video.addEventListener('playing', () => {
      if (current !== generation) return
      state.value = 'playing'
      error.value = ''
      clearTimeoutGuard()
    }, options)
    video.addEventListener('waiting', () => {
      if (current !== generation || ['error', 'unsupported', 'blocked'].includes(state.value)) return
      state.value = 'loading'
      armTimeout(current)
    }, options)
    video.addEventListener('pause', () => {
      if (current !== generation || video.readyState === 0 || ['error', 'unsupported', 'blocked'].includes(state.value)) return
      state.value = 'paused'
      clearTimeoutGuard()
    }, options)
    video.addEventListener('error', () => fail(direct ? liveNetworkError(true) : '直播播放失败，请重试或切换其他频道。', current), options)
    video.addEventListener('ended', () => fail('当前直播已结束或连接已中断，请重新连接。', current), options)
  } catch {
    if (current !== generation || lookup?.signal.aborted) return
    fail(type === 'auto' ? liveNetworkError(direct) : '播放器初始化失败，请重试。', current)
  }
}

function resume() {
  if (player) void requestPlay(player.video, generation)
}

async function chooseOtherChannel() {
  if (document.fullscreenElement && document.exitFullscreen) await document.exitFullscreen().catch(() => {})
  if (player?.fullscreenWeb) player.fullscreenWeb = false
  emit('choose-channel')
}

onMounted(startPlayback)
watch(() => [props.channel.id, props.channel.url, props.source.key, props.source.ua, props.source.headers, props.channel.headers, props.directConnect], startPlayback, { flush: 'post' })
onBeforeUnmount(dispose)
defineExpose({ reconnect: startPlayback })
</script>

<template>
  <section class="live-player" :data-state="state" :aria-label="channel.name + '直播播放器'">
    <div ref="container" class="live-player-mount"></div>
    <Teleport :to="overlayTarget || 'body'" :disabled="!overlayTarget">
    <div v-if="state === 'loading'" class="live-player-loading" role="status">
      <Loader2 :size="23" class="animate-spin" aria-hidden="true" /><span>正在连接直播…</span>
    </div>
    <div v-if="state === 'blocked'" class="live-player-overlay">
      <button type="button" class="live-start" @click="resume"><Play :size="22" aria-hidden="true" />点击播放直播</button>
      <p>浏览器已暂停自动播放，点击后开始观看。</p>
    </div>
    <div v-if="state === 'error' || state === 'unsupported'" class="live-player-overlay" role="alert">
      <CircleAlert :size="30" aria-hidden="true" />
      <h2>{{ state === 'unsupported' ? '暂不支持此频道' : '直播暂时无法播放' }}</h2>
      <p>{{ error }}</p>
      <div class="live-player-actions">
        <button v-if="state === 'error'" type="button" @click="startPlayback"><RefreshCw :size="15" aria-hidden="true" />重试播放</button>
        <button v-if="state === 'error'" type="button" @click="emit('mode-change', !directConnect)">{{ directConnect ? '切换为代理播放' : '切换为直连播放' }}</button>
        <button type="button" @click="chooseOtherChannel"><Tv :size="15" aria-hidden="true" />更换频道</button>
      </div>
    </div>
    </Teleport>
  </section>
</template>

<style scoped>
.live-player { position: relative; width: 100%; min-height: 240px; aspect-ratio: 16 / 9; overflow: hidden; border: 1px solid rgb(var(--color-theme-border) / .7); border-radius: 18px; background: #050912; color: #fff; }
.live-player-mount { position: absolute; inset: 0; }
.live-player-mount :deep(.art-video-player:not(.art-fullscreen-web)) { z-index: 0; }
.live-player-loading { position: absolute; z-index: 20; top: 16px; left: 16px; display: flex; align-items: center; gap: 9px; max-width: calc(100% - 32px); padding: 10px 14px; border-radius: 10px; background: rgb(5 9 18 / .86); font-size: 12px; pointer-events: none; }
.live-player-overlay { position: absolute; z-index: 150; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; overflow-y: auto; padding: 22px; background: rgb(5 9 18 / .94); text-align: center; }
.live-player-overlay > svg { color: #7dd3fc; flex-shrink: 0; }
.live-player-loading svg, .live-player-overlay svg { fill: none; }
.live-player-overlay h2 { font-size: 16px; font-weight: 600; }
.live-player-overlay p { max-width: 420px; color: #b5c2d5; font-size: 12px; line-height: 1.8; }
.live-player-actions { display: flex; flex-wrap: wrap; align-items: center; justify-content: center; gap: 8px; }
.live-player-actions button, .live-start { display: inline-flex; min-height: 40px; align-items: center; justify-content: center; gap: 7px; padding: 8px 12px; border: 1px solid rgb(125 211 252 / .35); border-radius: 9px; color: #e0f2fe; background: rgb(56 189 248 / .1); font-size: 12px; }
.live-start { min-height: 48px; padding: 12px 22px; font-size: 15px; }
.live-player button:hover { background: rgb(56 189 248 / .22); }
.live-player button:focus-visible { outline: 2px solid #7dd3fc; outline-offset: 3px; }
@media (max-width: 639px) { .live-player { min-height: 230px; border-radius: 13px; } .live-player[data-state="error"], .live-player[data-state="unsupported"] { min-height: 280px; } .live-player-overlay { gap: 9px; padding: 16px; } .live-player-overlay h2 { font-size: 14px; } .live-player-actions { gap: 6px; } .live-player-actions button { font-size: 11px; } }
@media (prefers-reduced-motion: reduce) { .live-player-loading svg { animation: none; } }
</style>

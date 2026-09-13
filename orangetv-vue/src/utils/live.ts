import { APP_TIME_ZONE } from './datetime'
import type { LiveChannel, LiveSource, LiveProgramme, LiveSchedule } from '@/types/live'

export const LIVE_PLAYBACK_MODE_EVENT = 'orangetv:live-playback-mode-change'

export function readLiveDirectConnect(): boolean {
  try { return localStorage.getItem('liveDirectConnect') === 'true' } catch { return false }
}

export function writeLiveDirectConnect(value: boolean) {
  try { localStorage.setItem('liveDirectConnect', String(value)) } catch { /* 仍可在当前页面切换。 */ }
  window.dispatchEvent(new CustomEvent<boolean>(LIVE_PLAYBACK_MODE_EVENT, { detail: value }))
}

export function resolveLiveUrl(value: string, base: string): string {
  if (!value.trim()) return ''
  try { return new URL(value.trim(), base).href } catch { return '' }
}

export type LiveStreamType = 'hls' | 'native' | 'flv' | 'mpegts' | 'auto' | 'unsupported'

export function liveStreamType(url: string): LiveStreamType {
  try {
    const parsed = new URL(url)
    if (!['http:', 'https:'].includes(parsed.protocol)) return 'unsupported'
    if (/\.flv(?:$|\/)/i.test(parsed.pathname)) return 'flv'
    if (/\.(?:ts|m2ts)(?:$|\/)/i.test(parsed.pathname)) return 'mpegts'
    if (/\.(?:mkv|avi)(?:$|\/)/i.test(parsed.pathname)) return 'unsupported'
    if (/\.(?:mp4|m4v|webm|ogv|ogg)$/i.test(parsed.pathname)) return 'native'
    if (/\.m3u8?(?:$|\/)/i.test(parsed.pathname)) return 'hls'
    // PHP 等转发地址需要根据实际响应识别直播格式。
    return 'auto'
  } catch { return 'unsupported' }
}

export function liveProxyUrl(url: string, endpoint: 'm3u8' | 'segment' | 'video' | 'logo' | 'stream' | 'manifest' | 'resolve', apiBase = '/api', userAgent?: string | null, headers?: Record<string, string>): string {
  const upstream = new URL(url)
  if (!['http:', 'https:'].includes(upstream.protocol)) throw new Error('不支持的直播地址')
  // Java 代理兼容 Base64，避免签名地址中的 +、% 被再次 URL 解码。
  const params = new URLSearchParams({ url: btoa(upstream.href) })
  if (userAgent) params.set('ua', userAgent)
  if (headers && Object.keys(headers).length) params.set('headers', encodeLiveHeaders(headers))
  const path = endpoint === 'resolve' ? '/live/resolve'
    : endpoint === 'stream' || endpoint === 'manifest' ? '/live/stream' : '/proxy/' + endpoint
  if (endpoint === 'manifest') params.set('manifest', 'true')
  // 原生视频请求无法设置 Authorization，使用后端已有的 token 参数认证。
  if (endpoint === 'stream' || endpoint === 'manifest') {
    try { const token = localStorage.getItem('token'); if (token) params.set('token', token) } catch { /* 未登录时由后端返回 401。 */ }
  }
  return apiBase.replace(/\/+$/, '') + path + '?' + params.toString()
}

export function encodeLiveHeaders(headers: Record<string, string>): string {
  return btoa(Array.from(new TextEncoder().encode(JSON.stringify(headers)), byte => String.fromCharCode(byte)).join(''))
}

export function livePlaybackHeaders(source: LiveSource, channel: LiveChannel): Record<string, string> {
  return { ...source.headers, ...(source.ua ? { 'User-Agent': source.ua } : {}), ...channel.headers }
}

export function liveHeadersForUrl(headers: Record<string, string>, original: string, target: string): Record<string, string> {
  const result = { ...headers }
  if (new URL(original).origin !== new URL(target).origin) {
    for (const key of Object.keys(result)) if (['cookie', 'authorization'].includes(key.toLowerCase())) delete result[key]
  }
  return result
}

export function liveNetworkError(direct: boolean, status?: number): string {
  if (direct && !status) return '浏览器无法读取该直播源，可能存在跨域限制或网络问题，请切换为代理播放。'
  if (status === 401 || status === 403) return '直播源拒绝访问，请检查授权或频道请求头，或更换频道。'
  if (status === 404) return '直播地址不存在或已失效，请更换频道或更新直播源。'
  if (!direct && (status === 502 || status === 504)) return '服务器无法获取直播内容，请检查源是否有效及服务器的 IPv4/IPv6 网络，或更换频道。'
  return '当前频道连接失败或暂时不可用，请重试或更换频道。'
}

export function parseXmltvTime(value: string | null): number | null {
  const match = value?.trim().match(/^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})?\s*(Z|[+-]\d{4})?$/)
  if (!match) return null
  const [, year, month, day, hour, minute, second = '00', offset = '+0800'] = match
  const wallTime = Date.UTC(+year, +month - 1, +day, +hour, +minute, +second)
  const date = new Date(wallTime)
  if (date.getUTCFullYear() !== +year || date.getUTCMonth() !== +month - 1 || date.getUTCDate() !== +day ||
      date.getUTCHours() !== +hour || date.getUTCMinutes() !== +minute || date.getUTCSeconds() !== +second) return null
  if (offset === 'Z') return wallTime
  const offsetHours = Number(offset.slice(1, 3))
  const offsetMinutes = Number(offset.slice(3))
  if (offsetHours > 23 || offsetMinutes > 59) return null
  return wallTime - (offset[0] === '+' ? 1 : -1) * (offsetHours * 60 + offsetMinutes) * 60000
}

export function parseLiveSchedule(xml: string): LiveSchedule {
  const document = new DOMParser().parseFromString(xml, 'application/xml')
  if (document.querySelector('parsererror') || document.documentElement.localName !== 'tv') {
    throw new Error('节目单格式无效')
  }
  const schedule: LiveSchedule = new Map()
  const seen = new Set<string>()
  for (const entry of document.getElementsByTagName('programme')) {
    const channel = entry.getAttribute('channel')?.trim()
    const title = entry.getElementsByTagName('title')[0]?.textContent?.trim()
    const start = parseXmltvTime(entry.getAttribute('start'))
    const end = parseXmltvTime(entry.getAttribute('stop'))
    if (!channel || !title || start === null || end === null || end <= start) continue
    const key = JSON.stringify([channel, start, end, title])
    if (seen.has(key)) continue
    seen.add(key)
    const programmes = schedule.get(channel) ?? []
    programmes.push({ title, start, end })
    schedule.set(channel, programmes)
  }
  for (const programmes of schedule.values()) programmes.sort((a, b) => a.start - b.start || a.end - b.end)
  // 部分频道列表没有 tvg-id，可按 XMLTV 中的频道显示名匹配。
  for (const channel of document.getElementsByTagName('channel')) {
    const programmes = schedule.get(channel.getAttribute('id')?.trim() ?? '')
    if (!programmes) continue
    for (const displayName of channel.getElementsByTagName('display-name')) {
      const name = displayName.textContent?.trim()
      if (name && !schedule.has(name)) schedule.set(name, programmes)
    }
  }
  return schedule
}

export function liveProgrammesToday(programmes: LiveProgramme[], now = Date.now()): LiveProgramme[] {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: APP_TIME_ZONE, year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(now)
  const part = (type: string) => parts.find(value => value.type === type)?.value
  const start = Date.parse(part('year') + '-' + part('month') + '-' + part('day') + 'T00:00:00+08:00')
  const end = start + 86400000
  return programmes.filter(programme => programme.end > start && programme.start < end)
}

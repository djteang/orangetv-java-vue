import request from './index'
import { encodeLiveHeaders, type LiveStreamType } from '@/utils/live'
import type { LiveChannel, LiveChannelsResponse, LiveEpgResponse, LiveSource } from '@/types/live'

export function resolveLivePlayback(url: string, headers: Record<string, string>, signal?: AbortSignal): Promise<{ ok: boolean; url?: string; type?: LiveStreamType; error?: string; status?: number }> {
  return request.get('/live/resolve', { params: { url: btoa(new URL(url).href), headers: encodeLiveHeaders(headers) }, signal })
}

export function getLiveSources(signal?: AbortSignal): Promise<LiveSource[]> {
  return request.get('/live/sources', { signal })
}

export function getLiveChannels(source: string, signal?: AbortSignal): Promise<LiveChannelsResponse> {
  return request.get('/live/channels', { params: { source }, signal })
}

export function getLiveEpg(source: string, signal?: AbortSignal): Promise<LiveEpgResponse> {
  return request.get('/live/epg', { params: { source }, signal })
}

export function getLiveCategories(signal?: AbortSignal): Promise<string[]> {
  return request.get('/live/categories', { signal })
}

export function getLiveSourcesByCategory(category: string, signal?: AbortSignal): Promise<LiveChannel[]> {
  return request.get('/live/sources/' + encodeURIComponent(category), { signal })
}

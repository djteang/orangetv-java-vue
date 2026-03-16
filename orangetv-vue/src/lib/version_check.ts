import { CURRENT_VERSION } from '@/lib/version'

export enum UpdateStatus {
  HAS_UPDATE = 'has_update',
  NO_UPDATE = 'no_update',
  FETCH_FAILED = 'fetch_failed',
}

const VERSION_CHECK_URLS = [
  'https://raw.githubusercontent.com/djteang/OrangeTV/refs/heads/main/VERSION.txt',
]

export async function checkForUpdates(): Promise<UpdateStatus> {
  try {
    const primaryVersion = await fetchVersionFromUrl(VERSION_CHECK_URLS[0])
    if (primaryVersion) {
      return compareVersions(primaryVersion)
    }
    if (VERSION_CHECK_URLS.length > 1) {
      const backupVersion = await fetchVersionFromUrl(VERSION_CHECK_URLS[1])
      if (backupVersion) {
        return compareVersions(backupVersion)
      }
    }
    return UpdateStatus.FETCH_FAILED
  } catch {
    return UpdateStatus.FETCH_FAILED
  }
}

async function fetchVersionFromUrl(url: string): Promise<string | null> {
  try {
    const controller = new AbortController()
    const timeoutId = setTimeout(() => controller.abort(), 5000)
    const timestamp = Date.now()
    const urlWithTimestamp = url.includes('?') ? `${url}&_t=${timestamp}` : `${url}?_t=${timestamp}`
    const response = await fetch(urlWithTimestamp, {
      method: 'GET',
      signal: controller.signal,
      headers: { 'Content-Type': 'text/plain' },
    })
    clearTimeout(timeoutId)
    if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`)
    const version = await response.text()
    return version.trim()
  } catch {
    return null
  }
}

export function compareVersions(remoteVersion: string): UpdateStatus {
  if (remoteVersion === CURRENT_VERSION) return UpdateStatus.NO_UPDATE
  try {
    const parse = (v: string) =>
      v.split('.').map((p) => {
        const n = parseInt(p, 10)
        if (isNaN(n) || n < 0) throw new Error(`Invalid version: ${v}`)
        return n
      })
    const normalize = (parts: number[]) => {
      const p = parts.slice(0, 3)
      while (p.length < 3) p.push(0)
      return p
    }
    const cur = normalize(parse(CURRENT_VERSION))
    const rem = normalize(parse(remoteVersion))
    for (let i = 0; i < 3; i++) {
      if (rem[i] > cur[i]) return UpdateStatus.HAS_UPDATE
      if (rem[i] < cur[i]) return UpdateStatus.NO_UPDATE
    }
    return UpdateStatus.NO_UPDATE
  } catch {
    return remoteVersion !== CURRENT_VERSION ? UpdateStatus.HAS_UPDATE : UpdateStatus.NO_UPDATE
  }
}

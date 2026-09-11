import type { SiteAnnouncement } from '@/types'

export function createAnnouncement(): SiteAnnouncement {
  return {
    id: globalThis.crypto?.randomUUID?.() || `announcement-${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`,
    content: '',
  }
}

// 新配置按数组顺序显示；未迁移的单条公告作为第一条，显式空数组表示关闭公告。
export function normalizeAnnouncements(value: unknown, legacy?: unknown): SiteAnnouncement[] {
  let entries = value
  if (typeof entries === 'string') {
    try { entries = JSON.parse(entries) } catch { entries = undefined }
  }
  if (!Array.isArray(entries)) {
    entries = typeof legacy === 'string' && legacy.trim()
      ? [{ id: 'legacy-announcement', content: legacy }]
      : []
  }

  const result: SiteAnnouncement[] = []
  const ids = new Set<string>()
  for (const [index, entry] of (entries as unknown[]).entries()) {
    const record = entry && typeof entry === 'object' ? entry as Record<string, unknown> : null
    const content = typeof entry === 'string' ? entry : record?.content
    if (typeof content !== 'string' || !content.trim()) continue

    const baseId = typeof record?.id === 'string' && record.id.trim() ? record.id : `announcement-${index + 1}`
    let id = baseId
    let suffix = 1
    while (ids.has(id)) id = `${baseId}-${suffix++}`
    ids.add(id)
    result.push({ id, content: content.trim() })
  }
  return result
}

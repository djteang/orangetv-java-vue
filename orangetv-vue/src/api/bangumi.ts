import type { BangumiCalendarData, BangumiCalendarItem } from '@/types'

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function optionalText(value: unknown): string | undefined {
  return typeof value === 'string' ? value : undefined
}

function parseCalendar(data: unknown): BangumiCalendarData[] {
  if (!Array.isArray(data)) throw new Error('番剧日历数据格式异常')

  return data.map((day): BangumiCalendarData => {
    if (!isRecord(day) || !isRecord(day.weekday) ||
        typeof day.weekday.en !== 'string' || !Array.isArray(day.items)) {
      throw new Error('番剧日历数据格式异常')
    }

    return {
      weekday: {
        en: day.weekday.en,
        cn: optionalText(day.weekday.cn) || '',
        ja: optionalText(day.weekday.ja) || '',
        id: typeof day.weekday.id === 'number' ? day.weekday.id : 0,
      },
      items: day.items.map((item): BangumiCalendarItem => {
        if (!isRecord(item) || typeof item.id !== 'number' || !Number.isFinite(item.id) ||
            typeof item.name !== 'string') {
          throw new Error('番剧条目数据格式异常')
        }

        const score = isRecord(item.rating) ? item.rating.score : undefined
        return {
          id: item.id,
          name: item.name,
          name_cn: optionalText(item.name_cn),
          air_date: optionalText(item.air_date),
          images: isRecord(item.images)
            ? Object.fromEntries(Object.entries(item.images).filter(([, url]) => typeof url === 'string'))
            : undefined,
          rating: typeof score === 'number' && Number.isFinite(score) ? { score } : undefined,
        }
      }),
    }
  })
}

export async function getBangumiCalendarData(): Promise<BangumiCalendarData[]> {
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), 10000)

  try {
    const response = await fetch('https://api.bgm.tv/calendar', { signal: controller.signal })
    if (!response.ok) throw new Error(`HTTP error! Status: ${response.status}`)

    // 解析响应体也受超时限制，异常响应不能进入页面渲染。
    return parseCalendar(await response.json())
  } finally {
    clearTimeout(timeoutId)
  }
}

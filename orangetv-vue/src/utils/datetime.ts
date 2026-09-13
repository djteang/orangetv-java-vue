export const APP_TIME_ZONE = 'Asia/Shanghai'
export type DateTimeValue = string | number | Date

// 后端 LocalDateTime / MySQL DATETIME 没有时区后缀，按站点的上海时间读取。
export function parseDateTime(value: DateTimeValue): Date {
  if (value instanceof Date) return new Date(value.getTime())
  if (typeof value !== 'string') return new Date(value)
  let normalized = value.trim().replace(' ', 'T')
  normalized = normalized.replace(/(\.\d{3})\d+(?=Z|[+-]\d{2}:?\d{2}|$)/i, '$1')
  if (/^\d{4}-\d{2}-\d{2}(?:T\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?)?$/.test(normalized)) {
    if (!normalized.includes('T')) normalized += 'T00:00:00'
    normalized += '+08:00'
  }
  return new Date(normalized)
}

export function formatDateTime(value: DateTimeValue | null | undefined, options: Intl.DateTimeFormatOptions = {}): string {
  if (value === null || value === undefined || value === '') return '时间未知'
  const date = parseDateTime(value)
  if (Number.isNaN(date.getTime())) return '时间未知'
  return date.toLocaleString('zh-CN', { hour12: false, ...options, timeZone: APP_TIME_ZONE })
}

function calendarDate(date: Date) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: APP_TIME_ZONE, year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(date)
  const part = (type: string) => Number(parts.find(item => item.type === type)?.value)
  const year = part('year')
  const month = part('month')
  const day = part('day')
  return { year, month, day, dayIndex: Date.UTC(year, month - 1, day) / 86400000 }
}

export function formatChatMessageTime(timestamp: number, now = Date.now()): string {
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return '时间未知'
  const messageDate = calendarDate(date)
  const currentDate = calendarDate(new Date(now))
  const daysAgo = currentDate.dayIndex - messageDate.dayIndex
  const time = date.toLocaleTimeString('zh-CN', {
    timeZone: APP_TIME_ZONE, hour12: false, hour: '2-digit', minute: '2-digit',
  })
  if (daysAgo === 0) return time
  if (daysAgo === 1) return `昨天 ${time}`
  if (daysAgo === 2) return `前天 ${time}`
  const day = `${messageDate.month}月${messageDate.day}日 ${time}`
  return messageDate.year === currentDate.year ? day : `${messageDate.year}年${day}`
}

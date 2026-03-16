import type { BangumiCalendarData } from '@/types'

export async function getBangumiCalendarData(): Promise<BangumiCalendarData[]> {
  const response = await fetch('https://api.bgm.tv/calendar')
  if (!response.ok) throw new Error(`HTTP error! Status: ${response.status}`)
  return response.json()
}

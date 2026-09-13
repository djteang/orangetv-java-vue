export const YELLOW_FILTER_CHANGE_EVENT = 'orangetv:yellow-filter-change'
const STORAGE_KEY = 'disableYellowFilter'
let fallbackPreference = false

export function readDisableYellowFilter(): boolean {
  try { return localStorage.getItem(STORAGE_KEY) === 'true' } catch { return fallbackPreference }
}

export function writeDisableYellowFilter(disabled: boolean) {
  fallbackPreference = disabled
  try { localStorage.setItem(STORAGE_KEY, String(disabled)) } catch { /* 无存储模式下仍在当前页面生效。 */ }
  if (typeof window !== 'undefined') window.dispatchEvent(new CustomEvent(YELLOW_FILTER_CHANGE_EVENT))
}

export function isYellowFilterChange(event: Event): boolean {
  return event.type !== 'storage' || (event as StorageEvent).key === STORAGE_KEY || (event as StorageEvent).key === null
}

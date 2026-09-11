import { onScopeDispose, shallowRef } from 'vue'
import type { WatchEffect } from '@/types'
import { createReactionParticles, isWatchEffectType, type ReactionParticle } from '@/utils/watchReactions'

export const REACTION_DURATION = 3000
export const REACTION_COMBO_WINDOW = 1300
const MAX_BURSTS = 2
const MAX_SEEN = 128

export interface ReactionBurst extends WatchEffect {
  key: string
  lane: number
  count: number
  updatedAt: number
  particles: ReactionParticle[]
}

export function useWatchReactions() {
  const bursts = shallowRef<ReactionBurst[]>([])
  const activity = shallowRef<ReactionBurst | null>(null)
  const timers = new Map<string, ReturnType<typeof setTimeout>>()
  const seen = new Set<string>()
  let activityTimer: ReturnType<typeof setTimeout> | undefined
  let sequence = 0

  function removeBurst(key: string) {
    clearTimeout(timers.get(key))
    timers.delete(key)
    bursts.value = bursts.value.filter(burst => burst.key !== key)
  }

  function addEffect(effect: WatchEffect) {
    if (!isWatchEffectType(effect.type) || typeof effect.sender !== 'string' || !effect.sender.trim()) return
    const eventKey = JSON.stringify([effect.sender, effect.id])
    if (effect.id && seen.has(eventKey)) return
    if (effect.id) {
      seen.add(eventKey)
      if (seen.size > MAX_SEEN) seen.delete(seen.values().next().value!)
    }

    const now = Date.now()
    const combo = bursts.value.find(burst => burst.type === effect.type && burst.sender === effect.sender && now - burst.updatedAt <= REACTION_COMBO_WINDOW)
    if (!combo && bursts.value.length >= MAX_BURSTS) removeBurst(bursts.value[0].key)
    const burst: ReactionBurst = {
      ...effect,
      key: combo?.key ?? `reaction-${++sequence}`,
      lane: combo?.lane ?? (bursts.value.some(item => item.lane === 0) ? 1 : 0),
      count: combo ? combo.count + 1 : 1,
      updatedAt: now,
      particles: createReactionParticles(effect.type),
    }
    bursts.value = [...bursts.value.filter(item => item.key !== burst.key), burst]
    clearTimeout(timers.get(burst.key))
    timers.set(burst.key, setTimeout(() => removeBurst(burst.key), REACTION_DURATION))
    activity.value = burst
    clearTimeout(activityTimer)
    activityTimer = setTimeout(() => { activity.value = null }, REACTION_DURATION + 800)
  }

  onScopeDispose(() => {
    timers.forEach(timer => clearTimeout(timer))
    timers.clear()
    clearTimeout(activityTimer)
    seen.clear()
    bursts.value = []
    activity.value = null
  })

  return { bursts, activity, addEffect }
}

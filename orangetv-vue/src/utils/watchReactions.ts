import type { CSSProperties } from 'vue'
import type { WatchEffectType } from '@/types'

export const watchReactions: { type: WatchEffectType; label: string; message: string; color: string }[] = [
  { type: 'heart', label: '心动', message: '为这一幕心动', color: '244 99 141' },
  { type: 'like', label: '点赞', message: '送出一个赞', color: '73 157 245' },
  { type: 'clap', label: '喝彩', message: '为这一幕喝彩', color: '224 155 48' },
  { type: 'fire', label: '高燃', message: '点燃了全场', color: '249 120 67' },
  { type: 'laugh', label: '笑翻', message: '笑到停不下来', color: '230 179 49' },
  { type: 'wow', label: '惊叹', message: '被这一幕惊艳', color: '164 126 242' },
]

export const reactionByType = Object.fromEntries(watchReactions.map(reaction => [reaction.type, reaction])) as Record<WatchEffectType, typeof watchReactions[number]>

export function isWatchEffectType(value: unknown): value is WatchEffectType {
  return typeof value === 'string' && Object.prototype.hasOwnProperty.call(reactionByType, value)
}

export interface ReactionParticle {
  style: CSSProperties
  alternate: boolean
}

// 每次触发时固定运动参数；聊天更新或播放器重绘不会改变飞行轨迹。
export function createReactionParticles(type: WatchEffectType): ReactionParticle[] {
  return Array.from({ length: 10 }, (_, index) => {
    const angle = (index / 10) * Math.PI * 2
    let x = Math.cos(angle) * (0.75 + Math.random() * 0.5)
    let y = Math.sin(angle) * (0.75 + Math.random() * 0.5)
    if (type === 'heart' || type === 'fire' || type === 'laugh') {
      x = (index % 2 ? 1 : -1) * (0.25 + Math.random() * 0.7)
      y = -(0.65 + Math.random() * 1.1)
    }
    if (type === 'clap') y = 0.4 + Math.random() * 0.9
    return {
      alternate: index % 3 === 0,
      style: {
        '--particle-x': x.toFixed(3),
        '--particle-y': y.toFixed(3),
        '--particle-size': (0.1 + Math.random() * 0.12).toFixed(3),
        '--particle-turn': `${Math.round((Math.random() - 0.5) * 180)}deg`,
        '--particle-delay': `${index * 38}ms`,
      } as CSSProperties,
    }
  })
}

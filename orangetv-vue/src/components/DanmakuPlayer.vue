<template>
  <div class="danmaku-container" ref="containerRef">
    <div class="danmaku-track" v-for="(track, index) in tracks" :key="index">
      <div
        v-for="danmaku in track"
        :key="danmaku.id"
        class="danmaku-item"
        :style="getDanmakuStyle(danmaku)"
      >
        {{ danmaku.text }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import request from '@/api/index'

interface Danmaku {
  id: string
  text: string
  time: number
  color: string
  type: number
  startTime: number
  track: number
}

interface Props {
  videoTitle: string
  episodeIndex?: number
  currentTime: number
  isPlaying: boolean
  apiUrl: string
  videoId?: string
}

const props = defineProps<Props>()

const containerRef = ref<HTMLElement>()
const tracks = ref<Danmaku[][]>([[], [], [], []])
const allDanmakus = ref<Danmaku[]>([])
const displayedDanmakus = ref<Set<string>>(new Set())

// 生成视频的弹幕标识（用于后端存储）
function getDanmuVideoId(): string {
  if (props.videoId) return props.videoId
  const ep = props.episodeIndex ?? 0
  return `${props.videoTitle}_ep${ep}`
}

// 从后端 DanmuController 获取弹幕
async function fetchLocalDanmaku(): Promise<Danmaku[]> {
  try {
    const danmuVideoId = getDanmuVideoId()
    const data = await request.get(`/danmu?id=${encodeURIComponent(danmuVideoId)}`) as any[]
    if (Array.isArray(data)) {
      return data.map((item: any) => ({
        id: `local_${item.id}`,
        text: item.content || '',
        time: item.time || 0,
        color: item.color || '#FFFFFF',
        type: item.type || 0,
        startTime: 0,
        track: 0,
      }))
    }
  } catch (error) {
    console.error('获取本地弹幕失败:', error)
  }
  return []
}

// 解析弹幕评论数据
function parseComments(comments: any[]): Danmaku[] {
  return comments.map((c: any, index: number) => ({
    id: `ext_${c.cid || index}`,
    text: c.m || '',
    time: parseFloat(c.p?.split(',')[0] || '0'),
    color: `#${parseInt(c.p?.split(',')[2] || '16777215').toString(16).padStart(6, '0')}`,
    type: parseInt(c.p?.split(',')[1] || '1'),
    startTime: 0,
    track: 0,
  }))
}

// 从外部弹幕 API 获取弹幕
async function fetchExternalDanmaku(): Promise<Danmaku[]> {
  if (!props.apiUrl || !props.videoTitle) return []

  try {
    // 1. 通过关键字搜索匹配的剧集
    const searchUrl = `${props.apiUrl}/api/v2/search/episodes?anime=${encodeURIComponent(props.videoTitle)}`
    const searchRes = await fetch(searchUrl)
    const searchData = await searchRes.json()

    if (!searchData.animes || searchData.animes.length === 0) {
      console.log('未找到匹配的外部弹幕')
      return []
    }

    // 2. 找到对应集数
    const anime = searchData.animes[0]
    const episodeIndex = props.episodeIndex || 0
    if (!anime.episodes || anime.episodes.length <= episodeIndex) {
      console.log('未找到对应集数')
      return []
    }

    const episode = anime.episodes[episodeIndex]

    // 3. 获取弹幕数据：优先使用 url 参数，否则使用 episodeId
    let commentUrl: string
    if (episode.url) {
      commentUrl = `${props.apiUrl}/api/v2/comment?url=${encodeURIComponent(episode.url)}&format=json`
    } else {
      commentUrl = `${props.apiUrl}/api/v2/comment/${episode.episodeId}?format=json`
    }

    const commentRes = await fetch(commentUrl)
    const commentData = await commentRes.json()

    // 4. 解析弹幕数据
    if (commentData.comments) {
      console.log(`从外部API获取到 ${commentData.comments.length} 条弹幕`)
      return parseComments(commentData.comments)
    }
  } catch (error) {
    console.error('获取外部弹幕失败:', error)
  }
  return []
}

// 获取所有弹幕数据（本地 + 外部）
async function fetchDanmaku() {
  const [localDanmakus, externalDanmakus] = await Promise.all([
    fetchLocalDanmaku(),
    fetchExternalDanmaku(),
  ])

  allDanmakus.value = [...localDanmakus, ...externalDanmakus]
  console.log(`加载了 ${allDanmakus.value.length} 条弹幕（本地 ${localDanmakus.length}，外部 ${externalDanmakus.length}）`)
}

// 获取弹幕样式
function getDanmakuStyle(danmaku: Danmaku) {
  const elapsed = Date.now() - danmaku.startTime
  const duration = 10000 // 10秒滚动完成
  const progress = elapsed / duration
  const translateX = progress * 120 // 从右到左移动120%

  return {
    color: danmaku.color,
    transform: `translateX(${100 - translateX}%)`,
    opacity: progress > 1 ? 0 : 1
  }
}

// 添加弹幕到轨道
function addDanmakuToTrack(danmaku: Danmaku) {
  // 找到空闲的轨道
  let trackIndex = 0
  for (let i = 0; i < tracks.value.length; i++) {
    const track = tracks.value[i]
    if (track.length === 0 || Date.now() - track[track.length - 1].startTime > 2000) {
      trackIndex = i
      break
    }
  }

  danmaku.track = trackIndex
  danmaku.startTime = Date.now()
  tracks.value[trackIndex].push(danmaku)

  // 10秒后移除
  setTimeout(() => {
    const track = tracks.value[trackIndex]
    const index = track.findIndex(d => d.id === danmaku.id)
    if (index !== -1) {
      track.splice(index, 1)
    }
  }, 10000)
}

// 更新弹幕显示
function updateDanmaku() {
  if (!props.isPlaying) return

  const currentTime = props.currentTime
  const timeWindow = 0.5 // 0.5秒的时间窗口

  allDanmakus.value.forEach(danmaku => {
    if (
      danmaku.time >= currentTime - timeWindow &&
      danmaku.time <= currentTime + timeWindow &&
      !displayedDanmakus.value.has(danmaku.id)
    ) {
      displayedDanmakus.value.add(danmaku.id)
      addDanmakuToTrack(danmaku)
    }
  })
}

let updateInterval: number | null = null

watch(() => props.isPlaying, (playing) => {
  if (playing) {
    updateInterval = window.setInterval(updateDanmaku, 100)
  } else {
    if (updateInterval) {
      clearInterval(updateInterval)
      updateInterval = null
    }
  }
})

watch(() => props.currentTime, (newTime, oldTime) => {
  // 如果时间跳跃（拖动进度条），清空已显示的弹幕
  if (Math.abs(newTime - oldTime) > 2) {
    displayedDanmakus.value.clear()
    tracks.value.forEach(track => track.splice(0))
  }
})

// 当集数变化时重新加载弹幕
watch(() => props.episodeIndex, () => {
  allDanmakus.value = []
  displayedDanmakus.value.clear()
  tracks.value.forEach(track => track.splice(0))
  fetchDanmaku()
})

onMounted(() => {
  fetchDanmaku()
})

onUnmounted(() => {
  if (updateInterval) {
    clearInterval(updateInterval)
  }
})
</script>

<style scoped>
.danmaku-container {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  overflow: hidden;
  z-index: 10;
}

.danmaku-track {
  position: absolute;
  width: 100%;
  height: 25%;
  overflow: hidden;
}

.danmaku-track:nth-child(1) { top: 10%; }
.danmaku-track:nth-child(2) { top: 35%; }
.danmaku-track:nth-child(3) { top: 60%; }
.danmaku-track:nth-child(4) { top: 85%; }

.danmaku-item {
  position: absolute;
  right: 0;
  white-space: nowrap;
  font-size: 20px;
  font-weight: bold;
  text-shadow:
    -1px -1px 0 #000,
    1px -1px 0 #000,
    -1px 1px 0 #000,
    1px 1px 0 #000;
  transition: opacity 0.3s;
  will-change: transform;
}
</style>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { PlayCircle, Link as LinkIcon } from 'lucide-vue-next'

interface Props {
  id?: string
  source?: string
  title: string
  poster: string
  episodes?: number
  sourceName?: string
  rate?: string
  year?: string
  doubanId?: number
  type?: string
  from?: 'douban' | 'search' | 'favorite' | 'shortdrama'
  query?: string
  isBangumi?: boolean
  currentEpisode?: number
  sourceCount?: number
  sources?: { source: string; source_name: string; id: string }[]
}

const props = withDefaults(defineProps<Props>(), {
  episodes: 1,
  type: '',
  from: 'search',
  isBangumi: false,
})

const router = useRouter()
const imageError = ref(false)
const imageLoaded = ref(false)

const isDouban = computed(() => props.from === 'douban')
const showRating = computed(() => isDouban.value && !!props.rate && parseFloat(props.rate) > 0)
const showDoubanLink = computed(() => !!props.doubanId && props.doubanId !== 0)
const showEpisodes = computed(() => props.episodes > 1)
const showYear = computed(() => props.from === 'search' && !!props.year && props.year !== 'unknown' && props.year.trim() !== '')
const showSourceCount = computed(() => props.sourceCount && props.sourceCount > 1)

const episodeText = computed(() => {
  if (props.currentEpisode !== undefined) {
    return `${props.currentEpisode}/${props.episodes}`
  }
  return props.episodes.toString()
})

const doubanUrl = computed(() => {
  if (!props.doubanId) return ''
  return props.isBangumi
    ? `https://bgm.tv/subject/${props.doubanId}`
    : `https://movie.douban.com/subject/${props.doubanId}`
})

function handleClick() {
  if (props.from === 'search') {
    sessionStorage.setItem('fromPlayPage', 'true')
  }

  if (props.from === 'douban' || props.isBangumi) {
    // 豆瓣/Bangumi 条目 → 播放页按标题搜索
    const query: Record<string, string> = { title: props.title.trim() }
    if (props.year && props.year !== 'unknown' && props.year.trim()) query.year = props.year
    if (props.type) query.stype = props.type
    router.push({ path: '/play', query })
  } else if (props.sources && props.sources.length > 1) {
    // 聚合结果 → 跳转到播放页，传递所有来源
    const query: Record<string, string> = {
      title: props.title.trim(),
      sources: JSON.stringify(props.sources),
    }
    if (props.year && props.year !== 'unknown') query.year = props.year
    if (props.type) query.stype = props.type
    router.push({ path: '/play', query })
  } else if (props.id && props.source) {
    // 有明确 source + id → 直接播放
    const query: Record<string, string> = {
      source: props.source,
      id: props.id,
      title: props.title,
    }
    if (props.year) query.year = props.year
    if (props.query) query.stitle = props.query.trim()
    if (props.type) query.stype = props.type
    router.push({ path: '/play', query })
  } else {
    // 兜底：按标题跳转播放页
    const query: Record<string, string> = { title: props.title.trim() }
    if (props.year) query.year = props.year
    router.push({ path: '/play', query })
  }
}

function handleDoubanClick(e: MouseEvent) {
  e.stopPropagation()
}

function handleImageError() {
  imageError.value = true
}

function handleImageLoad() {
  imageLoaded.value = true
}

const posterUrl = computed(() => {
  if (imageError.value || !props.poster) return ''
  if (props.poster.includes('doubanio.com')) {
    return `/api/image-proxy?url=${encodeURIComponent(props.poster)}`
  }
  return props.poster
})
</script>

<template>
  <div
    @click="handleClick"
    class="group relative w-full rounded-lg bg-transparent cursor-pointer transition-all duration-300 ease-in-out hover:scale-[1.05] hover:z-[500]"
  >
    <!-- 海报容器 -->
    <div class="relative aspect-[2/3] overflow-hidden rounded-lg">
      <!-- 骨架屏 -->
      <div
        v-if="!imageLoaded"
        class="absolute inset-0 bg-gray-200 dark:bg-gray-800 animate-pulse"
      ></div>

      <!-- 海报图片 -->
      <img
        v-if="posterUrl"
        :src="posterUrl"
        :alt="title"
        class="absolute inset-0 w-full h-full object-cover"
        referrerpolicy="no-referrer"
        loading="lazy"
        @error="handleImageError"
        @load="handleImageLoad"
      />

      <!-- 悬浮遮罩 - 渐变 -->
      <div
        class="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent transition-opacity duration-300 ease-in-out opacity-0 group-hover:opacity-100"
      ></div>

      <!-- 播放按钮 -->
      <div
        class="absolute inset-0 flex items-center justify-center opacity-0 transition-all duration-300 ease-in-out delay-75 group-hover:opacity-100 group-hover:scale-100"
      >
        <PlayCircle
          :size="50"
          :stroke-width="0.8"
          class="text-white fill-transparent transition-all duration-300 ease-out hover:fill-blue-500 hover:scale-[1.1]"
        />
      </div>

      <!-- 年份徽章 (搜索结果) -->
      <div
        v-if="showYear"
        class="absolute top-2 left-2 bg-black/50 text-white text-xs font-medium px-2 py-1 rounded backdrop-blur-sm shadow-sm transition-all duration-300 ease-out group-hover:opacity-90"
      >
        {{ year }}
      </div>

      <!-- 评分徽章 (豆瓣) - 右上角粉色圆形 -->
      <div
        v-if="showRating"
        class="absolute top-2 right-2 bg-pink-500 text-white text-xs font-bold w-7 h-7 rounded-full flex items-center justify-center shadow-md transition-all duration-300 ease-out group-hover:scale-110"
      >
        {{ rate }}
      </div>

      <!-- 集数徽章 - 右上角蓝色 -->
      <div
        v-if="showEpisodes && !showRating"
        class="absolute top-2 right-2 bg-blue-500 text-white text-xs font-semibold px-2 py-1 rounded-md shadow-md transition-all duration-300 ease-out group-hover:scale-110"
      >
        {{ episodeText }}
      </div>

      <!-- 豆瓣/Bangumi 链接 - 左上角 -->
      <a
        v-if="showDoubanLink"
        :href="doubanUrl"
        target="_blank"
        rel="noopener noreferrer"
        @click="handleDoubanClick"
        class="absolute top-2 left-2 opacity-0 -translate-x-2 transition-all duration-300 ease-in-out delay-100 sm:group-hover:opacity-100 sm:group-hover:translate-x-0"
      >
        <div
          class="bg-blue-500 text-white text-xs font-bold w-7 h-7 rounded-full flex items-center justify-center shadow-md hover:bg-blue-600 hover:scale-[1.1] transition-all duration-300 ease-out"
        >
          <LinkIcon :size="16" />
        </div>
      </a>
    </div>

    <!-- 标题与来源 -->
    <div class="mt-2 text-center">
      <div class="relative">
        <span
          class="block text-sm font-semibold truncate text-gray-900 dark:text-gray-100 transition-colors duration-300 ease-in-out group-hover:text-blue-600 dark:group-hover:text-blue-400 peer"
        >
          {{ title }}
        </span>
        <!-- Tooltip -->
        <div
          class="absolute bottom-full left-1/2 transform -translate-x-1/2 mb-2 px-3 py-1 bg-gray-800 text-white text-xs rounded-md shadow-lg opacity-0 invisible peer-hover:opacity-100 peer-hover:visible transition-all duration-200 ease-out delay-100 whitespace-nowrap pointer-events-none"
        >
          {{ title }}
          <div class="absolute top-full left-1/2 transform -translate-x-1/2 w-0 h-0 border-l-4 border-r-4 border-t-4 border-transparent border-t-gray-800"></div>
        </div>
      </div>
      <!-- 来源数量（聚合时显示） -->
      <span
        v-if="showSourceCount"
        class="block text-xs text-gray-500 dark:text-gray-400 mt-1"
      >
        <span
          class="inline-block border rounded px-2 py-0.5 border-green-500/60 text-green-600 dark:border-green-400/60 dark:text-green-400 transition-all duration-300 ease-in-out group-hover:border-green-500 group-hover:text-green-600 dark:group-hover:text-green-400"
        >
          {{ sourceCount }} 个来源
        </span>
      </span>
      <!-- 单一来源 -->
      <span
        v-else-if="sourceName"
        class="block text-xs text-gray-500 dark:text-gray-400 mt-1"
      >
        <span
          class="inline-block border rounded px-2 py-0.5 border-gray-500/60 dark:border-gray-400/60 transition-all duration-300 ease-in-out group-hover:border-blue-500/60 group-hover:text-blue-600 dark:group-hover:text-blue-400"
        >
          {{ sourceName }}
        </span>
      </span>
    </div>
  </div>
</template>

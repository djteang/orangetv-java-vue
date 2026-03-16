<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import ScrollableRow from './ScrollableRow.vue'
import VideoCard from './VideoCard.vue'

const userStore = useUserStore()
const loading = ref(true)

interface RecordItem {
  key: string
  id: string
  source: string
  title: string
  poster: string
  year: string
  sourceName: string
  progress: number
  totalEpisodes: number
  currentEpisode: number
  searchTitle: string
}

const playRecordItems = computed<RecordItem[]>(() => {
  return Object.entries(userStore.playRecords)
    .sort(([, a], [, b]) => (b.save_time || 0) - (a.save_time || 0))
    .map(([key, record]) => {
      const [source, id] = key.split('+')
      const progress = record.total_time && record.total_time > 0
        ? (record.play_time / record.total_time) * 100
        : 0
      return {
        key, id, source,
        title: record.title || '',
        poster: record.cover || '',
        year: record.year || '',
        sourceName: record.source_name || '',
        progress,
        totalEpisodes: record.total_episodes || 1,
        currentEpisode: record.index || 0,
        searchTitle: record.search_title || record.title || '',
      }
    })
})

async function handleClearAll() {
  for (const key of Object.keys(userStore.playRecords)) {
    await userStore.deletePlayRecord(key)
  }
}

onMounted(async () => {
  try {
    await userStore.fetchPlayRecords()
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section v-if="loading || playRecordItems.length > 0" class="mb-8">
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-bold text-gray-800 dark:text-gray-200">继续观看</h2>
      <button
        v-if="!loading && playRecordItems.length > 0"
        class="text-sm text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
        @click="handleClearAll"
      >
        清空
      </button>
    </div>
    <ScrollableRow>
      <!-- 加载骨架屏 -->
      <template v-if="loading">
        <div
          v-for="index in 6"
          :key="index"
          class="min-w-[96px] w-24 sm:min-w-[180px] sm:w-44"
        >
          <div class="relative aspect-[2/3] w-full overflow-hidden rounded-lg bg-gray-200 animate-pulse dark:bg-gray-800">
            <div class="absolute inset-0 bg-gray-300 dark:bg-gray-700"></div>
          </div>
          <div class="mt-2 h-4 bg-gray-200 rounded animate-pulse dark:bg-gray-800"></div>
          <div class="mt-1 h-3 bg-gray-200 rounded animate-pulse dark:bg-gray-800"></div>
        </div>
      </template>
      <!-- 真实数据 -->
      <template v-else>
        <div
          v-for="item in playRecordItems"
          :key="item.key"
          class="min-w-[96px] w-24 sm:min-w-[180px] sm:w-44"
        >
          <VideoCard
            :id="item.id"
            :title="item.title"
            :poster="item.poster"
            :year="item.year"
            :source="item.source"
            :source-name="item.sourceName"
            :episodes="item.totalEpisodes"
            :current-episode="item.currentEpisode"
            :query="item.searchTitle"
            :progress="item.progress"
            from="search"
            :type="item.totalEpisodes > 1 ? 'tv' : ''"
          />
        </div>
      </template>
    </ScrollableRow>
  </section>
</template>

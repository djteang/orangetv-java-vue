<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import type { Ref } from 'vue'
import { ChevronRight, X } from 'lucide-vue-next'
import PageLayout from '@/components/PageLayout.vue'
import CapsuleSwitch from '@/components/CapsuleSwitch.vue'
import ScrollableRow from '@/components/ScrollableRow.vue'
import VideoCard from '@/components/VideoCard.vue'
import ContinueWatching from '@/components/ContinueWatching.vue'
import BangumiEmptyState from '@/components/BangumiEmptyState.vue'
import DiscoveryState from '@/components/DiscoveryState.vue'
// import { useSiteStore } from '@/stores/site'
import { useUserStore } from '@/stores/user'
import { getDoubanCategories } from '@/api/douban'
import { getBangumiCalendarData } from '@/api/bangumi'
import type { DoubanItem, BangumiCalendarData } from '@/types'

// const siteStore = useSiteStore()
const userStore = useUserStore()

const activeTab = ref<'home' | 'favorites'>('home')
const hotMovies = ref<DoubanItem[]>([])
const hotTvShows = ref<DoubanItem[]>([])
const hotVarietyShows = ref<DoubanItem[]>([])
const bangumiCalendarData = ref<BangumiCalendarData[]>([])
const moviesLoading = ref(true)
const tvShowsLoading = ref(true)
const varietyShowsLoading = ref(true)
const bangumiLoading = ref(false)
const bangumiError = ref(false)
const moviesError = ref(false)
const tvShowsError = ref(false)
const varietyShowsError = ref(false)

// 收藏夹数据
interface FavoriteItem {
  id: string
  source: string
  title: string
  poster: string
  episodes: number
  sourceName: string
  currentEpisode?: number
  searchTitle?: string
  origin?: 'vod' | 'live'
}

const favoriteItems = computed<FavoriteItem[]>(() => {
  const allFavorites = userStore.favorites
  const allPlayRecords = userStore.playRecords

  return Object.entries(allFavorites)
    .sort(([, a], [, b]) => b.save_time - a.save_time)
    .map(([key, fav]) => {
      const plusIndex = key.indexOf('+')
      const source = key.slice(0, plusIndex)
      const id = key.slice(plusIndex + 1)
      const playRecord = allPlayRecords[key]

      return {
        id,
        source,
        title: fav.title,
        poster: fav.cover,
        episodes: fav.total_episodes,
        sourceName: fav.source_name,
        currentEpisode: playRecord?.index,
        searchTitle: fav.search_title,
        origin: fav.origin,
      }
    })
})

// 今日番剧
const todayAnimes = computed(() => {
  const today = new Date()
  const weekdays = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
  const currentWeekday = weekdays[today.getDay()]
  return bangumiCalendarData.value.find((item) => item.weekday.en === currentWeekday)?.items || []
})

async function fetchDoubanSection(
  params: Parameters<typeof getDoubanCategories>[0],
  items: Ref<DoubanItem[]>,
  isLoading: Ref<boolean>,
  failed: Ref<boolean>,
) {
  try {
    isLoading.value = true
    failed.value = false
    const data = await getDoubanCategories(params)
    if (data.code !== 200) throw new Error(data.message || '加载失败')
    items.value = data.list || []
  } catch (error) {
    items.value = []
    failed.value = true
    console.error('获取首页推荐失败:', error)
  } finally {
    isLoading.value = false
  }
}

async function fetchBangumiData() {
  if (bangumiLoading.value) return

  bangumiLoading.value = true
  bangumiError.value = false
  try {
    bangumiCalendarData.value = await getBangumiCalendarData()
  } catch (error) {
    bangumiCalendarData.value = []
    bangumiError.value = true
    console.error('获取新番放送失败:', error)
  } finally {
    bangumiLoading.value = false
  }
}

const reloadMovies = () => fetchDoubanSection({ kind: 'movie', category: '热门', type: '全部' }, hotMovies, moviesLoading, moviesError)
const reloadTv = () => fetchDoubanSection({ kind: 'tv', category: 'tv', type: 'tv' }, hotTvShows, tvShowsLoading, tvShowsError)
const reloadVariety = () => fetchDoubanSection({ kind: 'tv', category: 'show', type: 'show' }, hotVarietyShows, varietyShowsLoading, varietyShowsError)

async function clearAllFavorites() {
  for (const key of Object.keys(userStore.favorites)) {
    await userStore.deleteFavorite(key)
  }
}

async function handleDeleteFavorite(source: string, id: string) {
  await userStore.deleteFavorite(`${source}+${id}`)
}

watch(activeTab, async (newTab) => {
  if (newTab === 'favorites') {
    await userStore.fetchFavorites()
    await userStore.fetchPlayRecords()
  }
})

onMounted(() => {
  // 各栏目独立更新，单个接口失败或超时不会阻塞其他内容。
  void fetchDoubanSection(
    { kind: 'movie', category: '热门', type: '全部' }, hotMovies, moviesLoading, moviesError,
  )
  void fetchDoubanSection(
    { kind: 'tv', category: 'tv', type: 'tv' }, hotTvShows, tvShowsLoading, tvShowsError,
  )
  void fetchDoubanSection(
    { kind: 'tv', category: 'show', type: 'show' }, hotVarietyShows, varietyShowsLoading, varietyShowsError,
  )
  void fetchBangumiData()
})
</script>

<template>
  <PageLayout>
    <div class="px-2 sm:px-10 py-4 sm:py-8 overflow-visible">
      <!-- 顶部 Tab 切换 -->
      <div class="mb-8 flex justify-center">
        <CapsuleSwitch
          :options="[
            { label: '首页', value: 'home' },
            { label: '收藏夹', value: 'favorites' },
          ]"
          :active="activeTab"
          @change="(value) => (activeTab = value as 'home' | 'favorites')"
        />
      </div>

      <div class="max-w-[95%] mx-auto">
        <!-- 收藏夹视图 -->
        <template v-if="activeTab === 'favorites'">
          <section class="mb-8">
            <div class="mb-4 flex items-center justify-between">
              <h2 class="text-xl font-bold text-gray-800 dark:text-gray-200">我的收藏</h2>
              <button
                v-if="favoriteItems.length > 0"
                class="text-sm text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
                @click="clearAllFavorites"
              >
                清空
              </button>
            </div>
            <div
              class="justify-start grid grid-cols-3 gap-x-2 gap-y-14 sm:gap-y-20 px-0 sm:px-2 sm:grid-cols-[repeat(auto-fill,_minmax(11rem,_1fr))] sm:gap-x-8"
            >
              <div v-for="item in favoriteItems" :key="item.id + item.source" class="relative group/card w-full">
                <button
                  class="absolute -top-1.5 -right-1.5 z-[999] w-5 h-5 rounded-full bg-black/60 text-white flex items-center justify-center opacity-0 group-hover/card:opacity-100 transition-opacity duration-200 hover:bg-red-500"
                  title="取消收藏"
                  @click.stop="handleDeleteFavorite(item.source, item.id)"
                >
                  <X class="w-3 h-3" />
                </button>
                <VideoCard
                  :query="item.searchTitle"
                  :id="item.id"
                  :source="item.source"
                  :title="item.title"
                  :poster="item.poster"
                  :episodes="item.episodes"
                  :source-name="item.sourceName"
                  :current-episode="item.currentEpisode"
                  from="favorite"
                  :type="item.episodes > 1 ? 'tv' : ''"
                />
              </div>
              <DiscoveryState v-if="favoriteItems.length === 0" class="col-span-full" variant="favorites" title="暂无收藏内容" description="遇到喜欢的影片，点亮收藏，把好故事留在这里。" action-label="去发现好片" @action="activeTab = 'home'" />
            </div>
          </section>
        </template>

        <!-- 首页视图 -->
        <template v-else>
          <!-- 继续观看 -->
          <ContinueWatching />

          <!-- 热门电影 -->
          <section class="mb-8">
            <div class="mb-4 flex items-center justify-between">
              <h2 class="text-xl font-bold text-gray-800 dark:text-gray-200">热门电影</h2>
              <router-link
                to="/douban?type=movie"
                class="flex items-center text-sm text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
              >
                查看更多
                <ChevronRight class="w-4 h-4 ml-1" />
              </router-link>
            </div>
            <DiscoveryState v-if="!moviesLoading && hotMovies.length === 0" compact variant="movie" :mode="moviesError ? 'error' : 'empty'" :title="moviesError ? '热门电影暂时加载失败' : '暂无热门电影'" description="片库的这一站暂时安静，稍后再来看看新的故事。" action-label="重新加载" @action="reloadMovies" />
            <ScrollableRow v-else>
              <template v-if="moviesLoading">
                <div
                  v-for="index in 8"
                  :key="index"
                  class="min-w-[96px] w-24 sm:min-w-[180px] sm:w-44"
                >
                  <div
                    class="relative aspect-[2/3] w-full overflow-hidden rounded-lg bg-gray-200 animate-pulse dark:bg-gray-800"
                  >
                    <div class="absolute inset-0 bg-gray-300 dark:bg-gray-700"></div>
                  </div>
                  <div class="mt-2 h-4 bg-gray-200 rounded animate-pulse dark:bg-gray-800"></div>
                </div>
              </template>
              <template v-else>
                <div
                  v-for="(movie, index) in hotMovies"
                  :key="index"
                  class="min-w-[96px] w-24 sm:min-w-[180px] sm:w-44"
                >
                  <VideoCard
                    from="douban"
                    :title="movie.title"
                    :poster="movie.poster"
                    :douban-id="Number(movie.id)"
                    :rate="movie.rate"
                    :year="movie.year"
                    type="movie"
                  />
                </div>
              </template>
            </ScrollableRow>
          </section>

          <!-- 热门剧集 -->
          <section class="mb-8">
            <div class="mb-4 flex items-center justify-between">
              <h2 class="text-xl font-bold text-gray-800 dark:text-gray-200">热门剧集</h2>
              <router-link
                to="/douban?type=tv"
                class="flex items-center text-sm text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
              >
                查看更多
                <ChevronRight class="w-4 h-4 ml-1" />
              </router-link>
            </div>
            <DiscoveryState v-if="!tvShowsLoading && hotTvShows.length === 0" compact variant="tv" :mode="tvShowsError ? 'error' : 'empty'" :title="tvShowsError ? '热门剧集暂时加载失败' : '暂无热门剧集'" description="片库的这一站暂时安静，稍后再来看看新的故事。" action-label="重新加载" @action="reloadTv" />
            <ScrollableRow v-else>
              <template v-if="tvShowsLoading">
                <div
                  v-for="index in 8"
                  :key="index"
                  class="min-w-[96px] w-24 sm:min-w-[180px] sm:w-44"
                >
                  <div
                    class="relative aspect-[2/3] w-full overflow-hidden rounded-lg bg-gray-200 animate-pulse dark:bg-gray-800"
                  >
                    <div class="absolute inset-0 bg-gray-300 dark:bg-gray-700"></div>
                  </div>
                  <div class="mt-2 h-4 bg-gray-200 rounded animate-pulse dark:bg-gray-800"></div>
                </div>
              </template>
              <template v-else>
                <div
                  v-for="(show, index) in hotTvShows"
                  :key="index"
                  class="min-w-[96px] w-24 sm:min-w-[180px] sm:w-44"
                >
                  <VideoCard
                    from="douban"
                    :title="show.title"
                    :poster="show.poster"
                    :douban-id="Number(show.id)"
                    :rate="show.rate"
                    :year="show.year"
                  />
                </div>
              </template>
            </ScrollableRow>
          </section>

          <!-- 新番放送 -->
          <section class="mb-8">
            <div class="mb-4 flex items-center justify-between">
              <h2 class="text-xl font-bold text-gray-800 dark:text-gray-200">新番放送</h2>
              <router-link
                to="/douban?type=anime"
                class="flex items-center text-sm text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
              >
                查看更多
                <ChevronRight class="w-4 h-4 ml-1" />
              </router-link>
            </div>
            <BangumiEmptyState
              v-if="!bangumiLoading && (bangumiError || todayAnimes.length === 0)"
              :failed="bangumiError"
              @retry="fetchBangumiData"
            />
            <ScrollableRow v-else>
              <template v-if="bangumiLoading">
                <div
                  v-for="index in 8"
                  :key="index"
                  class="min-w-[96px] w-24 sm:min-w-[180px] sm:w-44"
                >
                  <div
                    class="relative aspect-[2/3] w-full overflow-hidden rounded-lg bg-gray-200 animate-pulse dark:bg-gray-800"
                  >
                    <div class="absolute inset-0 bg-gray-300 dark:bg-gray-700"></div>
                  </div>
                  <div class="mt-2 h-4 bg-gray-200 rounded animate-pulse dark:bg-gray-800"></div>
                </div>
              </template>
              <template v-else>
                <div
                  v-for="(anime, index) in todayAnimes"
                  :key="`${anime.id || 0}-${index}`"
                  class="min-w-[96px] w-24 sm:min-w-[180px] sm:w-44"
                >
                  <VideoCard
                    from="douban"
                    :title="anime.name_cn || anime.name || '未知标题'"
                    :poster="anime.images?.large || anime.images?.common || anime.images?.medium || anime.images?.small || anime.images?.grid || ''"
                    :douban-id="anime.id || 0"
                    :rate="anime.rating?.score?.toFixed(1) || ''"
                    :year="anime.air_date?.split('-')?.[0] || ''"
                    :is-bangumi="true"
                  />
                </div>
              </template>
            </ScrollableRow>
          </section>

          <!-- 热门综艺 -->
          <section class="mb-8">
            <div class="mb-4 flex items-center justify-between">
              <h2 class="text-xl font-bold text-gray-800 dark:text-gray-200">热门综艺</h2>
              <router-link
                to="/douban?type=show"
                class="flex items-center text-sm text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
              >
                查看更多
                <ChevronRight class="w-4 h-4 ml-1" />
              </router-link>
            </div>
            <DiscoveryState v-if="!varietyShowsLoading && hotVarietyShows.length === 0" compact variant="show" :mode="varietyShowsError ? 'error' : 'empty'" :title="varietyShowsError ? '热门综艺暂时加载失败' : '暂无热门综艺'" description="片库的这一站暂时安静，稍后再来看看新的故事。" action-label="重新加载" @action="reloadVariety" />
            <ScrollableRow v-else>
              <template v-if="varietyShowsLoading">
                <div
                  v-for="index in 8"
                  :key="index"
                  class="min-w-[96px] w-24 sm:min-w-[180px] sm:w-44"
                >
                  <div
                    class="relative aspect-[2/3] w-full overflow-hidden rounded-lg bg-gray-200 animate-pulse dark:bg-gray-800"
                  >
                    <div class="absolute inset-0 bg-gray-300 dark:bg-gray-700"></div>
                  </div>
                  <div class="mt-2 h-4 bg-gray-200 rounded animate-pulse dark:bg-gray-800"></div>
                </div>
              </template>
              <template v-else>
                <div
                  v-for="(show, index) in hotVarietyShows"
                  :key="index"
                  class="min-w-[96px] w-24 sm:min-w-[180px] sm:w-44"
                >
                  <VideoCard
                    from="douban"
                    :title="show.title"
                    :poster="show.poster"
                    :douban-id="Number(show.id)"
                    :rate="show.rate"
                    :year="show.year"
                  />
                </div>
              </template>
            </ScrollableRow>
          </section>
        </template>
      </div>
    </div>
  </PageLayout>
</template>

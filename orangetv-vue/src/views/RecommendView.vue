<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import PageLayout from '@/components/PageLayout.vue'
import ScrollableRow from '@/components/ScrollableRow.vue'
import VideoCard from '@/components/VideoCard.vue'
import DiscoveryState from '@/components/DiscoveryState.vue'
import { useAuthStore } from '@/stores/auth'
import { useUserStore } from '@/stores/user'
import { getDoubanRecommends } from '@/api/douban'
import type { DoubanItem } from '@/types'

const authStore = useAuthStore()
const userStore = useUserStore()

const recommendMovies = ref<DoubanItem[]>([])
const recommendTv = ref<DoubanItem[]>([])
const recommendAnime = ref<DoubanItem[]>([])
const recommendVariety = ref<DoubanItem[]>([])
const loading = ref(true)
const failedCategories = ref(new Set<string>())
const hasRecommendations = computed(() => [recommendMovies, recommendTv, recommendAnime, recommendVariety].some(items => items.value.length > 0))

// ======================== Bigram 余弦相似度推荐算法 ========================

/** 提取文本的 bigram 特征 */
function extractBigrams(text: string): Map<string, number> {
  const cleaned = text.replace(/[\s·.·　:：,，、!！?？()（）[\]【】]+/g, '').toLowerCase()
  const map = new Map<string, number>()
  if (cleaned.length < 2) return map
  for (let i = 0; i < cleaned.length - 1; i++) {
    const bigram = cleaned.substring(i, i + 2)
    map.set(bigram, (map.get(bigram) || 0) + 1)
  }
  return map
}

/** 构建用户画像向量：播放记录（按进度加权）+ 收藏（固定权重 2.0） */
function buildUserProfile(): Map<string, number> {
  const vector = new Map<string, number>()

  // 播放记录
  for (const record of Object.values(userStore.playRecords)) {
    if (!record.title) continue
    let weight = 0.1
    if (record.total_time > 0) {
      weight = Math.max(Math.min(record.play_time / record.total_time, 1.0), 0.1)
    }
    for (const [bigram, count] of extractBigrams(record.title)) {
      vector.set(bigram, (vector.get(bigram) || 0) + count * weight)
    }
  }

  // 收藏
  for (const fav of Object.values(userStore.favorites)) {
    if (!fav.title) continue
    for (const [bigram, count] of extractBigrams(fav.title)) {
      vector.set(bigram, (vector.get(bigram) || 0) + count * 2.0)
    }
  }

  return vector
}

/** 余弦相似度 */
function cosineSimilarity(a: Map<string, number>, b: Map<string, number>): number {
  if (a.size === 0 || b.size === 0) return 0

  let dot = 0
  const [smaller, larger] = a.size <= b.size ? [a, b] : [b, a]
  for (const [key, val] of smaller) {
    const other = larger.get(key)
    if (other !== undefined) dot += val * other
  }
  if (dot === 0) return 0

  let normA = 0, normB = 0
  for (const v of a.values()) normA += v * v
  for (const v of b.values()) normB += v * v

  const denom = Math.sqrt(normA) * Math.sqrt(normB)
  return denom === 0 ? 0 : dot / denom
}

/** 对候选列表做推荐排序，取 top N */
function rankCandidates(candidates: DoubanItem[], userProfile: Map<string, number>, n: number): DoubanItem[] {
  if (candidates.length === 0) return []

  // 无画像 → 按年份降序
  if (userProfile.size === 0) {
    return pickLatest(candidates, n)
  }

  const scored = candidates.map(item => ({
    item,
    score: cosineSimilarity(userProfile, extractBigrams(item.title)),
  }))

  scored.sort((a, b) => b.score - a.score)

  // 全部相似度为 0 → 按年份降序
  if (scored[0].score <= 0) {
    return pickLatest(candidates, n)
  }

  return scored.slice(0, n).map(s => s.item)
}

/** 按年份降序取最新的 N 个 */
function pickLatest(candidates: DoubanItem[], n: number): DoubanItem[] {
  return [...candidates]
    .sort((a, b) => {
      const ya = parseInt(a.year) || 0
      const yb = parseInt(b.year) || 0
      return yb - ya
    })
    .slice(0, n)
}

// ======================== 豆瓣候选池获取 ========================

interface CategoryConfig {
  kind: 'movie' | 'tv'
  category?: string
  format?: string
}

const CATEGORY_CONFIGS: Record<string, CategoryConfig> = {
  movie:   { kind: 'movie' },
  tv:      { kind: 'tv', format: '电视剧' },
  anime:   { kind: 'tv', category: '动画' },
  variety: { kind: 'tv', format: '综艺' },
}

async function fetchCandidates(category: string): Promise<DoubanItem[]> {
  const config = CATEGORY_CONFIGS[category]
  if (!config) return []

  try {
    const data = await getDoubanRecommends({
      kind: config.kind,
      pageLimit: 50,
      pageStart: 0,
      category: config.category || '',
      format: config.format || '',
    })
    if (data.code !== 200) throw new Error(data.message || '加载失败')
    return data.list || []
  } catch (e) {
    failedCategories.value = new Set([...failedCategories.value, category])
    console.error(`获取${category}候选池失败:`, e)
    return []
  }
}

// ======================== 主流程 ========================

async function fetchRecommendations() {
  if (!authStore.isLoggedIn) {
    loading.value = false
    return
  }

  try {
    loading.value = true
    failedCategories.value = new Set()

    // 并行：拉取用户数据 + 4 个分类候选池
    const [, , movieCandidates, tvCandidates, animeCandidates, varietyCandidates] = await Promise.all([
      userStore.fetchPlayRecords().catch(() => {}),
      userStore.fetchFavorites().catch(() => {}),
      fetchCandidates('movie'),
      fetchCandidates('tv'),
      fetchCandidates('anime'),
      fetchCandidates('variety'),
    ])

    // 构建用户画像
    const userProfile = buildUserProfile()

    // 推荐排序
    recommendMovies.value = rankCandidates(movieCandidates, userProfile, 10)
    recommendTv.value = rankCandidates(tvCandidates, userProfile, 10)
    recommendAnime.value = rankCandidates(animeCandidates, userProfile, 10)
    recommendVariety.value = rankCandidates(varietyCandidates, userProfile, 10)
  } catch (error) {
    console.error('获取推荐数据失败:', error)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchRecommendations()
})
</script>

<template>
  <PageLayout>
    <div class="px-2 sm:px-10 py-4 sm:py-8 overflow-visible">
      <div class="max-w-[95%] mx-auto">
        <!-- 未登录提示 -->
        <div
          v-if="!authStore.isLoggedIn"
          class="flex flex-col items-center justify-center py-20 text-gray-500 dark:text-gray-400"
        >
          <p class="text-lg">请先登录以获取个性化推荐</p>
          <router-link
            to="/login"
            class="mt-4 px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
          >
            去登录
          </router-link>
        </div>

        <template v-else>
          <!-- 推荐电影 -->
          <section v-if="loading || hasRecommendations" class="mb-8">
            <div class="mb-4 flex items-center justify-between">
              <h2 class="text-xl font-bold text-gray-800 dark:text-gray-200">推荐电影</h2>
            </div>
            <DiscoveryState v-if="!loading && recommendMovies.length === 0" compact variant="movie" :mode="failedCategories.has('movie') ? 'error' : 'empty'" title="暂无推荐电影" description="新的片单正在路上，也可以先看看其他分类。" action-label="刷新推荐" @action="fetchRecommendations" />
            <ScrollableRow v-else>
              <template v-if="loading">
                <div v-for="index in 8" :key="index" class="min-w-[96px] w-24 sm:min-w-[180px] sm:w-44">
                  <div class="relative aspect-[2/3] w-full overflow-hidden rounded-lg bg-gray-200 animate-pulse dark:bg-gray-800">
                    <div class="absolute inset-0 bg-gray-300 dark:bg-gray-700"></div>
                  </div>
                  <div class="mt-2 h-4 bg-gray-200 rounded animate-pulse dark:bg-gray-800"></div>
                </div>
              </template>
              <template v-else>
                <div v-for="(item, index) in recommendMovies" :key="`movie-${item.id}-${index}`" class="min-w-[96px] w-24 sm:min-w-[180px] sm:w-44">
                  <VideoCard from="douban" :title="item.title" :poster="item.poster" :douban-id="Number(item.id)" :rate="item.rate" :year="item.year" type="movie" />
                </div>
              </template>
            </ScrollableRow>
          </section>

          <!-- 推荐剧集 -->
          <section v-if="loading || hasRecommendations" class="mb-8">
            <div class="mb-4 flex items-center justify-between">
              <h2 class="text-xl font-bold text-gray-800 dark:text-gray-200">推荐剧集</h2>
            </div>
            <DiscoveryState v-if="!loading && recommendTv.length === 0" compact variant="tv" :mode="failedCategories.has('tv') ? 'error' : 'empty'" title="暂无推荐剧集" description="新的片单正在路上，也可以先看看其他分类。" action-label="刷新推荐" @action="fetchRecommendations" />
            <ScrollableRow v-else>
              <template v-if="loading">
                <div v-for="index in 8" :key="index" class="min-w-[96px] w-24 sm:min-w-[180px] sm:w-44">
                  <div class="relative aspect-[2/3] w-full overflow-hidden rounded-lg bg-gray-200 animate-pulse dark:bg-gray-800">
                    <div class="absolute inset-0 bg-gray-300 dark:bg-gray-700"></div>
                  </div>
                  <div class="mt-2 h-4 bg-gray-200 rounded animate-pulse dark:bg-gray-800"></div>
                </div>
              </template>
              <template v-else>
                <div v-for="(item, index) in recommendTv" :key="`tv-${item.id}-${index}`" class="min-w-[96px] w-24 sm:min-w-[180px] sm:w-44">
                  <VideoCard from="douban" :title="item.title" :poster="item.poster" :douban-id="Number(item.id)" :rate="item.rate" :year="item.year" />
                </div>
              </template>
            </ScrollableRow>
          </section>

          <!-- 推荐动漫 -->
          <section v-if="loading || hasRecommendations" class="mb-8">
            <div class="mb-4 flex items-center justify-between">
              <h2 class="text-xl font-bold text-gray-800 dark:text-gray-200">推荐动漫</h2>
            </div>
            <DiscoveryState v-if="!loading && recommendAnime.length === 0" compact variant="anime" :mode="failedCategories.has('anime') ? 'error' : 'empty'" title="暂无推荐动漫" description="新的片单正在路上，也可以先看看其他分类。" action-label="刷新推荐" @action="fetchRecommendations" />
            <ScrollableRow v-else>
              <template v-if="loading">
                <div v-for="index in 8" :key="index" class="min-w-[96px] w-24 sm:min-w-[180px] sm:w-44">
                  <div class="relative aspect-[2/3] w-full overflow-hidden rounded-lg bg-gray-200 animate-pulse dark:bg-gray-800">
                    <div class="absolute inset-0 bg-gray-300 dark:bg-gray-700"></div>
                  </div>
                  <div class="mt-2 h-4 bg-gray-200 rounded animate-pulse dark:bg-gray-800"></div>
                </div>
              </template>
              <template v-else>
                <div v-for="(item, index) in recommendAnime" :key="`anime-${item.id}-${index}`" class="min-w-[96px] w-24 sm:min-w-[180px] sm:w-44">
                  <VideoCard from="douban" :title="item.title" :poster="item.poster" :douban-id="Number(item.id)" :rate="item.rate" :year="item.year" />
                </div>
              </template>
            </ScrollableRow>
          </section>

          <!-- 推荐综艺 -->
          <section v-if="loading || hasRecommendations" class="mb-8">
            <div class="mb-4 flex items-center justify-between">
              <h2 class="text-xl font-bold text-gray-800 dark:text-gray-200">推荐综艺</h2>
            </div>
            <DiscoveryState v-if="!loading && recommendVariety.length === 0" compact variant="show" :mode="failedCategories.has('variety') ? 'error' : 'empty'" title="暂无推荐综艺" description="新的片单正在路上，也可以先看看其他分类。" action-label="刷新推荐" @action="fetchRecommendations" />
            <ScrollableRow v-else>
              <template v-if="loading">
                <div v-for="index in 8" :key="index" class="min-w-[96px] w-24 sm:min-w-[180px] sm:w-44">
                  <div class="relative aspect-[2/3] w-full overflow-hidden rounded-lg bg-gray-200 animate-pulse dark:bg-gray-800">
                    <div class="absolute inset-0 bg-gray-300 dark:bg-gray-700"></div>
                  </div>
                  <div class="mt-2 h-4 bg-gray-200 rounded animate-pulse dark:bg-gray-800"></div>
                </div>
              </template>
              <template v-else>
                <div v-for="(item, index) in recommendVariety" :key="`variety-${item.id}-${index}`" class="min-w-[96px] w-24 sm:min-w-[180px] sm:w-44">
                  <VideoCard from="douban" :title="item.title" :poster="item.poster" :douban-id="Number(item.id)" :rate="item.rate" :year="item.year" />
                </div>
              </template>
            </ScrollableRow>
          </section>

          <DiscoveryState v-if="!loading && !hasRecommendations" variant="recommend" :mode="failedCategories.size ? 'error' : 'empty'" :title="failedCategories.size ? '推荐信号暂时中断' : '暂无推荐内容'" :description="failedCategories.size ? '片库暂时没有连接成功，稍后再试试吧。' : '多看看、收藏喜欢的影片，让下一份片单更懂你。'" action-label="刷新推荐" @action="fetchRecommendations" />
        </template>
      </div>
    </div>
  </PageLayout>
</template>

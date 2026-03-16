<script setup lang="ts">
import { ref, watch, onMounted, nextTick } from 'vue'
import MultiLevelSelector from './MultiLevelSelector.vue'
import WeekdaySelector from './WeekdaySelector.vue'

type ContentType = 'movie' | 'tv' | 'show' | 'anime'

interface SelectorOption {
  label: string
  value: string
}

const props = defineProps<{
  type: ContentType
  primarySelection?: string
  secondarySelection?: string
}>()

const emit = defineEmits<{
  (e: 'primary-change', value: string): void
  (e: 'secondary-change', value: string): void
  (e: 'multi-level-change', values: Record<string, string>): void
  (e: 'weekday-change', weekday: string): void
}>()

// 各类型的选项配置（与 DoubanSelector.tsx 完全一致）
const moviePrimaryOptions: SelectorOption[] = [
  { label: '全部', value: '全部' },
  { label: '热门电影', value: '热门' },
  { label: '最新电影', value: '最新' },
  { label: '豆瓣高分', value: '豆瓣高分' },
  { label: '冷门佳片', value: '冷门佳片' },
]
const movieSecondaryOptions: SelectorOption[] = [
  { label: '全部', value: '全部' },
  { label: '华语', value: '华语' },
  { label: '欧美', value: '欧美' },
  { label: '韩国', value: '韩国' },
  { label: '日本', value: '日本' },
]
const tvPrimaryOptions: SelectorOption[] = [
  { label: '全部', value: '全部' },
  { label: '最近热门', value: '最近热门' },
]
const tvSecondaryOptions: SelectorOption[] = [
  { label: '全部', value: 'tv' },
  { label: '国产', value: 'tv_domestic' },
  { label: '欧美', value: 'tv_american' },
  { label: '日本', value: 'tv_japanese' },
  { label: '韩国', value: 'tv_korean' },
  { label: '动漫', value: 'tv_animation' },
  { label: '纪录片', value: 'tv_documentary' },
]
const showPrimaryOptions: SelectorOption[] = [
  { label: '全部', value: '全部' },
  { label: '最近热门', value: '最近热门' },
]
const showSecondaryOptions: SelectorOption[] = [
  { label: '全部', value: 'show' },
  { label: '国内', value: 'show_domestic' },
  { label: '国外', value: 'show_foreign' },
]
const animePrimaryOptions: SelectorOption[] = [
  { label: '每日放送', value: '每日放送' },
  { label: '番剧', value: '番剧' },
  { label: '剧场版', value: '剧场版' },
]

// 滑动指示器状态
const primaryContainerRef = ref<HTMLDivElement | null>(null)
const primaryButtonRefs = ref<(HTMLButtonElement | null)[]>([])
const primaryIndicatorStyle = ref({ left: 0, width: 0 })

const secondaryContainerRef = ref<HTMLDivElement | null>(null)
const secondaryButtonRefs = ref<(HTMLButtonElement | null)[]>([])
const secondaryIndicatorStyle = ref({ left: 0, width: 0 })

function updatePrimaryIndicator(options: SelectorOption[], value: string | undefined) {
  const idx = options.findIndex(o => o.value === value)
  if (idx < 0) return
  setTimeout(() => {
    const btn = primaryButtonRefs.value[idx]
    const container = primaryContainerRef.value
    if (btn && container) {
      const br = btn.getBoundingClientRect()
      const cr = container.getBoundingClientRect()
      if (br.width > 0) primaryIndicatorStyle.value = { left: br.left - cr.left, width: br.width }
    }
  }, 0)
}

function updateSecondaryIndicator(options: SelectorOption[], value: string | undefined) {
  const idx = options.findIndex(o => o.value === value)
  if (idx < 0) return
  setTimeout(() => {
    const btn = secondaryButtonRefs.value[idx]
    const container = secondaryContainerRef.value
    if (btn && container) {
      const br = btn.getBoundingClientRect()
      const cr = container.getBoundingClientRect()
      if (br.width > 0) secondaryIndicatorStyle.value = { left: br.left - cr.left, width: br.width }
    }
  }, 0)
}

function getPrimaryOptions(): SelectorOption[] {
  if (props.type === 'movie') return moviePrimaryOptions
  if (props.type === 'tv') return tvPrimaryOptions
  if (props.type === 'show') return showPrimaryOptions
  if (props.type === 'anime') return animePrimaryOptions
  return []
}

function getSecondaryOptions(): SelectorOption[] {
  if (props.type === 'movie') return movieSecondaryOptions
  if (props.type === 'tv') return tvSecondaryOptions
  if (props.type === 'show') return showSecondaryOptions
  return []
}

// type 变化时重新计算指示器
watch(() => props.type, async () => {
  await nextTick()
  primaryButtonRefs.value = []
  secondaryButtonRefs.value = []
  updatePrimaryIndicator(getPrimaryOptions(), props.primarySelection)
  updateSecondaryIndicator(getSecondaryOptions(), props.secondarySelection)
}, { flush: 'post' })

watch(() => props.primarySelection, () => {
  updatePrimaryIndicator(getPrimaryOptions(), props.primarySelection)
})

watch(() => props.secondarySelection, () => {
  updateSecondaryIndicator(getSecondaryOptions(), props.secondarySelection)
})

onMounted(() => {
  updatePrimaryIndicator(getPrimaryOptions(), props.primarySelection)
  updateSecondaryIndicator(getSecondaryOptions(), props.secondarySelection)
})

function effectivePrimary(): string {
  const opts = getPrimaryOptions()
  return props.primarySelection || (opts.length > 0 ? opts[0].value : '')
}

function effectiveSecondary(): string {
  const opts = getSecondaryOptions()
  return props.secondarySelection || (opts.length > 0 ? opts[0].value : '')
}
</script>

<template>
  <div class="space-y-4 sm:space-y-6">

    <!-- 电影 -->
    <template v-if="type === 'movie'">
      <div class="space-y-3 sm:space-y-4">
        <!-- 一级：分类 -->
        <div class="flex flex-col sm:flex-row sm:items-center gap-2">
          <span class="text-xs sm:text-sm font-medium text-gray-600 dark:text-gray-400 min-w-[48px]">分类</span>
          <div class="overflow-x-auto">
            <div ref="primaryContainerRef" class="relative inline-flex bg-gray-200/60 rounded-full p-0.5 sm:p-1 dark:bg-gray-700/60 backdrop-blur-sm">
              <div
                v-if="primaryIndicatorStyle.width > 0"
                class="absolute top-0.5 bottom-0.5 sm:top-1 sm:bottom-1 bg-white dark:bg-gray-500 rounded-full shadow-sm transition-all duration-300 ease-out"
                :style="{ left: `${primaryIndicatorStyle.left}px`, width: `${primaryIndicatorStyle.width}px` }"
              />
              <button
                v-for="(opt, idx) in moviePrimaryOptions"
                :key="opt.value"
                :ref="(el) => { primaryButtonRefs[idx] = el as HTMLButtonElement | null }"
                @click="emit('primary-change', opt.value)"
                :class="[
                  'relative z-10 px-2 py-1 sm:px-4 sm:py-2 text-xs sm:text-sm font-medium rounded-full transition-all duration-200 whitespace-nowrap',
                  effectivePrimary() === opt.value
                    ? 'text-gray-900 dark:text-gray-100 cursor-default'
                    : 'text-gray-700 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100 cursor-pointer',
                ]"
              >{{ opt.label }}</button>
            </div>
          </div>
        </div>
        <!-- 二级：地区 或 筛选 -->
        <template v-if="effectivePrimary() !== '全部'">
          <div class="flex flex-col sm:flex-row sm:items-center gap-2">
            <span class="text-xs sm:text-sm font-medium text-gray-600 dark:text-gray-400 min-w-[48px]">地区</span>
            <div class="overflow-x-auto">
              <div ref="secondaryContainerRef" class="relative inline-flex bg-gray-200/60 rounded-full p-0.5 sm:p-1 dark:bg-gray-700/60 backdrop-blur-sm">
                <div
                  v-if="secondaryIndicatorStyle.width > 0"
                  class="absolute top-0.5 bottom-0.5 sm:top-1 sm:bottom-1 bg-white dark:bg-gray-500 rounded-full shadow-sm transition-all duration-300 ease-out"
                  :style="{ left: `${secondaryIndicatorStyle.left}px`, width: `${secondaryIndicatorStyle.width}px` }"
                />
                <button
                  v-for="(opt, idx) in movieSecondaryOptions"
                  :key="opt.value"
                  :ref="(el) => { secondaryButtonRefs[idx] = el as HTMLButtonElement | null }"
                  @click="emit('secondary-change', opt.value)"
                  :class="[
                    'relative z-10 px-2 py-1 sm:px-4 sm:py-2 text-xs sm:text-sm font-medium rounded-full transition-all duration-200 whitespace-nowrap',
                    effectiveSecondary() === opt.value
                      ? 'text-gray-900 dark:text-gray-100 cursor-default'
                      : 'text-gray-700 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100 cursor-pointer',
                  ]"
                >{{ opt.label }}</button>
              </div>
            </div>
          </div>
        </template>
        <template v-else>
          <div class="flex flex-col sm:flex-row sm:items-center gap-2">
            <span class="text-xs sm:text-sm font-medium text-gray-600 dark:text-gray-400 min-w-[48px]">筛选</span>
            <div class="overflow-x-auto">
              <MultiLevelSelector :key="`movie-${effectivePrimary()}`" content-type="movie" @change="emit('multi-level-change', $event)" />
            </div>
          </div>
        </template>
      </div>
    </template>

    <!-- 电视剧 -->
    <template v-else-if="type === 'tv'">
      <div class="space-y-3 sm:space-y-4">
        <div class="flex flex-col sm:flex-row sm:items-center gap-2">
          <span class="text-xs sm:text-sm font-medium text-gray-600 dark:text-gray-400 min-w-[48px]">分类</span>
          <div class="overflow-x-auto">
            <div ref="primaryContainerRef" class="relative inline-flex bg-gray-200/60 rounded-full p-0.5 sm:p-1 dark:bg-gray-700/60 backdrop-blur-sm">
              <div
                v-if="primaryIndicatorStyle.width > 0"
                class="absolute top-0.5 bottom-0.5 sm:top-1 sm:bottom-1 bg-white dark:bg-gray-500 rounded-full shadow-sm transition-all duration-300 ease-out"
                :style="{ left: `${primaryIndicatorStyle.left}px`, width: `${primaryIndicatorStyle.width}px` }"
              />
              <button
                v-for="(opt, idx) in tvPrimaryOptions"
                :key="opt.value"
                :ref="(el) => { primaryButtonRefs[idx] = el as HTMLButtonElement | null }"
                @click="emit('primary-change', opt.value)"
                :class="[
                  'relative z-10 px-2 py-1 sm:px-4 sm:py-2 text-xs sm:text-sm font-medium rounded-full transition-all duration-200 whitespace-nowrap',
                  effectivePrimary() === opt.value
                    ? 'text-gray-900 dark:text-gray-100 cursor-default'
                    : 'text-gray-700 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100 cursor-pointer',
                ]"
              >{{ opt.label }}</button>
            </div>
          </div>
        </div>
        <template v-if="effectivePrimary() === '最近热门'">
          <div class="flex flex-col sm:flex-row sm:items-center gap-2">
            <span class="text-xs sm:text-sm font-medium text-gray-600 dark:text-gray-400 min-w-[48px]">类型</span>
            <div class="overflow-x-auto">
              <div ref="secondaryContainerRef" class="relative inline-flex bg-gray-200/60 rounded-full p-0.5 sm:p-1 dark:bg-gray-700/60 backdrop-blur-sm">
                <div
                  v-if="secondaryIndicatorStyle.width > 0"
                  class="absolute top-0.5 bottom-0.5 sm:top-1 sm:bottom-1 bg-white dark:bg-gray-500 rounded-full shadow-sm transition-all duration-300 ease-out"
                  :style="{ left: `${secondaryIndicatorStyle.left}px`, width: `${secondaryIndicatorStyle.width}px` }"
                />
                <button
                  v-for="(opt, idx) in tvSecondaryOptions"
                  :key="opt.value"
                  :ref="(el) => { secondaryButtonRefs[idx] = el as HTMLButtonElement | null }"
                  @click="emit('secondary-change', opt.value)"
                  :class="[
                    'relative z-10 px-2 py-1 sm:px-4 sm:py-2 text-xs sm:text-sm font-medium rounded-full transition-all duration-200 whitespace-nowrap',
                    effectiveSecondary() === opt.value
                      ? 'text-gray-900 dark:text-gray-100 cursor-default'
                      : 'text-gray-700 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100 cursor-pointer',
                  ]"
                >{{ opt.label }}</button>
              </div>
            </div>
          </div>
        </template>
        <template v-else-if="effectivePrimary() === '全部'">
          <div class="flex flex-col sm:flex-row sm:items-center gap-2">
            <span class="text-xs sm:text-sm font-medium text-gray-600 dark:text-gray-400 min-w-[48px]">筛选</span>
            <div class="overflow-x-auto">
              <MultiLevelSelector :key="`tv-${effectivePrimary()}`" content-type="tv" @change="emit('multi-level-change', $event)" />
            </div>
          </div>
        </template>
      </div>
    </template>

    <!-- 综艺 -->
    <template v-else-if="type === 'show'">
      <div class="space-y-3 sm:space-y-4">
        <div class="flex flex-col sm:flex-row sm:items-center gap-2">
          <span class="text-xs sm:text-sm font-medium text-gray-600 dark:text-gray-400 min-w-[48px]">分类</span>
          <div class="overflow-x-auto">
            <div ref="primaryContainerRef" class="relative inline-flex bg-gray-200/60 rounded-full p-0.5 sm:p-1 dark:bg-gray-700/60 backdrop-blur-sm">
              <div
                v-if="primaryIndicatorStyle.width > 0"
                class="absolute top-0.5 bottom-0.5 sm:top-1 sm:bottom-1 bg-white dark:bg-gray-500 rounded-full shadow-sm transition-all duration-300 ease-out"
                :style="{ left: `${primaryIndicatorStyle.left}px`, width: `${primaryIndicatorStyle.width}px` }"
              />
              <button
                v-for="(opt, idx) in showPrimaryOptions"
                :key="opt.value"
                :ref="(el) => { primaryButtonRefs[idx] = el as HTMLButtonElement | null }"
                @click="emit('primary-change', opt.value)"
                :class="[
                  'relative z-10 px-2 py-1 sm:px-4 sm:py-2 text-xs sm:text-sm font-medium rounded-full transition-all duration-200 whitespace-nowrap',
                  effectivePrimary() === opt.value
                    ? 'text-gray-900 dark:text-gray-100 cursor-default'
                    : 'text-gray-700 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100 cursor-pointer',
                ]"
              >{{ opt.label }}</button>
            </div>
          </div>
        </div>
        <template v-if="effectivePrimary() === '最近热门'">
          <div class="flex flex-col sm:flex-row sm:items-center gap-2">
            <span class="text-xs sm:text-sm font-medium text-gray-600 dark:text-gray-400 min-w-[48px]">类型</span>
            <div class="overflow-x-auto">
              <div ref="secondaryContainerRef" class="relative inline-flex bg-gray-200/60 rounded-full p-0.5 sm:p-1 dark:bg-gray-700/60 backdrop-blur-sm">
                <div
                  v-if="secondaryIndicatorStyle.width > 0"
                  class="absolute top-0.5 bottom-0.5 sm:top-1 sm:bottom-1 bg-white dark:bg-gray-500 rounded-full shadow-sm transition-all duration-300 ease-out"
                  :style="{ left: `${secondaryIndicatorStyle.left}px`, width: `${secondaryIndicatorStyle.width}px` }"
                />
                <button
                  v-for="(opt, idx) in showSecondaryOptions"
                  :key="opt.value"
                  :ref="(el) => { secondaryButtonRefs[idx] = el as HTMLButtonElement | null }"
                  @click="emit('secondary-change', opt.value)"
                  :class="[
                    'relative z-10 px-2 py-1 sm:px-4 sm:py-2 text-xs sm:text-sm font-medium rounded-full transition-all duration-200 whitespace-nowrap',
                    effectiveSecondary() === opt.value
                      ? 'text-gray-900 dark:text-gray-100 cursor-default'
                      : 'text-gray-700 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100 cursor-pointer',
                  ]"
                >{{ opt.label }}</button>
              </div>
            </div>
          </div>
        </template>
        <template v-else-if="effectivePrimary() === '全部'">
          <div class="flex flex-col sm:flex-row sm:items-center gap-2">
            <span class="text-xs sm:text-sm font-medium text-gray-600 dark:text-gray-400 min-w-[48px]">筛选</span>
            <div class="overflow-x-auto">
              <MultiLevelSelector :key="`show-${effectivePrimary()}`" content-type="show" @change="emit('multi-level-change', $event)" />
            </div>
          </div>
        </template>
      </div>
    </template>

    <!-- 动漫 -->
    <template v-else-if="type === 'anime'">
      <div class="space-y-3 sm:space-y-4">
        <div class="flex flex-col sm:flex-row sm:items-center gap-2">
          <span class="text-xs sm:text-sm font-medium text-gray-600 dark:text-gray-400 min-w-[48px]">分类</span>
          <div class="overflow-x-auto">
            <div ref="primaryContainerRef" class="relative inline-flex bg-gray-200/60 rounded-full p-0.5 sm:p-1 dark:bg-gray-700/60 backdrop-blur-sm">
              <div
                v-if="primaryIndicatorStyle.width > 0"
                class="absolute top-0.5 bottom-0.5 sm:top-1 sm:bottom-1 bg-white dark:bg-gray-500 rounded-full shadow-sm transition-all duration-300 ease-out"
                :style="{ left: `${primaryIndicatorStyle.left}px`, width: `${primaryIndicatorStyle.width}px` }"
              />
              <button
                v-for="(opt, idx) in animePrimaryOptions"
                :key="opt.value"
                :ref="(el) => { primaryButtonRefs[idx] = el as HTMLButtonElement | null }"
                @click="emit('primary-change', opt.value)"
                :class="[
                  'relative z-10 px-2 py-1 sm:px-4 sm:py-2 text-xs sm:text-sm font-medium rounded-full transition-all duration-200 whitespace-nowrap',
                  effectivePrimary() === opt.value
                    ? 'text-gray-900 dark:text-gray-100 cursor-default'
                    : 'text-gray-700 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100 cursor-pointer',
                ]"
              >{{ opt.label }}</button>
            </div>
          </div>
        </div>
        <!-- 每日放送 → 星期选择器 -->
        <template v-if="effectivePrimary() === '每日放送'">
          <div class="flex flex-col sm:flex-row sm:items-center gap-2">
            <span class="text-xs sm:text-sm font-medium text-gray-600 dark:text-gray-400 min-w-[48px]">星期</span>
            <div class="overflow-x-auto">
              <WeekdaySelector @weekday-change="emit('weekday-change', $event)" />
            </div>
          </div>
        </template>
        <!-- 番剧 / 剧场版 → 多级筛选 -->
        <template v-else>
          <div class="flex flex-col sm:flex-row sm:items-center gap-2">
            <span class="text-xs sm:text-sm font-medium text-gray-600 dark:text-gray-400 min-w-[48px]">筛选</span>
            <div class="overflow-x-auto">
              <MultiLevelSelector
                :key="`anime-${effectivePrimary()}`"
                :content-type="effectivePrimary() === '番剧' ? 'anime-tv' : 'anime-movie'"
                @change="emit('multi-level-change', $event)"
              />
            </div>
          </div>
        </template>
      </div>
    </template>

  </div>
</template>

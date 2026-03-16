<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'

/* eslint-disable no-undef */
declare const window: Window & typeof globalThis

type ContentType = 'movie' | 'tv' | 'show' | 'anime-tv' | 'anime-movie'

interface Option {
  label: string
  value: string
}

interface Category {
  key: string
  label: string
  options: Option[]
}

const props = withDefaults(defineProps<{
  contentType?: ContentType
}>(), {
  contentType: 'movie',
})

const emit = defineEmits<{
  (e: 'change', values: Record<string, string>): void
}>()

const activeCategory = ref<string | null>(null)
const dropdownPosition = ref({ x: 0, y: 0, width: 0 })
const values = ref<Record<string, string>>({})
const categoryRefs = ref<Record<string, HTMLDivElement | null>>({})
const dropdownRef = ref<HTMLDivElement | null>(null)

function getTypeOptions(ct: ContentType): Option[] {
  const base = [{ label: '全部', value: 'all' }]
  switch (ct) {
    case 'movie':
      return [...base,
        { label: '喜剧', value: 'comedy' }, { label: '爱情', value: 'romance' },
        { label: '动作', value: 'action' }, { label: '科幻', value: 'sci-fi' },
        { label: '悬疑', value: 'suspense' }, { label: '犯罪', value: 'crime' },
        { label: '惊悚', value: 'thriller' }, { label: '冒险', value: 'adventure' },
        { label: '音乐', value: 'music' }, { label: '历史', value: 'history' },
        { label: '奇幻', value: 'fantasy' }, { label: '恐怖', value: 'horror' },
        { label: '战争', value: 'war' }, { label: '传记', value: 'biography' },
        { label: '歌舞', value: 'musical' }, { label: '武侠', value: 'wuxia' },
        { label: '情色', value: 'erotic' }, { label: '灾难', value: 'disaster' },
        { label: '西部', value: 'western' }, { label: '纪录片', value: 'documentary' },
        { label: '短片', value: 'short' },
      ]
    case 'tv':
      return [...base,
        { label: '喜剧', value: 'comedy' }, { label: '爱情', value: 'romance' },
        { label: '悬疑', value: 'suspense' }, { label: '武侠', value: 'wuxia' },
        { label: '古装', value: 'costume' }, { label: '家庭', value: 'family' },
        { label: '犯罪', value: 'crime' }, { label: '科幻', value: 'sci-fi' },
        { label: '恐怖', value: 'horror' }, { label: '历史', value: 'history' },
        { label: '战争', value: 'war' }, { label: '动作', value: 'action' },
        { label: '冒险', value: 'adventure' }, { label: '传记', value: 'biography' },
        { label: '剧情', value: 'drama' }, { label: '奇幻', value: 'fantasy' },
        { label: '惊悚', value: 'thriller' }, { label: '灾难', value: 'disaster' },
        { label: '歌舞', value: 'musical' }, { label: '音乐', value: 'music' },
      ]
    case 'show':
      return [...base,
        { label: '真人秀', value: 'reality' }, { label: '脱口秀', value: 'talkshow' },
        { label: '音乐', value: 'music' }, { label: '歌舞', value: 'musical' },
      ]
    default:
      return base
  }
}

function getRegionOptions(ct: ContentType): Option[] {
  const base = [{ label: '全部', value: 'all' }]
  if (ct === 'movie' || ct === 'anime-movie') {
    return [...base,
      { label: '华语', value: 'chinese' }, { label: '欧美', value: 'western' },
      { label: '韩国', value: 'korean' }, { label: '日本', value: 'japanese' },
      { label: '中国大陆', value: 'mainland_china' }, { label: '美国', value: 'usa' },
      { label: '中国香港', value: 'hong_kong' }, { label: '中国台湾', value: 'taiwan' },
      { label: '英国', value: 'uk' }, { label: '法国', value: 'france' },
      { label: '德国', value: 'germany' }, { label: '意大利', value: 'italy' },
      { label: '西班牙', value: 'spain' }, { label: '印度', value: 'india' },
      { label: '泰国', value: 'thailand' }, { label: '俄罗斯', value: 'russia' },
      { label: '加拿大', value: 'canada' }, { label: '澳大利亚', value: 'australia' },
      { label: '爱尔兰', value: 'ireland' }, { label: '瑞典', value: 'sweden' },
      { label: '巴西', value: 'brazil' }, { label: '丹麦', value: 'denmark' },
    ]
  }
  return [...base,
    { label: '华语', value: 'chinese' }, { label: '欧美', value: 'western' },
    { label: '国外', value: 'foreign' }, { label: '韩国', value: 'korean' },
    { label: '日本', value: 'japanese' }, { label: '中国大陆', value: 'mainland_china' },
    { label: '中国香港', value: 'hong_kong' }, { label: '美国', value: 'usa' },
    { label: '英国', value: 'uk' }, { label: '泰国', value: 'thailand' },
    { label: '中国台湾', value: 'taiwan' }, { label: '意大利', value: 'italy' },
    { label: '法国', value: 'france' }, { label: '德国', value: 'germany' },
    { label: '西班牙', value: 'spain' }, { label: '俄罗斯', value: 'russia' },
    { label: '瑞典', value: 'sweden' }, { label: '巴西', value: 'brazil' },
    { label: '丹麦', value: 'denmark' }, { label: '印度', value: 'india' },
    { label: '加拿大', value: 'canada' }, { label: '爱尔兰', value: 'ireland' },
    { label: '澳大利亚', value: 'australia' },
  ]
}

function getLabelOptions(ct: ContentType): Option[] {
  const base = [{ label: '全部', value: 'all' }]
  if (ct === 'anime-movie') {
    return [...base,
      { label: '定格动画', value: 'stop_motion' }, { label: '传记', value: 'biography' },
      { label: '美国动画', value: 'us_animation' }, { label: '爱情', value: 'romance' },
      { label: '黑色幽默', value: 'dark_humor' }, { label: '歌舞', value: 'musical' },
      { label: '儿童', value: 'children' }, { label: '二次元', value: 'anime' },
      { label: '动物', value: 'animal' }, { label: '青春', value: 'youth' },
      { label: '历史', value: 'history' }, { label: '励志', value: 'inspirational' },
      { label: '恶搞', value: 'parody' }, { label: '治愈', value: 'healing' },
      { label: '运动', value: 'sports' }, { label: '后宫', value: 'harem' },
      { label: '情色', value: 'erotic' }, { label: '人性', value: 'human_nature' },
      { label: '悬疑', value: 'suspense' }, { label: '恋爱', value: 'love' },
      { label: '魔幻', value: 'fantasy' }, { label: '科幻', value: 'sci_fi' },
    ]
  }
  if (ct === 'anime-tv') {
    return [...base,
      { label: '黑色幽默', value: 'dark_humor' }, { label: '历史', value: 'history' },
      { label: '歌舞', value: 'musical' }, { label: '励志', value: 'inspirational' },
      { label: '恶搞', value: 'parody' }, { label: '治愈', value: 'healing' },
      { label: '运动', value: 'sports' }, { label: '后宫', value: 'harem' },
      { label: '情色', value: 'erotic' }, { label: '国漫', value: 'chinese_anime' },
      { label: '人性', value: 'human_nature' }, { label: '悬疑', value: 'suspense' },
      { label: '恋爱', value: 'love' }, { label: '魔幻', value: 'fantasy' },
      { label: '科幻', value: 'sci_fi' },
    ]
  }
  return base
}

function getPlatformOptions(ct: ContentType): Option[] {
  const base = [{ label: '全部', value: 'all' }]
  if (ct === 'tv' || ct === 'anime-tv' || ct === 'show') {
    return [...base,
      { label: '腾讯视频', value: 'tencent' }, { label: '爱奇艺', value: 'iqiyi' },
      { label: '优酷', value: 'youku' }, { label: '湖南卫视', value: 'hunan_tv' },
      { label: 'Netflix', value: 'netflix' }, { label: 'HBO', value: 'hbo' },
      { label: 'BBC', value: 'bbc' }, { label: 'NHK', value: 'nhk' },
      { label: 'CBS', value: 'cbs' }, { label: 'NBC', value: 'nbc' },
      { label: 'tvN', value: 'tvn' },
    ]
  }
  return base
}

const categories = computed<Category[]>(() => {
  const ct = props.contentType
  const isAnime = ct === 'anime-tv' || ct === 'anime-movie'
  return [
    isAnime
      ? { key: 'label', label: '类型', options: getLabelOptions(ct) }
      : { key: 'type', label: '类型', options: getTypeOptions(ct) },
    { key: 'region', label: '地区', options: getRegionOptions(ct) },
    {
      key: 'year', label: '年代', options: [
        { label: '全部', value: 'all' },
        { label: '2020年代', value: '2020s' },
        { label: '2025', value: '2025' }, { label: '2024', value: '2024' },
        { label: '2023', value: '2023' }, { label: '2022', value: '2022' },
        { label: '2021', value: '2021' }, { label: '2020', value: '2020' },
        { label: '2019', value: '2019' }, { label: '2010年代', value: '2010s' },
        { label: '2000年代', value: '2000s' }, { label: '90年代', value: '1990s' },
        { label: '80年代', value: '1980s' }, { label: '70年代', value: '1970s' },
        { label: '60年代', value: '1960s' }, { label: '更早', value: 'earlier' },
      ],
    },
    ...(ct === 'tv' || ct === 'show' || ct === 'anime-tv'
      ? [{ key: 'platform', label: '平台', options: getPlatformOptions(ct) }]
      : []),
    {
      key: 'sort', label: '排序', options: [
        { label: '综合排序', value: 'T' },
        { label: '近期热度', value: 'U' },
        {
          label: ct === 'tv' || ct === 'show' ? '首播时间' : '首映时间',
          value: 'R',
        },
        { label: '高分优先', value: 'S' },
      ],
    },
  ]
})

function calculateDropdownPosition(key: string) {
  const el = categoryRefs.value[key]
  if (!el) return
  const rect = el.getBoundingClientRect()
  const isMobile = window.innerWidth < 768
  let x = rect.left
  let width = Math.max(rect.width, 300)

  if (isMobile) {
    const padding = 16
    const maxWidth = window.innerWidth - padding * 2
    width = Math.min(width, maxWidth)
    if (x + width > window.innerWidth - padding) x = window.innerWidth - width - padding
    if (x < padding) x = padding
  }

  dropdownPosition.value = { x, y: rect.bottom, width }
}

function handleCategoryClick(key: string) {
  if (activeCategory.value === key) {
    activeCategory.value = null
  } else {
    activeCategory.value = key
    calculateDropdownPosition(key)
  }
}

function handleOptionSelect(categoryKey: string, optionValue: string) {
  values.value = { ...values.value, [categoryKey]: optionValue }

  const result: Record<string, string> = {
    type: 'all', region: 'all', year: 'all',
    platform: 'all', label: 'all', sort: 'T',
  }

  Object.entries(values.value).forEach(([key, val]) => {
    if (val && val !== 'all' && !(key === 'sort' && val === 'T')) {
      const cat = categories.value.find(c => c.key === key)
      if (cat) {
        const opt = cat.options.find(o => o.value === val)
        if (opt) {
          result[key] = key === 'sort' ? opt.value : opt.label
        }
      }
    }
  })

  emit('change', result)
  activeCategory.value = null
}

function getDisplayText(key: string): string {
  const cat = categories.value.find(c => c.key === key)
  if (!cat) return ''
  const val = values.value[key]
  if (!val || val === 'all' || (key === 'sort' && val === 'T')) return cat.label
  return cat.options.find(o => o.value === val)?.label || cat.label
}

function isDefault(key: string): boolean {
  const val = values.value[key]
  return !val || val === 'all' || (key === 'sort' && val === 'T')
}

function isSelected(key: string, optVal: string): boolean {
  const val = values.value[key]
  if (val === undefined) return optVal === 'all' || (key === 'sort' && optVal === 'T')
  return val === optVal
}

function getActiveOptions(): Option[] {
  if (!activeCategory.value) return []
  return categories.value.find(c => c.key === activeCategory.value)?.options || []
}

function handleScroll() {
  if (activeCategory.value) activeCategory.value = null
}

function handleResize() {
  if (activeCategory.value) calculateDropdownPosition(activeCategory.value)
}

function handleClickOutside(e: MouseEvent) {
  const target = e.target as Node
  const isInDropdown = dropdownRef.value?.contains(target)
  const isInCategory = Object.values(categoryRefs.value).some(el => el?.contains(target))
  if (!isInDropdown && !isInCategory) activeCategory.value = null
}

onMounted(() => {
  document.body.addEventListener('scroll', handleScroll, { passive: true })
  window.addEventListener('resize', handleResize)
  document.addEventListener('mousedown', handleClickOutside)
})

onBeforeUnmount(() => {
  document.body.removeEventListener('scroll', handleScroll)
  window.removeEventListener('resize', handleResize)
  document.removeEventListener('mousedown', handleClickOutside)
})
</script>

<template>
  <div class="relative inline-flex rounded-full p-0.5 sm:p-1 bg-transparent gap-1 sm:gap-2">
    <div
      v-for="cat in categories"
      :key="cat.key"
      :ref="(el) => { categoryRefs[cat.key] = el as HTMLDivElement | null }"
      class="relative"
    >
      <button
        @click="handleCategoryClick(cat.key)"
        :class="[
          'relative z-10 px-1.5 py-0.5 sm:px-2 sm:py-1 md:px-4 md:py-2 text-xs sm:text-sm font-medium rounded-full transition-all duration-200 whitespace-nowrap',
          activeCategory === cat.key
            ? isDefault(cat.key)
              ? 'text-gray-900 dark:text-gray-100 cursor-default'
              : 'text-blue-600 dark:text-blue-400 cursor-default'
            : isDefault(cat.key)
              ? 'text-gray-700 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100 cursor-pointer'
              : 'text-blue-600 hover:text-blue-700 dark:text-blue-400 dark:hover:text-blue-300 cursor-pointer',
        ]"
      >
        <span>{{ getDisplayText(cat.key) }}</span>
        <svg
          :class="['inline-block w-2.5 h-2.5 sm:w-3 sm:h-3 ml-0.5 sm:ml-1 transition-transform duration-200', activeCategory === cat.key ? 'rotate-180' : '']"
          fill="none" stroke="currentColor" viewBox="0 0 24 24"
        >
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
        </svg>
      </button>
    </div>
  </div>

  <!-- 悬浮下拉框 -->
  <Teleport to="body">
    <div
      v-if="activeCategory"
      ref="dropdownRef"
      class="fixed z-[9999] bg-white/95 dark:bg-gray-800/95 rounded-xl border border-gray-200/50 dark:border-gray-700/50 backdrop-blur-sm"
      :style="{
        left: `${dropdownPosition.x}px`,
        top: `${dropdownPosition.y}px`,
        ...(typeof window !== 'undefined' && window.innerWidth < 768
          ? { width: `${dropdownPosition.width}px` }
          : { minWidth: `${Math.max(dropdownPosition.width, 300)}px` }),
        maxWidth: '600px',
        position: 'fixed',
      }"
    >
      <div class="p-2 sm:p-4">
        <div class="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 gap-1 sm:gap-2">
          <button
            v-for="opt in getActiveOptions()"
            :key="opt.value"
            @click="handleOptionSelect(activeCategory!, opt.value)"
            :class="[
              'px-2 py-1.5 sm:px-3 sm:py-2 text-xs sm:text-sm rounded-lg transition-all duration-200 text-left',
              isSelected(activeCategory!, opt.value)
                ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400 border border-blue-200 dark:border-blue-700'
                : 'text-gray-700 dark:text-gray-300 hover:bg-gray-100/80 dark:hover:bg-gray-700/80',
            ]"
          >
            {{ opt.label }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

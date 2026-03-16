<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ArrowUpDown, ArrowDownWideNarrow, ArrowUpNarrowWide } from 'lucide-vue-next'

export type SearchFilterKey = 'source' | 'title' | 'year' | 'yearOrder'

export interface SearchFilterOption {
  label: string
  value: string
}

export interface SearchFilterCategory {
  key: SearchFilterKey
  label: string
  options: SearchFilterOption[]
}

interface Props {
  categories: SearchFilterCategory[]
  values: Partial<Record<SearchFilterKey, string>>
}

const props = defineProps<Props>()
const emit = defineEmits<{
  change: [values: Record<SearchFilterKey, string>]
}>()

const DEFAULTS: Record<SearchFilterKey, string> = {
  source: 'all',
  title: 'all',
  year: 'all',
  yearOrder: 'none',
}

const activeCategory = ref<SearchFilterKey | null>(null)
const dropdownPosition = ref({ x: 0, y: 0, width: 0 })
const categoryRefs = ref<Record<string, HTMLDivElement | null>>({})
const dropdownRef = ref<HTMLDivElement | null>(null)

const mergedValues = computed(() => {
  return {
    ...DEFAULTS,
    ...props.values,
  } as Record<SearchFilterKey, string>
})

function calculateDropdownPosition(categoryKey: SearchFilterKey) {
  const element = categoryRefs.value[categoryKey]
  if (element) {
    const rect = element.getBoundingClientRect()
    const viewportWidth = window.innerWidth
    const isMobile = viewportWidth < 768

    let x = rect.left
    // 为标题筛选设置更大的最小宽度，其他保持原来的最小宽度
    const minWidth = categoryKey === 'title' ? 400 : 240
    let dropdownWidth = Math.max(rect.width, minWidth)
    let useFixedWidth = false

    if (isMobile) {
      const padding = 16
      const maxWidth = viewportWidth - padding * 2
      dropdownWidth = Math.min(dropdownWidth, maxWidth)
      useFixedWidth = true

      if (x + dropdownWidth > viewportWidth - padding) {
        x = viewportWidth - dropdownWidth - padding
      }
      if (x < padding) {
        x = padding
      }
    }

    dropdownPosition.value = { x, y: rect.bottom, width: useFixedWidth ? dropdownWidth : rect.width }
  }
}

function handleCategoryClick(categoryKey: SearchFilterKey) {
  if (activeCategory.value === categoryKey) {
    activeCategory.value = null
  } else {
    activeCategory.value = categoryKey
    calculateDropdownPosition(categoryKey)
  }
}

function handleOptionSelect(categoryKey: SearchFilterKey, optionValue: string) {
  const newValues = {
    ...mergedValues.value,
    [categoryKey]: optionValue,
  } as Record<SearchFilterKey, string>
  emit('change', newValues)
  activeCategory.value = null
}

function getDisplayText(categoryKey: SearchFilterKey) {
  const category = props.categories.find((cat) => cat.key === categoryKey)
  if (!category) return ''
  const value = mergedValues.value[categoryKey]
  if (!value || value === DEFAULTS[categoryKey]) return category.label
  const option = category.options.find((opt) => opt.value === value)
  return option?.label || category.label
}

function isDefaultValue(categoryKey: SearchFilterKey) {
  const value = mergedValues.value[categoryKey]
  return !value || value === DEFAULTS[categoryKey]
}

function isOptionSelected(categoryKey: SearchFilterKey, optionValue: string) {
  const value = mergedValues.value[categoryKey] ?? DEFAULTS[categoryKey]
  return value === optionValue
}

function handleYearOrderClick() {
  let next: string
  switch (mergedValues.value.yearOrder) {
    case 'none':
      next = 'desc'
      break
    case 'desc':
      next = 'asc'
      break
    case 'asc':
      next = 'none'
      break
    default:
      next = 'desc'
  }
  emit('change', { ...mergedValues.value, yearOrder: next })
}

function handleScroll() {
  if (activeCategory.value) {
    activeCategory.value = null
  }
}

function handleResize() {
  if (activeCategory.value) {
    calculateDropdownPosition(activeCategory.value)
  }
}

function handleClickOutside(event: MouseEvent) {
  if (
    dropdownRef.value &&
    !dropdownRef.value.contains(event.target as Node) &&
    !Object.values(categoryRefs.value).some((ref) => ref && ref.contains(event.target as Node))
  ) {
    activeCategory.value = null
  }
}

onMounted(() => {
  document.body.addEventListener('scroll', handleScroll, { passive: true })
  window.addEventListener('scroll', handleScroll, { passive: true })
  window.addEventListener('resize', handleResize)
  document.addEventListener('mousedown', handleClickOutside)
})

onUnmounted(() => {
  document.body.removeEventListener('scroll', handleScroll)
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('resize', handleResize)
  document.removeEventListener('mousedown', handleClickOutside)
})

const dropdownStyle = computed(() => {
  const isMobile = typeof window !== 'undefined' && window.innerWidth < 768
  return {
    left: `${dropdownPosition.value.x}px`,
    top: `${dropdownPosition.value.y}px`,
    ...(isMobile
      ? { width: `${dropdownPosition.value.width}px` }
      : { minWidth: `${Math.max(dropdownPosition.value.width, activeCategory.value === 'title' ? 400 : 240)}px` }),
    maxWidth: '600px',
    position: 'fixed' as const,
  }
})
</script>

<template>
  <div class="relative inline-flex rounded-full p-0.5 sm:p-1 bg-transparent gap-1 sm:gap-2">
    <div
      v-for="category in categories"
      :key="category.key"
      :ref="(el) => { categoryRefs[category.key] = el as HTMLDivElement }"
      class="relative"
    >
      <button
        @click="handleCategoryClick(category.key)"
        :class="[
          'relative z-10 px-1.5 py-0.5 sm:px-2 sm:py-1 md:px-4 md:py-2 text-xs sm:text-sm font-medium rounded-full transition-all duration-200 whitespace-nowrap',
          activeCategory === category.key
            ? isDefaultValue(category.key)
              ? 'text-gray-900 dark:text-gray-100 cursor-default'
              : 'text-blue-600 dark:text-blue-400 cursor-default'
            : isDefaultValue(category.key)
              ? 'text-gray-700 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100 cursor-pointer'
              : 'text-blue-600 hover:text-blue-700 dark:text-blue-400 dark:hover:text-blue-300 cursor-pointer',
        ]"
      >
        <span>{{ getDisplayText(category.key) }}</span>
        <svg
          :class="[
            'inline-block w-2.5 h-2.5 sm:w-3 sm:h-3 ml-0.5 sm:ml-1 transition-transform duration-200',
            activeCategory === category.key ? 'rotate-180' : '',
          ]"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
        </svg>
      </button>
    </div>

    <!-- 年份排序切换按钮 -->
    <div class="relative">
      <button
        @click="handleYearOrderClick"
        :class="[
          'relative z-10 px-1.5 py-0.5 sm:px-2 sm:py-1 md:px-4 md:py-2 text-xs sm:text-sm font-medium rounded-full transition-all duration-200 whitespace-nowrap',
          mergedValues.yearOrder === 'none'
            ? 'text-gray-700 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100 cursor-pointer'
            : 'text-blue-600 hover:text-blue-700 dark:text-blue-400 dark:hover:text-blue-300 cursor-pointer',
        ]"
        :aria-label="`按年份${mergedValues.yearOrder === 'none' ? '排序' : mergedValues.yearOrder === 'desc' ? '降序' : '升序'}排序`"
      >
        <span>年份</span>
        <ArrowUpDown
          v-if="mergedValues.yearOrder === 'none'"
          class="inline-block ml-1 w-4 h-4 sm:w-4 sm:h-4"
        />
        <ArrowDownWideNarrow
          v-else-if="mergedValues.yearOrder === 'desc'"
          class="inline-block ml-1 w-4 h-4 sm:w-4 sm:h-4"
        />
        <ArrowUpNarrowWide v-else class="inline-block ml-1 w-4 h-4 sm:w-4 sm:h-4" />
      </button>
    </div>
  </div>

  <!-- 下拉面板 -->
  <Teleport to="body">
    <div
      v-if="activeCategory"
      ref="dropdownRef"
      class="fixed z-[9999] bg-white/95 dark:bg-gray-800/95 rounded-xl border border-gray-200/50 dark:border-gray-700/50 backdrop-blur-sm max-h-[50vh] flex flex-col shadow-lg"
      :style="dropdownStyle"
    >
      <div class="p-2 sm:p-4 overflow-y-auto flex-1 min-h-0">
        <div class="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 gap-1 sm:gap-2">
          <button
            v-for="option in categories.find((cat) => cat.key === activeCategory)?.options"
            :key="option.value"
            @click="handleOptionSelect(activeCategory!, option.value)"
            :class="[
              'px-2 py-1.5 sm:px-3 sm:py-2 text-xs sm:text-sm rounded-lg transition-all duration-200 text-left',
              isOptionSelected(activeCategory!, option.value)
                ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400 border border-blue-200 dark:border-blue-700'
                : 'text-gray-700 dark:text-gray-300 hover:bg-gray-100/80 dark:hover:bg-gray-700/80',
            ]"
          >
            {{ option.label }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

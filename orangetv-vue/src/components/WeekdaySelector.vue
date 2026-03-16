<script setup lang="ts">
import { ref, onMounted } from 'vue'

const emit = defineEmits<{
  (e: 'weekday-change', weekday: string): void
}>()

const weekdays = [
  { value: 'Mon', label: '周一' },
  { value: 'Tue', label: '周二' },
  { value: 'Wed', label: '周三' },
  { value: 'Thu', label: '周四' },
  { value: 'Fri', label: '周五' },
  { value: 'Sat', label: '周六' },
  { value: 'Sun', label: '周日' },
]

function getTodayWeekday(): string {
  const today = new Date().getDay()
  const map = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
  return map[today]
}

const selectedWeekday = ref(getTodayWeekday())

function handleSelect(value: string) {
  selectedWeekday.value = value
  emit('weekday-change', value)
}

onMounted(() => {
  emit('weekday-change', getTodayWeekday())
})
</script>

<template>
  <div class="relative inline-flex rounded-full p-0.5 sm:p-1">
    <button
      v-for="day in weekdays"
      :key="day.value"
      @click="handleSelect(day.value)"
      :class="[
        'relative z-10 px-2 py-1 sm:px-4 sm:py-2 text-xs sm:text-sm font-medium rounded-full transition-all duration-200 whitespace-nowrap',
        selectedWeekday === day.value
          ? 'text-blue-600 dark:text-blue-400 font-semibold'
          : 'text-gray-600 hover:text-gray-800 dark:text-gray-400 dark:hover:text-gray-200 cursor-pointer',
      ]"
    >
      {{ day.label }}
    </button>
  </div>
</template>

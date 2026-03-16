<script setup lang="ts">
import { useThemeStore } from '@/stores/theme'
import { Sun, Moon } from 'lucide-vue-next'

const themeStore = useThemeStore()
</script>

<template>
  <button
    @click="themeStore.toggle"
    class="w-10 h-10 p-2 rounded-full flex items-center justify-center text-gray-600 hover:bg-gray-200/50 dark:text-gray-300 dark:hover:bg-gray-700/50 transition-colors"
    aria-label="Toggle theme"
  >
    <Sun v-if="themeStore.isDark" class="w-full h-full" />
    <Moon v-else class="w-full h-full" />
  </button>
</template>

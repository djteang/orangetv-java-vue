<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ChevronLeft } from 'lucide-vue-next'

const router = useRouter()

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/')
  }
}
</script>

<template>
  <button
    @click="goBack"
    class="flex items-center justify-center w-8 h-8 rounded-lg text-gray-600 hover:text-gray-800 hover:bg-gray-100/50 transition-colors dark:text-gray-400 dark:hover:text-gray-200 dark:hover:bg-gray-700/50"
    title="返回"
  >
    <ChevronLeft class="w-5 h-5" />
  </button>
</template>

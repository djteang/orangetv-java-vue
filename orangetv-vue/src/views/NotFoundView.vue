<script setup lang="ts">
import { Home } from 'lucide-vue-next'
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900 px-4">
    <div class="text-center">
      <h1 class="text-6xl font-bold text-gray-300 dark:text-gray-700 mb-4">404</h1>
      <h2 class="text-2xl font-bold text-gray-800 dark:text-white mb-2">页面不存在</h2>
      <p class="text-gray-500 dark:text-gray-400 mb-6">
        您访问的页面可能已被删除或移动
      </p>
      <router-link
        to="/"
        class="inline-flex items-center gap-2 px-6 py-3 rounded-lg bg-blue-600 hover:bg-blue-700 text-white font-medium transition-colors"
      >
        <Home class="w-5 h-5" />
        返回首页
      </router-link>
    </div>
  </div>
</template>

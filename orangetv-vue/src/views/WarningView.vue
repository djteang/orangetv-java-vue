<script setup lang="ts">
import { AlertTriangle } from 'lucide-vue-next'
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900 px-4">
    <div class="text-center">
      <AlertTriangle class="w-16 h-16 mx-auto text-yellow-500 mb-4" />
      <h1 class="text-2xl font-bold text-gray-800 dark:text-white mb-2">警告</h1>
      <p class="text-gray-500 dark:text-gray-400 mb-6">
        本站仅供学习交流使用，请勿用于商业用途
      </p>
      <router-link
        to="/"
        class="inline-block px-6 py-3 rounded-lg bg-blue-600 hover:bg-blue-700 text-white font-medium transition-colors"
      >
        返回首页
      </router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useToast, removeToast } from '@/composables/useToast'
import { CheckCircle, XCircle, AlertTriangle, Info, X } from 'lucide-vue-next'

const { toasts } = useToast()

const icons = {
  success: CheckCircle,
  error: XCircle,
  warning: AlertTriangle,
  info: Info,
}

const colors = {
  success: 'bg-green-500',
  error: 'bg-red-500',
  warning: 'bg-yellow-500',
  info: 'bg-blue-500',
}
</script>

<template>
  <div class="fixed top-4 right-4 z-[9999] flex flex-col gap-2">
    <TransitionGroup name="toast">
      <div
        v-for="toast in toasts"
        :key="toast.id"
        class="flex items-center gap-3 px-4 py-3 rounded-lg shadow-lg bg-white dark:bg-gray-800 min-w-[280px] max-w-[400px]"
      >
        <component
          :is="icons[toast.type]"
          :class="['w-5 h-5 flex-shrink-0', colors[toast.type].replace('bg-', 'text-')]"
        />
        <span class="flex-1 text-sm text-gray-700 dark:text-gray-200">{{ toast.message }}</span>
        <button
          @click="removeToast(toast.id)"
          class="p-1 rounded hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
        >
          <X class="w-4 h-4 text-gray-400" />
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-enter-active {
  animation: slide-in-from-right 0.3s ease-out;
}

.toast-leave-active {
  animation: slide-out-to-right 0.2s ease-in;
}

@keyframes slide-in-from-right {
  from {
    transform: translateX(100%);
    opacity: 0;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}

@keyframes slide-out-to-right {
  from {
    transform: translateX(0);
    opacity: 1;
  }
  to {
    transform: translateX(100%);
    opacity: 0;
  }
}
</style>

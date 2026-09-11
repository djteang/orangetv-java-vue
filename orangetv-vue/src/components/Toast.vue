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
  <Teleport to="body">
    <div class="fixed top-4 right-4 z-[9999] flex w-[calc(100%-2rem)] flex-col gap-2 pointer-events-none sm:w-auto" aria-label="消息提示">
      <TransitionGroup name="toast">
        <div
          v-for="toast in toasts"
          :key="toast.id"
          :role="toast.type === 'error' ? 'alert' : 'status'"
          aria-atomic="true"
          class="pointer-events-auto flex w-full items-center gap-3 px-4 py-3 rounded-lg shadow-lg bg-white dark:bg-gray-800 sm:min-w-[280px] sm:max-w-[400px]"
        >
          <component
            :is="icons[toast.type]"
            :class="['w-5 h-5 flex-shrink-0', colors[toast.type].replace('bg-', 'text-')]"
          />
          <span class="min-w-0 flex-1 break-words text-sm text-gray-700 dark:text-gray-200">{{ toast.message }}</span>
          <button
            type="button"
            aria-label="关闭提示"
            @click="removeToast(toast.id)"
            class="flex-shrink-0 p-1 rounded hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
          >
            <X class="w-4 h-4 text-gray-400" />
          </button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
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

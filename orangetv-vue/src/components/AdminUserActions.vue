<script setup lang="ts">
import { Ban, History, KeyRound, Shield, Trash2, UserCheck } from 'lucide-vue-next'
import type { User } from '@/types'

const props = defineProps<{
  user: User
  viewerRole: 'owner' | 'admin' | null
  loadingKeys: Set<string>
  refreshing: boolean
}>()

const emit = defineEmits<{
  history: [username: string]
  ban: [username: string]
  unban: [username: string]
  setAdmin: [username: string]
  cancelAdmin: [username: string]
  changePassword: [username: string]
  delete: [username: string]
}>()

function isBusy(action: string) {
  return props.refreshing || props.loadingKeys.has(`${action}_${props.user.username}`)
}
</script>

<template>
  <div class="flex flex-wrap items-center gap-2 lg:justify-end">
    <button type="button" :disabled="refreshing" class="inline-flex min-h-10 items-center rounded-lg border border-theme-accent/20 bg-theme-accent/10 px-3 py-2 text-xs font-medium text-theme-accent transition-colors hover:bg-theme-accent/20 focus-visible:outline focus-visible:outline-2 focus-visible:outline-theme-accent disabled:cursor-not-allowed disabled:opacity-50" @click="emit('history', user.username)">
      <History class="mr-1 h-3.5 w-3.5" aria-hidden="true" />查看记录
    </button>
    <template v-if="user.role !== 'owner'">
      <button v-if="!user.banned" type="button" :disabled="isBusy('ban')" class="inline-flex min-h-10 items-center rounded-lg bg-yellow-100 px-3 py-2 text-xs font-medium text-yellow-800 transition-colors hover:bg-yellow-200 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-yellow-900/40 dark:text-yellow-200 dark:hover:bg-yellow-900/60" @click="emit('ban', user.username)">
        <Ban class="mr-1 h-3.5 w-3.5" aria-hidden="true" />封禁
      </button>
      <button v-else type="button" :disabled="isBusy('unban')" class="inline-flex min-h-10 items-center rounded-lg bg-green-100 px-3 py-2 text-xs font-medium text-green-800 transition-colors hover:bg-green-200 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-green-900/40 dark:text-green-200 dark:hover:bg-green-900/60" @click="emit('unban', user.username)">
        <UserCheck class="mr-1 h-3.5 w-3.5" aria-hidden="true" />解封
      </button>
      <template v-if="viewerRole === 'owner'">
        <button v-if="user.role === 'user'" type="button" :disabled="isBusy('setAdmin')" class="inline-flex min-h-10 items-center rounded-lg bg-blue-100 px-3 py-2 text-xs font-medium text-blue-800 transition-colors hover:bg-blue-200 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-blue-900/40 dark:text-blue-200 dark:hover:bg-blue-900/60" @click="emit('setAdmin', user.username)">
          <Shield class="mr-1 h-3.5 w-3.5" aria-hidden="true" />设为管理员
        </button>
        <button v-if="user.role === 'admin'" type="button" :disabled="isBusy('cancelAdmin')" class="inline-flex min-h-10 items-center rounded-lg bg-gray-100 px-3 py-2 text-xs font-medium text-gray-800 transition-colors hover:bg-gray-200 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-gray-700/40 dark:text-gray-200 dark:hover:bg-gray-700/60" @click="emit('cancelAdmin', user.username)">
          <Shield class="mr-1 h-3.5 w-3.5" aria-hidden="true" />取消管理员
        </button>
      </template>
      <button type="button" :disabled="refreshing" class="inline-flex min-h-10 items-center rounded-lg bg-blue-100 px-3 py-2 text-xs font-medium text-blue-800 transition-colors hover:bg-blue-200 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-blue-900/40 dark:text-blue-200 dark:hover:bg-blue-900/60" @click="emit('changePassword', user.username)">
        <KeyRound class="mr-1 h-3.5 w-3.5" aria-hidden="true" />改密
      </button>
      <button type="button" :disabled="refreshing || loadingKeys.has('deleteUser')" class="inline-flex min-h-10 items-center rounded-lg bg-red-100 px-3 py-2 text-xs font-medium text-red-800 transition-colors hover:bg-red-200 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-red-900/40 dark:text-red-200 dark:hover:bg-red-900/60" @click="emit('delete', user.username)">
        <Trash2 class="mr-1 h-3.5 w-3.5" aria-hidden="true" />删除
      </button>
    </template>
    <span v-else class="text-xs text-theme-text-secondary">站长账户</span>
  </div>
</template>

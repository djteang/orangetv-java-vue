<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight, Search } from 'lucide-vue-next'
import { formatDateTime } from '@/utils/datetime'
import type { User } from '@/types'
import { usePagination } from '@/composables/usePagination'
import AdminUserActions from './AdminUserActions.vue'
import AdminUserHistoryDialog from './AdminUserHistoryDialog.vue'
import ThemeSelect from './ThemeSelect.vue'

const props = defineProps<{
  users: User[]
  viewerRole: 'owner' | 'admin' | null
  loadingKeys: Set<string>
  refreshing: boolean
}>()

const emit = defineEmits<{
  ban: [username: string]
  unban: [username: string]
  setAdmin: [username: string]
  cancelAdmin: [username: string]
  changePassword: [username: string]
  delete: [username: string]
}>()

const selectedHistoryUser = ref<User | null>(null)
const fieldId = useId()
const pageSizeOptions = [10, 20, 50].map(value => ({ value, label: `${value} 条` }))
const searchTerm = ref('')
const query = computed(() => searchTerm.value.trim().toLocaleLowerCase())
const filteredUsers = computed(() => props.users.filter(user => user.username.toLocaleLowerCase().includes(query.value)))
const { currentPage, pageSize, totalItems, totalPages, pageItems, rangeStart, rangeEnd, goToPage, resetPage } = usePagination(filteredUsers)

const pageNumbers = computed(() => {
  const start = Math.max(1, Math.min(currentPage.value - 2, totalPages.value - 4))
  return Array.from({ length: Math.min(5, totalPages.value) }, (_, index) => start + index)
})

watch(query, resetPage, { flush: 'sync' })

const actionHandlers = {
  history: (username: string) => { selectedHistoryUser.value = props.users.find(user => user.username === username) || null },
  ban: (username: string) => emit('ban', username),
  unban: (username: string) => emit('unban', username),
  setAdmin: (username: string) => emit('setAdmin', username),
  cancelAdmin: (username: string) => emit('cancelAdmin', username),
  changePassword: (username: string) => emit('changePassword', username),
  delete: (username: string) => emit('delete', username),
}

function roleLabel(role: User['role']) {
  return role === 'owner' ? '站长' : role === 'admin' ? '管理员' : '用户'
}

function roleClass(role: User['role']) {
  return role === 'owner'
    ? 'bg-purple-100 text-purple-800 dark:bg-purple-900/40 dark:text-purple-200'
    : role === 'admin'
      ? 'bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-200'
      : 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-200'
}

function statusClass(banned: boolean) {
  return banned
    ? 'bg-red-100 text-red-800 dark:bg-red-900/40 dark:text-red-200'
    : 'bg-green-100 text-green-800 dark:bg-green-900/40 dark:text-green-200'
}

function formatDate(value?: string) {
  return value ? formatDateTime(value) : '从未登录'
}
</script>

<template>
  <div class="min-w-0" :aria-busy="refreshing">
    <div class="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <div class="relative w-full sm:max-w-xs">
        <label :for="`user-search-${fieldId}`" class="sr-only">搜索用户名</label>
        <Search class="pointer-events-none absolute left-3 top-3 h-4 w-4 text-theme-text-secondary" aria-hidden="true" />
        <input :id="`user-search-${fieldId}`" v-model="searchTerm" type="search" placeholder="搜索用户名" class="h-10 w-full rounded-lg border border-theme-border bg-theme-surface pl-9 pr-3 text-sm text-theme-text focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500" />
      </div>
      <div class="flex shrink-0 items-center gap-2 text-sm text-theme-text-secondary">
        <span v-if="refreshing" role="status" class="mr-auto">更新中...</span>
        <label :for="`user-page-size-${fieldId}`">每页</label>
        <ThemeSelect :id="`user-page-size-${fieldId}`" v-model="pageSize" :options="pageSizeOptions" label="每页用户数量" :disabled="refreshing" class="w-24" />
      </div>
    </div>

    <template v-if="pageItems.length">
      <!-- 宽屏表格 -->
      <div class="hidden overflow-x-auto lg:block">
        <table class="w-full text-sm" aria-label="用户列表">
          <thead>
            <tr class="border-b border-theme-border text-left text-theme-text-secondary">
              <th scope="col" class="px-2 py-3 font-medium">用户</th>
              <th scope="col" class="px-2 py-3 font-medium">角色</th>
              <th scope="col" class="px-2 py-3 font-medium">状态</th>
              <th scope="col" class="px-2 py-3 font-medium">设备码</th>
              <th scope="col" class="px-2 py-3 font-medium">最后登录</th>
              <th scope="col" class="px-2 py-3 text-right font-medium">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in pageItems" :key="user.id" class="border-b border-theme-border hover:bg-gray-50 dark:hover:bg-gray-800/50">
              <td class="max-w-[200px] px-2 py-3">
                <div class="flex min-w-0 items-center gap-3">
                  <img v-if="user.avatar" :src="user.avatar" alt="" class="h-8 w-8 shrink-0 rounded-full object-cover" />
                  <div v-else class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-blue-600 text-xs font-semibold text-white">{{ user.username.charAt(0).toUpperCase() }}</div>
                  <span class="min-w-0 break-all font-medium text-theme-text">{{ user.username }}</span>
                </div>
              </td>
              <td class="px-2 py-3"><span :class="['inline-flex whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium', roleClass(user.role)]">{{ roleLabel(user.role) }}</span></td>
              <td class="px-2 py-3"><span :class="['inline-flex whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium', statusClass(user.banned)]">{{ user.banned ? '已封禁' : '正常' }}</span></td>
              <td class="px-2 py-3">
                <div v-if="user.machineCodes?.length" class="space-y-1 text-xs">
                  <div v-for="(device, index) in user.machineCodes.slice(0, 2)" :key="index" class="break-all font-mono text-theme-text" :title="`设备码: ${device.machineCode}\n设备名称: ${device.deviceName || '未设置'}\n创建时间: ${formatDateTime(device.createdAt)}\n最后使用: ${device.lastUsedAt ? formatDateTime(device.lastUsedAt) : '未使用'}`">{{ device.machineCode.substring(0, 16) }}...</div>
                  <span v-if="user.machineCodes.length > 2" class="text-theme-text-secondary" :title="user.machineCodes.slice(2).map(device => `${device.machineCode} (${device.deviceName || '未命名'})`).join('\n')">+{{ user.machineCodes.length - 2 }} 更多</span>
                </div>
                <span v-else class="text-xs text-theme-text-secondary">未绑定</span>
              </td>
              <td class="px-2 py-3 text-xs text-theme-text-secondary">{{ formatDate(user.lastLoginAt) }}</td>
              <td class="px-2 py-3">
                <AdminUserActions :user="user" :viewer-role="viewerRole" :loading-keys="loadingKeys" :refreshing="refreshing" v-on="actionHandlers" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 手机和平板使用卡片，长用户名和设备码可自然换行。 -->
      <ul class="space-y-3 lg:hidden" aria-label="用户列表">
        <li v-for="user in pageItems" :key="user.id" class="min-w-0 rounded-xl border border-theme-border bg-theme-surface p-3">
          <article :aria-label="`用户 ${user.username}`">
            <div class="mb-3 flex min-w-0 items-start gap-3">
              <img v-if="user.avatar" :src="user.avatar" alt="" class="h-10 w-10 shrink-0 rounded-full object-cover" />
              <div v-else class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-blue-600 font-semibold text-white">{{ user.username.charAt(0).toUpperCase() }}</div>
              <div class="min-w-0 flex-1">
                <h4 class="break-all font-semibold text-theme-text">{{ user.username }}</h4>
                <div class="mt-1.5 flex flex-wrap gap-2">
                  <span :class="['rounded-full px-2 py-0.5 text-xs font-medium', roleClass(user.role)]">{{ roleLabel(user.role) }}</span>
                  <span :class="['rounded-full px-2 py-0.5 text-xs font-medium', statusClass(user.banned)]">{{ user.banned ? '已封禁' : '正常' }}</span>
                </div>
              </div>
            </div>
            <dl class="mb-3 space-y-2 text-xs">
              <div class="flex flex-wrap justify-between gap-x-3 gap-y-1">
                <dt class="text-theme-text-secondary">最后登录</dt>
                <dd class="break-words text-theme-text">{{ formatDate(user.lastLoginAt) }}</dd>
              </div>
              <div v-if="!user.machineCodes?.length" class="flex justify-between gap-3">
                <dt class="text-theme-text-secondary">设备码</dt>
                <dd class="text-theme-text">未绑定</dd>
              </div>
            </dl>
            <details v-if="user.machineCodes?.length" class="mb-3 text-xs">
              <summary class="cursor-pointer py-1 font-medium text-blue-700 dark:text-blue-300">查看 {{ user.machineCodes.length }} 个绑定设备</summary>
              <ul class="mt-2 space-y-2">
                <li v-for="(device, index) in user.machineCodes" :key="index" class="min-w-0 rounded-lg bg-gray-100 p-2 dark:bg-gray-700">
                  <p class="break-all font-medium text-theme-text">{{ device.deviceName || `设备 ${index + 1}` }}</p>
                  <p class="mt-1 break-all font-mono text-theme-text-secondary">{{ device.machineCode }}</p>
                  <p class="mt-1 text-theme-text-secondary">最后使用：{{ device.lastUsedAt ? formatDateTime(device.lastUsedAt) : '未使用' }}</p>
                </li>
              </ul>
            </details>
            <div class="border-t border-theme-border pt-3">
              <AdminUserActions :user="user" :viewer-role="viewerRole" :loading-keys="loadingKeys" :refreshing="refreshing" v-on="actionHandlers" />
            </div>
          </article>
        </li>
      </ul>
    </template>
    <div v-else class="py-10 text-center text-sm text-theme-text-secondary" role="status">
      {{ users.length ? '没有匹配的用户' : '暂无用户数据' }}
      <button v-if="query" type="button" class="mx-auto mt-3 block text-blue-600 hover:underline dark:text-blue-400" @click="searchTerm = ''">清除搜索</button>
    </div>

    <div class="mt-4 flex flex-col gap-3 border-t border-theme-border pt-4 sm:flex-row sm:flex-wrap sm:items-center sm:justify-between">
      <p class="text-center text-xs text-theme-text-secondary sm:text-left" aria-live="polite">显示 {{ rangeStart }}–{{ rangeEnd }} 条，共 {{ totalItems }} 个用户</p>
      <nav aria-label="用户列表分页" class="flex items-center justify-center gap-1">
        <button type="button" aria-label="第一页" title="第一页" :disabled="currentPage === 1 || refreshing" class="hidden h-10 w-10 items-center justify-center rounded-lg border border-theme-border text-theme-text hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-40 dark:hover:bg-gray-700 sm:inline-flex" @click="goToPage(1)"><ChevronsLeft class="h-4 w-4" aria-hidden="true" /></button>
        <button type="button" aria-label="上一页" title="上一页" :disabled="currentPage === 1 || refreshing" class="flex h-10 w-10 items-center justify-center rounded-lg border border-theme-border text-theme-text hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-40 dark:hover:bg-gray-700" @click="goToPage(currentPage - 1)"><ChevronLeft class="h-4 w-4" aria-hidden="true" /></button>
        <span class="min-w-24 px-2 text-center text-sm text-theme-text sm:hidden">第 {{ currentPage }} / {{ totalPages }} 页</span>
        <div class="hidden gap-1 sm:flex">
          <button v-for="page in pageNumbers" :key="page" type="button" :aria-label="`第 ${page} 页`" :aria-current="page === currentPage ? 'page' : undefined" :disabled="refreshing" :class="['h-10 min-w-10 rounded-lg border px-2 text-sm disabled:opacity-50', page === currentPage ? 'border-blue-600 bg-blue-600 text-white' : 'border-theme-border text-theme-text hover:bg-gray-100 dark:hover:bg-gray-700']" @click="goToPage(page)">{{ page }}</button>
        </div>
        <button type="button" aria-label="下一页" title="下一页" :disabled="currentPage === totalPages || refreshing" class="flex h-10 w-10 items-center justify-center rounded-lg border border-theme-border text-theme-text hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-40 dark:hover:bg-gray-700" @click="goToPage(currentPage + 1)"><ChevronRight class="h-4 w-4" aria-hidden="true" /></button>
        <button type="button" aria-label="最后一页" title="最后一页" :disabled="currentPage === totalPages || refreshing" class="hidden h-10 w-10 items-center justify-center rounded-lg border border-theme-border text-theme-text hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-40 dark:hover:bg-gray-700 sm:inline-flex" @click="goToPage(totalPages)"><ChevronsRight class="h-4 w-4" aria-hidden="true" /></button>
      </nav>
    </div>
  </div>
  <AdminUserHistoryDialog v-if="selectedHistoryUser" :key="selectedHistoryUser.username" :user="selectedHistoryUser" @close="selectedHistoryUser = null" />
</template>

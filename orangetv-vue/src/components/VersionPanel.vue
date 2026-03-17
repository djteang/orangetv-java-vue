<script setup lang="ts">
import { ref, watch } from 'vue'
import { Bug, CheckCircle, ChevronDown, ChevronUp, Download, Plus, RefreshCw, X } from 'lucide-vue-next'
import { changelog } from '@/lib/changelog'
import type { ChangelogEntry } from '@/lib/changelog'
import { CURRENT_VERSION } from '@/lib/version'
import { compareVersions, UpdateStatus } from '@/lib/version_check'

interface RemoteChangelogEntry {
  version: string
  date: string
  added: string[]
  changed: string[]
  fixed: string[]
}

const props = defineProps<{ isOpen: boolean; onClose: () => void }>()

const remoteChangelog = ref<RemoteChangelogEntry[]>([])
const hasUpdate = ref(false)
const latestVersion = ref('')
const showRemoteContent = ref(false)

watch(
  () => props.isOpen,
  (val) => {
    if (val) fetchRemoteChangelog()
  },
)

watch(
  () => props.isOpen,
  (val) => {
    if (val) {
      document.body.style.overflow = 'hidden'
      document.documentElement.style.overflow = 'hidden'
    } else {
      document.body.style.overflow = ''
      document.documentElement.style.overflow = ''
    }
  },
)

async function fetchRemoteChangelog() {
  try {
    const response = await fetch(
      'https://raw.githubusercontent.com/djteang/OrangeTV/refs/heads/main/CHANGELOG',
    )
    if (response.ok) {
      const content = await response.text()
      const parsed = parseChangelog(content)
      remoteChangelog.value = parsed
      if (parsed.length > 0) {
        const sorted = [...parsed].sort(
          (a, b) => new Date(b.date).getTime() - new Date(a.date).getTime(),
        )
        const latest = sorted[0]
        latestVersion.value = latest.version
        hasUpdate.value = compareVersions(latest.version) === UpdateStatus.HAS_UPDATE
      }
    }
  } catch {
    // ignore
  }
}

function parseChangelog(content: string): RemoteChangelogEntry[] {
  const lines = content.split('\n')
  const versions: RemoteChangelogEntry[] = []
  let current: RemoteChangelogEntry | null = null
  let section: string | null = null

  for (const line of lines) {
    const t = line.trim()
    const vMatch = t.match(/^## \[([\d.]+)\] - (\d{4}-\d{2}-\d{2})$/)
    if (vMatch) {
      if (current) versions.push(current)
      current = { version: vMatch[1], date: vMatch[2], added: [], changed: [], fixed: [] }
      section = null
      continue
    }
    if (current) {
      if (t === '### Added') { section = 'added'; continue }
      if (t === '### Changed') { section = 'changed'; continue }
      if (t === '### Fixed') { section = 'fixed'; continue }
      if (t.startsWith('- ') && section) {
        const entry = t.substring(2)
        if (section === 'added') current.added.push(entry)
        else if (section === 'changed') current.changed.push(entry)
        else if (section === 'fixed') current.fixed.push(entry)
      }
    }
  }
  if (current) versions.push(current)
  return versions
}

function isCurrentVersion(entry: ChangelogEntry | RemoteChangelogEntry) {
  return entry.version === CURRENT_VERSION
}
</script>

<template>
  <Teleport to="body">
    <template v-if="isOpen">
      <!-- 背景遮罩 -->
      <div
        class="fixed inset-0 bg-black/50 backdrop-blur-sm z-[1000]"
        @click="onClose"
        @touchmove.prevent
        @wheel.prevent
        style="touch-action: none"
      />

      <!-- 版本面板 -->
      <div
        class="fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-full max-w-xl max-h-[90vh] bg-white dark:bg-gray-900 rounded-xl shadow-xl z-[1001] overflow-hidden"
        @touchmove.stop
        style="touch-action: auto"
      >
        <!-- 标题栏 -->
        <div class="flex items-center justify-between p-3 sm:p-6 border-b border-gray-200 dark:border-gray-700">
          <div class="flex items-center gap-2 sm:gap-3">
            <h3 class="text-lg sm:text-xl font-bold text-gray-800 dark:text-gray-200">版本信息</h3>
            <div class="flex flex-wrap items-center gap-1 sm:gap-2">
              <span class="px-2 sm:px-3 py-1 text-xs sm:text-sm font-medium bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300 rounded-full">
                v{{ CURRENT_VERSION }}
              </span>
              <span v-if="hasUpdate" class="px-2 sm:px-3 py-1 text-xs sm:text-sm font-medium bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300 rounded-full flex items-center gap-1">
                <Download class="w-3 h-3 sm:w-4 sm:h-4" />
                <span class="hidden sm:inline">有新版本可用</span>
                <span class="sm:hidden">可更新</span>
              </span>
            </div>
          </div>
          <button
            @click="onClose"
            class="w-6 h-6 sm:w-8 sm:h-8 p-1 rounded-full flex items-center justify-center text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
            aria-label="关闭"
          >
            <X class="w-full h-full" />
          </button>
        </div>

        <!-- 内容区域 -->
        <div class="p-3 sm:p-6 overflow-y-auto max-h-[calc(95vh-140px)] sm:max-h-[calc(90vh-120px)]">
          <div class="space-y-3 sm:space-y-6">
            <!-- 有更新 -->
            <div v-if="hasUpdate" class="bg-gradient-to-r from-yellow-50 to-amber-50 dark:from-yellow-900/20 dark:to-amber-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg p-3 sm:p-4">
              <div class="flex flex-col gap-3">
                <div class="flex items-center gap-2 sm:gap-3">
                  <div class="w-8 h-8 sm:w-10 sm:h-10 bg-yellow-100 dark:bg-yellow-800/40 rounded-full flex items-center justify-center flex-shrink-0">
                    <Download class="w-4 h-4 sm:w-5 sm:h-5 text-yellow-600 dark:text-yellow-400" />
                  </div>
                  <div class="min-w-0 flex-1">
                    <h4 class="text-sm sm:text-base font-semibold text-yellow-800 dark:text-yellow-200">发现新版本</h4>
                    <p class="text-xs sm:text-sm text-yellow-700 dark:text-yellow-300 break-all">v{{ CURRENT_VERSION }} → v{{ latestVersion }}</p>
                  </div>
                </div>
                <a href="https://github.com/djteang/orangetv-java-vue" target="_blank" rel="noopener noreferrer" class="inline-flex items-center justify-center gap-2 px-3 py-2 bg-yellow-600 hover:bg-yellow-700 text-white text-xs sm:text-sm rounded-lg transition-colors shadow-sm w-full">
                  <Download class="w-3 h-3 sm:w-4 sm:h-4" />
                  前往仓库
                </a>
              </div>
            </div>

            <!-- 最新版本 -->
            <div v-else class="bg-gradient-to-r from-blue-50 to-emerald-50 dark:from-blue-900/20 dark:to-emerald-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-3 sm:p-4">
              <div class="flex flex-col gap-3">
                <div class="flex items-center gap-2 sm:gap-3">
                  <div class="w-8 h-8 sm:w-10 sm:h-10 bg-blue-100 dark:bg-blue-800/40 rounded-full flex items-center justify-center flex-shrink-0">
                    <CheckCircle class="w-4 h-4 sm:w-5 sm:h-5 text-blue-600 dark:text-blue-400" />
                  </div>
                  <div class="min-w-0 flex-1">
                    <h4 class="text-sm sm:text-base font-semibold text-blue-800 dark:text-blue-200">当前为最新版本</h4>
                    <p class="text-xs sm:text-sm text-blue-700 dark:text-blue-300 break-all">已是最新版本 v{{ CURRENT_VERSION }}</p>
                  </div>
                </div>
                <a href="https://github.com/djteang/orangetv-java-vue" target="_blank" rel="noopener noreferrer" class="inline-flex items-center justify-center gap-2 px-3 py-2 bg-blue-600 hover:bg-blue-700 text-white text-xs sm:text-sm rounded-lg transition-colors shadow-sm w-full">
                  <CheckCircle class="w-3 h-3 sm:w-4 sm:h-4" />
                  前往仓库
                </a>
              </div>
            </div>

            <!-- 远程可更新内容 -->
            <div v-if="hasUpdate" class="space-y-4">
              <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <h4 class="text-lg font-semibold text-gray-800 dark:text-gray-200 flex items-center gap-2">
                  <Download class="w-5 h-5 text-yellow-500" />
                  远程更新内容
                </h4>
                <button @click="showRemoteContent = !showRemoteContent" class="inline-flex items-center justify-center gap-2 px-3 py-1.5 bg-yellow-100 hover:bg-yellow-200 text-yellow-800 dark:bg-yellow-800/30 dark:hover:bg-yellow-800/50 dark:text-yellow-200 rounded-lg transition-colors text-sm w-full sm:w-auto">
                  <template v-if="showRemoteContent"><ChevronUp class="w-4 h-4" />收起</template>
                  <template v-else><ChevronDown class="w-4 h-4" />查看更新内容</template>
                </button>
              </div>
              <div v-if="showRemoteContent && remoteChangelog.length > 0" class="space-y-4">
                <div
                  v-for="entry in remoteChangelog.filter(e => !changelog.map(l => l.version).includes(e.version)).sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime())"
                  :key="entry.version"
                  :class="['p-4 rounded-lg border', entry.version === latestVersion ? 'bg-yellow-50 dark:bg-yellow-900/20 border-yellow-200 dark:border-yellow-800' : 'bg-gray-50 dark:bg-gray-800/60 border-gray-200 dark:border-gray-700']"
                >
                  <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-3">
                    <div class="flex flex-wrap items-center gap-2">
                      <h4 class="text-lg font-semibold text-gray-900 dark:text-gray-100">v{{ entry.version }}</h4>
                      <span v-if="entry.version === latestVersion" class="px-2 py-1 text-xs font-medium bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300 rounded-full">远程最新</span>
                    </div>
                    <div class="text-sm text-gray-500 dark:text-gray-400">{{ entry.date }}</div>
                  </div>
                  <div v-if="entry.added.length > 0" class="mb-3">
                    <h5 class="text-sm font-medium text-green-600 dark:text-green-400 mb-2 flex items-center gap-1"><Plus class="w-4 h-4" />新增功能</h5>
                    <ul class="space-y-1">
                      <li v-for="(item, i) in entry.added" :key="i" class="text-sm text-gray-700 dark:text-gray-300 flex items-start gap-2">
                        <span class="w-1.5 h-1.5 bg-green-400 rounded-full mt-2 flex-shrink-0"></span>{{ item }}
                      </li>
                    </ul>
                  </div>
                  <div v-if="entry.changed.length > 0" class="mb-3">
                    <h5 class="text-sm font-medium text-blue-600 dark:text-blue-400 mb-2 flex items-center gap-1"><RefreshCw class="w-4 h-4" />功能改进</h5>
                    <ul class="space-y-1">
                      <li v-for="(item, i) in entry.changed" :key="i" class="text-sm text-gray-700 dark:text-gray-300 flex items-start gap-2">
                        <span class="w-1.5 h-1.5 bg-blue-400 rounded-full mt-2 flex-shrink-0"></span>{{ item }}
                      </li>
                    </ul>
                  </div>
                  <div v-if="entry.fixed.length > 0">
                    <h5 class="text-sm font-medium text-purple-700 dark:text-purple-400 mb-2 flex items-center gap-1"><Bug class="w-4 h-4" />问题修复</h5>
                    <ul class="space-y-1">
                      <li v-for="(item, i) in entry.fixed" :key="i" class="text-sm text-gray-700 dark:text-gray-300 flex items-start gap-2">
                        <span class="w-1.5 h-1.5 bg-purple-500 rounded-full mt-2 flex-shrink-0"></span>{{ item }}
                      </li>
                    </ul>
                  </div>
                </div>
              </div>
            </div>

            <!-- 变更日志 -->
            <div class="border-b border-gray-200 dark:border-gray-700 pb-4">
              <h4 class="text-lg font-semibold text-gray-800 dark:text-gray-200 pb-3 sm:pb-4">变更日志</h4>
              <div class="space-y-4">
                <div
                  v-for="entry in changelog"
                  :key="entry.version"
                  :class="['p-4 rounded-lg border', isCurrentVersion(entry) ? 'bg-blue-50 dark:bg-blue-900/20 border-blue-200 dark:border-blue-800' : 'bg-gray-50 dark:bg-gray-800/60 border-gray-200 dark:border-gray-700']"
                >
                  <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-3">
                    <div class="flex flex-wrap items-center gap-2">
                      <h4 class="text-lg font-semibold text-gray-900 dark:text-gray-100">v{{ entry.version }}</h4>
                      <span v-if="isCurrentVersion(entry)" class="px-2 py-1 text-xs font-medium bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300 rounded-full">当前版本</span>
                    </div>
                    <div class="text-sm text-gray-500 dark:text-gray-400">{{ entry.date }}</div>
                  </div>
                  <div class="space-y-3">
                    <div v-if="entry.added.length > 0">
                      <h5 class="text-sm font-medium text-green-700 dark:text-green-400 mb-2 flex items-center gap-1"><Plus class="w-4 h-4" />新增功能</h5>
                      <ul class="space-y-1">
                        <li v-for="(item, i) in entry.added" :key="i" class="text-sm text-gray-700 dark:text-gray-300 flex items-start gap-2">
                          <span class="w-1.5 h-1.5 bg-green-500 rounded-full mt-2 flex-shrink-0"></span>{{ item }}
                        </li>
                      </ul>
                    </div>
                    <div v-if="entry.changed.length > 0">
                      <h5 class="text-sm font-medium text-blue-700 dark:text-blue-400 mb-2 flex items-center gap-1"><RefreshCw class="w-4 h-4" />功能改进</h5>
                      <ul class="space-y-1">
                        <li v-for="(item, i) in entry.changed" :key="i" class="text-sm text-gray-700 dark:text-gray-300 flex items-start gap-2">
                          <span class="w-1.5 h-1.5 bg-blue-500 rounded-full mt-2 flex-shrink-0"></span>{{ item }}
                        </li>
                      </ul>
                    </div>
                    <div v-if="entry.fixed.length > 0">
                      <h5 class="text-sm font-medium text-purple-700 dark:text-purple-400 mb-2 flex items-center gap-1"><Bug class="w-4 h-4" />问题修复</h5>
                      <ul class="space-y-1">
                        <li v-for="(item, i) in entry.fixed" :key="i" class="text-sm text-gray-700 dark:text-gray-300 flex items-start gap-2">
                          <span class="w-1.5 h-1.5 bg-purple-500 rounded-full mt-2 flex-shrink-0"></span>{{ item }}
                        </li>
                      </ul>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </Teleport>
</template>

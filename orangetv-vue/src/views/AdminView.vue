<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import PageLayout from '@/components/PageLayout.vue'
import { useToast } from '@/composables/useToast'
import * as adminApi from '@/api/admin'
import type { User, VideoSource, LiveSource, SiteConfig, AdminConfig, CustomCategory, ConfigSubscription } from '@/types'
import {
  Users, Video, Settings, Loader2, Plus, Trash2, Shield,
  Save, ChevronDown, ChevronUp, Pencil, Ban, UserCheck,
  KeyRound, X, FileText, FolderOpen, Database, Download, Upload, AlertTriangle,
  Zap, ShieldCheck
} from 'lucide-vue-next'

const toast = useToast()

// ─── 全局状态 ───────────────────────────────────────────────────────────
const loading = ref(true)
const role = ref<'owner' | 'admin' | null>(null)
const siteConfig = ref<SiteConfig>({})
const loadingKeys = ref<Set<string>>(new Set())

function isLoading(key: string) { return loadingKeys.value.has(key) }
async function withLoading<T>(key: string, fn: () => Promise<T>): Promise<T> {
  loadingKeys.value.add(key)
  try { return await fn() }
  finally { loadingKeys.value.delete(key) }
}

// 折叠面板状态
const expandedTabs = ref<Record<string, boolean>>({
  configFile: false,
  siteConfig: false,
  users: false,
  sources: false,
  live: false,
  categories: false,
  dataMigration: false,
})

function toggleTab(key: string) {
  expandedTabs.value[key] = !expandedTabs.value[key]
}

// ─── 初始化加载 ─────────────────────────────────────────────────────────
const configFile = ref('')
const configSubscription = ref<ConfigSubscription>({ URL: '', AutoUpdate: false, LastCheck: '' })
const categories = ref<CustomCategory[]>([])

async function fetchConfig() {
  try {
    const data = await adminApi.getAdminConfig() as AdminConfig
    role.value = (data.Role === 'owner' || data.Role === 'admin') ? data.Role : null
    siteConfig.value = data.Config || {}
    configFile.value = (data as any).ConfigFile || ''
    configSubscription.value = (data as any).ConfigSubscribtion || { URL: '', AutoUpdate: false, LastCheck: '' }
    categories.value = ((data as any).CustomCategories || []) as CustomCategory[]
  } catch {
    toast.error('获取配置失败')
  }
}

onMounted(async () => {
  await fetchConfig()
  if (role.value) {
    await fetchStats()
  }
  loading.value = false
})

// ─── 站点配置 ───────────────────────────────────────────────────────────
const siteSettings = ref({
  SiteName: '',
  Announcement: '',
  RequireDeviceCode: true,
  DisableYellowFilter: false,
  FluidSearch: true,
  EnableLinuxDoLogin: false,
  SearchDownstreamMaxPage: 1,
  SiteInterfaceCacheTime: 7200,
})

// 当 siteConfig 加载后同步到 siteSettings
watch(siteConfig, (cfg) => {
  if (cfg) {
    siteSettings.value = {
      SiteName: (cfg.site_name as string) || '',
      Announcement: (cfg.announcement as string) || '',
      RequireDeviceCode: cfg.require_device_code !== undefined ? !!cfg.require_device_code : true,
      DisableYellowFilter: !!cfg.disable_yellow_filter,
      FluidSearch: cfg.fluid_search !== undefined ? !!cfg.fluid_search : true,
      EnableLinuxDoLogin: !!(cfg as any).enable_linuxdo_login,
      SearchDownstreamMaxPage: (cfg.search_downstream_max_page as number) || 1,
      SiteInterfaceCacheTime: (cfg.site_interface_cache_time as number) || 7200,
    }
  }
}, { immediate: true })

async function handleSaveSiteConfig() {
  await withLoading('saveSiteConfig', async () => {
    try {
      await adminApi.updateSiteConfig({ ...siteSettings.value })
      toast.success('站点配置已保存')
      await fetchConfig()
    } catch { toast.error('保存失败') }
  })
}

// ─── 用户管理 ───────────────────────────────────────────────────────────
const users = ref<User[]>([])
const usersLoaded = ref(false)
const showAddUserForm = ref(false)
const newUser = ref({ username: '', password: '' })
const showChangePasswordForm = ref(false)
const changePasswordUser = ref({ username: '', password: '' })
const showDeleteUserModal = ref(false)
const deletingUser = ref<string | null>(null)

async function fetchUsers() {
  if (usersLoaded.value) return
  await withLoading('fetchUsers', async () => {
    try {
      users.value = await adminApi.getUsers() as User[]
      usersLoaded.value = true
    } catch { toast.error('获取用户列表失败') }
  })
}

async function handleAddUser() {
  if (!newUser.value.username || !newUser.value.password) return
  await withLoading('addUser', async () => {
    try {
      await adminApi.addUser(newUser.value.username, newUser.value.password)
      newUser.value = { username: '', password: '' }
      showAddUserForm.value = false
      usersLoaded.value = false
      await fetchUsers()
      toast.success('用户已添加')
    } catch { toast.error('添加失败') }
  })
}

async function handleBanUser(username: string) {
  await withLoading(`ban_${username}`, async () => {
    try {
      await adminApi.banUser(username)
      usersLoaded.value = false; await fetchUsers()
      toast.success('用户已封禁')
    } catch { toast.error('操作失败') }
  })
}

async function handleUnbanUser(username: string) {
  await withLoading(`unban_${username}`, async () => {
    try {
      await adminApi.unbanUser(username)
      usersLoaded.value = false; await fetchUsers()
      toast.success('用户已解封')
    } catch { toast.error('操作失败') }
  })
}

async function handleSetAdmin(username: string) {
  await withLoading(`setAdmin_${username}`, async () => {
    try {
      await adminApi.setAdmin(username)
      usersLoaded.value = false; await fetchUsers()
      toast.success('已设为管理员')
    } catch { toast.error('操作失败') }
  })
}

async function handleCancelAdmin(username: string) {
  await withLoading(`cancelAdmin_${username}`, async () => {
    try {
      await adminApi.cancelAdmin(username)
      usersLoaded.value = false; await fetchUsers()
      toast.success('已取消管理员')
    } catch { toast.error('操作失败') }
  })
}

function handleShowChangePassword(username: string) {
  changePasswordUser.value = { username, password: '' }
  showChangePasswordForm.value = true
}

async function handleChangePassword() {
  if (!changePasswordUser.value.password) return
  await withLoading('changePassword', async () => {
    try {
      await adminApi.changePassword(changePasswordUser.value.username, changePasswordUser.value.password)
      showChangePasswordForm.value = false
      changePasswordUser.value = { username: '', password: '' }
      toast.success('密码已修改')
    } catch { toast.error('修改密码失败') }
  })
}

function handleDeleteUser(username: string) {
  deletingUser.value = username
  showDeleteUserModal.value = true
}

async function handleConfirmDeleteUser() {
  if (!deletingUser.value) return
  await withLoading('deleteUser', async () => {
    try {
      await adminApi.deleteUser(deletingUser.value!)
      showDeleteUserModal.value = false
      deletingUser.value = null
      usersLoaded.value = false; await fetchUsers()
      toast.success('用户已删除')
    } catch { toast.error('删除失败') }
  })
}

// ─── 统计数据 ───────────────────────────────────────────────────────────
const stats = ref({
  totalUsers: 0,
  todayNewUsers: 0,
  totalSearches: 0,
  todaySearches: 0,
  totalVideoSources: 0,
  activeUsers: 0,
  userTrend: [] as { date: string; count: number }[],
  searchTrend: [] as { date: string; count: number }[]
})

async function fetchStats() {
  try {
    const data = await adminApi.getStats() as any
    stats.value = data
  } catch (err) {
    console.error('获取统计数据失败:', err)
  }
}

// ─── 视频源管理 ─────────────────────────────────────────────────────────
const videoSources = ref<VideoSource[]>([])
const sourcesLoaded = ref(false)
const showAddSource = ref(false)
const newSource = ref({ name: '', api: '', detail: '' })
const editingSource = ref<VideoSource | null>(null)

// 有效性检测状态
const validationKeyword = ref('灵笼')
const validationResults = ref<Record<string, {
  status: 'valid' | 'no_results' | 'invalid' | 'validating'
  message: string
  resultCount: number
}>>({})
const isBatchValidating = ref(false)

// 测速结果状态
const speedTestResults = ref<Record<string, {
  responseTime: number
  success: boolean
  testing: boolean
  error?: string
}>>({})

// 添加表单验证状态
const newSourceValidation = ref<{ status: string | null; message: string }>({ status: null, message: '' })
const isNewSourceValidating = ref(false)

// 编辑表单验证状态
const editSourceValidation = ref<{ status: string | null; message: string }>({ status: null, message: '' })

async function fetchSources() {
  if (sourcesLoaded.value) return
  await withLoading('fetchSources', async () => {
    try {
      videoSources.value = await adminApi.getVideoSources() as VideoSource[]
      sourcesLoaded.value = true
    } catch { toast.error('获取视频源失败') }
  })
}

async function handleAddSource() {
  if (!newSource.value.name || !newSource.value.api) {
    toast.warning('请填写名称和API地址'); return
  }
  await withLoading('addSource', async () => {
    try {
      await adminApi.addVideoSource('', newSource.value.name, newSource.value.api, newSource.value.detail)
      newSource.value = { name: '', api: '', detail: '' }
      newSourceValidation.value = { status: null, message: '' }
      showAddSource.value = false
      sourcesLoaded.value = false; await fetchSources()
      toast.success('视频源已添加')
    } catch { toast.error('添加失败') }
  })
}

async function handleEditSource() {
  if (!editingSource.value || !editingSource.value.name || !editingSource.value.api) return
  await withLoading('editSource', async () => {
    try {
      await adminApi.editVideoSource(editingSource.value!.key, editingSource.value!.name, editingSource.value!.api, editingSource.value!.detail)
      editingSource.value = null
      editSourceValidation.value = { status: null, message: '' }
      sourcesLoaded.value = false; await fetchSources()
      toast.success('视频源已更新')
    } catch { toast.error('编辑失败') }
  })
}

async function handleToggleSource(source: VideoSource) {
  const action = source.disabled ? 'enable' : 'disable'
  await withLoading(`toggle_${source.key}`, async () => {
    try {
      if (action === 'enable') await adminApi.enableVideoSource(source.key)
      else await adminApi.disableVideoSource(source.key)
      sourcesLoaded.value = false; await fetchSources()
      toast.success(action === 'enable' ? '已启用' : '已禁用')
    } catch { toast.error('操作失败') }
  })
}

async function handleDeleteSource(key: string) {
  await withLoading(`deleteSource_${key}`, async () => {
    try {
      await adminApi.deleteVideoSource(key)
      sourcesLoaded.value = false; await fetchSources()
      toast.success('视频源已删除')
    } catch { toast.error('删除失败') }
  })
}

// 有效性检测（SSE 流式验证）
function handleValidateSource(api: string, name: string, isNew: boolean) {
  if (!api.trim()) {
    toast.warning('API地址不能为空')
    return
  }

  const setResult = isNew ? newSourceValidation : editSourceValidation
  if (isNew) {
    isNewSourceValidating.value = true
  }
  setResult.value = { status: 'validating', message: '检测中...' }

  const startTime = Date.now()
  const keyword = validationKeyword.value.trim() || '灵笼'
  const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'
  const token = localStorage.getItem('token')
  const url = `${baseURL}/admin/source/validate?q=${encodeURIComponent(keyword)}&tempApi=${encodeURIComponent(api.trim())}&tempName=${encodeURIComponent(name)}`

  fetch(url, {
    headers: {
      'Accept': 'text/event-stream',
      ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
    },
  }).then(response => {
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const reader = response.body?.getReader()
    if (!reader) throw new Error('No reader')
    const decoder = new TextDecoder()
    let buffer = ''

    function read(): Promise<void> {
      return reader!.read().then(({ done, value }) => {
        if (done) {
          if (isNew) isNewSourceValidating.value = false
          return
        }
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''
        for (const line of lines) {
          if (line.startsWith('data:')) {
            try {
              const data = JSON.parse(line.substring(5).trim())
              const responseTime = Date.now() - startTime
              if (data.type === 'source_result' || data.type === 'source_error') {
                if (data.source === 'temp') {
                  let message = ''
                  if (data.status === 'valid') {
                    message = `搜索正常 (${data.resultCount || 0}条结果, ${responseTime}ms)`
                  } else if (data.status === 'no_results') {
                    message = `无法搜索到结果 (${responseTime}ms)`
                  } else {
                    message = `连接失败: ${data.error || '未知错误'}`
                  }
                  setResult.value = { status: data.status, message }
                }
              } else if (data.type === 'complete') {
                if (isNew) isNewSourceValidating.value = false
              }
            } catch { /* ignore parse errors */ }
          }
        }
        return read()
      })
    }
    return read()
  }).catch(err => {
    if (isNew) isNewSourceValidating.value = false
    setResult.value = {
      status: 'invalid',
      message: `连接错误: ${err instanceof Error ? err.message : '未知错误'}`,
    }
  })

  // 30秒超时
  setTimeout(() => {
    if (setResult.value.status === 'validating') {
      if (isNew) isNewSourceValidating.value = false
      setResult.value = { status: 'invalid', message: '检测超时（30秒）' }
    }
  }, 30000)
}

// 批量有效性检测
function handleBatchValidation() {
  if (videoSources.value.length === 0) return
  isBatchValidating.value = true

  for (const source of videoSources.value) {
    validationResults.value[source.key] = { status: 'validating', message: '检测中...', resultCount: 0 }
  }

  const startTime = Date.now()
  const keyword = validationKeyword.value.trim() || '灵笼'
  const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'
  const token = localStorage.getItem('token')
  const url = `${baseURL}/admin/source/validate?q=${encodeURIComponent(keyword)}`

  fetch(url, {
    headers: {
      'Accept': 'text/event-stream',
      ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
    },
  }).then(response => {
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const reader = response.body?.getReader()
    if (!reader) throw new Error('No reader')
    const decoder = new TextDecoder()
    let buffer = ''

    function read(): Promise<void> {
      return reader!.read().then(({ done, value }) => {
        if (done) {
          isBatchValidating.value = false
          return
        }
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''
        for (const line of lines) {
          if (line.startsWith('data:')) {
            try {
              const data = JSON.parse(line.substring(5).trim())
              const responseTime = Date.now() - startTime
              if (data.type === 'source_result' || data.type === 'source_error') {
                const sourceKey = data.source
                let message = ''
                let resultCount = 0
                if (data.status === 'valid') {
                  message = `搜索正常 (${data.resultCount || 0}条结果, ${responseTime}ms)`
                  resultCount = data.resultCount || 0
                } else if (data.status === 'no_results') {
                  message = `无法搜索到结果 (${responseTime}ms)`
                } else {
                  message = `连接失败: ${data.error || '未知错误'}`
                }
                validationResults.value[sourceKey] = { status: data.status, message, resultCount }
              } else if (data.type === 'complete') {
                isBatchValidating.value = false
              }
            } catch { /* ignore parse errors */ }
          }
        }
        return read()
      })
    }
    return read()
  }).catch(() => {
    isBatchValidating.value = false
    toast.error('有效性检测失败')
  })

  // 超时
  setTimeout(() => {
    if (isBatchValidating.value) {
      isBatchValidating.value = false
      for (const source of videoSources.value) {
        if (validationResults.value[source.key]?.status === 'validating') {
          validationResults.value[source.key] = { status: 'invalid', message: '检测超时', resultCount: 0 }
        }
      }
    }
  }, 60000)
}

// 单个视频源测速
async function handleSpeedTest(sourceKey: string, api: string) {
  speedTestResults.value = {
    ...speedTestResults.value,
    [sourceKey]: { responseTime: 0, success: false, testing: true },
  }

  try {
    const data = await adminApi.speedTestSource(sourceKey, api)
    speedTestResults.value = {
      ...speedTestResults.value,
      [sourceKey]: {
        responseTime: data.responseTime || 0,
        success: data.success ?? false,
        testing: false,
        error: data.error,
      },
    }
  } catch (err) {
    speedTestResults.value = {
      ...speedTestResults.value,
      [sourceKey]: {
        responseTime: 0,
        success: false,
        testing: false,
        error: err instanceof Error ? err.message : '测速失败',
      },
    }
  }
}

// ─── 直播源管理 ─────────────────────────────────────────────────────────
const liveSources = ref<LiveSource[]>([])
const liveLoaded = ref(false)
// const showAddLive = ref(false)
// const newLive = ref({ name: '', key: '', url: '', epg: '', ua: '' })
// const editingLive = ref<LiveSource | null>(null)

async function fetchLiveSources() {
  if (liveLoaded.value) return
  await withLoading('fetchLive', async () => {
    try {
      liveSources.value = await adminApi.getLiveSources() as LiveSource[]
      liveLoaded.value = true
    } catch { toast.error('获取直播源失败') }
  })
}

// 展开面板时按需加载数据
watch(expandedTabs, (tabs) => {
  if (tabs.users && !usersLoaded.value) fetchUsers()
  if (tabs.sources && !sourcesLoaded.value) fetchSources()
  if (tabs.live && !liveLoaded.value) fetchLiveSources()
}, { deep: true })

// ─── 配置文件管理 ─────────────────────────────────────────────────────────
const configFileContent = ref('')
const configSubUrl = ref('')
const configSubAutoUpdate = ref(false)
const configSubLastCheck = ref('')

// Base58 alphabet
const BASE58_ALPHABET = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz'

function base58Decode(input: string): string {
  let num = BigInt(0)
  for (const c of input) {
    const idx = BASE58_ALPHABET.indexOf(c)
    if (idx === -1) throw new Error('Invalid Base58')
    num = num * 58n + BigInt(idx)
  }
  const hex = num.toString(16).padStart(2, '0')
  const bytes = new Uint8Array((hex.length / 2) | 0)
  for (let i = 0; i < bytes.length; i++) {
    bytes[i] = parseInt(hex.substring(i * 2, i * 2 + 2), 16)
  }
  return new TextDecoder().decode(bytes)
}

function tryDecode(content: string): string {
  if (!content || !content.trim()) return content
  const trimmed = content.trim()
  // 1. Already valid JSON
  try { JSON.parse(trimmed); return trimmed } catch {}
  // 2. Try Base64
  try {
    const decoded = atob(trimmed)
    JSON.parse(decoded)
    return decoded
  } catch {}
  // 3. Try Base58
  try {
    const decoded = base58Decode(trimmed)
    JSON.parse(decoded)
    return decoded
  } catch {}
  // 4. Fallback
  return content
}

watch([configFile, configSubscription], ([file, sub]) => {
  configFileContent.value = tryDecode(file || '')
  configSubUrl.value = sub?.URL || ''
  configSubAutoUpdate.value = sub?.AutoUpdate || false
  configSubLastCheck.value = sub?.LastCheck || ''
}, { immediate: true })

async function handleFetchSubscription() {
  if (!configSubUrl.value) { toast.warning('请输入订阅 URL'); return }
  await withLoading('fetchSubscription', async () => {
    try {
      const result = await adminApi.fetchConfigSubscription(configSubUrl.value) as any
      const content = result
      if (typeof content === 'string') {
        configFileContent.value = tryDecode(content)
      }
      configSubLastCheck.value = new Date().toISOString()
      toast.success('订阅内容已拉取，请检查后点击保存')
    } catch { toast.error('拉取订阅失败') }
  })
}

async function handleSaveConfigFile() {
  await withLoading('saveConfigFile', async () => {
    try {
      await adminApi.saveConfigFile({
        config_file: configFileContent.value,
        config_subscription_url: configSubUrl.value,
        config_subscription_auto_update: configSubAutoUpdate.value,
      })
      toast.success('配置文件已保存，视频源/直播源已同步')
      await fetchConfig()
      // 后端已根据配置内容同步视频源和直播源，重置加载标记以便刷新
      sourcesLoaded.value = false
      liveLoaded.value = false
      if (expandedTabs.value.sources) await fetchSources()
      if (expandedTabs.value.live) await fetchLiveSources()
    } catch { toast.error('保存失败') }
  })
}

// ─── 分类管理 ─────────────────────────────────────────────────────────────
const newCategory = ref({ name: '', type: 'movie' as 'movie' | 'tv', query: '' })

async function handleAddCategory() {
  if (!newCategory.value.name || !newCategory.value.query) {
    toast.warning('请填写名称和关键词'); return
  }
  await withLoading('addCategory', async () => {
    try {
      await adminApi.addCategory(newCategory.value.name, newCategory.value.type, newCategory.value.query)
      newCategory.value = { name: '', type: 'movie', query: '' }
      await fetchConfig()
      toast.success('分类已添加')
    } catch { toast.error('添加失败') }
  })
}

async function handleDeleteCategory(index: number) {
  await withLoading(`deleteCategory_${index}`, async () => {
    try {
      await adminApi.deleteCategory(index)
      await fetchConfig()
      toast.success('分类已删除')
    } catch { toast.error('删除失败') }
  })
}

async function handleToggleCategory(index: number, disabled: boolean) {
  const action = disabled ? 'enable' : 'disable'
  await withLoading(`toggleCategory_${index}`, async () => {
    try {
      if (action === 'enable') await adminApi.enableCategory(index)
      else await adminApi.disableCategory(index)
      await fetchConfig()
      toast.success(action === 'enable' ? '已启用' : '已禁用')
    } catch { toast.error('操作失败') }
  })
}

// ─── 数据迁移 ─────────────────────────────────────────────────────────────
const exportPassword = ref('')
const importPassword = ref('')
const importFile = ref<File | null>(null)

function handleImportFileChange(e: Event) {
  const target = e.target as HTMLInputElement
  importFile.value = target.files?.[0] || null
}

async function handleExportData() {
  if (!exportPassword.value) { toast.warning('请输入加密密码'); return }
  await withLoading('exportData', async () => {
    try {
      const blob = await adminApi.exportData(exportPassword.value)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = 'orangetv_backup.dat'
      a.click()
      URL.revokeObjectURL(url)
      exportPassword.value = ''
      toast.success('数据已导出')
    } catch { toast.error('导出失败') }
  })
}

async function handleImportData() {
  if (!importFile.value || !importPassword.value) {
    toast.warning('请选择文件并输入密码'); return
  }
  await withLoading('importData', async () => {
    try {
      await adminApi.importData(importFile.value!, importPassword.value)
      importFile.value = null
      importPassword.value = ''
      await fetchConfig()
      toast.success('数据已导入')
    } catch { toast.error('导入失败，请检查密码是否正确') }
  })
}
</script>

<template>
  <PageLayout>
    <div class="px-2 sm:px-10 py-4 sm:py-8">
      <div class="max-w-[95%] mx-auto">

        <!-- 加载骨架 -->
        <template v-if="loading">
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-8">管理员设置</h1>
          <div class="space-y-4">
            <div v-for="i in 4" :key="i" class="h-20 bg-gray-200 dark:bg-gray-700 rounded-lg animate-pulse" />
          </div>
        </template>

        <!-- 无权限 -->
        <template v-else-if="!role">
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-8">管理员设置</h1>
          <div class="text-center py-20">
            <Shield class="w-16 h-16 mx-auto text-gray-300 dark:text-gray-600 mb-4" />
            <p class="text-gray-500 dark:text-gray-400">您没有管理员权限</p>
          </div>
        </template>

        <!-- 主内容 -->
        <template v-else>
          <div class="flex items-center gap-2 mb-8">
            <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">管理员设置</h1>
          </div>

          <!-- 统计概览 -->
          <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
            <!-- 总用户数 -->
            <div class="bg-white dark:bg-gray-800 rounded-xl p-6 shadow-sm border border-gray-200 dark:border-gray-700 hover:shadow-md transition-shadow">
              <div class="flex items-center justify-between mb-4">
                <div class="p-3 bg-blue-50 dark:bg-blue-900/20 rounded-lg">
                  <Users class="w-6 h-6 text-blue-600 dark:text-blue-400" />
                </div>
                <span class="text-xs font-medium text-green-600 dark:text-green-400 bg-green-50 dark:bg-green-900/20 px-2 py-1 rounded-full">
                  +{{ stats.todayNewUsers || 0 }} 今日
                </span>
              </div>
              <div class="space-y-1">
                <p class="text-sm text-gray-600 dark:text-gray-400">总用户数</p>
                <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ stats.totalUsers || 0 }}</p>
              </div>
            </div>

            <!-- 活跃用户 -->
            <div class="bg-white dark:bg-gray-800 rounded-xl p-6 shadow-sm border border-gray-200 dark:border-gray-700 hover:shadow-md transition-shadow">
              <div class="flex items-center justify-between mb-4">
                <div class="p-3 bg-green-50 dark:bg-green-900/20 rounded-lg">
                  <Zap class="w-6 h-6 text-green-600 dark:text-green-400" />
                </div>
                <span class="text-xs font-medium text-gray-500 dark:text-gray-400">7日内</span>
              </div>
              <div class="space-y-1">
                <p class="text-sm text-gray-600 dark:text-gray-400">活跃用户</p>
                <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ stats.activeUsers || 0 }}</p>
              </div>
            </div>

            <!-- 总搜索次数 -->
            <div class="bg-white dark:bg-gray-800 rounded-xl p-6 shadow-sm border border-gray-200 dark:border-gray-700 hover:shadow-md transition-shadow">
              <div class="flex items-center justify-between mb-4">
                <div class="p-3 bg-emerald-50 dark:bg-emerald-900/20 rounded-lg">
                  <svg class="w-6 h-6 text-emerald-600 dark:text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                  </svg>
                </div>
                <span class="text-xs font-medium text-emerald-600 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-900/20 px-2 py-1 rounded-full">
                  +{{ stats.todaySearches || 0 }} 今日
                </span>
              </div>
              <div class="space-y-1">
                <p class="text-sm text-gray-600 dark:text-gray-400">总搜索次数</p>
                <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ stats.totalSearches || 0 }}</p>
              </div>
            </div>

            <!-- 视频源数量 -->
            <div class="bg-white dark:bg-gray-800 rounded-xl p-6 shadow-sm border border-gray-200 dark:border-gray-700 hover:shadow-md transition-shadow">
              <div class="flex items-center justify-between mb-4">
                <div class="p-3 bg-orange-50 dark:bg-orange-900/20 rounded-lg">
                  <Video class="w-6 h-6 text-orange-600 dark:text-orange-400" />
                </div>
                <span class="text-xs font-medium text-gray-500 dark:text-gray-400">已配置</span>
              </div>
              <div class="space-y-1">
                <p class="text-sm text-gray-600 dark:text-gray-400">视频源数量</p>
                <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ stats.totalVideoSources || 0 }}</p>
              </div>
            </div>
          </div>

          <!-- 趋势图表 -->
          <div class="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-6">
            <!-- 用户增长趋势 -->
            <div class="bg-white dark:bg-gray-800 rounded-xl p-6 shadow-sm border border-gray-200 dark:border-gray-700">
              <div class="flex items-center justify-between mb-6">
                <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">用户增长趋势</h3>
                <span class="text-xs text-gray-500 dark:text-gray-400">最近 7 天</span>
              </div>
              <div class="h-48 flex items-end justify-between gap-2">
                <div v-for="(item, idx) in stats.userTrend" :key="idx" class="flex-1 flex flex-col items-center gap-2 group">
                  <div class="relative w-full">
                    <div
                      class="w-full bg-gradient-to-t from-blue-500 to-blue-400 rounded-t-lg transition-all duration-300 hover:from-blue-600 hover:to-blue-500 cursor-pointer"
                      :style="{ height: (item.count / Math.max(...stats.userTrend.map(i => i.count), 1) * 160) + 'px', minHeight: '4px' }"
                    >
                      <div class="absolute -top-8 left-1/2 -translate-x-1/2 bg-gray-900 dark:bg-gray-700 text-white text-xs px-2 py-1 rounded opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap pointer-events-none">
                        {{ item.count }} 人
                      </div>
                    </div>
                  </div>
                  <span class="text-xs text-gray-500 dark:text-gray-400">{{ item.date }}</span>
                </div>
              </div>
            </div>

            <!-- 搜索趋势 -->
            <div class="bg-white dark:bg-gray-800 rounded-xl p-6 shadow-sm border border-gray-200 dark:border-gray-700">
              <div class="flex items-center justify-between mb-6">
                <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">搜索趋势</h3>
                <span class="text-xs text-gray-500 dark:text-gray-400">最近 7 天</span>
              </div>
              <div class="h-48 flex items-end justify-between gap-2">
                <div v-for="(item, idx) in stats.searchTrend" :key="idx" class="flex-1 flex flex-col items-center gap-2 group">
                  <div class="relative w-full">
                    <div
                      class="w-full bg-gradient-to-t from-emerald-500 to-emerald-400 rounded-t-lg transition-all duration-300 hover:from-emerald-600 hover:to-emerald-500 cursor-pointer"
                      :style="{ height: (item.count / Math.max(...stats.searchTrend.map(i => i.count), 1) * 160) + 'px', minHeight: '4px' }"
                    >
                      <div class="absolute -top-8 left-1/2 -translate-x-1/2 bg-gray-900 dark:bg-gray-700 text-white text-xs px-2 py-1 rounded opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap pointer-events-none">
                        {{ item.count }} 次
                      </div>
                    </div>
                  </div>
                  <span class="text-xs text-gray-500 dark:text-gray-400">{{ item.date }}</span>
                </div>
              </div>
            </div>
          </div>

          <div class="space-y-4">

            <!-- ═══════════ 配置文件（仅站长） ═══════════ -->
            <div v-if="role === 'owner'" class="rounded-xl shadow-sm overflow-hidden bg-white/80 backdrop-blur-md dark:bg-gray-800/50 dark:ring-1 dark:ring-gray-700">
              <button @click="toggleTab('configFile')" class="w-full px-6 py-4 flex items-center justify-between bg-gray-50/70 dark:bg-gray-800/60 hover:bg-gray-100/80 dark:hover:bg-gray-700/60 transition-colors">
                <div class="flex items-center gap-3">
                  <FileText class="w-5 h-5 text-gray-600 dark:text-gray-400" />
                  <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100">配置文件</h3>
                </div>
                <component :is="expandedTabs.configFile ? ChevronUp : ChevronDown" class="w-5 h-5 text-gray-500 dark:text-gray-400" />
              </button>
                          <div v-if="expandedTabs.configFile" class="px-6 py-4 space-y-6">
                <!-- 订阅 URL -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">订阅 URL</label>
                  <div class="flex gap-2">
                    <input v-model="configSubUrl" type="text" placeholder="远程配置订阅地址" class="flex-1 px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent" />
                    <button @click="handleFetchSubscription" :disabled="isLoading('fetchSubscription')" class="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-gray-600 hover:bg-gray-700 text-white text-sm font-medium transition-colors disabled:opacity-50 whitespace-nowrap">
                      <Download class="w-4 h-4" />
                      {{ isLoading('fetchSubscription') ? '拉取中...' : '拉取' }}
                    </button>
                  </div>
                </div>
                <!-- 自动更新 & 上次检查 -->
                <div class="flex items-center justify-between">
                  <div class="flex items-center gap-4">
                    <div class="flex items-center gap-2">
                      <label class="text-sm font-medium text-gray-700 dark:text-gray-300">自动更新</label>
                      <button type="button" @click="configSubAutoUpdate = !configSubAutoUpdate" :class="['relative inline-flex h-6 w-11 items-center rounded-full transition-colors', configSubAutoUpdate ? 'bg-blue-600' : 'bg-gray-200 dark:bg-gray-700']">
                        <span :class="['inline-block h-4 w-4 transform rounded-full bg-white transition-transform', configSubAutoUpdate ? 'translate-x-6' : 'translate-x-1']" />
                      </button>
                    </div>
                    <span v-if="configSubLastCheck" class="text-xs text-gray-500 dark:text-gray-400">上次检查: {{ configSubLastCheck }}</span>
                  </div>
                </div>
                <!-- 配置文件内容 -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">配置文件内容</label>
                  <textarea v-model="configFileContent" rows="20" class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent font-mono text-sm" placeholder="JSON 配置内容" />
                </div>
                <!-- 保存按钮 -->
                <div class="flex justify-end">
                  <button @click="handleSaveConfigFile" :disabled="isLoading('saveConfigFile')" class="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed">
                    <Save class="w-4 h-4" />
                    {{ isLoading('saveConfigFile') ? '保存中...' : '保存' }}
                  </button>
                </div>
              </div>
            </div>

            <!-- ═══════════ 站点配置 ═══════════ -->
            <div class="rounded-xl shadow-sm overflow-hidden bg-white/80 backdrop-blur-md dark:bg-gray-800/50 dark:ring-1 dark:ring-gray-700">
                <button @click="toggleTab('siteConfig')" class="w-full px-6 py-4 flex items-center justify-between bg-gray-50/70 dark:bg-gray-800/60 hover:bg-gray-100/80 dark:hover:bg-gray-700/60 transition-colors">
                <div class="flex items-center gap-3">
                  <Settings class="w-5 h-5 text-gray-600 dark:text-gray-400" />
                  <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100">站点配置</h3>
    </div>
                <component :is="expandedTabs.siteConfig ? ChevronUp : ChevronDown" class="w-5 h-5 text-gray-500 dark:text-gray-400" />
  </button>
                <div v-if="expandedTabs.siteConfig" class="px-6 py-4 space-y-6">
                <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">站点名称</label>
                <input v-model="siteSettings.SiteName" type="text" class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent" />
    </div>
                <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">站点公告</label>
                <textarea v-model="siteSettings.Announcement" rows="3" class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent" />
    </div>
                <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">搜索接口可拉取最大页数</label>
                <input v-model.number="siteSettings.SearchDownstreamMaxPage" type="number" min="1" class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent" />
    </div>
                <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">站点接口缓存时间（秒）</label>
                <input v-model.number="siteSettings.SiteInterfaceCacheTime" type="number" min="1" class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent" />
    </div>
    <!-- 启用设备码验证 -->
                <div>
                <div class="flex items-center justify-between">
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">启用设备码验证</label>
                <button type="button" @click="siteSettings.RequireDeviceCode = !siteSettings.RequireDeviceCode" :class="['relative inline-flex h-6 w-11 items-center rounded-full transition-colors', siteSettings.RequireDeviceCode ? 'bg-blue-600' : 'bg-gray-200 dark:bg-gray-700']">
                <span :class="['inline-block h-4 w-4 transform rounded-full bg-white transition-transform', siteSettings.RequireDeviceCode ? 'translate-x-6' : 'translate-x-1']" />
        </button>
      </div>
                <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">启用后用户登录时需要绑定设备码，提升账户安全性</p>
    </div>
    <!-- 禁用黄色过滤器 -->
                <div>
                <div class="flex items-center justify-between">
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">禁用黄色过滤器</label>
                <button type="button" @click="siteSettings.DisableYellowFilter = !siteSettings.DisableYellowFilter" :class="['relative inline-flex h-6 w-11 items-center rounded-full transition-colors', siteSettings.DisableYellowFilter ? 'bg-blue-600' : 'bg-gray-200 dark:bg-gray-700']">
                <span :class="['inline-block h-4 w-4 transform rounded-full bg-white transition-transform', siteSettings.DisableYellowFilter ? 'translate-x-6' : 'translate-x-1']" />
        </button>
      </div>
                <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">禁用黄色内容的过滤功能，允许显示所有内容</p>
    </div>
    <!-- 启用流式搜索 -->
                <div>
                <div class="flex items-center justify-between">
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">启用流式搜索</label>
                <button type="button" @click="siteSettings.FluidSearch = !siteSettings.FluidSearch" :class="['relative inline-flex h-6 w-11 items-center rounded-full transition-colors', siteSettings.FluidSearch ? 'bg-blue-600' : 'bg-gray-200 dark:bg-gray-700']">
                <span :class="['inline-block h-4 w-4 transform rounded-full bg-white transition-transform', siteSettings.FluidSearch ? 'translate-x-6' : 'translate-x-1']" />
        </button>
      </div>
                <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">启用后搜索结果将实时流式返回，提升用户体验</p>
    </div>
    <!-- 启用 LinuxDO 登录 -->
                <div>
                <div class="flex items-center justify-between">
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">启用 LinuxDO 登录</label>
                <button type="button" @click="siteSettings.EnableLinuxDoLogin = !siteSettings.EnableLinuxDoLogin" :class="['relative inline-flex h-6 w-11 items-center rounded-full transition-colors', siteSettings.EnableLinuxDoLogin ? 'bg-blue-600' : 'bg-gray-200 dark:bg-gray-700']">
                <span :class="['inline-block h-4 w-4 transform rounded-full bg-white transition-transform', siteSettings.EnableLinuxDoLogin ? 'translate-x-6' : 'translate-x-1']" />
        </button>
      </div>
                <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">允许用户使用 LinuxDO 账号登录（需要信任等级 2+）</p>
    </div>
    <!-- 保存按钮 -->
                <div class="flex justify-end">
                <button @click="handleSaveSiteConfig" :disabled="isLoading('saveSiteConfig')" class="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed">
        <Save class="w-4 h-4" />
        {{ isLoading('saveSiteConfig') ? '保存中...' : '保存' }}
      </button>
    </div>
              </div>
            </div>
            <!-- ═══════════ 用户配置 ═══════════ -->
            <div class="rounded-xl shadow-sm overflow-hidden bg-white/80 backdrop-blur-md dark:bg-gray-800/50 dark:ring-1 dark:ring-gray-700">
                <button @click="toggleTab('users')" class="w-full px-6 py-4 flex items-center justify-between bg-gray-50/70 dark:bg-gray-800/60 hover:bg-gray-100/80 dark:hover:bg-gray-700/60 transition-colors">
                <div class="flex items-center gap-3">
                  <Users class="w-5 h-5 text-gray-600 dark:text-gray-400" />
                  <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100">用户配置</h3>
    </div>
                <component :is="expandedTabs.users ? ChevronUp : ChevronDown" class="w-5 h-5 text-gray-500 dark:text-gray-400" />
  </button>
                <div v-if="expandedTabs.users" class="px-6 py-4">
                <div v-if="isLoading('fetchUsers')" class="flex justify-center py-10">
      <Loader2 class="w-8 h-8 animate-spin text-blue-500" />
    </div>
                <div v-else>
      <!-- 操作栏 -->
                <div class="flex justify-between items-center mb-4">
                <span class="text-sm text-gray-500 dark:text-gray-400">共 {{ users.length }} 个用户</span>
                <button @click="showAddUserForm = !showAddUserForm" class="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-colors">
          <Plus class="w-4 h-4" />
          {{ showAddUserForm ? '取消' : '添加用户' }}
        </button>
      </div>
      <!-- 添加用户表单 -->
                <div v-if="showAddUserForm" class="mb-4 p-4 rounded-xl bg-gray-50 dark:bg-gray-900 border border-gray-200 dark:border-gray-700 space-y-3">
                <input v-model="newUser.username" placeholder="用户名" class="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                <input v-model="newUser.password" type="password" placeholder="密码" class="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                <button @click="handleAddUser" :disabled="isLoading('addUser')" class="px-3 py-1.5 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium transition-colors disabled:opacity-50">
          {{ isLoading('addUser') ? '添加中...' : '确认添加' }}
        </button>
      </div>
      <!-- 修改密码表单 -->
                <div v-if="showChangePasswordForm" class="mb-4 p-4 rounded-xl bg-gray-50 dark:bg-gray-900 border border-gray-200 dark:border-gray-700 space-y-3">
                <div class="flex items-center justify-between">
                <span class="text-sm font-medium text-gray-700 dark:text-gray-300">修改密码: {{ changePasswordUser.username }}</span>
                <button @click="showChangePasswordForm = false" class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"><X class="w-4 h-4" /></button>
        </div>
                <input v-model="changePasswordUser.password" type="password" placeholder="新密码" class="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                <button @click="handleChangePassword" :disabled="isLoading('changePassword')" class="px-3 py-1.5 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium transition-colors disabled:opacity-50">
          {{ isLoading('changePassword') ? '修改中...' : '确认修改' }}
        </button>
      </div>
      <!-- 用户列表 -->
                <div class="overflow-x-auto">
                <table class="w-full text-sm">
                <thead>
                <tr class="border-b border-gray-200 dark:border-gray-700">
                <th class="text-left py-3 px-2 font-medium text-gray-500 dark:text-gray-400">用户</th>
                <th class="text-left py-3 px-2 font-medium text-gray-500 dark:text-gray-400">角色</th>
                <th class="text-left py-3 px-2 font-medium text-gray-500 dark:text-gray-400">状态</th>
                <th class="text-left py-3 px-2 font-medium text-gray-500 dark:text-gray-400">设备码</th>
                <th class="text-left py-3 px-2 font-medium text-gray-500 dark:text-gray-400">最后登录</th>
                <th class="text-right py-3 px-2 font-medium text-gray-500 dark:text-gray-400">操作</th>
            </tr>
          </thead>
                <tbody>
                <tr v-for="user in users" :key="user.id" class="border-b border-gray-100 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors">
                <td class="py-3 px-2">
                <div class="flex items-center gap-3">
                  <img
                    v-if="user.avatar"
                    :src="user.avatar"
                    class="w-8 h-8 rounded-full object-cover flex-shrink-0"
                  />
                  <div v-else class="w-8 h-8 rounded-full flex items-center justify-center text-white text-xs font-semibold flex-shrink-0" style="background-color: #2563EB">
                    {{ user.username.charAt(0).toUpperCase() }}
                  </div>
                  <span class="font-medium text-gray-900 dark:text-gray-100">{{ user.username }}</span>
                </div>
              </td>
              <td class="py-3 px-2">
                <span :class="['inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', user.role === 'owner' ? 'bg-purple-100 text-purple-800 dark:bg-purple-900/40 dark:text-purple-200' : user.role === 'admin' ? 'bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-200' : 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-200']">
                  {{ user.role === 'owner' ? '站长' : user.role === 'admin' ? '管理员' : '用户' }}
                </span>
              </td>
              <td class="py-3 px-2">
                <span :class="['inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', user.banned ? 'bg-red-100 text-red-800 dark:bg-red-900/40 dark:text-red-200' : 'bg-green-100 text-green-800 dark:bg-green-900/40 dark:text-green-200']">
                  {{ user.banned ? '已封禁' : '正常' }}
                </span>
              </td>
              <td class="py-3 px-2">
                <div v-if="user.machineCodes && user.machineCodes.length > 0" class="text-xs space-y-1">
                  <div
                    v-for="(mc, idx) in user.machineCodes.slice(0, 2)"
                    :key="idx"
                    class="font-mono text-gray-900 dark:text-gray-100 break-all cursor-help"
                    :title="`设备码: ${mc.machineCode}\n设备名称: ${mc.deviceName || '未设置'}\n创建时间: ${new Date(mc.createdAt).toLocaleString('zh-CN')}\n最后使用: ${mc.lastUsedAt ? new Date(mc.lastUsedAt).toLocaleString('zh-CN') : '未使用'}`"
                  >
                    {{ mc.machineCode.substring(0, 16) }}...
                  </div>
                  <span
                    v-if="user.machineCodes.length > 2"
                    class="text-gray-500 dark:text-gray-400 cursor-help"
                    :title="user.machineCodes.slice(2).map(mc => `${mc.machineCode} (${mc.deviceName || '未命名'})`).join('\n')"
                  >
                    +{{ user.machineCodes.length - 2 }} 更多
                  </span>
                </div>
                <span v-else class="text-gray-500 dark:text-gray-400 text-xs">未绑定</span>
              </td>
              <td class="py-3 px-2">
                <span v-if="user.lastLoginAt" class="text-xs text-gray-600 dark:text-gray-400">
                  {{ new Date(user.lastLoginAt).toLocaleString('zh-CN') }}
                </span>
                <span v-else class="text-gray-400 text-xs">从未登录</span>
              </td>
              <td class="py-3 px-2">
                <div v-if="user.role !== 'owner'" class="flex items-center justify-end gap-1 flex-wrap">
                  <!-- 封禁/解封 -->
                  <button v-if="!user.banned" @click="handleBanUser(user.username)" :disabled="isLoading(`ban_${user.username}`)" class="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-yellow-100 text-yellow-800 hover:bg-yellow-200 dark:bg-yellow-900/40 dark:hover:bg-yellow-900/60 dark:text-yellow-200 transition-colors">
                    <Ban class="w-3 h-3 mr-1" />封禁
                  </button>
                  <button v-else @click="handleUnbanUser(user.username)" :disabled="isLoading(`unban_${user.username}`)" class="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-green-100 text-green-800 hover:bg-green-200 dark:bg-green-900/40 dark:hover:bg-green-900/60 dark:text-green-200 transition-colors">
                    <UserCheck class="w-3 h-3 mr-1" />解封
                  </button>
                  <!-- 设为管理员/取消管理员 (仅站长可操作) -->
                  <template v-if="role === 'owner'">
                    <button v-if="user.role === 'user'" @click="handleSetAdmin(user.username)" :disabled="isLoading(`setAdmin_${user.username}`)" class="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-blue-100 text-blue-800 hover:bg-blue-200 dark:bg-blue-900/40 dark:hover:bg-blue-900/60 dark:text-blue-200 transition-colors">
                      <Shield class="w-3 h-3 mr-1" />设为管理员
                    </button>
                    <button v-if="user.role === 'admin'" @click="handleCancelAdmin(user.username)" :disabled="isLoading(`cancelAdmin_${user.username}`)" class="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-gray-100 text-gray-800 hover:bg-gray-200 dark:bg-gray-700/40 dark:hover:bg-gray-700/60 dark:text-gray-200 transition-colors">
                      <Shield class="w-3 h-3 mr-1" />取消管理员
                    </button>
                  </template>
                  <!-- 修改密码 -->
                  <button @click="handleShowChangePassword(user.username)" class="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-blue-100 text-blue-800 hover:bg-blue-200 dark:bg-blue-900/40 dark:hover:bg-blue-900/60 dark:text-blue-200 transition-colors">
                    <KeyRound class="w-3 h-3 mr-1" />改密
                  </button>
                  <!-- 删除 -->
                  <button @click="handleDeleteUser(user.username)" class="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-red-100 text-red-800 hover:bg-red-200 dark:bg-red-900/40 dark:hover:bg-red-900/60 dark:text-red-200 transition-colors">
                    <Trash2 class="w-3 h-3 mr-1" />删除
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="users.length === 0" class="text-center py-10 text-gray-500 dark:text-gray-400">暂无用户数据</div>
      </div>
    </div>
              </div>
            </div>
            <!-- ═══════════ 视频源配置 ═══════════ -->
            <div class="rounded-xl shadow-sm overflow-hidden bg-white/80 backdrop-blur-md dark:bg-gray-800/50 dark:ring-1 dark:ring-gray-700">
                <button @click="toggleTab('sources')" class="w-full px-6 py-4 flex items-center justify-between bg-gray-50/70 dark:bg-gray-800/60 hover:bg-gray-100/80 dark:hover:bg-gray-700/60 transition-colors">
                <div class="flex items-center gap-3">
                  <Video class="w-5 h-5 text-gray-600 dark:text-gray-400" />
                  <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100">视频源配置</h3>
    </div>
                <component :is="expandedTabs.sources ? ChevronUp : ChevronDown" class="w-5 h-5 text-gray-500 dark:text-gray-400" />
  </button>
                <div v-if="expandedTabs.sources" class="px-6 py-4">
                <div v-if="isLoading('fetchSources')" class="flex justify-center py-10">
      <Loader2 class="w-8 h-8 animate-spin text-blue-500" />
    </div>
                <div v-else>
      <!-- 操作栏 -->
                <div class="flex justify-between items-center mb-4">
                <span class="text-sm text-gray-500 dark:text-gray-400">视频源列表</span>
                <div class="flex items-center gap-2">
                <input v-model="validationKeyword" type="text" placeholder="检测关键词" class="w-28 px-2.5 py-1.5 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500" />
                <button @click="handleBatchValidation" :disabled="isBatchValidating || videoSources.length === 0" class="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg transition-colors disabled:opacity-50">
            <ShieldCheck class="w-4 h-4" :class="{ 'animate-spin': isBatchValidating }" />
            {{ isBatchValidating ? '检测中...' : '有效性检测' }}
          </button>
                <button @click="showAddSource = !showAddSource" class="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-colors">
            <Plus class="w-4 h-4" />
            {{ showAddSource ? '取消' : '添加视频源' }}
          </button>
        </div>
      </div>
      <!-- 添加视频源表单 -->
                <div v-if="showAddSource" class="mb-4 p-4 rounded-xl bg-gray-50 dark:bg-gray-900 border border-gray-200 dark:border-gray-700 space-y-3">
                <input v-model="newSource.name" placeholder="视频源名称" class="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                <input v-model="newSource.api" placeholder="API 地址" class="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                <input v-model="newSource.detail" placeholder="详情接口（可选）" class="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                <div class="flex items-center gap-2">
                <button @click="handleValidateSource(newSource.api, newSource.name, true)" :disabled="!newSource.api || isNewSourceValidating" class="px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-sm font-medium transition-colors disabled:opacity-50">
            {{ isNewSourceValidating ? '检测中...' : '有效性检测' }}
          </button>
                <button @click="handleAddSource" :disabled="isLoading('addSource')" class="px-3 py-1.5 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium transition-colors disabled:opacity-50">
            {{ isLoading('addSource') ? '添加中...' : '确认添加' }}
          </button>
        </div>
        <!-- 验证结果显示 -->
                <div v-if="newSourceValidation.status" class="text-sm px-3 py-2 rounded-lg" :class="{
          'bg-blue-50 dark:bg-blue-900/20 text-blue-700 dark:text-blue-300': newSourceValidation.status === 'validating',
          'bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-300': newSourceValidation.status === 'valid',
          'bg-yellow-50 dark:bg-yellow-900/20 text-yellow-700 dark:text-yellow-300': newSourceValidation.status === 'no_results',
          'bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-300': newSourceValidation.status === 'invalid',
        }">
          {{ newSourceValidation.status === 'validating' ? '⟳' : newSourceValidation.status === 'valid' ? '✓' : newSourceValidation.status === 'no_results' ? '⚠' : '✗' }}
          {{ newSourceValidation.message }}
        </div>
      </div>
      <!-- 编辑视频源表单 -->
                <div v-if="editingSource" class="mb-4 p-4 rounded-xl bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 space-y-3">
                <div class="flex items-center justify-between">
                <span class="text-sm font-medium text-gray-700 dark:text-gray-300">编辑视频源: {{ editingSource.name }}</span>
                <button @click="editingSource = null; editSourceValidation = { status: null, message: '' }" class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"><X class="w-4 h-4" /></button>
        </div>
                <input v-model="editingSource.name" placeholder="视频源名称" class="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                <input v-model="editingSource.api" placeholder="API 地址" class="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                <input v-model="editingSource.detail" placeholder="详情接口（可选）" class="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                <div class="flex gap-2">
                <button @click="handleValidateSource(editingSource.api, editingSource.name, false)" :disabled="!editingSource.api" class="px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-sm font-medium transition-colors disabled:opacity-50">
            {{ editSourceValidation.status === 'validating' ? '检测中...' : '有效性检测' }}
          </button>
                <button @click="handleEditSource" :disabled="isLoading('editSource')" class="px-3 py-1.5 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium transition-colors disabled:opacity-50">
            {{ isLoading('editSource') ? '保存中...' : '保存修改' }}
          </button>
                <button @click="editingSource = null; editSourceValidation = { status: null, message: '' }" class="px-3 py-1.5 rounded-lg bg-gray-600 hover:bg-gray-700 text-white text-sm font-medium transition-colors">取消</button>
        </div>
        <!-- 验证结果显示 -->
                <div v-if="editSourceValidation.status" class="text-sm px-3 py-2 rounded-lg" :class="{
          'bg-blue-50 dark:bg-blue-900/20 text-blue-700 dark:text-blue-300': editSourceValidation.status === 'validating',
          'bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-300': editSourceValidation.status === 'valid',
          'bg-yellow-50 dark:bg-yellow-900/20 text-yellow-700 dark:text-yellow-300': editSourceValidation.status === 'no_results',
          'bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-300': editSourceValidation.status === 'invalid',
        }">
          {{ editSourceValidation.status === 'validating' ? '⟳' : editSourceValidation.status === 'valid' ? '✓' : editSourceValidation.status === 'no_results' ? '⚠' : '✗' }}
          {{ editSourceValidation.message }}
        </div>
      </div>
      <!-- 视频源表格 -->
                <div class="overflow-x-auto">
                <table class="w-full text-sm">
                <thead>
                <tr class="border-b border-gray-200 dark:border-gray-700">
                <th class="text-left py-3 px-2 font-medium text-gray-500 dark:text-gray-400">名称</th>
                <th class="text-left py-3 px-2 font-medium text-gray-500 dark:text-gray-400 hidden sm:table-cell">API</th>
                <th class="text-center py-3 px-2 font-medium text-gray-500 dark:text-gray-400 hidden sm:table-cell">有效性</th>
                <th class="text-center py-3 px-2 font-medium text-gray-500 dark:text-gray-400">测速</th>
                <th class="text-center py-3 px-2 font-medium text-gray-500 dark:text-gray-400">状态</th>
                <th class="text-right py-3 px-2 font-medium text-gray-500 dark:text-gray-400">操作</th>
            </tr>
          </thead>
                <tbody>
                <tr v-for="source in videoSources" :key="source.key" class="border-b border-gray-100 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors">
                <td class="py-3 px-2 font-medium text-gray-900 dark:text-gray-100">{{ source.name }}</td>
                <td class="py-3 px-2 text-gray-500 dark:text-gray-400 truncate max-w-[200px] hidden sm:table-cell">{{ source.api }}</td>
              <!-- 有效性列 -->
                <td class="py-3 px-2 text-center hidden sm:table-cell">
                <span v-if="!validationResults[source.key]" class="px-2 py-1 text-xs rounded-full bg-gray-100 dark:bg-gray-900/20 text-gray-600 dark:text-gray-400">
                  未检测
                </span>
                <span v-else-if="validationResults[source.key].status === 'validating'" class="px-2 py-1 text-xs rounded-full bg-blue-100 dark:bg-blue-900/20 text-blue-800 dark:text-blue-300">
                  ⟳ 检测中
                </span>
                <span v-else-if="validationResults[source.key].status === 'valid'" class="px-2 py-1 text-xs rounded-full bg-green-100 dark:bg-green-900/20 text-green-800 dark:text-green-300" :title="validationResults[source.key].message">
                  ✓ 有效
                </span>
                <span v-else-if="validationResults[source.key].status === 'no_results'" class="px-2 py-1 text-xs rounded-full bg-yellow-100 dark:bg-yellow-900/20 text-yellow-800 dark:text-yellow-300" :title="validationResults[source.key].message">
                  ⚠ 无法搜索
                </span>
                <span v-else class="px-2 py-1 text-xs rounded-full bg-red-100 dark:bg-red-900/20 text-red-800 dark:text-red-300" :title="validationResults[source.key].message">
                  ✗ 无效
                </span>
              </td>
              <!-- 测速列 -->
                <td class="py-3 px-2 text-center">
                <template v-if="!speedTestResults[source.key]">
                  <button @click="handleSpeedTest(source.key, source.api)" class="px-2 py-1 text-xs rounded-full font-medium bg-blue-100 text-blue-800 hover:bg-blue-200 dark:bg-blue-900/40 dark:hover:bg-blue-900/60 dark:text-blue-200 transition-colors cursor-pointer">
                    <Zap class="w-3 h-3 inline mr-0.5" />测速
                  </button>
                </template>
                <template v-else-if="speedTestResults[source.key].testing">
                  <span class="px-2 py-1 text-xs rounded-full bg-blue-100 dark:bg-blue-900/20 text-blue-800 dark:text-blue-300">
                    <Loader2 class="w-3 h-3 inline animate-spin mr-0.5" />测速中
                  </span>
                </template>
                <template v-else-if="speedTestResults[source.key].success">
                  <button @click="handleSpeedTest(source.key, source.api)" class="px-2 py-1 text-xs rounded-full cursor-pointer hover:opacity-80 transition-opacity" :class="
                    speedTestResults[source.key].responseTime < 1000
                      ? 'bg-green-100 dark:bg-green-900/20 text-green-800 dark:text-green-300'
                      : speedTestResults[source.key].responseTime < 3000
                        ? 'bg-yellow-100 dark:bg-yellow-900/20 text-yellow-800 dark:text-yellow-300'
                        : 'bg-red-100 dark:bg-red-900/20 text-red-800 dark:text-red-300'
                  " :title="`点击重新测速 (${speedTestResults[source.key].responseTime}ms)`">
                    {{ speedTestResults[source.key].responseTime }}ms
                  </button>
                </template>
                <template v-else>
                  <button @click="handleSpeedTest(source.key, source.api)" class="px-2 py-1 text-xs rounded-full bg-red-100 dark:bg-red-900/20 text-red-800 dark:text-red-300 cursor-pointer hover:opacity-80 transition-opacity" :title="`测速失败: ${speedTestResults[source.key].error || '未知错误'}，点击重试`">
                    ✗ 失败
                  </button>
                </template>
              </td>
                <td class="py-3 px-2 text-center">
                <button @click="handleToggleSource(source)" :disabled="isLoading(`toggle_${source.key}`)" :class="['relative inline-flex h-6 w-11 items-center rounded-full transition-colors', !source.disabled ? 'bg-blue-600' : 'bg-gray-200 dark:bg-gray-700']">
                  <span :class="['inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform', !source.disabled ? 'translate-x-6' : 'translate-x-1']" />
                </button>
              </td>
                <td class="py-3 px-2">
                <div class="flex items-center justify-end gap-1">
                  <button @click="editingSource = { ...source }; editSourceValidation = { status: null, message: '' }" class="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-blue-100 text-blue-800 hover:bg-blue-200 dark:bg-blue-900/40 dark:hover:bg-blue-900/60 dark:text-blue-200 transition-colors">
                    <Pencil class="w-3 h-3 mr-1" />编辑
                  </button>
                  <button @click="handleDeleteSource(source.key)" :disabled="isLoading(`deleteSource_${source.key}`)" class="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-red-100 text-red-800 hover:bg-red-200 dark:bg-red-900/40 dark:hover:bg-red-900/60 dark:text-red-200 transition-colors">
                    <Trash2 class="w-3 h-3 mr-1" />删除
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="videoSources.length === 0" class="text-center py-10 text-gray-500 dark:text-gray-400">暂无视频源</div>
      </div>
    </div>
  </div>
            </div>

            <!-- ═══════════ 分类配置 ═══════════ -->
            <div class="rounded-xl shadow-sm overflow-hidden bg-white/80 backdrop-blur-md dark:bg-gray-800/50 dark:ring-1 dark:ring-gray-700">
                <button @click="toggleTab('categories')" class="w-full px-6 py-4 flex items-center justify-between bg-gray-50/70 dark:bg-gray-800/60 hover:bg-gray-100/80 dark:hover:bg-gray-700/60 transition-colors">
                <div class="flex items-center gap-3">
                  <FolderOpen class="w-5 h-5 text-gray-600 dark:text-gray-400" />
                  <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100">分类配置</h3>
    </div>
                <component :is="expandedTabs.categories ? ChevronUp : ChevronDown" class="w-5 h-5 text-gray-500 dark:text-gray-400" />
  </button>
                <div v-if="expandedTabs.categories" class="px-6 py-4 space-y-6">
    <!-- 添加分类表单 -->
                <div class="p-4 rounded-xl bg-gray-50 dark:bg-gray-900 border border-gray-200 dark:border-gray-700 space-y-3">
                <div class="text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">添加分类</div>
                <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <input v-model="newCategory.name" placeholder="分类名称" class="px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
        <select v-model="newCategory.type" class="px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
          <option value="movie">电影</option>
          <option value="tv">电视剧</option>
        </select>
                <input v-model="newCategory.query" placeholder="搜索关键词" class="px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
      </div>
                <button @click="handleAddCategory" :disabled="isLoading('addCategory')" class="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-colors disabled:opacity-50">
        <Plus class="w-4 h-4" />
        {{ isLoading('addCategory') ? '添加中...' : '添加分类' }}
      </button>
    </div>
    <!-- 分类表格 -->
                <div class="overflow-x-auto">
                <table class="w-full text-sm">
                <thead>
                <tr class="border-b border-gray-200 dark:border-gray-700">
                <th class="text-left py-3 px-2 font-medium text-gray-500 dark:text-gray-400">名称</th>
                <th class="text-left py-3 px-2 font-medium text-gray-500 dark:text-gray-400">类型</th>
                <th class="text-left py-3 px-2 font-medium text-gray-500 dark:text-gray-400 hidden sm:table-cell">关键词</th>
                <th class="text-center py-3 px-2 font-medium text-gray-500 dark:text-gray-400">状态</th>
                <th class="text-right py-3 px-2 font-medium text-gray-500 dark:text-gray-400">操作</th>
          </tr>
        </thead>
                <tbody>
                <tr v-for="(cat, index) in categories" :key="index" class="border-b border-gray-100 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors">
                <td class="py-3 px-2 font-medium text-gray-900 dark:text-gray-100">{{ cat.name }}</td>
                <td class="py-3 px-2">
                <span :class="['inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', cat.type === 'movie' ? 'bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-200' : 'bg-purple-100 text-purple-800 dark:bg-purple-900/40 dark:text-purple-200']">
                {{ cat.type === 'movie' ? '电影' : '电视剧' }}
              </span>
            </td>
                <td class="py-3 px-2 text-gray-500 dark:text-gray-400 hidden sm:table-cell">{{ cat.query }}</td>
                <td class="py-3 px-2 text-center">
                <button @click="handleToggleCategory(index, !!cat.disabled)" :disabled="isLoading(`toggleCategory_${index}`)" :class="['relative inline-flex h-6 w-11 items-center rounded-full transition-colors', !cat.disabled ? 'bg-blue-600' : 'bg-gray-200 dark:bg-gray-700']">
                <span :class="['inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform', !cat.disabled ? 'translate-x-6' : 'translate-x-1']" />
              </button>
            </td>
                <td class="py-3 px-2">
                <div class="flex items-center justify-end">
                <button v-if="cat.from === 'custom'" @click="handleDeleteCategory(index)" :disabled="isLoading(`deleteCategory_${index}`)" class="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-red-100 text-red-800 hover:bg-red-200 dark:bg-red-900/40 dark:hover:bg-red-900/60 dark:text-red-200 transition-colors">
                  <Trash2 class="w-3 h-3 mr-1" />删除
                </button>
                <span v-else class="text-xs text-gray-400">配置来源</span>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-if="categories.length === 0" class="text-center py-10 text-gray-500 dark:text-gray-400">暂无分类</div>
    </div>
              </div>
            </div>

            <!-- ═══════════ 数据迁移（仅站长） ═══════════ -->
            <div v-if="role === 'owner'" class="rounded-xl shadow-sm overflow-hidden bg-white/80 backdrop-blur-md dark:bg-gray-800/50 dark:ring-1 dark:ring-gray-700">
              <button @click="toggleTab('dataMigration')" class="w-full px-6 py-4 flex items-center justify-between bg-gray-50/70 dark:bg-gray-800/60 hover:bg-gray-100/80 dark:hover:bg-gray-700/60 transition-colors">
                <div class="flex items-center gap-3">
                  <Database class="w-5 h-5 text-gray-600 dark:text-gray-400" />
                  <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100">数据迁移</h3>
    </div>
                <component :is="expandedTabs.dataMigration ? ChevronUp : ChevronDown" class="w-5 h-5 text-gray-500 dark:text-gray-400" />
  </button>
              <div v-if="expandedTabs.dataMigration" class="px-6 py-4 space-y-6">
    <!-- 警告 -->
    <div class="flex items-start gap-3 p-4 rounded-xl bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800">
      <AlertTriangle class="w-5 h-5 text-yellow-600 dark:text-yellow-400 flex-shrink-0 mt-0.5" />
      <div class="text-sm text-yellow-700 dark:text-yellow-300">
        <p class="font-medium">注意</p>
        <p>导入数据将清空现有的播放记录和收藏数据，此操作不可恢复。请确保已备份重要数据。</p>
      </div>
    </div>
    <!-- 两列布局 -->
    <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
      <!-- 导出 -->
      <div class="p-4 rounded-xl border-2 border-blue-200 dark:border-blue-800 bg-blue-50/50 dark:bg-blue-900/10 space-y-4">
        <div class="flex items-center gap-2">
          <Download class="w-5 h-5 text-blue-600 dark:text-blue-400" />
          <h4 class="font-medium text-blue-900 dark:text-blue-100">导出数据</h4>
        </div>
        <p class="text-sm text-blue-700 dark:text-blue-300">将所有数据加密导出为 .dat 文件</p>
        <input v-model="exportPassword" type="password" placeholder="设置加密密码" class="w-full px-3 py-2 rounded-lg border border-blue-300 dark:border-blue-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
        <button @click="handleExportData" :disabled="isLoading('exportData')" class="w-full inline-flex items-center justify-center gap-2 px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium transition-colors disabled:opacity-50">
          <Download class="w-4 h-4" />
          {{ isLoading('exportData') ? '导出中...' : '导出数据' }}
        </button>
      </div>
      <!-- 导入 -->
      <div class="p-4 rounded-xl border-2 border-red-200 dark:border-red-800 bg-red-50/50 dark:bg-red-900/10 space-y-4">
        <div class="flex items-center gap-2">
          <Upload class="w-5 h-5 text-red-600 dark:text-red-400" />
          <h4 class="font-medium text-red-900 dark:text-red-100">导入数据</h4>
        </div>
        <p class="text-sm text-red-700 dark:text-red-300">从 .dat 备份文件恢复数据</p>
        <input type="file" accept=".dat" @change="handleImportFileChange" class="w-full text-sm text-gray-500 dark:text-gray-400 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-sm file:font-medium file:bg-red-100 file:text-red-700 hover:file:bg-red-200 dark:file:bg-red-900/40 dark:file:text-red-200" />
        <input v-model="importPassword" type="password" placeholder="输入解密密码" class="w-full px-3 py-2 rounded-lg border border-red-300 dark:border-red-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 text-sm focus:outline-none focus:ring-2 focus:ring-red-500" />
        <button @click="handleImportData" :disabled="isLoading('importData')" class="w-full inline-flex items-center justify-center gap-2 px-4 py-2 rounded-lg bg-red-600 hover:bg-red-700 text-white text-sm font-medium transition-colors disabled:opacity-50">
          <Upload class="w-4 h-4" />
          {{ isLoading('importData') ? '导入中...' : '导入数据' }}
        </button>
      </div>
    </div>
  </div>
</div>

          </div>
        </template>
      </div>
    </div>
    <!-- 删除用户确认弹窗 -->
    <Teleport to="body">
      <div v-if="showDeleteUserModal && deletingUser" class="fixed inset-0 bg-black bg-opacity-50 z-50 flex items-center justify-center p-4" @click="showDeleteUserModal = false; deletingUser = null">
        <div class="bg-white dark:bg-gray-800 rounded-lg shadow-xl max-w-md w-full" @click.stop>
          <div class="p-6">
            <div class="flex items-center justify-between mb-6">
              <h3 class="text-xl font-semibold text-gray-900 dark:text-gray-100">确认删除用户</h3>
              <button @click="showDeleteUserModal = false; deletingUser = null" class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors">
                <X class="w-6 h-6" />
              </button>
            </div>
            <div class="mb-6">
              <div class="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-4">
                <p class="text-sm text-red-700 dark:text-red-400">
                  删除用户 <span class="font-bold">{{ deletingUser }}</span> 将同时删除其搜索历史、播放记录和收藏夹，此操作不可恢复！
                </p>
              </div>
            </div>
            <div class="flex justify-end space-x-3">
              <button @click="showDeleteUserModal = false; deletingUser = null" class="px-6 py-2.5 text-sm font-medium bg-gray-600 hover:bg-gray-700 text-white rounded-lg transition-colors">取消</button>
              <button @click="handleConfirmDeleteUser" :disabled="isLoading('deleteUser')" class="px-6 py-2.5 text-sm font-medium bg-red-600 hover:bg-red-700 text-white rounded-lg transition-colors disabled:opacity-50">
                {{ isLoading('deleteUser') ? '删除中...' : '确认删除' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </PageLayout>
</template>

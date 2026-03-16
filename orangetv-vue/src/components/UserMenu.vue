<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { CURRENT_VERSION } from '@/lib/version'
import { checkForUpdates, UpdateStatus } from '@/lib/version_check'
import { changePassword } from '@/api/auth'
import request from '@/api/index'
import {
  Camera, Check, ChevronDown, ExternalLink, KeyRound, LogOut,
  Settings, Shield, User, X, Upload,
} from 'lucide-vue-next'
import VersionPanel from './VersionPanel.vue'

const router = useRouter()
const authStore = useAuthStore()
const toast = useToast()

// --- 面板状态 ---
const isOpen = ref(false)
const isSettingsOpen = ref(false)
const isChangePasswordOpen = ref(false)
const isVersionPanelOpen = ref(false)
const isChangeAvatarOpen = ref(false)
const isMobile = ref(false)
const avatarUrl = ref('')
const isUploadingAvatar = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)

// 裁剪相关
const selectedImage = ref('')
const showCropper = ref(false)
const imageRef = ref<HTMLImageElement | null>(null)
// 像素值裁剪框 (相对于 img 元素的渲染尺寸)
const cropPx = ref({ x: 0, y: 0, size: 0 })
const imgSize = ref({ w: 0, h: 0 })
const isDragging = ref(false)
const dragType = ref<'move' | 'nw' | 'ne' | 'sw' | 'se'>('move')
const dragStart = ref({ x: 0, y: 0, crop: { x: 0, y: 0, size: 0 } })

// 修改密码
const newPassword = ref('')
const confirmPassword = ref('')
const passwordLoading = ref(false)
const passwordError = ref('')

// 版本检查
const updateStatus = ref<UpdateStatus | null>(null)
const isChecking = ref(true)

// --- 设置相关 ---
const defaultAggregateSearch = ref(true)
const doubanProxyUrl = ref('')
const enableOptimization = ref(true)
const fluidSearch = ref(true)
const liveDirectConnect = ref(false)
const doubanDataSource = ref('cmliussss-cdn-tencent')
const doubanImageProxyType = ref('cmliussss-cdn-tencent')
const doubanImageProxyUrl = ref('')
const isDoubanDropdownOpen = ref(false)
const isDoubanImageProxyDropdownOpen = ref(false)

const doubanDataSourceOptions = [
  { value: 'direct', label: '直连（服务器直接请求豆瓣）' },
  { value: 'cors-proxy-zwei', label: 'Cors Proxy By Zwei' },
  { value: 'cmliussss-cdn-tencent', label: '豆瓣 CDN By CMLiussss（腾讯云）' },
  { value: 'cmliussss-cdn-ali', label: '豆瓣 CDN By CMLiussss（阿里云）' },
  { value: 'custom', label: '自定义代理' },
]

const doubanImageProxyTypeOptions = [
  { value: 'direct', label: '直连（浏览器直接请求豆瓣）' },
  { value: 'server', label: '服务器代理（由服务器代理请求豆瓣）' },
  { value: 'img3', label: '豆瓣官方精品 CDN（阿里云）' },
  { value: 'cmliussss-cdn-tencent', label: '豆瓣 CDN By CMLiussss（腾讯云）' },
  { value: 'cmliussss-cdn-ali', label: '豆瓣 CDN By CMLiussss（阿里云）' },
  { value: 'custom', label: '自定义代理' },
]

// --- 计算属性 ---
const showAdminPanel = computed(() => authStore.isAdmin)
const showChangePassword = computed(() => authStore.user?.role !== 'owner')
const storageType = computed(() => 'database')

function getRoleText(role?: string) {
  switch (role) {
    case 'owner': return '站长'
    case 'admin': return '管理员'
    case 'user': return '用户'
    default: return ''
  }
}

function getRoleClass(role?: string) {
  switch (role) {
    case 'owner': return 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-300'
    case 'admin': return 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300'
    default: return 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300'
  }
}

function openUrl(url: string) { window.open(url, '_blank') }

function getThanksInfo(ds: string) {
  switch (ds) {
    case 'cors-proxy-zwei': return { text: 'Thanks to @Zwei', url: 'https://github.com/bestzwei' }
    case 'cmliussss-cdn-tencent':
    case 'cmliussss-cdn-ali': return { text: 'Thanks to @CMLiussss', url: 'https://github.com/cmliu' }
    default: return null
  }
}

// --- 滚动锁定 ---
watch([isSettingsOpen, isChangePasswordOpen, isChangeAvatarOpen], ([s, p, a]) => {
  if (s || p || a) {
    document.body.style.overflow = 'hidden'
    document.documentElement.style.overflow = 'hidden'
  } else {
    document.body.style.overflow = ''
    document.documentElement.style.overflow = ''
  }
})

// --- 生命周期 ---
function checkMobile() { isMobile.value = window.innerWidth < 768 }

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)

  // 读取本地设置
  loadSettings()

  // 获取头像
  if (authStore.user?.username) fetchUserAvatar(authStore.user.username)

  // 版本检查
  checkForUpdates().then(s => { updateStatus.value = s }).catch(() => {}).finally(() => { isChecking.value = false })
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
  document.body.style.overflow = ''
  document.documentElement.style.overflow = ''
})

function loadSettings() {
  const s = (k: string) => localStorage.getItem(k)
  if (s('defaultAggregateSearch') !== null) defaultAggregateSearch.value = JSON.parse(s('defaultAggregateSearch')!)
  if (s('doubanDataSource') !== null) doubanDataSource.value = s('doubanDataSource')!
  if (s('doubanProxyUrl') !== null) doubanProxyUrl.value = s('doubanProxyUrl')!
  if (s('doubanImageProxyType') !== null) doubanImageProxyType.value = s('doubanImageProxyType')!
  if (s('doubanImageProxyUrl') !== null) doubanImageProxyUrl.value = s('doubanImageProxyUrl')!
  if (s('enableOptimization') !== null) enableOptimization.value = JSON.parse(s('enableOptimization')!)
  if (s('fluidSearch') !== null) fluidSearch.value = JSON.parse(s('fluidSearch')!)
  if (s('liveDirectConnect') !== null) liveDirectConnect.value = JSON.parse(s('liveDirectConnect')!)
}

// --- 菜单操作 ---
function handleMenuClick() { isOpen.value = !isOpen.value }
function handleCloseMenu() { isOpen.value = false }

function handleLogout() {
  handleCloseMenu()
  authStore.logout()
  router.push('/login')
}

function handleAdminPanel() { handleCloseMenu(); router.push('/admin') }

function handleSettings() { isOpen.value = false; isSettingsOpen.value = true }
function handleCloseSettings() { isSettingsOpen.value = false }

// --- 修改密码 ---
function handleChangePassword() {
  isOpen.value = false
  isChangePasswordOpen.value = true
  newPassword.value = ''
  confirmPassword.value = ''
  passwordError.value = ''
}
function handleCloseChangePassword() {
  isChangePasswordOpen.value = false
  newPassword.value = ''
  confirmPassword.value = ''
  passwordError.value = ''
}
async function handleSubmitChangePassword() {
  passwordError.value = ''
  if (!newPassword.value) { passwordError.value = '新密码不得为空'; return }
  if (newPassword.value !== confirmPassword.value) { passwordError.value = '两次输入的密码不一致'; return }
  passwordLoading.value = true
  try {
    await changePassword({ oldPassword: '', newPassword: newPassword.value })
    // 成功（拦截器已解包，走到这里说明 code===200）
    isChangePasswordOpen.value = false
    authStore.logout()
    router.push('/login')
  } catch (err: any) {
    passwordError.value = err?.message || '修改密码失败'
  }
  finally { passwordLoading.value = false }
}

// --- 头像 ---
async function fetchUserAvatar(username: string) {
  try {
    const res = await request.get(`/avatar?user=${encodeURIComponent(username)}`) as any
    if (res?.avatar) avatarUrl.value = res.avatar
  } catch { /* ignore */ }
}
function handleChangeAvatar() {
  isOpen.value = false; isChangeAvatarOpen.value = true
  selectedImage.value = ''; showCropper.value = false
}
function handleCloseChangeAvatar() {
  isChangeAvatarOpen.value = false; selectedImage.value = ''; showCropper.value = false
}
function handleOpenFileSelector() { fileInputRef.value?.click() }

function handleAvatarSelected(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) { toast.error('请选择图片文件'); return }
  if (file.size > 2 * 1024 * 1024) { toast.error('图片大小不能超过 2MB'); return }
  const reader = new FileReader()
  reader.onload = (ev) => {
    if (ev.target?.result) { selectedImage.value = ev.target.result.toString(); showCropper.value = true }
  }
  reader.readAsDataURL(file)
}

function onImageLoad(e: Event) {
  const img = e.target as HTMLImageElement
  // img 元素此时的渲染尺寸 (没有 object-contain, 就是实际尺寸)
  const w = img.clientWidth
  const h = img.clientHeight
  imgSize.value = { w, h }
  // 居中正方形裁剪区域
  const minDim = Math.min(w, h)
  const size = minDim * 0.8
  cropPx.value = { x: (w - size) / 2, y: (h - size) / 2, size }
}

function startDrag(e: MouseEvent | TouchEvent, type: 'move' | 'nw' | 'ne' | 'sw' | 'se') {
  e.preventDefault()
  isDragging.value = true
  dragType.value = type
  const pt = 'touches' in e ? e.touches[0] : e
  dragStart.value = { x: pt.clientX, y: pt.clientY, crop: { ...cropPx.value } }
  document.addEventListener('mousemove', onDrag)
  document.addEventListener('mouseup', stopDrag)
  document.addEventListener('touchmove', onDrag, { passive: false })
  document.addEventListener('touchend', stopDrag)
}

function onDrag(e: MouseEvent | TouchEvent) {
  if (!isDragging.value) return
  e.preventDefault()
  const pt = 'touches' in e ? e.touches[0] : e
  const dx = pt.clientX - dragStart.value.x
  const dy = pt.clientY - dragStart.value.y
  const sc = dragStart.value.crop
  const { w, h } = imgSize.value
  const minSize = 30

  if (dragType.value === 'move') {
    let nx = sc.x + dx
    let ny = sc.y + dy
    nx = Math.max(0, Math.min(w - sc.size, nx))
    ny = Math.max(0, Math.min(h - sc.size, ny))
    cropPx.value = { x: nx, y: ny, size: sc.size }
  } else {
    // 角落拖拽 - 保持正方形
    const delta = Math.abs(dx) > Math.abs(dy) ? dx : dy
    let newX = sc.x, newY = sc.y, newSize = sc.size
    if (dragType.value === 'se') {
      newSize = Math.max(minSize, Math.min(w - sc.x, h - sc.y, sc.size + delta))
    } else if (dragType.value === 'nw') {
      newSize = Math.max(minSize, sc.size - delta)
      newX = sc.x + sc.size - newSize
      newY = sc.y + sc.size - newSize
      if (newX < 0) { newX = 0; newSize = sc.x + sc.size; newY = sc.y + sc.size - newSize }
      if (newY < 0) { newY = 0; newSize = sc.y + sc.size; newX = sc.x + sc.size - newSize }
    } else if (dragType.value === 'ne') {
      newSize = Math.max(minSize, Math.min(w - sc.x, sc.size + delta))
      newY = sc.y + sc.size - newSize
      if (newY < 0) { newY = 0; newSize = sc.y + sc.size }
    } else if (dragType.value === 'sw') {
      newSize = Math.max(minSize, sc.size - delta)
      newX = sc.x + sc.size - newSize
      if (newX < 0) { newX = 0; newSize = sc.x + sc.size }
      if (sc.y + newSize > h) { newSize = h - sc.y }
    }
    cropPx.value = { x: newX, y: newY, size: newSize }
  }
}

function stopDrag() {
  isDragging.value = false
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', stopDrag)
  document.removeEventListener('touchmove', onDrag)
  document.removeEventListener('touchend', stopDrag)
}

function getCroppedImage(image: HTMLImageElement): Promise<string> {
  const canvas = document.createElement('canvas')
  const ctx = canvas.getContext('2d')!
  const scaleX = image.naturalWidth / image.clientWidth
  const scaleY = image.naturalHeight / image.clientHeight
  const outputSize = 200
  canvas.width = outputSize; canvas.height = outputSize
  ctx.imageSmoothingEnabled = true; ctx.imageSmoothingQuality = 'high'
  ctx.drawImage(image,
    cropPx.value.x * scaleX, cropPx.value.y * scaleY,
    cropPx.value.size * scaleX, cropPx.value.size * scaleY,
    0, 0, outputSize, outputSize)
  return new Promise(resolve => {
    canvas.toBlob(blob => {
      if (blob) { const r = new FileReader(); r.onload = () => resolve(r.result as string); r.readAsDataURL(blob) }
    }, 'image/jpeg', 0.9)
  })
}

async function handleConfirmCrop() {
  if (!imageRef.value || !cropPx.value.size || !authStore.user?.username) return
  try {
    isUploadingAvatar.value = true
    const base64 = await getCroppedImage(imageRef.value)
    await request.post('/avatar', { avatar: base64, targetUser: authStore.user.username })
    avatarUrl.value = base64; toast.success('头像上传成功'); handleCloseChangeAvatar()
  } catch (err: any) { toast.error(err?.message || '头像上传失败') }
  finally { isUploadingAvatar.value = false }
}

function handleResetCrop() {
  showCropper.value = false; selectedImage.value = ''
  cropPx.value = { x: 0, y: 0, size: 0 }
  if (fileInputRef.value) fileInputRef.value.value = ''
}

// --- 设置处理 ---
function saveSetting(key: string, value: any) { localStorage.setItem(key, typeof value === 'string' ? value : JSON.stringify(value)) }
function handleAggregateToggle(v: boolean) { defaultAggregateSearch.value = v; saveSetting('defaultAggregateSearch', v) }
function handleOptimizationToggle(v: boolean) { enableOptimization.value = v; saveSetting('enableOptimization', v) }
function handleFluidSearchToggle(v: boolean) { fluidSearch.value = v; saveSetting('fluidSearch', v) }
function handleLiveDirectConnectToggle(v: boolean) { liveDirectConnect.value = v; saveSetting('liveDirectConnect', v) }
function handleDoubanDataSourceChange(v: string) { doubanDataSource.value = v; saveSetting('doubanDataSource', v); isDoubanDropdownOpen.value = false }
function handleDoubanProxyUrlChange(v: string) { doubanProxyUrl.value = v; saveSetting('doubanProxyUrl', v) }
function handleDoubanImageProxyTypeChange(v: string) { doubanImageProxyType.value = v; saveSetting('doubanImageProxyType', v); isDoubanImageProxyDropdownOpen.value = false }
function handleDoubanImageProxyUrlChange(v: string) { doubanImageProxyUrl.value = v; saveSetting('doubanImageProxyUrl', v) }

function handleResetSettings() {
  defaultAggregateSearch.value = true; enableOptimization.value = true; fluidSearch.value = true
  liveDirectConnect.value = false; doubanDataSource.value = 'cmliussss-cdn-tencent'
  doubanProxyUrl.value = ''; doubanImageProxyType.value = 'cmliussss-cdn-tencent'; doubanImageProxyUrl.value = ''
  const keys = ['defaultAggregateSearch','enableOptimization','fluidSearch','liveDirectConnect','doubanDataSource','doubanProxyUrl','doubanImageProxyType','doubanImageProxyUrl']
  const vals = [true, true, true, false, 'cmliussss-cdn-tencent', '', 'cmliussss-cdn-tencent', '']
  keys.forEach((k, i) => saveSetting(k, vals[i]))
}

// 点击外部关闭下拉框
function onDocClick(e: MouseEvent) {
  const t = e.target as Element
  if (isDoubanDropdownOpen.value && !t.closest('[data-dropdown="douban-datasource"]')) isDoubanDropdownOpen.value = false
  if (isDoubanImageProxyDropdownOpen.value && !t.closest('[data-dropdown="douban-image-proxy"]')) isDoubanImageProxyDropdownOpen.value = false
}
onMounted(() => document.addEventListener('mousedown', onDocClick))
onUnmounted(() => document.removeEventListener('mousedown', onDocClick))
</script>

<template>
  <div class="relative">
    <!-- 触发按钮 -->
    <button
      @click="handleMenuClick"
      :class="[isMobile ? 'w-8 h-8 p-0.5' : 'w-10 h-10 p-0.5', 'rounded-full flex items-center justify-center text-gray-600 hover:bg-gray-200/50 dark:text-gray-300 dark:hover:bg-gray-700/50 transition-colors overflow-hidden']"
      aria-label="User Menu"
    >
      <div v-if="avatarUrl" class="w-full h-full rounded-full overflow-hidden">
        <img :src="avatarUrl" alt="用户头像" class="w-full h-full object-cover" />
      </div>
      <User v-else class="w-6 h-6" />
    </button>
    <div v-if="updateStatus === UpdateStatus.HAS_UPDATE" class="absolute top-[2px] right-[2px] w-2 h-2 bg-yellow-500 rounded-full"></div>
  </div>

  <!-- 菜单面板 Portal -->
  <Teleport to="body">
    <template v-if="isOpen">
      <div class="fixed inset-0 bg-transparent z-[1000]" @click="handleCloseMenu" />
      <div class="fixed top-14 right-4 w-56 bg-white dark:bg-gray-900 rounded-lg shadow-xl z-[1001] border border-gray-200/50 dark:border-gray-700/50 overflow-hidden select-none">
        <!-- 用户信息 -->
        <div class="px-3 py-2.5 border-b border-gray-200 dark:border-gray-700 bg-gradient-to-r from-gray-50 to-gray-100/50 dark:from-gray-800 dark:to-gray-800/50">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-full overflow-hidden flex-shrink-0">
              <img v-if="avatarUrl" :src="avatarUrl" alt="用户头像" class="w-full h-full object-cover" />
              <div v-else class="w-full h-full bg-blue-100 dark:bg-blue-900/40 flex items-center justify-center">
                <User class="w-6 h-6 text-blue-500 dark:text-blue-400" />
              </div>
            </div>
            <div class="flex-1 min-w-0">
              <div class="flex items-center justify-between">
                <span class="text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">当前用户</span>
                <span :class="['inline-flex items-center px-1.5 py-0.5 rounded-full text-xs font-medium', getRoleClass(authStore.user?.role)]">
                  {{ getRoleText(authStore.user?.role || 'user') }}
                </span>
              </div>
              <div class="flex items-center justify-between">
                <div class="font-semibold text-gray-900 dark:text-gray-100 text-sm truncate">{{ authStore.user?.username || 'default' }}</div>
                <div class="text-[10px] text-gray-400 dark:text-gray-500">{{ storageType === 'localstorage' ? '本地' : storageType }}</div>
              </div>
            </div>
          </div>
        </div>
        <!-- 菜单项 -->
        <div class="py-1">
          <button @click="handleSettings" class="w-full px-3 py-2 text-left flex items-center gap-2.5 text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors text-sm">
            <Settings class="w-4 h-4 text-gray-500 dark:text-gray-400" /><span class="font-medium">设置</span>
          </button>
          <button v-if="showAdminPanel" @click="handleAdminPanel" class="w-full px-3 py-2 text-left flex items-center gap-2.5 text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors text-sm">
            <Shield class="w-4 h-4 text-gray-500 dark:text-gray-400" /><span class="font-medium">管理面板</span>
          </button>
          <button @click="handleChangeAvatar" class="w-full px-3 py-2 text-left flex items-center gap-2.5 text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors text-sm">
            <Camera class="w-4 h-4 text-gray-500 dark:text-gray-400" /><span class="font-medium">修改头像</span>
          </button>
          <button v-if="showChangePassword" @click="handleChangePassword" class="w-full px-3 py-2 text-left flex items-center gap-2.5 text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors text-sm">
            <KeyRound class="w-4 h-4 text-gray-500 dark:text-gray-400" /><span class="font-medium">修改密码</span>
          </button>
          <div class="my-1 border-t border-gray-200 dark:border-gray-700"></div>
          <button @click="handleLogout" class="w-full px-3 py-2 text-left flex items-center gap-2.5 text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors text-sm">
            <LogOut class="w-4 h-4" /><span class="font-medium">登出</span>
          </button>
          <div class="my-1 border-t border-gray-200 dark:border-gray-700"></div>
          <button @click="isVersionPanelOpen = true; handleCloseMenu()" class="w-full px-3 py-2 text-center flex items-center justify-center text-gray-500 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors text-xs">
            <div class="flex items-center gap-1">
              <span class="font-mono">v{{ CURRENT_VERSION }}</span>
              <div v-if="!isChecking && updateStatus && updateStatus !== UpdateStatus.FETCH_FAILED"
                :class="['w-2 h-2 rounded-full -translate-y-2', updateStatus === UpdateStatus.HAS_UPDATE ? 'bg-yellow-500' : updateStatus === UpdateStatus.NO_UPDATE ? 'bg-green-400' : '']"
              ></div>
            </div>
          </button>
        </div>
      </div>
    </template>
  </Teleport>

  <!-- 设置面板 -->
  <Teleport to="body">
    <template v-if="isSettingsOpen">
      <div class="fixed inset-0 bg-black/50 backdrop-blur-sm z-[1000]" @click="handleCloseSettings" @touchmove.prevent @wheel.prevent style="touch-action: none" />
      <div class="fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-full max-w-xl max-h-[90vh] bg-white dark:bg-gray-900 rounded-xl shadow-xl z-[1001] flex flex-col">
        <div class="flex-1 p-6 overflow-y-auto" style="touch-action: pan-y; overscroll-behavior: contain">
          <!-- 标题栏 -->
          <div class="flex items-center justify-between mb-6">
            <div class="flex items-center gap-3">
              <h3 class="text-xl font-bold text-gray-800 dark:text-gray-200">本地设置</h3>
              <button @click="handleResetSettings" class="px-2 py-1 text-xs text-red-500 hover:text-red-700 dark:text-red-400 dark:hover:text-red-300 border border-red-200 hover:border-red-300 dark:border-red-800 dark:hover:border-red-700 hover:bg-red-50 dark:hover:bg-red-900/20 rounded transition-colors" title="重置为默认设置">恢复默认</button>
            </div>
            <button @click="handleCloseSettings" class="w-8 h-8 p-1 rounded-full flex items-center justify-center text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors" aria-label="Close">
              <X class="w-full h-full" />
            </button>
          </div>

          <div class="space-y-6">
            <!-- 豆瓣数据源 -->
            <div class="space-y-3">
              <div>
                <h4 class="text-sm font-medium text-gray-700 dark:text-gray-300">豆瓣数据代理</h4>
                <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">选择获取豆瓣数据的方式</p>
              </div>
              <div class="relative" data-dropdown="douban-datasource">
                <button type="button" @click="isDoubanDropdownOpen = !isDoubanDropdownOpen" class="w-full px-3 py-2.5 pr-10 border border-gray-300 dark:border-gray-600 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-green-500 focus:border-green-500 transition-all duration-200 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 shadow-sm hover:border-gray-400 dark:hover:border-gray-500 text-left">
                  {{ doubanDataSourceOptions.find(o => o.value === doubanDataSource)?.label }}
                </button>
                <div class="absolute inset-y-0 right-0 flex items-center pr-3 pointer-events-none">
                  <ChevronDown :class="['w-4 h-4 text-gray-400 dark:text-gray-500 transition-transform duration-200', isDoubanDropdownOpen ? 'rotate-180' : '']" />
                </div>
                <div v-if="isDoubanDropdownOpen" class="absolute z-50 w-full mt-1 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-lg shadow-lg max-h-60 overflow-auto">
                  <button v-for="opt in doubanDataSourceOptions" :key="opt.value" type="button" @click="handleDoubanDataSourceChange(opt.value)"
                    :class="['w-full px-3 py-2.5 text-left text-sm transition-colors duration-150 flex items-center justify-between hover:bg-gray-100 dark:hover:bg-gray-700', doubanDataSource === opt.value ? 'bg-green-50 dark:bg-green-900/20 text-green-600 dark:text-green-400' : 'text-gray-900 dark:text-gray-100']">
                    <span class="truncate">{{ opt.label }}</span>
                    <Check v-if="doubanDataSource === opt.value" class="w-4 h-4 text-green-600 dark:text-green-400 flex-shrink-0 ml-2" />
                  </button>
                </div>
              </div>
              <div v-if="getThanksInfo(doubanDataSource)" class="mt-3">
                <button type="button" @click="openUrl(getThanksInfo(doubanDataSource)!.url)" class="flex items-center justify-center gap-1.5 w-full px-3 text-xs text-gray-500 dark:text-gray-400 cursor-pointer">
                  <span class="font-medium">{{ getThanksInfo(doubanDataSource)!.text }}</span>
                  <ExternalLink class="w-3.5 opacity-70" />
                </button>
              </div>
            </div>
            <!-- 豆瓣代理地址 - 自定义时显示 -->
            <div v-if="doubanDataSource === 'custom'" class="space-y-3">
              <div>
                <h4 class="text-sm font-medium text-gray-700 dark:text-gray-300">豆瓣代理地址</h4>
                <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">自定义代理服务器地址</p>
              </div>
              <input type="text" class="w-full px-3 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-green-500 focus:border-green-500 transition-all duration-200 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 placeholder-gray-500 dark:placeholder-gray-400 shadow-sm hover:border-gray-400 dark:hover:border-gray-500"
                placeholder="例如: https://proxy.example.com/fetch?url=" :value="doubanProxyUrl" @input="handleDoubanProxyUrlChange(($event.target as HTMLInputElement).value)" />
            </div>

            <div class="border-t border-gray-200 dark:border-gray-700"></div>

            <!-- 豆瓣图片代理 -->
            <div class="space-y-3">
              <div>
                <h4 class="text-sm font-medium text-gray-700 dark:text-gray-300">豆瓣图片代理</h4>
                <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">选择获取豆瓣图片的方式</p>
              </div>
              <div class="relative" data-dropdown="douban-image-proxy">
                <button type="button" @click="isDoubanImageProxyDropdownOpen = !isDoubanImageProxyDropdownOpen" class="w-full px-3 py-2.5 pr-10 border border-gray-300 dark:border-gray-600 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-green-500 focus:border-green-500 transition-all duration-200 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 shadow-sm hover:border-gray-400 dark:hover:border-gray-500 text-left">
                  {{ doubanImageProxyTypeOptions.find(o => o.value === doubanImageProxyType)?.label }}
                </button>
                <div class="absolute inset-y-0 right-0 flex items-center pr-3 pointer-events-none">
                  <ChevronDown :class="['w-4 h-4 text-gray-400 dark:text-gray-500 transition-transform duration-200', isDoubanImageProxyDropdownOpen ? 'rotate-180' : '']" />
                </div>
                <div v-if="isDoubanImageProxyDropdownOpen" class="absolute z-50 w-full mt-1 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-lg shadow-lg max-h-60 overflow-auto">
                  <button v-for="opt in doubanImageProxyTypeOptions" :key="opt.value" type="button" @click="handleDoubanImageProxyTypeChange(opt.value)"
                    :class="['w-full px-3 py-2.5 text-left text-sm transition-colors duration-150 flex items-center justify-between hover:bg-gray-100 dark:hover:bg-gray-700', doubanImageProxyType === opt.value ? 'bg-green-50 dark:bg-green-900/20 text-green-600 dark:text-green-400' : 'text-gray-900 dark:text-gray-100']">
                    <span class="truncate">{{ opt.label }}</span>
                    <Check v-if="doubanImageProxyType === opt.value" class="w-4 h-4 text-green-600 dark:text-green-400 flex-shrink-0 ml-2" />
                  </button>
                </div>
              </div>
              <div v-if="getThanksInfo(doubanImageProxyType)" class="mt-3">
                <button type="button" @click="openUrl(getThanksInfo(doubanImageProxyType)!.url)" class="flex items-center justify-center gap-1.5 w-full px-3 text-xs text-gray-500 dark:text-gray-400 cursor-pointer">
                  <span class="font-medium">{{ getThanksInfo(doubanImageProxyType)!.text }}</span>
                  <ExternalLink class="w-3.5 opacity-70" />
                </button>
              </div>
            </div>

            <!-- 豆瓣图片代理地址 - 自定义时显示 -->
            <div v-if="doubanImageProxyType === 'custom'" class="space-y-3">
              <div>
                <h4 class="text-sm font-medium text-gray-700 dark:text-gray-300">豆瓣图片代理地址</h4>
                <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">自定义图片代理服务器地址</p>
              </div>
              <input type="text" class="w-full px-3 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-green-500 focus:border-green-500 transition-all duration-200 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 placeholder-gray-500 dark:placeholder-gray-400 shadow-sm hover:border-gray-400 dark:hover:border-gray-500"
                placeholder="例如: https://proxy.example.com/fetch?url=" :value="doubanImageProxyUrl" @input="handleDoubanImageProxyUrlChange(($event.target as HTMLInputElement).value)" />
            </div>

            <div class="border-t border-gray-200 dark:border-gray-700"></div>
            <!-- 默认聚合搜索 -->
            <div class="flex items-center justify-between">
              <div>
                <h4 class="text-sm font-medium text-gray-700 dark:text-gray-300">默认聚合搜索结果</h4>
                <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">搜索时默认按标题和年份聚合显示结果</p>
              </div>
              <label class="flex items-center cursor-pointer">
                <div class="relative">
                  <input type="checkbox" class="sr-only peer" :checked="defaultAggregateSearch" @change="handleAggregateToggle(($event.target as HTMLInputElement).checked)" />
                  <div class="w-11 h-6 bg-gray-300 rounded-full peer-checked:bg-green-500 transition-colors dark:bg-gray-600"></div>
                  <div class="absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full transition-transform peer-checked:translate-x-5"></div>
                </div>
              </label>
            </div>

            <!-- 优选和测速 -->
            <div class="flex items-center justify-between">
              <div>
                <h4 class="text-sm font-medium text-gray-700 dark:text-gray-300">优选和测速</h4>
                <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">如出现播放器劫持问题可关闭</p>
              </div>
              <label class="flex items-center cursor-pointer">
                <div class="relative">
                  <input type="checkbox" class="sr-only peer" :checked="enableOptimization" @change="handleOptimizationToggle(($event.target as HTMLInputElement).checked)" />
                  <div class="w-11 h-6 bg-gray-300 rounded-full peer-checked:bg-green-500 transition-colors dark:bg-gray-600"></div>
                  <div class="absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full transition-transform peer-checked:translate-x-5"></div>
                </div>
              </label>
            </div>

            <!-- 流式搜索 -->
            <div class="flex items-center justify-between">
              <div>
                <h4 class="text-sm font-medium text-gray-700 dark:text-gray-300">流式搜索输出</h4>
                <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">启用搜索结果实时流式输出，关闭后使用传统一次性搜索</p>
              </div>
              <label class="flex items-center cursor-pointer">
                <div class="relative">
                  <input type="checkbox" class="sr-only peer" :checked="fluidSearch" @change="handleFluidSearchToggle(($event.target as HTMLInputElement).checked)" />
                  <div class="w-11 h-6 bg-gray-300 rounded-full peer-checked:bg-green-500 transition-colors dark:bg-gray-600"></div>
                  <div class="absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full transition-transform peer-checked:translate-x-5"></div>
                </div>
              </label>
            </div>

            <!-- IPTV 直连 -->
            <div class="flex items-center justify-between">
              <div>
                <h4 class="text-sm font-medium text-gray-700 dark:text-gray-300">IPTV 视频浏览器直连</h4>
                <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">开启 IPTV 视频浏览器直连时，需要自备 Allow CORS 插件</p>
              </div>
              <label class="flex items-center cursor-pointer">
                <div class="relative">
                  <input type="checkbox" class="sr-only peer" :checked="liveDirectConnect" @change="handleLiveDirectConnectToggle(($event.target as HTMLInputElement).checked)" />
                  <div class="w-11 h-6 bg-gray-300 rounded-full peer-checked:bg-green-500 transition-colors dark:bg-gray-600"></div>
                  <div class="absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full transition-transform peer-checked:translate-x-5"></div>
                </div>
              </label>
            </div>
          </div>

          <div class="mt-6 pt-4 border-t border-gray-200 dark:border-gray-700">
            <p class="text-xs text-gray-500 dark:text-gray-400 text-center">这些设置保存在本地浏览器中</p>
          </div>
        </div>
      </div>
    </template>
  </Teleport>

  <!-- 修改密码面板 -->
  <Teleport to="body">
    <template v-if="isChangePasswordOpen">
      <div class="fixed inset-0 bg-black/50 backdrop-blur-sm z-[1000]" @click="handleCloseChangePassword" @touchmove.prevent @wheel.prevent style="touch-action: none" />
      <div class="fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-full max-w-md bg-white dark:bg-gray-900 rounded-xl shadow-xl z-[1001] overflow-hidden">
        <div class="h-full p-6" @touchmove.stop style="touch-action: auto">
          <div class="flex items-center justify-between mb-6">
            <h3 class="text-xl font-bold text-gray-800 dark:text-gray-200">修改密码</h3>
            <button @click="handleCloseChangePassword" class="w-8 h-8 p-1 rounded-full flex items-center justify-center text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors" aria-label="Close">
              <X class="w-full h-full" />
            </button>
          </div>
          <div class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">新密码</label>
              <input type="password" class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-green-500 focus:border-transparent transition-colors bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 placeholder-gray-500 dark:placeholder-gray-400"
                placeholder="请输入新密码" v-model="newPassword" :disabled="passwordLoading" />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">确认密码</label>
              <input type="password" class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-green-500 focus:border-transparent transition-colors bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 placeholder-gray-500 dark:placeholder-gray-400"
                placeholder="请再次输入新密码" v-model="confirmPassword" :disabled="passwordLoading" />
            </div>
            <div v-if="passwordError" class="text-red-500 text-sm bg-red-50 dark:bg-red-900/20 p-3 rounded-md border border-red-200 dark:border-red-800">{{ passwordError }}</div>
          </div>
          <div class="flex gap-3 mt-6 pt-4 border-t border-gray-200 dark:border-gray-700">
            <button @click="handleCloseChangePassword" class="flex-1 px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 bg-gray-100 dark:bg-gray-800 hover:bg-gray-200 dark:hover:bg-gray-700 rounded-md transition-colors" :disabled="passwordLoading">取消</button>
            <button @click="handleSubmitChangePassword" class="flex-1 px-4 py-2 text-sm font-medium text-white bg-green-600 hover:bg-green-700 dark:bg-green-700 dark:hover:bg-green-600 rounded-md transition-colors disabled:opacity-50 disabled:cursor-not-allowed" :disabled="passwordLoading || !newPassword || !confirmPassword">
              {{ passwordLoading ? '修改中...' : '确认修改' }}
            </button>
          </div>
          <div class="mt-4 pt-4 border-t border-gray-200 dark:border-gray-700">
            <p class="text-xs text-gray-500 dark:text-gray-400 text-center">修改密码后需要重新登录</p>
          </div>
        </div>
      </div>
    </template>
  </Teleport>

  <!-- 修改头像面板 -->
  <Teleport to="body">
    <template v-if="isChangeAvatarOpen">
      <div class="fixed inset-0 bg-black/50 backdrop-blur-sm z-[1000]" @click="handleCloseChangeAvatar" @touchmove.prevent @wheel.prevent style="touch-action: none" />
      <div class="fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-full max-w-md bg-white dark:bg-gray-900 rounded-xl shadow-xl z-[1001] overflow-hidden">
        <div class="p-6">
          <div class="flex items-center justify-between mb-6">
            <h3 class="text-xl font-bold text-gray-800 dark:text-gray-200">修改头像</h3>
            <button @click="handleCloseChangeAvatar" class="w-8 h-8 p-1 rounded-full flex items-center justify-center text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors" aria-label="Close">
              <X class="w-full h-full" />
            </button>
          </div>

          <!-- 预览模式 -->
          <template v-if="!showCropper">
            <div class="flex flex-col items-center justify-center gap-6 my-6">
              <div class="w-24 h-24 rounded-full overflow-hidden">
                <img v-if="avatarUrl" :src="avatarUrl" alt="用户头像" class="w-full h-full object-cover" />
                <div v-else class="w-full h-full bg-blue-100 dark:bg-blue-900/40 flex items-center justify-center">
                  <User class="w-12 h-12 text-blue-500 dark:text-blue-400" />
                </div>
              </div>
              <div>
                <input ref="fileInputRef" type="file" accept="image/*" class="hidden" @change="handleAvatarSelected" :disabled="isUploadingAvatar" />
                <button @click="handleOpenFileSelector" :disabled="isUploadingAvatar" class="flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg disabled:opacity-50 disabled:cursor-not-allowed transition-colors">
                  <Upload class="w-4 h-4" />选择图片
                </button>
              </div>
            </div>
          </template>

          <!-- 裁剪模式 -->
          <template v-else>
            <div class="flex flex-col items-center justify-center gap-4 my-6">
              <div class="relative select-none inline-block" style="user-select: none">
                <img ref="imageRef" :src="selectedImage" alt="Crop me" style="display: block; max-width: 100%; max-height: 256px;" @load="onImageLoad" draggable="false" />
                <!-- 裁剪覆盖层 (精确覆盖图片) -->
                <div v-if="cropPx.size > 0" class="absolute inset-0 pointer-events-none">
                  <!-- SVG 遮罩: 外部半透明 + 圆形裁剪区域透明 -->
                  <svg :width="imgSize.w" :height="imgSize.h" class="absolute top-0 left-0">
                    <defs>
                      <mask id="cropCircleMask">
                        <rect width="100%" height="100%" fill="white" />
                        <circle
                          :cx="cropPx.x + cropPx.size / 2"
                          :cy="cropPx.y + cropPx.size / 2"
                          :r="cropPx.size / 2"
                          fill="black"
                        />
                      </mask>
                    </defs>
                    <rect width="100%" height="100%" fill="rgba(0,0,0,0.5)" mask="url(#cropCircleMask)" />
                  </svg>
                  <!-- 圆形裁剪框边框 + 拖拽区域 -->
                  <div class="absolute border-2 border-white/70 rounded-full pointer-events-auto cursor-move"
                    :style="{ left: cropPx.x + 'px', top: cropPx.y + 'px', width: cropPx.size + 'px', height: cropPx.size + 'px' }"
                    @mousedown.prevent="startDrag($event, 'move')"
                    @touchstart.prevent="startDrag($event, 'move')">
                    <!-- 四角拖拽手柄 -->
                    <div class="absolute -top-1.5 -left-1.5 w-3 h-3 bg-white rounded-full cursor-nw-resize shadow"
                      @mousedown.stop.prevent="startDrag($event, 'nw')" @touchstart.stop.prevent="startDrag($event, 'nw')"></div>
                    <div class="absolute -top-1.5 -right-1.5 w-3 h-3 bg-white rounded-full cursor-ne-resize shadow"
                      @mousedown.stop.prevent="startDrag($event, 'ne')" @touchstart.stop.prevent="startDrag($event, 'ne')"></div>
                    <div class="absolute -bottom-1.5 -left-1.5 w-3 h-3 bg-white rounded-full cursor-sw-resize shadow"
                      @mousedown.stop.prevent="startDrag($event, 'sw')" @touchstart.stop.prevent="startDrag($event, 'sw')"></div>
                    <div class="absolute -bottom-1.5 -right-1.5 w-3 h-3 bg-white rounded-full cursor-se-resize shadow"
                      @mousedown.stop.prevent="startDrag($event, 'se')" @touchstart.stop.prevent="startDrag($event, 'se')"></div>
                  </div>
                </div>
              </div>
              <div class="flex gap-3">
                <button @click="handleResetCrop" class="px-4 py-2 bg-gray-500 hover:bg-gray-600 text-white rounded-lg transition-colors">重新选择</button>
                <button @click="handleConfirmCrop" :disabled="isUploadingAvatar || !cropPx.size" class="flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg disabled:opacity-50 disabled:cursor-not-allowed transition-colors">
                  <Check class="w-4 h-4" />{{ isUploadingAvatar ? '上传中...' : '确认上传' }}
                </button>
              </div>
            </div>
          </template>

          <p class="text-xs text-gray-500 dark:text-gray-400 text-center mt-4 pt-4 border-t border-gray-200 dark:border-gray-700">支持 JPG、PNG、GIF 等格式，文件大小不超过 2MB</p>
        </div>
      </div>
    </template>
  </Teleport>

  <!-- 版本面板 -->
  <VersionPanel :is-open="isVersionPanelOpen" :on-close="() => isVersionPanelOpen = false" />
</template>

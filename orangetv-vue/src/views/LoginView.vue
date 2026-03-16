<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useSiteStore } from '@/stores/site'
import { useToast } from '@/composables/useToast'
import { CheckCircle, Shield } from 'lucide-vue-next'
import ThemeToggle from '@/components/ThemeToggle.vue'
import MachineCode from '@/utils/machine-code'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const siteStore = useSiteStore()
const toast = useToast()

const username = ref('')
const password = ref('')
const error = ref<string | null>(null)
const loading = ref(false)
const linuxDoLoading = ref(false)

// 机器码相关状态
const machineCode = ref('')
const deviceInfo = ref('')
const machineCodeGenerated = ref(false)
const bindMachineCode = ref(false)
const requireMachineCode = ref(false)

// 版本信息
const CURRENT_VERSION = '8.9.5'

// 输入框是否有值（用于浮动标签效果）
const usernameHasValue = computed(() => username.value.length > 0)
const passwordHasValue = computed(() => password.value.length > 0)

// 格式化的机器码
const formattedMachineCode = computed(() => MachineCode.formatMachineCode(machineCode.value))

onMounted(async () => {
  // 检查是否是 LinuxDo OAuth 回调
  const urlParams = new URLSearchParams(window.location.search)
  const oauthStatus = urlParams.get('oauth')
  const token = urlParams.get('token')
  const errorMessage = urlParams.get('message')

  if (oauthStatus === 'success' && token) {
    authStore.setToken(token)
    try {
      await authStore.fetchCurrentUser()
      authStore.setJustLoggedIn() // 设置刚登录标志，触发公告弹窗
      toast.success('登录成功')
      const redirect = (route.query.redirect as string) || '/'
      router.replace(redirect)
    } catch (error) {
      toast.error('获取用户信息失败')
      authStore.logout()
    }
    return
  }

  if (oauthStatus === 'error') {
    toast.error(errorMessage || 'OAuth 登录失败')
    return
  }

  // 获取站点配置
  await siteStore.fetchConfig()

  // 如果启用设备码功能，生成机器码
  if (siteStore.requireDeviceCode && MachineCode.isSupported()) {
    try {
      machineCode.value = await MachineCode.generateMachineCode()
      deviceInfo.value = await MachineCode.getDeviceInfo()
      machineCodeGenerated.value = true
    } catch (err) {
      console.error('生成机器码失败:', err)
    }
  }
})

async function handleSubmit() {
  if (!username.value || !password.value) {
    error.value = '请输入用户名和密码'
    return
  }

  error.value = null
  loading.value = true

  try {
    // 构建登录请求
    const loginData: { username: string; password: string; machineCode?: string } = {
      username: username.value,
      password: password.value,
    }

    // 如果启用设备码且需要绑定或验证
    if (siteStore.requireDeviceCode && (requireMachineCode.value || bindMachineCode.value) && machineCode.value) {
      loginData.machineCode = machineCode.value
    }

    await authStore.login(loginData)

    // 登录成功（拦截器已解包，走到这里说明 code===200）
    toast.success('登录成功')
    const redirect = (route.query.redirect as string) || '/'
    router.replace(redirect)
  } catch (err: unknown) {
    const axiosError = err as { response?: { status?: number; data?: { message?: string; requireMachineCode?: boolean; machineCodeMismatch?: boolean } }; code?: number; message?: string }
    // 业务错误（拦截器 reject 的 { code, message }）
    if (axiosError.code && !axiosError.response) {
      error.value = axiosError.message || '登录失败'
    } else if (axiosError.response?.status === 401) {
      error.value = '用户名或密码错误'
    } else if (axiosError.response?.status === 403) {
      if (axiosError.response?.data?.requireMachineCode) {
        requireMachineCode.value = true
        error.value = '该账户已绑定设备，请验证机器码'
      } else if (axiosError.response?.data?.machineCodeMismatch) {
        error.value = '机器码不匹配，此账户只能在绑定的设备上使用'
      } else {
        error.value = axiosError.response?.data?.message || '访问被拒绝'
      }
    } else if (axiosError.response?.status === 409) {
      error.value = '机器码冲突'
    } else {
      error.value = '网络错误，请稍后重试'
    }
  } finally {
    loading.value = false
  }
}

// function goToRegister() {
//   router.push('/register')
// }

async function handleLinuxDoLogin() {
  linuxDoLoading.value = true
  try {
    const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'
    const response = await fetch(`${baseURL}/auth/linuxdo/auth-url`, {
      headers: {
        'Content-Type': 'application/json'
      }
    })
    const data = await response.json()
    if (data.code === 200 && data.data?.authUrl) {
      window.location.href = data.data.authUrl
    } else {
      toast.error(data.message || '获取授权链接失败')
      linuxDoLoading.value = false
    }
  } catch (err) {
    toast.error('获取授权链接失败')
    linuxDoLoading.value = false
  }
}

function openGithub() {
  window.open('https://github.com/djteang/OrangeTV', '_blank')
}
</script>

<template>
  <div class="relative min-h-screen flex items-center justify-center px-4 overflow-hidden bg-gray-50 dark:bg-gray-900">
    <!-- 主题切换按钮 -->
    <div class="absolute top-4 right-4">
      <ThemeToggle />
    </div>

    <!-- 登录卡片 -->
    <div
      class="relative z-10 w-full max-w-md rounded-3xl bg-gradient-to-b from-white/90 via-white/70 to-white/40 dark:from-zinc-900/90 dark:via-zinc-900/70 dark:to-zinc-900/40 backdrop-blur-xl shadow-2xl p-10 dark:border dark:border-zinc-800"
    >
      <!-- 标题 -->
      <h1
        class="text-blue-600 tracking-tight text-center text-3xl font-extrabold mb-8 bg-clip-text drop-shadow-sm"
      >
        {{ siteStore.siteName }}
      </h1>

      <!-- 表单 -->
      <form @submit.prevent="handleSubmit" class="space-y-6">
        <!-- 用户名输入框 -->
        <div class="relative">
          <input
            id="username"
            v-model="username"
            type="text"
            autocomplete="username"
            placeholder="用户名"
            class="peer block w-full rounded-lg border-0 py-4 px-4 pt-6 text-gray-900 dark:text-gray-100 shadow-sm ring-1 ring-white/60 dark:ring-white/20 focus:ring-2 focus:ring-blue-500 focus:outline-none sm:text-base bg-white/60 dark:bg-zinc-800/60 backdrop-blur placeholder-transparent"
          />
          <label
            for="username"
            :class="[
              'absolute left-4 transition-all duration-200 pointer-events-none',
              usernameHasValue
                ? 'top-1 text-xs text-blue-600 dark:text-blue-400'
                : 'top-4 text-base text-gray-500 dark:text-gray-400 peer-focus:top-1 peer-focus:text-xs peer-focus:text-blue-600 peer-focus:dark:text-blue-400',
            ]"
          >
            用户名
          </label>
        </div>

        <!-- 密码输入框 -->
        <div class="relative">
          <input
            id="password"
            v-model="password"
            type="password"
            autocomplete="current-password"
            placeholder="密码"
            class="peer block w-full rounded-lg border-0 py-4 px-4 pt-6 text-gray-900 dark:text-gray-100 shadow-sm ring-1 ring-white/60 dark:ring-white/20 focus:ring-2 focus:ring-blue-500 focus:outline-none sm:text-base bg-white/60 dark:bg-zinc-800/60 backdrop-blur placeholder-transparent"
          />
          <label
            for="password"
            :class="[
              'absolute left-4 transition-all duration-200 pointer-events-none',
              passwordHasValue
                ? 'top-1 text-xs text-blue-600 dark:text-blue-400'
                : 'top-4 text-base text-gray-500 dark:text-gray-400 peer-focus:top-1 peer-focus:text-xs peer-focus:text-blue-600 peer-focus:dark:text-blue-400',
            ]"
          >
            密码
          </label>
        </div>

        <!-- 机器码信息显示 - 只有在启用设备码功能时才显示 -->
        <div v-if="siteStore.requireDeviceCode && machineCodeGenerated" class="space-y-4">
          <div class="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-4">
            <div class="flex items-center space-x-2 mb-2">
              <Shield class="w-4 h-4 text-blue-600 dark:text-blue-400" />
              <span class="text-sm font-medium text-blue-800 dark:text-blue-300">设备识别码</span>
            </div>
            <div class="space-y-2">
              <div class="text-xs font-mono text-gray-700 dark:text-gray-300 break-all">
                {{ formattedMachineCode }}
              </div>
              <div class="text-xs text-gray-600 dark:text-gray-400">
                设备信息: {{ deviceInfo }}
              </div>
            </div>
          </div>

          <!-- 绑定选项 -->
          <div v-if="!requireMachineCode" class="space-y-2">
            <div class="flex items-center space-x-3">
              <input
                id="bindMachineCode"
                v-model="bindMachineCode"
                type="checkbox"
                class="w-4 h-4 text-blue-600 bg-gray-100 border-gray-300 rounded focus:ring-blue-500 dark:focus:ring-blue-600 dark:ring-offset-gray-800 focus:ring-2 dark:bg-gray-700 dark:border-gray-600"
              />
              <label for="bindMachineCode" class="text-sm text-gray-700 dark:text-gray-300">
                绑定此设备（提升账户安全性）
              </label>
            </div>
          </div>
        </div>

        <!-- 错误提示 -->
        <p v-if="error" class="text-sm text-red-600 dark:text-red-400">
          {{ error }}
        </p>

        <!-- 登录按钮 -->
        <button
          type="submit"
          :disabled="!password || !username || loading"
          class="inline-flex w-full justify-center rounded-lg bg-blue-600 py-3 text-base font-semibold text-white shadow-lg transition-all duration-200 hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {{ loading ? '登录中...' : '登录' }}
        </button>

        <!-- 其他登录方式 -->
        <div v-if="siteStore.enableLinuxDoLogin" class="mt-6">
          <div class="relative">
            <div class="absolute inset-0 flex items-center">
              <div class="w-full border-t border-gray-300 dark:border-gray-600"></div>
            </div>
            <div class="relative flex justify-center text-sm">
              <span class="px-2 bg-white dark:bg-zinc-800 text-gray-500 dark:text-gray-400">其他登录方式</span>
            </div>
          </div>

          <button
            type="button"
            @click="handleLinuxDoLogin"
            :disabled="loading || linuxDoLoading"
            class="mt-4 w-full flex items-center justify-center gap-2 px-4 py-3 border border-gray-300 dark:border-gray-600 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <img v-if="!linuxDoLoading" src="/linuxdo.png" alt="LinuxDo" class="w-5 h-5" />
            <svg v-else class="animate-spin w-5 h-5 text-gray-600 dark:text-gray-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            <span class="text-sm font-medium text-gray-700 dark:text-gray-300">LinuxDo 登录</span>
          </button>
        </div>
      </form>
    </div>

    <!-- 版本信息 -->
    <button
      @click="openGithub"
      class="absolute bottom-4 left-1/2 transform -translate-x-1/2 flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400 transition-colors cursor-pointer hover:text-gray-700 dark:hover:text-gray-300"
    >
      <span class="font-mono">v{{ CURRENT_VERSION }}</span>
      <div class="flex items-center gap-1.5 text-blue-600 dark:text-blue-400">
        <CheckCircle class="w-3.5 h-3.5" />
        <span class="font-semibold text-xs">已是最新</span>
      </div>
    </button>
  </div>
</template>

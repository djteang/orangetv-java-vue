<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useSiteStore } from '@/stores/site'
import { useToast } from '@/composables/useToast'
import { register } from '@/api/auth'
import { CheckCircle } from 'lucide-vue-next'
import ThemeToggle from '@/components/ThemeToggle.vue'

const router = useRouter()
const siteStore = useSiteStore()
const toast = useToast()

const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const error = ref<string | null>(null)
const loading = ref(false)

const CURRENT_VERSION = '8.9.5'

const usernameHasValue = computed(() => username.value.length > 0)
const passwordHasValue = computed(() => password.value.length > 0)
const confirmPasswordHasValue = computed(() => confirmPassword.value.length > 0)

onMounted(async () => {
  await siteStore.fetchConfig()
  if (!siteStore.allowRegistration) {
    router.replace('/login')
  }
})

async function handleSubmit() {
  if (!username.value || !password.value || !confirmPassword.value) {
    error.value = '请填写所有字段'
    return
  }

  if (password.value !== confirmPassword.value) {
    error.value = '两次输入的密码不一致'
    return
  }

  if (password.value.length < 6) {
    error.value = '密码长度至少6位'
    return
  }

  error.value = null
  loading.value = true

  try {
    await register({
      username: username.value,
      password: password.value,
    })

    // 注册成功（拦截器已解包，走到这里说明 code===200）
    toast.success('注册成功，请登录')
    router.replace('/login')
  } catch (err: unknown) {
    const axiosError = err as { response?: { status?: number; data?: { message?: string } }; code?: number; message?: string }
    if (axiosError.code && !axiosError.response) {
      // 业务错误（拦截器 reject 的 { code, message }）
      error.value = axiosError.message || '注册失败'
    } else if (axiosError.response?.status === 409) {
      error.value = '用户名已存在'
    } else {
      error.value = axiosError.response?.data?.message || '注册失败，请稍后重试'
    }
  } finally {
    loading.value = false
  }
}

function goToLogin() {
  router.push('/login')
}

function openGithub() {
  window.open('https://github.com/djteang/OrangeTV', '_blank')
}
</script>

<template>
  <div class="relative min-h-screen flex items-center justify-center px-4 overflow-hidden bg-gray-50 dark:bg-gray-900">
    <div class="absolute top-4 right-4">
      <ThemeToggle />
    </div>

    <div
      class="relative z-10 w-full max-w-md rounded-3xl bg-gradient-to-b from-white/90 via-white/70 to-white/40 dark:from-zinc-900/90 dark:via-zinc-900/70 dark:to-zinc-900/40 backdrop-blur-xl shadow-2xl p-10 dark:border dark:border-zinc-800"
    >
      <h1
        class="text-blue-600 tracking-tight text-center text-3xl font-extrabold mb-8 bg-clip-text drop-shadow-sm"
      >
        注册 {{ siteStore.siteName }}
      </h1>

      <form @submit.prevent="handleSubmit" class="space-y-6">
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

        <div class="relative">
          <input
            id="password"
            v-model="password"
            type="password"
            autocomplete="new-password"
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

        <div class="relative">
          <input
            id="confirmPassword"
            v-model="confirmPassword"
            type="password"
            autocomplete="new-password"
            placeholder="确认密码"
            class="peer block w-full rounded-lg border-0 py-4 px-4 pt-6 text-gray-900 dark:text-gray-100 shadow-sm ring-1 ring-white/60 dark:ring-white/20 focus:ring-2 focus:ring-blue-500 focus:outline-none sm:text-base bg-white/60 dark:bg-zinc-800/60 backdrop-blur placeholder-transparent"
          />
          <label
            for="confirmPassword"
            :class="[
              'absolute left-4 transition-all duration-200 pointer-events-none',
              confirmPasswordHasValue
                ? 'top-1 text-xs text-blue-600 dark:text-blue-400'
                : 'top-4 text-base text-gray-500 dark:text-gray-400 peer-focus:top-1 peer-focus:text-xs peer-focus:text-blue-600 peer-focus:dark:text-blue-400',
            ]"
          >
            确认密码
          </label>
        </div>

        <p v-if="error" class="text-sm text-red-600 dark:text-red-400">
          {{ error }}
        </p>

        <button
          type="submit"
          :disabled="!password || !username || !confirmPassword || loading"
          class="inline-flex w-full justify-center rounded-lg bg-blue-600 py-3 text-base font-semibold text-white shadow-lg transition-all duration-200 hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {{ loading ? '注册中...' : '注册' }}
        </button>

        <div class="text-center">
          <button
            type="button"
            @click="goToLogin"
            class="text-sm text-blue-600 dark:text-blue-400 hover:text-blue-700 dark:hover:text-blue-300 transition-colors"
          >
            已有账号？立即登录
          </button>
        </div>
      </form>
    </div>

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

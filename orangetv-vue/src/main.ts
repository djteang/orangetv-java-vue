// SockJS requires `global` which is not defined in browsers
if (typeof globalThis !== 'undefined' && !(globalThis as any).global) {
  ;(globalThis as any).global = globalThis
}

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useThemeStore } from './stores/theme'

import { useSiteStore } from './stores/site'
import './assets/main.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

// 初始化主题
const themeStore = useThemeStore()
themeStore.init()

// 初始化站点配置
const siteStore = useSiteStore()
siteStore.fetchConfig()

// 用户信息已从 localStorage 恢复，无需额外请求

app.mount('#app')

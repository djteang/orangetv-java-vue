import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/HomeView.vue'),
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { guest: true },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { guest: true },
  },
  {
    path: '/search',
    name: 'Search',
    component: () => import('@/views/SearchView.vue'),
  },
  {
    path: '/play',
    name: 'Play',
    component: () => import('@/views/PlayView.vue'),
  },
  {
    path: '/douban',
    name: 'Douban',
    component: () => import('@/views/DoubanView.vue'),
  },
  {
    path: '/shortdrama',
    name: 'ShortDrama',
    component: () => import('@/views/ShortDramaView.vue'),
  },
  {
    path: '/live',
    name: 'Live',
    component: () => import('@/views/LiveView.vue'),
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('@/views/AdminView.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
  },
  {
    path: '/watch-together/:roomId',
    name: 'WatchTogether',
    component: () => import('@/views/WatchTogetherView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/warning',
    name: 'Warning',
    component: () => import('@/views/WarningView.vue'),
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFoundView.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, _from, next) => {
  const authStore = useAuthStore()

  // 需要登录的页面
  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
    return
  }

  // 需要管理员权限的页面
  if (to.meta.requiresAdmin && !authStore.isAdmin) {
    next({ name: 'Home' })
    return
  }

  // 已登录用户访问登录页
  if (to.meta.guest && authStore.isLoggedIn) {
    next({ name: 'Home' })
    return
  }

  next()
})

export default router

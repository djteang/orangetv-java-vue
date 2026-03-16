# OrangeTV Vue 3 前端

基于 Vue 3 + TypeScript + TailwindCSS 重构的 OrangeTV 前端项目。

## 技术栈

- Vue 3.5 + Composition API
- Vite 5
- Vue Router 4
- Pinia (状态管理)
- TailwindCSS 3.4
- Axios (HTTP 请求)
- @stomp/stompjs + sockjs-client (WebSocket)
- Artplayer + hls.js (视频播放)
- Lucide Vue Next (图标)

## 项目结构

```
src/
├── api/              # API 请求封装
│   ├── index.ts      # Axios 实例配置
│   ├── auth.ts       # 认证 API
│   ├── user.ts       # 用户数据 API
│   ├── search.ts     # 搜索 API
│   ├── live.ts       # 直播 API
│   └── douban.ts     # 豆瓣 API
├── assets/           # 静态资源
│   └── main.css      # 全局样式
├── components/       # 通用组件
│   ├── PageLayout.vue
│   ├── Sidebar.vue
│   ├── VideoCard.vue
│   └── ...
├── composables/      # 组合式函数
│   └── useToast.ts
├── router/           # 路由配置
│   └── index.ts
├── services/         # 业务服务
│   └── websocket.ts  # STOMP WebSocket
├── stores/           # Pinia 状态管理
│   ├── auth.ts
│   ├── site.ts
│   ├── theme.ts
│   └── user.ts
├── types/            # TypeScript 类型
│   └── index.ts
├── views/            # 页面组件
│   ├── HomeView.vue
│   ├── LoginView.vue
│   ├── SearchView.vue
│   ├── PlayView.vue
│   └── ...
├── App.vue           # 根组件
└── main.ts           # 入口文件
```

## 开发

```bash
# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 类型检查
npm run typecheck

# 构建生产版本
npm run build
```

## 配置

开发环境下，Vite 会将 `/api` 和 `/ws` 请求代理到后端服务器 `http://localhost:8080`。

可以通过环境变量 `VITE_API_BASE_URL` 配置 API 基础路径。

## WebSocket

项目使用 STOMP over SockJS 与后端通信：

- 端点: `/ws`
- 用户队列: `/user/{username}/queue/messages`
- 应用目的地前缀: `/app`
- 广播主题: `/topic/broadcast`

## 与原 Next.js 项目的对应关系

| React 概念 | Vue 3 对应 |
|-----------|-----------|
| useState | ref() / reactive() |
| useEffect | onMounted() / watch() |
| useMemo | computed() |
| useContext | provide/inject 或 Pinia |
| children | `<slot />` |
| className | class |

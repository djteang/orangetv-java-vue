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

### 界面主题

点击页面右上角的主题按钮，可选择 **明亮、黑夜、中国红、跟随系统**。中国红使用朱红强调色和暖白背景，支持网页桌面布局和移动布局，登录、注册页面也可切换。

中国红使用全红的镰刀锤头党徽样式图标，仅此图标在菜单和顶部主题按钮中保持同样的红色；其他主题图标沿用默认配色。启用中国红后，页面直接显示“探索彩蛋”按钮，点击即可依次探索“锦绣山河”“文脉千年”“星河逐梦”三幕背景。青绿长城与熊猫旅人、花灯与醒狮、月兔与中国航天，采用原创 SVG 插画，以粗线条、明快色块、夸张造型和奇幻科幻感演绎中国元素。

彩蛋直接融入页面背景，插画位于内容下方，不拦截页面点击。入口仅显示紧凑的“探索彩蛋”按钮，不带左侧说明；开启后显示“下一幕、暂停／继续、收起”三个按钮，收起后恢复入口。“下一幕”依次切换三幕背景，“暂停”停止背景动画并变为“继续”。手机操作栏位于顶部导航下方并靠右排列，正文预留相应空间；桌面操作栏随页面滚动保持可用。登录、注册页的操作栏固定在主题按钮下方，提示页和 404 页也保留入口；共同观影页的吸顶栏为彩蛋操作栏留出空间。主题菜单只切换主题，已取消长按和重复选择主题触发彩蛋的方式。探索顺序保存在本地，原有记录继续有效；刷新后入口仍可见，切换主题会退出背景。页面在后台时暂停动画，返回后恢复；系统减少动态效果设置优先显示完整静态插画。三个场景按需加载。

主题选择保存在浏览器本地，刷新后继续生效；选择“跟随系统”时会随系统的明暗设置自动更新。主题变量位于 `src/assets/main.css`，现有蓝色强调色和灰色中性色通过 `tailwind.config.ts` 接入这些变量。

### 移动端分类与用户列表

手机底部的“分类”会弹出电影、剧集、动漫和综艺选项。管理面板的用户列表默认每页 10 条，可切换为 20 或 50 条，并支持搜索用户名；手机和平板显示用户卡片，宽屏显示表格。分页沿用现有用户接口在前端完成，更新用户后保留当前页，删除末页数据时会自动校正页码。

### 接口代理

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

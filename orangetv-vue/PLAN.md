# Vue 前端样式与功能对齐计划

## 问题分析

对比原版 Next.js 和 Vue 版本，发现以下差异：

### 1. 首页 - 新番放送不显示
**原因**：`HomeView.vue` 中 `bangumiCalendarData` 声明了但从未被赋值。`fetchRecommendData()` 只请求了 3 个豆瓣接口，没有调用 Bangumi API。
**修复**：创建 `src/api/bangumi.ts`，在 `fetchRecommendData` 中并行调用 `GetBangumiCalendarData()`。

### 2. 首页 - 缺少骨架屏动画一致性
原版和 Vue 版的骨架屏基本一致（都用 `animate-pulse`），这部分无需大改。

### 3. 豆瓣页面（电影/剧集/动漫/综艺）差异较大
- **Vue 版**：只有简单的 4 个 tab 切换，固定 20 条数据，无分页
- **原版**：有 `DoubanSelector` 多级筛选器（一级分类 + 二级分类 + 星期选择器），IntersectionObserver 无限滚动分页，25 条骨架屏，描述文字
- **修复**：重写 `DoubanView.vue`，添加无限滚动分页、骨架屏、描述文字，保持与原版一致的布局

### 4. 搜索页面差异
- **Vue 版**：基本搜索功能，单一网格布局
- **原版**：有搜索建议、返回顶部按钮（带滚动进度环）、搜索进度条（流式搜索）
- **修复**：搜索页面的核心布局和网格样式对齐原版

### 5. 路由 - 电影/剧集/动漫/综艺没有独立路由
原版侧边栏点击"电影"跳转到 `/douban?type=movie`，Vue 版也是这样。这部分一致，无需改。

---

## 实施步骤

### Step 1: 创建 Bangumi API (`src/api/bangumi.ts`)
- 从 `https://api.bgm.tv/calendar` 获取数据
- 导出 `getBangumiCalendar()` 函数
- 定义 `BangumiCalendarData` 类型

### Step 2: 修复 HomeView.vue 新番放送
- 在 `fetchRecommendData` 的 `Promise.all` 中加入 `getBangumiCalendar()` 调用
- 将返回数据赋值给 `bangumiCalendarData`

### Step 3: 重写 DoubanView.vue
- 添加页面描述文字（"来自豆瓣的精选内容" / "来自 Bangumi 番组计划的精选内容"）
- 添加 25 条骨架屏（与原版 `DoubanCardSkeleton` 一致）
- 添加 IntersectionObserver 无限滚动分页
- 网格布局对齐原版：`grid-cols-3 gap-x-2 gap-y-12 sm:grid-cols-[repeat(auto-fill,minmax(160px,1fr))] sm:gap-x-8 sm:gap-y-20`
- 添加"加载更多"指示器、"已加载全部内容"提示、空状态提示

### Step 4: 对齐 SearchView.vue 样式
- 搜索结果网格布局对齐原版：`grid-cols-3 gap-x-2 gap-y-12 sm:grid-cols-[repeat(auto-fill,minmax(160px,1fr))] sm:gap-x-8 sm:gap-y-20`
- 搜索历史标签样式对齐
- 添加返回顶部按钮

### Step 5: 对齐首页网格布局
- 收藏夹网格：`grid-cols-3 gap-x-2 gap-y-14 sm:gap-y-20 sm:grid-cols-[repeat(auto-fill,_minmax(11rem,_1fr))] sm:gap-x-8`
- 确保与原版一致

---

## 涉及文件

| 操作 | 文件 |
|------|------|
| 新建 | `src/api/bangumi.ts` |
| 修改 | `src/views/HomeView.vue` |
| 重写 | `src/views/DoubanView.vue` |
| 修改 | `src/views/SearchView.vue` |

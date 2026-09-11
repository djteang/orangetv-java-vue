export interface ChangelogEntry {
  version: string
  date: string
  added: string[]
  changed: string[]
  fixed: string[]
}

export const changelog: ChangelogEntry[] = [
  {
    version: '9.1.0',
    date: '2026-09-11',
    added: ['推荐栏目', '中国红主题及彩蛋', '暂无内容的动画显示', '聊天支持发送语音'],
    changed: ['优化搜索页的流式搜索效率', '支持配置、显示多条公告'],
    fixed: ['重构统计面板', '用户配置分页显示', '观影进度失效'],
  },
  {
    version: '9.0.1',
    date: '2026-03-26',
    added: ['聚合LogVar 弹幕 API 服务器'],
    changed: [],
    fixed: ['优化观看记录的保存频率', '首页的继续观看部分进度条显示'],
  },
  {
    version: '9.0.0',
    date: '2026-03-17',
    added: ['统计面板','一起看功能','LinuxDo登录'],
    changed: ['去掉直播和短剧功能'],
    fixed: ['重构整个聊天功能逻辑', '项目重构为SpringBoot3+Vue3'],
  },
  {
    version: '8.9.5',
    date: '2025-09-21',
    added: ['添加内置主题，支持用户自定义CSS'],
    changed: ['优化搜索页面缓存机制'],
    fixed: ['镜像健康检查问题', '弹幕功能适配移动端'],
  },
]

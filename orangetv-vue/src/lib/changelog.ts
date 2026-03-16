export interface ChangelogEntry {
  version: string
  date: string
  added: string[]
  changed: string[]
  fixed: string[]
}

export const changelog: ChangelogEntry[] = [
  {
    version: '8.9.5',
    date: '2025-09-21',
    added: ['添加内置主题，支持用户自定义CSS'],
    changed: ['优化搜索页面缓存机制'],
    fixed: ['镜像健康检查问题', '弹幕功能适配移动端'],
  },
]

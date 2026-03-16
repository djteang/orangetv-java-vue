import type { DoubanItem, DoubanResult } from '@/types'

interface DoubanRecommendsParams {
  kind: 'tv' | 'movie'
  pageLimit?: number
  pageStart?: number
  category?: string
  format?: string
  label?: string
  region?: string
  year?: string
  platform?: string
  sort?: string
}

interface DoubanCategoryParams {
  kind: 'movie' | 'tv'
  category: string
  type: string
  pageLimit?: number
  pageStart?: number
}

interface DoubanCategoryApiResponse {
  total: number
  items: Array<{
    id: string
    title: string
    card_subtitle: string
    pic: {
      large: string
      normal: string
    }
    rating: {
      value: number
    }
  }>
}

async function fetchWithTimeout(url: string, timeout = 10000): Promise<Response> {
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), timeout)

  try {
    const response = await fetch(url, {
      signal: controller.signal,
      headers: {
        'User-Agent':
          'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36',
        Referer: 'https://movie.douban.com/',
        Accept: 'application/json, text/plain, */*',
      },
    })
    clearTimeout(timeoutId)
    return response
  } catch (error) {
    clearTimeout(timeoutId)
    throw error
  }
}

/**
 * 获取豆瓣可用标签（分类+地区等）
 * @param type 'movie' | 'tv'
 * @returns 标签字符串数组
 */
export async function getDoubanTags(type: string): Promise<string[]> {
  const target = `https://movie.douban.cmliussss.net/j/search_tags?type=${type}`
  try {
    const response = await fetchWithTimeout(target)
    if (!response.ok) throw new Error(`HTTP error! Status: ${response.status}`)
    const data: { tags: string[] } = await response.json()
    return data.tags || []
  } catch (error) {
    console.error('获取豆瓣标签失败:', error)
    return []
  }
}

export async function getDoubanCategories(params: DoubanCategoryParams): Promise<DoubanResult> {
  const { kind, category, type, pageLimit = 20, pageStart = 0 } = params

  // 使用腾讯 CDN 代理
  const target = `https://m.douban.cmliussss.net/rexxar/api/v2/subject/recent_hot/${kind}?start=${pageStart}&limit=${pageLimit}&category=${category}&type=${type}`

  try {
    const response = await fetchWithTimeout(target)

    if (!response.ok) {
      throw new Error(`HTTP error! Status: ${response.status}`)
    }

    const doubanData: DoubanCategoryApiResponse = await response.json()

    const list: DoubanItem[] = doubanData.items.map((item) => ({
      id: item.id,
      title: item.title,
      poster: item.pic?.normal || item.pic?.large || '',
      rate: item.rating?.value ? item.rating.value.toFixed(1) : '',
      year: item.card_subtitle?.match(/(\d{4})/)?.[1] || '',
    }))

    return {
      code: 200,
      message: '获取成功',
      list,
    }
  } catch (error) {
    console.error('获取豆瓣分类数据失败:', error)
    return {
      code: 500,
      message: '获取豆瓣分类数据失败',
      list: [],
    }
  }
}

export async function getDoubanRecommends(params: DoubanRecommendsParams): Promise<DoubanResult> {
  const { kind, pageLimit = 25, pageStart = 0 } = params
  let { category, format, region, year, platform, sort, label } = params

  if (category === 'all') category = ''
  if (format === 'all') format = ''
  if (label === 'all') label = ''
  if (region === 'all') region = ''
  if (year === 'all') year = ''
  if (platform === 'all') platform = ''
  if (sort === 'T') sort = ''

  const selectedCategories: Record<string, string> = { 类型: category || '' }
  if (format) selectedCategories['形式'] = format
  if (region) selectedCategories['地区'] = region

  const tags: string[] = []
  if (category) tags.push(category)
  if (!category && format) tags.push(format)
  if (label) tags.push(label)
  if (region) tags.push(region)
  if (year) tags.push(year)
  if (platform) tags.push(platform)

  const baseUrl = `https://m.douban.cmliussss.net/rexxar/api/v2/${kind}/recommend`
  const reqParams = new URLSearchParams()
  reqParams.append('refresh', '0')
  reqParams.append('start', pageStart.toString())
  reqParams.append('count', pageLimit.toString())
  reqParams.append('selected_categories', JSON.stringify(selectedCategories))
  reqParams.append('uncollect', 'false')
  reqParams.append('score_range', '0,10')
  reqParams.append('tags', tags.join(','))
  if (sort) reqParams.append('sort', sort)

  const target = `${baseUrl}?${reqParams.toString()}`

  try {
    const response = await fetchWithTimeout(target)
    if (!response.ok) throw new Error(`HTTP error! Status: ${response.status}`)

    const data = await response.json()
    const list: DoubanItem[] = (data.items || [])
      .filter((item: { type: string }) => item.type === 'movie' || item.type === 'tv')
      .map((item: { id: string; title: string; pic: { large: string; normal: string }; rating: { value: number }; year: string }) => ({
        id: item.id,
        title: item.title,
        poster: item.pic?.normal || item.pic?.large || '',
        rate: item.rating?.value ? item.rating.value.toFixed(1) : '',
        year: item.year || '',
      }))

    return { code: 200, message: '获取成功', list }
  } catch (error) {
    console.error('获取豆瓣推荐数据失败:', error)
    return { code: 500, message: '获取豆瓣推荐数据失败', list: [] }
  }
}

export async function searchDouban(keyword: string): Promise<DoubanResult> {
  // 豆瓣搜索 API
  const target = `https://m.douban.cmliussss.net/rexxar/api/v2/search/subjects?q=${encodeURIComponent(keyword)}&count=20`

  try {
    const response = await fetchWithTimeout(target)

    if (!response.ok) {
      throw new Error(`HTTP error! Status: ${response.status}`)
    }

    const data = await response.json()

    const list: DoubanItem[] = (data.items || [])
      .filter((item: { target_type: string }) => item.target_type === 'movie' || item.target_type === 'tv')
      .map((item: { target: { id: string; title: string; cover_url: string; rating: { value: number }; year: string } }) => ({
        id: item.target.id,
        title: item.target.title,
        poster: item.target.cover_url || '',
        rate: item.target.rating?.value ? item.target.rating.value.toFixed(1) : '',
        year: item.target.year || '',
      }))

    return {
      code: 200,
      message: '获取成功',
      list,
    }
  } catch (error) {
    console.error('搜索豆瓣数据失败:', error)
    return {
      code: 500,
      message: '搜索豆瓣数据失败',
      list: [],
    }
  }
}

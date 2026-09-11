import { computed, ref, watch, type Ref } from 'vue'

export function usePagination<T>(items: Readonly<Ref<readonly T[]>>, initialPageSize = 10) {
  const currentPage = ref(1)
  const pageSize = ref(initialPageSize)
  const totalItems = computed(() => items.value.length)
  const totalPages = computed(() => Math.max(1, Math.ceil(totalItems.value / pageSize.value)))
  const startIndex = computed(() => (currentPage.value - 1) * pageSize.value)
  const pageItems = computed(() => items.value.slice(startIndex.value, startIndex.value + pageSize.value))
  const rangeStart = computed(() => totalItems.value ? startIndex.value + 1 : 0)
  const rangeEnd = computed(() => Math.min(startIndex.value + pageSize.value, totalItems.value))

  function goToPage(page: number) {
    currentPage.value = Math.min(totalPages.value, Math.max(1, page))
  }

  function resetPage() {
    currentPage.value = 1
  }

  watch(pageSize, resetPage, { flush: 'sync' })
  watch(totalPages, () => goToPage(currentPage.value), { flush: 'sync' })

  return { currentPage, pageSize, totalItems, totalPages, pageItems, rangeStart, rangeEnd, goToPage, resetPage }
}

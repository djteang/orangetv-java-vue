<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import { ChevronLeft, ChevronRight, Megaphone } from 'lucide-vue-next'
import type { SiteAnnouncement } from '@/types'

const props = defineProps<{ announcements: SiteAnnouncement[] }>()
const emit = defineEmits<{ close: [] }>()
const dialogRef = ref<HTMLDialogElement | null>(null)
const contentRef = ref<HTMLElement | null>(null)
const dialogId = `site-announcements-${useId()}`
const currentIndex = ref(0)
const currentAnnouncement = computed(() => props.announcements[currentIndex.value])
const isLast = computed(() => currentIndex.value >= props.announcements.length - 1)
let previousOverflow: string | null = null

function previous() {
  if (currentIndex.value > 0) currentIndex.value--
}

function advance() {
  if (!currentAnnouncement.value) return
  if (isLast.value) emit('close')
  else currentIndex.value++
}

watch(currentIndex, async () => {
  await nextTick()
  if (contentRef.value) contentRef.value.scrollTop = 0
})

onMounted(() => {
  if (!props.announcements.length) {
    emit('close')
    return
  }
  previousOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  dialogRef.value?.showModal()
})

onBeforeUnmount(() => {
  dialogRef.value?.close()
  if (previousOverflow !== null) document.body.style.overflow = previousOverflow
})
</script>

<template>
  <Teleport to="body">
    <dialog
      ref="dialogRef"
      class="announcement-dialog w-[calc(100%-2rem)] max-w-lg overflow-hidden rounded-2xl border-0 bg-theme-surface p-0 text-theme-text shadow-2xl"
      :aria-labelledby="`${dialogId}-title`"
      :aria-describedby="`${dialogId}-progress`"
      aria-modal="true"
      @cancel.prevent
    >
      <header class="flex flex-shrink-0 flex-wrap items-center justify-between gap-3 bg-gradient-to-r from-blue-500 to-blue-600 px-5 py-4 text-white">
        <h3 :id="`${dialogId}-title`" class="flex items-center gap-2 text-lg font-semibold"><Megaphone :size="22" aria-hidden="true" />站点公告</h3>
        <span :id="`${dialogId}-progress`" class="text-sm text-white/90" aria-live="polite" aria-atomic="true">第 {{ currentIndex + 1 }} 条 / 共 {{ announcements.length }} 条</span>
      </header>

      <div ref="contentRef" class="min-h-0 flex-1 overflow-y-auto overscroll-contain px-5 py-6" aria-live="polite" aria-atomic="true">
        <p class="whitespace-pre-wrap break-words text-sm leading-relaxed">{{ currentAnnouncement?.content }}</p>
      </div>

      <footer class="flex flex-shrink-0 items-center justify-between gap-3 border-t border-theme-border px-5 py-4">
        <button v-if="currentIndex > 0" type="button" class="inline-flex min-h-11 items-center justify-center gap-1 rounded-lg border border-theme-border px-4 py-2 text-sm font-medium hover:bg-blue-500/10 focus-visible:outline focus-visible:outline-2 focus-visible:outline-blue-500" @click="previous">
          <ChevronLeft :size="16" aria-hidden="true" />上一条
        </button>
        <button type="button" autofocus class="ml-auto inline-flex min-h-11 items-center justify-center gap-1 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-500" @click="advance">
          {{ isLast ? '我知道了' : '下一条' }}<ChevronRight v-if="!isLast" :size="16" aria-hidden="true" />
        </button>
      </footer>
    </dialog>
  </Teleport>
</template>

<style scoped>
.announcement-dialog { margin: auto; max-height: calc(100dvh - 32px); }
.announcement-dialog[open] { display: flex; flex-direction: column; }
.announcement-dialog::backdrop { background: rgb(0 0 0 / .5); -webkit-backdrop-filter: blur(4px); backdrop-filter: blur(4px); }
</style>

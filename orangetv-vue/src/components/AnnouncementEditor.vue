<script setup lang="ts">
import { nextTick, ref, useId } from 'vue'
import { ArrowDown, ArrowUp, ChevronsUp, Plus, Trash2 } from 'lucide-vue-next'
import type { SiteAnnouncement } from '@/types'
import { createAnnouncement } from '@/utils/announcements'

const props = withDefaults(defineProps<{
  modelValue: SiteAnnouncement[]
  disabled?: boolean
}>(), { disabled: false })
const emit = defineEmits<{ 'update:modelValue': [value: SiteAnnouncement[]] }>()
const editorRef = ref<HTMLElement | null>(null)
const editorId = `announcements-${useId()}`
const status = ref('')

async function addAnnouncement() {
  if (props.disabled) return
  emit('update:modelValue', [...props.modelValue, createAnnouncement()])
  await nextTick()
  const fields = editorRef.value?.querySelectorAll('textarea')
  fields?.[fields.length - 1]?.focus()
}

function updateContent(index: number, content: string) {
  if (props.disabled) return
  emit('update:modelValue', props.modelValue.map((item, i) => i === index ? { ...item, content } : item))
}

function moveAnnouncement(from: number, to: number) {
  if (props.disabled || from === to || from < 0 || from >= props.modelValue.length || to < 0 || to >= props.modelValue.length) return
  const items = [...props.modelValue]
  const [item] = items.splice(from, 1)
  items.splice(to, 0, item)
  emit('update:modelValue', items)
  status.value = `第 ${from + 1} 条公告已移至第 ${to + 1} 条`
}

function removeAnnouncement(index: number) {
  if (props.disabled) return
  emit('update:modelValue', props.modelValue.filter((_, i) => i !== index))
  status.value = `已删除第 ${index + 1} 条公告，保存后生效`
}
</script>

<template>
  <section ref="editorRef" class="min-w-0 space-y-3" aria-label="站点公告配置">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <h4 class="text-sm font-medium text-gray-700 dark:text-gray-300">站点公告</h4>
      <button type="button" :disabled="disabled" class="inline-flex min-h-11 sm:min-h-9 items-center gap-1.5 rounded-lg border border-blue-500/30 px-3 text-sm text-blue-600 dark:text-blue-400 hover:bg-blue-500/10 disabled:opacity-50" @click="addAnnouncement">
        <Plus :size="16" aria-hidden="true" />新增公告
      </button>
    </div>
    <p class="text-xs leading-relaxed text-gray-500 dark:text-gray-400">按从上到下的顺序依次显示；可用“置顶”设置第一条公告。修改后点击保存生效。</p>
    <p v-if="!modelValue.length" class="rounded-lg border border-dashed border-theme-border p-4 text-sm text-theme-text-secondary">暂未设置公告，点击“新增公告”开始添加。</p>

    <article v-for="(announcement, index) in modelValue" :key="announcement.id" class="announcement-row min-w-0 rounded-xl border border-theme-border bg-theme-surface p-3 sm:p-4">
      <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
        <label :for="`${editorId}-${announcement.id}`" class="text-sm font-medium text-theme-text">
          第 {{ index + 1 }} 条公告
          <span v-if="index === 0" class="ml-1 text-xs text-blue-600 dark:text-blue-400">最先显示</span>
        </label>
        <div class="grid w-full grid-cols-2 gap-1 sm:flex sm:w-auto">
          <button type="button" :disabled="disabled || index === 0" :aria-label="`将第 ${index + 1} 条公告置顶`" class="announcement-order-button" @click="moveAnnouncement(index, 0)">
            <ChevronsUp :size="14" aria-hidden="true" />置顶
          </button>
          <button type="button" :disabled="disabled || index === 0" :aria-label="`上移第 ${index + 1} 条公告`" class="announcement-order-button" @click="moveAnnouncement(index, index - 1)">
            <ArrowUp :size="14" aria-hidden="true" />上移
          </button>
          <button type="button" :disabled="disabled || index === modelValue.length - 1" :aria-label="`下移第 ${index + 1} 条公告`" class="announcement-order-button" @click="moveAnnouncement(index, index + 1)">
            <ArrowDown :size="14" aria-hidden="true" />下移
          </button>
          <button type="button" :disabled="disabled" :aria-label="`删除第 ${index + 1} 条公告`" class="announcement-order-button text-red-600 dark:text-red-400" @click="removeAnnouncement(index)">
            <Trash2 :size="14" aria-hidden="true" />删除
          </button>
        </div>
      </div>
      <textarea
        :id="`${editorId}-${announcement.id}`"
        :value="announcement.content"
        :disabled="disabled"
        :aria-label="`第 ${index + 1} 条公告内容`"
        rows="4"
        placeholder="填写这条公告的内容"
        class="block w-full min-w-0 resize-y rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm leading-relaxed text-gray-900 focus:border-blue-500 focus:ring-2 focus:ring-blue-500 disabled:opacity-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
        @input="updateContent(index, ($event.target as HTMLTextAreaElement).value)"
      />
    </article>
    <p class="sr-only" role="status">{{ status }}</p>
  </section>
</template>

<style scoped>
.announcement-order-button {
  display: inline-flex;
  min-height: 44px;
  align-items: center;
  justify-content: center;
  gap: 3px;
  padding: 0 8px;
  border-radius: 6px;
  font-size: 12px;
}
.announcement-order-button:hover:not(:disabled) { background: rgb(var(--color-primary-100) / .5); }
.announcement-order-button:disabled { opacity: .35; cursor: not-allowed; }
.announcement-order-button:focus-visible { outline: 2px solid rgb(var(--color-primary-500)); outline-offset: 1px; }
@media (min-width: 640px) { .announcement-order-button { min-height: 36px; } }
</style>

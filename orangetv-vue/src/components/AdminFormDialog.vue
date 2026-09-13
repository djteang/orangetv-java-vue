<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, useId } from 'vue'
import { Loader2, Save, X } from 'lucide-vue-next'

const props = withDefaults(defineProps<{
  title: string
  description?: string
  submitLabel?: string
  busyLabel?: string
  busy?: boolean
  submitDisabled?: boolean
  error?: string
}>(), { submitLabel: '保存', busyLabel: '保存中…' })
const emit = defineEmits<{ close: []; submit: [] }>()
const id = `admin-form-${useId()}`
const dialogRef = ref<HTMLDialogElement | null>(null)
let previousOverflow: string | null = null
let trigger: HTMLElement | null = null
const backdropPressed = ref(false)

function requestClose() {
  if (!props.busy) emit('close')
}

function isBackdrop(event: MouseEvent) {
  const dialog = dialogRef.value
  if (!dialog || event.target !== dialog) return false
  const rect = dialog.getBoundingClientRect()
  return event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom
}

function trapFocus(event: KeyboardEvent) {
  if (event.key !== 'Tab' || event.defaultPrevented || !dialogRef.value) return
  const elements = Array.from(dialogRef.value.querySelectorAll<HTMLElement>('button, a[href], input, textarea, select, [tabindex]'))
    .filter(element => element.tabIndex >= 0 && !element.matches(':disabled, [hidden]') && element.getClientRects().length > 0)
  const first = elements[0]
  const last = elements[elements.length - 1]
  if (!first || !last) {
    event.preventDefault()
    return
  }
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

function submit() {
  if (!props.busy && !props.submitDisabled) emit('submit')
}

onMounted(() => {
  trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
  previousOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  dialogRef.value?.showModal()
  dialogRef.value?.querySelector<HTMLInputElement>('input:not([disabled])')?.focus({ preventScroll: true })
})
onBeforeUnmount(() => {
  dialogRef.value?.close()
  if (previousOverflow !== null) document.body.style.overflow = previousOverflow
  if (trigger?.isConnected) trigger.focus({ preventScroll: true })
})
</script>

<template>
  <Teleport to="body">
    <dialog ref="dialogRef" class="admin-form-dialog" :aria-labelledby="`${id}-title`"
      :aria-describedby="description ? `${id}-description` : undefined" aria-modal="true"
      @cancel.prevent="requestClose" @keydown="trapFocus" @pointerdown="backdropPressed = isBackdrop($event)"
      @click="backdropPressed && isBackdrop($event) && requestClose()">
      <form class="admin-form" :aria-label="title" :aria-busy="busy" novalidate @submit.prevent="submit">
        <header class="admin-form-heading">
          <span class="admin-form-heading-icon"><slot name="icon"><Save :size="22" aria-hidden="true" /></slot></span>
          <div class="admin-form-heading-text"><h2 :id="`${id}-title`">{{ title }}</h2><p v-if="description" :id="`${id}-description`">{{ description }}</p></div>
          <button type="button" class="admin-form-close" :aria-label="`关闭${title}`" :disabled="busy" @click="requestClose"><X :size="20" aria-hidden="true" /></button>
        </header>
        <div class="admin-form-content">
          <fieldset :disabled="busy"><slot /></fieldset>
          <p v-if="error" class="admin-form-error" role="alert">{{ error }}</p>
        </div>
        <footer class="admin-form-footer">
          <div v-if="$slots.actions" class="admin-form-extra-actions"><slot name="actions" /></div>
          <div class="admin-form-buttons">
            <button type="button" class="admin-form-button" :disabled="busy" @click="requestClose">取消</button>
            <button type="submit" class="admin-form-button admin-form-submit" :disabled="busy || submitDisabled">
              <Loader2 v-if="busy" :size="16" class="animate-spin" aria-hidden="true" /><Save v-else :size="16" aria-hidden="true" />
              {{ busy ? busyLabel : submitLabel }}
            </button>
          </div>
        </footer>
      </form>
    </dialog>
  </Teleport>
</template>

<style scoped>
.admin-form-dialog { width: calc(100% - 32px); max-width: 600px; max-height: calc(100vh - 48px); max-height: calc(100dvh - 48px); margin: auto; padding: 0; overflow: hidden; border: 1px solid rgb(var(--color-theme-border)); border-radius: 22px; background: rgb(var(--color-theme-surface)); color: rgb(var(--color-theme-text)); box-shadow: 0 24px 90px rgb(0 0 0 / .22); }
.admin-form-dialog[open] { display: flex; flex-direction: column; }
.admin-form-dialog::backdrop { background: rgb(0 0 0 / .45); backdrop-filter: blur(5px); }
.admin-form { display: flex; flex-direction: column; min-height: 0; }
.admin-form-heading { display: flex; flex-shrink: 0; align-items: center; gap: 13px; padding: 24px 26px 20px; }
.admin-form-heading-icon { display: grid; width: 45px; height: 45px; flex-shrink: 0; place-items: center; border-radius: 14px; background: rgb(var(--color-theme-accent) / .1); color: rgb(var(--color-theme-accent)); }
.admin-form-heading-text { flex: 1; min-width: 0; }
.admin-form-heading h2 { font-size: 19px; font-weight: 650; }
.admin-form-heading p { margin-top: 3px; font-size: 12px; line-height: 1.7; color: rgb(var(--color-theme-text-secondary)); overflow-wrap: anywhere; }
.admin-form-close { display: grid; width: 40px; height: 40px; flex-shrink: 0; place-items: center; border-radius: 10px; color: rgb(var(--color-theme-text-secondary)); }
.admin-form-close:hover:not(:disabled) { background: rgb(var(--color-theme-border) / .5); color: rgb(var(--color-theme-text)); }
.admin-form-content { flex: 1; min-height: 0; overflow-y: auto; overscroll-behavior: contain; padding: 4px 26px 26px; }
.admin-form-content fieldset { min-width: 0; }
.admin-form-content :deep(.admin-form-fields) { display: grid; grid-template-columns: minmax(0, 1fr); gap: 18px; }
.admin-form-content :deep(.admin-form-field) { display: flex; min-width: 0; flex-direction: column; gap: 8px; font-size: 13px; font-weight: 500; }
.admin-form-content :deep(.admin-form-field input) { width: 100%; min-width: 0; min-height: 44px; padding: 10px 12px; border: 1px solid rgb(var(--color-theme-border)); border-radius: 10px; background: rgb(var(--color-theme-bg) / .55); color: rgb(var(--color-theme-text)); font-size: 14px; font-weight: 400; box-shadow: none; }
.admin-form-content :deep(.admin-form-field small), .admin-form-content :deep(.admin-form-hint) { color: rgb(var(--color-theme-text-secondary)); font-size: 11px; font-weight: 400; line-height: 1.8; overflow-wrap: anywhere; }
.admin-form-content :deep(input::placeholder) { color: rgb(var(--color-theme-text-secondary) / .75); }
.admin-form-content :deep(input:disabled) { cursor: not-allowed; opacity: .6; }
.admin-form-content :deep(input:focus-visible), .admin-form button:focus-visible, .admin-form-footer :deep(button:focus-visible) { outline: 2px solid rgb(var(--color-theme-accent)); outline-offset: 2px; }
.admin-form-error { margin-top: 16px; padding: 12px; border: 1px solid rgb(239 68 68 / .2); border-radius: 10px; background: rgb(239 68 68 / .07); color: #b91c1c; font-size: 12px; line-height: 1.8; overflow-wrap: anywhere; }
:global(.dark) .admin-form-error { color: #fca5a5; }
.admin-form-footer { display: flex; flex-shrink: 0; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: 12px; padding: 16px 26px; border-top: 1px solid rgb(var(--color-theme-border)); background: rgb(var(--color-theme-bg) / .6); }
.admin-form-extra-actions { margin-right: auto; }
.admin-form-buttons { display: flex; flex-wrap: wrap; gap: 8px; }
.admin-form-button, .admin-form-footer :deep(.admin-form-button) { display: inline-flex; min-height: 42px; align-items: center; justify-content: center; gap: 7px; padding: 10px 16px; border: 1px solid rgb(var(--color-theme-border)); border-radius: 10px; background: rgb(var(--color-theme-surface)); color: rgb(var(--color-theme-text)); font-size: 13px; font-weight: 500; }
.admin-form-button:hover:not(:disabled), .admin-form-footer :deep(.admin-form-button:hover:not(:disabled)) { border-color: rgb(var(--color-theme-accent) / .6); color: rgb(var(--color-theme-accent)); }
.admin-form-footer .admin-form-submit { border-color: rgb(var(--color-theme-accent)); background: rgb(var(--color-theme-accent)); color: white; }
.admin-form-footer .admin-form-submit:hover:not(:disabled) { background: rgb(var(--color-theme-accent) / .9); color: white; }
.admin-form button:disabled, .admin-form-footer :deep(button:disabled) { cursor: not-allowed; opacity: .5; }
@media (max-width: 639px), (max-height: 540px) {
  .admin-form-dialog { width: calc(100% - 20px); max-height: calc(100vh - 24px); max-height: calc(100dvh - max(12px, env(safe-area-inset-top, 0px)) - max(12px, env(safe-area-inset-bottom, 0px))); border-radius: 17px; }
  .admin-form-heading { gap: 10px; padding: 18px 16px 16px; }
  .admin-form-content { padding: 4px 16px 20px; }
  .admin-form-footer { padding: 12px 16px; }
  .admin-form-content :deep(.admin-form-field input) { font-size: 16px; }
}
@media (max-height: 540px) { .admin-form-heading { padding-top: 10px; padding-bottom: 10px; } .admin-form-heading-icon { width: 36px; height: 36px; border-radius: 10px; } }
@media (prefers-reduced-motion: reduce) { .admin-form .animate-spin { animation: none; } }
</style>

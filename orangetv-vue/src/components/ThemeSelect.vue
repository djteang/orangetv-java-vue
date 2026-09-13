<script setup lang="ts" generic="T extends string | number">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import { Check, ChevronDown } from 'lucide-vue-next'

const props = defineProps<{
  modelValue: T
  options: readonly { value: T; label: string; disabled?: boolean }[]
  label: string
  id?: string
  disabled?: boolean
  placeholder?: string
}>()
const emit = defineEmits<{ 'update:modelValue': [value: T] }>()
const generatedId = `theme-select-${useId()}`
const triggerId = computed(() => props.id || generatedId)
const menuId = `${generatedId}-options`
const triggerRef = ref<HTMLButtonElement | null>(null)
const menuRef = ref<HTMLDivElement | null>(null)
const isOpen = ref(false)
const activeIndex = ref(-1)
const portalTarget = ref<HTMLElement | string>('body')
const supportsPopover = typeof HTMLElement !== 'undefined' && 'showPopover' in HTMLElement.prototype
const menuStyle = ref({ left: '0px', top: '0px', width: '180px', maxHeight: '280px' })
const selectedOption = computed(() => props.options.find(option => option.value === props.modelValue))
const unavailable = computed(() => props.disabled || !props.options.some(option => !option.disabled))
let searchBuffer = ''
let searchTime = 0

function positionMenu() {
  const trigger = triggerRef.value
  const menu = menuRef.value
  if (!isOpen.value || !trigger || !menu) return
  const rect = trigger.getBoundingClientRect()
  const viewport = window.visualViewport
  const leftEdge = (viewport?.offsetLeft ?? 0) + 8
  const topEdge = (viewport?.offsetTop ?? 0) + 8
  const rightEdge = leftEdge + (viewport?.width ?? window.innerWidth) - 16
  const bottomEdge = topEdge + (viewport?.height ?? window.innerHeight) - 16
  if (!rect.width || rect.bottom < topEdge || rect.top > bottomEdge) {
    closeMenu()
    return
  }
  const width = Math.min(Math.max(rect.width, 180), rightEdge - leftEdge)
  const below = Math.max(0, bottomEdge - rect.bottom - 6)
  const above = Math.max(0, rect.top - topEdge - 6)
  const openAbove = below < Math.min(menu.scrollHeight, 280) && above > below
  const maxHeight = Math.min(280, openAbove ? above : below)
  const height = Math.min(menu.scrollHeight + 2, maxHeight)
  menuStyle.value = {
    left: `${Math.max(leftEdge, Math.min(rect.left, rightEdge - width))}px`,
    top: `${openAbove ? rect.top - height - 6 : rect.bottom + 6}px`,
    width: `${width}px`,
    maxHeight: `${maxHeight}px`,
  }
}

async function revealActiveOption() {
  await nextTick()
  const menu = menuRef.value
  const option = menu?.querySelector<HTMLElement>(`[data-index="${activeIndex.value}"]`)
  if (!menu || !option) return
  if (option.offsetTop < menu.scrollTop) menu.scrollTop = option.offsetTop
  else if (option.offsetTop + option.offsetHeight > menu.scrollTop + menu.clientHeight) {
    menu.scrollTop = option.offsetTop + option.offsetHeight - menu.clientHeight
  }
}

async function openMenu(index = props.options.findIndex(option => option.value === props.modelValue && !option.disabled)) {
  if (unavailable.value) return
  activeIndex.value = index >= 0 ? index : props.options.findIndex(option => !option.disabled)
  isOpen.value = true
  await nextTick()
  if (!isOpen.value || !menuRef.value) return
  if (supportsPopover && !menuRef.value.matches(':popover-open')) menuRef.value.showPopover()
  positionMenu()
  void revealActiveOption()
}

function closeMenu(restoreFocus = false) {
  if (supportsPopover && menuRef.value?.matches(':popover-open')) menuRef.value.hidePopover()
  isOpen.value = false
  searchBuffer = ''
  if (restoreFocus) triggerRef.value?.focus({ preventScroll: true })
}

function selectOption(index: number) {
  const option = props.options[index]
  if (!option || option.disabled || unavailable.value) return
  emit('update:modelValue', option.value)
  closeMenu(true)
}

function moveActive(direction: number) {
  for (let step = 1; step <= props.options.length; step++) {
    const index = (activeIndex.value + direction * step + props.options.length) % props.options.length
    if (!props.options[index].disabled) {
      activeIndex.value = index
      void revealActiveOption()
      return
    }
  }
}

function handleKeydown(event: KeyboardEvent) {
  if (unavailable.value) return
  if (event.key === 'Escape' && isOpen.value) {
    event.preventDefault()
    event.stopPropagation()
    closeMenu(true)
  } else if (event.key === 'Tab') {
    closeMenu()
  } else if (['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) {
    event.preventDefault()
    event.stopPropagation()
    if (event.key === 'Home' || event.key === 'End') {
      const indices = props.options.map((option, index) => option.disabled ? -1 : index).filter(index => index !== -1)
      void openMenu(event.key === 'Home' ? indices[0] : indices[indices.length - 1])
    } else if (!isOpen.value) {
      void openMenu()
    } else {
      moveActive(event.key === 'ArrowDown' ? 1 : -1)
    }
  } else if ((event.key === 'Enter' || event.key === ' ') && isOpen.value) {
    event.preventDefault()
    selectOption(activeIndex.value)
  } else if (event.key.length === 1 && event.key !== ' ' && !event.ctrlKey && !event.metaKey && !event.altKey) {
    event.preventDefault()
    searchBuffer = (Date.now() - searchTime < 700 ? searchBuffer : '') + event.key.toLocaleLowerCase()
    searchTime = Date.now()
    const index = props.options.findIndex(option => !option.disabled && option.label.toLocaleLowerCase().startsWith(searchBuffer))
    if (index !== -1) void openMenu(index)
  }
}

function handleOutside(event: Event) {
  const target = event.target as Node | null
  if (isOpen.value && !triggerRef.value?.contains(target) && !menuRef.value?.contains(target)) closeMenu()
}

function handleScroll(event: Event) {
  if (!(event.target instanceof Node) || !menuRef.value?.contains(event.target)) positionMenu()
}

watch(unavailable, value => { if (value) closeMenu() })
watch(() => props.options, () => {
  if (!isOpen.value) return
  void openMenu()
}, { deep: true })

onMounted(() => {
  portalTarget.value = triggerRef.value?.closest('dialog') || 'body'
  document.addEventListener('pointerdown', handleOutside)
  document.addEventListener('focusin', handleOutside)
  window.addEventListener('scroll', handleScroll, true)
  window.addEventListener('resize', positionMenu)
  window.visualViewport?.addEventListener('resize', positionMenu)
  window.visualViewport?.addEventListener('scroll', positionMenu)
})
onBeforeUnmount(() => {
  closeMenu()
  document.removeEventListener('pointerdown', handleOutside)
  document.removeEventListener('focusin', handleOutside)
  window.removeEventListener('scroll', handleScroll, true)
  window.removeEventListener('resize', positionMenu)
  window.visualViewport?.removeEventListener('resize', positionMenu)
  window.visualViewport?.removeEventListener('scroll', positionMenu)
})
</script>

<template>
  <div class="theme-select">
    <button :id="triggerId" ref="triggerRef" type="button" role="combobox" class="theme-select-trigger"
      :disabled="unavailable" :aria-label="label" aria-haspopup="listbox" :aria-expanded="isOpen"
      :aria-controls="isOpen ? menuId : undefined" :aria-activedescendant="isOpen && activeIndex >= 0 ? `${menuId}-${activeIndex}` : undefined"
      @click="isOpen ? closeMenu(true) : openMenu()" @keydown="handleKeydown">
      <span>{{ selectedOption?.label ?? placeholder ?? '请选择' }}</span>
      <ChevronDown :size="16" aria-hidden="true" :class="{ 'theme-select-chevron--open': isOpen }" />
    </button>
    <Teleport :to="portalTarget" :disabled="supportsPopover">
      <div v-if="isOpen" :id="menuId" ref="menuRef" :popover="supportsPopover ? 'manual' : undefined"
        class="theme-select-menu" :style="menuStyle" role="listbox" :aria-label="label" @keydown="handleKeydown">
        <button v-for="(option, index) in options" :id="`${menuId}-${index}`" :key="option.value" type="button"
          role="option" tabindex="-1" :data-index="index" :aria-selected="modelValue === option.value"
          :disabled="option.disabled" :class="['theme-select-option', { 'theme-select-option--active': activeIndex === index }]"
          @mousedown.prevent @click="selectOption(index)">
          <span>{{ option.label }}</span><Check v-if="modelValue === option.value" :size="16" aria-hidden="true" />
        </button>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.theme-select { min-width: 0; }
.theme-select-trigger { display: flex; width: 100%; min-height: 40px; align-items: center; justify-content: space-between; gap: 12px; padding: 9px 12px; border: 1px solid rgb(var(--color-theme-border)); border-radius: 10px; background: rgb(var(--color-theme-surface)); color: rgb(var(--color-theme-text)); text-align: left; font-size: 13px; line-height: 1.5; transition: border-color .15s, background-color .15s; }
.theme-select-trigger > span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.theme-select-trigger > svg { flex-shrink: 0; color: rgb(var(--color-theme-text-secondary)); transition: transform .15s; }
.theme-select-chevron--open { transform: rotate(180deg); }
.theme-select-trigger:hover:not(:disabled), .theme-select-trigger[aria-expanded="true"] { border-color: rgb(var(--color-theme-accent) / .65); background: rgb(var(--color-theme-accent) / .04); }
.theme-select-trigger:focus-visible { outline: 2px solid rgb(var(--color-theme-accent)); outline-offset: 2px; }
.theme-select-trigger:disabled, .theme-select-option:disabled { cursor: not-allowed; opacity: .5; }
.theme-select-menu { position: fixed; inset: auto; z-index: 100; margin: 0; padding: 6px; overflow-x: hidden; overflow-y: auto; overscroll-behavior: contain; border: 1px solid rgb(var(--color-theme-border)); border-radius: 12px; background: rgb(var(--color-theme-surface)); color: rgb(var(--color-theme-text)); box-shadow: 0 16px 40px rgb(0 0 0 / .16), 0 4px 12px rgb(0 0 0 / .06); animation: select-appear .15s ease-out; }
.theme-select-option { display: flex; width: 100%; min-height: 40px; align-items: center; gap: 12px; padding: 9px 12px; border-radius: 8px; font-size: 13px; line-height: 1.6; text-align: left; }
.theme-select-option > span { flex: 1; min-width: 0; overflow-wrap: anywhere; }
.theme-select-option > svg { flex-shrink: 0; }
.theme-select-option:hover:not(:disabled), .theme-select-option--active { background: rgb(var(--color-theme-border) / .4); }
.theme-select-option[aria-selected="true"] { background: rgb(var(--color-theme-accent) / .1); color: rgb(var(--color-theme-accent)); font-weight: 600; }
.theme-select-option--active { outline: 2px solid rgb(var(--color-theme-accent) / .45); outline-offset: -2px; }
@keyframes select-appear { from { opacity: 0; transform: translateY(-3px); } to { opacity: 1; transform: translateY(0); } }
@media (prefers-reduced-motion: reduce) { .theme-select-menu { animation: none; } .theme-select-trigger, .theme-select-trigger > svg { transition: none; } }
</style>

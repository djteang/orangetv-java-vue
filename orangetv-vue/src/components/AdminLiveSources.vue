<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Check, Loader2, Pencil, Plus, Radio, RefreshCw, Trash2, Tv } from 'lucide-vue-next'
import AdminFormDialog from './AdminFormDialog.vue'
import * as adminApi from '@/api/admin'
import type { LiveSource } from '@/types'
import { useToast } from '@/composables/useToast'

const toast = useToast()
const sources = ref<LiveSource[]>([])
const loading = ref(false)
const loadError = ref('')
const actionError = ref('')
const pending = ref('')
const showForm = ref(false)
const editingId = ref<number | null>(null)
const deleteId = ref<number | null>(null)
const formError = ref('')
const emptyForm = () => ({ name: '', key: '', url: '', epg: '', ua: '' })
const form = ref(emptyForm())
const editingInline = computed(() => sources.value.some(source => source.id === editingId.value && source.inline))
const enabledCount = computed(() => sources.value.filter(source => source.enabled && !source.disabled).length)
const busy = computed(() => loading.value || !!pending.value)
let requestVersion = 0
let disposed = false

function errorMessage(reason: unknown, fallback: string) {
  if (reason && typeof reason === 'object') {
    const error = reason as { message?: unknown; response?: { data?: { message?: unknown } } }
    const message = error.response?.data?.message ?? error.message
    if (typeof message === 'string' && message.trim()) return message
  }
  return fallback
}

async function reloadSources() {
  const version = ++requestVersion
  loading.value = true
  loadError.value = ''
  try {
    const response = await adminApi.getLiveSources()
    if (disposed || version !== requestVersion) return
    if (!Array.isArray(response)) throw new Error('直播源列表格式异常，请重试。')
    sources.value = response
  } catch {
    if (!disposed && version === requestVersion) loadError.value = '直播源列表加载失败，请重试。'
  } finally {
    if (!disposed && version === requestVersion) loading.value = false
  }
}

function openForm(source?: LiveSource) {
  if (busy.value) return
  editingId.value = source?.id ?? null
  form.value = source
    ? { name: source.name, key: source.key, url: source.url, epg: source.epg ?? '', ua: source.ua ?? '' }
    : emptyForm()
  formError.value = ''
  actionError.value = ''
  deleteId.value = null
  showForm.value = true
}

function closeForm() {
  showForm.value = false
  editingId.value = null
  form.value = emptyForm()
  formError.value = ''
}

function validHttpUrl(value: string) {
  try { return ['http:', 'https:'].includes(new URL(value).protocol) } catch { return false }
}

async function mutate(key: string, request: () => Promise<void>, message: string, onSuccess: () => void) {
  if (busy.value) return
  pending.value = key
  actionError.value = ''
  try {
    await request()
    if (disposed) return
    onSuccess()
    toast.success(message)
    await reloadSources()
  } catch (reason) {
    if (!disposed) {
      const message = errorMessage(reason, '操作失败，请稍后重试。')
      if (key === 'save') formError.value = message
      else actionError.value = message
    }
  } finally {
    if (!disposed) pending.value = ''
  }
}

async function saveSource() {
  if (busy.value) return
  const values = {
    name: form.value.name.trim(), key: form.value.key.trim(), url: form.value.url.trim(),
    epg: form.value.epg.trim(), ua: form.value.ua.trim(),
  }
  formError.value = ''
  if (!values.name || !values.key || !values.url) formError.value = '请填写直播源名称、唯一标识和频道列表地址。'
  else if (!editingInline.value && !validHttpUrl(values.url)) formError.value = '频道列表地址需要是有效的 HTTP 或 HTTPS 地址。'
  else if (values.epg && !validHttpUrl(values.epg)) formError.value = '节目单地址需要是有效的 HTTP 或 HTTPS 地址。'
  else if (sources.value.some(source => source.key === values.key && source.id !== editingId.value)) formError.value = '该唯一标识已被其他直播源使用，请更换。'
  if (formError.value) return
  const id = editingId.value
  let saved = false
  await mutate('save', () => id === null
    ? adminApi.addLiveSource(values.name, values.key, values.url, values.epg, values.ua)
    : adminApi.editLiveSource(id, values.name, values.key, values.url, values.epg, values.ua),
  id === null ? '直播源已添加并启用' : '直播源已保存', () => { saved = true })
  if (saved && !disposed) closeForm()
}

async function toggleSource(source: LiveSource) {
  const enable = !source.enabled || source.disabled
  await mutate('toggle-' + source.id,
    () => enable ? adminApi.enableLiveSource(source.id) : adminApi.disableLiveSource(source.id),
    enable ? '直播源已启用' : '直播源已停用', () => {
      source.enabled = enable
      source.disabled = !enable
    })
}

async function removeSource(source: LiveSource) {
  await mutate('delete-' + source.id, () => adminApi.deleteLiveSource(source.id), '直播源已删除', () => {
    sources.value = sources.value.filter(item => item.id !== source.id)
    deleteId.value = null
    if (editingId.value === source.id) void closeForm()
  })
}

onMounted(reloadSources)
onBeforeUnmount(() => { disposed = true; ++requestVersion })
defineExpose({ reloadSources })
</script>

<template>
  <section class="admin-live" aria-label="直播源管理">
    <div class="admin-live-toolbar">
      <div>
        <p>共 {{ sources.length }} 个直播源，{{ enabledCount }} 个已启用</p>
        <span>支持 M3U、TXT 和 JSON 频道列表；TVBox 配置可从“配置文件”导入。</span>
      </div>
      <div class="admin-live-actions">
        <button type="button" class="admin-live-button" :disabled="busy" @click="reloadSources"><RefreshCw :size="15" :class="{ 'animate-spin': loading }" aria-hidden="true" />刷新列表</button>
        <button type="button" class="admin-live-button admin-live-primary" :disabled="busy" aria-haspopup="dialog" @click="openForm()"><Plus :size="16" aria-hidden="true" />添加直播源</button>
      </div>
    </div>

    <AdminFormDialog v-if="showForm" :title="editingId === null ? '添加直播源' : '编辑直播源'" description="配置频道列表及节目单，保存后可在直播页面观看。" submit-label="保存直播源" :busy="busy" :error="formError" @close="closeForm" @submit="saveSource">
      <template #icon><Radio :size="22" aria-hidden="true" /></template>
      <div class="admin-form-fields">
        <label class="admin-form-field"><span>直播源名称</span><input v-model="form.name" name="liveName" maxlength="100" required placeholder="例如：公共电视" autocomplete="off" /></label>
        <label class="admin-form-field"><span>唯一标识（KEY）</span><input v-model="form.key" name="liveKey" maxlength="50" required placeholder="例如：iptv" autocomplete="off" autocapitalize="off" spellcheck="false" /></label>
        <div v-if="editingInline" class="admin-form-field"><span>配置内频道</span><p class="admin-form-hint">频道内容随配置文件导入。需要更新频道时，请重新同步对应配置文件。</p></div>
        <label v-else class="admin-form-field"><span>频道列表地址</span><input v-model="form.url" name="liveUrl" type="url" maxlength="500" required placeholder="https://example.com/channels.m3u" autocomplete="off" autocapitalize="off" spellcheck="false" /><small>填写包含频道名称和播放地址的 M3U、TXT 或 JSON 列表。</small></label>
        <label class="admin-form-field"><span>节目单地址（EPG，选填）</span><input v-model="form.epg" name="liveEpg" type="url" maxlength="500" placeholder="https://example.com/epg.xml" autocomplete="off" autocapitalize="off" spellcheck="false" /></label>
        <label class="admin-form-field"><span>自定义 UA（选填）</span><input v-model="form.ua" name="liveUa" maxlength="200" placeholder="留空使用默认 User-Agent" autocomplete="off" spellcheck="false" /></label>
      </div>
    </AdminFormDialog>

    <div v-if="loadError" class="admin-live-error admin-live-retry" role="alert"><p>{{ loadError }}</p><button type="button" class="admin-live-button" :disabled="busy" @click="reloadSources">重新加载直播源</button></div>
    <p v-if="actionError" class="admin-live-error" role="alert">{{ actionError }}</p>
    <div v-if="loading && !sources.length" class="admin-live-empty" role="status"><Loader2 :size="26" class="animate-spin" aria-hidden="true" />正在加载直播源…</div>
    <div v-else-if="!loading && !loadError && !sources.length" class="admin-live-empty" role="status"><Radio :size="30" aria-hidden="true" /><p>暂无直播源</p><span>添加直播源后，用户即可从“直播”菜单进入观看。</span></div>

    <div v-if="sources.length" class="admin-live-list" :aria-busy="loading">
      <article v-for="source in sources" :key="source.id" class="admin-live-card" :data-source-key="source.key">
        <header><div><h4>{{ source.name }}</h4><p>KEY：{{ source.key }}<span v-if="source.channelCount != null"> · {{ source.channelCount }} 个频道</span></p></div><span class="admin-live-status" :class="{ 'admin-live-status--enabled': source.enabled && !source.disabled }"><Check v-if="source.enabled && !source.disabled" :size="12" aria-hidden="true" />{{ source.enabled && !source.disabled ? '已启用' : '已停用' }}</span></header>
        <dl><div><dt>频道列表</dt><dd>{{ source.inline ? '配置内频道' : source.url }}</dd></div><div><dt>节目单</dt><dd>{{ source.epg || '未设置' }}</dd></div><div><dt>UA</dt><dd>{{ source.ua || '默认' }}</dd></div></dl>
        <div class="admin-live-card-actions">
          <router-link v-if="source.enabled && !source.disabled" :to="{ path: '/live', query: { source: source.key } }" class="admin-live-button" :aria-label="'观看' + source.name"><Tv :size="14" aria-hidden="true" />观看</router-link>
          <button type="button" class="admin-live-button" role="switch" :aria-checked="source.enabled && !source.disabled" :aria-label="'启用' + source.name" :disabled="busy" @click="toggleSource(source)"><Loader2 v-if="pending === 'toggle-' + source.id" :size="14" class="animate-spin" aria-hidden="true" />{{ source.enabled && !source.disabled ? '停用' : '启用' }}</button>
          <button type="button" class="admin-live-button" :aria-label="'编辑' + source.name" :disabled="busy" @click="openForm(source)"><Pencil :size="14" aria-hidden="true" />编辑</button>
          <button type="button" class="admin-live-button admin-live-danger" :aria-label="'删除' + source.name" :disabled="busy" @click="deleteId = source.id; actionError = ''"><Trash2 :size="14" aria-hidden="true" />删除</button>
        </div>
        <div v-if="deleteId === source.id" class="admin-live-delete" role="group" :aria-label="'删除直播源' + source.name">
          <p>确定删除“{{ source.name }}”？删除后将从直播页移除。</p>
          <div class="admin-live-actions"><button type="button" class="admin-live-button" :disabled="busy" @click="deleteId = null">取消删除</button><button type="button" class="admin-live-button admin-live-danger" :disabled="busy" @click="removeSource(source)"><Loader2 v-if="pending === 'delete-' + source.id" :size="14" class="animate-spin" aria-hidden="true" />确认删除</button></div>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.admin-live { padding: 20px 24px; color: rgb(var(--color-theme-text)); }
.admin-live-toolbar { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 14px; margin-bottom: 20px; }
.admin-live-toolbar p { font-size: 13px; font-weight: 550; }
.admin-live-toolbar > div > span { display: block; margin-top: 7px; font-size: 12px; line-height: 1.8; color: rgb(var(--color-theme-text-secondary)); }
.admin-live-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.admin-live-button { display: inline-flex; min-height: 38px; align-items: center; justify-content: center; gap: 6px; padding: 8px 12px; border: 1px solid rgb(var(--color-theme-border)); border-radius: 8px; background: rgb(var(--color-theme-surface)); color: rgb(var(--color-theme-text)); font-size: 12px; }
.admin-live-button:hover:not(:disabled) { color: rgb(var(--color-theme-accent)); border-color: rgb(var(--color-theme-accent) / .6); }
.admin-live-button:disabled { cursor: not-allowed; opacity: .5; }
.admin-live-primary { background: #2563eb; border-color: #2563eb; color: #fff; }
.admin-live-primary:hover:not(:disabled) { background: #1d4ed8; border-color: #1d4ed8; color: #fff; }
.admin-live-danger { color: #dc2626; }
:global(.dark) .admin-live-danger { color: #fca5a5; }
.admin-live button:focus-visible, .admin-live a:focus-visible, .admin-live input:focus-visible { outline: 2px solid rgb(var(--color-theme-accent)); outline-offset: 3px; }
.admin-live-card header { display: flex; align-items: start; justify-content: space-between; gap: 12px; }
.admin-live h4 { font-size: 14px; font-weight: 600; overflow-wrap: anywhere; }
.admin-live-error { margin: 14px 0; padding: 12px; border-radius: 8px; background: rgb(239 68 68 / .08); color: #b91c1c; font-size: 12px; line-height: 1.8; overflow-wrap: anywhere; }
:global(.dark) .admin-live-error { color: #fca5a5; }
.admin-live-retry { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px; }
.admin-live-empty { display: flex; min-height: 170px; flex-direction: column; align-items: center; justify-content: center; gap: 12px; text-align: center; font-size: 13px; color: rgb(var(--color-theme-text-secondary)); }
.admin-live-empty span { font-size: 12px; line-height: 1.8; }
.admin-live-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); align-items: start; gap: 14px; }
.admin-live-card { min-width: 0; padding: 18px; border: 1px solid rgb(var(--color-theme-border)); border-radius: 12px; background: rgb(var(--color-theme-surface) / .65); }
.admin-live-card header > div { min-width: 0; }
.admin-live-card header p { margin-top: 6px; color: rgb(var(--color-theme-text-secondary)); font-size: 10px; line-height: 1.7; overflow-wrap: anywhere; }
.admin-live-status { display: inline-flex; flex-shrink: 0; align-items: center; gap: 4px; padding: 4px 6px; border-radius: 6px; color: rgb(var(--color-theme-text-secondary)); background: rgb(var(--color-theme-border) / .3); font-size: 10px; }
.admin-live-status--enabled { color: rgb(var(--color-theme-accent)); background: rgb(var(--color-theme-accent) / .09); }
.admin-live-card dl { display: grid; gap: 10px; margin: 18px 0; }
.admin-live-card dl > div { display: grid; grid-template-columns: 54px minmax(0, 1fr); gap: 8px; font-size: 11px; line-height: 1.8; }
.admin-live-card dt { color: rgb(var(--color-theme-text-secondary)); }
.admin-live-card dd { min-width: 0; overflow-wrap: anywhere; }
.admin-live-card-actions { display: flex; flex-wrap: wrap; gap: 7px; }
.admin-live-card-actions .admin-live-button { flex: 1; padding: 7px 8px; }
.admin-live-delete { margin-top: 15px; padding-top: 15px; border-top: 1px solid rgb(var(--color-theme-border)); }
.admin-live-delete p { margin-bottom: 12px; font-size: 12px; line-height: 1.8; overflow-wrap: anywhere; }
@media (max-width: 1023px) { .admin-live-list { grid-template-columns: minmax(0, 1fr); } }
@media (max-width: 639px) { .admin-live { padding: 16px; } .admin-live-card { padding: 14px; } .admin-live-toolbar > .admin-live-actions { width: 100%; } .admin-live-toolbar > .admin-live-actions > button { flex: 1; } .admin-live-card-actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); } .admin-live-card-actions .admin-live-button { white-space: nowrap; } }
@media (prefers-reduced-motion: reduce) { .admin-live .animate-spin { animation: none; } }
</style>

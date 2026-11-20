<script setup lang="ts" generic="Entry extends StorageEntry">
import type { PreviewKind } from './inline-preview'
import type { StorageEntry } from './presentation'
import { Maximize2, RefreshCw } from 'lucide-vue-next'
import { computed, onActivated, onDeactivated, ref, watch } from 'vue'
import { inlinePreviewKind, inlinePreviewLimit } from './inline-preview'
import { formatFileSize } from './presentation'

const props = defineProps<{
  entry: Entry
  paused: boolean
  load: (entry: Entry, kind: PreviewKind, signal: AbortSignal) => Promise<string | Blob>
}>()
const emit = defineEmits<{ expand: [] }>()

const active = ref(true)
const attempt = ref(0)
const state = ref<'idle' | 'loading' | 'image' | 'text' | 'error'>('idle')
const content = ref('')
const error = ref('')
const kind = computed(() => inlinePreviewKind(props.entry))
const unavailable = computed(() => {
  if (!kind.value) {
    return '此格式可通过打开操作查看。'
  }
  if (!Number.isFinite(props.entry.size) || props.entry.size < 0) {
    return '文件大小未知，请通过打开操作查看。'
  }
  if (props.entry.size > inlinePreviewLimit[kind.value]) {
    return `预览上限为 ${formatFileSize(inlinePreviewLimit[kind.value])}，请通过打开操作查看。`
  }
  return ''
})

function imageFailed() {
  error.value = '浏览器无法显示此图片。'
  state.value = 'error'
}

watch([() => props.entry, () => props.paused, active, attempt], ([entry, paused, visible], _previous, onCleanup) => {
  state.value = 'idle'
  content.value = ''
  error.value = ''
  const format = kind.value
  if (!format || unavailable.value || paused || !visible) {
    return
  }
  const controller = new AbortController()
  let objectUrl: string | undefined
  state.value = 'loading'
  // Ignore files passed over during a sequence of arrow-key presses.
  const timer = window.setTimeout(async () => {
    try {
      const result = await props.load(entry, format, controller.signal)
      if (controller.signal.aborted) {
        return
      }
      if (result instanceof Blob) {
        objectUrl = URL.createObjectURL(result)
      }
      content.value = typeof result === 'string' ? result : objectUrl ?? ''
      state.value = format
    }
    catch (failure) {
      if (!controller.signal.aborted) {
        error.value = failure instanceof RangeError
          ? '文件超出预览上限，请通过打开操作查看。'
          : '无法读取文件内容。'
        state.value = 'error'
      }
    }
  }, 150)
  onCleanup(() => {
    window.clearTimeout(timer)
    controller.abort()
    if (objectUrl) {
      URL.revokeObjectURL(objectUrl)
    }
  })
}, { immediate: true })

onActivated(() => { active.value = true })
onDeactivated(() => { active.value = false })
</script>

<template>
  <section class="inline-preview" aria-label="内容预览" :aria-busy="state === 'loading'">
    <h4>内容预览</h4>
    <div class="preview-stage" :class="{ 'preview-stage--previewable': kind }">
      <button v-if="state === 'image'" type="button" class="inline-image" :aria-label="`查看大图 · ${entry.name}`" title="查看大图" @click="emit('expand')">
        <img :key="content" :src="content" :alt="entry.name" decoding="async" referrerpolicy="no-referrer" @error="imageFailed">
        <span class="expand-icon" aria-hidden="true"><Maximize2 :size="14" /></span>
      </button>
      <pre v-else-if="state === 'text' && content.length" class="inline-text" tabindex="0" aria-label="文件文本">{{ content }}</pre>
      <div v-else class="preview-state" role="status">
        <span v-if="state === 'loading'">正在读取文件…</span>
        <template v-else-if="state === 'error'">
          <span>{{ error }}</span>
          <el-button size="small" text :icon="RefreshCw" @click="attempt++">
            重试
          </el-button>
        </template>
        <span v-else>{{ unavailable || (state === 'text' ? '此文件为空。' : '预览已暂停。') }}</span>
      </div>
    </div>
  </section>
</template>

<style scoped>
.inline-preview {
  min-width: 0;
  margin-bottom: 14px;
}

.inline-preview h4 {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--mrr-muted-foreground);
}

/*
 * 预览区高度按「可预览」与否固定：加载占位、失败提示和最终内容共用同一个盒子，
 * 图片解码完成后不会因为「44px 占位 → 208px 图片」把详情栏和整页高度顶起来。
 * 同一目录里图片、文本、不可预览文件之间切换时，详情栏高度也不再跳变。
 */
.preview-stage {
  display: grid;
  min-width: 0;
}

.preview-stage--previewable {
  height: clamp(132px, 22vh, 208px);
}

.preview-stage--previewable > * {
  min-height: 0;
}

.inline-image,
.inline-text,
.preview-state {
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-md);
}

.inline-image {
  position: relative;
  display: grid;
  grid-template: minmax(0, 1fr) / minmax(0, 1fr);
  place-items: center;
  width: 100%;
  height: 100%;
  padding: 8px;
  cursor: zoom-in;
}

.inline-image img {
  display: block;
  width: 100%;
  height: 100%;
  min-height: 0;
  object-fit: contain;
}

.expand-icon {
  position: absolute;
  right: 8px;
  bottom: 8px;
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  color: var(--mrr-card-foreground);
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-md);
}

.inline-text {
  height: 100%;
  max-height: 100%;
  padding: 10px 12px;
  margin: 0;
  overflow: auto;
  font-size: 12px;
  line-height: 1.6;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.inline-text:focus-visible,
.inline-image:focus-visible {
  outline: 2px solid var(--mrr-ring);
  outline-offset: -2px;
}

.preview-state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  padding: 6px 10px;
  font-size: 12px;
  color: var(--mrr-muted-foreground);
}
</style>

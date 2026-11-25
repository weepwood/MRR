<script setup lang="ts">
import { ElDialog } from 'element-plus'
import { getReviewImage } from '@/api/modules/classification'
import AppError from '@/components/AppError/index.vue'
import AppLoading from '@/components/AppLoading/index.vue'

const props = withDefaults(defineProps<{ scanId: number | null, inline?: boolean }>(), { inline: false })
const emit = defineEmits<{ close: [] }>()
const source = ref('')
const loading = ref(false)
const failed = ref(false)
let generation = 0
let controller: AbortController | undefined

function clear() {
  generation++
  controller?.abort()
  controller = undefined
  if (source.value) {
    URL.revokeObjectURL(source.value)
  }
  source.value = ''
  loading.value = false
  failed.value = false
}

async function load() {
  clear()
  const id = props.scanId
  if (!id || id <= 0) {
    return
  }
  const current = generation
  loading.value = true
  controller = new AbortController()
  try {
    const blob = await getReviewImage(id, controller.signal)
    if (generation !== current) {
      return
    }
    if (!(blob instanceof Blob) || !blob.size || !blob.type.startsWith('image/')) {
      throw new Error('Invalid image')
    }
    source.value = URL.createObjectURL(blob)
  }
  catch {
    if (generation === current) {
      failed.value = true
    }
  }
  finally {
    if (generation === current) {
      loading.value = false
      controller = undefined
    }
  }
}

function close() {
  clear()
  emit('close')
}

watch(() => props.scanId, () => { void load() }, { immediate: true })
watch(() => useUserStore().token, close, { flush: 'sync' })
onBeforeUnmount(clear)
</script>

<template>
  <component
    :is="inline ? 'section' : ElDialog"
    v-bind="inline ? { 'aria-label': '原图核对', 'class': 'scan-inline' } : { modelValue: scanId !== null, title: `影像核对 #${scanId ?? ''}`, width: 'min(880px, 94vw)', appendToBody: true, destroyOnClose: true }"
    @update:model-value="!$event && close()"
  >
    <AppLoading v-if="loading" />
    <AppError v-else-if="failed" message="影像不可读或已变化，请重试" @retry="load()" />
    <img v-else-if="source" :src="source" alt="待核对影像" class="scan-content" @error="failed = true">
  </component>
</template>

<style scoped>
.scan-inline {
  display: grid;
  place-items: center;
  min-height: 240px;
  background: var(--el-fill-color-light);
  border-radius: var(--mrr-radius-card);
}

.scan-content {
  display: block;
  max-width: 100%;
  max-height: 70vh;
  margin-inline: auto;
  object-fit: contain;
}
</style>

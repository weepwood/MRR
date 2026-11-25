<script setup lang="ts">
import { getReviewImage } from '@/api/modules/classification'
import AppError from '@/components/AppError/index.vue'
import AppLoading from '@/components/AppLoading/index.vue'

const props = defineProps<{ scanId: number | null }>()
const emit = defineEmits<{ close: [] }>()
const source = ref('')
const loading = ref(false)
const failed = ref(false)
let generation = 0

function clear() {
  generation++
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
  try {
    const response = await getReviewImage(id)
    if (generation !== current) {
      return
    }
    if (!(response.data instanceof Blob) || !response.data.size || !response.data.type.startsWith('image/')) {
      throw new Error('Invalid image')
    }
    source.value = URL.createObjectURL(response.data)
  }
  catch {
    if (generation === current) {
      failed.value = true
    }
  }
  finally {
    if (generation === current) {
      loading.value = false
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
  <el-dialog :model-value="scanId !== null" :title="`影像核对 #${scanId ?? ''}`" width="min(880px, 94vw)" append-to-body destroy-on-close @update:model-value="!$event && close()">
    <AppLoading v-if="loading" />
    <AppError v-else-if="failed" message="影像不可读或已变化，请重试" @retry="load()" />
    <img v-else-if="source" :src="source" alt="待核对影像" class="scan-content" @error="failed = true">
  </el-dialog>
</template>

<style scoped>
.scan-content {
  display: block;
  max-width: 100%;
  max-height: 70vh;
  margin-inline: auto;
  object-fit: contain;
}
</style>

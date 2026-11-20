<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  name?: string
  url: string
  loading: boolean
  failed?: boolean
}>()

const emit = defineEmits<{ retry: [] }>()
const visible = defineModel<boolean>({ required: true })
const title = computed(() => props.name ? `预览 · ${props.name}` : '图片预览')
</script>

<template>
  <el-dialog v-model="visible" :title="title" width="min(920px, 92vw)" destroy-on-close>
    <div v-loading="loading" class="preview-content" :aria-busy="loading">
      <el-image
        v-if="url"
        :key="url"
        :src="url"
        :alt="name"
        :preview-src-list="[url]"
        fit="contain"
        preview-teleported
        hide-on-click-modal
      >
        <template #error>
          <el-empty description="无法显示此图片" :image-size="64">
            <el-button @click="emit('retry')">
              重新加载
            </el-button>
          </el-empty>
        </template>
      </el-image>
      <el-empty v-else-if="failed" description="图片读取失败" :image-size="64">
        <el-button @click="emit('retry')">
          重新加载
        </el-button>
      </el-empty>
    </div>
  </el-dialog>
</template>

<style scoped>
.preview-content {
  display: grid;
  min-height: min(42vh, 300px);
  overflow: hidden;
  background: var(--el-fill-color-light);
  border-radius: var(--mrr-radius-md);
}

.preview-content :deep(.el-image) {
  display: grid;
  width: 100%;
  height: min(66vh, 680px);
}

.preview-content :deep(.el-empty) {
  align-self: center;
}
</style>

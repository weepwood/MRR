<script setup lang="ts">
import { computed } from 'vue'
import AppError from '@/components/AppError/index.vue'

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
          <AppError size="compact" message="无法显示此图片" retry-text="重新加载" @retry="emit('retry')" />
        </template>
      </el-image>
      <AppError v-else-if="failed" size="compact" message="图片读取失败" retry-text="重新加载" @retry="emit('retry')" />
    </div>
  </el-dialog>
</template>

<style scoped>
/*
 * 加载遮罩、失败空态和图片共用同一个固定高度的盒子：
 * 之前这里只给了 min-height，图片解码完成后 el-image 才撑到 66vh，
 * 对话框会突然变高并重新居中，看起来就是一次高度闪烁。
 */
.preview-content {
  display: grid;
  height: min(66vh, 680px);
  min-height: 240px;
  overflow: hidden;
  background: var(--el-fill-color-light);
  border-radius: var(--mrr-radius-md);
}

.preview-content :deep(.el-image) {
  display: grid;
  grid-template-rows: minmax(0, 1fr);
  width: 100%;
  height: 100%;
}

/*
 * el-image 默认只给 inner 设宽度，高图会按原比例撑高再被 overflow 裁掉。
 * 这里按盒子收口，靠 object-fit: contain 完整显示，同时保证图片高度恒等于盒子高度。
 */
.preview-content :deep(.el-image__inner) {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.preview-content :deep(.app-error) {
  align-self: center;
}
</style>

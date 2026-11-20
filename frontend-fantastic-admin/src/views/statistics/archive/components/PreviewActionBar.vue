<script setup lang="ts">
import type { GalleryImage } from '../types'
import { ArrowLeft, ArrowRight } from 'lucide-vue-next'
import { computed, ref, watch } from 'vue'
import { actionDialog } from '@/ui/components/ActionDialog'
import { TYPE_OPTIONS } from '../constants'

defineOptions({ name: 'PreviewActionBar' })

const props = withDefaults(defineProps<{
  image: GalleryImage | null
  index: number
  total: number
  isSelected?: boolean
  showSelectionStatus?: boolean
  savingType?: boolean
}>(), {
  isSelected: false,
  showSelectionStatus: true,
  savingType: false,
})

const emit = defineEmits<{
  saveType: [type: number]
  navigate: [delta: number]
  toggle: []
}>()

const pendingType = ref(0)

const currentType = computed(() => Number(props.image?.btype || 0))

async function confirmType() {
  if (pendingType.value === currentType.value) {
    return
  }
  const label = TYPE_OPTIONS.find(item => item.value === pendingType.value)?.label || '所选分类'
  try {
    await actionDialog.confirm(
      `确认将当前影像分类切换为“${label}”吗？`,
      '确认分类变更',
      { confirmButtonText: '确认切换', cancelButtonText: '取消', type: 'warning' },
    )
    emit('saveType', pendingType.value)
  }
  catch {
    pendingType.value = currentType.value
  }
}

watch(() => props.image?.btype, () => {
  pendingType.value = currentType.value
}, { immediate: true })
</script>

<template>
  <div class="preview-toolbar" :class="{ 'preview-toolbar--navigation-only': !props.showSelectionStatus }">
    <el-select
      v-model="pendingType"
      class="preview-type-select"
      popper-class="preview-type-popper"
      aria-label="影像分类"
      :loading="props.savingType"
      size="small"
      @change="confirmType"
    >
      <el-option v-for="item in TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
    </el-select>

    <div
      class="preview-controls"
      :class="{ 'preview-controls--navigation-only': !props.showSelectionStatus }"
    >
      <div class="page-navigation" aria-label="影像翻页">
        <el-button circle size="small" :icon="ArrowLeft" :disabled="props.index === 0" aria-label="上一张影像" @click="emit('navigate', -1)" />
        <el-button circle size="small" :icon="ArrowRight" :disabled="props.index >= props.total - 1" aria-label="下一张影像" @click="emit('navigate', 1)" />
      </div>
      <el-button v-if="props.showSelectionStatus" size="small" type="primary" :plain="!props.isSelected" @click="emit('toggle')">
        P{{ props.image?.pages ?? '-' }} {{ props.isSelected ? '已选' : '选中' }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
/* 分类下拉挂在 body 上，默认 z-index 只有 2000 出头；工具条是 fixed 且 z-index 3001，
 * 全屏时下拉向上展开会被工具条盖住、下半截点不动。这里抬到工具条之上。
 * 层级梯度：影像查看器 3000 < 预览工具条 3001 < 下拉 3002 < 确认弹窗 3200（ActionDialog）< 提示条 3300 < 水印 4000。 */
:global(.preview-type-popper) {
  z-index: 3002 !important;
}

/* 全屏影像之上留一层悬浮工具条：边看大图边改分类。层级梯度见上方说明。 */
.preview-toolbar {
  position: fixed;
  right: 16px;
  bottom: 16px;
  z-index: 3001;
  display: grid;
  gap: 6px;
  width: min(180px, calc(100vw - 32px));
  min-width: 0;
  padding: 8px;
  background: hsl(var(--card) / 92%);
  border: 1px solid var(--divider);
  border-radius: var(--mrr-radius-md);
  box-shadow: 0 4px 12px rgb(0 0 0 / 6%);
}

.preview-controls,
.page-navigation {
  display: flex;
  align-items: center;
}

.preview-controls {
  gap: 6px;
  justify-content: space-between;
  width: 100%;
}

.preview-controls--navigation-only {
  justify-content: flex-start;
}

.preview-toolbar--navigation-only {
  width: auto;
}

.preview-toolbar--navigation-only .preview-controls {
  width: auto;
}

.page-navigation {
  flex: none;
  gap: 2px;
}

.preview-type-select {
  width: 100%;
}

.preview-type-select :deep(.el-select__wrapper) {
  min-height: var(--mrr-input-height);
}

@media (max-width: 720px) {
  .preview-toolbar {
    right: 8px;
    bottom: 8px;
    width: min(180px, calc(100vw - 16px));
  }

  .preview-controls {
    gap: 4px;
  }
}
</style>

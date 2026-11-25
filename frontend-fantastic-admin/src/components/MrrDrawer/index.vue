<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { X } from 'lucide-vue-next'
import { cn } from '@/utils'

defineOptions({ inheritAttrs: false })

withDefaults(defineProps<{
  title: string
  description?: string
  contentClass?: HTMLAttributes['class']
  overlayClass?: string
  footer?: boolean
  destroyOnClose?: boolean
  showCancelButton?: boolean
}>(), {
  footer: true,
  destroyOnClose: true,
})
const emit = defineEmits<{ confirm: [], closed: [] }>()
const open = defineModel<boolean>({ default: false })
function confirm() {
  open.value = false
  emit('confirm')
}
</script>

<template>
  <el-drawer
    v-model="open"
    v-bind="$attrs"
    append-to-body
    :title="title"
    :class="cn('mrr-drawer-shell', contentClass)"
    :modal-class="cn('mrr-dialog-mask', overlayClass)"
    size="100%"
    :destroy-on-close="destroyOnClose"
    :close-icon="X"
    header-class="mrr-drawer-header mrr-overlay-border-bottom"
    body-class="mrr-drawer-body"
    footer-class="mrr-overlay-footer mrr-overlay-border-top"
    @closed="emit('closed')"
  >
    <template #header>
      <div class="mrr-drawer-heading">
        <h2 class="mrr-overlay-title">
          {{ title }}
        </h2>
        <p v-if="description" class="mrr-overlay-description">
          {{ description }}
        </p>
      </div>
    </template>
    <slot />
    <template v-if="footer" #footer>
      <el-button v-if="showCancelButton" class="mrr-button-flow" :auto-insert-space="false" @click="open = false">
        取消
      </el-button>
      <el-button type="primary" class="mrr-button-flow" :auto-insert-space="false" @click="confirm">
        确定
      </el-button>
    </template>
  </el-drawer>
</template>

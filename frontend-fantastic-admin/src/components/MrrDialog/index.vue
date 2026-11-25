<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { X } from 'lucide-vue-next'
import { cn } from '@/utils'

defineOptions({ inheritAttrs: false })

const props = withDefaults(defineProps<{
  title?: string
  description?: string
  class?: HTMLAttributes['class']
  contentClass?: string
  headerClass?: string
  footerClass?: string
  overlayClass?: string
  alignCenter?: boolean
  appearance?: 'business' | 'custom'
  width?: string | number
  border?: boolean
  header?: boolean
  footer?: boolean
  closable?: boolean
  closeOnClickOverlay?: boolean
  closeOnPressEscape?: boolean
  showCancelButton?: boolean
}>(), {
  alignCenter: true,
  appearance: 'business',
  width: '540px',
  border: true,
  header: true,
  footer: true,
  closable: true,
  closeOnClickOverlay: true,
  closeOnPressEscape: true,
})
const emit = defineEmits<{ confirm: [], opened: [], closed: [] }>()
const open = defineModel<boolean>({ default: false })
function confirm() {
  open.value = false
  emit('confirm')
}
</script>

<template>
  <el-dialog
    v-model="open"
    v-bind="$attrs"
    append-to-body
    destroy-on-close
    :title="title"
    :class="cn('mrr-dialog-shell', { 'mrr-dialog-shell--business': appearance === 'business' }, props.class)"
    :width="width"
    :modal-class="cn('mrr-dialog-mask', overlayClass)"
    :align-center="alignCenter"
    :show-close="closable"
    :close-icon="X"
    :close-on-click-modal="closeOnClickOverlay"
    :close-on-press-escape="closeOnPressEscape"
    :header-class="cn('mrr-dialog-header', { 'mrr-dialog-header-hidden': !header, 'mrr-overlay-border-bottom': header && border }, headerClass)"
    :body-class="cn('mrr-dialog-body', contentClass)"
    :footer-class="cn('mrr-overlay-footer', { 'mrr-overlay-border-top': border }, footerClass)"
    @opened="emit('opened')"
    @closed="emit('closed')"
  >
    <template #header>
      <slot v-if="header" name="header">
        <h2 class="mrr-overlay-title">
          {{ title }}
        </h2>
        <p v-if="description" class="mrr-overlay-description">
          {{ description }}
        </p>
      </slot>
    </template>
    <slot />
    <template v-if="footer" #footer>
      <slot name="footer">
        <el-button v-if="showCancelButton" class="mrr-button-flow" :auto-insert-space="false" @click="open = false">
          取消
        </el-button>
        <el-button type="primary" class="mrr-button-flow" :auto-insert-space="false" @click="confirm">
          确定
        </el-button>
      </slot>
    </template>
  </el-dialog>
</template>

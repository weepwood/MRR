<script setup lang="ts">
import type { IdentifierTone } from '@/utils/identifier-input'
import { computed } from 'vue'

defineOptions({ name: 'MrrInputLengthBadge' })

const props = withDefaults(defineProps<{
  /** 已输入的字符数，0 表示空值（不渲染）。 */
  length: number
  /** 标准位数；给了就显示「6/8」，不给就只显示「15 位」。 */
  total?: number
  /** 配色语义：neutral 灰、ok 绿、warn 橙。 */
  tone?: IdentifierTone
  /** 字段名，用于读屏播报。 */
  label?: string
}>(), {
  total: undefined,
  tone: 'neutral',
  label: '编号',
})

const text = computed(() => (props.total ? `${props.length}/${props.total}` : `${props.length} 位`))
const ariaLabel = computed(() => `${props.label}已输入 ${props.length} 位`)
</script>

<template>
  <span
    v-if="length > 0"
    class="length-badge"
    :class="`is-${tone}`"
    :aria-label="ariaLabel"
  >{{ text }}</span>
</template>

<style scoped>
/* 位数随输入实时变化，tabular-nums 让数字等宽、定宽避免徽标左右抖动；字体跟随项目统一字体。
 * 号码本身要占满 141px 的半栏输入框，徽标只能让位：9px、最小 16px 宽。 */
.length-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 16px;
  font-size: 9px;
  font-variant-numeric: tabular-nums;
  line-height: 1;
  color: var(--text-secondary);
  letter-spacing: 0;
  white-space: nowrap;
  user-select: none;
}

.length-badge.is-ok {
  color: var(--el-color-success);
}

.length-badge.is-warn {
  color: var(--el-color-warning);
}
</style>

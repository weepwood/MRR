<script setup lang="ts">
import { cn } from '@/utils'

defineOptions({
  name: 'FaButtonGroup',
})

const props = defineProps<{
  vertical?: boolean
}>()
</script>

<template>
  <!--
    拼接几何在 components/form-controls.css 的 .fa-button-group 里，
    与 .el-button-group 同一种处理：相邻按钮重叠 1px、只保留外缘圆角。
  -->
  <div :class="cn('fa-button-group', { 'fa-button-group--vertical': props.vertical })">
    <slot />
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'MrrStatusTag' })

const props = withDefaults(defineProps<{
  status?: string
  label?: string
  tone?: StatusTone
  /**
   * 进行中提示（状态点外的呼吸光圈）。不传时，自动识别为「运行中 / 处理中」的状态开启；
   * 显式传 tone 时默认关闭，需要时再传 live。
   */
  live?: boolean
}>(), {
  // 布尔 prop 缺省会被 Vue 转成 false，这里显式保留 undefined 以区分「未传」与「关闭」。
  live: undefined,
})

type StatusTone = 'success' | 'info' | 'warning' | 'danger' | 'neutral'

const RUNNING_STATUSES = ['running', 'processing', 'pending-review', '进行中', '处理中', '运行中']

const normalizedStatus = computed(() => String(props.status || '').trim().toLowerCase())

const statusMeta = computed(() => {
  if (props.tone) {
    return { tone: props.tone, label: props.label || props.status || '-' }
  }

  const status = normalizedStatus.value
  if (['active', 'enabled', 'success', 'completed', 'complete', 'up', 'healthy', '启用', '正常', '成功', '完成'].includes(status)) {
    return { tone: 'success' as const, label: props.label || (status === 'active' ? '启用' : props.status || '正常') }
  }
  if (RUNNING_STATUSES.includes(status)) {
    return { tone: 'info' as const, label: props.label || props.status || '处理中' }
  }
  if (['warning', 'pending', 'waiting', '待处理', '警告', '等待'].includes(status)) {
    return { tone: 'warning' as const, label: props.label || props.status || '待处理' }
  }
  if (['disabled', 'failed', 'failure', 'error', 'critical', 'down', '禁用', '失败', '异常'].includes(status)) {
    return { tone: 'danger' as const, label: props.label || (status === 'disabled' ? '禁用' : props.status || '异常') }
  }
  return { tone: 'neutral' as const, label: props.label || props.status || '未知' }
})

const isLive = computed(() => props.live ?? (!props.tone && RUNNING_STATUSES.includes(normalizedStatus.value)))
</script>

<template>
  <span class="mrr-status-tag" :class="[`mrr-status-tag--${statusMeta.tone}`, { 'mrr-status-tag--live': isLive }]">
    <span class="mrr-status-tag__dot" aria-hidden="true" />
    <span>{{ statusMeta.label }}</span>
  </span>
</template>

<style scoped>
.mrr-status-tag {
  --mrr-status-color: var(--mrr-muted-foreground);
  --mrr-status-soft: var(--mrr-tone-neutral-soft);
  --mrr-status-border: var(--mrr-tone-neutral-border);

  display: inline-flex;
  gap: 5px;
  align-items: center;
  min-height: 24px;
  padding: 2px 8px;
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
  color: var(--mrr-status-color);
  white-space: nowrap;

  /*
   * 直接读 tones.css 的 -soft / -border：调色板（theme-palette.ts）按明暗调整的
   * 浅底与描边强度（暗色 16% / 34%）会自动生效；此前这里自写 9% / 18%，
   * 调色板的调整到不了状态胶囊。两组令牌在 browser-compatibility.css 都有静态回退。
   */
  background: var(--mrr-status-soft);
  border: 1px solid var(--mrr-status-border);
  border-radius: var(--mrr-radius-sm);
}

.mrr-status-tag__dot {
  position: relative;
  width: 6px;
  height: 6px;
  background: currentcolor;
  border-radius: 50%;
}

.mrr-status-tag--live .mrr-status-tag__dot::after {
  position: absolute;
  inset: 0;
  content: "";
  background: currentcolor;
  border-radius: 50%;
  opacity: 0;
  animation: mrr-live-pulse calc(var(--mrr-motion-loop-soft) * 4 / 3) var(--mrr-ease-out) infinite;
}

.mrr-status-tag--success {
  --mrr-status-color: var(--mrr-tone-success);
  --mrr-status-soft: var(--mrr-tone-success-soft);
  --mrr-status-border: var(--mrr-tone-success-border);
}

.mrr-status-tag--info {
  --mrr-status-color: var(--mrr-tone-info);
  --mrr-status-soft: var(--mrr-tone-info-soft);
  --mrr-status-border: var(--mrr-tone-info-border);
}

.mrr-status-tag--warning {
  --mrr-status-color: var(--mrr-tone-warning);
  --mrr-status-soft: var(--mrr-tone-warning-soft);
  --mrr-status-border: var(--mrr-tone-warning-border);
}

.mrr-status-tag--danger {
  --mrr-status-color: var(--mrr-destructive);
  --mrr-status-soft: var(--mrr-tone-danger-soft);
  --mrr-status-border: var(--mrr-tone-danger-border);
}

/* Chrome 86：-soft / -border 令牌本身就有静态回退，不再需要单独的 @supports 分支。 */

/* 强制颜色模式会清掉底色，光圈没有意义，只保留状态点与文字。 */
@media (forced-colors: active) {
  .mrr-status-tag--live .mrr-status-tag__dot::after {
    display: none;
  }
}
</style>

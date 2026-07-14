<script setup lang="ts">
type StatusTone = 'success' | 'info' | 'warning' | 'danger' | 'neutral'

const props = defineProps<{
  status?: string
  label?: string
  tone?: StatusTone
}>()

const normalizedStatus = computed(() => String(props.status || '').trim().toLowerCase())

const statusMeta = computed(() => {
  if (props.tone) {
    return { tone: props.tone, label: props.label || props.status || '-' }
  }

  const status = normalizedStatus.value
  if (['active', 'enabled', 'success', 'completed', 'complete', 'up', 'healthy', '启用', '正常', '成功', '完成'].includes(status)) {
    return { tone: 'success' as const, label: props.label || (status === 'active' ? '启用' : props.status || '正常') }
  }
  if (['running', 'processing', 'pending-review', '进行中', '处理中', '运行中'].includes(status)) {
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
</script>

<template>
  <span class="mrr-status-tag" :class="`mrr-status-tag--${statusMeta.tone}`">
    <span class="mrr-status-tag__dot" aria-hidden="true" />
    <span>{{ statusMeta.label }}</span>
  </span>
</template>

<style scoped>
.mrr-status-tag {
  --mrr-status-color: var(--text-secondary);
  --mrr-status-bg: var(--surface-muted);

  display: inline-flex;
  gap: 6px;
  align-items: center;
  min-height: 24px;
  padding: 3px 9px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  color: var(--mrr-status-color);
  white-space: nowrap;
  background: var(--mrr-status-bg);
  border: 1px solid color-mix(in srgb, var(--mrr-status-color) 18%, transparent);
  border-radius: var(--mrr-radius-pill);
}

.mrr-status-tag__dot {
  width: 6px;
  height: 6px;
  background: currentcolor;
  border-radius: 50%;
}

.mrr-status-tag--success {
  --mrr-status-color: #15803d;
  --mrr-status-bg: #f0fdf4;
}

.mrr-status-tag--info {
  --mrr-status-color: #0369a1;
  --mrr-status-bg: #f0f9ff;
}

.mrr-status-tag--warning {
  --mrr-status-color: #b45309;
  --mrr-status-bg: #fffbeb;
}

.mrr-status-tag--danger {
  --mrr-status-color: #b91c1c;
  --mrr-status-bg: #fef2f2;
}

:global(.dark) .mrr-status-tag--success {
  --mrr-status-color: #86efac;
  --mrr-status-bg: rgb(22 101 52 / 24%);
}

:global(.dark) .mrr-status-tag--info {
  --mrr-status-color: #7dd3fc;
  --mrr-status-bg: rgb(3 105 161 / 24%);
}

:global(.dark) .mrr-status-tag--warning {
  --mrr-status-color: #fcd34d;
  --mrr-status-bg: rgb(180 83 9 / 24%);
}

:global(.dark) .mrr-status-tag--danger {
  --mrr-status-color: #fca5a5;
  --mrr-status-bg: rgb(185 28 28 / 24%);
}
</style>

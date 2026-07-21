<script setup lang="ts">
defineOptions({ name: 'MrrStatusTag' })

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
  --mrr-status-color: var(--mrr-muted-foreground);

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
  background: color-mix(in srgb, var(--mrr-status-color) 9%, var(--mrr-card));
  border: 1px solid color-mix(in srgb, var(--mrr-status-color) 18%, transparent);
  border-radius: var(--mrr-radius-sm);
}

.mrr-status-tag__dot {
  width: 5px;
  height: 5px;
  background: currentcolor;
  border-radius: 50%;
}

.mrr-status-tag--success {
  --mrr-status-color: #16803c;
}

.mrr-status-tag--info {
  --mrr-status-color: #087ea4;
}

.mrr-status-tag--warning {
  --mrr-status-color: #b45f06;
}

.mrr-status-tag--danger {
  --mrr-status-color: var(--mrr-destructive);
}
</style>

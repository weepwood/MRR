<script setup lang="ts">
import type { MrrTableAction } from './types'
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  actions: MrrTableAction[]
  maxInline?: number
  moreLabel?: string
  permissionChecker?: (permission: string | string[]) => boolean
}>(), {
  maxInline: 2,
  moreLabel: '更多操作',
})

const emit = defineEmits<{
  select: [key: string]
}>()

const availableActions = computed(() => props.actions.filter((action) => {
  if (action.visible === false) {
    return false
  }
  if (!action.permission) {
    return true
  }
  return Boolean(props.permissionChecker?.(action.permission))
}))

const orderedActions = computed(() => {
  const inline = availableActions.value.filter(action => action.placement === 'inline')
  const automatic = availableActions.value.filter(action => !action.placement || action.placement === 'auto')
  const overflow = availableActions.value.filter(action => action.placement === 'overflow')
  return [...inline, ...automatic, ...overflow]
})

const inlineActions = computed(() => orderedActions.value
  .slice(0, Math.max(0, props.maxInline)))

const inlineKeys = computed(() => new Set(inlineActions.value.map(action => action.key)))
const overflowActions = computed(() => orderedActions.value.filter(action => !inlineKeys.value.has(action.key)))

function actionTooltip(action: MrrTableAction) {
  return action.disabled && action.disabledReason ? action.disabledReason : action.label
}

function handleAction(action: MrrTableAction) {
  if (action.disabled || action.loading) {
    return
  }
  emit('select', action.key)
}

function handleOverflowCommand(key: string) {
  const action = overflowActions.value.find(item => item.key === key)
  if (action) {
    handleAction(action)
  }
}

defineExpose({ availableActions, inlineActions, overflowActions })
</script>

<template>
  <div v-if="availableActions.length" class="mrr-table-actions" @click.stop>
    <el-tooltip
      v-for="action in inlineActions"
      :key="action.key"
      :content="actionTooltip(action)"
      placement="top"
      :show-after="250"
    >
      <span class="mrr-table-actions__button-wrap">
        <el-button
          text
          circle
          :aria-label="action.label"
          :disabled="action.disabled"
          :loading="action.loading"
          class="mrr-table-actions__button"
          :class="`mrr-table-actions__button--${action.tone || 'default'}`"
          @click="handleAction(action)"
        >
          <FaIcon v-if="!action.loading" :name="action.icon" />
        </el-button>
      </span>
    </el-tooltip>

    <el-dropdown
      v-if="overflowActions.length"
      trigger="click"
      placement="bottom-end"
      :teleported="true"
      :hide-on-click="true"
      @command="handleOverflowCommand"
    >
      <el-button
        text
        circle
        :aria-label="moreLabel"
        class="mrr-table-actions__button mrr-table-actions__button--more"
        @click.stop
      >
        <FaIcon name="i-ri:more-fill" />
      </el-button>

      <template #dropdown>
        <el-dropdown-menu class="mrr-table-actions__menu">
          <el-dropdown-item
            v-for="action in overflowActions"
            :key="action.key"
            :command="action.key"
            :disabled="action.disabled || action.loading"
            :title="action.disabledReason || action.label"
            :class="[`mrr-table-actions__menu-item--${action.tone || 'default'}`]"
          >
            <FaIcon :name="action.icon" class="mrr-table-actions__menu-icon" />
            <span>{{ action.label }}</span>
            <FaIcon v-if="action.loading" name="i-ri:loader-4-line" class="mrr-table-actions__menu-loading" />
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<style scoped>
.mrr-table-actions {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  justify-content: center;
  min-width: 0;
  white-space: nowrap;
}

.mrr-table-actions__button-wrap {
  display: inline-flex;
}

.mrr-table-actions__button {
  width: 28px;
  height: 28px;
  padding: 0;
  margin: 0;
  font-size: 16px;
  color: var(--mrr-muted-foreground);
  background: color-mix(in srgb, var(--mrr-muted) 68%, transparent);
  border-radius: 8px;
}

.mrr-table-actions__button:hover,
.mrr-table-actions__button:focus-visible {
  color: var(--mrr-foreground);
  background: var(--mrr-muted);
}

.mrr-table-actions__button--primary {
  color: var(--mrr-primary);
  background: color-mix(in srgb, var(--mrr-primary) 9%, var(--mrr-card));
}

.mrr-table-actions__button--success {
  color: var(--el-color-success);
  background: color-mix(in srgb, var(--el-color-success) 10%, var(--mrr-card));
}

.mrr-table-actions__button--warning {
  color: var(--el-color-warning-dark-2);
  background: color-mix(in srgb, var(--el-color-warning) 12%, var(--mrr-card));
}

.mrr-table-actions__button--danger {
  color: var(--mrr-destructive);
  background: color-mix(in srgb, var(--mrr-destructive) 10%, var(--mrr-card));
}

.mrr-table-actions__button--more {
  color: var(--mrr-muted-foreground);
  background: transparent;
}

.mrr-table-actions__button--more:hover,
.mrr-table-actions__button--more:focus-visible {
  background: var(--mrr-muted);
}

.mrr-table-actions__button:focus-visible {
  outline: 2px solid color-mix(in srgb, var(--mrr-primary) 55%, transparent);
  outline-offset: 2px;
}

.mrr-table-actions__button.is-disabled {
  opacity: 0.48;
}

:global(.mrr-table-actions__menu) {
  min-width: 168px;
  padding: 8px;
}

:global(.mrr-table-actions__menu .el-dropdown-menu__item) {
  gap: 10px;
  min-height: 40px;
  padding: 8px 12px;
  border-radius: 8px;
}

:global(.mrr-table-actions__menu-item--danger) {
  color: var(--mrr-destructive) !important;
}

:global(.mrr-table-actions__menu-item--warning) {
  color: var(--el-color-warning-dark-2) !important;
}

:global(.mrr-table-actions__menu-item--success) {
  color: var(--el-color-success) !important;
}

.mrr-table-actions__menu-icon {
  flex: 0 0 auto;
  font-size: 17px;
}

.mrr-table-actions__menu-loading {
  margin-left: auto;
  animation: mrr-table-actions-spin 1s linear infinite;
}

@keyframes mrr-table-actions-spin {
  to { transform: rotate(360deg); }
}
</style>

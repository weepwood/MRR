<script setup lang="ts">
interface FieldDefinition {
  name: string
  description: string
}

withDefaults(defineProps<{
  fields: FieldDefinition[]
  title?: string
}>(), {
  title: '字段说明',
})
</script>

<template>
  <details class="field-dictionary">
    <summary class="field-dictionary__summary">
      <FaIcon class="field-dictionary__chevron" name="i-ri:arrow-right-s-line" />
      <span class="field-dictionary__label">{{ title }}</span>
      <span class="field-dictionary__count">{{ fields.length }} 项</span>
    </summary>
    <dl class="field-dictionary__list">
      <div v-for="field in fields" :key="field.name" class="field-dictionary__item">
        <dt>
          <code>{{ field.name }}</code>
        </dt>
        <dd>{{ field.description }}</dd>
      </div>
    </dl>
  </details>
</template>

<style scoped>
/* 默认收起：字段说明属于低频查阅内容，不占用首屏。 */
.field-dictionary {
  min-width: 0;
  border-top: 1px solid var(--mrr-shell-divider);
}

.field-dictionary__summary {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 9px 2px;
  font-size: 12px;
  color: var(--mrr-muted-foreground);
  cursor: pointer;
  list-style: none;
}

.field-dictionary__summary::-webkit-details-marker {
  display: none;
}

.field-dictionary__summary:hover .field-dictionary__label {
  color: var(--mrr-primary);
}

.field-dictionary__summary:focus-visible {
  outline: 2px solid var(--mrr-primary);
  outline-offset: 2px;
  border-radius: var(--mrr-radius-sm);
}

.field-dictionary__chevron {
  flex: 0 0 auto;
  font-size: 14px;
  transition: transform 150ms ease;
}

.field-dictionary[open] .field-dictionary__chevron {
  transform: rotate(90deg);
}

.field-dictionary__label {
  font-weight: 600;
  color: var(--mrr-foreground);
}

.field-dictionary__count {
  padding: 1px 7px;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  background: var(--mrr-muted);
  border-radius: var(--mrr-radius-pill);
}

.field-dictionary__list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px 20px;
  padding: 2px 2px 12px;
  margin: 0;
}

.field-dictionary__item {
  display: flex;
  gap: 8px;
  align-items: baseline;
  min-width: 0;
}

.field-dictionary__item dt {
  flex: 0 0 132px;
  min-width: 0;
}

.field-dictionary__item code {
  font-size: 11px;
  font-weight: 600;
  color: var(--mrr-primary);
  overflow-wrap: anywhere;
}

.field-dictionary__item dd {
  min-width: 0;
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--mrr-muted-foreground);
}

@media (max-width: 720px) {
  .field-dictionary__list {
    grid-template-columns: minmax(0, 1fr);
  }

  .field-dictionary__item dt {
    flex-basis: 108px;
  }
}
</style>

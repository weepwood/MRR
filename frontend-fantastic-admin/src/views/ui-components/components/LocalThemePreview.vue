<script setup lang="ts">
import type { ThemePaletteScheme } from '@/utils/theme-palette'
import { computed, ref } from 'vue'
import { buildThemeColorScale, toCssVariables } from '@/utils/theme-color'
import { GR8R_COLORS, gr8rCssVariables } from '@/utils/theme-palette'

const scheme = ref<'follow' | ThemePaletteScheme>('follow')
const enabled = ref(true)
const checked = ref(false)
const text = ref('示例内容')
const clicks = ref(0)
const previewStyle = computed(() => {
  if (scheme.value === 'follow') { return undefined }
  const c = GR8R_COLORS[scheme.value]
  const variables = gr8rCssVariables(scheme.value)
  return {
    ...variables,
    ...toCssVariables(buildThemeColorScale(c.accent, scheme.value)),
    '--mrr-background': c.canvas,
    '--mrr-card': c.surface,
    '--mrr-card-foreground': c.ink,
    '--mrr-foreground': c.ink,
    '--mrr-muted-foreground': c.secondaryInk,
    '--mrr-muted': c.mutedSurface,
    '--mrr-accent': c.raisedSurface,
    '--mrr-accent-foreground': c.ink,
    '--mrr-border': c.line,
    '--mrr-primary': c.accent,
    '--mrr-tone-primary': c.accent,
    '--mrr-tone-primary-ink': c.accent,
    '--el-bg-color': c.surface,
    '--el-fill-color-blank': c.surface,
    '--el-text-color-primary': c.ink,
    '--el-text-color-regular': c.ink,
    '--el-text-color-secondary': c.secondaryInk,
    '--el-border-color': c.line,
    '--el-disabled-bg-color': c.mutedSurface,
    '--el-disabled-text-color': c.secondaryInk,
  }
})
</script>

<template>
  <section class="local-theme-preview" aria-label="局部主题与尺寸对照">
    <div class="local-theme-preview__heading">
      <h3>局部主题与尺寸对照</h3>
      <el-radio-group v-model="scheme" aria-label="局部预览主题">
        <el-radio-button value="follow">
          跟随全站
        </el-radio-button>
        <el-radio-button value="light">
          暖色亮
        </el-radio-button>
        <el-radio-button value="dark">
          暖色暗
        </el-radio-button>
      </el-radio-group>
    </div>
    <div class="local-theme-preview__surface" :class="{ 'mrr-local-warm-preview': scheme !== 'follow' }" :style="previewStyle" :data-preview-scheme="scheme">
      <div class="example-row local-theme-preview__sizes">
        <el-button v-for="size in (['small', 'default', 'large'] as const)" :key="size" :size="size" type="primary" @click="clicks++">
          {{ { small: '紧凑', default: '标准', large: '宽松' }[size] }}
        </el-button>
        <el-button disabled>
          禁用
        </el-button>
      </div>
      <div class="example-row">
        <el-input v-model="text" aria-label="局部预览输入" class="local-theme-preview__input" />
        <el-switch v-model="enabled" aria-label="局部预览开关" />
        <el-checkbox v-model="checked">
          局部选择
        </el-checkbox>
      </div>
      <p class="local-theme-preview__hint" role="status">
        本地示例操作 {{ clicks }} 次；不修改全站外观。浮层继续跟随全站。
      </p>
    </div>
  </section>
</template>

<style scoped>
.local-theme-preview {
  display: grid;
  gap: var(--mrr-space-3);
  padding-top: var(--mrr-space-4);
  border-top: 1px solid var(--mrr-border);
}

.local-theme-preview__heading {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
  justify-content: space-between;
}

.local-theme-preview__heading h3 {
  margin: 0;
  font-size: var(--mrr-font-size-md);
  font-weight: var(--mrr-font-weight-heading);
}

.local-theme-preview__surface {
  display: grid;
  gap: var(--mrr-space-3);
  min-width: 0;
  padding: var(--mrr-space-4);
  color: var(--mrr-foreground);
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-md);
}

.local-theme-preview__sizes :deep(.el-button) {
  margin: 0;
}

.local-theme-preview__input {
  width: min(100%, 240px);
}

.local-theme-preview__hint {
  margin: 0;
  font-size: var(--mrr-font-size-xs);
  color: var(--mrr-muted-foreground);
}
</style>

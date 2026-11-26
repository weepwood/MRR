<script setup lang="ts">
import type { ThemePaletteScheme } from '@/utils/theme-palette'
import { computed, ref } from 'vue'
import { buildThemeColorScale, DEFAULT_THEME_COLOR, toCssVariables } from '@/utils/theme-color'
import { GR8R_COLORS, gr8rCssVariables } from '@/utils/theme-palette'
import { darkTheme, lightTheme } from '../../../../themes'

const scheme = ref<'follow' | ThemePaletteScheme | 'classic-light' | 'classic-dark'>('follow')
const enabled = ref(true)
const checked = ref(false)
const text = ref('示例内容')
const clicks = ref(0)
const validation = ref('')
const matrixState = ref<'normal' | 'disabled' | 'loading'>('normal')
const half = ref(true)
const mixed = ref(false)
const previewCount = ref(3)
const previewSource = ref('local')
const previewStyle = computed(() => {
  if (scheme.value === 'follow') { return undefined }
  if (scheme.value === 'classic-light' || scheme.value === 'classic-dark') {
    const dark = scheme.value === 'classic-dark'
    const theme = dark ? darkTheme : lightTheme
    const color = toCssVariables(buildThemeColorScale(DEFAULT_THEME_COLOR, dark ? 'dark' : 'light'))
    return {
      ...theme,
      ...color,
      '--mrr-background': 'hsl(var(--background))',
      '--mrr-card': 'hsl(var(--card))',
      '--mrr-card-foreground': 'hsl(var(--card-foreground))',
      '--mrr-foreground': 'hsl(var(--foreground))',
      '--mrr-muted-foreground': 'hsl(var(--muted-foreground))',
      '--mrr-muted': 'hsl(var(--muted))',
      '--mrr-accent': 'hsl(var(--accent))',
      '--mrr-accent-foreground': 'hsl(var(--accent-foreground))',
      '--mrr-border': 'hsl(var(--border))',
      '--mrr-border-strong': 'hsl(var(--border))',
      '--mrr-primary': color['--color-primary'],
      '--mrr-primary-foreground': 'hsl(var(--primary-foreground))',
      '--mrr-control-bg': 'hsl(var(--background))',
      '--mrr-control-bg-hover': 'hsl(var(--muted))',
      '--mrr-control-bg-disabled': 'hsl(var(--muted))',
      '--mrr-control-border': 'hsl(var(--input))',
      '--mrr-control-border-hover': 'hsl(var(--muted-foreground))',
      '--mrr-field-focus-bg': 'hsl(var(--card))',
      '--mrr-ring': color['--color-primary'],
      '--mrr-destructive': 'hsl(var(--destructive))',
      '--el-bg-color': 'hsl(var(--card))',
      '--el-bg-color-overlay': 'hsl(var(--popover))',
      '--mrr-popover': 'hsl(var(--popover))',
      '--mrr-popover-foreground': 'hsl(var(--popover-foreground))',
      '--mrr-tone-primary-soft': color['--el-color-primary-light-9'],
      '--mrr-tone-primary-ink': dark ? 'hsl(var(--foreground))' : color['--color-primary'],
      '--el-fill-color-blank': 'hsl(var(--card))',
      '--el-text-color-primary': 'hsl(var(--foreground))',
      '--el-text-color-regular': 'hsl(var(--foreground))',
      '--el-text-color-secondary': 'hsl(var(--muted-foreground))',
      '--el-border-color': 'hsl(var(--border))',
      '--el-disabled-bg-color': 'hsl(var(--muted))',
      '--el-disabled-text-color': 'hsl(var(--muted-foreground))',
    }
  }
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
    '--el-bg-color-overlay': c.surface,
    '--mrr-popover': c.surface,
    '--mrr-popover-foreground': c.ink,
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
        <el-radio-button value="classic-light">
          经典亮
        </el-radio-button>
        <el-radio-button value="classic-dark">
          经典暗
        </el-radio-button>
        <el-radio-button value="light">
          暖色亮
        </el-radio-button>
        <el-radio-button value="dark">
          暖色暗
        </el-radio-button>
      </el-radio-group>
    </div>
    <div class="local-theme-preview__surface" :class="{ 'mrr-local-warm-preview': scheme === 'light' || scheme === 'dark' }" :style="previewStyle" :data-preview-scheme="scheme">
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
      <div class="local-theme-preview__matrix" aria-label="基础控件状态矩阵">
        <div class="example-row">
          <el-radio-group v-model="matrixState" aria-label="局部操作状态">
            <el-radio-button value="normal">
              默认
            </el-radio-button>
            <el-radio-button value="disabled">
              禁用
            </el-radio-button>
            <el-radio-button value="loading">
              加载
            </el-radio-button>
          </el-radio-group>
          <el-button type="primary" :loading="matrixState === 'loading'" :disabled="matrixState === 'disabled'" @click="clicks++">
            执行示例
          </el-button>
        </div>
        <el-form label-position="top" class="mrr-edit-form mrr-edit-form--paired">
          <el-form-item label="只读示例">
            <el-input model-value="不可修改" readonly />
          </el-form-item>
          <el-form-item label="校验示例" :error="validation.trim() ? '' : '请输入示例值'">
            <el-input v-model="validation" aria-label="局部校验输入" />
          </el-form-item>
        </el-form>
        <el-form label-position="top" class="mrr-edit-form mrr-edit-form--paired">
          <el-form-item label="数字输入">
            <el-input-number v-model="previewCount" :min="0" :max="10" aria-label="局部数字输入" />
          </el-form-item>
          <el-form-item label="选择器">
            <el-select v-model="previewSource" aria-label="局部来源" :teleported="false">
              <el-option label="本地" value="local" /><el-option label="归档" value="archive" />
            </el-select>
          </el-form-item>
          <el-form-item label="禁用输入">
            <el-input model-value="禁用状态" disabled />
          </el-form-item>
        </el-form>
        <div class="example-row">
          <el-checkbox v-model="mixed" :indeterminate="half" @change="half = false">
            半选示例
          </el-checkbox>
          <el-switch :model-value="true" disabled aria-label="局部禁用开关" />
        </div>
      </div>
      <p class="local-theme-preview__hint" role="status">
        本地示例操作 {{ clicks }} 次；不修改全站外观。经典预览使用默认主色与正式配色，尺寸跟随宿主；示例内联下拉随局部配色，全局浮层继续跟随全站。
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

.local-theme-preview__surface :deep(.el-radio-button) {
  --el-radio-button-checked-text-color: var(--mrr-primary-foreground);
}

.local-theme-preview__matrix {
  display: grid;
  gap: var(--mrr-space-3);
  padding-top: var(--mrr-space-3);
  border-top: 1px solid var(--mrr-border);
}

.local-theme-preview__heading :deep(.el-radio-group) {
  flex-wrap: wrap;
}

.local-theme-preview__hint {
  margin: 0;
  font-size: var(--mrr-font-size-xs);
  color: var(--mrr-muted-foreground);
}
</style>

<script setup lang="ts">
import type { ExampleId } from '../catalog'
import { computed, ref, useId } from 'vue'
import MrrSegmentedTabs from '@/components/MrrSegmentedTabs/index.vue'
import LocalThemePreview from './LocalThemePreview.vue'

defineProps<{ example: ExampleId }>()

const id = useId()
const buttonState = ref('normal')
const clicks = ref(0)
const activeTab = ref<string | number>(`${id}-preview`)
const name = ref('组件演示')
const nativeName = ref('组件演示')
const validationName = ref('')
const validationError = computed(() => validationName.value.trim() ? '' : '请输入示例名称')
const note = ref('这里是可编辑的示例说明。')
const nativeNote = ref('这里是可编辑的示例说明。')
const source = ref('local')
const nativeSource = ref('local')
const version = ref<number>()
const date = ref('2026-11-12')
const count = ref(3)
const checked = ref<boolean | 'indeterminate'>(true)
const enabled = ref(true)
const slider = ref(40)
const matrixSwitch = ref(false)
const matrixCheck = ref(false)
const matrixOn = ref(true)
const matrixMixed = ref(false)
const matrixIndeterminate = ref(true)
const options = [{ label: '本地示例', value: 'local' }, { label: '归档示例', value: 'archive' }]
</script>

<template>
  <div v-if="example === 'buttons'" class="example-stack">
    <el-radio-group v-model="buttonState" aria-label="按钮示例状态">
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
    <div class="example-row">
      <el-button class="mrr-button-flow" type="primary" :auto-insert-space="false" data-testid="role-primary" :disabled="buttonState === 'disabled'" :loading="buttonState === 'loading'" :aria-busy="(buttonState === 'loading') || undefined" @click="clicks++">
        <template #loading>
          <FaIcon name="i-lucide:loader-circle" class="animate-spin" aria-hidden="true" />
        </template>
        主要操作
      </el-button>
      <el-button class="mrr-button-flow" :auto-insert-space="false" data-testid="role-outline" :disabled="buttonState === 'disabled'" @click="clicks++">
        次要操作
      </el-button>
      <el-button class="mrr-button-flow mrr-button-secondary" text :auto-insert-space="false" :disabled="buttonState === 'disabled'" @click="clicks++">
        辅助操作
      </el-button>
      <el-button class="mrr-button-flow" text :auto-insert-space="false" data-testid="role-ghost" :disabled="buttonState === 'disabled'" @click="clicks++">
        轻量操作
      </el-button>
      <el-button class="mrr-button-flow" type="danger" :auto-insert-space="false" data-testid="role-destructive" :disabled="buttonState === 'disabled'" @click="clicks++">
        危险操作示例
      </el-button>
      <el-button class="mrr-button-flow" link type="primary" :auto-insert-space="false" data-testid="role-link" :disabled="buttonState === 'disabled'" @click="clicks++">
        文字操作
      </el-button>
    </div>
    <div class="example-row" data-testid="el-button-parity">
      <el-button data-testid="el-primary" type="primary" :disabled="buttonState === 'disabled'" :loading="buttonState === 'loading'" @click="clicks++">
        主要操作
      </el-button>
      <el-button data-testid="el-outline" :disabled="buttonState === 'disabled'" @click="clicks++">
        次要操作
      </el-button>
      <el-button data-testid="el-ghost" text :disabled="buttonState === 'disabled'" @click="clicks++">
        轻量操作
      </el-button>
      <el-button data-testid="el-destructive" type="danger" :disabled="buttonState === 'disabled'" @click="clicks++">
        危险操作示例
      </el-button>
      <el-button data-testid="el-link" link type="primary" :disabled="buttonState === 'disabled'" @click="clicks++">
        文字操作
      </el-button>
    </div>
    <p class="example-note">
      所有按钮均使用 Element Plus；主次、危险、辅助、文字操作共享尺寸与状态令牌。
      图标按钮可选择随控件圆角的方形或正圆入口，同一工具栏保持一种形态。
    </p>
    <div class="example-row">
      <el-button class="mrr-button-flow" size="small" :auto-insert-space="false" data-testid="role-sm" @click="clicks++">
        紧凑按钮
      </el-button>
      <el-button class="mrr-button-flow" size="large" :auto-insert-space="false" data-testid="role-lg" @click="clicks++">
        宽按钮
      </el-button>
      <el-button class="mrr-button-flow mrr-button-square" :auto-insert-space="false" data-testid="role-icon" aria-label="图标按钮示例" @click="clicks++">
        <FaIcon name="i-lucide:plus" aria-hidden="true" />
      </el-button>
      <el-button-group class="mrr-joined-buttons">
        <el-button class="mrr-button-flow" :auto-insert-space="false" @click="clicks++">
          上一项
        </el-button>
        <el-button class="mrr-button-flow" :auto-insert-space="false" @click="clicks++">
          下一项
        </el-button>
      </el-button-group>
    </div>
    <div class="example-row" data-testid="el-size-parity">
      <el-button size="small" data-testid="el-sm" @click="clicks++">
        紧凑按钮
      </el-button>
      <el-button size="large" data-testid="el-lg" @click="clicks++">
        宽按钮
      </el-button>
      <el-button circle data-testid="el-icon" aria-label="图标按钮示例" @click="clicks++">
        <FaIcon name="i-lucide:plus" aria-hidden="true" />
      </el-button>
    </div>
    <p class="example-note" role="status">
      已触发 {{ clicks }} 次示例操作
    </p>
    <LocalThemePreview />
  </div>

  <div v-else-if="example === 'navigation'" class="example-stack">
    <MrrSegmentedTabs v-model="activeTab" label="页签示例">
      <el-tab-pane :name="`${id}-preview`" label="组件预览" lazy>
        <p class="example-note">
          正在展示页签的预览内容，可以使用方向键切换页签。
        </p>
      </el-tab-pane>
      <el-tab-pane :name="`${id}-notes`" label="交互说明" lazy>
        <p class="example-note">
          页签内容随选择切换；提示可以通过悬停或键盘聚焦查看。
        </p>
      </el-tab-pane>
    </MrrSegmentedTabs>
    <div class="example-row">
      <MrrTooltip content="这是组件说明提示，支持键盘聚焦。">
        <el-button class="mrr-button-flow" :auto-insert-space="false">
          查看提示
        </el-button>
      </MrrTooltip>
      <span class="example-note">快捷键样式 <FaKbd>Ctrl</FaKbd> + <FaKbd>K</FaKbd></span>
    </div>
  </div>

  <div v-else-if="example === 'fields'" class="example-field-grid">
    <div class="example-field">
      <label :for="`${id}-name`">示例名称</label>
      <el-input :id="`${id}-name`" v-model="name" placeholder="输入示例名称" class="example-full-width" />
    </div>
    <div class="example-field">
      <label :for="`${id}-native-name`">原生名称</label>
      <el-input :id="`${id}-native-name`" v-model="nativeName" placeholder="输入示例名称" class="example-full-width" />
    </div>
    <div class="example-field">
      <label :for="`${id}-source`">示例来源</label>
      <el-select :id="`${id}-source`" v-model="source" class="example-full-width mrr-single-select">
        <el-option v-for="option in options" :key="option.value" :label="option.label" :value="option.value" />
      </el-select>
    </div>
    <div class="example-field">
      <label :for="`${id}-native-source`">原生来源</label>
      <el-select :id="`${id}-native-source`" v-model="nativeSource" class="example-full-width">
        <el-option v-for="option in options" :key="option.value" :label="option.label" :value="option.value" />
      </el-select>
    </div>
    <div class="example-field">
      <label :for="`${id}-version`">版本比较基准</label>
      <el-select :id="`${id}-version`" v-model="version" class="example-full-width mrr-single-select" placeholder="选择基准版本">
        <el-option :value="1" label="#1 · 合成历史版本" />
        <el-option :value="2" label="#2 · 合成历史版本" />
      </el-select>
    </div>
    <div class="example-field">
      <label :for="`${id}-date`">示例日期</label>
      <el-date-picker :id="`${id}-date`" v-model="date" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" class="example-full-width" />
    </div>
    <div class="example-field">
      <label :for="`${id}-count`">示例数量</label>
      <el-input-number :id="`${id}-count`" v-model="count" :min="1" :max="20" controls-position="right" class="example-full-width" />
    </div>
    <div class="example-field">
      <label :for="`${id}-disabled`">禁用输入</label>
      <el-input :id="`${id}-disabled`" model-value="当前不可编辑" disabled class="example-full-width" />
    </div>
    <div class="example-field">
      <label :for="`${id}-native-disabled`">原生禁用输入</label>
      <el-input :id="`${id}-native-disabled`" model-value="当前不可编辑" disabled class="example-full-width" />
    </div>
    <div class="example-field">
      <label :for="`${id}-readonly`">只读名称</label>
      <el-input :id="`${id}-readonly`" model-value="可以选择复制" readonly class="example-full-width mrr-readonly-field" />
    </div>
    <div class="example-field">
      <label :for="`${id}-native-readonly`">原生只读名称</label>
      <el-input :id="`${id}-native-readonly`" model-value="可以选择复制" readonly class="example-full-width mrr-readonly-field" />
    </div>
    <div class="example-field">
      <el-form label-position="top">
        <el-form-item label="校验示例" :for="`${id}-validation`" :error="validationError">
          <el-input :id="`${id}-validation`" v-model="validationName" :aria-invalid="!!validationError" :aria-describedby="validationError ? `${id}-validation-error` : undefined" />
          <template #error="{ error }">
            <span :id="`${id}-validation-error`" class="el-form-item__error">{{ error }}</span>
          </template>
        </el-form-item>
      </el-form>
    </div>
    <div class="example-field">
      <label :for="`${id}-note`">示例说明</label>
      <el-input :id="`${id}-note`" v-model="note" type="textarea" class="mrr-textarea-rows" :rows="2" />
    </div>
    <div class="example-field">
      <label :for="`${id}-native-note`">原生说明</label>
      <el-input :id="`${id}-native-note`" v-model="nativeNote" type="textarea" class="mrr-textarea-rows" :rows="2" />
    </div>
    <div class="example-field">
      <label :for="`${id}-fa-validation`">独立校验示例</label>
      <el-input :id="`${id}-fa-validation`" v-model="validationName" :class="{ 'account-field-invalid': !!validationError }" :aria-invalid="!!validationError" :aria-describedby="validationError ? `${id}-fa-validation-error` : undefined" class="example-full-width" />
      <span v-if="validationError" :id="`${id}-fa-validation-error`" class="example-field-error">{{ validationError }}</span>
    </div>
  </div>

  <div v-else class="example-stack">
    <div class="example-row">
      <el-checkbox v-model="checked">
        选中此示例
      </el-checkbox>
      <el-checkbox :model-value="false" disabled>
        禁用复选框
      </el-checkbox>
    </div>
    <div class="example-row">
      <el-switch :id="`${id}-switch`" v-model="enabled" />
      <label :for="`${id}-switch`">启用示例</label>
      <el-switch :model-value="false" disabled aria-label="禁用开关" />
    </div>
    <div class="example-field">
      <span :id="`${id}-slider-label`">示例进度 {{ slider }}%</span>
      <el-slider v-model="slider" :aria-label="`示例进度 ${slider}%`" :step="5" class="example-slider mrr-compact-slider" />
    </div>
    <div class="selection-matrix" aria-label="选择控件状态矩阵">
      <div class="selection-matrix__cell">
        <span>交互 · 初始关闭</span>
        <el-switch v-model="matrixSwitch" aria-label="矩阵开关" />
        <el-checkbox v-model="matrixCheck" aria-label="矩阵复选框" />
      </div>
      <div class="selection-matrix__cell">
        <span>交互 · 初始半选 / 开启</span>
        <el-switch v-model="matrixOn" aria-label="矩阵开启开关" />
        <el-checkbox v-model="matrixMixed" :indeterminate="matrixIndeterminate" aria-label="矩阵半选复选框" @change="matrixIndeterminate = false" />
      </div>
      <div class="selection-matrix__cell">
        <span>禁用且选中</span>
        <el-switch :model-value="true" disabled aria-label="矩阵禁用开关" />
        <el-checkbox :model-value="true" disabled aria-label="矩阵禁用复选框" />
      </div>
    </div>
    <p class="example-note" role="status">
      复选框{{ checked ? '已选中' : '未选中' }}，开关{{ enabled ? '已启用' : '已关闭' }}。
    </p>
  </div>
</template>

<style scoped>
.selection-matrix {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 120px), 1fr));
  gap: var(--mrr-space-2);
}

.selection-matrix__cell {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
  min-width: 0;
  padding: var(--mrr-space-3);
  background: var(--mrr-section-heading-bg);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-md);
}

.selection-matrix__cell > span {
  flex: 0 0 100%;
  font-size: var(--mrr-font-size-xs);
  color: var(--mrr-muted-foreground);
}
</style>

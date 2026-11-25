<script setup lang="ts">
import type { ExampleId } from '../catalog'
import { computed, ref, useId } from 'vue'
import { handleSegmentedTabKey } from '@/utils/segmented-tabs'

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
const date = ref('2026-11-12')
const count = ref(3)
const checked = ref<boolean | 'indeterminate'>(true)
const enabled = ref(true)
const slider = ref(40)
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
      <FaButton data-testid="fa-primary" :disabled="buttonState === 'disabled'" :loading="buttonState === 'loading'" @click="clicks++">
        主要操作
      </FaButton>
      <FaButton data-testid="fa-outline" variant="outline" :disabled="buttonState === 'disabled'" @click="clicks++">
        次要操作
      </FaButton>
      <FaButton variant="secondary" :disabled="buttonState === 'disabled'" @click="clicks++">
        辅助操作
      </FaButton>
      <FaButton data-testid="fa-ghost" variant="ghost" :disabled="buttonState === 'disabled'" @click="clicks++">
        轻量操作
      </FaButton>
      <FaButton data-testid="fa-destructive" variant="destructive" :disabled="buttonState === 'disabled'" @click="clicks++">
        危险操作示例
      </FaButton>
      <FaButton data-testid="fa-link" variant="link" :disabled="buttonState === 'disabled'" @click="clicks++">
        文字操作
      </FaButton>
    </div>
    <!--
      与上一排逐一对位：同一角色在 Element Plus 与界面封装里应当是同一个控件的样子。
      「辅助操作」是界面封装独有的中性实心底，Element Plus 没有等价变体，因此不参与对照。
    -->
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
      上排为界面封装（FaButton），下排为 Element Plus 原生按钮：两族共用同一组尺寸与状态令牌。
      图标按钮是唯一保留差异的形态——Element Plus 用 <code>circle</code>（正圆），界面封装用 <code>size="icon"</code>（随控件圆角），两者同为控件高度见方。
    </p>
    <div class="example-row">
      <FaButton size="sm" variant="outline" data-testid="fa-sm" @click="clicks++">
        紧凑按钮
      </FaButton>
      <FaButton size="lg" variant="outline" data-testid="fa-lg" @click="clicks++">
        宽按钮
      </FaButton>
      <FaButton size="icon" variant="outline" data-testid="fa-icon" aria-label="图标按钮示例" @click="clicks++">
        <FaIcon name="i-lucide:plus" aria-hidden="true" />
      </FaButton>
      <FaButtonGroup>
        <FaButton variant="outline" @click="clicks++">
          上一项
        </FaButton>
        <FaButton variant="outline" @click="clicks++">
          下一项
        </FaButton>
      </FaButtonGroup>
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
  </div>

  <div v-else-if="example === 'navigation'" class="example-stack">
    <el-tabs v-model="activeTab" class="mrr-segmented-tabs" @keydown="handleSegmentedTabKey">
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
    </el-tabs>
    <div class="example-row">
      <FaTooltip text="这是组件说明提示，支持键盘聚焦。">
        <FaButton variant="outline">
          查看提示
        </FaButton>
      </FaTooltip>
      <span class="example-note">快捷键样式 <FaKbd>Ctrl</FaKbd> + <FaKbd>K</FaKbd></span>
    </div>
  </div>

  <div v-else-if="example === 'fields'" class="example-field-grid">
    <div class="example-field">
      <label :for="`${id}-name`">示例名称</label>
      <FaInput :id="`${id}-name`" v-model="name" placeholder="输入示例名称" class="example-full-width" />
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
      <label :for="`${id}-date`">示例日期</label>
      <el-date-picker :id="`${id}-date`" v-model="date" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" class="example-full-width" />
    </div>
    <div class="example-field">
      <label :for="`${id}-count`">示例数量</label>
      <el-input-number :id="`${id}-count`" v-model="count" :min="1" :max="20" controls-position="right" class="example-full-width" />
    </div>
    <div class="example-field">
      <label :for="`${id}-disabled`">禁用输入</label>
      <FaInput :id="`${id}-disabled`" model-value="当前不可编辑" disabled class="example-full-width" />
    </div>
    <div class="example-field">
      <label :for="`${id}-native-disabled`">原生禁用输入</label>
      <el-input :id="`${id}-native-disabled`" model-value="当前不可编辑" disabled class="example-full-width" />
    </div>
    <div class="example-field">
      <label :for="`${id}-readonly`">只读名称</label>
      <FaInput :id="`${id}-readonly`" model-value="可以选择复制" readonly class="example-full-width" />
    </div>
    <div class="example-field">
      <label :for="`${id}-native-readonly`">原生只读名称</label>
      <el-input :id="`${id}-native-readonly`" model-value="可以选择复制" readonly class="example-full-width" />
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
      <FaTextarea :id="`${id}-note`" v-model="note" :rows="2" />
    </div>
    <div class="example-field">
      <label :for="`${id}-native-note`">原生说明</label>
      <el-input :id="`${id}-native-note`" v-model="nativeNote" type="textarea" :rows="2" />
    </div>
    <div class="example-field">
      <label :for="`${id}-fa-validation`">封装校验示例</label>
      <FaInput :id="`${id}-fa-validation`" v-model="validationName" :aria-invalid="!!validationError" :aria-describedby="validationError ? `${id}-fa-validation-error` : undefined" class="example-full-width" />
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
    <p class="example-note" role="status">
      复选框{{ checked ? '已选中' : '未选中' }}，开关{{ enabled ? '已启用' : '已关闭' }}。
    </p>
  </div>
</template>

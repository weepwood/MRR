<script setup lang="ts">
import type { ExampleId } from '../catalog'
import { ref, useId } from 'vue'

defineProps<{ example: ExampleId }>()

const id = useId()
const buttonState = ref('normal')
const clicks = ref(0)
const activeTab = ref<string | number>('preview')
const name = ref('组件演示')
const note = ref('这里是可编辑的示例说明。')
const source = ref('local')
const date = ref('2026-11-12')
const count = ref(3)
const checked = ref<boolean | 'indeterminate'>(true)
const enabled = ref(true)
const slider = ref([40])
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
    <FaTabs v-model="activeTab" :list="[{ label: '组件预览', value: 'preview' }, { label: '交互说明', value: 'notes' }]">
      <template #preview>
        <p class="example-note">
          正在展示页签的预览内容，可以使用方向键切换页签。
        </p>
      </template>
      <template #notes>
        <p class="example-note">
          页签内容随选择切换；提示可以通过悬停或键盘聚焦查看。
        </p>
      </template>
    </FaTabs>
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
      <label :for="`${id}-source`">示例来源</label>
      <FaSelect :id="`${id}-source`" v-model="source" :options="options" class="example-full-width" />
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
      <label :for="`${id}-note`">示例说明</label>
      <FaTextarea :id="`${id}-note`" v-model="note" :rows="2" />
    </div>
  </div>

  <div v-else class="example-stack">
    <div class="example-row">
      <FaCheckbox v-model="checked">
        选中此示例
      </FaCheckbox>
      <FaCheckbox :model-value="false" disabled>
        禁用复选框
      </FaCheckbox>
    </div>
    <div class="example-row">
      <FaSwitch :id="`${id}-switch`" v-model="enabled" />
      <label :for="`${id}-switch`">启用示例</label>
      <FaSwitch :model-value="false" disabled aria-label="禁用开关" />
    </div>
    <div class="example-field">
      <span :id="`${id}-slider-label`">示例进度 {{ slider[0] }}%</span>
      <FaSlider v-model="slider" :aria-labelledby="`${id}-slider-label`" :step="5" class="example-slider" />
    </div>
    <p class="example-note" role="status">
      复选框{{ checked ? '已选中' : '未选中' }}，开关{{ enabled ? '已启用' : '已关闭' }}。
    </p>
  </div>
</template>

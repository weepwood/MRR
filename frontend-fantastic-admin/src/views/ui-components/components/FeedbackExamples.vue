<script setup lang="ts">
import type { ExampleId } from '../catalog'
import { nextTick, ref, watch } from 'vue'

defineProps<{ example: ExampleId }>()

const state = ref('loaded')
const modalOpen = ref(false)
const drawerOpen = ref(false)
const message = ref('选择一个浮层查看实际效果。')
const glassBlur = ref(true)
const modalTrigger = ref<{ $el: HTMLButtonElement }>()
const drawerTrigger = ref<{ $el: HTMLButtonElement }>()

// 浮层由 v-model 打开，页面负责把关闭后的焦点交还对应触发按钮。
watch([modalOpen, drawerOpen], async ([modal, drawer], [previousModal, previousDrawer]) => {
  await nextTick()
  const trigger = previousModal && !modal ? modalTrigger.value : previousDrawer && !drawer ? drawerTrigger.value : undefined
  if (trigger?.$el.isConnected) {
    trigger.$el.focus()
  }
}, { flush: 'post' })

function confirm(kind: string) {
  modalOpen.value = false
  drawerOpen.value = false
  message.value = `${kind}示例已确认。`
}
</script>

<template>
  <div v-if="example === 'feedback'" class="example-stack">
    <el-radio-group v-model="state" aria-label="内容示例状态">
      <el-radio-button value="loaded">
        正常
      </el-radio-button>
      <el-radio-button value="loading">
        加载中
      </el-radio-button>
      <el-radio-button value="empty">
        无数据
      </el-radio-button>
      <el-radio-button value="error">
        加载失败
      </el-radio-button>
    </el-radio-group>
    <AppLoading v-if="state === 'loading'" :rows="3" :cols="3" />
    <AppEmpty v-else-if="state === 'empty'" description="还没有演示项目，可以恢复示例内容。" action-text="恢复示例" @action="state = 'loaded'" />
    <AppError v-else-if="state === 'error'" message="演示加载失败，点击重试恢复正常状态。" @retry="state = 'loaded'" />
    <div v-else class="example-stack">
      <el-alert title="示例内容已就绪" description="此提示用于检查成功状态和说明层级。" type="success" show-icon :closable="false" />
      <p class="example-note">
        进度示例 64%
      </p>
      <FaProgress :model-value="64" aria-label="演示任务进度 64%" />
    </div>
  </div>
  <div v-else-if="example === 'overlays'" class="example-stack">
    <div class="example-row">
      <FaButton ref="modalTrigger" variant="outline" @click="modalOpen = true">
        打开对话框
      </FaButton>
      <FaButton ref="drawerTrigger" variant="outline" @click="drawerOpen = true">
        打开抽屉
      </FaButton>
      <FaPopover side="bottom" align="start" :collision-padding="12">
        <FaButton variant="outline">
          打开气泡
        </FaButton>
        <template #panel>
          <p>气泡用于补充轻量说明，点击外部或按 Escape 关闭。</p>
        </template>
      </FaPopover>
    </div>
    <p class="example-note" role="status">
      {{ message }}
    </p>
    <FaModal v-model="modalOpen" title="对话框示例" description="预览标题、内容、操作与焦点。" show-cancel-button open-auto-focus class="gallery-demo-dialog" @confirm="confirm('对话框')">
      <p>这是一个真实的对话框组件。可以确认、取消，或按 Escape 关闭。</p>
    </FaModal>
    <FaDrawer v-model="drawerOpen" title="抽屉示例" description="查看侧边详情与底部操作。" show-cancel-button open-auto-focus content-class="gallery-demo-drawer" @confirm="confirm('抽屉')">
      <p>这是合成的详情内容，用于验收抽屉的阅读和关闭体验。</p>
    </FaDrawer>
  </div>
  <div v-else class="example-stack">
    <p class="example-note">
      毛玻璃（backdrop-filter）与背景模糊已在生产 UI 整体下线：外观设置面板、导航搜索、侧栏菜单、登录页等浮层一律使用不透明表面。
      以下演示仅在本页启用，用于比较效果与将来启用时的验收。
    </p>
    <div class="glass-demo-stage">
      <div class="glass-demo-backdrop" aria-hidden="true">
        <span v-for="index in 5" :key="index" :class="`glass-demo-blob glass-demo-blob--${index}`" />
        <span class="glass-demo-grid" />
      </div>
      <div class="glass-demo-panel" :class="glassBlur ? 'glass-demo-panel--frosted' : 'glass-demo-panel--opaque'">
        <p class="glass-demo-title">
          毛玻璃浮层
        </p>
        <p class="glass-demo-text">
          {{ glassBlur ? 'backdrop-filter: blur(20px) saturate(145%)，半透明底色。' : '不透明表面（生产 UI 当前形态）。' }}
        </p>
      </div>
    </div>
    <div class="example-row">
      <FaSwitch :id="`${example}-glass-switch`" v-model="glassBlur" />
      <label :for="`${example}-glass-switch`">启用毛玻璃（关闭则对比生产 UI 的不透明表面）</label>
    </div>
  </div>
</template>

<style scoped>
/*
 * 毛玻璃演示专区：backdrop-filter 只允许出现在这里，
 * 生产 UI 的浮层表面（glass-surface.css 等）一律不透明。
 */
.glass-demo-stage {
  position: relative;
  min-height: 220px;
  overflow: hidden;
  border-radius: var(--mrr-radius-lg);
}

.glass-demo-backdrop {
  position: absolute;
  inset: 0;
  overflow: hidden;
  background: var(--mrr-card);
}

.glass-demo-blob {
  position: absolute;
  display: block;
  width: 150px;
  height: 150px;
  border-radius: 50%;
  opacity: 0.85;
  filter: blur(6px);
}

.glass-demo-blob--1 { top: -30px; left: -20px; background: #60a5fa; }
.glass-demo-blob--2 { top: 60px; left: 34%; background: #34d399; }
.glass-demo-blob--3 { top: -20px; right: 26%; background: #f472b6; }
.glass-demo-blob--4 { bottom: -40px; left: 14%; background: #fbbf24; }
.glass-demo-blob--5 { right: -30px; bottom: -50px; background: #a78bfa; }

.glass-demo-grid {
  position: absolute;
  inset: 12px;
  background-image:
    linear-gradient(rgb(15 23 42 / 16%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(15 23 42 / 16%) 1px, transparent 1px);
  background-size: 28px 28px;
}

.glass-demo-panel {
  position: relative;
  max-width: 420px;
  padding: var(--mrr-space-5) var(--mrr-space-6);
  margin: var(--mrr-space-6) auto;
  border-radius: var(--mrr-radius-xl);
}

.glass-demo-panel--frosted {
  background: rgb(255 255 255 / 55%);
  border: 1px solid rgb(255 255 255 / 65%);
  box-shadow: var(--mrr-shadow-lg);
  backdrop-filter: blur(20px) saturate(145%);
}

:root.dark .glass-demo-panel--frosted {
  background: rgb(23 30 42 / 55%);
  border-color: rgb(255 255 255 / 14%);
}

.glass-demo-panel--opaque {
  background: var(--mrr-popover);
  border: 1px solid var(--mrr-border);
  box-shadow: var(--mrr-shadow-lg);
}

.glass-demo-title {
  margin: 0 0 var(--mrr-space-1);
  font-size: 15px;
  font-weight: 700;
}

.glass-demo-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
}
</style>

<script setup lang="ts">
import type { ExampleId } from '../catalog'
import { nextTick, ref, watch } from 'vue'

defineProps<{ example: ExampleId }>()

const state = ref('loaded')
const modalOpen = ref(false)
const drawerOpen = ref(false)
const message = ref('选择一个浮层查看实际效果。')
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
  <div v-else class="example-stack">
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
</template>

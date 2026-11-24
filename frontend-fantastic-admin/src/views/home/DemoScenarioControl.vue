<script setup lang="ts">
import type { DemoScenario } from '@/mock-support/scenarios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ref } from 'vue'
import { DEMO_SCENARIOS, pagesScenarioStorage, readScenario, writeScenario } from '@/mock-support/scenarios'

const selected = ref<DemoScenario>(readScenario(pagesScenarioStorage()))
const busy = ref(false)

async function applyScenario(value: DemoScenario) {
  if (busy.value) { return }
  busy.value = true
  try {
    await ElMessageBox.confirm('将重新加载本页的合成数据，当前演示业务修改和任务进度会丢失。不会修改生产数据或其他浏览器设置。', '切换演示场景', {
      confirmButtonText: '确认重新加载',
      cancelButtonText: '取消',
      type: 'warning',
    })
    if (!writeScenario(pagesScenarioStorage(), value)) {
      ElMessage.error('浏览器存储不可用，未切换演示场景')
      return
    }
    window.location.reload()
  }
  catch {
    // The confirmation was dismissed.
  }
  finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="mx-4 mt-4 flex flex-wrap items-center gap-3 rounded-lg border p-3" aria-label="演示场景控制">
    <div class="min-w-0 flex-1">
      <div class="text-sm font-medium">
        演示场景
      </div>
      <p class="mt-1 text-xs opacity-70">
        仅使用合成数据；刷新保留场景，业务修改不跨会话保存。
      </p>
    </div>
    <el-select v-model="selected" aria-label="选择演示场景" class="w-48" :disabled="busy">
      <el-option v-for="item in DEMO_SCENARIOS" :key="item.value" :label="item.label" :value="item.value" />
    </el-select>
    <el-button :loading="busy" @click="applyScenario(selected)">
      应用场景
    </el-button>
    <el-button :disabled="busy" @click="applyScenario('standard')">
      恢复默认
    </el-button>
    <p class="w-full text-xs opacity-70">
      {{ DEMO_SCENARIOS.find(item => item.value === selected)?.description }}
    </p>
  </section>
</template>

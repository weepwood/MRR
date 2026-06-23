<script setup lang="ts">
import { ref } from 'vue'
import SmokeTestPage from './smoke-test.vue'
import PressureTestPage from './pressure-test.vue'
import ApiTestPage from './api-test.vue'
import DataCheckPage from './data-check.vue'

defineOptions({ name: 'TestingPage' })

const activeTab = ref<'smoke' | 'pressure' | 'api' | 'data'>('smoke')
</script>

<template>
  <div class="page-shell">
    <div>
      <p class="eyebrow">Backend Test Lab</p>
      <h2>测试中心</h2>
      <p class="subtitle">
        集中管理冒烟检查、压测任务、接口调试与数据完整性校验。
      </p>
    </div>

    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="冒烟测试" name="smoke">
        <SmokeTestPage />
      </el-tab-pane>
      <el-tab-pane label="压测任务" name="pressure">
        <PressureTestPage />
      </el-tab-pane>
      <el-tab-pane label="接口调试" name="api">
        <ApiTestPage />
      </el-tab-pane>
      <el-tab-pane label="数据检查" name="data">
        <DataCheckPage />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.page-shell {
  display: grid;
  gap: 20px;
}

.eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

h2 {
  margin: 0;
  font-size: 28px;
}

.subtitle {
  margin: 8px 0 0;
  color: #64748b;
}
</style>

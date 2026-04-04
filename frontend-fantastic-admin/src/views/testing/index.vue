<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { reactive, ref } from 'vue'
import { clearPressureTestHistory, getLatestPressureTest, getPressureTestHistory, runPressureTest } from '@/api/modules/monitoring'
import { getSystemHealth, getSystemOverview } from '@/api/modules/system'

defineOptions({ name: 'TestingPage' })

const smokeLoading = ref(false)
const pressureLoading = ref(false)
const smokeResults = ref<any[]>([])
const pressureHistory = ref<any[]>([])

const pressureForm = reactive({
  concurrency: 5,
  requests: 50,
  targetPath: '/v1/system/health',
})

async function runSmokeSuite() {
  smokeLoading.value = true
  try {
    const [health, overview] = await Promise.all([getSystemHealth(), getSystemOverview()])
    smokeResults.value = [
      { name: '系统健康', ok: true, summary: JSON.stringify(health).slice(0, 120) },
      { name: '系统概览', ok: true, summary: JSON.stringify(overview).slice(0, 120) },
    ]
    ElMessage.success('冒烟测试完成')
  }
  catch (error: any) {
    smokeResults.value = [{ name: '冒烟测试', ok: false, summary: error?.message || '执行失败' }]
    ElMessage.error(error?.message || '冒烟测试失败')
  }
  finally {
    smokeLoading.value = false
  }
}

async function loadPressureHistory() {
  try {
    const history = await getPressureTestHistory()
    pressureHistory.value = Array.isArray(history) ? history : []
  }
  catch {
    pressureHistory.value = []
  }
}

async function runPressure() {
  pressureLoading.value = true
  try {
    await runPressureTest({
      concurrency: pressureForm.concurrency,
      requests: pressureForm.requests,
      targetUrl: pressureForm.targetPath,
    })
    await loadPressureHistory()
    ElMessage.success('压测任务已提交')
  }
  catch (error: any) {
    ElMessage.error(error?.message || '压测执行失败')
  }
  finally {
    pressureLoading.value = false
  }
}

async function refreshLatest() {
  try {
    const latest = await getLatestPressureTest()
    if (latest) {
      pressureHistory.value = [latest, ...pressureHistory.value.filter(item => item?.runId !== latest?.runId)]
    }
  }
  catch (error: any) {
    ElMessage.error(error?.message || '获取最近压测失败')
  }
}

async function clearHistory() {
  try {
    await clearPressureTestHistory()
    pressureHistory.value = []
    ElMessage.success('压测历史已清空')
  }
  catch (error: any) {
    ElMessage.error(error?.message || '清空压测历史失败')
  }
}
</script>

<template>
  <div class="page-shell">
    <div>
      <p class="eyebrow">
        Backend Test Lab
      </p>
      <h2>测试中心</h2>
      <p class="subtitle">
        集中管理冒烟检查与压测任务，帮助迁移后后台快速做联调和回归。
      </p>
    </div>

    <el-row :gutter="20">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            冒烟测试
          </template>
          <el-button type="primary" :loading="smokeLoading" @click="runSmokeSuite">
            执行冒烟测试
          </el-button>
          <div class="stack-list" style="margin-top: 16px">
            <article v-for="item in smokeResults" :key="item.name" class="stack-item">
              <div>
                <strong>{{ item.name }}</strong>
                <p>{{ item.summary }}</p>
              </div>
              <el-tag :type="item.ok ? 'success' : 'danger'">
                {{ item.ok ? '通过' : '失败' }}
              </el-tag>
            </article>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            压测任务
          </template>
          <el-form :model="pressureForm" label-width="100px">
            <el-form-item label="并发数">
              <el-input-number v-model="pressureForm.concurrency" :min="1" :max="200" />
            </el-form-item>
            <el-form-item label="请求数">
              <el-input-number v-model="pressureForm.requests" :min="1" :max="5000" />
            </el-form-item>
            <el-form-item label="目标路径">
              <el-input v-model="pressureForm.targetPath" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="pressureLoading" @click="runPressure">
                启动压测
              </el-button>
              <el-button @click="refreshLatest">
                刷新最近结果
              </el-button>
              <el-button type="danger" plain @click="clearHistory">
                清空历史
              </el-button>
            </el-form-item>
          </el-form>

          <div class="stack-list">
            <article v-for="(item, index) in pressureHistory" :key="item?.runId || index" class="stack-item">
              <div>
                <strong>{{ item?.runId || `记录 ${index + 1}` }}</strong>
                <p>{{ item?.message || JSON.stringify(item).slice(0, 140) }}</p>
              </div>
              <el-tag>{{ item?.status || '记录' }}</el-tag>
            </article>
            <el-empty v-if="!pressureHistory.length" description="暂无压测历史" />
          </div>
        </el-card>
      </el-col>
    </el-row>
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
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #64748b;
  font-weight: 700;
}

h2 {
  margin: 0;
  font-size: 28px;
}

.subtitle {
  margin: 8px 0 0;
  color: #64748b;
}

.stack-list {
  display: grid;
  gap: 12px;
}

.stack-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  padding: 14px;
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.03);
}

.stack-item strong {
  display: block;
}

.stack-item p {
  margin: 6px 0 0;
  color: #64748b;
}
</style>

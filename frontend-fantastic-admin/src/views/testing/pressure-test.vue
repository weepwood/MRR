<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { reactive, ref, onMounted } from 'vue'
import { clearPressureTestHistory, getLatestPressureTest, getPressureTestHistory, runPressureTest } from '@/api/modules/monitoring'

defineOptions({ name: 'PressureTestPage' })

const pressureLoading = ref(false)
const pressureHistory = ref<any[]>([])

const pressureForm = reactive({
  concurrency: 5,
  totalRequests: 100,
  targetUrl: '/api/v1/system/health',
})

onMounted(() => {
  loadPressureHistory()
})

async function loadPressureHistory() {
  try {
    const history = await getPressureTestHistory()
    pressureHistory.value = Array.isArray(history) ? history : []
  } catch {
    pressureHistory.value = []
  }
}

async function runPressure() {
  pressureLoading.value = true
  try {
    const targetUrl = pressureForm.targetUrl.startsWith('http')
      ? pressureForm.targetUrl
      : `${window.location.protocol}//${window.location.hostname}:18045${pressureForm.targetUrl}`
    await runPressureTest({
      concurrency: pressureForm.concurrency,
      totalRequests: pressureForm.totalRequests,
      targetUrl,
    })
    await loadPressureHistory()
    ElMessage.success('压测任务已完成')
  } catch (error: any) {
    ElMessage.error(error?.message || '压测执行失败')
  } finally {
    pressureLoading.value = false
  }
}

async function refreshLatest() {
  try {
    const latest = await getLatestPressureTest()
    if (latest && latest.data) {
      const latestData = latest.data
      pressureHistory.value = [latestData, ...pressureHistory.value.filter(item => item?.runId !== latestData?.runId)]
      ElMessage.success('已刷新')
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '获取最近压测失败')
  }
}

async function clearHistory() {
  try {
    await clearPressureTestHistory()
    pressureHistory.value = []
    ElMessage.success('压测历史已清空')
  } catch (error: any) {
    ElMessage.error(error?.message || '清空压测历史失败')
  }
}
</script>

<template>
  <div class="grid gap-5">
    <el-card shadow="never">
      <template #header>
        <span>压测配置</span>
      </template>
      <el-form :model="pressureForm" label-width="90px" class="max-w-160">
        <el-form-item label="目标 URL">
          <el-input v-model="pressureForm.targetUrl" placeholder="/api/v1/system/health 或完整 URL" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="并发数">
              <el-input-number v-model="pressureForm.concurrency" :min="1" :max="200" class="w-full" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="请求数">
              <el-input-number v-model="pressureForm.totalRequests" :min="1" :max="5000" class="w-full" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item>
          <el-button type="primary" :loading="pressureLoading" @click="runPressure">
            {{ pressureLoading ? '压测中...' : '启动压测' }}
          </el-button>
          <el-button @click="refreshLatest">刷新最近结果</el-button>
          <el-button type="danger" plain @click="clearHistory">清空历史</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <span>压测历史</span>
      </template>
      <div class="grid gap-3">
        <article
          v-for="(item, index) in pressureHistory"
          :key="item?.runId || index"
          class="flex items-center justify-between p-4 rd-3 bg-#f8fafc"
        >
          <div>
            <strong class="text-sm">{{ item?.name || item?.targetUrl || `记录 ${index + 1}` }}</strong>
            <p class="mt-1 text-xs color-#64748b">
              成功 {{ item.successCount }}/{{ item.totalRequests }}
              | avg {{ item.avgLatencyMs }}ms | p95 {{ item.p95LatencyMs }}ms
              | {{ item.requestsPerSecond }} req/s
            </p>
          </div>
          <div class="flex items-center gap-2">
            <el-tag :type="(item.successRate ?? 0) >= 95 ? 'success' : 'warning'" size="small">
              {{ item.successRate ?? '-' }}%
            </el-tag>
            <el-tag type="info" size="small">{{ item.durationMillis ? `${(item.durationMillis / 1000).toFixed(1)}s` : '-' }}</el-tag>
          </div>
        </article>
        <el-empty v-if="!pressureHistory.length" description="暂无压测历史" />
      </div>
    </el-card>
  </div>
</template>

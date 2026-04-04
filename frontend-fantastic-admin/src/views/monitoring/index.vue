<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { getSystemHealth, getSystemInfo, getSystemMemory, getSystemOverview, getSystemProperties, getSystemRuntime } from '@/api/modules/system'

defineOptions({ name: 'MonitoringPage' })

const loading = ref(false)
const healthStatus = ref<any>({})
const runtimeInfo = ref<any>({})
const memoryInfo = ref<any>({ heap: {}, nonHeap: {} })
const systemInfo = ref<any>({ application: {}, jvm: {}, operatingSystem: {} })
const properties = ref<Record<string, string>>({})

const healthTone = computed(() => {
  const value = String(healthStatus.value.status || '').toUpperCase()
  if (value === 'UP') { return 'success' }
  if (value === 'WARNING') { return 'warning' }
  return 'danger'
})

const memoryPercent = computed(() => Number.parseFloat(String(memoryInfo.value.usagePercent || '0').replace('%', '')) || 0)
const cpuLoadPercent = computed(() => {
  const processors = Number(systemInfo.value.jvm?.availableProcessors || 0)
  const loadAverage = Number(systemInfo.value.operatingSystem?.systemLoadAverage || 0)
  if (!processors || !Number.isFinite(loadAverage)) { return 0 }
  return Math.max(0, Math.min(100, Math.round((loadAverage / processors) * 100)))
})

const summaryCards = computed(() => [
  { label: '健康状态', value: healthStatus.value.status || 'UNKNOWN', note: '系统健康检查接口返回' },
  { label: '运行时长', value: runtimeInfo.value.uptimeFormatted || '-', note: 'JVM 进程启动至今' },
  { label: '堆内存使用率', value: memoryInfo.value.usagePercent || '-', note: '来自系统内存指标' },
  { label: 'CPU 负载估算', value: `${cpuLoadPercent.value}%`, note: '系统负载 / 可用处理器数' },
])

async function loadAll() {
  loading.value = true
  try {
    const [overviewRes, healthRes, runtimeRes, memoryRes, infoRes, propertiesRes] = await Promise.all([
      getSystemOverview(),
      getSystemHealth(),
      getSystemRuntime(),
      getSystemMemory(),
      getSystemInfo(),
      getSystemProperties(),
    ])

    // 后端返回格式: { code: 200, message: '...', data: {...}, timestamp: '...' }
    const overview = overviewRes.data || {}
    healthStatus.value = healthRes.data || overview.health || {}
    runtimeInfo.value = runtimeRes.data || overview.runtime || {}
    memoryInfo.value = memoryRes.data || overview.memory || {}
    systemInfo.value = infoRes.data || overview.info || { application: {}, jvm: {}, operatingSystem: {} }
    properties.value = propertiesRes.data || overview.properties || {}
  }
  catch (error: any) {
    ElMessage.error(error?.message || '监控信息加载失败')
  }
  finally {
    loading.value = false
  }
}

onMounted(loadAll)
</script>

<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <p class="eyebrow">
          System Monitor
        </p>
        <h2>系统监控</h2>
        <p class="subtitle">
          统一查看运行时、内存、JVM 与系统属性信息，用于排查环境与性能问题。
        </p>
      </div>
      <el-button type="primary" :loading="loading" @click="loadAll">
        刷新监控
      </el-button>
    </div>

    <section class="summary-grid">
      <el-card v-for="item in summaryCards" :key="item.label" shadow="never">
        <div class="summary-label">
          {{ item.label }}
        </div>
        <div class="summary-value" :class="item.label === '健康状态' ? healthTone : ''">
          {{ item.value }}
        </div>
        <div class="summary-note">
          {{ item.note }}
        </div>
      </el-card>
    </section>

    <el-row :gutter="20">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            JVM 与应用信息
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="应用名称">
              {{ systemInfo.application?.name || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="启动时间">
              {{ systemInfo.application?.startTime || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="运行时长">
              {{ systemInfo.application?.runTime || runtimeInfo.uptimeFormatted || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="Java 版本">
              {{ systemInfo.jvm?.javaVersion || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="Java Vendor">
              {{ systemInfo.jvm?.javaVendor || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="可用处理器">
              {{ systemInfo.jvm?.availableProcessors || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="系统负载">
              {{ systemInfo.operatingSystem?.systemLoadAverage || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            内存概览
          </template>
          <div class="metric">
            <div class="metric-top">
              <span>堆内存使用率</span>
              <strong>{{ memoryInfo.usagePercent || '0%' }}</strong>
            </div>
            <el-progress :percentage="memoryPercent" :stroke-width="12" />
          </div>
          <el-descriptions :column="1" border style="margin-top: 16px">
            <el-descriptions-item label="Heap Used">
              {{ memoryInfo.heap?.used || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="Heap Committed">
              {{ memoryInfo.heap?.committed || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="Heap Max">
              {{ memoryInfo.heap?.max || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="Non-Heap Used">
              {{ memoryInfo.nonHeap?.used || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="Non-Heap Committed">
              {{ memoryInfo.nonHeap?.committed || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>
        系统属性
      </template>
      <div class="properties-grid">
        <article v-for="(value, key) in properties" :key="key" class="property-item">
          <div class="property-key">
            {{ key }}
          </div>
          <div class="property-value" :title="value">
            {{ value }}
          </div>
        </article>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page-shell {
  display: grid;
  gap: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
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

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.summary-label {
  font-size: 12px;
  color: #64748b;
}

.summary-value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 800;
  color: #0f172a;
}

.summary-note {
  margin-top: 8px;
  color: #64748b;
  font-size: 12px;
}

.summary-value.success {
  color: #16a34a;
}

.summary-value.warning {
  color: #d97706;
}

.summary-value.danger {
  color: #dc2626;
}

.metric {
  display: grid;
  gap: 8px;
}

.metric-top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.properties-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}

.property-item {
  padding: 14px;
  border-radius: 14px;
  background: #fff;
  border: 1px solid rgba(148, 163, 184, 0.16);
}

.property-key {
  font-size: 12px;
  color: #64748b;
}

.property-value {
  margin-top: 6px;
  color: #0f172a;
  word-break: break-all;
}
</style>

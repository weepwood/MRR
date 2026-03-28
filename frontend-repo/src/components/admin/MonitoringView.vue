<template>
  <div class="monitoring-view">
    <el-card class="panel-card">
      <template #header>
        <div class="card-header">
          <div>
            <div class="title">系统监控</div>
            <div class="subtitle">聚合运行状态、内存、运行时与系统属性信息</div>
          </div>
          <el-button type="primary" :loading="loading" @click="refreshData">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </template>

      <div v-loading="loading" class="content">
        <div class="summary-grid">
          <div class="summary-card">
            <div class="summary-label">应用</div>
            <div class="summary-value">{{ overview.info?.application?.name || '-' }}</div>
            <div class="summary-meta">启动时间：{{ overview.info?.application?.startTime || '-' }}</div>
          </div>
          <div class="summary-card">
            <div class="summary-label">运行时长</div>
            <div class="summary-value">{{ overview.info?.application?.runTime || overview.runtime?.uptimeFormatted || '-' }}</div>
            <div class="summary-meta">毫秒：{{ overview.runtime?.uptimeMillis ?? '-' }}</div>
          </div>
          <div class="summary-card" :class="healthClass">
            <div class="summary-label">健康状态</div>
            <div class="summary-value">{{ overview.health?.status || '-' }}</div>
            <div class="summary-meta">端口：{{ overview.health?.port || '-' }}</div>
          </div>
          <div class="summary-card">
            <div class="summary-label">内存使用</div>
            <div class="summary-value">{{ overview.memory?.usagePercent || '-' }}</div>
            <div class="summary-meta">堆内存：{{ overview.info?.jvm?.usedMemory || '-' }}</div>
          </div>
        </div>

        <el-row :gutter="16" class="section-grid">
          <el-col :xs="24" :md="12">
            <el-card shadow="never" class="section-card">
              <template #header>
                <div class="section-header">
                  <el-icon><Monitor /></el-icon>
                  <span>运行时信息</span>
                </div>
              </template>
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="JVM 名称">{{ overview.runtime?.name || '-' }}</el-descriptions-item>
                <el-descriptions-item label="启动时间">{{ formatTimestamp(overview.runtime?.startTime) }}</el-descriptions-item>
                <el-descriptions-item label="运行时长">{{ overview.runtime?.uptimeFormatted || '-' }}</el-descriptions-item>
                <el-descriptions-item label="参数数量">{{ overview.runtime?.inputArguments?.length || 0 }}</el-descriptions-item>
              </el-descriptions>
              <div class="args-list">
                <div v-for="(arg, index) in overview.runtime?.inputArguments || []" :key="index" class="arg-item">
                  <span class="arg-index">{{ index + 1 }}</span>
                  <span class="arg-text">{{ arg }}</span>
                </div>
                <div v-if="!overview.runtime?.inputArguments || overview.runtime.inputArguments.length === 0" class="empty-state">
                  暂无启动参数
                </div>
              </div>
            </el-card>
          </el-col>

          <el-col :xs="24" :md="12">
            <el-card shadow="never" class="section-card">
              <template #header>
                <div class="section-header">
                  <el-icon><DataAnalysis /></el-icon>
                  <span>内存信息</span>
                </div>
              </template>
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="Heap Init">{{ overview.memory?.heap?.init || '-' }}</el-descriptions-item>
                <el-descriptions-item label="Heap Used">{{ overview.memory?.heap?.used || '-' }}</el-descriptions-item>
                <el-descriptions-item label="Heap Committed">{{ overview.memory?.heap?.committed || '-' }}</el-descriptions-item>
                <el-descriptions-item label="Heap Max">{{ overview.memory?.heap?.max || '-' }}</el-descriptions-item>
                <el-descriptions-item label="Non-Heap Used">{{ overview.memory?.nonHeap?.used || '-' }}</el-descriptions-item>
                <el-descriptions-item label="Usage">{{ overview.memory?.usagePercent || '-' }}</el-descriptions-item>
              </el-descriptions>
            </el-card>
          </el-col>

          <el-col :xs="24" :md="12">
            <el-card shadow="never" class="section-card">
              <template #header>
                <div class="section-header">
                  <el-icon><Setting /></el-icon>
                  <span>系统属性</span>
                </div>
              </template>
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="操作系统">{{ overview.properties?.['os.name'] || '-' }} {{ overview.properties?.['os.version'] || '' }}</el-descriptions-item>
                <el-descriptions-item label="CPU 架构">{{ overview.properties?.['os.arch'] || '-' }}</el-descriptions-item>
                <el-descriptions-item label="Java 版本">{{ overview.properties?.['java.version'] || '-' }}</el-descriptions-item>
                <el-descriptions-item label="Java 供应商">{{ overview.properties?.['java.vendor'] || '-' }}</el-descriptions-item>
                <el-descriptions-item label="工作目录">{{ overview.properties?.['user.dir'] || '-' }}</el-descriptions-item>
                <el-descriptions-item label="用户目录">{{ overview.properties?.['user.home'] || '-' }}</el-descriptions-item>
              </el-descriptions>
            </el-card>
          </el-col>

          <el-col :xs="24" :md="12">
            <el-card shadow="never" class="section-card">
              <template #header>
                <div class="section-header">
                  <el-icon><InfoFilled /></el-icon>
                  <span>JVM / 应用信息</span>
                </div>
              </template>
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="应用名称">{{ overview.info?.application?.name || '-' }}</el-descriptions-item>
                <el-descriptions-item label="启动时间">{{ overview.info?.application?.startTime || '-' }}</el-descriptions-item>
                <el-descriptions-item label="Java 版本">{{ overview.info?.jvm?.javaVersion || '-' }}</el-descriptions-item>
                <el-descriptions-item label="Java 供应商">{{ overview.info?.jvm?.javaVendor || '-' }}</el-descriptions-item>
                <el-descriptions-item label="Java Home">{{ overview.info?.jvm?.javaHome || '-' }}</el-descriptions-item>
                <el-descriptions-item label="可用处理器">{{ overview.info?.jvm?.availableProcessors || '-' }}</el-descriptions-item>
              </el-descriptions>
            </el-card>
          </el-col>
        </el-row>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DataAnalysis, InfoFilled, Monitor, Refresh, Setting } from '@element-plus/icons-vue'
import { getSystemOverview } from '@/utils/api'

const loading = ref(false)
const overview = reactive({
  info: {
    application: {},
    jvm: {},
    operatingSystem: {}
  },
  memory: {
    heap: {},
    nonHeap: {}
  },
  runtime: {
    inputArguments: []
  },
  health: {},
  properties: {}
})

const healthClass = computed(() => {
  const status = String(overview.health?.status || '').toUpperCase()
  if (status === 'UP') return 'health-up'
  if (status === 'WARNING') return 'health-warning'
  return 'health-unknown'
})

const loadOverview = async () => {
  loading.value = true
  try {
    const response = await getSystemOverview()
    const result = response?.data
    if (!result || result.code !== 200) {
      throw new Error(result?.message || '监控信息加载失败')
    }

    const payload = result.data || {}
    Object.assign(overview.info, payload.info || {})
    Object.assign(overview.memory, payload.memory || {})
    Object.assign(overview.runtime, payload.runtime || {})
    Object.assign(overview.health, payload.health || {})
    Object.assign(overview.properties, payload.properties || {})
  } catch (error) {
    ElMessage.error(error?.message || '监控信息加载失败')
  } finally {
    loading.value = false
  }
}

const refreshData = async () => {
  await loadOverview()
  ElMessage.success('监控信息已刷新')
}

const formatTimestamp = (value) => {
  if (!value) return '-'
  const timestamp = Number(value)
  if (Number.isNaN(timestamp)) return String(value)
  const date = new Date(timestamp)
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN', { hour12: false })
}

onMounted(() => {
  loadOverview()
})
</script>

<style scoped>
.monitoring-view {
  height: 100%;
}

.panel-card {
  min-height: 560px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
}

.title {
  font-size: 16px;
  font-weight: 700;
  color: #1d2b42;
}

.subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: #6a7d99;
}

.content {
  min-height: 420px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 18px;
}

.summary-card {
  border-radius: 16px;
  border: 1px solid #dce8fb;
  background: linear-gradient(180deg, #fbfdff 0%, #f4f8ff 100%);
  padding: 16px 18px;
  box-shadow: 0 8px 20px rgba(24, 65, 134, 0.08);
}

.summary-label {
  color: #5f7090;
  font-size: 12px;
}

.summary-value {
  margin-top: 6px;
  color: #1f2b42;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.25;
}

.summary-meta {
  margin-top: 6px;
  color: #7386a8;
  font-size: 12px;
}

.health-up .summary-value {
  color: #16a34a;
}

.health-warning .summary-value {
  color: #d97706;
}

.health-unknown .summary-value {
  color: #475569;
}

.section-grid {
  margin-top: 6px;
}

.section-card {
  margin-bottom: 16px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #1d2b42;
  font-weight: 600;
}

.args-list {
  margin-top: 14px;
}

.arg-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f9fbff;
  border: 1px solid #e6eef9;
  margin-bottom: 8px;
}

.arg-index {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #e9f2ff;
  color: #2e81ff;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.arg-text {
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Fira Code', monospace;
  font-size: 12px;
  color: #31455f;
  word-break: break-all;
}

.empty-state {
  padding: 20px;
  text-align: center;
  color: #8694ad;
  font-size: 13px;
}

@media (max-width: 1024px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .card-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>

<template>
  <div class="monitoring-view pmr-page">
    <el-card class="panel-card pmr-panel">
      <template #header>
        <div class="card-header pmr-panel-header">
          <div>
            <div class="title">系统监控</div>
            <div class="subtitle">运行时、内存、JVM 和系统属性</div>
          </div>
          <el-button type="primary" :loading="loading" @click="refreshData">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </template>

      <div v-loading="loading" class="content">
        <div class="info-cards">
          <div class="info-card">
            <div class="info-label">运行时长</div>
            <div class="info-value">{{ systemInfo.uptimeFormatted || '-' }}</div>
          </div>
          <div class="info-card">
            <div class="info-label">JVM 名称</div>
            <div class="info-value ellipsis" :title="systemInfo.name">{{ systemInfo.name || '-' }}</div>
          </div>
          <div class="info-card">
            <div class="info-label">启动时间</div>
            <div class="info-value">{{ formatTimestamp(systemInfo.startTime) }}</div>
          </div>
          <div class="info-card" :class="healthStatus.status?.toLowerCase()">
            <div class="info-label">系统状态</div>
            <div class="info-value">
              {{ healthStatus.status || '-' }}
              <span v-if="healthStatus.port" class="muted">端口 {{ healthStatus.port }}</span>
            </div>
          </div>
        </div>

        <el-card class="section-card" shadow="hover">
          <template #header>
            <div class="section-header">
              <el-icon><Timer /></el-icon>
              <span>JVM 启动参数</span>
            </div>
          </template>
          <div v-if="systemInfo.inputArguments?.length" class="args-list">
            <div v-for="(arg, index) in systemInfo.inputArguments" :key="index" class="arg-item">
              <span class="arg-no">{{ index + 1 }}</span>
              <span class="arg-text">{{ arg }}</span>
            </div>
          </div>
          <div v-else class="empty">暂无启动参数</div>
        </el-card>

        <el-card class="section-card" shadow="hover">
          <template #header>
            <div class="section-header">
              <el-icon><FolderOpened /></el-icon>
              <span>类路径(ClassPath)</span>
            </div>
          </template>
          <el-input type="textarea" :model-value="systemInfo.classPath || '暂无数据'" :rows="5" readonly />
          <div v-if="systemInfo.classPath" class="tags">
            <el-tag type="success">{{ countJarFiles(systemInfo.classPath) }} 个 JAR</el-tag>
            <el-tag type="primary">{{ countPaths(systemInfo.classPath) }} 个路径</el-tag>
          </div>
        </el-card>

        <el-card class="section-card" shadow="hover">
          <template #header>
            <div class="section-header">
              <el-icon><DataAnalysis /></el-icon>
              <span>内存情况</span>
            </div>
          </template>
          <div class="memory-summary">
            <div class="usage-box">
              <div class="info-label">总体使用率</div>
              <div class="usage-value" :class="usageClass">{{ memoryInfo.usagePercent || '-' }}</div>
            </div>
            <div class="trend-box">
              <div v-for="point in memoryTrendData" :key="point.ts" class="trend-item">
                <div class="trend-bar" :style="{ height: `${point.percent}%` }"></div>
                <div class="trend-label">{{ point.percent }}%</div>
              </div>
            </div>
          </div>
          <div class="memory-grid">
            <div class="memory-card">
              <h4>Heap</h4>
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="Init">{{ memoryInfo.heap?.init || '-' }}</el-descriptions-item>
                <el-descriptions-item label="Used">{{ memoryInfo.heap?.used || '-' }}</el-descriptions-item>
                <el-descriptions-item label="Committed">{{ memoryInfo.heap?.committed || '-' }}</el-descriptions-item>
                <el-descriptions-item label="Max">{{ memoryInfo.heap?.max || '-' }}</el-descriptions-item>
              </el-descriptions>
            </div>
            <div class="memory-card">
              <h4>Non-Heap</h4>
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="Init">{{ memoryInfo.nonHeap?.init || '-' }}</el-descriptions-item>
                <el-descriptions-item label="Used">{{ memoryInfo.nonHeap?.used || '-' }}</el-descriptions-item>
                <el-descriptions-item label="Committed">{{ memoryInfo.nonHeap?.committed || '-' }}</el-descriptions-item>
                <el-descriptions-item label="Max">{{ memoryInfo.nonHeap?.max || '-' }}</el-descriptions-item>
              </el-descriptions>
            </div>
          </div>
        </el-card>

        <el-card class="section-card" shadow="hover">
          <template #header>
            <div class="section-header">
              <el-icon><Monitor /></el-icon>
              <span>运行详情</span>
            </div>
          </template>
          <div class="detail-grid">
            <div class="detail-card">
              <h4>JVM 信息</h4>
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="应用">{{ systemDetail.application?.name || '-' }}</el-descriptions-item>
                <el-descriptions-item label="启动时间">{{ systemDetail.application?.startTime || '-' }}</el-descriptions-item>
                <el-descriptions-item label="运行时长">{{ systemDetail.application?.runTime || '-' }}</el-descriptions-item>
                <el-descriptions-item label="Java">{{ systemDetail.jvm?.javaVersion || '-' }}</el-descriptions-item>
                <el-descriptions-item label="供应商">{{ systemDetail.jvm?.javaVendor || '-' }}</el-descriptions-item>
                <el-descriptions-item label="Java Home">
                  <span class="ellipsis" :title="systemDetail.jvm?.javaHome">{{ systemDetail.jvm?.javaHome || '-' }}</span>
                </el-descriptions-item>
              </el-descriptions>
            </div>
            <div class="detail-card">
              <h4>内存信息</h4>
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="已用">{{ systemDetail.jvm?.usedMemory || '-' }}</el-descriptions-item>
                <el-descriptions-item label="总内存">{{ systemDetail.jvm?.totalMemory || '-' }}</el-descriptions-item>
                <el-descriptions-item label="最大内存">{{ systemDetail.jvm?.maxMemory || '-' }}</el-descriptions-item>
                <el-descriptions-item label="空闲">{{ systemDetail.jvm?.freeMemory || '-' }}</el-descriptions-item>
              </el-descriptions>
            </div>
            <div class="detail-card">
              <h4>系统信息</h4>
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="处理器">{{ systemDetail.jvm?.availableProcessors || '-' }}</el-descriptions-item>
                <el-descriptions-item label="操作系统">{{ systemDetail.operatingSystem?.name || '-' }}</el-descriptions-item>
                <el-descriptions-item label="架构">{{ systemDetail.operatingSystem?.arch || '-' }}</el-descriptions-item>
                <el-descriptions-item label="版本">{{ systemDetail.operatingSystem?.version || '-' }}</el-descriptions-item>
              </el-descriptions>
            </div>
          </div>
        </el-card>

        <el-card class="section-card" shadow="hover">
          <template #header>
            <div class="section-header">
              <el-icon><InfoFilled /></el-icon>
              <span>系统属性</span>
            </div>
          </template>
          <div class="props-grid">
            <div v-for="item in propItems" :key="item.key" class="prop-item">
              <div class="prop-label">{{ item.label }}</div>
              <div class="prop-value ellipsis" :title="systemProperties[item.key]">{{ systemProperties[item.key] || '-' }}</div>
            </div>
          </div>
        </el-card>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DataAnalysis, FolderOpened, InfoFilled, Monitor, Refresh, Timer } from '@element-plus/icons-vue'
import { getSystemHealth, getSystemInfo, getSystemMemory, getSystemProperties, getSystemRuntime } from '@/utils/api'

const loading = ref(false)
const systemInfo = ref({ inputArguments: [], classPath: '' })
const systemProperties = ref({})
const memoryInfo = ref({ heap: {}, nonHeap: {} })
const systemDetail = ref({ application: {}, jvm: {}, operatingSystem: {} })
const healthStatus = ref({})
const memoryTrendData = ref([])

const propItems = [
  { key: 'os.name', label: '操作系统' },
  { key: 'os.arch', label: 'CPU 架构' },
  { key: 'os.version', label: '系统版本' },
  { key: 'java.version', label: 'Java 版本' },
  { key: 'java.vendor', label: 'Java 供应商' },
  { key: 'java.home', label: 'Java Home' },
  { key: 'user.name', label: '用户名' },
  { key: 'user.dir', label: '工作目录' },
  { key: 'user.home', label: '用户目录' },
  { key: 'file.encoding', label: '文件编码' }
]

const usageClass = computed(() => {
  const v = Number.parseFloat(String(memoryInfo.value.usagePercent || '').replace('%', ''))
  if (Number.isNaN(v)) return 'normal'
  if (v >= 85) return 'critical'
  if (v >= 70) return 'warning'
  return 'normal'
})

const formatTimestamp = (value) => {
  if (!value) return '-'
  const ts = Number(value)
  if (Number.isNaN(ts)) return String(value)
  const d = new Date(ts)
  return Number.isNaN(d.getTime()) ? String(value) : d.toLocaleString('zh-CN', { hour12: false })
}

const countJarFiles = (classPath) => classPath.split(';').filter((item) => item.trim().toLowerCase().endsWith('.jar')).length
const countPaths = (classPath) => classPath.split(';').filter(Boolean).length

const pushTrend = (memory) => {
  const percent = Number.parseFloat(String(memory?.usagePercent || '').replace('%', ''))
  if (Number.isNaN(percent)) return
  memoryTrendData.value = [...memoryTrendData.value, { percent, ts: Date.now() }].slice(-10)
}

const pickPayload = (response) => (response?.data?.code === 200 ? response.data.data || {} : response?.data || {})

const loadAll = async () => {
  loading.value = true
  try {
    const [runtimeRes, propRes, memoryRes, infoRes, healthRes] = await Promise.all([
      getSystemRuntime(),
      getSystemProperties(),
      getSystemMemory(),
      getSystemInfo(),
      getSystemHealth()
    ])
    systemInfo.value = { ...systemInfo.value, ...pickPayload(runtimeRes) }
    systemProperties.value = { ...systemProperties.value, ...pickPayload(propRes) }
    memoryInfo.value = { ...memoryInfo.value, ...pickPayload(memoryRes) }
    systemDetail.value = { ...systemDetail.value, ...pickPayload(infoRes) }
    healthStatus.value = { ...healthStatus.value, ...pickPayload(healthRes) }
    pushTrend(memoryInfo.value)
  } catch (error) {
    ElMessage.error(error?.message || '监控信息加载失败')
  } finally {
    loading.value = false
  }
}

const refreshData = async () => {
  await loadAll()
  ElMessage.success('监控信息已刷新')
}

onMounted(loadAll)
</script>

<style scoped>
.monitoring-view { height: 100%; }
.panel-card { min-height: 560px; }
.card-header, .section-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.title { font-size: 16px; font-weight: 700; color: #1d2b42; }
.subtitle { margin-top: 4px; font-size: 12px; color: #6a7d99; }
.content { min-height: 420px; }
.info-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; margin-bottom: 20px; }
.info-card { background: #fff; border: 1px solid #dce8fb; border-radius: 14px; padding: 16px; box-shadow: 0 8px 18px rgba(24,65,134,.08); }
.info-label, .prop-label { color: #6a7d99; font-size: 12px; font-weight: 600; }
.info-value { margin-top: 6px; font-size: 22px; font-weight: 700; color: #1d2b42; }
.info-card.up { border-color: #b7ebc6; }
.info-card.warning { border-color: #ffd59e; }
.info-card.critical { border-color: #ffb4ae; }
.muted { font-size: 12px; color: #8694ad; margin-left: 8px; font-weight: 400; }
.section-card { margin-top: 20px; }
.args-list { display: grid; gap: 8px; }
.arg-item { display: flex; gap: 12px; align-items: flex-start; background: #f9fbff; border: 1px solid #e6eef9; border-radius: 10px; padding: 10px 12px; }
.arg-no { min-width: 22px; height: 22px; border-radius: 6px; background: #e9f2ff; color: #2e81ff; display: inline-flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; }
.arg-text { font-family: monospace; font-size: 12px; color: #31455f; word-break: break-all; }
.empty { padding: 18px; text-align: center; color: #8694ad; }
.tags { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 12px; }
.memory-summary { display: grid; grid-template-columns: 200px 1fr; gap: 16px; align-items: stretch; margin-bottom: 16px; }
.usage-box { background: #fbfbfd; border-radius: 12px; padding: 18px; text-align: center; }
.usage-value { font-size: 34px; font-weight: 800; margin-top: 6px; }
.usage-value.normal { color: #34c759; }
.usage-value.warning { color: #ff9500; }
.usage-value.critical { color: #ff3b30; }
.trend-box { display: grid; grid-template-columns: repeat(10, minmax(0, 1fr)); gap: 8px; align-items: end; background: linear-gradient(to bottom, #fafafa, #ffffff); border: 1px solid #e8e8ed; border-radius: 12px; padding: 16px; min-height: 120px; }
.trend-item { display: flex; flex-direction: column; align-items: center; gap: 8px; height: 100%; justify-content: end; }
.trend-bar { width: 100%; min-height: 10px; border-radius: 8px 8px 2px 2px; background: linear-gradient(to top, #4facfe, #00f2fe); }
.trend-label { font-size: 11px; color: #6a7d99; }
.memory-grid, .detail-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 16px; }
.memory-card, .detail-card { background: #fbfbfd; border-radius: 12px; padding: 16px; }
.memory-card h4, .detail-card h4 { margin: 0 0 12px; color: #1d1d1f; font-size: 15px; }
.props-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 12px; }
.prop-item { background: #fbfbfd; border: 1px solid transparent; border-radius: 10px; padding: 14px; }
.prop-value, .ellipsis { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
@media (max-width: 768px) {
  .card-header { flex-direction: column; align-items: flex-start; }
  .memory-summary { grid-template-columns: 1fr; }
  .trend-box { grid-template-columns: repeat(5, minmax(0, 1fr)); }
}
</style>

<template>
  <div class="monitoring-view">
    <el-card>
      <template #header>
        <div class="card-header">
          <span><el-icon><Monitor /></el-icon> 系统运行时监控</span>
          <el-button type="primary" @click="refreshData" :loading="loading" size="small">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </template>

      <div v-loading="loading" class="content-wrapper">
        <!-- 顶部信息卡片 -->
        <div class="info-cards-container">
          <!-- 运行时长卡片 -->
          <div class="info-card uptime">
            <div class="icon">
              <el-icon><Timer /></el-icon>
            </div>
            <div class="content">
              <div class="label">运行时长</div>
              <div class="value">{{ systemInfo.uptimeFormatted || '-' }}</div>
            </div>
          </div>

          <!-- JVM 名称卡片 -->
          <div class="info-card name">
            <div class="icon">
              <el-icon><Monitor /></el-icon>
            </div>
            <div class="content">
              <div class="label">JVM 名称</div>
              <div class="value short-text" :title="systemInfo.name">{{ systemInfo.name || '-' }}</div>
            </div>
          </div>

          <!-- 启动时间卡片 -->
          <div class="info-card start-time">
            <div class="icon">
              <el-icon><Calendar /></el-icon>
            </div>
            <div class="content">
              <div class="label">启动时间</div>
              <div class="value">{{ formatTimestamp(systemInfo.startTime) }}</div>
            </div>
          </div>

          <!-- 系统健康状态 -->
          <div class="info-card health" :class="healthStatus.status">
            <div class="icon">
              <el-icon v-if="healthStatus.status === 'UP'"><CircleCheck /></el-icon>
              <el-icon v-else><Warning /></el-icon>
            </div>
            <div class="content">
              <div class="label">系统状态</div>
              <div class="value">
                <span :class="healthStatus.status">{{ healthStatus.status || '-' }}</span>
                <span v-if="healthStatus.port" class="port-info">端口：{{ healthStatus.port }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- JVM 参数 -->
        <el-card class="section-card" shadow="hover">
          <template #header>
            <div class="section-header">
              <el-icon><Setting /></el-icon>
              <span>JVM 启动参数</span>
              <el-tag size="small" type="info">{{ systemInfo.inputArguments?.length || 0 }} 个参数</el-tag>
            </div>
          </template>
          
          <div class="args-list">
            <div 
              v-for="(arg, index) in systemInfo.inputArguments" 
              :key="index" 
              class="arg-item"
            >
              <span class="arg-number">{{ index + 1 }}</span>
              <span class="arg-text">{{ arg }}</span>
            </div>
            <div v-if="!systemInfo.inputArguments || systemInfo.inputArguments.length === 0" class="empty-data">
              暂无数据
            </div>
          </div>
        </el-card>

        <!-- 类路径信息 -->
        <el-card class="section-card" shadow="hover">
          <template #header>
            <div class="section-header">
              <el-icon><FolderOpened /></el-icon>
              <span>类路径 (ClassPath)</span>
            </div>
          </template>
          
          <div class="classpath-content">
            <el-input
              type="textarea"
              :model-value="systemInfo.classPath || '暂无数据'"
              :rows="6"
              readonly
              class="classpath-textarea"
            />
            <div class="classpath-stats" v-if="systemInfo.classPath">
              <el-tag size="small" type="success" style="margin-right: 8px;">
                <el-icon><Document /></el-icon>
                {{ countJarFiles(systemInfo.classPath) }} 个 JAR 文件
              </el-tag>
              <el-tag size="small" type="primary">
                <el-icon><Link /></el-icon>
                路径数：{{ countPaths(systemInfo.classPath) }}
              </el-tag>
            </div>
          </div>
        </el-card>

        <!-- 内存使用信息 -->
        <el-card class="section-card" shadow="hover">
          <template #header>
            <div class="section-header">
              <el-icon><DataAnalysis /></el-icon>
              <span>内存使用情况</span>
            </div>
          </template>
          
          <div class="memory-overview">
            <div class="memory-stat">
              <div class="stat-label">总使用率</div>
              <div class="stat-value" :class="getUsageClass(memoryInfo.usagePercent)">
                {{ memoryInfo.usagePercent || '-' }}
              </div>
            </div>
            
            <div class="memory-details">
              <div class="memory-section">
                <h4 class="memory-title">堆内存 (Heap)</h4>
                <el-descriptions :column="2" border size="small">
                  <el-descriptions-item label="初始大小">{{ memoryInfo.heap?.init || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="已提交">{{ memoryInfo.heap?.committed || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="最大值">{{ memoryInfo.heap?.max || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="已使用">{{ memoryInfo.heap?.used || '-' }}</el-descriptions-item>
                </el-descriptions>
              </div>
              
              <div class="memory-section">
                <h4 class="memory-title">非堆内存 (Non-Heap)</h4>
                <el-descriptions :column="2" border size="small">
                  <el-descriptions-item label="初始大小">{{ memoryInfo.nonHeap?.init || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="已提交">{{ memoryInfo.nonHeap?.committed || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="最大值">{{ memoryInfo.nonHeap?.max || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="已使用">{{ memoryInfo.nonHeap?.used || '-' }}</el-descriptions-item>
                </el-descriptions>
              </div>
            </div>
          </div>
        </el-card>
        
        <!-- 运行详情 -->
        <el-card class="section-card" shadow="hover">
          <template #header>
            <div class="section-header">
              <el-icon><DataAnalysis /></el-icon>
              <span>详细信息</span>
            </div>
          </template>
          
          <el-descriptions :column="2" border>
            <el-descriptions-item label="运行时长 (毫秒)">
              {{ systemInfo.uptimeMillis || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="格式化时长">
              {{ systemInfo.uptimeFormatted || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="JVM 名称">
              {{ systemInfo.name || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="启动时间戳">
              {{ systemInfo.startTime || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="参数数量" :span="2">
              {{ systemInfo.inputArguments?.length || 0 }} 个
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 系统属性 -->
        <el-card class="section-card" shadow="hover">
          <template #header>
            <div class="section-header">
              <el-icon><InfoFilled /></el-icon>
              <span>系统属性</span>
            </div>
          </template>
          
          <div class="properties-grid">
            <div class="property-item">
              <div class="property-icon platform">
                <el-icon><Platform /></el-icon>
              </div>
              <div class="property-content">
                <div class="property-label">操作系统</div>
                <div class="property-value">{{ systemProperties['os.name'] || '-' }} {{ systemProperties['os.version'] }}</div>
              </div>
            </div>
            
            <div class="property-item">
              <div class="property-icon cpu">
                <el-icon><Cpu /></el-icon>
              </div>
              <div class="property-content">
                <div class="property-label">CPU 架构</div>
                <div class="property-value">{{ systemProperties['os.arch'] || '-' }}</div>
              </div>
            </div>
            
            <div class="property-item">
              <div class="property-icon java">
                <el-icon><Setting /></el-icon>
              </div>
              <div class="property-content">
                <div class="property-label">Java 版本</div>
                <div class="property-value">{{ systemProperties['java.version'] || '-' }}</div>
              </div>
            </div>
            
            <div class="property-item">
              <div class="property-icon vendor">
                <el-icon><Monitor /></el-icon>
              </div>
              <div class="property-content">
                <div class="property-label">Java 供应商</div>
                <div class="property-value">{{ systemProperties['java.vendor'] || '-' }}</div>
              </div>
            </div>
            
            <div class="property-item">
              <div class="property-icon encoding">
                <el-icon><Document /></el-icon>
              </div>
              <div class="property-content">
                <div class="property-label">文件编码</div>
                <div class="property-value">{{ systemProperties['file.encoding'] || '-' }}</div>
              </div>
            </div>
            
            <div class="property-item">
              <div class="property-icon user">
                <el-icon><UserFilled /></el-icon>
              </div>
              <div class="property-content">
                <div class="property-label">用户名称</div>
                <div class="property-value">{{ systemProperties['user.name'] || '-' }}</div>
              </div>
            </div>
            
            <div class="property-item full-width">
              <div class="property-icon home">
                <el-icon><Location /></el-icon>
              </div>
              <div class="property-content">
                <div class="property-label">用户目录</div>
                <div class="property-value long-text" :title="systemProperties['user.home']">
                  {{ systemProperties['user.home'] || '-' }}
                </div>
              </div>
            </div>
            
            <div class="property-item full-width">
              <div class="property-icon dir">
                <el-icon><FolderOpened /></el-icon>
              </div>
              <div class="property-content">
                <div class="property-label">工作目录</div>
                <div class="property-value long-text" :title="systemProperties['user.dir']">
                  {{ systemProperties['user.dir'] || '-' }}
                </div>
              </div>
            </div>
            
            <div class="property-item full-width">
              <div class="property-icon jhome">
                <el-icon><Link /></el-icon>
              </div>
              <div class="property-content">
                <div class="property-label">Java 主目录</div>
                <div class="property-value long-text" :title="systemProperties['java.home']">
                  {{ systemProperties['java.home'] || '-' }}
                </div>
              </div>
            </div>
          </div>
        </el-card>

        <!-- 系统详细信息 -->
        <el-card class="section-card" shadow="hover">
          <template #header>
            <div class="section-header">
              <el-icon><Monitor /></el-icon>
              <span>系统详细信息</span>
            </div>
          </template>
          
          <div class="system-detail-grid">
            <!-- JVM 信息 -->
            <div class="detail-section">
              <h4 class="detail-title">JVM 信息</h4>
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="应用名称">{{ systemDetail.application?.name || '-' }}</el-descriptions-item>
                <el-descriptions-item label="启动时间">{{ systemDetail.application?.startTime || '-' }}</el-descriptions-item>
                <el-descriptions-item label="运行时长">{{ systemDetail.application?.runTime || '-' }}</el-descriptions-item>
                <el-descriptions-item label="Java 版本">{{ systemDetail.jvm?.javaVersion || '-' }}</el-descriptions-item>
                <el-descriptions-item label="Java 供应商">{{ systemDetail.jvm?.javaVendor || '-' }}</el-descriptions-item>
                <el-descriptions-item label="Java 主目录" label-class-name="long-label">
                  <span class="long-value" :title="systemDetail.jvm?.javaHome">{{ systemDetail.jvm?.javaHome || '-' }}</span>
                </el-descriptions-item>
              </el-descriptions>
            </div>
            
            <!-- 内存信息 -->
            <div class="detail-section">
              <h4 class="detail-title">内存信息</h4>
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="已用内存">{{ systemDetail.jvm?.usedMemory || '-' }}</el-descriptions-item>
                <el-descriptions-item label="总内存">{{ systemDetail.jvm?.totalMemory || '-' }}</el-descriptions-item>
                <el-descriptions-item label="最大内存">{{ systemDetail.jvm?.maxMemory || '-' }}</el-descriptions-item>
                <el-descriptions-item label="空闲内存">{{ systemDetail.jvm?.freeMemory || '-' }}</el-descriptions-item>
              </el-descriptions>
            </div>
            
            <!-- 处理器信息 -->
            <div class="detail-section">
              <h4 class="detail-title">处理器信息</h4>
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="可用处理器数">{{ systemDetail.jvm?.availableProcessors || '-' }}</el-descriptions-item>
                <el-descriptions-item label="操作系统">{{ systemDetail.operatingSystem?.name || '-' }}</el-descriptions-item>
                <el-descriptions-item label="系统架构">{{ systemDetail.operatingSystem?.arch || '-' }}</el-descriptions-item>
                <el-descriptions-item label="系统版本">{{ systemDetail.operatingSystem?.version || '-' }}</el-descriptions-item>
              </el-descriptions>
            </div>
          </div>
        </el-card>

      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { 
  Monitor, 
  Refresh, 
  Timer, 
  Cpu, 
  Calendar, 
  Setting, 
  FolderOpened, 
  Document,
  Link,
  DataAnalysis,
  InfoFilled,
  Platform,
  Location,
  UserFilled,
  TrendCharts,
  Warning,
  CircleCheck
} from '@element-plus/icons-vue'
import { getSystemRuntime, getSystemProperties, getSystemMemory, getSystemInfo, getSystemHealth } from '@/utils/api'

const loading = ref(false)
const systemInfo = ref({
  uptimeMillis: null,
  uptimeFormatted: '',
  inputArguments: [],
  name: '',
  startTime: null,
  classPath: ''
})

const systemProperties = ref({
  'java.vendor': '',
  'file.encoding': '',
  'java.version': '',
  'user.dir': '',
  'user.home': '',
  'os.arch': '',
  'os.name': '',
  'user.name': '',
  'java.home': '',
  'os.version': ''
})

const memoryInfo = ref({
  usagePercent: '',
  heap: {
    init: '',
    committed: '',
    max: '',
    used: ''
  },
  nonHeap: {
    init: '',
    committed: '',
    max: '',
    used: ''
  }
})

const systemDetail = ref({
  jvm: {
    usedMemory: '',
    availableProcessors: 0,
    totalMemory: '',
    javaVersion: '',
    javaVendor: '',
    maxMemory: '',
    freeMemory: '',
    javaHome: ''
  },
  application: {
    name: '',
    startTime: '',
    runTime: ''
  },
  operatingSystem: {
    availableProcessors: '',
    name: '',
    arch: '',
    version: ''
  }
})

const healthStatus = ref({
  components: {
    memory: {
      usagePercent: '',
      status: ''
    }
  },
  application: '',
  port: '',
  status: '',
  timestamp: ''
})

// 内存使用趋势数据（模拟历史数据）
const memoryTrendData = ref([])

// SVG 图表配置
const svgWidth = 800
const svgHeight = 320
const paddingLeft = 50
const paddingRight = 20
const paddingTop = 20
const paddingBottom = 40
const chartWidth = svgWidth - paddingLeft - paddingRight
const chartHeight = svgHeight - paddingTop - paddingBottom

// 计算 X 轴步长
const xStep = computed(() => {
  if (memoryTrendData.value.length <= 1) return chartWidth
  return chartWidth / (memoryTrendData.value.length - 1)
})

// 获取系统运行时信息
const loadSystemInfo = async () => {
  loading.value = true
  try {
    const response = await getSystemRuntime()
    
    if (response.data && response.data.code === 200) {
      systemInfo.value = response.data.data || {}
    } else if (response.data) {
      // 如果没有 code 字段，直接使用 data
      systemInfo.value = response.data
    }
  } catch (error) {
    console.error('加载系统信息失败:', error)
    ElMessage.error('加载系统信息失败：' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// 获取系统属性信息
const loadSystemProperties = async () => {
  try {
    const response = await getSystemProperties()
    
    if (response.data && response.data.code === 200) {
      systemProperties.value = response.data.data || {}
    } else if (response.data) {
      systemProperties.value = response.data
    }
  } catch (error) {
    console.error('加载系统属性失败:', error)
    ElMessage.error('加载系统属性失败：' + (error.message || '未知错误'))
  }
}

// 获取系统内存信息
const loadSystemMemory = async () => {
  try {
    const response = await getSystemMemory()
    
    if (response.data && response.data.code === 200) {
      memoryInfo.value = response.data.data || {}
      // 添加到趋势数据
      addToMemoryTrend(memoryInfo.value)
    } else if (response.data) {
      memoryInfo.value = response.data
      addToMemoryTrend(memoryInfo.value)
    }
  } catch (error) {
    console.error('加载内存信息失败:', error)
    ElMessage.error('加载内存信息失败：' + (error.message || '未知错误'))
  }
}

// 获取系统详细信息
const loadSystemDetail = async () => {
  try {
    const response = await getSystemInfo()
    
    if (response.data && response.data.code === 200) {
      systemDetail.value = response.data.data || {}
    } else if (response.data) {
      systemDetail.value = response.data
    }
  } catch (error) {
    console.error('加载系统详情失败:', error)
    ElMessage.error('加载系统详情失败：' + (error.message || '未知错误'))
  }
}

// 获取系统健康状态
const loadSystemHealth = async () => {
  try {
    const response = await getSystemHealth()
    
    if (response.data && response.data.code === 200) {
      healthStatus.value = response.data.data || {}
    } else if (response.data) {
      healthStatus.value = response.data
    }
  } catch (error) {
    console.error('加载健康状态失败:', error)
    ElMessage.error('加载健康状态失败：' + (error.message || '未知错误'))
  }
}

// 添加内存数据到趋势数组（保留最近 10 条记录）
const addToMemoryTrend = (memoryData) => {
  const now = new Date()
  const timeStr = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}`
  
  // 解析使用百分比
  let usagePercent = 0
  if (memoryData.usagePercent) {
    usagePercent = parseFloat(memoryData.usagePercent.replace('%', ''))
  }
  
  memoryTrendData.value.push({
    time: timeStr,
    heapUsed: parseFloat(memoryData.heap?.used || 0),
    nonHeapUsed: parseFloat(memoryData.nonHeap?.used || 0),
    usagePercent: usagePercent
  })
  
  // 保持最近 10 条数据
  if (memoryTrendData.value.length > 10) {
    memoryTrendData.value.shift()
  }
}

// 刷新数据
const refreshData = () => {
  loadSystemInfo()
  loadSystemProperties()
  loadSystemMemory()
  loadSystemDetail()
  loadSystemHealth()
}

// 格式化时间戳
const formatTimestamp = (timestamp) => {
  if (!timestamp) return '-'
  const date = new Date(timestamp)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

// 获取使用率颜色类
const getUsageClass = (usagePercent) => {
  if (!usagePercent) return ''
  const percent = parseFloat(usagePercent.replace('%', ''))
  if (percent > 80) return 'critical'
  if (percent > 60) return 'warning'
  return 'normal'
}

// 计算柱状图高度（基于最大值归一化）
const calculateBarHeight = (value) => {
  if (!value || value <= 0) return 0
  // 假设最大值为 100MB 用于归一化
  const maxValue = 100
  const height = (value / maxValue) * 100
  return Math.min(height, 100) // 最大 100%
}

// 计算 Y 轴位置
const calculateYPosition = (value, isPercent = false) => {
  if (!value) return paddingTop + chartHeight
  
  const numValue = parseFloat(value)
  if (isNaN(numValue)) return paddingTop + chartHeight
  
  // 根据数据类型确定最大值
  const maxValue = isPercent ? 100 : 100 // 百分比最大 100%，内存值最大 100MB
  const normalizedValue = Math.min(numValue / maxValue, 1)
  
  // SVG Y 轴从上到下，需要反转
  return paddingTop + chartHeight - (normalizedValue * chartHeight)
}

// 生成折线点坐标
const generateLinePoints = (metric, isPercent = false) => {
  if (memoryTrendData.value.length === 0) return ''
  
  const points = memoryTrendData.value.map((item, index) => {
    const x = paddingLeft + (index * xStep.value)
    const y = calculateYPosition(item[metric], isPercent)
    return `${x},${y}`
  })
  
  return points.join(' ')
}

// 格式化 tooltip 显示
const formatTooltip = (item, metric) => {
  const labels = {
    heapUsed: '堆内存',
    nonHeapUsed: '非堆内存',
    usagePercent: '使用率'
  }
  
  const value = item[metric]
  const unit = metric === 'usagePercent' ? '%' : ' MB'
  
  return `${labels[metric]}: ${value}${unit}\n时间：${item.time}`
}

// 统计 JAR 文件数量
const countJarFiles = (classPath) => {
  if (!classPath) return 0
  const matches = classPath.match(/\.jar/gi)
  return matches ? matches.length : 0
}

// 统计路径数量
const countPaths = (classPath) => {
  if (!classPath) return 0
  return classPath.split(/[;]/).filter(p => p.trim()).length
}

// 初始化加载
onMounted(() => {
  loadSystemInfo()
  loadSystemProperties()
  loadSystemMemory()
  loadSystemDetail()
  loadSystemHealth()
})

</script>

<style scoped>
.monitoring-view {
  padding: 0;
  background-color: #fbfbfd;
  min-height: 100vh;
}

/* 卡片样式 - 苹果风格 */
:deep(.el-card) {
  border-radius: 18px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  background: #ffffff;
  border: none;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  margin-bottom: 24px;
}

:deep(.el-card:hover) {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
}

:deep(.el-card__header) {
  background: transparent;
  color: #1d1d1f;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
  padding: 24px 32px;
  font-weight: 600;
  font-size: 21px;
  letter-spacing: -0.02em;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.content-wrapper {
  padding: 32px;
}

/* 信息卡片容器 - 网格布局 */
.info-cards-container {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 24px;
  margin-bottom: 32px;
}

/* 信息卡片 - 极简设计 */
.info-card {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 32px;
  border-radius: 18px;
  background: #ffffff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
  flex: 1;
  min-width: 0;
}

.info-card:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  transform: translateY(-4px);
}

.info-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  border-radius: 18px 18px 0 0;
}

.info-card.uptime::before {
  background: linear-gradient(90deg, #0071e3, #005bb5);
}

.info-card.name::before {
  background: linear-gradient(90deg, #ff2d55, #d41a4a);
}

.info-card.start-time::before {
  background: linear-gradient(90deg, #34c759, #28a745);
}

.info-card.health::before {
  background: linear-gradient(90deg, #ff9500, #ff7f00);
}

.info-card.health.UP::before {
  background: linear-gradient(90deg, #34c759, #28a745);
}

.info-card.health.DOWN::before {
  background: linear-gradient(90deg, #ff2d55, #d41a4a);
}

.info-card .icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  flex-shrink: 0;
}

.info-card.uptime .icon {
  background: rgba(0, 113, 227, 0.1);
  color: #0071e3;
}

.info-card.name .icon {
  background: rgba(255, 45, 85, 0.1);
  color: #ff2d55;
}

.info-card.start-time .icon {
  background: rgba(52, 199, 89, 0.1);
  color: #34c759;
}

.info-card.health .icon {
  background: rgba(52, 199, 89, 0.1);
  color: #34c759;
}

.info-card.health.DOWN::before {
  background: linear-gradient(90deg, #ff3b30, #e6352b);
}

.info-card.health.DOWN .icon {
  background: rgba(255, 59, 48, 0.1);
  color: #ff3b30;
}

.info-card.health .value span {
  display: inline-flex;
  align-items: center;
  gap: 12px;
}

.info-card.health .value .UP {
  color: #34c759;
  font-weight: 600;
}

.info-card.health .value .DOWN {
  color: #ff3b30;
  font-weight: 600;
}

.port-info {
  font-size: 13px;
  color: #86868b;
  font-weight: 400;
  margin-left: 8px;
}

.info-card .content {
  flex: 1;
}

.info-card .label {
  font-size: 13px;
  font-weight: 600;
  color: #86868b;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 8px;
}

.info-card .value {
  font-size: 28px;
  font-weight: 600;
  color: #1d1d1f;
  line-height: 1.3;
  letter-spacing: -0.02em;
}

.short-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}

/* 区域卡片 */
.section-card {
  margin-top: 32px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #1d1d1f;
  font-weight: 600;
  font-size: 17px;
}

/* 参数列表 */
.args-list {
  max-height: 500px;
  overflow-y: auto;
  padding: 8px 0;
}

.arg-item {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 16px 20px;
  margin: 8px 0;
  background: #fbfbfd;
  border-radius: 12px;
  transition: all 0.3s ease;
  border: 1px solid transparent;
}

.arg-item:hover {
  background: #f5f5f7;
  border-color: rgba(0, 0, 0, 0.05);
}

.arg-number {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 24px;
  padding: 0 8px;
  background: #f5f5f7;
  color: #86868b;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;
}

.arg-text {
  flex: 1;
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Fira Code', monospace;
  font-size: 13px;
  color: #333333;
  line-height: 1.6;
  word-break: break-all;
}

.empty-data {
  text-align: center;
  padding: 60px 20px;
  color: #86868b;
  font-size: 14px;
}

/* 类路径内容 */
.classpath-content {
  position: relative;
}

.classpath-textarea {
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Fira Code', monospace;
  font-size: 13px;
}

.classpath-textarea :deep(.el-textarea__inner) {
  border-radius: 12px;
  border: 1px solid #d2d2d7;
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Fira Code', monospace;
  font-size: 13px;
  background: #fbfbfd;
  resize: vertical;
  transition: all 0.3s ease;
}

.classpath-textarea :deep(.el-textarea__inner:hover) {
  border-color: #86868b;
}

.classpath-textarea :deep(.el-textarea__inner:focus) {
  border-color: #0071e3;
  box-shadow: 0 0 0 4px rgba(0, 113, 227, 0.1);
}

.classpath-stats {
  margin-top: 16px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.classpath-stats :deep(.el-tag) {
  border-radius: 8px;
  padding: 6px 14px;
  font-weight: 500;
  font-size: 12px;
  border: none;
  background: #f5f5f7;
  color: #1d1d1f;
}

.classpath-stats :deep(.el-tag.el-tag--success) {
  background: rgba(52, 199, 89, 0.1);
  color: #34c759;
}

.classpath-stats :deep(.el-tag.el-tag--primary) {
  background: rgba(0, 113, 227, 0.1);
  color: #0071e3;
}

/* 描述列表美化 */
:deep(.el-descriptions) {
  margin-top: 8px;
}

:deep(.el-descriptions__label) {
  background: #fbfbfd;
  font-weight: 600;
  font-size: 13px;
  color: #86868b;
  border-radius: 8px;
  width: 140px;
  padding: 12px 16px;
}

:deep(.el-descriptions__content) {
  border-radius: 8px;
  font-size: 13px;
  color: #1d1d1f;
  padding: 12px 16px;
  font-weight: 500;
}

:deep(.el-descriptions__body) {
  margin-top: 12px;
}

/* 按钮美化 - 现代化圆角渐变样式 */
:deep(.el-button) {
  border-radius: 12px;
  font-weight: 500;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

:deep(.el-button--primary) {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.2);
  color: #ffffff;
}

:deep(.el-button--primary:hover) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

:deep(.el-button--small) {
  padding: 8px 16px;
  font-size: 13px;
}

/* Loading 美化 */
:deep(.el-loading-spinner) {
  color: #0071e3;
}

:deep(.el-loading-spinner .path) {
  stroke: #0071e3;
}

:deep(.el-loading-spinner .el-loading-text) {
  color: #86868b;
  font-size: 13px;
  margin-top: 12px;
}

/* 标签美化 */
:deep(.el-tag) {
  border-radius: 6px;
  padding: 4px 10px;
  font-weight: 500;
  font-size: 11px;
  border: none;
  background: #f5f5f7;
  color: #1d1d1f;
}

/* 滚动条美化 - 苹果风格 */
::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: #d2d2d7;
  border-radius: 4px;
}

::-webkit-scrollbar-thumb:hover {
  background: #86868b;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .monitoring-view {
    padding: 0;
  }
  
  .content-wrapper {
    padding: 20px;
  }
  
  .info-cards-container {
    grid-template-columns: 1fr;
    gap: 16px;
  }
  
  .info-card {
    flex-direction: column;
    text-align: center;
    padding: 24px;
  }
  
  .info-card .icon {
    width: 48px;
    height: 48px;
    font-size: 24px;
  }
  
  .info-card .value {
    font-size: 24px;
  }
  
  .section-header {
    font-size: 15px;
  }
  
  /* 内存和图表响应式 */
  .memory-details {
    grid-template-columns: 1fr;
  }
  
  .system-detail-grid {
    grid-template-columns: 1fr;
  }
  
  .line-chart {
    height: 280px;
  }
  
  .chart-legend {
    flex-wrap: wrap;
    gap: 12px;
  }
  
  .x-axis-label {
    font-size: 10px;
  }

}

/* 动画效果 */
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.info-card {
  animation: fadeInUp 0.6s cubic-bezier(0.4, 0, 0.2, 1);
}

.info-card:nth-child(1) { animation-delay: 0.1s; }
.info-card:nth-child(2) { animation-delay: 0.2s; }
.info-card:nth-child(3) { animation-delay: 0.3s; }

.section-card {
  animation: fadeInUp 0.6s cubic-bezier(0.4, 0, 0.2, 1) 0.4s both;
}

/* 标题图标 */
.card-header :deep(.el-icon) {
  margin-right: 8px;
  font-size: 20px;
  vertical-align: -3px;
}

.section-header :deep(.el-icon) {
  font-size: 18px;
  color: #86868b;
}

/* 健康状态动画 */
@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.7;
  }
}

.info-card.health .icon {
  animation: pulse 2s ease-in-out infinite;
}

/* 柱状图动画 */
@keyframes growUp {
  from {
    transform: scaleY(0);
    opacity: 0;
  }
  to {
    transform: scaleY(1);
    opacity: 1;
  }
}

.bar {
  animation: growUp 0.6s cubic-bezier(0.4, 0, 0.2, 1) backwards;
}

.bar-group:nth-child(1) .bar { animation-delay: 0.05s; }
.bar-group:nth-child(2) .bar { animation-delay: 0.1s; }
.bar-group:nth-child(3) .bar { animation-delay: 0.15s; }
.bar-group:nth-child(4) .bar { animation-delay: 0.2s; }
.bar-group:nth-child(5) .bar { animation-delay: 0.25s; }
.bar-group:nth-child(6) .bar { animation-delay: 0.3s; }
.bar-group:nth-child(7) .bar { animation-delay: 0.35s; }
.bar-group:nth-child(8) .bar { animation-delay: 0.4s; }
.bar-group:nth-child(9) .bar { animation-delay: 0.45s; }
.bar-group:nth-child(10) .bar { animation-delay: 0.5s; }

/* 系统属性网格布局 */
.properties-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  padding: 8px 0;
}

.property-item {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 20px;
  background: #fbfbfd;
  border-radius: 12px;
  transition: all 0.3s ease;
  border: 1px solid transparent;
}

.property-item:hover {
  background: #f5f5f7;
  border-color: rgba(0, 0, 0, 0.05);
  transform: translateY(-2px);
}

.property-item.full-width {
  grid-column: 1 / -1;
}

.property-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  flex-shrink: 0;
}


.property-icon.cpu {
  background: rgba(255, 45, 85, 0.1);
  color: #ff2d55;
}

.property-icon.java {
  background: rgba(52, 199, 89, 0.1);
  color: #34c759;
}

.property-icon.vendor {
  background: rgba(175, 82, 222, 0.1);
  color: #af52de;
}

.property-icon.encoding {
  background: rgba(255, 149, 0, 0.1);
  color: #ff9500;
}

.property-icon.user {
  background: rgba(50, 173, 230, 0.1);
  color: #32ade6;
}

.property-icon.home {
  background: rgba(142, 142, 147, 0.1);
  color: #8e8e93;
}

.property-icon.dir {
  background: rgba(94, 92, 230, 0.1);
  color: #5e5ce6;
}

.property-icon.jhome {
  background: rgba(142, 84, 175, 0.1);
  color: #8e54af;
}

.property-icon.platform {
  background: rgba(0, 113, 227, 0.1);
  color: #0071e3;
}

.property-content {
  flex: 1;
  min-width: 0;
}

.property-label {
  font-size: 12px;
  font-weight: 600;
  color: #86868b;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 8px;
}

.property-value {
  font-size: 15px;
  font-weight: 500;
  color: #1d1d1f;
  line-height: 1.4;
  word-break: break-word;
}

.long-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 内存使用概览 */
.memory-overview {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.memory-stat {
  text-align: center;
  padding: 20px;
  background: #fbfbfd;
  border-radius: 12px;
}

.memory-stat .stat-label {
  font-size: 13px;
  font-weight: 600;
  color: #86868b;
  margin-bottom: 8px;
}

.memory-stat .stat-value {
  font-size: 36px;
  font-weight: 700;
  color: #1d1d1f;
}

.memory-stat .stat-value.normal {
  color: #34c759;
}

.memory-stat .stat-value.warning {
  color: #ff9500;
}

.memory-stat .stat-value.critical {
  color: #ff3b30;
}

.memory-details {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 20px;
}

.memory-section {
  background: #ffffff;
}

.memory-title {
  font-size: 15px;
  font-weight: 600;
  color: #1d1d1f;
  margin: 0 0 16px 0;
  padding-left: 8px;
  border-left: 4px solid #0071e3;
}

/* 图表容器 */
.chart-container {
  padding: 16px 0;
}

.trend-chart {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.chart-legend {
  display: flex;
  justify-content: center;
  gap: 24px;
  padding: 12px;
  background: #fbfbfd;
  border-radius: 8px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #1d1d1f;
}

.legend-color {
  width: 16px;
  height: 16px;
  border-radius: 4px;
  display: inline-block;
}

.legend-color.heap {
  background: linear-gradient(135deg, #667eea, #764ba2);
}

.legend-color.non-heap {
  background: linear-gradient(135deg, #f093fb, #f5576c);
}

.legend-color.percent {
  background: linear-gradient(135deg, #4facfe, #00f2fe);
}

/* 图例线条样式 */
.legend-line {
  width: 24px;
  height: 3px;
  border-radius: 2px;
  display: inline-block;
  margin-right: 8px;
}

.legend-line.heap {
  background: linear-gradient(90deg, #667eea, #764ba2);
}

.legend-line.non-heap {
  background: linear-gradient(90deg, #f093fb, #f5576c);
}

.legend-line.percent {
  background: linear-gradient(90deg, #4facfe, #00f2fe);
}

/* 折线图容器 */
.line-chart {
  width: 100%;
  height: 320px;
  background: linear-gradient(to bottom, #fafafa, #ffffff);
  border-radius: 12px;
  border: 1px solid #e8e8ed;
  overflow: hidden;
}

.line-chart svg {
  width: 100%;
  height: 100%;
}

/* SVG 渐变定义 */
.line-chart svg defs {
  display: block;
}

.gradient-defs {
  position: absolute;
  width: 0;
  height: 0;
  overflow: hidden;
}

/* 网格线 */
.grid-lines line {
  stroke-dasharray: 4 4;
}

/* 折线路径 */
.line-path {
  transition: all 0.3s ease;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.1));
  stroke-dasharray: 1000;
  stroke-dashoffset: 1000;
  animation: drawLine 1.5s cubic-bezier(0.4, 0, 0.2, 1) forwards;
}

@keyframes drawLine {
  to {
    stroke-dashoffset: 0;
  }
}

.heap-line {
  animation-delay: 0.2s;
}

.non-heap-line {
  animation-delay: 0.4s;
}

.percent-line {
  animation-delay: 0.6s;
}

/* 数据点动画 */
.data-dot {
  cursor: pointer;
  transition: all 0.2s ease;
  stroke: #ffffff;
  stroke-width: 2;
  opacity: 0;
  animation: fadeInDot 0.5s ease forwards;
}

@keyframes fadeInDot {
  to {
    opacity: 1;
  }
}

.data-dot:nth-child(1) { animation-delay: 0.8s; }
.data-dot:nth-child(2) { animation-delay: 0.9s; }
.data-dot:nth-child(3) { animation-delay: 1.0s; }
.data-dot:nth-child(4) { animation-delay: 1.1s; }
.data-dot:nth-child(5) { animation-delay: 1.2s; }
.data-dot:nth-child(6) { animation-delay: 1.3s; }
.data-dot:nth-child(7) { animation-delay: 1.4s; }
.data-dot:nth-child(8) { animation-delay: 1.5s; }
.data-dot:nth-child(9) { animation-delay: 1.6s; }
.data-dot:nth-child(10) { animation-delay: 1.7s; }

.data-dot:hover {
  r: 6;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.2));
}

.heap-dot {
  fill: #667eea;
}

.non-heap-dot {
  fill: #f093fb;
}

.percent-dot {
  fill: #4facfe;
}

/* X 轴标签 */
.x-axis-label {
  font-size: 11px;
  fill: #86868b;
  font-weight: 500;
}

/* 柱状图样式 */
.bar-chart {
  display: flex;
  justify-content: space-around;
  align-items: flex-end;
  height: 240px;
  padding: 20px 10px;
  background: linear-gradient(to bottom, #fafafa, #ffffff);
  border-radius: 12px;
  border: 1px solid #e8e8ed;
}

.bar-group {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  flex: 1;
  max-width: 80px;
}

.bars {
  display: flex;
  gap: 4px;
  align-items: flex-end;
  height: 180px;
  position: relative;
}

.bar {
  width: 12px;
  border-radius: 4px 4px 0 0;
  transition: all 0.3s ease;
  position: relative;
  cursor: pointer;
}

.bar:hover {
  opacity: 0.8;
  transform: scaleY(1.02);
}

.heap-bar {
  background: linear-gradient(to top, #667eea, #764ba2);
}

.non-heap-bar {
  background: linear-gradient(to top, #f093fb, #f5576c);
}

.bar-label {
  font-size: 11px;
  color: #86868b;
  font-weight: 500;
  white-space: nowrap;
}

.percent-label {
  font-size: 10px;
  color: #0071e3;
  font-weight: 600;
  margin-top: -4px;
}

/* 系统详细信息网格 */
.system-detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 24px;
  padding: 8px 0;
}

.detail-section {
  background: #fbfbfd;
  padding: 20px;
  border-radius: 12px;
}

.detail-title {
  font-size: 15px;
  font-weight: 600;
  color: #1d1d1f;
  margin: 0 0 16px 0;
  padding-bottom: 12px;
  border-bottom: 2px solid #e8e8ed;
}

.detail-section :deep(.el-descriptions) {
  margin-top: 0;
}

.detail-section :deep(.el-descriptions__label) {
  width: 100px;
  font-size: 12px;
}

.detail-section :deep(.el-descriptions__content) {
  font-size: 13px;
}

.detail-section .long-label {
  width: auto;
  min-width: 100px;
}

.detail-section .long-value {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 300px;
}

.action-btn {
  border-radius: 12px;
  padding: 14px 28px;
  font-weight: 500;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.2);
}

.action-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.3);
}

/* 复制按钮 */
.copy-btn {
  border-radius: 12px;
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 500;
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
  border: none;
  color: #ffffff;
  box-shadow: 0 2px 8px rgba(79, 172, 254, 0.2);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.copy-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(79, 172, 254, 0.3);
}

/* 刷新按钮 */
.refresh-btn {
  border-radius: 12px;
  padding: 12px 24px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 8px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.2);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.refresh-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.3);
}
</style>

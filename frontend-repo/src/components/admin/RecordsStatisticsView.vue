<template>
  <div class="records-statistics-view">
    <el-card>
      <template #header>
        <div class="card-header">
          <span> 病案扫描数据统计</span>
          <!-- <el-button type="primary" @click="refreshData" :loading="loading" size="small">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button> -->
        </div>
      </template>

      <div v-loading="loading" class="content-wrapper">
        <!-- 顶部统计卡片 -->
        <div class="info-cards-container">
          <!-- 总记录数卡片 -->
          <div class="info-card total-records">
            <div class="icon">
              <el-icon>
                <Grid />
              </el-icon>
            </div>
            <div class="content">
              <div class="label">总记录数</div>
              <div class="value">{{ formatNumber(summaryData.total?.totalRecords) }}</div>
            </div>
          </div>

          <!-- 总页数卡片 -->
          <div class="info-card total-pages">
            <div class="icon">
              <el-icon><Tickets /></el-icon>
            </div>
            <div class="content">
              <div class="label">项目期间总页数</div>
              <div class="value">{{ formatNumber(summaryData.total?.totalPages) }}</div>
            </div>
          </div>

          <!-- 唯一病案号数卡片 -->
          <div class="info-card unique-bah">
            <div class="icon">
              <el-icon>
                <Document />
              </el-icon>
            </div>
            <div class="content">
              <div class="label">项目期间扫描病案数</div>
              <!-- 过滤掉 null 值后计算唯一病案号数量 -->
              <div class="value">{{ formatNumber(summaryData.uniqueBAHCount) }}</div>
            </div>
          </div>

          <!-- 概览卡片 -->
          <div class="info-card overview">
            <div class="icon">
              <el-icon><TrendCharts /></el-icon>
            </div>
            <div class="content">
              <div class="label">统计时间范围</div>
              <div class="value small-text">
                {{ dateRange.start }} - {{ dateRange.end }}
              </div>
            </div>
          </div>
        </div>

        <!-- 日期趋势图表 -->
        <el-card class="section-card" shadow="hover">
          <template #header>
            <div class="section-header">
              <el-icon><TrendCharts /></el-icon>
              <span>每日扫描记录</span>
              <el-tag size="small" type="success">最近 {{ sortedDateData.length }} 天</el-tag>
            </div>
          </template>
          
          <div ref="chartContainerRef" class="chart-container">
            <svg :width="svgWidth" :height="320" class="trend-chart" preserveAspectRatio="xMidYMid meet">
              <!-- 网格线 -->
              <g class="grid-lines">
                <line 
                  v-for="i in 5" 
                  :key="'h' + i"
                  :x1="paddingLeft" 
                  :y1="paddingTop + (i - 1) * chartHeight / 4"
                  :x2="svgWidth - paddingRight" 
                  :y2="paddingTop + (i - 1) * chartHeight / 4"
                  stroke="#e0e0e0" 
                  stroke-width="1"
                  opacity="0.3"
                />
              </g>

              <!-- Y 轴标签 -->
              <g class="y-axis-labels">
                <text 
                  v-for="i in 5" 
                  :key="'yl' + i"
                  :x="paddingLeft - 10" 
                  :y="paddingTop + (i - 1) * chartHeight / 4 + 4"
                  text-anchor="end"
                  class="axis-label"
                >
                  {{ Math.round(maxRecordCount * (1 - (i - 1) / 4)) }}
                </text>
              </g>

              <!-- 柱状图 -->
              <g class="bar-series" v-if="showBarSeries">
                <rect
                  v-for="(item, index) in sortedDateData"
                  :key="'bar' + index"
                  :x="getBarX(index)"
                  :y="getBarY(item.recordCount || 0)"
                  :width="barWidth"
                  :height="getBarHeight(item.recordCount || 0)"
                  fill="url(#gradientBar)"
                  rx="4"
                  ry="4"
                  class="bar-item"
                >
                  <title>{{ formatDate(item.date) }}: {{ item.recordCount }} 条记录</title>
                </rect>
              </g>

              <!-- 累计记录数折线 -->
              <g class="line-series" v-if="showLineSeries">
                <!-- 填充区域 -->
                <path
                  v-if="cumulativeRecordPoints.length > 1"
                  :d="getAreaPath(cumulativeRecordPoints)"
                  fill="url(#gradientCumulative)"
                  opacity="0.15"
                  stroke="url(#gradientCumulative)"
                  stroke-width="1"
                />
                
                <!-- 折线 -->
                <polyline
                  v-if="cumulativeRecordPoints.length > 1"
                  :points="cumulativeRecordPoints"
                  fill="none"
                  stroke="url(#gradientCumulative)"
                  stroke-width="3"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />

                <!-- 数据点 -->
                <g v-for="(point, index) in cumulativeRecordPoints" :key="'cr' + index">
                  <circle
                    :cx="point.split(',')[0]"
                    :cy="point.split(',')[1]"
                    r="4"
                    fill="#ff2d55"
                    stroke="#ffffff"
                    stroke-width="2"
                    class="data-point"
                  >
                    <title>{{ formatDate(sortedDateData[index]?.date) }}: 累计 {{ cumulativeRecords[index] }} 条记录</title>
                  </circle>
                </g>
              </g>

              <!-- X 轴日期标签 -->
              <g class="x-axis-labels">
                <text 
                  v-for="(item, index) in displayDateLabels" 
                  :key="'xl' + index"
                  :x="paddingLeft + (index * xStep)" 
                  :y="320 - paddingBottom + 20"
                  text-anchor="middle"
                  class="axis-label date-label"
                >
                  {{ item }}
                </text>
              </g>

              <!-- 渐变定义 -->
              <defs>
                <linearGradient id="gradientBar" x1="0%" y1="0%" x2="0%" y2="100%">
                  <stop offset="0%" style="stop-color:#0071e3;stop-opacity:1" />
                  <stop offset="100%" style="stop-color:#00c6fb;stop-opacity:1" />
                </linearGradient>
                <linearGradient id="gradientCumulative" x1="0%" y1="0%" x2="100%" y2="0%">
                  <stop offset="0%" style="stop-color:#ff2d55;stop-opacity:1" />
                  <stop offset="100%" style="stop-color:#ff6b8a;stop-opacity:1" />
                </linearGradient>
              </defs>
            </svg>
          </div>

          <!-- 图例 -->
          <div class="chart-legend">
            <div class="legend-item">
              <span class="legend-color bar" style="background: linear-gradient(180deg, #0071e3, #00c6fb);"></span>
              <span class="legend-text">每日记录数</span>
            </div>
            <div
              class="legend-item legend-toggle"
              :class="{ 'legend-inactive': !showLineSeries }"
              @click="showLineSeries = !showLineSeries"
            >
              <span class="legend-color line" style="background: linear-gradient(90deg, #ff2d55, #ff6b8a);"></span>
              <span class="legend-text">累计记录数趋势（点击显示/隐藏）</span>
            </div>
          </div>
        </el-card>

        <!-- 病案明细列表 -->
        <el-card class="section-card" shadow="hover">
          <template #header>
            <div class="section-header">
              <el-icon><Document /></el-icon>
              <span>病案明细</span>
              <el-tag size="small" type="primary">{{ statisticsListData.total || 0 }} 条记录</el-tag>
            </div>
          </template>
          
          <div class="table-container">
            <div class="list-search-bar">
              <el-input
                v-model="listSearchKeyword"
                placeholder="搜索病案号 / 设备ID / 负责人 / 日期 / 类型"
                clearable
                class="search-item keyword"
                @keyup.enter="handleListSearch"
              />
              <el-select
                v-model="listSearchType"
                placeholder="全部类型"
                clearable
                class="search-item type"
                @change="handleListSearch"
              >
                <el-option
                  v-for="item in statisticsTypeOptions"
                  :key="item"
                  :label="item"
                  :value="item"
                />
              </el-select>
              <el-date-picker
                v-model="listSearchDateRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                value-format="YYYY-MM-DD"
                class="search-item date"
              />
              <el-button type="primary" :icon="Search" @click="handleListSearch">搜索</el-button>
              <el-button :icon="Delete" @click="resetListSearch">重置</el-button>
            </div>

            <el-table 
              :data="statisticsListData.list" 
              style="width: 100%"
              v-loading="loading"
              :default-sort="{ prop: 'date', order: descending }"
              empty-text="暂无数据"
            >
              <el-table-column type="index" label="序号" width="60" align="center" />
              
              <el-table-column prop="bah" label="病案号" min-width="120" sortable>
                <template #default="{ row }">
                  <span class="bah-code-link">{{ row.bah || '-' }}</span>
                </template>
              </el-table-column>
              
              <el-table-column prop="cid" label="扫描设备ID" width="150" sortable align="center" />
              
              <el-table-column prop="openerNo" label="扫描负责人" width="150" sortable>
                <template #default="{ row }">
                  <span>{{ row.openerNo === 'NULL' ? '-' : row.openerNo }}</span>
                </template>
              </el-table-column>
              
              <el-table-column prop="date" label="日期" width="120" sortable>
                <template #default="{ row }">
                  <span>{{ formatDate(row.date) }}</span>
                </template>
              </el-table-column>
              
              <el-table-column prop="type" label="类型" width="100" sortable align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="getTypeTagType(row.type)">
                    {{ row.type || '未知' }}
                  </el-tag>
                </template>
              </el-table-column>
              
              <el-table-column prop="pages" label="页数" width="100" sortable align="center">
                <template #default="{ row }">
                  <span :class="{ 'highlight-pages': row.pages > 50 }">
                    {{ row.pages ?? 0 }}
                  </span>
                </template>
              </el-table-column>
            </el-table>
            
            <!-- 分页控件 -->
            <div class="pagination-wrapper">
              <el-pagination
                v-model:current-page="currentPage"
                v-model:page-size="pageSize"
                :page-sizes="[50, 100, 200, 500]"
                :total="statisticsListData.total"
                layout="total, sizes, prev, pager, next, jumper"
                @size-change="handleSizeChange"
                @current-change="handleCurrentChange"
                class="custom-pagination"
              />
            </div>
          </div>
        </el-card>

      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, nextTick, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { 
  DataAnalysis, 
  Refresh, 
  Document, 
  Tickets, 
  Grid, 
  TrendCharts, 
  PieChart,
  Star,
  Search,
  Delete
} from '@element-plus/icons-vue'
import { getStatisticsSummary, getStatisticsDateSummary, getDashboardData, getStatisticsList } from '@/utils/api'

const loading = ref(false)
const chartContainerRef = ref(null)
const svgWidth = ref(800)

// 统计数据
const summaryData = ref({
  total: {
    totalRecords: null,
    totalPages: null
  },
  uniqueBAHCount: null,
  byType: []
})

// 日期统计数据
const dateSummaryData = ref([])

// 仪表盘数据
const dashboardData = ref({
  overview: {
    totalRecords: null,
    totalPages: null
  },
  recentTrend: [],
  topBAH: [],
  uniqueBAHCount: null
})

// 病案统计列表数据
const statisticsListData = ref({
  total: 0,
  size: 100,
  totalPages: 0,
  page: 1,
  list: []
})

// 分页配置
const currentPage = ref(1)
const pageSize = ref(100)
const listSearchKeyword = ref('')
const listSearchType = ref('')
const listSearchDateRange = ref([])

const statisticsTypeOptions = computed(() => {
  const source = summaryData.value?.byType || []
  return source
    .map(item => item?.type)
    .filter(item => item && item !== 'NULL')
})

// 图表系列显示控制
const showBarSeries = ref(true) // 控制柱状图显示
const showLineSeries = ref(true) // 控制折线图显示

// SVG 图表配置
const svgHeight = 320
const paddingLeft = 60
const paddingRight = 30
const paddingTop = 30
const paddingBottom = 50
const chartHeight = svgHeight - paddingTop - paddingBottom

// 最小显示日期数量（超过此数量启用滚动）
const minDateCountForScroll = 100

// 计算最大记录数用于 Y 轴缩放
const maxRecordCount = computed(() => {
  const records = sortedDateData.value.map(item => item.recordCount || 0)
  const max = Math.max(...records, 100)
  return Math.ceil(max / 100) * 100 // 向上取整到百位
})

// 计算最大累计记录数用于折线图 Y 轴缩放
const maxCumulativeCount = computed(() => {
  const cumulative = calculateCumulativeRecords()
  const max = Math.max(...cumulative, 100)
  return Math.ceil(max / 500) * 500 // 向上取整到 500 位
})

// 排序后的日期数据（按日期升序，自动过滤无日期数据）
const sortedDateData = computed(() => {
  if (!dateSummaryData.value || dateSummaryData.value.length === 0) return []
  
  // 先过滤掉无日期或日期为空的记录
  const validData = dateSummaryData.value.filter(item => {
    return item && item.date && item.date.trim() !== '' && item.date !== 'NULL'
  })
  
  // 然后按日期排序
  return [...validData].sort((a, b) => {
    const dateA = a.date ? new Date(a.date.replace(/\//g, '-')) : new Date(0)
    const dateB = b.date ? new Date(b.date.replace(/\//g, '-')) : new Date(0)
    return dateA - dateB
  })
})

// 显示的日期标签（只显示部分）
const displayDateLabels = computed(() => {
  const data = sortedDateData.value
  if (data.length <= 5) return data.map(item => formatDateShort(item.date))
  
  // 每隔几个显示一个标签
  const step = Math.ceil(data.length / 8)
  const labels = []
  for (let i = 0; i < data.length; i += step) {
    labels[i] = formatDateShort(data[i].date)
  }
  // 确保最后一个标签显示
  labels[data.length - 1] = formatDateShort(data[data.length - 1].date)
  return labels
})

// 计算图表宽度（动态）
const chartWidth = computed(() => {
  return svgWidth.value - paddingLeft - paddingRight
})

// 计算 X 轴步长
const xStep = computed(() => {
  if (sortedDateData.value.length <= 1) return chartWidth.value
  return chartWidth.value / (sortedDateData.value.length - 1)
})

// 更新图表宽度（考虑滚动）
const updateChartWidth = () => {
  if (chartContainerRef.value) {
    const containerWidth = chartContainerRef.value.offsetWidth
    const dataLength = sortedDateData.value.length
    
    // 如果数据量超过 100 个，设置固定宽度以启用滚动
    if (dataLength > minDateCountForScroll) {
      // 每个数据点至少占用 12px（柱子宽度 + 间隙）
      const minDataWidth = dataLength * 12
      svgWidth.value = Math.max(containerWidth, minDataWidth + paddingLeft + paddingRight)
    } else {
      // 否则使用容器宽度的 95%
      svgWidth.value = Math.max(containerWidth * 0.95, 600)
    }
  }
}

// 柱状图配置
const barWidth = computed(() => {
  const dataLength = sortedDateData.value.length
  if (dataLength <= 1) return Math.min(40, chartWidth.value)
  
  // 根据数据量动态调整柱子宽度
  const maxBarWidth = 35
  const minBarWidth = 6
  const gap = 6
  
  // 计算可用的总宽度
  const availableWidth = chartWidth.value
  // 每个柱子占用的宽度（包括间隙）
  const barSlotWidth = availableWidth / dataLength
  
  // 柱子宽度为槽位宽度减去间隙
  const calculatedBarWidth = barSlotWidth - gap
  
  return Math.max(minBarWidth, Math.min(maxBarWidth, calculatedBarWidth))
})

// 获取柱子的 X 坐标
const getBarX = (index) => {
  const centerX = paddingLeft + (index * xStep.value)
  return centerX - barWidth.value / 2
}

// 获取柱子的 Y 坐标（顶部位置）
const getBarY = (value) => {
  if (!value || value <= 0) return paddingTop + chartHeight
  return calculateYPosition(value)
}

// 获取柱子的高度
const getBarHeight = (value) => {
  if (!value || value <= 0) return 0
  const baselineY = paddingTop + chartHeight
  return baselineY - calculateYPosition(value)
}

// 生成记录数折线点
const recordCountPoints = computed(() => {
  if (sortedDateData.value.length === 0) return []
  
  return sortedDateData.value.map((item, index) => {
    const x = paddingLeft + (index * xStep.value)
    const y = calculateYPosition(item.recordCount || 0)
    return `${x},${y}`
  })
})

// 生成总页数折线点
const totalPagesPoints = computed(() => {
  if (sortedDateData.value.length === 0) return []
  
  return sortedDateData.value.map((item, index) => {
    const x = paddingLeft + (index * xStep.value)
    const y = calculatePagesYPosition(item.pages || 0)
    return `${x},${y}`
  })
})

// 计算累计记录数数组
const calculateCumulativeRecords = () => {
  if (!sortedDateData.value || sortedDateData.value.length === 0) return []
  
  const cumulative = []
  let sum = 0
  sortedDateData.value.forEach(item => {
    sum += item.recordCount || 0
    cumulative.push(sum)
  })
  return cumulative
}

/**
 * 累计记录数数组（用于折线图和 tooltip）
 */
const cumulativeRecords = computed(() => {
  return calculateCumulativeRecords()
})

/**
 * 统计时间范围（从日期数据中获取最小和最大日期）
 */
const dateRange = computed(() => {
  if (!sortedDateData.value || sortedDateData.value.length === 0) return { start: '-', end: '-' }
  
  const startDate = sortedDateData.value[0]?.date || '-'
  const endDate = sortedDateData.value[sortedDateData.value.length - 1]?.date || '-'
  
  return {
    start: startDate !== '-' ? formatDate(startDate) : '-',
    end: endDate !== '-' ? formatDate(endDate) : '-'
  }
})

// 生成累计记录数折线点
const cumulativeRecordPoints = computed(() => {
  if (sortedDateData.value.length === 0) return []
  
  const cumulative = calculateCumulativeRecords()
  return sortedDateData.value.map((item, index) => {
    const x = paddingLeft + (index * xStep.value)
    const y = calculateCumulativeYPosition(cumulative[index])
    return `${x},${y}`
  })
})

// 获取类型样式类
const getTypeClass = (type) => {
  const typeMap = {
    '普通': 'type-normal',
    '质控': 'type-quality',
    '高拍': 'type-high',
    'unknown': 'type-unknown'
  }
  return typeMap[type] || 'type-unknown'
}

// 获取类型图标
const getTypeIcon = (type) => {
  const iconMap = {
    '普通': Document,
    '质控': CircleCheck,
    '高拍': Picture,
    'unknown': QuestionFilled
  }
  return iconMap[type] || iconMap['unknown']
}

// 计算百分比
const getPercentage = (value) => {
  if (!value || !summaryData.value.total?.totalRecords) return 0
  return Math.min((value / summaryData.value.total.totalRecords) * 100, 100).toFixed(1)
}

// 计算 Top 百分比
const getTopPercentage = (value) => {
  if (!value) return 0
  const maxRecord = Math.max(...(dashboardData.value.topBAH?.map(item => item.recordCount) || [1]))
  return Math.min((value / maxRecord) * 100, 100).toFixed(1)
}

// 获取排名颜色
const getRankColor = (index) => {
  const colors = [
    'linear-gradient(90deg, #ffd700, #ffed4e)',  // 金牌
    'linear-gradient(90deg, #c0c0c0, #e8e8e8)',  // 银牌
    'linear-gradient(90deg, #cd7f32, #e09856)',  // 铜牌
    'linear-gradient(90deg, #0071e3, #00c6fb)',  // 蓝渐变色
  ]
  return colors[index] || colors[3]
}

// 格式化数字（添加千分位分隔符）
const formatNumber = (num) => {
  if (num === null || num === undefined || num === '') return '-'
  return Number(num).toLocaleString('zh-CN')
}

// 格式化日期
const formatDate = (dateStr) => {
  if (!dateStr) return '无日期'
  return dateStr.replace(/\//g, '-')
}

// 格式化简短日期
const formatDateShort = (dateStr) => {
  if (!dateStr) return ''
  const parts = dateStr.split('/')
  if (parts.length >= 2) {
    return `${parts[1]}/${parts[2]}`
  }
  return dateStr
}

// 计算 Y 轴位置（用于记录数柱状图）
const calculateYPosition = (value) => {
  if (!value || value <= 0) return paddingTop + chartHeight
  const normalizedValue = Math.min(value / maxRecordCount.value, 1)
  return paddingTop + chartHeight - (normalizedValue * chartHeight)
}

// 计算 Y 轴位置（用于页数折线图 - 保留但不再使用）
const calculatePagesYPosition = (value) => {
  if (!value || value <= 0) return paddingTop + chartHeight
  const normalizedValue = Math.min(value / maxPagesCount.value, 1)
  return paddingTop + chartHeight - (normalizedValue * chartHeight)
}

// 计算 Y 轴位置（用于累计记录数折线图）
const calculateCumulativeYPosition = (value) => {
  if (!value || value <= 0) return paddingTop + chartHeight
  const normalizedValue = Math.min(value / maxCumulativeCount.value, 1)
  return paddingTop + chartHeight - (normalizedValue * chartHeight)
}

// 生成面积图路径
const getAreaPath = (points) => {
  if (points.length === 0) return ''
  
  const firstX = points[0].split(',')[0]
  const lastX = points[points.length - 1].split(',')[0]
  const baselineY = paddingTop + chartHeight
  
  return `${points.join(' ')} L ${lastX} ${baselineY} L ${firstX} ${baselineY} Z`
}

// 获取病案统计列表
const loadStatisticsList = async () => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      size: pageSize.value
    }
    if (listSearchKeyword.value.trim()) {
      params.keyword = listSearchKeyword.value.trim()
    }
    if (listSearchType.value) {
      params.type = listSearchType.value
    }
    if (Array.isArray(listSearchDateRange.value) && listSearchDateRange.value.length === 2) {
      params.startDate = listSearchDateRange.value[0]
      params.endDate = listSearchDateRange.value[1]
    }

    const response = await getStatisticsList(params)
    
    if (response.data && response.data.code === 200) {
      statisticsListData.value = response.data.data || {}
      // 过滤掉 null 值
      if (statisticsListData.value.list) {
        statisticsListData.value.list = statisticsListData.value.list.filter(item => item !== null)
      }
    } else if (response.data) {
      statisticsListData.value = response.data
      if (statisticsListData.value.list) {
        statisticsListData.value.list = statisticsListData.value.list.filter(item => item !== null)
      }
    }
  } catch (error) {
    console.error('加载病案列表失败:', error)
    ElMessage.error('加载病案列表失败：' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

const handleListSearch = () => {
  currentPage.value = 1
  loadStatisticsList()
}

const resetListSearch = () => {
  listSearchKeyword.value = ''
  listSearchType.value = ''
  listSearchDateRange.value = []
  currentPage.value = 1
  loadStatisticsList()
}

// 处理分页大小变化
const handleSizeChange = (newSize) => {
  pageSize.value = newSize
  currentPage.value = 1
  loadStatisticsList()
}

// 处理当前页码变化
const handleCurrentChange = (newPage) => {
  currentPage.value = newPage
  loadStatisticsList()
}

// 获取类型标签样式
const getTypeTagType = (type) => {
  const typeMap = {
    '普通': '',
    '质控': 'success',
    '高拍': 'warning',
    'unknown': 'info'
  }
  return typeMap[type] || 'info'
}

// 加载统计数据
const loadSummary = async () => {
  try {
    const response = await getStatisticsSummary()
    if (response.data && response.data.code === 200) {
      summaryData.value = response.data.data || {}
    } else if (response.data) {
      summaryData.value = response.data
    }
  } catch (error) {
    console.error('加载统计概览失败:', error)
    ElMessage.error('加载统计概览失败：' + (error.message || '未知错误'))
  }
}

// 加载日期统计数据
const loadDateSummary = async () => {
  try {
    const response = await getStatisticsDateSummary()
    if (response.data && response.data.code === 200) {
      dateSummaryData.value = response.data.data || []
    } else if (response.data) {
      dateSummaryData.value = response.data
    }
  } catch (error) {
    console.error('加载日期统计失败:', error)
    ElMessage.error('加载日期统计失败：' + (error.message || '未知错误'))
  }
}

// 加载仪表盘数据
const loadDashboard = async () => {
  try {
    const response = await getDashboardData()
    if (response.data && response.data.code === 200) {
      dashboardData.value = response.data.data || {}
    } else if (response.data) {
      dashboardData.value = response.data
    }
  } catch (error) {
    console.error('加载仪表盘数据失败:', error)
    ElMessage.error('加载仪表盘数据失败：' + (error.message || '未知错误'))
  }
}

// 刷新数据
const refreshData = () => {
  loadSummary()
  loadDateSummary()
  loadDashboard()
  loadStatisticsList()
}

// 初始化加载
onMounted(() => {
  refreshData()
  // 初始更新图表宽度
  nextTick(() => {
    updateChartWidth()
  })
  // 监听窗口大小变化
  window.addEventListener('resize', updateChartWidth)
})

// 组件卸载时移除事件监听
onUnmounted(() => {
  window.removeEventListener('resize', updateChartWidth)
})
</script>

<style scoped>
.records-statistics-view {
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
  /* transform: translateY(-2px); */
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

.info-card.total-records::before {
  background: linear-gradient(90deg, #0071e3, #005bb5);
}

.info-card.total-pages::before {
  background: linear-gradient(90deg, #ff2d55, #d41a4a);
}

.info-card.unique-bah::before {
  background: linear-gradient(90deg, #34c759, #28a745);
}

.info-card.overview::before {
  background: linear-gradient(90deg, #ff9500, #ff7f00);
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

.info-card.total-records .icon {
  background: linear-gradient(135deg, #0071e3 0%, #005bb5 100%);
  color: #ffffff;
}

.info-card.total-pages .icon {
  background: linear-gradient(135deg, #ff2d55 0%, #d41a4a 100%);
  color: #ffffff;
}

.info-card.unique-bah .icon {
  background: linear-gradient(135deg, #34c759 0%, #28a745 100%);
  color: #ffffff;
}

.info-card.overview .icon {
  background: linear-gradient(135deg, #ff9500 0%, #ff7f00 100%);
  color: #ffffff;
}

.info-card .content {
  flex: 1;
  min-width: 0;
}

.info-card .label {
  font-size: 12px;
  color: #86868b;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 8px;
  font-weight: 500;
}

.info-card .value {
  font-size: 32px;
  font-weight: 700;
  color: #1d1d1f;
  line-height: 1.2;
  letter-spacing: -0.02em;
}

.info-card .value.small-text {
  font-size: 20px;
  color: #1d1d1f;
}

/* Section Card */
.section-card {
  margin-bottom: 24px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.section-header .el-icon {
  font-size: 20px;
  color: #0071e3;
}

/* 类型分布 */
.type-distribution {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.type-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  border-radius: 12px;
  background: #fafafa;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.type-item:hover {
  background: #f0f0f0;
  transform: translateX(4px);
}

.type-item.type-normal {
  border-left: 4px solid #0071e3;
}

.type-item.type-quality {
  border-left: 4px solid #34c759;
}

.type-item.type-high {
  border-left: 4px solid #ff9500;
}

.type-item.type-unknown {
  border-left: 4px solid #8e8e93;
}

.type-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  flex-shrink: 0;
}

.type-normal .type-icon {
  background: linear-gradient(135deg, #0071e3, #005bb5);
  color: #ffffff;
}

.type-quality .type-icon {
  background: linear-gradient(135deg, #34c759, #28a745);
  color: #ffffff;
}

.type-high .type-icon {
  background: linear-gradient(135deg, #ff9500, #ff7f00);
  color: #ffffff;
}

.type-unknown .type-icon {
  background: linear-gradient(135deg, #8e8e93, #636366);
  color: #ffffff;
}

.type-info {
  flex: 1;
  min-width: 0;
}

.type-name {
  font-size: 16px;
  font-weight: 600;
  color: #1d1d1f;
  margin-bottom: 8px;
}

.type-stats {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.stat-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background: #ffffff;
  border-radius: 8px;
  font-size: 13px;
  color: #1d1d1f;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.stat-badge .el-icon {
  font-size: 14px;
}

.stat-badge.pages {
  background: #f5f5f7;
  color: #86868b;
}

.type-progress {
  width: 200px;
  height: 8px;
  background: #e0e0e0;
  border-radius: 4px;
  overflow: hidden;
  flex-shrink: 0;
}

.progress-bar {
  height: 100%;
  background: linear-gradient(90deg, #0071e3, #00c6fb);
  border-radius: 4px;
  transition: width 0.5s cubic-bezier(0.4, 0, 0.2, 1);
}

.type-normal .progress-bar {
  background: linear-gradient(90deg, #0071e3, #005bb5);
}

.type-quality .progress-bar {
  background: linear-gradient(90deg, #34c759, #28a745);
}

.type-high .progress-bar {
  background: linear-gradient(90deg, #ff9500, #ff7f00);
}

/* 图表容器 */
.chart-container {
  width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  margin-bottom: 16px;
  position: relative;
  /* 自定义滚动条样式 */
  scrollbar-width: thin;
  scrollbar-color: rgba(0, 0, 0, 0.2) transparent;
}

/* Webkit 浏览器滚动条 */
.chart-container::-webkit-scrollbar {
  height: 8px;
}

.chart-container::-webkit-scrollbar-track {
  background: transparent;
}

.chart-container::-webkit-scrollbar-thumb {
  background: linear-gradient(90deg, #0071e3, #00c6fb);
  border-radius: 4px;
  opacity: 0.8;
}

.chart-container::-webkit-scrollbar-thumb:hover {
  background: linear-gradient(90deg, #005bb5, #00a8e8);
}

.trend-chart {
  display: block;
  max-width: 100%;
  height: auto;
}

.axis-label {
  font-size: 12px;
  fill: #86868b;
}

.date-label {
  font-size: 11px;
  fill: #636366;
}

.data-point {
  cursor: pointer;
  transition: all 0.2s ease;
}

.data-point:hover {
  r: 6;
  fill: #00c6fb;
}

/* 柱状图样式 */
.bar-item {
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.bar-item:hover {
  opacity: 0.8;
  transform: translateY(-2px);
}

/* 图表图例 */
.chart-legend {
  display: flex;
  gap: 24px;
  justify-content: center;
  padding-top: 16px;
  border-top: 1px solid rgba(0, 0, 0, 0.05);
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.legend-item.legend-toggle {
  cursor: pointer;
  user-select: none;
}

.legend-item.legend-toggle:hover {
  opacity: 0.8;
}

.legend-item.legend-inactive .legend-color.line {
  background: #d3d3d3;
}

.legend-item.legend-inactive .legend-text {
  color: #b0b0b0;
}

.legend-color {
  width: 20px;
  height: 20px;
  border-radius: 4px;
  flex-shrink: 0;
}

.legend-color.bar {
  background: linear-gradient(180deg, #0071e3, #00c6fb);
}

.legend-color.line {
  height: 3px;
  border-radius: 2px;
}

.legend-text {
  font-size: 13px;
  color: #636366;
  font-weight: 500;
}

/* 表格容器 */
.table-container {
  width: 100%;
  overflow-x: auto;
}

.list-search-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
  align-items: center;
}

.search-item.keyword {
  width: 320px;
}

.search-item.type {
  width: 160px;
}

.search-item.date {
  width: 320px;
}

.bah-code-link {
  color: #0071e3;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.bah-code-link:hover {
  color: #005bb5;
  text-decoration: underline;
}

.highlight-pages {
  /* color: #ff9500; */
  /* font-weight: 600; */
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  padding-top: 24px;
  margin-top: 16px;
  border-top: 1px solid rgba(0, 0, 0, 0.05);
}

.custom-pagination {
  padding: 16px 0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .content-wrapper {
    padding: 20px;
  }

  .info-cards-container {
    grid-template-columns: 1fr;
  }

  .info-card {
    padding: 24px;
  }

  .info-card .value {
    font-size: 28px;
  }

  .type-item,
  .top-item {
    flex-wrap: wrap;
  }

  .type-progress,
  .bah-bar {
    width: 100%;
    order: 3;
  }

  .chart-container {
    overflow-x: scroll;
  }

  .list-search-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .search-item.keyword,
  .search-item.type,
  .search-item.date {
    width: 100%;
  }
  
  /* 表格移动端适配 */
  :deep(.el-table) {
    font-size: 12px;
  }
  
  :deep(.el-table th) {
    font-size: 12px;
    padding: 8px 4px;
  }
  
  :deep(.el-table td) {
    padding: 8px 4px;
  }
  
  .pagination-wrapper {
    :deep(.el-pagination) {
      justify-content: center;
      
      .el-pagination__total,
      .el-pagination__sizes,
      .el-pager {
        display: none;
      }
      
      .btn-prev,
      .btn-next {
        display: inline-block;
      }
    }
  }
}
</style>

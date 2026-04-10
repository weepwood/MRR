<script setup lang="ts">
import { ArrowRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getDashboardData, getStatisticsDateSummary, getStatisticsSummary } from '@/api/modules/statistics'

defineOptions({ name: 'StatisticsPage' })

const router = useRouter()
const loading = ref(false)
const summaryData = ref<any>({ total: {}, byType: [] })
const dashboardData = ref<any>({ recentTrend: [], topBAH: [] })
const dateSummaryData = ref<any[]>([])

const summaryCards = computed(() => [
  { label: '总记录数', value: summaryData.value.total?.totalRecords || 0, note: '统计表内累计记录' },
  { label: '总页数', value: summaryData.value.total?.totalPages || 0, note: '累计扫描页数' },
  { label: '唯一病案号', value: summaryData.value.uniqueBAHCount || 0, note: '病案维度的归档数量' },
  { label: '近 30 天趋势点', value: dashboardData.value.recentTrend?.length || 0, note: '仪表盘趋势数据量' },
])

const topBahList = computed(() => Array.isArray(dashboardData.value.topBAH) ? dashboardData.value.topBAH.slice(0, 8) : [])
const typeList = computed(() => Array.isArray(summaryData.value.byType) ? summaryData.value.byType : [])
const recentDates = computed(() => Array.isArray(dateSummaryData.value) ? dateSummaryData.value.slice(-10).reverse() : [])

async function loadData() {
  loading.value = true
  try {
    const [summaryRes, dashboardRes, dateRes] = await Promise.all([
      getStatisticsSummary(),
      getDashboardData(),
      getStatisticsDateSummary(),
    ])
    summaryData.value = summaryRes.data || { total: {}, byType: [] }
    dashboardData.value = dashboardRes.data || { recentTrend: [], topBAH: [] }
    dateSummaryData.value = Array.isArray(dateRes.data) ? dateRes.data : []
  }
  catch (error: any) {
    ElMessage.error(error?.message || '统计数据加载失败')
  }
  finally {
    loading.value = false
  }
}

function goToDetail() {
  router.push('/statistics-detail')
}

function openArchive(bah: string) {
  router.push(`/statistics/archive/${bah}`)
}

onMounted(loadData)
</script>

<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <p class="eyebrow">
          Statistics Center
        </p>
        <h2>统计分析</h2>
        <p class="subtitle">
          聚合查看病案统计概览、类型分布、趋势变化与高频病案归档情况。
        </p>
      </div>
      <el-button type="primary" @click="goToDetail">
        查看统计明细
        <el-icon><ArrowRight /></el-icon>
      </el-button>
    </div>

    <section class="summary-grid">
      <el-card v-for="item in summaryCards" :key="item.label" shadow="never">
        <div class="summary-label">
          {{ item.label }}
        </div>
        <div class="summary-value">
          {{ item.value }}
        </div>
        <div class="summary-note">
          {{ item.note }}
        </div>
      </el-card>
    </section>

    <el-row :gutter="20">
      <el-col :span="12">
        <el-card shadow="never" :loading="loading">
          <template #header>
            类型分布
          </template>
          <div class="stack-list">
            <article v-for="item in typeList" :key="item.type" class="stack-item">
              <div>
                <strong>{{ item.type || '未分类' }}</strong>
                <p>记录数 {{ item.recordCount || item.totalRecords || 0 }}</p>
              </div>
              <el-tag>{{ item.totalPages || item.pageCount || 0 }} 页</el-tag>
            </article>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" :loading="loading">
          <template #header>
            近 10 日趋势
          </template>
          <div class="stack-list">
            <article v-for="item in recentDates" :key="item.date" class="stack-item">
              <div>
                <strong>{{ item.date || '-' }}</strong>
                <p>记录数 {{ item.recordCount || 0 }}</p>
              </div>
              <el-tag type="success">
                {{ item.totalPages || 0 }} 页
              </el-tag>
            </article>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" :loading="loading">
      <template #header>
        高频病案号
      </template>
      <el-table :data="topBahList" stripe>
        <el-table-column prop="bah" label="病案号" min-width="140" />
        <el-table-column prop="recordCount" label="记录数" width="120" />
        <el-table-column prop="totalPages" label="总页数" width="120" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openArchive(row.bah)">
              归档图像
            </el-button>
          </template>
        </el-table-column>
      </el-table>
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
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
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
  font-size: 12px;
  color: #64748b;
}

.stack-list {
  display: grid;
  gap: 12px;
}

.stack-item {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 14px;
  background: rgb(15 23 42 / 3%);
  border-radius: 14px;
}

.stack-item strong {
  display: block;
}

.stack-item p {
  margin: 6px 0 0;
  color: #64748b;
}
</style>

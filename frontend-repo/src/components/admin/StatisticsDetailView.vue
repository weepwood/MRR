<template>
  <div class="statistics-detail-view pmr-page">

    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      class="mb-16"
    />

    <el-card class="pmr-panel" shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <h3 class="pmr-panel-title">病案明细列表</h3>
            <p class="pmr-panel-subtitle">支持关键词、类型和日期范围筛选。</p>
          </div>
          <el-tag type="primary">{{ statisticsListData.total || 0 }} 条记录</el-tag>
        </div>
      </template>

      <div class="table-container">
        <div class="list-search-bar">
          <el-input
            v-model="listSearchKeyword"
            placeholder="搜索病案号或扫描设备"
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
          class="records-detail-table"
          :data="statisticsListData.list"
          table-layout="fixed"
          stripe
          v-loading="loading"
          :header-cell-class-name="'records-table-header-cell'"
          :default-sort="{ prop: 'date', order: 'descending' }"
          @sort-change="handleTableSortChange"
          empty-text="暂无数据"
        >
          <el-table-column type="index" label="序号" width="80" align="center" :index="computeTableIndex" />
          <el-table-column prop="bah" label="病案号" min-width="140" sortable="custom" show-overflow-tooltip />
          <el-table-column prop="cid" label="扫描设备ID" width="160" sortable="custom" align="center" show-overflow-tooltip />
          <el-table-column prop="openerNo" label="扫描负责人" width="160" sortable="custom" show-overflow-tooltip>
            <template #default="{ row }">
              <span>{{ row.openerNo === 'NULL' ? '-' : row.openerNo }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="date" label="日期" width="120" sortable="custom" show-overflow-tooltip>
            <template #default="{ row }">
              <span>{{ formatDate(row.date) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="type" label="类型" width="110" sortable="custom" align="center" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag size="small" :type="getTypeTagType(row.type)">
                {{ row.type || '未知' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="pages" label="页数" width="100" sortable="custom" align="center">
            <template #default="{ row }">
              <span class="pages-badge">{{ row.pages ?? 0 }}</span>
            </template>
          </el-table-column>
        </el-table>

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
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { DataBoard, Delete, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getStatisticsList, getStatisticsSummary } from '@/services/api/index.js'

const router = useRouter()
const loading = ref(false)
const error = ref('')
const summaryData = ref({
  byType: []
})
const statisticsListData = ref({
  total: 0,
  size: 100,
  totalPages: 0,
  page: 1,
  list: []
})
const currentPage = ref(1)
const pageSize = ref(100)
const listSearchKeyword = ref('')
const listSearchType = ref('')
const listSearchDateRange = ref([])
const tableSort = ref({
  prop: 'date',
  order: 'descending'
})

const statisticsTypeOptions = computed(() => {
  const source = summaryData.value?.byType || []
  return source.map((item) => item?.type).filter((item) => item && item !== 'NULL')
})

const getBackendSortOrder = (order) => {
  if (order === 'ascending') return 'asc'
  return 'desc'
}

const formatDate = (dateStr) => {
  if (!dateStr) return '无日期'
  return String(dateStr).replace(/\//g, '-')
}

const getTypeTagType = (type) => {
  const typeMap = {
    普通: '',
    质控: 'success',
    高拍: 'warning',
    unknown: 'info'
  }
  return typeMap[type] || 'info'
}

const computeTableIndex = (index) => (currentPage.value - 1) * pageSize.value + index + 1

const loadSummary = async () => {
  try {
    const response = await getStatisticsSummary()
    if (response.data?.code === 200) {
      summaryData.value = response.data.data || {}
    } else if (response.data) {
      summaryData.value = response.data
    }
  } catch (err) {
    console.error('加载统计摘要失败:', err)
  }
}

const loadStatisticsList = async () => {
  loading.value = true
  error.value = ''
  try {
    const { prop, order } = tableSort.value
    const params = {
      page: currentPage.value,
      size: pageSize.value,
      sortBy: prop || 'date',
      sortOrder: getBackendSortOrder(order)
    }

    if (listSearchKeyword.value.trim()) params.keyword = listSearchKeyword.value.trim()
    if (listSearchType.value) params.type = listSearchType.value
    if (Array.isArray(listSearchDateRange.value) && listSearchDateRange.value.length === 2) {
      params.startDate = listSearchDateRange.value[0]
      params.endDate = listSearchDateRange.value[1]
    }

    const response = await getStatisticsList(params)
    if (response.data?.code === 200) {
      statisticsListData.value = response.data.data || {}
    } else if (response.data) {
      statisticsListData.value = response.data
    }

    if (Array.isArray(statisticsListData.value.list)) {
      statisticsListData.value.list = statisticsListData.value.list.filter((item) => item !== null)
    } else {
      statisticsListData.value.list = []
    }
  } catch (err) {
    console.error('加载病案明细失败:', err)
    error.value = err?.message || '加载病案明细失败'
    ElMessage.error(error.value)
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

const handleSizeChange = (newSize) => {
  pageSize.value = newSize
  currentPage.value = 1
  loadStatisticsList()
}

const handleCurrentChange = (newPage) => {
  currentPage.value = newPage
  loadStatisticsList()
}

const handleTableSortChange = ({ prop, order }) => {
  tableSort.value = {
    prop: prop || 'date',
    order: order || 'descending'
  }
  currentPage.value = 1
  loadStatisticsList()
}

const goBackToStatistics = () => {
  router.push('/admin/statistics')
}

onMounted(async () => {
  await loadSummary()
  await loadStatisticsList()
})
</script>

<style scoped>
.statistics-detail-view {
  display: grid;
  gap: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.table-container {
  display: grid;
  gap: 16px;
}

.list-search-bar {
  display: grid;
  grid-template-columns: minmax(0, 2fr) 180px 260px auto auto;
  gap: 12px;
  align-items: center;
}

.search-item {
  width: 100%;
}

.pages-badge {
  font-weight: 700;
  color: #1d4ed8;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
}

.mb-16 {
  margin-bottom: 16px;
}

@media (max-width: 1100px) {
  .list-search-bar {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 640px) {
  .list-search-bar {
    grid-template-columns: 1fr;
  }

  .pagination-wrapper {
    justify-content: center;
  }
}
</style>

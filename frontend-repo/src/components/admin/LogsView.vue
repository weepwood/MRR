<template>
  <div class="logs-view">
    <el-card class="logs-card">
      <template #header>
        <div class="card-header">
          <div class="title">系统日志</div>
          <div class="header-actions">
            <el-button type="primary" @click="refreshData" :loading="loading">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
            <el-button @click="resetFilters">
              <el-icon><Delete /></el-icon>
              重置筛选
            </el-button>
          </div>
        </div>
      </template>

      <el-form :model="filters" inline class="filter-form">
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="URI / IP / UA / Query"
            @keyup.enter="handleSearch"
            style="width: 220px;"
          />
        </el-form-item>
        <el-form-item label="客户端 IP">
          <el-input
            v-model="filters.clientIp"
            clearable
            placeholder="如 127.0.0.1"
            @keyup.enter="handleSearch"
            style="width: 160px;"
          />
        </el-form-item>
        <el-form-item label="请求 URI">
          <el-input
            v-model="filters.requestUri"
            clearable
            placeholder="/statistics-api/summary"
            @keyup.enter="handleSearch"
            style="width: 240px;"
          />
        </el-form-item>
        <el-form-item label="方法">
          <el-select v-model="filters.method" clearable placeholder="全部" style="width: 120px;">
            <el-option v-for="item in methodOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态码">
          <el-select v-model="filters.responseStatus" clearable placeholder="全部" style="width: 120px;">
            <el-option
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="访问时间">
          <el-date-picker
            v-model="filters.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            format="YYYY-MM-DD HH:mm:ss"
            style="width: 360px;"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch" :loading="loading">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" stripe border class="logs-table">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-content">
              <div><span class="field">User-Agent:</span> {{ row.userAgent || '-' }}</div>
              <div><span class="field">Query String:</span> {{ row.queryString || '-' }}</div>
              <div><span class="field">Referer:</span> {{ row.referer || '-' }}</div>
              <div><span class="field">Request Body:</span></div>
              <pre>{{ row.requestBody || '-' }}</pre>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="accessTime" label="访问时间" min-width="170">
          <template #default="{ row }">
            {{ formatAccessTime(row.accessTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="clientIp" label="客户端 IP" min-width="130" />
        <el-table-column prop="method" label="方法" width="90">
          <template #default="{ row }">
            <el-tag :type="methodTagType(row.method)">{{ row.method || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="requestUri" label="请求 URI" min-width="320" show-overflow-tooltip />
        <el-table-column prop="responseStatus" label="状态码" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.responseStatus)">
              {{ row.responseStatus || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="executeTime" label="耗时" width="90">
          <template #default="{ row }">
            {{ formatExecuteTime(row.executeTime) }}
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, Refresh, Search } from '@element-plus/icons-vue'
import { searchSystemLogs } from '@/utils/api'

const loading = ref(false)
const tableData = ref([])
const page = ref(1)
const size = ref(20)
const total = ref(0)

const filters = reactive({
  keyword: '',
  clientIp: '',
  requestUri: '',
  method: '',
  responseStatus: '',
  timeRange: []
})

const methodOptions = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'OPTIONS']
const statusOptions = [
  { label: '2xx 成功', value: '2' },
  { label: '3xx 重定向', value: '3' },
  { label: '4xx 客户端错误', value: '4' },
  { label: '5xx 服务端错误', value: '5' },
  { label: '200', value: '200' },
  { label: '401', value: '401' },
  { label: '403', value: '403' },
  { label: '404', value: '404' },
  { label: '500', value: '500' }
]

const normalize = (value) => {
  if (typeof value !== 'string') return ''
  return value.trim()
}

const buildParams = () => {
  const params = {
    page: page.value,
    size: size.value
  }

  const keyword = normalize(filters.keyword)
  const clientIp = normalize(filters.clientIp)
  const requestUri = normalize(filters.requestUri)

  if (keyword) params.keyword = keyword
  if (clientIp) params.clientIp = clientIp
  if (requestUri) params.requestUri = requestUri
  if (filters.method) params.method = filters.method
  if (filters.responseStatus) params.responseStatus = filters.responseStatus

  if (Array.isArray(filters.timeRange) && filters.timeRange.length === 2) {
    params.startTime = filters.timeRange[0]
    params.endTime = filters.timeRange[1]
  }

  return params
}

const loadData = async () => {
  loading.value = true
  try {
    const response = await searchSystemLogs(buildParams())
    const result = response?.data

    if (!result || result.code !== 200) {
      throw new Error(result?.message || '日志查询失败')
    }

    const payload = result.data || {}
    tableData.value = Array.isArray(payload.list) ? payload.list : []
    total.value = Number(payload.total || 0)
  } catch (error) {
    tableData.value = []
    total.value = 0
    ElMessage.error(error?.message || '日志查询失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  loadData()
}

const handlePageChange = () => {
  loadData()
}

const handleSizeChange = () => {
  page.value = 1
  loadData()
}

const refreshData = async () => {
  await loadData()
  ElMessage.success('日志已刷新')
}

const resetFilters = () => {
  filters.keyword = ''
  filters.clientIp = ''
  filters.requestUri = ''
  filters.method = ''
  filters.responseStatus = ''
  filters.timeRange = []
  page.value = 1
  loadData()
}

const formatExecuteTime = (value) => {
  if (value === null || value === undefined || value === '') return '-'
  return `${value}ms`
}

const formatAccessTime = (value) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', { hour12: false })
}

const methodTagType = (method) => {
  const value = (method || '').toUpperCase()
  if (value === 'GET') return 'primary'
  if (value === 'POST') return 'success'
  if (value === 'PUT' || value === 'PATCH') return 'warning'
  if (value === 'DELETE') return 'danger'
  return 'info'
}

const statusTagType = (status) => {
  const code = Number.parseInt(String(status || ''), 10)
  if (Number.isNaN(code)) return 'info'
  if (code >= 500) return 'danger'
  if (code >= 400) return 'warning'
  if (code >= 300) return ''
  if (code >= 200) return 'success'
  return 'info'
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.logs-view {
  height: 100%;
}

.logs-card {
  min-height: 560px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.title {
  font-weight: 700;
  color: #1d2b42;
  font-size: 16px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.filter-form {
  margin-bottom: 12px;
}

.logs-table {
  width: 100%;
}

.expand-content {
  background: #f9fbff;
  border: 1px solid #e6eef9;
  border-radius: 8px;
  padding: 10px 12px;
  line-height: 1.6;
  color: #31455f;
}

.expand-content .field {
  font-weight: 600;
  color: #1f3552;
}

.expand-content pre {
  margin: 6px 0 0;
  padding: 8px;
  white-space: pre-wrap;
  word-break: break-word;
  background: #ffffff;
  border: 1px solid #e6eef9;
  border-radius: 6px;
}

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}

@media (max-width: 768px) {
  .card-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .header-actions {
    width: 100%;
  }

  .header-actions .el-button {
    flex: 1;
  }
}
</style>

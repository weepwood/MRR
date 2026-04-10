<script setup lang="ts">
import { Refresh, Search, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { getLogById, searchSystemLogs } from '@/api/modules/logs'

defineOptions({ name: 'LogsPage' })

const loading = ref(false)
const tableData = ref<any[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const detailVisible = ref(false)
const currentLog = ref<any>(null)

const filters = reactive({
  keyword: '',
  clientIp: '',
  requestUri: '',
  method: '',
  responseStatus: '',
  timeRange: [] as string[],
})

const methodOptions = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'OPTIONS']
const statusOptions = [
  { label: '全部', value: '' },
  { label: '2xx', value: '2' },
  { label: '4xx', value: '4' },
  { label: '5xx', value: '5' },
  { label: '200', value: '200' },
  { label: '401', value: '401' },
  { label: '403', value: '403' },
  { label: '404', value: '404' },
  { label: '500', value: '500' },
]

function buildParams() {
  const params: Record<string, any> = { page: page.value, size: size.value }
  if (filters.keyword.trim()) {
    params.keyword = filters.keyword.trim()
  }
  if (filters.clientIp.trim()) {
    params.clientIp = filters.clientIp.trim()
  }
  if (filters.requestUri.trim()) {
    params.requestUri = filters.requestUri.trim()
  }
  if (filters.method) {
    params.method = filters.method
  }
  if (filters.responseStatus) {
    params.responseStatus = filters.responseStatus
  }
  if (filters.timeRange.length === 2) {
    params.startTime = filters.timeRange[0]
    params.endTime = filters.timeRange[1]
  }
  return params
}

async function loadData() {
  loading.value = true
  try {
    const response = await searchSystemLogs(buildParams() as {
      page: number
      size: number
      keyword?: string
      clientIp?: string
      requestUri?: string
      method?: string
      responseStatus?: string
      startTime?: string
      endTime?: string
    })
    const payload = response.data || {}
    tableData.value = Array.isArray(payload.list) ? payload.list : []
    total.value = Number(payload.total || 0)
  }
  catch (error: any) {
    tableData.value = []
    total.value = 0
    ElMessage.error(error?.message || '日志查询失败')
  }
  finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  loadData()
}

function resetFilters() {
  filters.keyword = ''
  filters.clientIp = ''
  filters.requestUri = ''
  filters.method = ''
  filters.responseStatus = ''
  filters.timeRange = []
  handleSearch()
}

async function openDetail(row: any) {
  try {
    const response = await getLogById(row.id)
    currentLog.value = response.data || row
  }
  catch {
    currentLog.value = row
  }
  finally {
    detailVisible.value = true
  }
}

function formatDateTime(value: unknown) {
  if (!value) {
    return '-'
  }
  const date = new Date(String(value))
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN', { hour12: false })
}

function formatExecuteTime(value: unknown) {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  return `${value} ms`
}

function methodTagType(method: string) {
  if (method === 'GET') {
    return 'primary'
  }
  if (method === 'POST') {
    return 'success'
  }
  if (method === 'DELETE') {
    return 'danger'
  }
  if (method === 'PUT' || method === 'PATCH') {
    return 'warning'
  }
  return 'info'
}

function statusTagType(status: string | number) {
  const code = Number(status)
  if (Number.isNaN(code)) {
    return 'info'
  }
  if (code >= 500) {
    return 'danger'
  }
  if (code >= 400) {
    return 'warning'
  }
  if (code >= 200) {
    return 'success'
  }
  return 'info'
}

onMounted(loadData)
</script>

<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <p class="eyebrow">
          Operation Logs
        </p>
        <h2>日志管理</h2>
        <p class="subtitle">
          支持按 URI、IP、状态码和时间范围快速检索系统访问日志。
        </p>
      </div>
      <el-button :loading="loading" @click="loadData">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </div>

    <el-card shadow="never">
      <el-form inline @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="filters.keyword" clearable placeholder="URI / Body / UA" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="客户端 IP">
          <el-input v-model="filters.clientIp" clearable placeholder="127.0.0.1" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="请求 URI">
          <el-input v-model="filters.requestUri" clearable placeholder="/v1/scan-api/page" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="方法">
          <el-select v-model="filters.method" clearable placeholder="全部">
            <el-option v-for="item in methodOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态码">
          <el-select v-model="filters.responseStatus" clearable placeholder="全部">
            <el-option v-for="item in statusOptions" :key="item.label" :label="item.label" :value="item.value" />
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
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            查询
          </el-button>
          <el-button @click="resetFilters">
            重置
          </el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="tableData" stripe style="margin-top: 12px;">
        <el-table-column prop="accessTime" label="访问时间" min-width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.accessTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="clientIp" label="客户端 IP" min-width="140" />
        <el-table-column prop="method" label="方法" width="100">
          <template #default="{ row }">
            <el-tag :type="methodTagType(row.method)">
              {{ row.method || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="requestUri" label="请求 URI" min-width="260" show-overflow-tooltip />
        <el-table-column prop="responseStatus" label="状态码" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.responseStatus)">
              {{ row.responseStatus || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="executeTime" label="耗时" width="100">
          <template #default="{ row }">
            {{ formatExecuteTime(row.executeTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openDetail(row)">
              <el-icon><View /></el-icon>
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSearch"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" title="日志详情" width="760px">
      <el-descriptions v-if="currentLog" :column="2" border>
        <el-descriptions-item label="ID">
          {{ currentLog.id || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="客户端 IP">
          {{ currentLog.clientIp || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="请求方法">
          {{ currentLog.method || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="状态码">
          {{ currentLog.responseStatus || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="访问时间" :span="2">
          {{ formatDateTime(currentLog.accessTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="请求 URI" :span="2">
          {{ currentLog.requestUri || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="耗时">
          {{ formatExecuteTime(currentLog.executeTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="Referer">
          {{ currentLog.referer || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="Query String" :span="2">
          {{ currentLog.queryString || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="User-Agent" :span="2">
          {{ currentLog.userAgent || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="Request Body" :span="2">
          <pre class="detail-pre">{{ currentLog.requestBody || '-' }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
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

.pager {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

.detail-pre {
  margin: 0;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
</style>

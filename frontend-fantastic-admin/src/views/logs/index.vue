<script setup lang="ts">
import { Refresh, Search, View } from '@element-plus/icons-vue'
import { ref } from 'vue'
import { getLogById, searchSystemLogs } from '@/api/modules/logs'
import { useCrudList } from '@/composables/useCrudList'
import type { LogRecord, PaginatedResult } from '@/api/types'
import AppLoading from '@/components/AppLoading/index.vue'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'

defineOptions({ name: 'LogsPage' })

const detailVisible = ref(false)
const currentLog = ref<LogRecord | null>(null)
const error = ref('')

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

interface LogsQuery {
  keyword: string
  username: string
  clientIp: string
  requestUri: string
  method: string
  responseStatus: string
  timeRange: string[]
}

const { list, total, loading, pageNum, pageSize, query, handleSearch, resetFilters, loadData } = useCrudList<
  LogRecord,
  LogsQuery
>({
  fetchApi: async (params) => {
    const { page, size, keyword, username, clientIp, requestUri, method, responseStatus, timeRange } = params
    const apiParams: Record<string, any> = { page, size }
    if (keyword?.trim()) apiParams.keyword = keyword.trim()
    if (username?.trim()) apiParams.username = username.trim()
    if (clientIp?.trim()) apiParams.clientIp = clientIp.trim()
    if (requestUri?.trim()) apiParams.requestUri = requestUri.trim()
    if (method) apiParams.method = method
    if (responseStatus) apiParams.responseStatus = responseStatus
    if (timeRange?.length === 2) {
      apiParams.startTime = timeRange[0]
      apiParams.endTime = timeRange[1]
    }
    try {
      error.value = ''
      return searchSystemLogs(apiParams as Parameters<typeof searchSystemLogs>[0])
    }
    catch (e: unknown) {
      const msg = e instanceof Error ? e.message : '加载日志记录失败'
      error.value = msg
      return { list: [], total: 0, page: 1, size: 20 } as PaginatedResult<LogRecord>
    }
  },
  defaultQuery: { keyword: '', username: '', clientIp: '', requestUri: '', method: '', responseStatus: '', timeRange: [] },
})

async function openDetail(row: LogRecord) {
  try {
    const response = await getLogById(row.id!)
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
  if (!value) return '-'
  const date = new Date(String(value))
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN', { hour12: false })
}

function formatExecuteTime(value: unknown) {
  if (value === null || value === undefined || value === '') return '-'
  return `${value} ms`
}

function methodTagType(method: string) {
  if (method === 'GET') return 'primary'
  if (method === 'POST') return 'success'
  if (method === 'DELETE') return 'danger'
  if (method === 'PUT' || method === 'PATCH') return 'warning'
  return 'info'
}

function statusTagType(status: string | number) {
  const code = Number(status)
  if (Number.isNaN(code)) return 'info'
  if (code >= 500) return 'danger'
  if (code >= 400) return 'warning'
  if (code >= 200) return 'success'
  return 'info'
}
</script>

<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <p class="eyebrow">Operation Logs</p>
        <h2>日志管理</h2>
        <p class="subtitle">按请求 ID、真实 URI、接口模板、业务参数、用户、IP、状态码和时间范围追溯接口访问。</p>
      </div>
      <el-button :loading="loading" @click="loadData">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </div>

    <el-card shadow="never">
      <el-form inline @submit.prevent>
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            clearable
            placeholder="Request ID / URI / 参数 / 审计目标"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="用户">
          <el-input v-model="query.username" clearable placeholder="用户名" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="客户端 IP">
          <el-input v-model="query.clientIp" clearable placeholder="127.0.0.1" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="请求 URI">
          <el-input v-model="query.requestUri" clearable placeholder="真实路径或接口模板" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="方法">
          <el-select v-model="query.method" clearable placeholder="全部" style="width: 140px;">
            <el-option v-for="item in methodOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态码">
          <el-select v-model="query.responseStatus" clearable placeholder="全部" style="width: 140px;">
            <el-option v-for="item in statusOptions" :key="item.label" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="访问时间">
          <el-date-picker
            v-model="query.timeRange"
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
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <AppLoading v-if="loading" type="table" :rows="8" />
      <AppError v-else-if="error" :message="error" @retry="loadData" />
      <AppEmpty v-else-if="!list.length" description="暂无日志记录" />
      <el-table v-else :data="list" stripe style="margin-top: 12px;">
        <el-table-column prop="accessTime" label="访问时间" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.accessTime) }}</template>
        </el-table-column>
        <el-table-column prop="requestId" label="Request ID" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.requestId || '-' }}</template>
        </el-table-column>
        <el-table-column prop="username" label="用户" min-width="120">
          <template #default="{ row }">{{ row.username || '-' }}</template>
        </el-table-column>
        <el-table-column prop="clientIp" label="客户端 IP" min-width="140" />
        <el-table-column prop="method" label="方法" width="100">
          <template #default="{ row }">
            <el-tag :type="methodTagType(row.method)">{{ row.method || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="requestUri" label="真实请求 URI" min-width="280" show-overflow-tooltip />
        <el-table-column prop="responseStatus" label="状态码" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.responseStatus)">{{ row.responseStatus || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="executeTime" label="耗时" width="100">
          <template #default="{ row }">{{ formatExecuteTime(row.executeTime) }}</template>
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
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSearch"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" title="接口访问审计详情" width="900px">
      <el-descriptions v-if="currentLog" :column="2" border>
        <el-descriptions-item label="日志 ID">{{ currentLog.id || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Request ID">{{ currentLog.requestId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="用户">{{ currentLog.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户端 IP">{{ currentLog.clientIp || '-' }}</el-descriptions-item>
        <el-descriptions-item label="请求方法">{{ currentLog.method || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态码">{{ currentLog.responseStatus || '-' }}</el-descriptions-item>
        <el-descriptions-item label="访问时间">{{ formatDateTime(currentLog.accessTime) }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ formatExecuteTime(currentLog.executeTime) }}</el-descriptions-item>
        <el-descriptions-item label="真实请求 URI" :span="2">
          <span class="detail-value">{{ currentLog.requestUri || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="接口模板" :span="2">
          <span class="detail-value">{{ currentLog.endpointTemplate || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="Referer" :span="2">
          <span class="detail-value">{{ currentLog.referer || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="Query String" :span="2">
          <pre class="detail-pre">{{ currentLog.queryString || '-' }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="Request Body" :span="2">
          <pre class="detail-pre">{{ currentLog.requestBody || '-' }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="审计动作">{{ currentLog.auditAction || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审计目标">{{ currentLog.auditTarget || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审计描述" :span="2">
          <span class="detail-value">{{ currentLog.auditDescription || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="异常信息" :span="2">
          <pre class="detail-pre error-detail">{{ currentLog.errorMessage || '-' }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="User-Agent" :span="2">
          <span class="detail-value">{{ currentLog.userAgent || '-' }}</span>
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
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

h2 {
  margin: 0;
  font-size: 28px;
}

.subtitle {
  margin: 8px 0 0;
  color: var(--text-secondary);
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

.detail-value,
.detail-pre {
  overflow-wrap: anywhere;
  word-break: break-word;
}

.detail-pre {
  max-height: 260px;
  margin: 0;
  overflow: auto;
  white-space: pre-wrap;
}

.error-detail {
  color: var(--el-color-danger);
}
</style>

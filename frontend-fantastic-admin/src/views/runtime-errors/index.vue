<script setup lang="ts">
/* eslint-disable antfu/if-newline, curly, vue/singleline-html-element-content-newline */
import type {
  SystemErrorEvent,
  SystemErrorOverview,
  SystemErrorStatus,
} from '@/api/modules/system-errors'
import { Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import {
  getSystemErrorDetail,
  getSystemErrorOverview,
  searchSystemErrors,
  updateSystemErrorStatus,
} from '@/api/modules/system-errors'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import AppLoading from '@/components/AppLoading/index.vue'
import { useUserStore } from '@/store/modules/user'
import { checkPermission } from '@/utils/permission'

defineOptions({ name: 'RuntimeErrorsPage' })

const userStore = useUserStore()
const canManage = computed(() => checkPermission(userStore.permissions, 'system:error:manage'))
const loading = ref(false)
const error = ref('')
const list = ref<SystemErrorEvent[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const detailVisible = ref(false)
const detailLoading = ref(false)
const current = ref<SystemErrorEvent | null>(null)
const updatingId = ref<number | null>(null)

const overview = ref<SystemErrorOverview>({
  totalGroups: 0,
  totalOccurrences: 0,
  openGroups: 0,
  acknowledgedGroups: 0,
  resolvedGroups: 0,
  errorGroups: 0,
  warnGroups: 0,
  recentOccurrences: 0,
})

const query = reactive({
  keyword: '',
  level: '',
  status: '',
  module: '',
})

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const params = {
      page: page.value,
      size: size.value,
      keyword: query.keyword.trim() || undefined,
      level: query.level || undefined,
      status: query.status || undefined,
      module: query.module.trim() || undefined,
    }
    const [pageResult, overviewResult] = await Promise.all([
      searchSystemErrors(params),
      getSystemErrorOverview(),
    ])
    list.value = pageResult.data?.list ?? []
    total.value = pageResult.data?.total ?? 0
    if (overviewResult.data) {
      overview.value = overviewResult.data
    }
  }
  catch (exception: unknown) {
    error.value = exception instanceof Error ? exception.message : '加载运行错误失败'
  }
  finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  void loadData()
}

function resetFilters() {
  query.keyword = ''
  query.level = ''
  query.status = ''
  query.module = ''
  page.value = 1
  void loadData()
}

async function openDetail(row: SystemErrorEvent) {
  detailVisible.value = true
  detailLoading.value = true
  current.value = row
  try {
    const response = await getSystemErrorDetail(row.id)
    if (response.data) {
      current.value = response.data
    }
  }
  finally {
    detailLoading.value = false
  }
}

async function changeStatus(row: SystemErrorEvent, status: SystemErrorStatus) {
  if (!canManage.value) return
  updatingId.value = row.id
  try {
    await updateSystemErrorStatus(row.id, status)
    ElMessage.success('处理状态已更新')
    await loadData()
    if (current.value?.id === row.id) {
      const response = await getSystemErrorDetail(row.id)
      current.value = response.data ?? current.value
    }
  }
  finally {
    updatingId.value = null
  }
}

function formatDateTime(value?: string) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

function levelTagType(level: string) {
  return level === 'ERROR' ? 'danger' : 'warning'
}

function statusTagType(status: string) {
  if (status === 'RESOLVED') return 'success'
  if (status === 'ACKNOWLEDGED') return 'warning'
  return 'danger'
}

function statusLabel(status: string) {
  if (status === 'RESOLVED') return '已解决'
  if (status === 'ACKNOWLEDGED') return '处理中'
  return '待处理'
}

onMounted(loadData)
</script>

<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <p class="eyebrow">Runtime Error Center</p>
        <h2>运行错误中心</h2>
        <p class="subtitle">集中查看后端 WARN / ERROR，按指纹合并重复事件，并通过错误编号、Request ID 关联接口访问日志。</p>
      </div>
      <el-button :loading="loading" @click="loadData">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </div>

    <div class="overview-grid">
      <el-card shadow="never" class="overview-card danger-card">
        <span>待处理错误组</span>
        <strong>{{ overview.openGroups }}</strong>
        <small>ERROR {{ overview.errorGroups }} 组</small>
      </el-card>
      <el-card shadow="never" class="overview-card">
        <span>近 24 小时发生次数</span>
        <strong>{{ overview.recentOccurrences }}</strong>
        <small>重复事件合并计数</small>
      </el-card>
      <el-card shadow="never" class="overview-card">
        <span>累计错误组</span>
        <strong>{{ overview.totalGroups }}</strong>
        <small>累计 {{ overview.totalOccurrences }} 次</small>
      </el-card>
      <el-card shadow="never" class="overview-card success-card">
        <span>已解决</span>
        <strong>{{ overview.resolvedGroups }}</strong>
        <small>处理中 {{ overview.acknowledgedGroups }} 组</small>
      </el-card>
    </div>

    <el-card shadow="never">
      <el-form inline @submit.prevent>
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            clearable
            placeholder="错误编号 / Request ID / 异常 / 消息"
            style="width: 300px;"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="模块">
          <el-input v-model="query.module" clearable placeholder="controller / storage" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="query.level" clearable placeholder="全部" style="width: 130px;">
            <el-option label="ERROR" value="ERROR" />
            <el-option label="WARN" value="WARN" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 140px;">
            <el-option label="待处理" value="OPEN" />
            <el-option label="处理中" value="ACKNOWLEDGED" />
            <el-option label="已解决" value="RESOLVED" />
          </el-select>
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
      <AppEmpty v-else-if="!list.length" description="暂无运行错误事件" />
      <el-table v-else :data="list" stripe style="margin-top: 12px;">
        <el-table-column prop="lastSeenAt" label="最后发生" min-width="175">
          <template #default="{ row }">{{ formatDateTime(row.lastSeenAt) }}</template>
        </el-table-column>
        <el-table-column prop="errorId" label="错误编号" min-width="190" show-overflow-tooltip />
        <el-table-column prop="level" label="级别" width="90">
          <template #default="{ row }">
            <el-tag :type="levelTagType(row.level)">{{ row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="module" label="模块" min-width="120" show-overflow-tooltip />
        <el-table-column prop="messageSummary" label="错误摘要" min-width="360" show-overflow-tooltip />
        <el-table-column prop="requestId" label="Request ID" min-width="155" show-overflow-tooltip>
          <template #default="{ row }">{{ row.requestId || '-' }}</template>
        </el-table-column>
        <el-table-column prop="occurrenceCount" label="次数" width="90" align="right" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" :width="canManage ? 250 : 90" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <template v-if="canManage">
              <el-button
                v-if="row.status === 'OPEN'"
                link
                type="warning"
                :loading="updatingId === row.id"
                @click="changeStatus(row, 'ACKNOWLEDGED')"
              >
                标记处理中
              </el-button>
              <el-button
                v-if="row.status !== 'RESOLVED'"
                link
                type="success"
                :loading="updatingId === row.id"
                @click="changeStatus(row, 'RESOLVED')"
              >
                解决
              </el-button>
              <el-button
                v-else
                link
                type="danger"
                :loading="updatingId === row.id"
                @click="changeStatus(row, 'OPEN')"
              >
                重新打开
              </el-button>
            </template>
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

    <el-drawer v-model="detailVisible" title="运行错误详情" size="70%">
      <AppLoading v-if="detailLoading" type="card" :cols="1" />
      <el-descriptions v-else-if="current" :column="2" border>
        <el-descriptions-item label="错误编号">{{ current.errorId }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(current.status)">{{ statusLabel(current.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="级别">{{ current.level }}</el-descriptions-item>
        <el-descriptions-item label="累计次数">{{ current.occurrenceCount }}</el-descriptions-item>
        <el-descriptions-item label="首次发生">{{ formatDateTime(current.firstSeenAt) }}</el-descriptions-item>
        <el-descriptions-item label="最后发生">{{ formatDateTime(current.lastSeenAt) }}</el-descriptions-item>
        <el-descriptions-item label="模块">{{ current.module || '-' }}</el-descriptions-item>
        <el-descriptions-item label="线程">{{ current.threadName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Request ID" :span="2">{{ current.requestId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="记录器" :span="2">{{ current.loggerName }}</el-descriptions-item>
        <el-descriptions-item label="异常类型" :span="2">{{ current.exceptionType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="错误摘要" :span="2">
          <pre class="detail-pre summary-pre">{{ current.messageSummary }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="脱敏堆栈" :span="2">
          <pre class="detail-pre">{{ current.stackTrace || '该事件没有异常堆栈。' }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="处理人">{{ current.acknowledgedBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="解决时间">{{ formatDateTime(current.resolvedAt) }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<style scoped>
/* stylelint-disable order/properties-order, declaration-property-value-keyword-no-deprecated, media-feature-range-notation */
.page-shell {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-header h2 {
  margin: 4px 0 8px;
}

.eyebrow {
  margin: 0;
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.subtitle {
  margin: 0;
  color: var(--el-text-color-secondary);
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.overview-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.overview-card span,
.overview-card small {
  color: var(--el-text-color-secondary);
}

.overview-card strong {
  font-size: 30px;
  line-height: 1;
}

.danger-card strong {
  color: var(--el-color-danger);
}

.success-card strong {
  color: var(--el-color-success);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.detail-pre {
  box-sizing: border-box;
  max-height: 460px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  font-family: Consolas, "Courier New", monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.summary-pre {
  max-height: 180px;
}

@media (max-width: 1100px) {
  .overview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .page-header {
    flex-direction: column;
  }

  .overview-grid {
    grid-template-columns: 1fr;
  }
}
/* stylelint-enable order/properties-order, declaration-property-value-keyword-no-deprecated, media-feature-range-notation */
</style>

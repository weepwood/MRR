<script setup lang="ts">
import type { RuntimeLogRow } from '@/api/modules/runtime-logs'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getRuntimeLogFiles, getRuntimeLogHealth, getRuntimeLogs, importRuntimeLog } from '@/api/modules/runtime-logs'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import MrrMetricCard from '@/components/MrrMetricCard/index.vue'
import { rangePickerPopperOptions } from '@/ui/provider/picker-options'
import useAuth from '@/utils/composables/useAuth'
import { formatDateTime } from '@/utils/display-format'

const route = useRoute()
const { auth } = useAuth()
let queryVersion = 0
const loading = ref(false)
const error = ref('')
const rows = ref<RuntimeLogRow[]>([])
const health = ref<Record<string, unknown>>({})
const files = ref<{ fileName: string, bytes: number }[]>([])
const selectedFiles = ref<string[]>([])
const historyRange = ref<[string, string] | null>(null)
const importing = ref(false)
const requestId = ref(String(route.query.requestId || ''))
const level = ref('')
const logger = ref('')
const errorId = ref('')
const range = ref<[string, string]>([new Date(Date.now() - 86400000).toISOString(), new Date().toISOString()])
const hasMore = ref(false)
const cursor = ref<RuntimeLogRow | null>(null)
const detail = ref<RuntimeLogRow | null>(null)
const detailVisible = ref(false)
function bytes(value: unknown) { return typeof value === 'number' && Number.isFinite(value) ? `${(value / 1024 / 1024).toFixed(1)} MB` : '未知' }
const backlog = computed(() => health.value.backlog as Record<string, unknown> | undefined)
const selectableFiles = computed(() => files.value.filter((file) => {
  if (!historyRange.value) { return true }
  const day = file.fileName.match(/\.(\d{4}-\d{2}-\d{2})\./)?.[1]
  return Boolean(day && day >= historyRange.value[0] && day <= historyRange.value[1])
}))
const metrics = computed(() => [
  { label: '最近成功轮询', value: typeof health.value.pollAgeSeconds === 'number' ? `${health.value.pollAgeSeconds} 秒前` : '未知', note: '轮询成功不代表积压已处理完毕' },
  { label: '已登记待采集文件', value: typeof backlog.value?.pending_files === 'number' ? backlog.value.pending_files : '未知', note: `未登记文件在下一轮发现；原始积压 ${bytes(backlog.value?.pending_raw_bytes)}` },
  { label: '缓冲卷剩余空间', value: bytes(health.value.bufferDiskFreeBytes), note: '数据库卷空间需在服务器核实' },
  { label: '数据库日志容量', value: bytes(health.value.databaseBytes), note: '明细及索引；长期保留，不自动删除' },
])
const appliedFilters = ref<Record<string, unknown>>({})
function currentFilters() { return { from: range.value?.[0], to: range.value?.[1], requestId: requestId.value.trim() || undefined, level: level.value || undefined, logger: logger.value.trim() || undefined, errorId: errorId.value.trim() || undefined } }
const filtersChanged = computed(() => JSON.stringify(appliedFilters.value) !== JSON.stringify(currentFilters()))
const gap = computed(() => Boolean(health.value.unknownGap) || Number(health.value.invalidLines || 0) > 0)

async function load(reset = true) {
  const version = ++queryVersion
  loading.value = true
  error.value = ''
  if (reset) { cursor.value = null }
  try {
    if (reset) { appliedFilters.value = currentFilters() }
    const result = await getRuntimeLogs({ ...appliedFilters.value, cursorTime: cursor.value?.occurred_at, cursorId: cursor.value?.event_id, size: 50 })
    if (version !== queryVersion) { return }
    if (!Array.isArray(result.data?.list)) { throw new Error('日志响应格式异常') }
    rows.value = result.data.list
    hasMore.value = result.data?.hasMore || false
  }
  catch (exception) {
    if (version === queryVersion) { error.value = exception instanceof Error ? exception.message : '运行日志读取失败' }
  }
  finally {
    if (version === queryVersion) { loading.value = false }
  }
}
async function refreshHealth() {
  try {
    const [status, inventory] = await Promise.all([getRuntimeLogHealth(), getRuntimeLogFiles()])
    health.value = status.data || {}
    files.value = inventory.data || []
  }
  catch (exception) { error.value = exception instanceof Error ? exception.message : '采集状态读取失败' }
}
async function importFile() {
  if (!selectedFiles.value.length) { return }
  importing.value = true
  try { for (const name of selectedFiles.value) { await importRuntimeLog(name) } selectedFiles.value = []; await refreshHealth() }
  catch { ElMessage.error('补录登记失败，请检查所选文件和导入权限') }
  finally { importing.value = false }
}
function next() { cursor.value = rows.value[rows.value.length - 1] || null; void load(false) }
function showDetail(row: RuntimeLogRow) { detail.value = row; detailVisible.value = true }
onMounted(() => { void load(); void refreshHealth() })
</script>

<template>
  <div class="page-shell runtime-log-page mrr-content-enter">
    <div class="page-header">
      <div><h2>运行日志</h2><p>通过追踪编号检查已脱敏的后端日志；默认最近24小时，单次范围最多31天。</p></div>
      <el-button :loading="loading" @click="load()">
        刷新日志
      </el-button>
    </div>
    <el-alert v-if="gap" title="日志存在缺口或无法确认完整性，请检查采集状态" type="warning" :closable="false" show-icon />
    <el-alert v-if="health.failureCode || health.status === 'UNAVAILABLE'" title="日志采集暂不可用，业务请求仍保持服务；请检查数据库及磁盘" type="error" :closable="false" show-icon />
    <el-alert v-if="health.auditHmacConfigured === false" title="审计检索密钥尚未配置，编号只会隐藏，无法通过摘要检索；请由管理员配置独立密钥" type="warning" :closable="false" show-icon />
    <section class="mrr-metric-grid mrr-metric-grid--four">
      <MrrMetricCard v-for="metric in metrics" :key="metric.label" v-bind="metric" />
    </section>
    <el-card shadow="never">
      <el-form inline @submit.prevent="load()">
        <el-form-item label="时间范围">
          <el-date-picker v-model="range" type="datetimerange" :popper-options="rangePickerPopperOptions" value-format="YYYY-MM-DDTHH:mm:ssZ" start-placeholder="开始时间" end-placeholder="结束时间" />
        </el-form-item>
        <el-form-item label="追踪编号">
          <el-input v-model="requestId" clearable placeholder="Request ID" />
        </el-form-item>
        <el-form-item label="模块">
          <el-input v-model="logger" clearable placeholder="完整 logger 名称" />
        </el-form-item>
        <el-form-item label="错误编号">
          <el-input v-model="errorId" clearable />
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="level" clearable placeholder="全部级别" style="width: 140px;">
            <el-option v-for="item in ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR']" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button native-type="submit" type="primary" :loading="loading">
            查询日志
          </el-button>
        </el-form-item>
      </el-form>
      <el-alert v-if="filtersChanged" title="筛选已修改，点击查询后生效；下一批沿用已提交条件" type="info" :closable="false" />
      <AppError v-if="error" :message="error" @retry="load()" />
      <AppEmpty v-else-if="!loading && !rows.length" description="所选条件下没有已入库的日志；可检查采集延迟或调整时间范围" />
      <el-table v-else v-loading="loading" :data="rows" row-key="event_id">
        <el-table-column label="时间" min-width="200">
          <template #default="scope">
            {{ formatDateTime(scope.row.occurred_at) }}
          </template>
        </el-table-column>
        <el-table-column prop="level" label="级别" width="90" />
        <el-table-column prop="logger_name" label="模块" min-width="200" show-overflow-tooltip />
        <el-table-column prop="request_id" label="追踪编号" min-width="160" />
        <el-table-column prop="message_summary" label="摘要" min-width="220" show-overflow-tooltip />
        <el-table-column label="操作" width="90">
          <template #default="scope">
            <el-button link type="primary" @click="showDetail(scope.row as RuntimeLogRow)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="log-pagination">
        <el-button :disabled="!cursor || loading" @click="load()">
          回到首批
        </el-button><el-button :disabled="!hasMore || loading" @click="next">
          下一批
        </el-button>
      </div>
    </el-card>
    <el-card shadow="never">
      <template #header>
        <div class="collector-heading">
          <strong>采集与补录</strong><el-button :loading="importing" @click="refreshHealth">
            刷新状态
          </el-button>
        </div>
      </template>
      <p>长期数据库日志不自动删除。补录仅限服务端日志目录中的文件，后台按批次处理。</p>
      <el-form v-if="auth('system:log:import')" inline @submit.prevent="importFile">
        <el-form-item label="轮转日期">
          <el-date-picker v-model="historyRange" type="daterange" :popper-options="rangePickerPopperOptions" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" />
        </el-form-item>
        <el-form-item label="历史文件">
          <el-select v-model="selectedFiles" multiple :multiple-limit="20" placeholder="选择固定目录中的文件" style="width: 340px;">
            <el-option v-for="file in selectableFiles" :key="file.fileName" :label="file.fileName" :value="file.fileName" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button native-type="submit" :loading="importing" :disabled="!selectedFiles.length">
            登记补录
          </el-button>
        </el-form-item>
      </el-form>
      <p>轮转日期筛选仅匹配文件名日期；每批最多20个文件，每文件解压上限128MB。旧格式正文默认隐藏，保留安全元数据；失败会显示明确缺口。</p>
      <el-table :data="(health.sources as Record<string, unknown>[]) || []" max-height="320">
        <el-table-column prop="file_name" label="来源文件" min-width="240" />
        <el-table-column prop="state" label="状态" width="120" />
        <el-table-column prop="byte_offset" label="已确认字节偏移" min-width="150" />
        <el-table-column prop="invalid_lines" label="已确认无效行" width="130" />
        <el-table-column prop="failure_code" label="故障原因" min-width="190" />
      </el-table>
      <el-collapse>
        <el-collapse-item title="容量、完整性与采集详情" name="health">
          <pre class="log-detail">{{ JSON.stringify(health, null, 2) }}</pre>
        </el-collapse-item>
      </el-collapse>
    </el-card>
    <el-drawer v-model="detailVisible" title="日志详情" size="60%">
      <pre class="log-detail" v-text="`${detail?.message_summary ?? ''}\n${detail?.stack_trace ?? ''}`" />
    </el-drawer>
  </div>
</template>

<style scoped>
.runtime-log-page { display: grid; gap: 16px; }
.page-header { display: flex; gap: 16px; align-items: center; justify-content: space-between; }
.collector-heading { display: flex; gap: 16px; align-items: center; justify-content: space-between; }
.page-header h2 { margin: 0; }
.page-header p { color: var(--el-text-color-secondary); }
.log-detail { max-height: 440px; overflow: auto; font-family: Consolas, monospace; overflow-wrap: anywhere; white-space: pre-wrap; }
.log-pagination { display: flex; gap: 8px; justify-content: flex-end; margin-top: 16px; }
</style>

<script setup lang="ts">
import { Refresh, Search, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { getLogById, searchImageAuditLogs } from '@/api/modules/logs'

defineOptions({ name: 'ImageAuditPage' })

const loading = ref(false)
const tableData = ref<any[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const detailVisible = ref(false)
const currentLog = ref<any>(null)

const filters = reactive({ keyword: '', username: '', clientIp: '', auditAction: '', responseStatus: '', timeRange: [] as string[] })
const actionOptions = [
  { label: '查询病案图片列表', value: 'LIST' },
  { label: '查看本地病案图片', value: 'VIEW_IMAGE' },
  { label: '查看 OSS 病案图片', value: 'VIEW_OSS_IMAGE' },
  { label: '下载病案压缩包', value: 'DOWNLOAD' },
  { label: '禁用用户', value: 'DISABLE_USER' },
  { label: '更新用户信息', value: 'UPDATE_USER' },
  { label: '更新角色权限配置', value: 'UPDATE_ROLE' },
  { label: '修改密码', value: 'CHANGE_PASSWORD' },
  { label: '上传图片到 OSS', value: 'OSS_UPLOAD' },
  { label: '删除 OSS 对象', value: 'DELETE_OSS_OBJECT' },
]
const statusOptions = [{ label: '全部', value: '' }, { label: '2xx', value: '2' }, { label: '4xx', value: '4' }, { label: '5xx', value: '5' }, { label: '200', value: '200' }, { label: '302', value: '302' }, { label: '404', value: '404' }]

function buildParams() {
  const params: Record<string, any> = { page: page.value, size: size.value }
  for (const key of ['keyword', 'username', 'clientIp', 'auditAction', 'responseStatus']) {
    const value = String((filters as any)[key] || '').trim()
    if (value) { params[key] = value }
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
    const response = await searchImageAuditLogs(buildParams() as any)
    const payload = response.data || {}
    tableData.value = Array.isArray(payload.list) ? payload.list : []
    total.value = Number(payload.total || 0)
  }
  catch (error: any) {
    tableData.value = []
    total.value = 0
    ElMessage.error(error?.message || '审计日志查询失败')
  }
  finally { loading.value = false }
}
function handleSearch() { page.value = 1; loadData() }
function resetFilters() { filters.keyword = ''; filters.username = ''; filters.clientIp = ''; filters.auditAction = ''; filters.responseStatus = ''; filters.timeRange = []; handleSearch() }
async function openDetail(row: any) {
  try { const res = await getLogById(row.id); currentLog.value = { ...(res.data || {}), ...row } }
  catch { currentLog.value = row }
  finally { detailVisible.value = true }
}
function formatDateTime(value: unknown) { if (!value) { return '-' }; const date = new Date(String(value)); return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN', { hour12: false }) }
function statusTagType(status: string | number) { const code = Number(status); if (Number.isNaN(code)) { return 'info' }; if (code >= 500) { return 'danger' }; if (code >= 400) { return 'warning' }; if (code >= 200 && code < 400) { return 'success' }; return 'info' }
function actionLabel(value: string) { return actionOptions.find(item => item.value === value)?.label || value || '-' }
onMounted(loadData)
</script>

<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <p class="eyebrow">
          Sensitive Audit
        </p><h2>病案图片访问审计</h2><p class="subtitle">
          专门展示用户查询、查看、下载病案图片等敏感操作记录。
        </p>
      </div>
      <el-button :loading="loading" @click="loadData">
        <el-icon><Refresh /></el-icon>刷新
      </el-button>
    </div>
    <el-alert type="warning" show-icon :closable="false" title="审计范围：/api/v1/img/{病案号}、/api/v1/img/image/*、/api/v1/img/oss-image/*、/api/v1/img/download/{病案号}" />
    <el-card shadow="never">
      <el-form inline @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="filters.keyword" clearable placeholder="用户 / IP / URI / 病案号" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="用户">
          <el-input v-model="filters.username" clearable placeholder="用户名" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="客户端 IP">
          <el-input v-model="filters.clientIp" clearable placeholder="127.0.0.1" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="敏感操作">
          <el-select v-model="filters.auditAction" clearable placeholder="全部" style="width: 200px;">
            <el-option v-for="item in actionOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态码">
          <el-select v-model="filters.responseStatus" clearable placeholder="全部" style="width: 140px;">
            <el-option v-for="item in statusOptions" :key="item.label" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="访问时间">
          <el-date-picker v-model="filters.timeRange" type="datetimerange" range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间" value-format="YYYY-MM-DD HH:mm:ss" format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>查询
          </el-button><el-button @click="resetFilters">
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
        <el-table-column prop="username" label="用户" min-width="120">
          <template #default="{ row }">
            {{ row.username || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="auditDescription" label="敏感操作" min-width="170">
          <template #default="{ row }">
            <el-tag type="danger">
              {{ row.auditDescription || actionLabel(row.auditAction) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="auditTarget" label="审计对象" min-width="140">
          <template #default="{ row }">
            {{ row.auditTarget || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="clientIp" label="客户端 IP" min-width="140" />
        <el-table-column prop="requestUri" label="请求 URI" min-width="300" show-overflow-tooltip />
        <el-table-column prop="responseStatus" label="状态码" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.responseStatus)">
              {{ row.responseStatus || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="executeTime" label="耗时(ms)" width="100" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openDetail(row)">
              <el-icon><View /></el-icon>详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="page" v-model:page-size="size" :page-sizes="[10, 20, 50, 100]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="handleSearch" @current-change="loadData" />
      </div>
    </el-card>
    <el-dialog v-model="detailVisible" title="审计详情" width="760px">
      <el-descriptions v-if="currentLog" :column="2" border>
        <el-descriptions-item label="ID">
          {{ currentLog.id || '-' }}
        </el-descriptions-item><el-descriptions-item label="用户">
          {{ currentLog.username || '-' }}
        </el-descriptions-item><el-descriptions-item label="敏感操作">
          {{ currentLog.auditDescription || actionLabel(currentLog.auditAction) }}
        </el-descriptions-item><el-descriptions-item label="审计对象">
          {{ currentLog.auditTarget || '-' }}
        </el-descriptions-item><el-descriptions-item label="客户端 IP">
          {{ currentLog.clientIp || '-' }}
        </el-descriptions-item><el-descriptions-item label="状态码">
          {{ currentLog.responseStatus || '-' }}
        </el-descriptions-item><el-descriptions-item label="访问时间" :span="2">
          {{ formatDateTime(currentLog.accessTime) }}
        </el-descriptions-item><el-descriptions-item label="请求 URI" :span="2">
          {{ currentLog.requestUri || '-' }}
        </el-descriptions-item><el-descriptions-item label="Referer" :span="2">
          {{ currentLog.referer || '-' }}
        </el-descriptions-item><el-descriptions-item label="User-Agent" :span="2">
          {{ currentLog.userAgent || '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-shell { display: grid; gap: 20px; }
.page-header { display: flex; gap: 16px; align-items: flex-start; justify-content: space-between; }
.eyebrow { margin: 0 0 6px; font-size: 12px; font-weight: 700; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.12em; }
h2 { margin: 0; font-size: 28px; }
.subtitle { margin: 8px 0 0; color: var(--text-secondary); }
.pager { display: flex; justify-content: center; margin-top: 16px; }
</style>

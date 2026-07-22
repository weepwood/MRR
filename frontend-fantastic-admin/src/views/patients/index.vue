<script setup lang="ts">
import type { PatientRecord } from '@/api/modules/patients'
import type { EffectiveSystemSettings } from '@/utils/system-settings'
import { Download, Refresh, Search, Upload } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { exportPatientsExcel, getPatients } from '@/api/modules/patients'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import AppLoading from '@/components/AppLoading/index.vue'
import useAuth from '@/utils/composables/useAuth'
import { loadEffectiveSystemSettings, SYSTEM_SETTINGS_UPDATED_EVENT } from '@/utils/system-settings'
import PatientAnalyticsPanel from './components/PatientAnalyticsPanel.vue'
import PatientImportDialog from './components/PatientImportDialog.vue'

defineOptions({ name: 'PatientsPage' })

const loading = ref(false)
const exporting = ref(false)
const tableData = ref<PatientRecord[]>([])
const error = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const revealedIdCards = ref(new Set<string>())
const patientIdCardRevealEnabled = ref(false)
const patientIdCardCopyEnabled = ref(false)
const importDialogVisible = ref(false)
const analyticsRefreshKey = ref(0)
const { auth } = useAuth()
const canImportPatients = computed(() => auth('record:edit'))

const filters = reactive({ keyword: '' })

async function loadData() {
  loading.value = true
  try {
    error.value = ''
    const params = { page: page.value, size: size.value, ...(filters.keyword.trim() && { keyword: filters.keyword.trim() }) }
    const res = await getPatients(params)
    const payload = res.data ?? { list: [], total: 0 }
    tableData.value = Array.isArray(payload.list) ? payload.list : []
    total.value = Number(payload.total || 0)
    revealedIdCards.value.clear()
  }
  catch (err: unknown) {
    tableData.value = []
    total.value = 0
    error.value = err instanceof Error ? err.message : '患者列表加载失败'
  }
  finally {
    loading.value = false
  }
}

function refreshAll() {
  void loadData()
  analyticsRefreshKey.value += 1
}

function handleSearch() {
  page.value = 1
  void loadData()
}

function resetFilters() {
  filters.keyword = ''
  handleSearch()
}

function handlePageChange(nextPage: number) {
  page.value = nextPage
  void loadData()
}

function handleSizeChange(nextSize: number) {
  size.value = nextSize
  page.value = 1
  void loadData()
}

function handleImported() {
  page.value = 1
  refreshAll()
}

async function handleExport() {
  exporting.value = true
  try {
    const response = await exportPatientsExcel(filters.keyword.trim() || undefined)
    const blob = response.data instanceof Blob
      ? response.data
      : new Blob([response.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `患者数据-${new Date().toISOString().slice(0, 10)}.xlsx`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
    ElMessage.success('患者数据导出完成')
  }
  catch {
    ElMessage.error('患者数据导出失败')
  }
  finally {
    exporting.value = false
  }
}

function maskIdCard(value?: string) {
  if (!value || value.length <= 7) {
    return value || '—'
  }
  return `${value.slice(0, 3)}${'*'.repeat(value.length - 7)}${value.slice(-4)}`
}

function isIdCardRevealed(value?: string) {
  return patientIdCardRevealEnabled.value && !!value && revealedIdCards.value.has(value)
}

async function handleIdCardClick(value?: string) {
  if (!value) {
    return
  }
  if (patientIdCardRevealEnabled.value) {
    if (revealedIdCards.value.has(value)) {
      revealedIdCards.value.delete(value)
    }
    else {
      revealedIdCards.value.add(value)
    }
  }

  if (!patientIdCardCopyEnabled.value) {
    return
  }

  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success('身份证号已复制')
  }
  catch {
    ElMessage.error('身份证号复制失败')
  }
}

function applyIdCardSettings(settings: EffectiveSystemSettings) {
  patientIdCardRevealEnabled.value = settings.patientIdCardRevealEnabled
  patientIdCardCopyEnabled.value = settings.patientIdCardCopyEnabled
  if (!settings.patientIdCardRevealEnabled) {
    revealedIdCards.value.clear()
  }
}

async function loadIdCardSettings() {
  const { settings } = await loadEffectiveSystemSettings()
  applyIdCardSettings(settings)
}

function handleSystemSettingsUpdated(event: Event) {
  const settings = (event as CustomEvent<EffectiveSystemSettings>).detail
  if (settings) {
    applyIdCardSettings(settings)
  }
}

onMounted(() => {
  void loadData()
  void loadIdCardSettings()
  window.addEventListener(SYSTEM_SETTINGS_UPDATED_EVENT, handleSystemSettingsUpdated)
})

onUnmounted(() => {
  window.removeEventListener(SYSTEM_SETTINGS_UPDATED_EVENT, handleSystemSettingsUpdated)
})
</script>

<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <p class="eyebrow">
          Patient Management
        </p>
        <h2>患者管理</h2>
        <p class="subtitle">
          查询、导入和导出患者基本信息，并分析身份证完整性、重复患者、年度日期与科室分布。
        </p>
      </div>
      <div class="header-actions">
        <el-button
          v-if="canImportPatients"
          type="primary"
          :icon="Upload"
          @click="importDialogVisible = true"
        >
          导入患者数据
        </el-button>
        <el-button :icon="Download" :loading="exporting" @click="handleExport">
          导出当前结果
        </el-button>
        <el-button :loading="loading" :icon="Refresh" @click="refreshAll">
          刷新
        </el-button>
      </div>
    </div>

    <PatientAnalyticsPanel :refresh-key="analyticsRefreshKey" />

    <el-card shadow="never">
      <div class="filter-bar">
        <el-input
          v-model="filters.keyword"
          placeholder="搜索病案号 / 姓名 / 身份证号 / 科室 / 病区 / 床位"
          clearable
          class="filter-input"
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="handleSearch">
          查询
        </el-button>
        <el-button @click="resetFilters">
          重置
        </el-button>
      </div>
    </el-card>

    <el-card shadow="never">
      <AppLoading v-if="loading" type="table" :rows="8" />
      <AppError v-else-if="error" :message="error" @retry="loadData" />
      <AppEmpty v-else-if="!tableData.length" description="暂无患者记录" />
      <el-table v-else :data="tableData" stripe style="width: 100%;">
        <el-table-column prop="id" label="ID" width="100" />
        <el-table-column prop="bah" label="病案号" width="140">
          <template #default="{ row }">
            <router-link
              v-if="row.bah"
              :to="{ path: '/archive', query: { bah: row.bah, from: 'patients' } }"
              class="bah-link"
              target="_blank"
              rel="noopener noreferrer"
            >
              {{ row.bah }}
            </router-link>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="idCard" label="身份证号" width="200">
          <template #default="{ row }">
            <el-button
              v-if="patientIdCardRevealEnabled || patientIdCardCopyEnabled"
              link
              class="id-card-toggle"
              @click="handleIdCardClick(row.idCard)"
            >
              {{ isIdCardRevealed(row.idCard) ? row.idCard : maskIdCard(row.idCard) }}
            </el-button>
            <span v-else>{{ maskIdCard(row.idCard) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="department" label="科室" width="150" show-overflow-tooltip />
        <el-table-column prop="bingqu" label="病区" width="150" show-overflow-tooltip />
        <el-table-column prop="chuangwei" label="床位" width="120" show-overflow-tooltip />
        <el-table-column prop="ruyuan" label="入院日期" width="120" />
        <el-table-column prop="admissiontime" label="入院时间" min-width="160" show-overflow-tooltip />
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <PatientImportDialog v-model="importDialogVisible" @imported="handleImported" />
  </div>
</template>

<style scoped>
.page-shell { display: grid; gap: 16px; }
.page-header { display: flex; gap: 16px; align-items: flex-start; justify-content: space-between; }
.header-actions { display: flex; gap: 10px; align-items: center; }
.eyebrow { margin: 0 0 6px; font-size: 12px; font-weight: 700; color: #64748b; text-transform: uppercase; letter-spacing: 0.12em; }
h2 { margin: 0; font-size: 28px; }
.subtitle { margin: 8px 0 0; color: #64748b; }
.filter-bar { display: flex; gap: 12px; align-items: center; }
.filter-input { width: min(560px, 100%); }
.pagination-bar { display: flex; justify-content: flex-end; margin-top: 16px; }
.id-card-toggle { font-size: 12px; }
.bah-link { color: var(--el-color-primary); text-decoration: none; }
.bah-link:hover { text-decoration: underline; }

@media (width <= 760px) {
  .page-header { flex-direction: column; }
  .header-actions { flex-wrap: wrap; width: 100%; }
}
</style>

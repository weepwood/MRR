<script setup lang="ts">
import type { PatientRecord } from '@/api/modules/patients'
import type { MrrTableAction } from '@/components/MrrTableActions/types'
import type { EffectiveSystemSettings } from '@/utils/system-settings'
import { RefreshCw as Refresh, Search, Upload } from 'lucide-vue-next'
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getPatients } from '@/api/modules/patients'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import AppLoading from '@/components/AppLoading/index.vue'
import MrrDataTablePanel from '@/components/MrrDataTablePanel/index.vue'
import MrrFilterActionButton from '@/components/MrrFilterActionButton/index.vue'
import MrrFilterBar from '@/components/MrrFilterBar/index.vue'
import MrrPageHeader from '@/components/MrrPageHeader/index.vue'
import MrrPageShell from '@/components/MrrPageShell/index.vue'
import MrrTableActions from '@/components/MrrTableActions/index.vue'
import { useTableActionLayout } from '@/composables/useTableActionLayout'
import useAuth from '@/utils/composables/useAuth'
import { copyText as copyToClipboard } from '@/utils/copy-text'
import { loadEffectiveSystemSettings, SYSTEM_SETTINGS_UPDATED_EVENT } from '@/utils/system-settings'
import PatientAnalyticsPanel from './components/PatientAnalyticsPanel.vue'
import PatientEditDialog from './components/PatientEditDialog.vue'

defineOptions({ name: 'PatientsPage' })

const router = useRouter()
const loading = ref(false)
const tableData = ref<PatientRecord[]>([])
const error = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const revealedIdCards = ref(new Set<string>())
const patientIdCardRevealEnabled = ref(false)
const patientIdCardCopyEnabled = ref(false)
const editDialogVisible = ref(false)
const editingPatient = ref<PatientRecord>()
const analyticsRefreshKey = ref(0)
const { auth } = useAuth()
const canEditPatients = computed(() => auth('record:edit'))

const filters = reactive({ keyword: '' })
const patientActions: MrrTableAction[] = [
  {
    key: 'edit',
    label: '编辑患者',
    icon: 'i-lucide:pencil',
    tone: 'primary',
    placement: 'inline',
  },
]
const {
  maxInlineActions: patientMaxInlineActions,
  actionColumnWidth: patientActionColumnWidth,
} = useTableActionLayout(patientActions.length, 1)

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

function openDataExchange() {
  void router.push({ path: '/data-exchange', query: { dataset: 'patients' } })
}

function handleEdit(row: PatientRecord) {
  editingPatient.value = { ...row }
  editDialogVisible.value = true
}

function handlePatientAction(action: string, row: PatientRecord) {
  if (action === 'edit') {
    handleEdit(row)
  }
}

function handlePatientSaved(updated: PatientRecord) {
  const index = tableData.value.findIndex(item => item.id === updated.id)
  if (index >= 0) {
    tableData.value[index] = updated
  }
  refreshAll()
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

  await copyToClipboard(value, { success: '身份证号已复制' })
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
  <MrrPageShell compact>
    <MrrPageHeader
      size="compact"
      title="患者管理"
      description="查询和编辑患者基本信息，并查看身份证完整性与重复患者情况。"
    >
      <template #actions>
        <el-button :loading="loading" :icon="Refresh" @click="refreshAll">
          刷新
        </el-button>
        <el-button :icon="Upload" @click="openDataExchange">
          数据导入导出
        </el-button>
      </template>
    </MrrPageHeader>

    <PatientAnalyticsPanel :refresh-key="analyticsRefreshKey">
      <template #patients>
        <MrrDataTablePanel
          title="患者列表"
          description="支持按病案号、姓名、身份证号、科室、病区或床位查询。"
          :count="loading || error ? undefined : total"
          :loading="loading"
          :result-key="tableData"
        >
          <template #filters>
            <MrrFilterBar embedded labelled>
              <div class="mrr-filter-field patient-keyword">
                <label for="patients-keyword">关键词</label>
                <el-input
                  id="patients-keyword"
                  v-model="filters.keyword"
                  aria-label="关键词"
                  placeholder="例如病案号或姓名"
                  clearable
                  @keyup.enter="handleSearch"
                >
                  <template #prefix>
                    <el-icon><Search /></el-icon>
                  </template>
                </el-input>
              </div>
              <template #actions>
                <MrrFilterActionButton action="search" :pending="loading" :busy="loading" @click="handleSearch">
                  查询
                </MrrFilterActionButton>
                <MrrFilterActionButton action="reset" :pending="loading" :busy="loading" @click="resetFilters">
                  重置
                </MrrFilterActionButton>
              </template>
            </MrrFilterBar>
          </template>

          <AppLoading v-if="loading && !tableData.length" type="table" :rows="8" />
          <AppError v-else-if="error" :message="error" @retry="loadData" />
          <AppEmpty v-else-if="!tableData.length" description="暂无患者记录" />
          <!--
            列宽改为 min-width：原来 9 个固定 width 列合计约 1240px（不含操作列），
            1366 宽屏下容器放不下，操作列被挤出视区需要横向滚动；
            宽屏下又不会伸展，右侧留出空白。min-width 让 el-table 按容器分配余量。
          -->
          <el-table v-else :data="tableData" stripe class="patients-table">
            <el-table-column prop="bah" label="病案号" min-width="130">
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
            <el-table-column prop="name" label="姓名" min-width="90" />
            <el-table-column prop="idCard" label="身份证号" min-width="190">
              <template #default="{ row }">
                <el-button
                  v-if="patientIdCardRevealEnabled || patientIdCardCopyEnabled"
                  link
                  class="id-card-toggle"
                  @click="handleIdCardClick(row.idCard)"
                >
                  {{ isIdCardRevealed(row.idCard) ? row.idCard : maskIdCard(row.idCard) }}
                </el-button>
                <span v-else class="patient-id-card">{{ maskIdCard(row.idCard) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="department" label="科室" min-width="120" show-overflow-tooltip />
            <el-table-column prop="bingqu" label="病区" min-width="120" show-overflow-tooltip />
            <el-table-column prop="chuangwei" label="床位" min-width="90" show-overflow-tooltip />
            <el-table-column prop="ruyuan" label="入院日期" min-width="110" />
            <el-table-column prop="admissiontime" label="入院时间" min-width="160" show-overflow-tooltip />
            <el-table-column prop="id" label="ID" min-width="80" />
            <el-table-column
              v-if="canEditPatients"
              label="操作"
              :width="patientActionColumnWidth"
              fixed="right"
              align="center"
            >
              <template #default="{ row }">
                <MrrTableActions
                  :actions="patientActions"
                  :max-inline="patientMaxInlineActions"
                  @select="handlePatientAction($event, row)"
                />
              </template>
            </el-table-column>
          </el-table>

          <template v-if="total > 0 && !error" #pagination>
            <el-pagination
              v-model:current-page="page"
              v-model:page-size="size"
              :total="total"
              :page-sizes="[10, 20, 50, 100]"
              layout="sizes, prev, pager, next, jumper"
              @current-change="handlePageChange"
              @size-change="handleSizeChange"
            />
          </template>
        </MrrDataTablePanel>
      </template>
    </PatientAnalyticsPanel>

    <PatientEditDialog
      v-model="editDialogVisible"
      :patient="editingPatient"
      @saved="handlePatientSaved"
    />
  </MrrPageShell>
</template>

<style scoped>
.patient-keyword {
  flex: 1 1 280px;
}

/* 身份证号用等宽字体：掩码 * 与数字等宽，逐行上下对齐，掩码位数一眼可数。 */
.id-card-toggle,
.patients-table :deep(.patient-id-card) {
  font-family: var(--mrr-font-mono);
  font-size: var(--mrr-font-size-xs);
  letter-spacing: 0.02em;
}

.bah-link {
  font-variant-numeric: tabular-nums;
  color: var(--mrr-navigation-active-foreground);
  text-decoration: underline;
  text-underline-offset: 2px;
}

@media (max-width: 760px) {
  .patient-keyword {
    flex: 1 1 100%;
  }
}
</style>

<script setup lang="ts">
import type { PatientRecord } from '@/api/modules/patients'
import type { MrrTableAction } from '@/components/MrrTableActions/types'
import type { EffectiveSystemSettings } from '@/utils/system-settings'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
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
import { useCrudList } from '@/composables/useCrudList'
import { useTableActionLayout } from '@/composables/useTableActionLayout'
import { useUserStore } from '@/store/modules/user'
import useAuth from '@/utils/composables/useAuth'
import { copyText as copyToClipboard } from '@/utils/copy-text'
import { loadEffectiveSystemSettings, SYSTEM_SETTINGS_UPDATED_EVENT } from '@/utils/system-settings'
import { tableSequenceColumn } from '@/utils/table-sequence'
import PatientAnalyticsPanel from './components/PatientAnalyticsPanel.vue'
import PatientEditDialog from './components/PatientEditDialog.vue'

defineOptions({ name: 'PatientsPage' })

const router = useRouter()
const error = ref('')
const revealedIdCards = ref(new Set<string>())
const patientIdCardRevealEnabled = ref(false)
const patientIdCardCopyEnabled = ref(false)
const editDialogVisible = ref(false)
const editingPatient = ref<PatientRecord>()
const analyticsRefreshKey = ref(0)
const { auth } = useAuth()
const canEditPatients = computed(() => auth('record:edit'))

const userStore = useUserStore()
const canReadPatients = computed(() => userStore.isSessionVerified && auth('record:read'))
const {
  list: tableData,
  total,
  loadedRowOffset,
  pageNum: page,
  pageSize: size,
  loading,
  query: filters,
  loadData,
  handleSearch,
  resetFilters,
  handlePageChange,
  handleSizeChange,
} = useCrudList<PatientRecord, { keyword: string }>({
  defaultQuery: { keyword: '' },
  immediate: false,
  session: {
    key: () => String(userStore.sessionEpoch),
    allowed: () => canReadPatients.value,
    onReset: () => {
      error.value = ''
      revealedIdCards.value.clear()
      editDialogVisible.value = false
      editingPatient.value = undefined
    },
  },
  fetchApi: async (params, { isCurrent }) => {
    if (isCurrent()) { error.value = '' }
    try {
      const res = await getPatients({ page: params.page, size: params.size, ...(params.keyword.trim() && { keyword: params.keyword.trim() }) })
      if (isCurrent()) { revealedIdCards.value.clear() }
      const payload = res.data
      return {
        list: Array.isArray(payload?.list) ? payload.list : [],
        total: Number(payload?.total || 0),
        page: payload?.page ?? params.page,
        size: payload?.size ?? params.size,
      }
    }
    catch (err: unknown) {
      if (isCurrent()) {
        error.value = err instanceof Error ? err.message : '患者列表加载失败'
        revealedIdCards.value.clear()
      }
      return { list: [], total: 0, page: params.page, size: params.size }
    }
  },
})
watch(canEditPatients, (allowed) => {
  if (!allowed) {
    editDialogVisible.value = false
    editingPatient.value = undefined
  }
}, { flush: 'sync' })
watch([() => userStore.sessionEpoch, canReadPatients], ([, allowed]) => {
  if (allowed) { void loadData() }
})

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

function refreshAll() {
  void loadData()
  analyticsRefreshKey.value += 1
}

function openDataExchange() {
  void router.push({ path: '/data-exchange', query: { dataset: 'patients' } })
}

function handleEdit(row: PatientRecord) {
  if (!canReadPatients.value || !canEditPatients.value) { return }
  editingPatient.value = { ...row }
  editDialogVisible.value = true
}

function handlePatientAction(action: string, row: PatientRecord) {
  if (action === 'edit') {
    handleEdit(row)
  }
}

function handlePatientSaved(updated: PatientRecord) {
  if (!canReadPatients.value || !canEditPatients.value || editingPatient.value?.id !== updated.id) { return }
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
  // 点击号码只负责「查看 / 隐藏」这一个动作。
  // 复制是敏感动作：必须由独立的显式按钮发起（见模板中的复制按钮），
  // 静默把完整身份证号送进剪贴板（可被云剪贴板同步出内网）是隐私合规隐患。
  if (!value || !patientIdCardRevealEnabled.value) {
    return
  }
  if (revealedIdCards.value.has(value)) {
    revealedIdCards.value.delete(value)
  }
  else {
    revealedIdCards.value.add(value)
  }
}

async function handleIdCardCopy(value?: string) {
  if (!value || !patientIdCardCopyEnabled.value) {
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
      eyebrow="Patient Management"
      title="患者管理"
      description="查询和编辑患者基本信息，并查看身份证完整性与重复患者情况。"
    >
      <template #actions>
        <el-button :loading="loading" @click="refreshAll">
          <FaIcon v-if="!loading" name="i-lucide:refresh-cw" aria-hidden="true" />
          刷新
        </el-button>
        <el-button @click="openDataExchange">
          <FaIcon name="i-lucide:upload" aria-hidden="true" />
          数据导入导出
        </el-button>
      </template>
    </MrrPageHeader>

    <PatientAnalyticsPanel v-if="canReadPatients" :key="userStore.sessionEpoch" :refresh-key="analyticsRefreshKey">
      <template #patients>
        <MrrDataTablePanel
          title="患者列表"
          description="支持按病案号、姓名、身份证号、科室、病区或床位查询。"
          :count="!loading && !error && !total ? 0 : undefined"
          :loading="loading"
          :result-key="tableData"
        >
          <template #filters>
            <MrrFilterBar embedded :focus-ring="false">
              <div class="mrr-filter-field patient-keyword mrr-filter-field--keyword">
                <el-input
                  id="patients-keyword"
                  v-model="filters.keyword"
                  aria-label="关键词"
                  placeholder="搜索病案号或姓名"
                  clearable
                  @keyup.enter="handleSearch"
                >
                  <template #prefix>
                    <FaIcon name="i-lucide:search" aria-hidden="true" />
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
          <!-- 短字段使用稳定宽度，科室、病区弹性分配余量，窄屏保留原生横向滚动。 -->
          <el-table v-else :data="tableData" stripe class="patients-table">
            <el-table-column v-bind="tableSequenceColumn(loadedRowOffset, tableData.length)" />
            <el-table-column prop="bah" label="病案号" width="132" show-overflow-tooltip>
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
            <el-table-column prop="name" label="姓名" width="112" show-overflow-tooltip />
            <el-table-column prop="idCard" label="身份证号" width="220">
              <template #default="{ row }">
                <!-- 无身份证号的标本行（导入前/带病案号入院）不渲染任何可点控件 -->
                <div class="id-card-cell">
                  <el-button
                    v-if="patientIdCardRevealEnabled && row.idCard"
                    link
                    class="id-card-toggle"
                    @click="handleIdCardClick(row.idCard)"
                  >
                    {{ isIdCardRevealed(row.idCard) ? row.idCard : maskIdCard(row.idCard) }}
                  </el-button>
                  <span v-else class="patient-id-card">{{ maskIdCard(row.idCard) }}</span>
                  <el-button
                    v-if="patientIdCardCopyEnabled && row.idCard"
                    link
                    class="id-card-copy"
                    :aria-label="`复制身份证号（病案号 ${row.bah}）`"
                    @click="handleIdCardCopy(row.idCard)"
                  >
                    <FaIcon name="i-lucide:copy" class="size-3.5" aria-hidden="true" />
                  </el-button>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="department" label="科室" min-width="140" show-overflow-tooltip />
            <el-table-column prop="bingqu" label="病区" min-width="130" show-overflow-tooltip />
            <el-table-column prop="chuangwei" label="床位" width="90" show-overflow-tooltip />
            <el-table-column prop="ruyuan" label="入院日期" width="120" show-overflow-tooltip />
            <el-table-column prop="admissiontime" label="入院时间" width="176" show-overflow-tooltip />
            <el-table-column prop="id" label="ID" width="108" show-overflow-tooltip />
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
              layout="total, prev, pager, next, jumper, sizes"
              @current-change="handlePageChange"
              @size-change="handleSizeChange"
            />
          </template>
        </MrrDataTablePanel>
      </template>
    </PatientAnalyticsPanel>

    <PatientEditDialog
      v-if="canReadPatients && canEditPatients"
      :key="userStore.sessionEpoch"
      v-model="editDialogVisible"
      :patient="editingPatient"
      @saved="handlePatientSaved"
    />
  </MrrPageShell>
</template>

<style scoped>
/* 身份证号用等宽字体：掩码 * 与数字等宽，逐行上下对齐，掩码位数一眼可数。 */
.id-card-toggle,
.id-card-copy,
.patients-table :deep(.patient-id-card) {
  font-family: var(--mrr-font-mono);
  font-size: var(--mrr-font-size-xs);
  letter-spacing: 0.02em;
}

/* 「查看」与「复制」是两个职责分离的显式动作；复制按钮紧贴号码，不抢占点击热区 */
.id-card-cell {
  display: inline-flex;
  gap: var(--mrr-space-1);
  align-items: center;
  min-width: 0;
  max-width: 100%;
}

.id-card-copy {
  flex: 0 0 auto;
  padding: 0 var(--mrr-space-1);
  color: var(--mrr-muted-foreground);
}

.id-card-copy:hover {
  color: var(--mrr-foreground);
}

.bah-link {
  font-variant-numeric: tabular-nums;
  color: var(--mrr-navigation-active-foreground);
  text-decoration: underline;
  text-underline-offset: 2px;
}
</style>

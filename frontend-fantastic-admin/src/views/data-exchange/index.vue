<route lang="yaml">
name: dataExchange
meta:
  title: 数据导入导出
  icon: i-lucide:arrow-left-right
  auth:
    - record:read
    - statistics:read
  cache: true
</route>

<script setup lang="ts">
import type {
  ArchiveBoxExchangeFilters,
  ArchiveExchangeFilters,
  DataExchangeDataset,
  DataExchangeExportDataset,
  PatientExchangeFilters,
  ScanExchangeFilters,
  StatisticsExchangeFilters,
} from '@/api/modules/data-exchange'
import { ElMessage } from 'element-plus'
import { Download, Info as InfoFilled } from 'lucide-vue-next'
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { exportDataExchangeCsv } from '@/api/modules/data-exchange'
import MrrFilterActionButton from '@/components/MrrFilterActionButton/index.vue'
import { rangePickerPopperOptions } from '@/ui/provider/picker-options'
import useAuth from '@/utils/composables/useAuth'
import { downloadBlob, getResponseHeader } from '@/utils/file-download'
import DatasetImportPanel from './components/DatasetImportPanel.vue'

defineOptions({ name: 'DataExchangePage' })

type ExchangeTab = DataExchangeDataset | 'archives'

const route = useRoute()
const router = useRouter()
const { auth } = useAuth()
const canImport = computed(() => auth('record:edit'))
const canReadRecords = computed(() => auth('record:read'))
const canReadStatistics = computed(() => auth('statistics:read'))
const activeTab = ref<ExchangeTab>(normalizeTab(route.query.dataset))

/* 数据集切换使用分段控件；选项顺序与后端支持的数据集保持一致。 */
const datasetTabs: { label: string, value: ExchangeTab }[] = [
  { label: '患者信息', value: 'patients' },
  { label: '统计数据', value: 'statistics' },
  { label: '病案主档', value: 'archives' },
  { label: '档案装箱', value: 'archive-boxes' },
  { label: '扫描记录', value: 'scan' },
]
const exporting = ref<Partial<Record<ExchangeTab, boolean>>>({})

const patientFilters = reactive<PatientExchangeFilters>({ keyword: '' })
const statisticsFilters = reactive<StatisticsExchangeFilters>({
  keyword: '',
  bah: '',
  sjh: '',
  type: '',
  startDate: '',
  endDate: '',
})
const statisticsDateRange = ref<string[]>([])
const archiveFilters = reactive<ArchiveExchangeFilters>({
  keyword: '',
  bah: '',
  sjh: '',
  patientId: '',
  type: '',
  startDate: '',
  endDate: '',
})
const archiveDateRange = ref<string[]>([])
const archiveBoxFilters = reactive<ArchiveBoxExchangeFilters>({
  keyword: '',
  bah: '',
  sjh: '',
  boxNo: '',
  status: '',
})
const scanFilters = reactive<ScanExchangeFilters>({
  bah: '',
  sjh: '',
  brxh: '',
  folder: '',
  filename: '',
  btype: undefined,
  afterId: undefined,
})

const patientFields = [
  { name: 'bah', description: '病案号，必填；保留当前系统编号规则' },
  { name: 'name', description: '患者姓名' },
  { name: 'idcard', description: '身份证号，错误明细自动脱敏' },
  { name: 'ruyuan', description: '入院日期：YYYY-MM-DD' },
  { name: 'admissiontime', description: '入院时间：YYYY-MM-DD HH:MM[:SS]' },
  { name: 'department', description: '住院科室' },
  { name: 'bingqu', description: '病区，不能写成旧字段 binqu' },
  { name: 'chuangwei', description: '床位' },
]

const statisticsFields = [
  { name: 'bah', description: '病案号；与 sjh 不能同时为空' },
  { name: 'cid', description: '扫描设备 ID' },
  { name: 'openerno', description: '负责人或操作人编号' },
  { name: 'date', description: '归档日期：YYYY-MM-DD' },
  { name: 'type', description: '档案类型；空值按未扫描处理' },
  { name: 'pages', description: '非负整数页数' },
  { name: 'sjh', description: '上架号；非空时作为更新依据' },
  { name: 'patientname', description: '患者姓名' },
  { name: 'inpatientdepartment', description: '住院科室' },
  { name: 'patientid', description: '患者 ID，错误明细自动脱敏' },
  { name: 'dischargedate', description: '出院日期：YYYY-MM-DD' },
]

const archiveFields = [
  { name: 'id', description: '稳定病案主键，仅由系统生成' },
  { name: 'sjh', description: '上架号，非空值唯一' },
  { name: 'bah', description: '病案号，可能因历史规则重复' },
  { name: 'patient_id', description: '患者标识' },
  { name: 'patient_name', description: '患者姓名' },
  { name: 'inpatient_department', description: '住院科室' },
  { name: 'device_id', description: '扫描设备 ID' },
  { name: 'operator_no', description: '负责人或操作人编号' },
  { name: 'archive_date', description: '归档日期' },
  { name: 'discharge_date', description: '出院日期' },
  { name: 'archive_type', description: '病案类型' },
  { name: 'page_count', description: '病案页数' },
  { name: 'source_statistics_id', description: '来源统计记录 ID' },
]

const archiveBoxFields = [
  { name: 'bah', description: '病案号；高位病案必须提供 sjh' },
  { name: 'sjh', description: '上架号；优先用于关联病案主档' },
  { name: 'box_no', description: '实际箱号；MISSING 状态可为空' },
  { name: 'expected_box_no', description: '预期箱号' },
  { name: 'status', description: 'NORMAL / MISSING / MISPLACED / CONFLICT / OTHER' },
  { name: 'remark', description: '异常说明或备注' },
]

const scanFields = [
  { name: 'sjh', description: '上架号；非空时可创建最小病案主档' },
  { name: 'bah', description: '病案号；与 sjh 不能同时为空' },
  { name: 'brxh', description: '病人序号' },
  { name: 'folder', description: '原始图片目录，必填' },
  { name: 'filename', description: '图片文件名，必填' },
  { name: 'btype', description: '图片类型 0～15，空值按 0 处理' },
  { name: 'filesize', description: '文件字节数，不能为空单位文本' },
]

watch(() => route.query.dataset, (value) => {
  const next = normalizeTab(value)
  if (next !== activeTab.value) {
    activeTab.value = next
  }
})

watch(activeTab, (value) => {
  if (route.query.dataset === value) {
    return
  }
  void router.replace({ query: { ...route.query, dataset: value } })
})

function normalizeTab(value: unknown): ExchangeTab {
  const supported: ExchangeTab[] = ['patients', 'statistics', 'archives', 'archive-boxes', 'scan']
  const normalized = Array.isArray(value) ? value[0] : value
  return supported.includes(normalized as ExchangeTab) ? normalized as ExchangeTab : 'patients'
}

function compactParams(source: Record<string, unknown>) {
  return Object.fromEntries(
    Object.entries(source).filter(([, value]) => value !== undefined && value !== null && String(value).trim() !== ''),
  ) as Record<string, string | number | undefined>
}

async function exportDataset(
  tab: ExchangeTab,
  dataset: DataExchangeExportDataset,
  params: Record<string, unknown>,
  fileName: string,
) {
  exporting.value[tab] = true
  try {
    const response = await exportDataExchangeCsv(dataset, compactParams(params))
    downloadBlob(response.data, `${fileName}-${today()}.csv`)
    const exportLimit = getResponseHeader(response.headers, 'x-export-row-limit')
    ElMessage.success(exportLimit
      ? `数据导出完成；接口单次最多返回 ${formatCount(exportLimit)} 行`
      : '数据导出完成')
  }
  catch {
    ElMessage.error('数据导出失败')
  }
  finally {
    exporting.value[tab] = false
  }
}

function exportPatients() {
  return exportDataset('patients', 'patients', patientFilters, 'mr_patient')
}

function exportStatistics() {
  const [startDate, endDate] = statisticsDateRange.value
  return exportDataset('statistics', 'statistics', {
    ...statisticsFilters,
    startDate,
    endDate,
  }, 'mr_statistics')
}

function exportArchives() {
  const [startDate, endDate] = archiveDateRange.value
  return exportDataset('archives', 'archives', {
    ...archiveFilters,
    startDate,
    endDate,
  }, 'mr_archive')
}

function exportArchiveBoxes() {
  return exportDataset('archive-boxes', 'archive-boxes', archiveBoxFilters, 'mr_archive_box_record')
}

function exportScans() {
  return exportDataset('scan', 'scan', scanFilters, 'mr_scan')
}

function resetPatientFilters() {
  patientFilters.keyword = ''
}

function resetStatisticsFilters() {
  Object.assign(statisticsFilters, {
    keyword: '',
    bah: '',
    sjh: '',
    type: '',
    startDate: '',
    endDate: '',
  })
  statisticsDateRange.value = []
}

function resetArchiveFilters() {
  Object.assign(archiveFilters, {
    keyword: '',
    bah: '',
    sjh: '',
    patientId: '',
    type: '',
    startDate: '',
    endDate: '',
  })
  archiveDateRange.value = []
}

function resetArchiveBoxFilters() {
  Object.assign(archiveBoxFilters, {
    keyword: '',
    bah: '',
    sjh: '',
    boxNo: '',
    status: '',
  })
}

function resetScanFilters() {
  Object.assign(scanFilters, {
    bah: '',
    sjh: '',
    brxh: '',
    folder: '',
    filename: '',
    btype: undefined,
    afterId: undefined,
  })
}

function formatCount(value: string) {
  const count = Number(value)
  return Number.isFinite(count) && count >= 0 ? count.toLocaleString('zh-CN') : value
}

function today() {
  return new Date().toISOString().slice(0, 10)
}
</script>

<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <p class="eyebrow">
          Data Exchange
        </p>
        <div class="page-title-row">
          <h2>数据导入导出</h2>
          <el-tooltip placement="right" :show-after="200">
            <template #content>
              <span v-if="!canImport">当前账号只有读取权限。可以按权限导出数据；正式导入患者、统计、装箱和扫描记录需要 record:edit 权限。<br></span>
              导出采用受控行数上限：所有条件导出单次最多返回 100,000 行。结果可能超过上限时，请缩小筛选范围；扫描记录可使用 afterId 按主键游标分卷导出。
            </template>
            <button type="button" class="info-trigger" aria-label="数据交换说明">
              <el-icon><InfoFilled /></el-icon>
            </button>
          </el-tooltip>
        </div>
        <p class="subtitle">
          患者、统计、病案主档、档案装箱和扫描记录统一在此导入导出。系统只开放预定义数据集，不接受任意表名、SQL 或服务器路径。
        </p>
      </div>
    </div>

    <el-segmented
      v-model="activeTab"
      class="dataset-switch"
      :options="datasetTabs"
      block
      aria-label="数据集切换"
    />

    <div v-show="activeTab === 'patients'" class="tab-content">
      <el-card shadow="never">
        <template #header>
          <div class="panel-heading">
            <div>
              <h3>患者数据条件导出</h3>
              <p>导出字段与患者导入模板保持一致，可在本页面重新校验和导入。</p>
            </div>
          </div>
        </template>
        <div class="filter-grid filter-grid--patient">
          <el-input v-model="patientFilters.keyword" clearable placeholder="病案号 / 姓名 / 身份证号 / 科室" />
          <div class="filter-actions">
            <MrrFilterActionButton action="reset" @click="resetPatientFilters">
              重置
            </MrrFilterActionButton>
            <el-button
              type="primary"
              :icon="Download"
              :loading="exporting.patients"
              :disabled="!canReadRecords"
              @click="exportPatients"
            >
              导出 CSV
            </el-button>
          </div>
        </div>
      </el-card>

      <DatasetImportPanel
        v-if="canImport"
        dataset="patients"
        title="患者数据导入"
        description="复用患者管理已经验证的两步导入服务；文件内和数据库内完全重复行会自动跳过。"
        :fields="patientFields"
        max-file-size="单文件不超过 20 MB"
      />
      <el-card v-else shadow="never">
        <div class="readonly-fields">
          <h3>患者数据字段</h3>
          <div class="field-grid">
            <div v-for="field in patientFields" :key="field.name" class="field-item">
              <code>{{ field.name }}</code><span>{{ field.description }}</span>
            </div>
          </div>
        </div>
      </el-card>
    </div>

    <div v-show="activeTab === 'statistics'" class="tab-content">
      <el-card shadow="never">
        <template #header>
          <div class="panel-heading">
            <div>
              <h3>统计数据条件导出</h3>
              <p>按病案号、上架号、档案类型、日期和综合关键字筛选。</p>
            </div>
          </div>
        </template>
        <div class="filter-grid">
          <el-input v-model="statisticsFilters.bah" clearable placeholder="病案号" />
          <el-input v-model="statisticsFilters.sjh" clearable placeholder="上架号" />
          <el-input v-model="statisticsFilters.type" clearable placeholder="档案类型" />
          <el-input v-model="statisticsFilters.keyword" clearable placeholder="患者、科室、设备或负责人" />
          <el-date-picker
            v-model="statisticsDateRange"
            type="daterange"
            :popper-options="rangePickerPopperOptions"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
          <div class="filter-actions">
            <MrrFilterActionButton action="reset" @click="resetStatisticsFilters">
              重置
            </MrrFilterActionButton>
            <el-button
              type="primary"
              :icon="Download"
              :loading="exporting.statistics"
              :disabled="!canReadStatistics"
              @click="exportStatistics"
            >
              导出 CSV
            </el-button>
          </div>
        </div>
      </el-card>

      <DatasetImportPanel
        v-if="canImport"
        dataset="statistics"
        title="统计数据导入"
        description="非空上架号命中现有记录时更新；无上架号记录按完整业务字段精确去重，并由触发器同步病案主档。"
        :fields="statisticsFields"
        max-file-size="单文件不超过 50 MB"
      />
    </div>

    <div v-show="activeTab === 'archives'" class="tab-content">
      <el-card shadow="never">
        <template #header>
          <div class="panel-heading">
            <div>
              <div class="page-title-row">
                <h3>病案主档条件导出</h3>
                <el-tooltip placement="right" :show-after="200">
                  <template #content>
                    病案主档只允许导出。mr_archive 是由统计数据和稳定关联规则维护的主数据。禁止直接导入，以免绕过触发器、破坏 archive_id 关联或制造重复上架号。
                  </template>
                  <button type="button" class="info-trigger" aria-label="病案主档导出说明">
                    <el-icon><InfoFilled /></el-icon>
                  </button>
                </el-tooltip>
              </div>
              <p>用于审计、核对和迁移前检查，不作为直接回写模板。</p>
            </div>
          </div>
        </template>
        <div class="filter-grid">
          <el-input v-model="archiveFilters.bah" clearable placeholder="病案号" />
          <el-input v-model="archiveFilters.sjh" clearable placeholder="上架号" />
          <el-input v-model="archiveFilters.patientId" clearable placeholder="患者 ID" />
          <el-input v-model="archiveFilters.type" clearable placeholder="档案类型" />
          <el-input v-model="archiveFilters.keyword" clearable placeholder="患者姓名 / 科室 / 设备 / 操作人" />
          <el-date-picker
            v-model="archiveDateRange"
            type="daterange"
            :popper-options="rangePickerPopperOptions"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
          <div class="filter-actions">
            <MrrFilterActionButton action="reset" @click="resetArchiveFilters">
              重置
            </MrrFilterActionButton>
            <el-button
              type="primary"
              :icon="Download"
              :loading="exporting.archives"
              :disabled="!canReadRecords"
              @click="exportArchives"
            >
              导出 CSV
            </el-button>
          </div>
        </div>
      </el-card>

      <el-card shadow="never">
        <div class="readonly-fields">
          <h3>病案主档导出字段</h3>
          <div class="field-grid">
            <div v-for="field in archiveFields" :key="field.name" class="field-item">
              <code>{{ field.name }}</code><span>{{ field.description }}</span>
            </div>
          </div>
        </div>
      </el-card>
    </div>

    <div v-show="activeTab === 'archive-boxes'" class="tab-content">
      <el-card shadow="never">
        <template #header>
          <div class="panel-heading">
            <div>
              <h3>档案装箱条件导出</h3>
              <p>按病案号、上架号、箱号、状态或备注筛选装箱记录。</p>
            </div>
          </div>
        </template>
        <div class="filter-grid">
          <el-input v-model="archiveBoxFilters.bah" clearable placeholder="病案号" />
          <el-input v-model="archiveBoxFilters.sjh" clearable placeholder="上架号" />
          <el-input v-model="archiveBoxFilters.boxNo" clearable placeholder="箱号" />
          <el-select v-model="archiveBoxFilters.status" clearable placeholder="全部状态">
            <el-option label="正常" value="NORMAL" />
            <el-option label="缺失" value="MISSING" />
            <el-option label="位置不一致" value="MISPLACED" />
            <el-option label="冲突" value="CONFLICT" />
            <el-option label="其他" value="OTHER" />
          </el-select>
          <el-input v-model="archiveBoxFilters.keyword" clearable placeholder="综合关键字" />
          <div class="filter-actions">
            <MrrFilterActionButton action="reset" @click="resetArchiveBoxFilters">
              重置
            </MrrFilterActionButton>
            <el-button
              type="primary"
              :icon="Download"
              :loading="exporting['archive-boxes']"
              :disabled="!canReadRecords"
              @click="exportArchiveBoxes"
            >
              导出 CSV
            </el-button>
          </div>
        </div>
      </el-card>

      <DatasetImportPanel
        v-if="canImport"
        dataset="archive-boxes"
        title="档案装箱导入"
        description="导入前解析稳定 archive_id；无法唯一关联病案主档或同一病案存在冲突时整批拒绝。"
        :fields="archiveBoxFields"
        max-file-size="单文件不超过 20 MB"
      />
    </div>

    <div v-show="activeTab === 'scan'" class="tab-content">
      <el-card shadow="never">
        <template #header>
          <div class="panel-heading">
            <div>
              <div class="page-title-row">
                <h3>扫描记录条件导出</h3>
                <el-tooltip placement="right" :show-after="200">
                  <template #content>
                    浏览器只处理小批量扫描记录。单次最多 100,000 行。三千万级全量迁移仍应使用分卷 CSV、后端流式 COPY、任务暂停恢复、失败重试和主键游标导出，不应通过浏览器上传。
                  </template>
                  <button type="button" class="info-trigger" aria-label="扫描记录导入说明">
                    <el-icon><InfoFilled /></el-icon>
                  </button>
                </el-tooltip>
              </div>
              <p>导出按 ID 升序，afterId 可用于手工分卷；单次最多返回 100,000 行。</p>
            </div>
          </div>
        </template>
        <div class="filter-grid filter-grid--scan">
          <el-input-number v-model="scanFilters.afterId" :min="0" controls-position="right" placeholder="起始 ID（不含）" />
          <el-input v-model="scanFilters.bah" clearable placeholder="病案号" />
          <el-input v-model="scanFilters.sjh" clearable placeholder="上架号" />
          <el-input v-model="scanFilters.brxh" clearable placeholder="病人序号" />
          <el-input v-model="scanFilters.folder" clearable placeholder="目录" />
          <el-input v-model="scanFilters.filename" clearable placeholder="文件名" />
          <el-input-number v-model="scanFilters.btype" :min="0" :max="15" controls-position="right" placeholder="图片类型" />
          <div class="filter-actions">
            <MrrFilterActionButton action="reset" @click="resetScanFilters">
              重置
            </MrrFilterActionButton>
            <el-button
              type="primary"
              :icon="Download"
              :loading="exporting.scan"
              :disabled="!canReadRecords"
              @click="exportScans"
            >
              导出 CSV
            </el-button>
          </div>
        </div>
      </el-card>

      <DatasetImportPanel
        v-if="canImport"
        dataset="scan"
        title="扫描记录小批量导入"
        description="以 folder + filename 作为受控定位条件；完全重复跳过，已存在路径按模板字段更新，新路径创建扫描记录并关联病案主档。"
        :fields="scanFields"
        max-file-size="单文件不超过 50 MB"
      />
    </div>
  </div>
</template>

<style scoped>
.page-shell,
.tab-content,
.readonly-fields {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
  min-width: 0;
}

.page-header,
.panel-heading {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.page-title-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.info-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  color: var(--el-text-color-secondary);
  cursor: help;
  background: transparent;
  border: 0;
  border-radius: var(--mrr-radius-pill);
}

.info-trigger:hover,
.info-trigger:focus-visible {
  color: var(--el-color-primary);
}

.info-trigger:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

.eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--el-text-color-secondary);
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

h2,
h3 {
  margin: 0;
}

h2 {
  font-size: 28px;
}

.panel-heading h3 {
  font-size: 18px;
}

.subtitle,
.panel-heading p {
  margin: 8px 0 0;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

/*
 * 数据集切换：浅灰底槽 + 白色滑块的分段控件。
 * 圆角跟随全局「界面圆角」设置（--mrr-radius-lg），不写死 px。
 */
.dataset-switch {
  --el-segmented-bg-color: var(--mrr-muted);
  --el-segmented-color: var(--mrr-muted-foreground);
  --el-segmented-padding: 4px;
  --el-segmented-item-selected-bg-color: #fff;
  --el-segmented-item-selected-color: var(--mrr-foreground);
  --el-segmented-item-hover-bg-color: rgb(15 23 42 / 4%);
  --el-segmented-item-active-bg-color: rgb(15 23 42 / 7%);

  width: 100%;
  min-height: 40px;
  overflow: hidden;
  font-size: 13px;
  border-radius: var(--mrr-radius-lg);
}

.dataset-switch :deep(.el-segmented__item) {
  font-weight: 500;
  border-radius: clamp(0px, calc(var(--mrr-radius-lg) - 2px), 20px);
}

.dataset-switch :deep(.el-segmented__item.is-selected) {
  font-weight: 600;
}

.dataset-switch :deep(.el-segmented__item-selected) {
  border-radius: clamp(0px, calc(var(--mrr-radius-lg) - 2px), 20px);
  box-shadow: 0 1px 2px rgb(15 23 42 / 10%), 0 2px 6px rgb(15 23 42 / 5%);
}

@media (max-width: 720px) {
  .dataset-switch {
    font-size: 12px;
  }

  .dataset-switch :deep(.el-segmented__item) {
    padding: 0 4px;
  }
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
  align-items: center;
}

.filter-grid :deep(.el-date-editor),
.filter-grid :deep(.el-input-number) {
  width: 100%;
}

.filter-grid--patient {
  grid-template-columns: minmax(0, 1fr) auto;
}

.filter-grid--scan {
  grid-template-columns: repeat(4, minmax(190px, 1fr));
}

.filter-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.filter-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
}

.field-item {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
  padding: 8px 10px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--mrr-radius-md);
}

.field-item code {
  flex: 0 0 148px;
  font-weight: 700;
  color: var(--el-color-primary);
}

.field-item span {
  min-width: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 1100px) {
  .filter-grid--patient {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .filter-grid--scan {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .page-header,
  .panel-heading {
    flex-direction: column;
  }

  .filter-grid,
  .field-grid {
    grid-template-columns: 1fr;
  }

  .filter-grid--patient {
    grid-template-columns: 1fr;
  }

  .filter-grid--scan {
    grid-template-columns: 1fr;
  }
}
</style>

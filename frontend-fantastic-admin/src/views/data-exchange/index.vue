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
import { Download } from 'lucide-vue-next'
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { exportDataExchangeCsv } from '@/api/modules/data-exchange'
import MrrFilterActionButton from '@/components/MrrFilterActionButton/index.vue'
import MrrInfoTip from '@/components/MrrInfoTip/index.vue'
import MrrPageHeader from '@/components/MrrPageHeader/index.vue'
import MrrPageShell from '@/components/MrrPageShell/index.vue'
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'
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
  <MrrPageShell class="data-exchange-page">
    <MrrPageHeader
      eyebrow="Data Exchange"
      title="数据导入导出"
      description="患者、统计、病案主档、档案装箱和扫描记录统一在此导入导出。系统只开放预定义数据集，不接受任意表名、SQL 或服务器路径。"
    >
      <template #badge>
        <MrrInfoTip label="数据交换说明">
          <span v-if="!canImport">当前账号只有读取权限。可以按权限导出数据；正式导入患者、统计、装箱和扫描记录需要 record:edit 权限。<br></span>
          导出采用受控行数上限：所有条件导出单次最多返回 100,000 行。结果可能超过上限时，请缩小筛选范围；扫描记录可使用 afterId 按主键游标分卷导出。
        </MrrInfoTip>
      </template>
    </MrrPageHeader>

    <el-segmented
      v-model="activeTab"
      class="dataset-switch"
      :options="datasetTabs"
      block
      aria-label="数据集切换"
    />

    <div v-show="activeTab === 'patients'" class="tab-content">
      <MrrSectionCard title="患者数据条件导出" description="导出字段与患者导入模板保持一致，可在本页面重新校验和导入。">
        <div class="filter-grid filter-grid--patient">
          <el-input v-model="patientFilters.keyword" clearable aria-label="病案号 / 姓名 / 身份证号 / 科室" placeholder="病案号 / 姓名 / 身份证号 / 科室" />
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
      </MrrSectionCard>

      <DatasetImportPanel
        v-if="canImport"
        dataset="patients"
        title="患者数据导入"
        description="复用患者管理已经验证的两步导入服务；文件内和数据库内完全重复行会自动跳过。"
        :fields="patientFields"
        max-file-size="单文件不超过 20 MB"
      />
      <MrrSectionCard v-else title="患者数据字段" description="当前账号只能导出；以下为导出文件包含的字段。">
        <div class="mrr-field-grid">
          <div v-for="field in patientFields" :key="field.name" class="mrr-field-item">
            <code>{{ field.name }}</code><span>{{ field.description }}</span>
          </div>
        </div>
      </MrrSectionCard>
    </div>

    <div v-show="activeTab === 'statistics'" class="tab-content">
      <MrrSectionCard title="统计数据条件导出" description="按病案号、上架号、档案类型、日期和综合关键字筛选。">
        <div class="filter-grid">
          <el-input v-model="statisticsFilters.bah" clearable aria-label="病案号" placeholder="病案号" />
          <el-input v-model="statisticsFilters.sjh" clearable aria-label="上架号" placeholder="上架号" />
          <el-input v-model="statisticsFilters.type" clearable aria-label="档案类型" placeholder="档案类型" />
          <el-input v-model="statisticsFilters.keyword" clearable aria-label="患者、科室、设备或负责人" placeholder="患者、科室、设备或负责人" />
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
      </MrrSectionCard>

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
      <MrrSectionCard title="病案主档条件导出" description="用于审计、核对和迁移前检查，不作为直接回写模板。">
        <template #title-suffix>
          <MrrInfoTip label="病案主档导出说明">
            病案主档只允许导出。mr_archive 是由统计数据和稳定关联规则维护的主数据。禁止直接导入，以免绕过触发器、破坏 archive_id 关联或制造重复上架号。
          </MrrInfoTip>
        </template>
        <div class="filter-grid">
          <el-input v-model="archiveFilters.bah" clearable aria-label="病案号" placeholder="病案号" />
          <el-input v-model="archiveFilters.sjh" clearable aria-label="上架号" placeholder="上架号" />
          <el-input v-model="archiveFilters.patientId" clearable aria-label="患者 ID" placeholder="患者 ID" />
          <el-input v-model="archiveFilters.type" clearable aria-label="档案类型" placeholder="档案类型" />
          <el-input v-model="archiveFilters.keyword" clearable aria-label="患者姓名 / 科室 / 设备 / 操作人" placeholder="患者姓名 / 科室 / 设备 / 操作人" />
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
      </MrrSectionCard>

      <MrrSectionCard title="病案主档导出字段" description="导出文件包含以下字段，仅用于核对，不作为导入模板。">
        <div class="mrr-field-grid">
          <div v-for="field in archiveFields" :key="field.name" class="mrr-field-item">
            <code>{{ field.name }}</code><span>{{ field.description }}</span>
          </div>
        </div>
      </MrrSectionCard>
    </div>

    <div v-show="activeTab === 'archive-boxes'" class="tab-content">
      <MrrSectionCard title="档案装箱条件导出" description="按病案号、上架号、箱号、状态或备注筛选装箱记录。">
        <div class="filter-grid">
          <el-input v-model="archiveBoxFilters.bah" clearable aria-label="病案号" placeholder="病案号" />
          <el-input v-model="archiveBoxFilters.sjh" clearable aria-label="上架号" placeholder="上架号" />
          <el-input v-model="archiveBoxFilters.boxNo" clearable aria-label="箱号" placeholder="箱号" />
          <el-select v-model="archiveBoxFilters.status" clearable aria-label="全部状态" placeholder="全部状态">
            <el-option label="正常" value="NORMAL" />
            <el-option label="缺失" value="MISSING" />
            <el-option label="位置不一致" value="MISPLACED" />
            <el-option label="冲突" value="CONFLICT" />
            <el-option label="其他" value="OTHER" />
          </el-select>
          <el-input v-model="archiveBoxFilters.keyword" clearable aria-label="综合关键字" placeholder="综合关键字" />
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
      </MrrSectionCard>

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
      <MrrSectionCard title="扫描记录条件导出" description="导出按 ID 升序，afterId 可用于手工分卷；单次最多返回 100,000 行。">
        <template #title-suffix>
          <MrrInfoTip label="扫描记录导入说明">
            浏览器只处理小批量扫描记录。单次最多 100,000 行。三千万级全量迁移仍应使用分卷 CSV、后端流式 COPY、任务暂停恢复、失败重试和主键游标导出，不应通过浏览器上传。
          </MrrInfoTip>
        </template>
        <div class="filter-grid filter-grid--scan">
          <el-input-number v-model="scanFilters.afterId" :min="0" controls-position="right" aria-label="起始 ID（不含）" placeholder="起始 ID（不含）" />
          <el-input v-model="scanFilters.bah" clearable aria-label="病案号" placeholder="病案号" />
          <el-input v-model="scanFilters.sjh" clearable aria-label="上架号" placeholder="上架号" />
          <el-input v-model="scanFilters.brxh" clearable aria-label="病人序号" placeholder="病人序号" />
          <el-input v-model="scanFilters.folder" clearable aria-label="目录" placeholder="目录" />
          <el-input v-model="scanFilters.filename" clearable aria-label="文件名" placeholder="文件名" />
          <el-input-number v-model="scanFilters.btype" :min="0" :max="15" controls-position="right" aria-label="图片类型" placeholder="图片类型" />
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
      </MrrSectionCard>

      <DatasetImportPanel
        v-if="canImport"
        dataset="scan"
        title="扫描记录小批量导入"
        description="以 folder + filename 作为受控定位条件；完全重复跳过，已存在路径按模板字段更新，新路径创建扫描记录并关联病案主档。"
        :fields="scanFields"
        max-file-size="单文件不超过 50 MB"
      />
    </div>
  </MrrPageShell>
</template>

<style scoped>
/* 页面级间距、宽度与页头由 MrrPageShell / MrrPageHeader 统一提供，这里只保留页内分组的 16px。 */
.tab-content {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--mrr-space-4);
  min-width: 0;
}

/*
 * 数据集切换：浅灰底槽 + 卡面滑块的分段控件。
 * 圆角跟随全局「界面圆角」设置（--mrr-radius-lg），不写死 px。
 * 滑块底色必须取 --mrr-card 而不是写死白色：暗色主题下白色滑块配
 * --mrr-foreground 的浅色文字只有 1.04:1，选中的数据集完全读不出来。
 */
.dataset-switch {
  --el-segmented-bg-color: var(--mrr-muted);
  --el-segmented-color: var(--mrr-muted-foreground);
  --el-segmented-padding: 4px;
  --el-segmented-item-selected-bg-color: var(--mrr-card);
  --el-segmented-item-selected-color: var(--mrr-foreground);

  /* 悬停 / 按下用前景色叠加，跟随明暗主题；原写死的冷色墨在暗色下不可见、在暖色主题下偏蓝。 */
  --el-segmented-item-hover-bg-color: color-mix(in srgb, var(--mrr-foreground) 5%, transparent);
  --el-segmented-item-active-bg-color: color-mix(in srgb, var(--mrr-foreground) 9%, transparent);

  width: 100%;
  min-height: 40px;
  overflow: hidden;
  font-size: var(--mrr-font-size-sm);
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
  box-shadow: var(--mrr-shadow-xs), 0 0 0 1px var(--mrr-border);
}

@media (max-width: 720px) {
  .dataset-switch {
    font-size: var(--mrr-font-size-xs);
  }

  .dataset-switch :deep(.el-segmented__item) {
    padding: 0 var(--mrr-space-1);
  }
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: var(--mrr-space-3);
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
  gap: var(--mrr-space-2);
  justify-content: flex-end;
}

.filter-actions :deep(.el-button + .el-button) {
  margin-left: 0;
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
  .filter-grid,
  .filter-grid--patient {
    grid-template-columns: 1fr;
  }

  .filter-grid--scan {
    grid-template-columns: 1fr;
  }
}
</style>

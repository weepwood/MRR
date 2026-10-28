<script setup lang="ts">
import type {
  PatientAnalyticsSummary,
  PatientMultiRecordGroup,
  PatientRecord,
} from '@/api/modules/patients'
import { onMounted, ref, watch } from 'vue'
import {
  getMissingIdCardPatients,
  getPatientAnalyticsSummary,
  getPatientMultiRecordGroups,
} from '@/api/modules/patients'

const props = withDefaults(defineProps<{
  refreshKey?: number
}>(), {
  refreshKey: 0,
})

const currentYear = new Date().getFullYear()

const summaryLoading = ref(false)
const summaryError = ref('')
const summary = ref<PatientAnalyticsSummary>()

const activeDetailTab = ref<'patients' | 'missing' | 'multi'>('patients')
const missingLoading = ref(false)
const missingRecords = ref<PatientRecord[]>([])
const missingPage = ref(1)
const missingSize = ref(10)
const missingTotal = ref(0)

const multiLoading = ref(false)
const multiGroups = ref<PatientMultiRecordGroup[]>([])
const multiPage = ref(1)
const multiSize = ref(10)
const multiTotal = ref(0)
const includeSuspected = ref(true)

async function loadSummary() {
  summaryLoading.value = true
  summaryError.value = ''
  try {
    const response = await getPatientAnalyticsSummary(currentYear)
    summary.value = response.data
  }
  catch (error: unknown) {
    summary.value = undefined
    summaryError.value = error instanceof Error ? error.message : '患者统计加载失败'
  }
  finally {
    summaryLoading.value = false
  }
}

async function loadMissingRecords() {
  missingLoading.value = true
  try {
    const response = await getMissingIdCardPatients({
      page: missingPage.value,
      size: missingSize.value,
    })
    missingRecords.value = response.data?.list ?? []
    missingTotal.value = Number(response.data?.total || 0)
  }
  finally {
    missingLoading.value = false
  }
}

async function loadMultiGroups() {
  multiLoading.value = true
  try {
    const response = await getPatientMultiRecordGroups({
      page: multiPage.value,
      size: multiSize.value,
      includeSuspected: includeSuspected.value,
    })
    multiGroups.value = response.data?.list ?? []
    multiTotal.value = Number(response.data?.total || 0)
  }
  finally {
    multiLoading.value = false
  }
}

function reloadAll() {
  void loadSummary()
  void loadMissingRecords()
  void loadMultiGroups()
}

function handleMissingPageChange(page: number) {
  missingPage.value = page
  void loadMissingRecords()
}

function handleMissingSizeChange(size: number) {
  missingSize.value = size
  missingPage.value = 1
  void loadMissingRecords()
}

function handleMultiPageChange(page: number) {
  multiPage.value = page
  void loadMultiGroups()
}

function handleMultiSizeChange(size: number) {
  multiSize.value = size
  multiPage.value = 1
  void loadMultiGroups()
}

watch(includeSuspected, () => {
  multiPage.value = 1
  void loadMultiGroups()
})

watch(() => props.refreshKey, reloadAll)

onMounted(reloadAll)
</script>

<template>
  <section class="analytics-panel">
    <el-alert
      v-if="summaryError"
      :title="summaryError"
      type="error"
      show-icon
      :closable="false"
    />

    <div v-loading="summaryLoading" class="mrr-metric-grid">
      <MrrMetricCard
        label="患者数据行"
        icon="i-lucide:user-round"
        :value="summary?.totalRecords ?? 0"
        :note="`去重病案号 ${summary?.totalArchives ?? 0} 个`"
      />
      <MrrMetricCard
        :label="`${currentYear} 年病案`"
        icon="i-lucide:calendar-days"
        :value="summary?.yearArchives ?? 0"
        note="按入院日期去重统计"
      />
      <MrrMetricCard
        label="身份证号为空"
        icon="i-lucide:triangle-alert"
        tone="warning"
        :value="summary?.missingIdCardRecords ?? 0"
        note="需要补录或人工核对"
      />
      <MrrMetricCard
        label="同一患者多病案"
        icon="i-lucide:building-2"
        :value="summary?.confirmedMultiRecordGroups ?? 0"
        :note="`另有疑似 ${summary?.suspectedMultiRecordGroups ?? 0} 组`"
      />
    </div>

    <slot name="search" />

    <el-card shadow="never" class="detail-card">
      <el-tabs v-model="activeDetailTab">
        <el-tab-pane label="患者表格" name="patients">
          <slot name="patients" />
        </el-tab-pane>

        <el-tab-pane name="missing">
          <template #label>
            身份证为空病案（{{ summary?.missingIdCardRecords ?? missingTotal }}）
          </template>
          <el-table v-loading="missingLoading" :data="missingRecords" stripe>
            <el-table-column prop="bah" label="病案号" min-width="140">
              <template #default="{ row }">
                <router-link
                  v-if="row.bah"
                  :to="{ path: '/archive', query: { bah: row.bah, from: 'patients' } }"
                  target="_blank"
                  class="record-link"
                >
                  {{ row.bah }}
                </router-link>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="姓名" min-width="100" />
            <el-table-column prop="ruyuan" label="入院日期" min-width="120" />
            <el-table-column prop="admissiontime" label="入院时间" min-width="170" />
            <el-table-column prop="department" label="科室" min-width="140" show-overflow-tooltip />
            <el-table-column prop="bingqu" label="病区" min-width="130" show-overflow-tooltip />
            <el-table-column prop="chuangwei" label="床位" min-width="100" />
          </el-table>
          <div class="pagination-bar">
            <el-pagination
              v-model:current-page="missingPage"
              v-model:page-size="missingSize"
              :total="missingTotal"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="handleMissingPageChange"
              @size-change="handleMissingSizeChange"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane name="multi">
          <template #label>
            同一患者多病案（{{ multiTotal }}）
          </template>
          <div class="multi-toolbar">
            <el-switch v-model="includeSuspected" active-text="包含仅姓名相同的疑似分组" />
            <span>身份证号一致为高可信；仅姓名相同可能是同名患者，需要人工核对。</span>
          </div>
          <el-table v-loading="multiLoading" :data="multiGroups" stripe>
            <el-table-column label="识别依据" width="130">
              <template #default="{ row }">
                <el-tag :type="row.matchType === 'IDCARD' ? 'success' : 'warning'">
                  {{ row.matchType === 'IDCARD' ? '身份证一致' : '仅姓名相同' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="patientName" label="患者姓名" min-width="140" show-overflow-tooltip />
            <el-table-column prop="maskedIdCard" label="身份证号" min-width="190">
              <template #default="{ row }">
                {{ row.maskedIdCard || '—' }}
              </template>
            </el-table-column>
            <el-table-column prop="archiveCount" label="病案数" width="90" />
            <el-table-column label="病案号" min-width="280">
              <template #default="{ row }">
                <div class="archive-tags">
                  <router-link
                    v-for="archiveNumber in row.archiveNumbers"
                    :key="archiveNumber"
                    :to="{ path: '/archive', query: { bah: archiveNumber, from: 'patients' } }"
                    target="_blank"
                    class="record-link"
                  >
                    {{ archiveNumber }}
                  </router-link>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="入院日期范围" min-width="210">
              <template #default="{ row }">
                {{ row.firstAdmissionDate || '—' }} ～ {{ row.lastAdmissionDate || '—' }}
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-bar">
            <el-pagination
              v-model:current-page="multiPage"
              v-model:page-size="multiSize"
              :total="multiTotal"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="handleMultiPageChange"
              @size-change="handleMultiSizeChange"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </section>
</template>

<style scoped>
.analytics-panel {
  display: grid;
  gap: 16px;
}

.detail-card :deep(.el-card__body) {
  padding-top: 4px;
}

.multi-toolbar {
  display: flex;
  gap: 14px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.record-link {
  color: var(--el-color-primary);
  text-decoration: none;
}

.record-link:hover {
  text-decoration: underline;
}

.archive-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
}

@media (max-width: 720px) {
  .multi-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>

<script setup lang="ts">
import { Download, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
// 请根据实际项目路径调整 API 导入，当前路径 @/api/modules/records 不存在
// 示例：如果 API 位于 @/api/records，请修改为：
// import { batchDownloadRecords, getScanByCondition, getScanList } from '@/api/records'
import { batchDownloadRecords, getScanByCondition, getScanList } from '@/api/modules/records'

defineOptions({ name: 'RecordsPage' })

const loading = ref(false)
const downloading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const detailVisible = ref(false)
const currentRecord = ref<any>(null)
const selectedRows = ref<any[]>([])

const filters = reactive({
  bah: '',
  brxh: '',
  openerNo: '',
  btype: '',
  uploadFlag: '',
})

const typeOptions = [
  { label: '全部类型', value: '' },
  { label: '病案首页', value: '1' },
  { label: '病程记录', value: '2' },
  { label: '手术记录', value: '3' },
  { label: '护理记录', value: '5' },
  { label: '检验单', value: '8' },
  { label: '医嘱', value: '9' },
]

const summaryCards = computed(() => [
  { label: '当前页记录数', value: tableData.value.length, note: '当前筛选结果中已加载的数据条数' },
  { label: '总记录数', value: total.value, note: '符合当前筛选条件的扫描记录总量' },
  { label: '已选记录', value: selectedRows.value.length, note: '可用于批量打包下载' },
  { label: '当前页码', value: page.value, note: `每页 ${size.value} 条` },
])

function buildRequest() {
  return {
    bah: filters.bah.trim() || undefined,
    brxh: filters.brxh.trim() || undefined,
    openerNo: filters.openerNo.trim() || undefined,
    btype: filters.btype || undefined,
    uploadFlag: filters.uploadFlag || undefined,
  }
}

async function loadData() {
  loading.value = true
  try {
    const hasConditions = Object.values(buildRequest()).some(Boolean)
    const response = hasConditions
      ? await getScanByCondition({ ...buildRequest(), page: page.value, size: size.value } as any)
      : await getScanList({ page: page.value, size: size.value })

    const payload = response.data || {}
    tableData.value = Array.isArray(payload.list) ? payload.list : []
    total.value = Number(payload.total || tableData.value.length || 0)
  }
  catch (error: any) {
    tableData.value = []
    total.value = 0
    ElMessage.error(error?.message || '记录加载失败')
  }
  finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  loadData()
}

function resetFilters() {
  filters.bah = ''
  filters.brxh = ''
  filters.openerNo = ''
  filters.btype = ''
  filters.uploadFlag = ''
  handleSearch()
}

function handleSelectionChange(rows: any[]) {
  selectedRows.value = rows
}

function openDetail(row: any) {
  currentRecord.value = row
  detailVisible.value = true
}

async function handleBatchDownload() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请先选择要下载的记录')
    return
  }

  downloading.value = true
  try {
    const result = await batchDownloadRecords(selectedRows.value.map(row => row.id))
    const blob = result instanceof Blob ? result : result?.data
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `scan-batch-${Date.now()}.zip`
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success('批量下载已开始')
  }
  catch (error: any) {
    ElMessage.error(error?.message || '批量下载失败')
  }
  finally {
    downloading.value = false
  }
}

function typeLabel(value: unknown) {
  const option = typeOptions.find(item => item.value === String(value))
  return option?.label || (value ? `类型 ${value}` : '-')
}

onMounted(loadData)
</script>

<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <p class="eyebrow">
          Scan Records
        </p>
        <h2>记录管理</h2>
        <p class="subtitle">
          管理扫描记录、按条件检索明细，并支持多选后批量打包下载。
        </p>
      </div>
      <el-button type="primary" :loading="downloading" @click="handleBatchDownload">
        <el-icon><Download /></el-icon>
        批量下载
      </el-button>
    </div>

    <section class="summary-grid">
      <el-card v-for="item in summaryCards" :key="item.label" shadow="never">
        <div class="summary-label">
          {{ item.label }}
        </div>
        <div class="summary-value">
          {{ item.value }}
        </div>
        <div class="summary-note">
          {{ item.note }}
        </div>
      </el-card>
    </section>

    <el-card shadow="never">
      <el-form inline @submit.prevent>
        <el-form-item label="病案号">
          <el-input v-model="filters.bah" clearable placeholder="输入病案号" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="病人序号">
          <el-input v-model="filters.brxh" clearable placeholder="输入病人序号" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="扫描人员">
          <el-input v-model="filters.openerNo" clearable placeholder="输入工号" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filters.btype" clearable placeholder="全部类型">
            <el-option v-for="item in typeOptions" :key="item.label" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            查询
          </el-button>
          <el-button @click="resetFilters">
            重置
          </el-button>
        </el-form-item>
      </el-form>

      <el-table
        v-loading="loading"
        :data="tableData"
        stripe
        style="margin-top: 12px"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="bah" label="病案号" min-width="140" />
        <el-table-column prop="brxh" label="病人序号" min-width="120" />
        <el-table-column prop="filename" label="文件名" min-width="220" show-overflow-tooltip />
        <el-table-column prop="btype" label="类型" width="140">
          <template #default="{ row }">
            {{ typeLabel(row.btype) }}
          </template>
        </el-table-column>
        <el-table-column prop="pages" label="页数" width="80" />
        <el-table-column prop="openerNo" label="扫描人员" min-width="110" />
        <el-table-column prop="folder" label="目录" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openDetail(row)">
              详情
            </el-button>
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

    <el-dialog v-model="detailVisible" title="记录详情" width="720px">
      <el-descriptions v-if="currentRecord" :column="2" border>
        <el-descriptions-item label="病案号">
          {{ currentRecord.bah || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="病人序号">
          {{ currentRecord.brxh || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="文件名" :span="2">
          {{ currentRecord.filename || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="类型">
          {{ typeLabel(currentRecord.btype) }}
        </el-descriptions-item>
        <el-descriptions-item label="页数">
          {{ currentRecord.pages || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="扫描人员">
          {{ currentRecord.openerNo || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="上传标记">
          {{ currentRecord.uploadFlag || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="目录" :span="2">
          {{ currentRecord.folder || '-' }}
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
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #64748b;
  font-weight: 700;
}

h2 {
  margin: 0;
  font-size: 28px;
}

.subtitle {
  margin: 8px 0 0;
  color: #64748b;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.summary-label {
  font-size: 12px;
  color: #64748b;
}

.summary-value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 800;
  color: #0f172a;
}

.summary-note {
  margin-top: 8px;
  color: #64748b;
  font-size: 12px;
}

.pager {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}
</style>

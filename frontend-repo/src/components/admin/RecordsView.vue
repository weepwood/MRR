<template>
  <div class="crud-view pmr-page">
    <el-card class="panel-card pmr-panel">
      <template #header>
        <div class="card-header pmr-panel-header">
          <div>
            <div class="title">通用 CRUD</div>
            <div class="subtitle">当前模块基于 `mr_scan` 表，支持增删改查与条件分页</div>
          </div>
          <div class="header-actions pmr-toolbar-actions">
            <el-button type="primary" @click="handleAdd">
              <el-icon><Plus /></el-icon>
              新增
            </el-button>
            <el-button @click="refreshData" :loading="loading">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <el-form :model="filters" inline class="filter-form" @submit.prevent>
        <el-form-item label="病案号">
          <el-input v-model="filters.bah" clearable placeholder="BAH" @keyup.enter="handleSearch" style="width: 160px;" />
        </el-form-item>
        <el-form-item label="病人序号">
          <el-input v-model="filters.brxh" clearable placeholder="BRXH" @keyup.enter="handleSearch" style="width: 160px;" />
        </el-form-item>
        <el-form-item label="文件名">
          <el-input v-model="filters.filename" clearable placeholder="filename" @keyup.enter="handleSearch" style="width: 180px;" />
        </el-form-item>
        <el-form-item label="文件夹">
          <el-input v-model="filters.folder" clearable placeholder="folder" @keyup.enter="handleSearch" style="width: 160px;" />
        </el-form-item>
        <el-form-item label="扫描员">
          <el-input v-model="filters.openerNo" clearable placeholder="openerno" @keyup.enter="handleSearch" style="width: 160px;" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filters.btype" clearable placeholder="全部" style="width: 150px;">
            <el-option v-for="item in btypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.uploadFlag" clearable placeholder="全部" style="width: 120px;">
            <el-option label="正常" :value="1" />
            <el-option label="删除" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch" :loading="loading">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" stripe border class="crud-table">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="bah" label="病案号" width="120" />
        <el-table-column prop="brxh" label="病人序号" width="120" />
        <el-table-column prop="filename" label="文件名" min-width="220" show-overflow-tooltip />
        <el-table-column prop="btype" label="类型" width="160">
          <template #default="{ row }">
            <el-tag :type="getBtypeType(row.btype)">{{ getBtypeText(row.btype) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="pages" label="页数" width="90" />
        <el-table-column prop="openerNo" label="扫描员" width="120" />
        <el-table-column prop="folder" label="文件夹" min-width="140" show-overflow-tooltip />
        <el-table-column prop="uploadDate" label="扫描时间" min-width="170">
          <template #default="{ row }">
            {{ formatDate(row.uploadDate) }}
          </template>
        </el-table-column>
        <el-table-column prop="uploadFlag" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.uploadFlag === 1 ? 'success' : 'danger'">
              {{ row.uploadFlag === 1 ? '正常' : '删除' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" @click="openDetail(row)">
              <el-icon><View /></el-icon>
              详情
            </el-button>
            <el-button type="warning" @click="handleEdit(row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button type="danger" @click="handleDelete(row)">
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="760px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="crud-form">
        <el-row :gutter="16">
          <el-col :xs="24" :md="12">
            <el-form-item label="病案号" prop="bah">
              <el-input v-model="form.bah" placeholder="请输入病案号" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="病人序号" prop="brxh">
              <el-input v-model="form.brxh" placeholder="请输入病人序号" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="文件名" prop="filename">
              <el-input v-model="form.filename" placeholder="请输入文件名" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="类型" prop="btype">
              <el-select v-model="form.btype" placeholder="请选择类型" style="width: 100%;">
                <el-option v-for="item in btypeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="页数" prop="pages">
              <el-input-number v-model="form.pages" :min="1" :max="9999" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="扫描员" prop="openerNo">
              <el-input v-model="form.openerNo" placeholder="请输入扫描员" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="文件夹" prop="folder">
              <el-input v-model="form.folder" placeholder="请输入文件夹" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="状态" prop="uploadFlag">
              <el-select v-model="form.uploadFlag" placeholder="请选择状态" style="width: 100%;">
                <el-option label="正常" :value="1" />
                <el-option label="删除" :value="0" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="记录详情" width="720px">
      <el-descriptions v-if="currentRecord" :column="2" border>
        <el-descriptions-item label="ID">{{ currentRecord.id || '-' }}</el-descriptions-item>
        <el-descriptions-item label="病案号">{{ currentRecord.bah || '-' }}</el-descriptions-item>
        <el-descriptions-item label="病人序号">{{ currentRecord.brxh || '-' }}</el-descriptions-item>
        <el-descriptions-item label="文件名">{{ currentRecord.filename || '-' }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ getBtypeText(currentRecord.btype) }}</el-descriptions-item>
        <el-descriptions-item label="页数">{{ currentRecord.pages || '-' }}</el-descriptions-item>
        <el-descriptions-item label="扫描员">{{ currentRecord.openerNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="文件夹">{{ currentRecord.folder || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ currentRecord.uploadFlag === 1 ? '正常' : '删除' }}</el-descriptions-item>
        <el-descriptions-item label="扫描时间">{{ formatDate(currentRecord.uploadDate) }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus, Refresh, Search, View } from '@element-plus/icons-vue'
import { createScan, deleteScan, getScanByCondition, updateScan } from '@/utils/api'

const loading = ref(false)
const saving = ref(false)
const tableData = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const dialogVisible = ref(false)
const detailVisible = ref(false)
const currentRecord = ref(null)
const formRef = ref(null)

const filters = reactive({
  bah: '',
  brxh: '',
  filename: '',
  folder: '',
  openerNo: '',
  btype: '',
  uploadFlag: ''
})

const form = reactive({
  id: null,
  brxh: '',
  bah: '',
  filename: '',
  btype: 1,
  pages: 1,
  openerNo: '',
  uploadFlag: 1,
  folder: ''
})

const btypeOptions = [
  { label: '01-病案首页', value: 1 },
  { label: '02-病程记录', value: 2 },
  { label: '03-手术记录', value: 3 },
  { label: '04-术后病程', value: 4 },
  { label: '05-护理记录', value: 5 },
  { label: '06-会诊单', value: 6 },
  { label: '07-特殊检查', value: 7 },
  { label: '08-检验单', value: 8 },
  { label: '09-化验单', value: 9 },
  { label: '10-体温单', value: 10 },
  { label: '12-出院记录', value: 12 },
  { label: '13-大病历', value: 13 },
  { label: '14-其他', value: 14 }
]

const rules = {
  bah: [{ required: true, message: '请输入病案号', trigger: 'blur' }],
  brxh: [{ required: true, message: '请输入病人序号', trigger: 'blur' }],
  filename: [{ required: true, message: '请输入文件名', trigger: 'blur' }],
  btype: [{ required: true, message: '请选择类型', trigger: 'change' }],
  pages: [{ required: true, message: '请输入页数', trigger: 'change' }],
  openerNo: [{ required: true, message: '请输入扫描员', trigger: 'blur' }],
  folder: [{ required: true, message: '请输入文件夹', trigger: 'blur' }],
  uploadFlag: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const dialogTitle = computed(() => (form.id ? '编辑记录' : '新增记录'))

const cloneForm = () => ({
  id: null,
  brxh: '',
  bah: '',
  filename: '',
  btype: 1,
  pages: 1,
  openerNo: '',
  uploadFlag: 1,
  folder: ''
})

const normalize = (value) => (typeof value === 'string' ? value.trim() : value)

const buildParams = () => {
  const params = {}
  const values = {
    bah: normalize(filters.bah),
    brxh: normalize(filters.brxh),
    filename: normalize(filters.filename),
    folder: normalize(filters.folder),
    openerNo: normalize(filters.openerNo)
  }

  Object.entries(values).forEach(([key, value]) => {
    if (value) params[key] = value
  })

  if (filters.btype !== '' && filters.btype !== null && filters.btype !== undefined) {
    params.btype = filters.btype
  }
  if (filters.uploadFlag !== '' && filters.uploadFlag !== null && filters.uploadFlag !== undefined) {
    params.uploadFlag = filters.uploadFlag
  }

  return params
}

const loadData = async () => {
  loading.value = true
  try {
    const response = await getScanByCondition(buildParams(), page.value, size.value)
    const result = response?.data
    if (!result || result.code !== 200) {
      throw new Error(result?.message || '查询失败')
    }

    const payload = result.data || {}
    tableData.value = Array.isArray(payload.list) ? payload.list : []
    total.value = Number(payload.total || 0)
  } catch (error) {
    tableData.value = []
    total.value = 0
    ElMessage.error(error?.message || '查询失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  loadData()
}

const handlePageChange = () => {
  loadData()
}

const handleSizeChange = () => {
  page.value = 1
  loadData()
}

const refreshData = async () => {
  await loadData()
  ElMessage.success('数据已刷新')
}

const resetFilters = () => {
  filters.bah = ''
  filters.brxh = ''
  filters.filename = ''
  filters.folder = ''
  filters.openerNo = ''
  filters.btype = ''
  filters.uploadFlag = ''
  page.value = 1
  loadData()
}

const resetForm = () => {
  Object.assign(form, cloneForm())
  formRef.value?.clearValidate?.()
}

const handleAdd = () => {
  resetForm()
  dialogVisible.value = true
}

const handleEdit = (row) => {
  Object.assign(form, cloneForm(), row)
  dialogVisible.value = true
}

const openDetail = (row) => {
  currentRecord.value = { ...row }
  detailVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const payload = {
      brxh: form.brxh,
      bah: form.bah,
      filename: form.filename,
      btype: form.btype,
      pages: form.pages,
      openerNo: form.openerNo,
      uploadFlag: form.uploadFlag,
      folder: form.folder
    }

    const response = form.id ? await updateScan(form.id, payload) : await createScan(payload)
    if (response?.data?.code !== 200) {
      throw new Error(response?.data?.message || '保存失败')
    }

    ElMessage.success(form.id ? '更新成功' : '新增成功')
    dialogVisible.value = false
    loadData()
  } catch (error) {
    ElMessage.error(error?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除记录 ID ${row.id} 吗？`, '提示', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  try {
    const response = await deleteScan(row.id)
    if (response?.data?.code !== 200) {
      throw new Error(response?.data?.message || '删除失败')
    }
    ElMessage.success('删除成功')
    loadData()
  } catch (error) {
    ElMessage.error(error?.message || '删除失败')
  }
}

const formatDate = (value) => {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN', { hour12: false })
}

const getBtypeType = (btype) => {
  if ([1, 2, 8, 12].includes(Number(btype))) return 'primary'
  if ([3, 7, 9, 14].includes(Number(btype))) return 'success'
  if ([4, 10, 13].includes(Number(btype))) return 'warning'
  return 'info'
}

const getBtypeText = (btype) => {
  const matched = btypeOptions.find((item) => Number(item.value) === Number(btype))
  return matched ? matched.label : '未知'
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.crud-view {
  height: 100%;
}

.panel-card {
  min-height: 560px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.title {
  font-size: 16px;
  font-weight: 700;
  color: #1d2b42;
}

.subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: #6a7d99;
}

.header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.filter-form {
  margin-bottom: 12px;
}

.crud-table {
  width: 100%;
}

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}

.crud-form {
  padding-top: 4px;
}

@media (max-width: 768px) {
  .card-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .header-actions {
    width: 100%;
  }

  .header-actions .el-button {
    flex: 1;
  }
}
</style>

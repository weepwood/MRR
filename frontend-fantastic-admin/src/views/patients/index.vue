<script setup lang="ts">
import type { PatientRecord } from '@/api/modules/patients'
import { Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { exportPatientsExcel, getPatients } from '@/api/modules/patients'

defineOptions({ name: 'PatientsPage' })

const loading = ref(false)
const tableData = ref<PatientRecord[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)

const filters = reactive({ keyword: '' })

async function loadData() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value, ...(filters.keyword.trim() && { keyword: filters.keyword.trim() }) }
    const res = await getPatients(params)
    const payload = res.data || {} as any
    tableData.value = Array.isArray(payload.list) ? payload.list : []
    total.value = Number(payload.total || 0)
  }
  catch (error: any) {
    tableData.value = []
    total.value = 0
    ElMessage.error(error?.message || '患者列表加载失败')
  }
  finally { loading.value = false }
}

function handleSearch() { page.value = 1; loadData() }
function resetFilters() { filters.keyword = ''; handleSearch() }
function handlePageChange(p: number) { page.value = p; loadData() }
function handleSizeChange(s: number) { size.value = s; page.value = 1; loadData() }

async function exportExcel() {
  try {
    const params = filters.keyword.trim() ? { keyword: filters.keyword.trim() } : undefined
    const response = await exportPatientsExcel(params)
    const blob = new Blob([response.data as BlobPart], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = `patients-${new Date().toISOString().slice(0, 10)}.xlsx`
    link.click()
    URL.revokeObjectURL(link.href)
    ElMessage.success('导出成功')
  }
  catch (error: any) {
    ElMessage.error(error?.message || '导出失败')
  }
}

onMounted(loadData)
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
          查询和管理患者基本信息，支持按病案号、姓名、身份证号、科室搜索。
        </p>
      </div>
      <el-button :loading="loading" :icon="Refresh" @click="loadData">
        刷新
      </el-button>
      <el-button type="success" @click="exportExcel">
        导出 Excel
      </el-button>
    </div>

    <el-card shadow="never">
      <div class="filter-bar">
        <el-input
          v-model="filters.keyword"
          placeholder="搜索病案号 / 姓名 / 身份证号 / 科室"
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
      <el-table v-loading="loading" :data="tableData" stripe style="width: 100%;">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="bah" label="病案号" width="140" />
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="idCard" label="身份证号" min-width="180" show-overflow-tooltip />
        <el-table-column prop="department" label="科室" width="120" />
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
  </div>
</template>

<style scoped>
.page-shell { display: grid; gap: 16px; }
.page-header { display: flex; gap: 16px; align-items: flex-start; justify-content: space-between; }
.eyebrow { margin: 0 0 6px; font-size: 12px; font-weight: 700; color: #64748b; text-transform: uppercase; letter-spacing: 0.12em; }
h2 { margin: 0; font-size: 28px; }
.subtitle { margin: 8px 0 0; color: #64748b; }
.filter-bar { display: flex; gap: 12px; align-items: center; }
.filter-input { width: 360px; }
.pagination-bar { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>

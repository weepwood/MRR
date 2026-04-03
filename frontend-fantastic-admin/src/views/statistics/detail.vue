<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getStatisticsList } from '@/api/modules/statistics'

defineOptions({ name: 'StatisticsDetailPage' })

const router = useRouter()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)

const filters = reactive({
  keyword: '',
  type: '',
  dateRange: [] as string[],
})

async function loadData() {
  loading.value = true
  try {
    const response = await getStatisticsList({
      page: page.value,
      size: size.value,
      keyword: filters.keyword.trim() || undefined,
      type: filters.type || undefined,
      startDate: filters.dateRange[0] || undefined,
      endDate: filters.dateRange[1] || undefined,
      sortBy: 'date',
      sortOrder: 'desc',
    })
    const payload = response.data || {}
    tableData.value = Array.isArray(payload.list) ? payload.list : []
    total.value = Number(payload.total || 0)
  } catch (error: any) {
    tableData.value = []
    total.value = 0
    ElMessage.error(error?.message || '统计明细加载失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  loadData()
}

function openArchive(row: any) {
  router.push({
    path: `/statistics/archive/${row.bah}`,
    query: {
      date: row.date || '',
      type: row.type || '',
      cid: row.cid || '',
      openerNo: row.openerNo || '',
      pages: row.pages || '',
    },
  })
}

onMounted(loadData)
</script>

<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <p class="eyebrow">Statistics Detail</p>
        <h2>统计明细</h2>
        <p class="subtitle">对统计明细按病案号、日期范围和类型进行二次筛选，并可继续跳转归档图像页。</p>
      </div>
    </div>

    <el-card shadow="never">
      <el-form inline @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="filters.keyword" clearable placeholder="bah / cid / openerNo" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="类型">
          <el-input v-model="filters.type" clearable placeholder="输入类型名称" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker
            v-model="filters.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="tableData" stripe style="margin-top: 12px">
        <el-table-column prop="bah" label="病案号" min-width="140" />
        <el-table-column prop="cid" label="设备 ID" min-width="120" />
        <el-table-column prop="openerNo" label="扫描人员" min-width="120" />
        <el-table-column prop="date" label="日期" width="120" />
        <el-table-column prop="type" label="类型" min-width="120" />
        <el-table-column prop="pages" label="页数" width="100" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openArchive(row)">归档图像</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSearch"
          @current-change="loadData"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page-shell {
  display: grid;
  gap: 20px;
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

.pager {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}
</style>

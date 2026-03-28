<script setup>
import { useRouter } from 'vue-router'
import RecordsStatisticsView from '@/components/admin/RecordsStatisticsView.vue'

const router = useRouter()

const openDetailPage = () => {
  router.push('/admin/statistics/detail')
}
</script>

<template>
  <div class="statistics-summary-page">
    <div class="summary-actions">
      <el-button type="primary" plain @click="openDetailPage">
        病案明细
      </el-button>
    </div>
    <RecordsStatisticsView />
  </div>
</template>

<style scoped>
.statistics-summary-page {
  display: grid;
  gap: 16px;
}

.summary-actions {
  display: flex;
  justify-content: flex-end;
}

:deep(.list-card) {
  display: none;
}
</style>

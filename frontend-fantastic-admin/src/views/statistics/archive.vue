<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getImgApiByBah } from '@/api/modules/image'

defineOptions({ name: 'StatisticsArchivePage' })

const route = useRoute()
const loading = ref(false)
const images = ref<any[]>([])

const bah = computed(() => String(route.params.bah || ''))
const title = computed(() => `归档图像 - ${bah.value || '-'}`)

async function loadImages() {
  if (!bah.value) return
  loading.value = true
  try {
    const response = await getImgApiByBah(bah.value)
    const payload = response.data || []
    images.value = Array.isArray(payload) ? payload : []
  } catch (error: any) {
    images.value = []
    ElMessage.error(error?.message || '归档图像加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadImages)
</script>

<template>
  <div class="page-shell">
    <div>
      <p class="eyebrow">Archive Gallery</p>
      <h2>{{ title }}</h2>
      <p class="subtitle">当前页面用于校验病案号对应的归档图像是否成功接入新后台。</p>
    </div>

    <el-card shadow="never" :loading="loading">
      <template #header>归档元信息</template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="病案号">{{ bah || '-' }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ route.query.date || '-' }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ route.query.type || '-' }}</el-descriptions-item>
        <el-descriptions-item label="设备 ID">{{ route.query.cid || '-' }}</el-descriptions-item>
        <el-descriptions-item label="扫描人员">{{ route.query.openerNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="页数">{{ route.query.pages || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" :loading="loading">
      <template #header>接口返回预览</template>
      <el-empty v-if="!images.length" description="当前未返回可展示的图像数据" />
      <el-table v-else :data="images" stripe>
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="btype" label="类型" width="90" />
        <el-table-column prop="pages" label="页数" width="90" />
        <el-table-column prop="img_url" label="图像地址" min-width="320" show-overflow-tooltip />
      </el-table>
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
</style>

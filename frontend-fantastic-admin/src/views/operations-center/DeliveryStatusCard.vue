<script setup lang="ts">
import type { AccessLogPartitionStatus } from '@/api/modules/operations'
import { onMounted, ref } from 'vue'
import { getAccessLogPartitionStatus } from '@/api/modules/operations'
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'
import { formatDateTime } from '@/utils/display-format'

defineProps<{ mode?: 'direct' | 'gateway', modeStale: boolean }>()

const partition = ref<AccessLogPartitionStatus>()
const loading = ref(false)
const failed = ref(false)
const fetchedAt = ref('')
const phaseLabels = { COPYING: '复制中', READY: '等待切换', CUTOVER: '已切换', ABORTED: '已放弃' }

function count(value?: number | null) {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0
    ? new Intl.NumberFormat('zh-CN').format(value)
    : '未知'
}

function state(value: boolean | undefined, yes: string, no: string) {
  return value === true ? yes : value === false ? no : '未知'
}

async function refresh() {
  if (loading.value) { return }
  loading.value = true
  try {
    partition.value = await getAccessLogPartitionStatus()
    fetchedAt.value = new Date().toISOString()
    failed.value = false
  }
  catch {
    failed.value = true
  }
  finally {
    loading.value = false
  }
}

onMounted(refresh)
</script>

<template>
  <MrrSectionCard
    class="delivery-status-card"
    title="影像下发与访问日志分区"
    description="只读配置和迁移状态，不执行切换；现场可用性及源站隔离需单独验收。"
  >
    <template #actions>
      <el-button text type="primary" :loading="loading" @click="refresh">
        刷新分区状态
      </el-button>
    </template>
    <el-descriptions :column="1" size="small">
      <el-descriptions-item :label="modeStale ? '上次下发模式' : '配置下发模式'">
        <el-tag :type="mode === 'gateway' ? 'success' : 'info'">
          {{ mode === 'gateway' ? '受控网关' : mode === 'direct' ? '直连' : '未知' }}
        </el-tag>
      </el-descriptions-item>
    </el-descriptions>
    <el-alert v-if="failed" :title="partition ? '刷新失败，以下为上次分区状态' : '未取得分区状态'" type="warning" :closable="false" show-icon />
    <el-skeleton v-if="loading && !partition" :rows="3" animated />
    <el-descriptions v-if="partition" :column="1" size="small">
      <el-descriptions-item label="迁移阶段">
        {{ partition.phase ? phaseLabels[partition.phase] || '未知' : '未知' }}
      </el-descriptions-item>
      <el-descriptions-item label="自动复制">
        {{ state(partition.copyEnabled, '已开启', '已关闭') }}
      </el-descriptions-item>
      <el-descriptions-item label="分区数量">
        {{ count(partition.partitionCount) }}
      </el-descriptions-item>
      <el-descriptions-item label="已复制行数">
        {{ count(partition.rowsCopied) }}
      </el-descriptions-item>
      <el-descriptions-item label="剩余 ID 跨度（非行数）">
        {{ count(partition.copyLag) }}
      </el-descriptions-item>
      <el-descriptions-item label="默认分区">
        <el-tag :type="partition.defaultPartitionHasRows === true ? 'warning' : 'info'">
          {{ state(partition.defaultPartitionHasRows, '有数据', '无数据') }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="切换后旧表">
        {{ state(partition.legacyTablePresent, '仍保留', '不存在') }}
      </el-descriptions-item>
      <el-descriptions-item label="状态获取时间">
        {{ fetchedAt ? formatDateTime(fetchedAt) : '未知' }}
      </el-descriptions-item>
      <el-descriptions-item label="迁移状态更新时间">
        {{ partition.updatedAt ? formatDateTime(partition.updatedAt) : '未知' }}
      </el-descriptions-item>
    </el-descriptions>
  </MrrSectionCard>
</template>

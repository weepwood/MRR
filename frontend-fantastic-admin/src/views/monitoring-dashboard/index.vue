<script setup lang="ts">
import { ref } from 'vue'
import MrrPageShell from '@/components/MrrPageShell/index.vue'
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'
import MonitoringPage from '@/views/monitoring/index.vue'

defineOptions({ name: 'MonitoringDashboardPage' })

const statusFrameKey = ref(0)

function reloadStatusFrame() {
  statusFrameKey.value += 1
}

function openStatusPage() {
  window.open('/status', '_blank', 'noopener,noreferrer')
}
</script>

<template>
  <MrrPageShell class="monitoring-dashboard">
    <MonitoringPage />

    <section class="embedded-status-section">
      <!-- 自写卡头 → MrrSectionCard：标题 14px / 650、操作在右，与上方监控页各分区卡同一外观。 -->
      <MrrSectionCard
        class="embedded-status-card"
        title="服务可用性状态"
        description="嵌入公开状态页，查看最近 90 天可用率、每日状态和异常区间"
        icon="i-lucide:activity"
        body-padding="none"
      >
        <template #actions>
          <el-button @click="reloadStatusFrame">
            重新加载
          </el-button>
          <el-button type="primary" plain @click="openStatusPage">
            独立打开
          </el-button>
        </template>

        <iframe
          :key="statusFrameKey"
          class="embedded-status-frame"
          src="/status"
          title="MRR 服务可用性状态"
          loading="lazy"
        />
      </MrrSectionCard>
    </section>
  </MrrPageShell>
</template>

<style scoped>
/* 外层页面壳层统一提供块间距（24px）与宽度上限，内嵌的监控页自身也是壳层，宽度一致。 */
.embedded-status-card {
  overflow: hidden;
  border-radius: var(--mrr-radius-card);
}

.embedded-status-frame {
  display: block;
  width: 100%;
  height: min(1080px, calc(100vh - 150px));
  min-height: 720px;
  background: var(--el-bg-color-page);
  border: 0;
}

@media (max-width: 760px) {
  .embedded-status-frame {
    height: 820px;
    min-height: 640px;
  }
}
</style>

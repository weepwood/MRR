<script setup lang="ts">
import { ref } from 'vue'
import MrrPageShell from '@/components/MrrPageShell/index.vue'
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
      <el-card shadow="never" class="embedded-status-card">
        <template #header>
          <div class="embedded-status-header">
            <div>
              <strong>服务可用性状态</strong>
              <p>嵌入公开状态页，查看最近 90 天可用率、每日状态和异常区间</p>
            </div>
            <div class="embedded-status-actions">
              <el-button @click="reloadStatusFrame">
                重新加载
              </el-button>
              <el-button type="primary" plain @click="openStatusPage">
                独立打开
              </el-button>
            </div>
          </div>
        </template>

        <iframe
          :key="statusFrameKey"
          class="embedded-status-frame"
          src="/status"
          title="MRR 服务可用性状态"
          loading="lazy"
        />
      </el-card>
    </section>
  </MrrPageShell>
</template>

<style scoped>
/* 外层页面壳层统一提供块间距（24px）与宽度上限，内嵌的监控页自身也是壳层，宽度一致。 */
.embedded-status-card {
  overflow: hidden;
  border-radius: var(--mrr-radius-card);
}

.embedded-status-card :deep(.el-card__body) {
  padding: 0;
}

.embedded-status-header,
.embedded-status-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.embedded-status-header {
  justify-content: space-between;
}

.embedded-status-header > div:first-child {
  display: grid;
  gap: 4px;
}

.embedded-status-header strong {
  color: var(--el-text-color-primary);
}

.embedded-status-header p {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
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
  .embedded-status-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .embedded-status-actions {
    flex-wrap: wrap;
  }

  .embedded-status-frame {
    height: 820px;
    min-height: 640px;
  }
}
</style>

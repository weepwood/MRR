<script setup lang="ts">
import type { ExampleId } from '../catalog'
import { computed, onBeforeUnmount, onMounted, ref, useId } from 'vue'

defineProps<{ example: ExampleId }>()

const id = useId()
const settingsStore = useSettingsStore()

/* ---------- 页头尺寸 ---------- */
const longDescription = ref(false)
const shortText = '按完整编号检索扫描记录，采用游标读取。'
const longText = '按完整编号检索扫描记录，采用游标读取；病案号、上架号兼容原始前导零。结果按批次返回，本批数量不代表全库总数。'
const defaultHeader = ref<HTMLElement>()
const compactHeader = ref<HTMLElement>()
const headerHeights = ref({ default: 0, compact: 0 })
const titleStyle = computed(() => settingsStore.settings.app.pageTitleStyle)
let headerObserver: ResizeObserver | undefined

function measureHeaders() {
  const read = (host?: HTMLElement) => Math.round(host?.querySelector('.mrr-page-header')?.getBoundingClientRect().height ?? 0)
  headerHeights.value = { default: read(defaultHeader.value), compact: read(compactHeader.value) }
}

/* ---------- 动效与刷新反馈 ---------- */
const liveOverride = ref(true)
const pendingPages = ref(128)
const metricMessage = ref('点击「模拟数据更新」，数值背后会淡出一次主色浅底。')

const batches = [
  [
    { id: 'BATCH-0101', pages: 1280, status: 'completed', label: '已完成' },
    { id: 'BATCH-0102', pages: 964, status: 'running', label: '识别中' },
    { id: 'BATCH-0103', pages: 512, status: 'pending', label: '等待中' },
    { id: 'BATCH-0104', pages: 2048, status: 'completed', label: '已完成' },
  ],
  [
    { id: 'BATCH-0105', pages: 736, status: 'failed', label: '需复核' },
    { id: 'BATCH-0106', pages: 1490, status: 'completed', label: '已完成' },
    { id: 'BATCH-0107', pages: 388, status: 'running', label: '识别中' },
    { id: 'BATCH-0108', pages: 1024, status: 'completed', label: '已完成' },
  ],
]
const batchIndex = ref(0)
const resultVersion = ref(0)
const tableLoading = ref(false)
const tableCleared = ref(false)
const tableMessage = ref('慢查询会在 200ms 后出现进度条；快查询不出现任何遮挡。')
const tableRows = computed(() => tableCleared.value ? [] : batches[batchIndex.value])
let tableTimer: ReturnType<typeof setTimeout> | undefined

function runQuery(latency: number, fromEmpty = false) {
  if (tableLoading.value) {
    return
  }
  if (fromEmpty) {
    tableCleared.value = true
  }
  tableLoading.value = true
  tableTimer = setTimeout(() => {
    tableCleared.value = false
    batchIndex.value = (batchIndex.value + 1) % batches.length
    resultVersion.value += 1
    tableLoading.value = false
    tableMessage.value = fromEmpty
      ? `首次加载：没有可保留的结果，显示骨架屏（${latency}ms）。`
      : latency < 200
        ? `快查询（${latency}ms）：没有遮挡，新结果直接淡入。`
        : `慢查询（${latency}ms）：旧结果保留并变暗，新结果到达后逐行淡入。`
  }, latency)
}

function updateMetric() {
  const delta = 7 + Math.round(Math.random() * 20)
  pendingPages.value += delta
  metricMessage.value = `数值增加 ${delta} 页，提示只播放一次；首屏渲染时不会播放。`
}

const enterRun = ref(0)

onMounted(() => {
  if (typeof ResizeObserver === 'undefined') {
    measureHeaders()
    return
  }
  headerObserver = new ResizeObserver(measureHeaders)
  for (const host of [defaultHeader.value, compactHeader.value]) {
    if (host) {
      headerObserver.observe(host)
    }
  }
})

onBeforeUnmount(() => {
  headerObserver?.disconnect()
  if (tableTimer !== undefined) {
    clearTimeout(tableTimer)
  }
})
</script>

<template>
  <div v-if="example === 'page-header'" class="example-stack">
    <div class="example-row">
      <FaSwitch :id="`${id}-long-description`" v-model="longDescription" />
      <label :for="`${id}-long-description`">使用长说明（检查折行）</label>
    </div>
    <!--
      示例页头只做视觉对照：真实页面每页只有一个 h1，这里的两个样例对读屏隐藏，
      样例内也不放可聚焦的操作，避免 aria-hidden 内出现焦点。
    -->
    <figure class="pattern-figure">
      <figcaption>
        <strong>默认</strong>首页、监控、统计、运维诊断等「页头本身承载概览」的页面
        <span v-if="headerHeights.default" class="pattern-measure">{{ headerHeights.default }}px</span>
      </figcaption>
      <div ref="defaultHeader" aria-hidden="true" class="pattern-sample">
        <MrrPageHeader title="系统与数据库监控" :description="longDescription ? longText : shortText" />
      </div>
    </figure>
    <figure class="pattern-figure">
      <figcaption>
        <strong>紧凑 size="compact"</strong>查询列表、游标大表、存储浏览、审计、导入导出
        <span v-if="headerHeights.compact" class="pattern-measure">{{ headerHeights.compact }}px</span>
      </figcaption>
      <div ref="compactHeader" aria-hidden="true" class="pattern-sample">
        <MrrPageHeader size="compact" title="记录管理" :description="longDescription ? longText : shortText" />
      </div>
    </figure>
    <p class="example-note">
      两种尺寸都按「标题在上、说明在下」显示；紧凑尺寸只改变卡片留白、字号与图标。当前为{{ titleStyle === 'plain' ? '页面同级风格，两种尺寸看起来相同' : '卡片风格' }}，可在「界面外观 → 页面标题风格」切换后对照。
    </p>
  </div>

  <div v-else class="example-stack">
    <section class="pattern-section" :aria-labelledby="`${id}-live`">
      <h3 :id="`${id}-live`">
        进行中状态
      </h3>
      <div class="example-row" aria-label="进行中状态示例">
        <MrrStatusTag status="running" label="迁移中" />
        <MrrStatusTag status="processing" label="识别中" />
        <MrrStatusTag tone="info" label="同步中" :live="liveOverride" />
        <MrrStatusTag status="completed" label="已完成" />
        <MrrStatusTag status="failed" label="失败" />
      </div>
      <div class="example-row">
        <FaSwitch :id="`${id}-live-switch`" v-model="liveOverride" />
        <label :for="`${id}-live-switch`">「同步中」显式开启光圈（传 tone 时默认关闭）</label>
      </div>
      <p class="example-note">
        运行中 / 处理中自动显示呼吸光圈；终态与待处理不显示。状态仍由文字与颜色表达。
      </p>
    </section>

    <section class="pattern-section" :aria-labelledby="`${id}-metric`">
      <h3 :id="`${id}-metric`">
        数值更新提示
      </h3>
      <div class="pattern-metric">
        <MrrMetricCard label="待复核影像" :value="pendingPages.toLocaleString('zh-CN')" suffix="页" note="合成数据，每次更新随机增加" icon="i-lucide:scan-eye" tone="amber" />
        <FaButton variant="outline" @click="updateMetric">
          <FaIcon name="i-lucide:refresh-cw" aria-hidden="true" />
          模拟数据更新
        </FaButton>
      </div>
      <p class="example-note" role="status">
        {{ metricMessage }}
      </p>
    </section>

    <section class="pattern-section" :aria-labelledby="`${id}-table`">
      <h3 :id="`${id}-table`">
        表格刷新
      </h3>
      <MrrDataTablePanel title="刷新反馈示例" :count="tableRows.length || undefined" :loading="tableLoading" :result-key="resultVersion">
        <template #actions>
          <FaButton variant="outline" size="sm" :disabled="tableLoading" @click="runQuery(600)">
            模拟慢查询
          </FaButton>
          <FaButton variant="outline" size="sm" :disabled="tableLoading" @click="runQuery(120)">
            模拟快查询
          </FaButton>
          <FaButton variant="ghost" size="sm" :disabled="tableLoading" @click="runQuery(600, true)">
            清空后首次加载
          </FaButton>
        </template>
        <AppLoading v-if="tableLoading && !tableRows.length" type="table" :rows="4" :cols="3" />
        <el-table v-else :data="tableRows" row-key="id">
          <el-table-column label="批次编号" min-width="140">
            <template #default="{ row }">
              <span class="pattern-mono">{{ row.id }}</span>
            </template>
          </el-table-column>
          <el-table-column label="影像页数" min-width="110" align="right">
            <template #default="{ row }">
              {{ row.pages.toLocaleString('zh-CN') }}
            </template>
          </el-table-column>
          <el-table-column label="批次状态" min-width="120">
            <template #default="{ row }">
              <MrrStatusTag :status="row.status" :label="row.label" />
            </template>
          </el-table-column>
        </el-table>
      </MrrDataTablePanel>
      <p class="example-note" role="status">
        {{ tableMessage }}
      </p>
    </section>

    <section class="pattern-section" :aria-labelledby="`${id}-enter`">
      <h3 :id="`${id}-enter`">
        区块入场
      </h3>
      <div :key="enterRun" class="pattern-enter" aria-hidden="true">
        <span class="pattern-enter__block pattern-enter__block--header">页头</span>
        <span class="pattern-enter__block pattern-enter__block--summary">摘要</span>
        <span class="pattern-enter__block pattern-enter__block--body">主体</span>
      </div>
      <div class="example-row">
        <FaButton variant="outline" @click="enterRun += 1">
          <FaIcon name="i-lucide:play" aria-hidden="true" />
          重播入场
        </FaButton>
      </div>
      <p class="example-note">
        与 MrrPageShell 相同的关键帧和间隔：每块淡入并上移 4px，间隔 30ms，总长 240ms。切换页面时自动播放；本页本身在壳层内，嵌套壳层不重复播放，所以这里单独演示。
      </p>
    </section>
  </div>
</template>

<style scoped>
.pattern-figure {
  display: grid;
  gap: var(--mrr-space-2);
  min-width: 0;
  margin: 0;
}

.pattern-figure figcaption {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: baseline;
  font-size: var(--mrr-font-size-xs);
  color: var(--mrr-muted-foreground);
}

.pattern-figure figcaption strong {
  font-size: var(--mrr-font-size-sm);
  font-weight: 600;
  color: var(--mrr-foreground);
}

.pattern-measure {
  margin-left: auto;
  font-family: var(--mrr-font-mono);
  font-variant-numeric: tabular-nums;
}

/* 样例放在工作区底色上，和真实页面里页头卡与背景的关系一致。 */
.pattern-sample {
  min-width: 0;
  padding: var(--mrr-space-3);
  background: var(--mrr-workspace-bg);
  border-radius: var(--mrr-radius-md);
}

.pattern-section {
  display: grid;
  gap: var(--mrr-space-3);
  min-width: 0;
}

.pattern-section + .pattern-section {
  padding-top: var(--mrr-space-5);
  border-top: 1px solid var(--mrr-shell-divider);
}

.pattern-section h3 {
  margin: 0;
  font-size: var(--mrr-font-size-md);
  font-weight: 650;
}

.pattern-metric {
  display: grid;
  grid-template-columns: minmax(0, 320px) auto;
  gap: var(--mrr-space-4);
  align-items: center;
  justify-content: start;
}

.pattern-mono {
  font-family: var(--mrr-font-mono);
}

.pattern-enter {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--mrr-space-2);
  max-width: 420px;
  padding: var(--mrr-space-3);
  background: var(--mrr-workspace-bg);
  border-radius: var(--mrr-radius-md);
}

.pattern-enter__block {
  display: flex;
  align-items: center;
  padding-inline: var(--mrr-space-3);
  font-size: var(--mrr-font-size-xs);
  color: var(--mrr-muted-foreground);
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-sm);
  animation: mrr-page-enter var(--mrr-motion-move) var(--mrr-ease-out) backwards;
}

.pattern-enter__block--header {
  height: 32px;
}

.pattern-enter__block--summary {
  height: 28px;
  animation-delay: var(--mrr-motion-stagger);
}

.pattern-enter__block--body {
  height: 64px;
  animation-delay: calc(var(--mrr-motion-stagger) * 2);
}

@media (max-width: 600px) {
  .pattern-metric {
    grid-template-columns: minmax(0, 1fr);
    justify-items: start;
  }

  .pattern-measure {
    margin-left: 0;
  }
}
</style>

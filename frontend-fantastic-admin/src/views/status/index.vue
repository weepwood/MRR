<script setup lang="ts">
import type {
  DailySystemAvailability,
  MinuteSystemAvailability,
  SystemStatusIncident,
  SystemStatusSummary,
} from '@/api/modules/system-status'
import type { ECOption } from '@/plugins/echarts'
import dayjs from 'dayjs'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  getDailySystemAvailability,
  getMinuteSystemAvailability,
  getSystemStatusIncidents,
  getSystemStatusSummary,
} from '@/api/modules/system-status'
import serverLogo from '@/assets/images/server.svg'
import MrrChart from '@/components/charts/MrrChart.vue'

defineOptions({ name: 'PublicSystemStatusPage' })

const HISTORY_DAYS = 90
const summary = ref<SystemStatusSummary | null>(null)
const daily = ref<DailySystemAvailability[]>([])
const minutes = ref<MinuteSystemAvailability[]>([])
const incidents = ref<SystemStatusIncident[]>([])
const selectedDate = ref<string | null>(null)
const loading = ref(true)
const refreshing = ref(false)
const loadError = ref(false)
let refreshTimer: number | undefined
let minuteRequestVersion = 0

function currentDate() {
  return dayjs().format('YYYY-MM-DD')
}

const currentState = computed(() => summary.value?.currentStatus ?? 'NO_DATA')
const filteredIncidents = computed(() => {
  if (!selectedDate.value) {
    return incidents.value
  }

  const dayStart = dayjs(selectedDate.value).startOf('day')
  const dayEnd = dayjs(selectedDate.value).endOf('day')
  return incidents.value.filter((incident) => {
    const incidentStart = dayjs(incident.startedAt)
    const incidentEnd = incident.endedAt ? dayjs(incident.endedAt) : dayjs()
    return !incidentEnd.isBefore(dayStart) && !incidentStart.isAfter(dayEnd)
  })
})
const minuteHourLabels = computed(() => minutes.value.filter((_, index) => index % 60 === 0))
const hourlyAvailability = computed(() => minuteHourLabels.value.map((item, index) => {
  const hourMinutes = minutes.value.slice(index * 60, index * 60 + 60)
  const monitoredMinutes = hourMinutes.filter(minute => minute.status !== 'NO_DATA')
  const uptimeMinutes = monitoredMinutes.filter(minute => minute.status === 'UP').length
  return {
    label: dayjs(item.startedAt).format('HH:mm'),
    uptimePercentage: monitoredMinutes.length
      ? Math.round(uptimeMinutes * 100_000 / monitoredMinutes.length) / 1_000
      : null,
  }
}))
const hasHourlyAvailability = computed(() => hourlyAvailability.value.some(item => item.uptimePercentage !== null))
const hourlyAvailabilityChart = computed<ECOption>(() => ({
  tooltip: {
    trigger: 'axis',
    valueFormatter: value => value === '-' ? '暂无数据' : `${value}%`,
  },
  grid: {
    top: 24,
    right: 20,
    bottom: 28,
    left: 18,
    containLabel: true,
  },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: hourlyAvailability.value.map(item => item.label),
    axisLabel: { hideOverlap: true, margin: 12 },
    splitLine: { show: false },
  },
  yAxis: {
    type: 'value',
    min: 0,
    max: 100,
    axisLabel: { formatter: '{value}%' },
  },
  series: [{
    name: '可用率',
    type: 'line',
    data: hourlyAvailability.value.map(item => item.uptimePercentage ?? '-'),
    smooth: true,
    showSymbol: true,
    symbol: 'circle',
    symbolSize: 6,
    lineStyle: { width: 2.5, color: '#2563eb' },
    itemStyle: { color: '#2563eb' },
    areaStyle: { color: '#2563eb', opacity: 0.12 },
  }],
}))
const statusCopy = computed(() => {
  if (currentState.value === 'UP') {
    return {
      title: '所有服务正常运行',
      description: 'MRR 前后端与数据库健康检查均正常。',
    }
  }
  if (currentState.value === 'DOWN') {
    return {
      title: '服务当前存在异常',
      description: '系统已记录异常区间，恢复后状态会自动更新。',
    }
  }
  return {
    title: '正在收集运行数据',
    description: '尚未形成足够的服务运行历史。',
  }
})

const uptimeText = computed(() => {
  const percentage = summary.value?.uptimePercentage
  return typeof percentage === 'number' ? `${percentage.toFixed(3)}%` : '暂无数据'
})

const currentDurationText = computed(() => {
  const since = summary.value?.currentStatusSince
  if (!since) {
    return '暂无持续时间数据'
  }
  return `当前状态已持续 ${formatDuration(Math.max(0, dayjs().diff(dayjs(since), 'second')))}`
})

async function loadAll() {
  loading.value = true
  loadError.value = false
  try {
    const [summaryResponse, dailyResponse, minuteResponse, incidentResponse] = await Promise.all([
      getSystemStatusSummary(HISTORY_DAYS),
      getDailySystemAvailability(HISTORY_DAYS),
      getMinuteSystemAvailability(currentDate()),
      getSystemStatusIncidents(HISTORY_DAYS),
    ])
    summary.value = summaryResponse.data ?? null
    daily.value = dailyResponse.data ?? []
    minutes.value = minuteResponse.data ?? []
    incidents.value = incidentResponse.data ?? []
  }
  catch {
    loadError.value = true
  }
  finally {
    loading.value = false
  }
}

async function refreshCurrentStatus() {
  if (refreshing.value) {
    return
  }
  refreshing.value = true
  const requestVersion = selectedDate.value ? null : ++minuteRequestVersion
  try {
    const [summaryResponse, minuteResponse] = await Promise.all([
      getSystemStatusSummary(HISTORY_DAYS),
      selectedDate.value ? Promise.resolve(null) : getMinuteSystemAvailability(currentDate()),
    ])
    summary.value = summaryResponse.data ?? summary.value
    if (minuteResponse && requestVersion === minuteRequestVersion) {
      minutes.value = minuteResponse.data ?? minutes.value
    }
    loadError.value = false
  }
  catch {
    loadError.value = true
  }
  finally {
    refreshing.value = false
  }
}

function formatDateTime(value?: string | null) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '仍在持续'
}

function formatDuration(totalSeconds: number) {
  if (!Number.isFinite(totalSeconds) || totalSeconds <= 0) {
    return '不足 1 分钟'
  }
  const days = Math.floor(totalSeconds / 86_400)
  const hours = Math.floor(totalSeconds % 86_400 / 3_600)
  const minutes = Math.floor(totalSeconds % 3_600 / 60)
  const parts: string[] = []
  if (days) {
    parts.push(`${days} 天`)
  }
  if (hours) {
    parts.push(`${hours} 小时`)
  }
  if (minutes || parts.length === 0) {
    parts.push(`${minutes} 分钟`)
  }
  return parts.slice(0, 2).join(' ')
}

function dailyTitle(item: DailySystemAvailability) {
  const availability = typeof item.uptimePercentage === 'number'
    ? `${item.uptimePercentage.toFixed(3)}%`
    : '暂无数据'
  const downtimePercentage = item.monitoredSeconds
    ? `${(item.downtimeSeconds * 100 / item.monitoredSeconds).toFixed(3)}%`
    : '暂无数据'
  return `${item.date} · 可用率 ${availability} · 异常 ${downtimePercentage} · ${formatDuration(item.downtimeSeconds)}`
}

function dailyCellStyle(item: DailySystemAvailability) {
  if (item.status !== 'DOWN' || !item.monitoredSeconds) {
    return undefined
  }

  const downtimeRatio = Math.min(1, item.downtimeSeconds / item.monitoredSeconds)
  return { '--day-down-opacity': `${0.28 + downtimeRatio * 0.72}` }
}

function minuteTitle(item: MinuteSystemAvailability) {
  const state = item.status === 'UP' ? '正常' : item.status === 'DOWN' ? '异常' : '暂无数据'
  return `${dayjs(item.startedAt).format('YYYY-MM-DD HH:mm')} · ${state}`
}

async function toggleDateFilter(date: string) {
  const nextDate = selectedDate.value === date ? null : date
  selectedDate.value = nextDate
  const requestVersion = ++minuteRequestVersion
  try {
    const response = await getMinuteSystemAvailability(nextDate ?? currentDate())
    if (requestVersion === minuteRequestVersion) {
      minutes.value = response.data ?? []
    }
    loadError.value = false
  }
  catch {
    loadError.value = true
  }
}

function clearDateFilter() {
  if (selectedDate.value) {
    void toggleDateFilter(selectedDate.value)
  }
}

onMounted(() => {
  void loadAll()
  refreshTimer = window.setInterval(() => {
    void refreshCurrentStatus()
  }, 60_000)
})

onBeforeUnmount(() => {
  if (refreshTimer !== undefined) {
    window.clearInterval(refreshTimer)
  }
})
</script>

<template>
  <main class="status-page">
    <div class="status-shell">
      <header class="status-header">
        <a class="brand" href="/" aria-label="返回 MRR">
          <span class="brand-mark"><img :src="serverLogo" alt=""></span>
          <span>
            <strong>服务状态</strong>
            <small>Medical Record Repository</small>
          </span>
        </a>
        <button class="refresh-button" type="button" :disabled="refreshing" @click="refreshCurrentStatus">
          <i class="i-ant-design:reload-outlined" :class="{ spinning: refreshing }" aria-hidden="true" />
          刷新状态
        </button>
      </header>

      <section v-if="loading" class="state-panel loading-panel" aria-busy="true">
        <span class="loading-dot" />
        <div>
          <strong>正在读取运行历史</strong>
          <p>正在汇总最近 {{ HISTORY_DAYS }} 天的服务状态。</p>
        </div>
      </section>

      <template v-else>
        <section
          class="state-panel"
          :class="`is-${currentState.toLowerCase().replace('_', '-')}`"
        >
          <div class="state-icon" aria-hidden="true">
            <i v-if="currentState === 'UP'" class="i-ant-design:check-circle-filled" />
            <i v-else-if="currentState === 'DOWN'" class="i-ant-design:warning-filled" />
            <i v-else class="i-ant-design:clock-circle-filled" />
          </div>
          <div class="state-copy">
            <span class="eyebrow">Current system status</span>
            <h1>{{ statusCopy.title }}</h1>
            <p>{{ statusCopy.description }}</p>
            <small>{{ currentDurationText }}</small>
          </div>
          <div class="state-meta">
            <span>最近检查</span>
            <strong>{{ formatDateTime(summary?.lastCheckedAt) }}</strong>
          </div>
        </section>

        <p v-if="loadError" class="inline-warning">
          部分状态数据暂时无法刷新，页面正在显示最后一次成功获取的结果。
        </p>

        <section class="summary-grid" aria-label="服务可用率汇总">
          <article class="summary-card">
            <span>最近 {{ HISTORY_DAYS }} 天可用率</span>
            <strong>{{ uptimeText }}</strong>
            <small>按已记录运行区间的实际时长计算</small>
          </article>
          <article class="summary-card">
            <span>已监控时长</span>
            <strong>{{ formatDuration(summary?.monitoredSeconds ?? 0) }}</strong>
            <small>首次启用状态记录后开始累计</small>
          </article>
          <article class="summary-card">
            <span>累计异常时长</span>
            <strong>{{ formatDuration(summary?.downtimeSeconds ?? 0) }}</strong>
            <small>包含重启后根据心跳缺口补录的区间</small>
          </article>
        </section>

        <section class="availability-chart-card">
          <div class="section-heading">
            <div>
              <span class="eyebrow">Availability trend</span>
              <h2>{{ selectedDate ? `${selectedDate} 可用率趋势` : '最近 24 小时可用率趋势' }}</h2>
              <p>根据分钟级运行记录按小时汇总；缺少运行记录的小时不计入可用率。</p>
            </div>
          </div>
          <MrrChart
            :option="hourlyAvailabilityChart"
            :empty="!hasHourlyAvailability"
            empty-description="最近一天暂无可用率数据"
            :height="250"
            :aria-label="selectedDate ? `${selectedDate} 可用率趋势图` : '最近 24 小时可用率趋势图'"
          />
        </section>

        <section class="history-card">
          <div class="section-heading">
            <div>
              <span class="eyebrow">Availability history</span>
              <h2>最近 {{ HISTORY_DAYS }} 天运行记录</h2>
              <p>每个方块代表一天，点击可同步查看当天的分钟记录和异常运行区间。</p>
            </div>
            <div class="legend" aria-label="状态图例">
              <span><i class="up" />正常</span>
              <span><i class="down" />存在异常</span>
              <span><i class="no-data" />暂无数据</span>
            </div>
          </div>

          <div class="daily-track">
            <button
              v-for="item in daily"
              :key="item.date"
              type="button"
              class="day-cell"
              :class="[
                `is-${item.status.toLowerCase().replace('_', '-')}`,
                { 'is-selected': selectedDate === item.date },
              ]"
              :title="dailyTitle(item)"
              :aria-label="dailyTitle(item)"
              :aria-pressed="selectedDate === item.date"
              :style="dailyCellStyle(item)"
              @click="toggleDateFilter(item.date)"
            />
          </div>
          <div class="track-labels">
            <span>{{ daily.at(0)?.date ?? '—' }}</span>
            <span>{{ daily.at(-1)?.date ?? '—' }}</span>
          </div>
        </section>

        <section class="minute-history-card">
          <div class="section-heading">
            <div>
              <span class="eyebrow">Minute-by-minute history</span>
              <h2>{{ selectedDate ? `${selectedDate} 运行记录` : '当天的运行记录' }}</h2>
              <p>每个方块代表一分钟；任一分钟内出现异常即标记为异常。</p>
            </div>
            <div class="legend" aria-label="分钟运行记录图例">
              <span><i class="up" />正常</span>
              <span><i class="down" />异常</span>
              <span><i class="no-data" />暂无数据</span>
            </div>
          </div>

          <div class="minute-track-wrapper">
            <div class="minute-hour-labels" aria-hidden="true">
              <span v-for="item in minuteHourLabels" :key="item.startedAt">
                {{ dayjs(item.startedAt).format('HH:mm') }}
              </span>
            </div>
            <div class="minute-track">
              <span
                v-for="item in minutes"
                :key="item.startedAt"
                class="minute-cell"
                :class="`is-${item.status.toLowerCase().replace('_', '-')}`"
                :title="minuteTitle(item)"
                :aria-label="minuteTitle(item)"
              />
            </div>
          </div>
        </section>

        <section class="incidents-card">
          <div class="section-heading">
            <div>
              <span class="eyebrow">Incident history</span>
              <h2>{{ selectedDate ? `${selectedDate} 异常运行区间` : '异常运行区间' }}</h2>
              <p>{{ selectedDate ? '仅展示与所选日期有时间重叠的异常区间。' : '仅展示系统自动检测或根据心跳中断推断出的异常时间段。' }}</p>
            </div>
            <button v-if="selectedDate" type="button" class="clear-date-filter" @click="clearDateFilter">
              查看全部
            </button>
          </div>

          <div v-if="filteredIncidents.length" class="incident-list">
            <article v-for="incident in filteredIncidents" :key="`${incident.startedAt}-${incident.endedAt}`" class="incident-row">
              <span class="incident-marker" aria-hidden="true" />
              <div class="incident-copy">
                <div>
                  <strong>{{ incident.reason }}</strong>
                  <span v-if="incident.ongoing" class="ongoing-tag">处理中</span>
                </div>
                <p>
                  {{ formatDateTime(incident.startedAt) }}
                  <span>至</span>
                  {{ formatDateTime(incident.endedAt) }}
                </p>
              </div>
              <strong class="incident-duration">{{ formatDuration(incident.durationSeconds) }}</strong>
            </article>
          </div>
          <div v-else class="empty-incidents">
            <i class="i-ant-design:safety-certificate-outlined" aria-hidden="true" />
            <strong>{{ selectedDate ? '当天没有异常记录' : '查询范围内没有异常记录' }}</strong>
            <p>{{ selectedDate ? '可选择其他日期或查看全部异常记录。' : '系统将在检测到状态变化后自动记录异常区间。' }}</p>
          </div>
        </section>
      </template>

      <footer class="status-footer">
        <span>MRR 服务状态</span>
        <span>页面每 60 秒自动刷新当前状态</span>
      </footer>
    </div>
  </main>
</template>

<style scoped>
.status-page {
  min-height: 100vh;
  padding: 36px 20px 28px;
  color: #172033;
  background:
    radial-gradient(circle at 12% 0%, rgb(37 99 235 / 9%), transparent 30%),
    radial-gradient(circle at 90% 8%, rgb(20 184 166 / 8%), transparent 26%),
    #f5f7fb;
}

.status-shell {
  width: min(1040px, 100%);
  margin: 0 auto;
}

.status-header,
.section-heading,
.state-panel,
.incident-row,
.status-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.status-header {
  gap: 24px;
  margin-bottom: 28px;
}

.brand {
  display: inline-flex;
  gap: 12px;
  align-items: center;
  color: inherit;
  text-decoration: none;
}

.brand-mark {
  display: block;
  flex: none;
  width: 52px;
  height: 52px;
}

.brand-mark img {
  display: block;
  width: 100%;
  height: 100%;
}

.brand > span:last-child {
  display: grid;
  gap: 2px;
}

.brand strong {
  font-size: 16px;
}

.brand small,
.state-copy small,
.summary-card small,
.status-footer,
.track-labels {
  font-size: 12px;
  color: #7b879d;
}

.refresh-button {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  padding: 9px 14px;
  font: inherit;
  font-size: 13px;
  color: #344054;
  cursor: pointer;
  background: rgb(255 255 255 / 84%);
  border: 1px solid #dfe5ef;
  border-radius: 10px;
  box-shadow: 0 5px 18px rgb(15 23 42 / 4%);
}

.refresh-button:disabled {
  cursor: wait;
  opacity: 0.65;
}

.spinning {
  animation: spin 0.8s linear infinite;
}

.state-panel,
.history-card,
.availability-chart-card,
.minute-history-card,
.incidents-card,
.summary-card {
  background: rgb(255 255 255 / 92%);
  border: 1px solid #e1e7f0;
  border-radius: 18px;
  box-shadow: 0 18px 50px rgb(15 23 42 / 5%);
}

.state-panel {
  position: relative;
  gap: 20px;
  min-height: 150px;
  padding: 26px 28px;
  overflow: hidden;
}

.state-panel::after {
  position: absolute;
  top: -70px;
  right: -60px;
  width: 190px;
  height: 190px;
  pointer-events: none;
  content: "";
  background: currentcolor;
  border-radius: 50%;
  opacity: 0.055;
}

.state-panel.is-up {
  color: #07845f;
  border-color: rgb(16 185 129 / 24%);
}

.state-panel.is-down {
  color: #c2413a;
  border-color: rgb(239 68 68 / 26%);
}

.state-panel.is-no-data {
  color: #667085;
}

.state-icon {
  display: grid;
  flex: 0 0 58px;
  place-items: center;
  width: 58px;
  height: 58px;
  font-size: 34px;
  background: currentcolor;
  border-radius: 18px;
}

.state-icon i {
  color: #fff;
}

.state-copy {
  flex: 1;
  min-width: 0;
}

.eyebrow {
  display: block;
  margin-bottom: 7px;
  font-size: 10px;
  font-weight: 800;
  color: #2563eb;
  text-transform: uppercase;
  letter-spacing: 0.14em;
}

.state-copy h1,
.section-heading h2 {
  margin: 0;
  color: #172033;
  letter-spacing: -0.025em;
}

.state-copy h1 {
  font-size: clamp(24px, 4vw, 32px);
}

.state-copy p,
.section-heading p,
.empty-incidents p {
  margin: 7px 0 0;
  font-size: 13px;
  line-height: 1.65;
  color: #68758b;
}

.state-copy small {
  display: block;
  margin-top: 9px;
}

.state-meta {
  display: grid;
  gap: 5px;
  min-width: 184px;
  padding-left: 22px;
  border-left: 1px solid #e6eaf1;
}

.state-meta span {
  font-size: 11px;
  color: #8994a7;
}

.state-meta strong {
  font-size: 13px;
  color: #344054;
}

.loading-panel {
  justify-content: flex-start;
  color: #2563eb;
}

.loading-dot {
  width: 16px;
  height: 16px;
  background: currentcolor;
  border-radius: 50%;
  box-shadow: 0 0 0 8px rgb(37 99 235 / 10%);
  animation: pulse 1.2s ease-in-out infinite;
}

.loading-panel p {
  margin: 5px 0 0;
  font-size: 13px;
  color: #68758b;
}

.inline-warning {
  padding: 10px 13px;
  margin: 12px 0 0;
  font-size: 12px;
  color: #9a6700;
  background: #fff8df;
  border: 1px solid #f3df9a;
  border-radius: 10px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.summary-card {
  display: grid;
  gap: 7px;
  padding: 20px;
}

.summary-card > span {
  font-size: 12px;
  color: #68758b;
}

.summary-card strong {
  font-size: 24px;
  color: #172033;
  letter-spacing: -0.03em;
}

.history-card,
.availability-chart-card,
.minute-history-card,
.incidents-card {
  padding: 24px;
  margin-top: 16px;
}

.section-heading {
  gap: 24px;
  align-items: flex-start;
}

.section-heading h2 {
  font-size: 19px;
}

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: flex-end;
  font-size: 11px;
  color: #68758b;
}

.legend span {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}

.legend i {
  width: 9px;
  height: 9px;
  border-radius: 3px;
}

.legend .up,
.day-cell.is-up,
.minute-cell.is-up {
  background: #20b486;
}

.legend .down,
.day-cell.is-down,
.minute-cell.is-down {
  background: #ef6b62;
}

.legend .no-data,
.day-cell.is-no-data,
.minute-cell.is-no-data {
  background: #dce2eb;
}

.day-cell.is-down {
  background-color: rgb(239 107 98 / var(--day-down-opacity, 1));
}

.daily-track {
  display: grid;
  grid-template-columns: repeat(45, minmax(5px, 1fr));
  gap: 4px;
  margin-top: 25px;
}

.minute-track-wrapper {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 8px;
  padding-right: 16px;
  margin-top: 22px;
  overflow: hidden;
}

.minute-history-card {
  container-name: minute-history;
  container-type: inline-size;
}

.minute-track {
  min-width: 0;
}

.minute-track {
  display: grid;
  grid-template-rows: repeat(24, minmax(0, 1fr));
  grid-template-columns: repeat(60, minmax(0, 1fr));
  gap: 2px;
  aspect-ratio: 2.2;
}

.minute-cell {
  border-radius: 2px;
}

.minute-hour-labels {
  display: grid;
  grid-template-rows: repeat(24, minmax(0, 1fr));
  gap: 2px;
  align-items: center;
  font-size: 10px;
  color: #7b879d;
  text-align: right;
}

@container minute-history (max-width: 800px) {
  .minute-track-wrapper {
    grid-template-columns: minmax(0, 1fr);
    padding-right: 0;
  }

  .minute-hour-labels {
    display: none;
  }
}

.day-cell {
  min-width: 5px;
  height: 34px;
  padding: 0;
  cursor: pointer;
  border: 0;
  border-radius: 4px;
  transition: opacity 120ms ease, transform 120ms ease;
}

.day-cell.is-selected {
  outline: 2px solid #172033;
  outline-offset: 2px;
}

.day-cell:hover {
  z-index: 1;
  opacity: 0.78;
  transform: translateY(-2px) scaleY(1.08);
}

.track-labels {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
}

.clear-date-filter {
  flex: none;
  padding: 7px 10px;
  font: inherit;
  font-size: 12px;
  color: #2563eb;
  cursor: pointer;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 7px;
}

.incident-list {
  margin-top: 20px;
  border-top: 1px solid #edf0f5;
}

.incident-row {
  gap: 14px;
  padding: 17px 2px;
  border-bottom: 1px solid #edf0f5;
}

.incident-marker {
  flex: 0 0 9px;
  width: 9px;
  height: 9px;
  background: #ef6b62;
  border-radius: 50%;
  box-shadow: 0 0 0 5px rgb(239 107 98 / 10%);
}

.incident-copy {
  flex: 1;
  min-width: 0;
}

.incident-copy > div {
  display: flex;
  gap: 8px;
  align-items: center;
}

.incident-copy strong,
.incident-duration {
  font-size: 13px;
  color: #344054;
}

.incident-copy p {
  margin: 5px 0 0;
  font-size: 12px;
  color: #8994a7;
}

.incident-copy p span {
  margin: 0 5px;
}

.ongoing-tag {
  padding: 2px 7px;
  font-size: 10px;
  color: #b42318;
  background: #feeceb;
  border-radius: 999px;
}

.incident-duration {
  flex: none;
}

.empty-incidents {
  display: grid;
  gap: 6px;
  justify-items: center;
  padding: 38px 20px 20px;
  color: #20a477;
  text-align: center;
}

.empty-incidents i {
  margin-bottom: 4px;
  font-size: 34px;
}

.empty-incidents strong {
  color: #344054;
}

.status-footer {
  gap: 20px;
  padding: 22px 4px 0;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@keyframes pulse {
  50% { opacity: 0.65; transform: scale(0.8); }
}

@media (width <= 760px) {
  .status-page {
    padding: 24px 14px;
  }

  .state-panel,
  .section-heading {
    align-items: flex-start;
  }

  .state-panel {
    flex-wrap: wrap;
    padding: 22px;
  }

  .state-meta {
    width: 100%;
    padding: 15px 0 0;
    margin-left: 78px;
    border-top: 1px solid #e6eaf1;
    border-left: 0;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }

  .section-heading {
    flex-direction: column;
  }

  .legend {
    justify-content: flex-start;
  }

  .daily-track {
    grid-template-columns: repeat(30, minmax(5px, 1fr));
  }
}

@media (width <= 520px) {
  .status-header,
  .incident-row,
  .status-footer {
    align-items: flex-start;
  }

  .brand small {
    display: none;
  }

  .refresh-button {
    padding-inline: 11px;
  }

  .state-icon {
    flex-basis: 48px;
    width: 48px;
    height: 48px;
    font-size: 28px;
    border-radius: 15px;
  }

  .state-meta {
    margin-left: 0;
  }

  .history-card,
  .availability-chart-card,
  .minute-history-card,
  .incidents-card {
    padding: 20px;
  }

  .daily-track {
    grid-template-columns: repeat(18, minmax(5px, 1fr));
  }

  .incident-row,
  .status-footer {
    flex-direction: column;
  }

  .incident-duration {
    margin-left: 23px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .spinning,
  .loading-dot {
    animation: none;
  }

  .day-cell {
    transition: none;
  }
}
</style>

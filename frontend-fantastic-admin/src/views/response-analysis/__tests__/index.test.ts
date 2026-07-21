import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ResponseAnalysisPage from '../index.vue'

const { getResponseMetricAnalysis } = vi.hoisted(() => ({
  getResponseMetricAnalysis: vi.fn(),
}))

vi.mock('@/api/modules/response-metrics', () => ({
  getResponseMetricAnalysis,
}))

const analysisData = {
  overview: {
    totalRequests: 120,
    successRate: 98.5,
    p95ClientDurationMs: 320.4,
    avgServerDurationMs: 88.2,
  },
  trend: [
    { bucket: '2026-07-12', requestCount: 50, errorCount: 1, avgClientDurationMs: 180, avgServerDurationMs: 70 },
    { bucket: '2026-07-13', requestCount: 70, errorCount: 1, avgClientDurationMs: 220, avgServerDurationMs: 92 },
  ],
  slowEndpoints: [
    {
      routePattern: '/api/v1/statistics/summary',
      method: 'GET',
      requestCount: 20,
      errorCount: 1,
      avgClientDurationMs: 250,
      p95ClientDurationMs: 420,
      avgServerDurationMs: 80,
    },
  ],
}

function mountPage() {
  return mount(ResponseAnalysisPage, {
    global: {
      directives: {
        loading: {},
      },
      stubs: {
        'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
        'el-card': { template: '<section class="el-card"><slot name="header" /><slot /></section>' },
        'el-empty': { props: ['description'], template: '<div class="el-empty">{{ description }}</div>' },
        'el-icon': { template: '<i><slot /></i>' },
        'el-option': true,
        'el-table': { props: ['data'], template: '<div class="el-table"><div v-for="row in data" :key="row.routePattern" class="slow-row">{{ row.method }} {{ row.routePattern }}</div><slot /></div>' },
        'el-table-column': true,
        'ResponseTrendChart': { props: ['data'], template: '<div class="response-trend-chart">{{ data.length }}</div>' },
      },
    },
  })
}

describe('responseAnalysisPage', () => {
  beforeEach(() => {
    getResponseMetricAnalysis.mockReset()
  })

  it('loads the default 365-day range and renders overview, trend, and slow endpoints', async () => {
    getResponseMetricAnalysis.mockResolvedValue({ code: 200, data: analysisData })
    const wrapper = mountPage()
    await flushPromises()

    expect(getResponseMetricAnalysis).toHaveBeenCalledWith(365)
    expect(wrapper.text()).toContain('120')
    expect(wrapper.text()).toContain('98.5%')
    expect(wrapper.text()).toContain('320.4 ms')
    expect(wrapper.text()).toContain('88.2 ms')
    expect(wrapper.find('.response-trend-chart').text()).toBe('2')
    expect(wrapper.text()).toContain('GET /api/v1/statistics/summary')
  })

  it('shows an empty state when no response metrics exist', async () => {
    getResponseMetricAnalysis.mockResolvedValue({
      code: 200,
      data: { overview: { totalRequests: 0 }, trend: [], slowEndpoints: [] },
    })
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('暂无响应指标数据')
  })

  it('keeps frontend-only metrics visible while server logs are still flushing', async () => {
    getResponseMetricAnalysis.mockResolvedValue({
      code: 200,
      data: {
        overview: { totalRequests: 0, frontendSampleCount: 1 },
        trend: [],
        slowEndpoints: [{ ...analysisData.slowEndpoints[0], requestCount: 1 }],
      },
    })
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).not.toContain('暂无响应指标数据')
    expect(wrapper.text()).toContain('GET /api/v1/statistics/summary')
  })

  it('shows an error state when loading fails', async () => {
    getResponseMetricAnalysis.mockRejectedValue(new Error('request failed'))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('响应分析数据加载失败')
  })
})

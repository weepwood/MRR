import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const getResponseMetricAnalysis = vi.fn()

vi.mock('@/api/modules/response-metrics', () => ({
  getResponseMetricAnalysis,
}))

import ResponseAnalysisPage from '../index.vue'

const analysisData = {
  overview: {
    totalRequests: 120,
    successRate: 98.5,
    clientP95DurationMs: 320.4,
    serverAverageDurationMs: 88.2,
  },
  trend: [
    { bucket: '2026-07-12', requestCount: 50, clientAverageDurationMs: 180, serverAverageDurationMs: 70 },
    { bucket: '2026-07-13', requestCount: 70, clientAverageDurationMs: 220, serverAverageDurationMs: 92 },
  ],
  slowEndpoints: [
    {
      endpointTemplate: '/api/v1/statistics/summary',
      method: 'GET',
      requestCount: 20,
      errorRate: 1.5,
      clientAverageDurationMs: 250,
      clientP95DurationMs: 420,
      serverAverageDurationMs: 80,
      maxDurationMs: 560,
    },
  ],
}

function mountPage() {
  return mount(ResponseAnalysisPage, {
    global: {
      stubs: {
        'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
        'el-card': { template: '<section class="el-card"><slot name="header" /><slot /></section>' },
        'el-empty': { props: ['description'], template: '<div class="el-empty">{{ description }}</div>' },
        'el-icon': { template: '<i><slot /></i>' },
        'el-option': true,
        'el-select': {
          props: ['modelValue'],
          emits: ['update:modelValue', 'change'],
          template: '<select class="range-select" :value="modelValue" @change="$emit(\'update:modelValue\', Number($event.target.value)); $emit(\'change\')"><option value="1">1</option><option value="7">7</option><option value="30">30</option></select>',
        },
        'el-table': { props: ['data'], template: '<div class="el-table"><div v-for="row in data" :key="row.endpointTemplate" class="slow-row">{{ row.method }} {{ row.endpointTemplate }}</div><slot /></div>' },
        'el-table-column': true,
        ResponseTrendChart: { props: ['data'], template: '<div class="response-trend-chart">{{ data.length }}</div>' },
      },
    },
  })
}

describe('ResponseAnalysisPage', () => {
  beforeEach(() => {
    getResponseMetricAnalysis.mockReset()
  })

  it('loads the last seven days and renders overview, trend, and slow endpoints', async () => {
    getResponseMetricAnalysis.mockResolvedValue({ code: 200, data: analysisData })
    const wrapper = mountPage()
    await flushPromises()

    expect(getResponseMetricAnalysis).toHaveBeenCalledWith(7)
    expect(wrapper.text()).toContain('120')
    expect(wrapper.text()).toContain('98.5%')
    expect(wrapper.text()).toContain('320.4 ms')
    expect(wrapper.text()).toContain('88.2 ms')
    expect(wrapper.find('.response-trend-chart').text()).toBe('2')
    expect(wrapper.text()).toContain('GET /api/v1/statistics/summary')
  })

  it('reloads data when the time range changes', async () => {
    getResponseMetricAnalysis.mockResolvedValue({ code: 200, data: analysisData })
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.find('.range-select').setValue('30')
    await flushPromises()

    expect(getResponseMetricAnalysis).toHaveBeenLastCalledWith(30)
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

  it('shows an error state when loading fails', async () => {
    getResponseMetricAnalysis.mockRejectedValue(new Error('request failed'))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('响应分析数据加载失败')
  })
})

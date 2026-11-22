import { flushPromises, mount } from '@vue/test-utils'
import { ElMessage } from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { downloadBlob } from '@/utils/file-download'
import StatisticsDetailPage from '../index.vue'

const api = vi.hoisted(() => ({
  exportStatisticsCsv: vi.fn(),
  getStatisticsList: vi.fn(),
  getStatisticsSummary: vi.fn(),
}))

const settingsApi = vi.hoisted(() => ({
  getSystemSettings: vi.fn(),
}))

vi.mock('@/api/modules/statistics', () => api)
vi.mock('@/api/modules/settings', () => settingsApi)
vi.mock('@/utils/file-download', () => ({ downloadBlob: vi.fn() }))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

const stubs = {
  'AppEmpty': true,
  'AppError': true,
  'AppLoading': true,
  // 全局注册的指标卡在测试环境不会自动解析，用轻量 stub 暴露 label/value/note 供文本断言。
  'MrrMetricCard': {
    props: ['label', 'value', 'note', 'suffix'],
    template: '<div class="mrr-metric-card">{{ label }}|{{ value }}|{{ note }}|{{ suffix }}</div>',
  },
  'el-button': { template: '<button><slot/></button>' },
  'el-card': { template: '<section><slot name="header"/><slot/></section>' },
  'el-date-picker': true,
  'el-icon': { template: '<i><slot/></i>' },
  'el-input': true,
  'el-option': true,
  'el-pagination': {
    props: ['layout', 'pageSize'],
    template: '<div class="pagination-stub" :data-layout="layout" :data-page-size="pageSize" />',
  },
  'el-radio-button': { template: '<button><slot/></button>' },
  'el-radio-group': { template: '<div><slot/></div>' },
  'el-select': { template: '<div><slot/></div>' },
  'el-table': true,
  'el-table-column': true,
  'el-tag': { template: '<span><slot/></span>' },
}

let containerWidth = 0
let resizeCallback: ResizeObserverCallback
let observe: ReturnType<typeof vi.fn>
let disconnect: ReturnType<typeof vi.fn>

function triggerResize(width: number) {
  containerWidth = width
  resizeCallback([], {} as ResizeObserver)
}

function createArchiveResponse(count: number, size: number) {
  return {
    data: {
      list: Array.from({ length: count }, (_, index) => ({
        bah: `BAH-${index + 1}`,
        cid: 'D',
        date: '2026-01-01',
        openerNo: 'dev',
        pages: 1,
        sjh: '',
        type: '普通',
      })),
      page: 1,
      size,
      total: 100,
      totalPages: Math.ceil(100 / size),
    },
  }
}

describe('statistics detail responsive pagination', () => {
  beforeEach(() => {
    containerWidth = 1168
    observe = vi.fn()
    disconnect = vi.fn()

    vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockImplementation(() => containerWidth)
    vi.stubGlobal('ResizeObserver', class {
      constructor(callback: ResizeObserverCallback) {
        resizeCallback = callback
      }

      observe = observe
      disconnect = disconnect
      unobserve = vi.fn()
    })

    api.getStatisticsList.mockReset().mockImplementation(({ page, size }) => Promise.resolve({
      data: { list: [], total: 100, page, size, totalPages: Math.ceil(100 / size) },
    }))
    api.getStatisticsSummary.mockReset().mockResolvedValue({
      data: { byType: [], total: {} },
    })
    api.exportStatisticsCsv.mockReset()
    settingsApi.getSystemSettings.mockReset().mockResolvedValue({ data: {} })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('shows the export limit message when the CSV request is rejected', async () => {
    const limitMessage = '统计导出超过单次 100000 条上限，请缩小筛选范围'
    const showError = vi.spyOn(ElMessage, 'error').mockReturnValue({ close: vi.fn() })
    api.exportStatisticsCsv.mockRejectedValue(new Error(limitMessage))
    const wrapper = mount(StatisticsDetailPage, { global: { stubs } })
    await flushPromises()

    await wrapper.get('.mrr-page-header__aside button').trigger('click')
    await flushPromises()

    expect(showError).toHaveBeenCalledWith(`导出失败：${limitMessage}`)
    wrapper.unmount()
  })

  it('downloads the Blob returned by the CSV request without changing its bytes', async () => {
    const csv = new Blob(['bah,patientName\n0001,Sample'], { type: 'text/csv' })
    api.exportStatisticsCsv.mockResolvedValue(csv)
    const wrapper = mount(StatisticsDetailPage, { global: { stubs } })
    await flushPromises()

    await wrapper.get('.mrr-page-header__aside button').trigger('click')
    await flushPromises()

    expect(downloadBlob).toHaveBeenCalledOnce()
    expect(vi.mocked(downloadBlob).mock.calls[0]?.[0]).toBe(csv)
    expect(vi.mocked(downloadBlob).mock.calls[0]?.[1]).toMatch(/^statistics-\d+\.csv$/)
    wrapper.unmount()
  })

  it('uses a complete-row page size on the first request', async () => {
    const wrapper = mount(StatisticsDetailPage, { global: { stubs } })
    await flushPromises()

    expect(api.getStatisticsList).toHaveBeenCalledOnce()
    expect(api.getStatisticsList).toHaveBeenCalledWith(expect.objectContaining({ page: 1, size: 18 }))
    expect(observe).toHaveBeenCalledWith(wrapper.find('.archive-shelf').element)
    expect(wrapper.find('.pagination-stub').attributes('data-page-size')).toBe('18')
    expect(wrapper.find('.pagination-stub').attributes('data-layout')).not.toContain('sizes')

    wrapper.unmount()
    expect(disconnect).toHaveBeenCalledOnce()
  })

  it('reloads only when a resize changes the complete-row page size', async () => {
    const wrapper = mount(StatisticsDetailPage, { global: { stubs } })
    await flushPromises()

    expect(api.getStatisticsList).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, size: 18 }))

    triggerResize(1440)
    await flushPromises()

    expect(api.getStatisticsList).toHaveBeenCalledTimes(2)
    expect(api.getStatisticsList).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, size: 20 }))
    expect(api.getStatisticsSummary).toHaveBeenCalledOnce()

    triggerResize(1500)
    await flushPromises()

    expect(api.getStatisticsList).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('ignores an older resize response that finishes last', async () => {
    const wrapper = mount(StatisticsDetailPage, { global: { stubs } })
    await flushPromises()

    let resolveOlderRequest: ((value: unknown) => void) | undefined
    api.getStatisticsList
      .mockImplementationOnce(() => new Promise((resolve) => {
        resolveOlderRequest = resolve
      }))
      .mockResolvedValueOnce(createArchiveResponse(21, 21))

    triggerResize(1440)
    triggerResize(2500)
    await flushPromises()

    expect(wrapper.findAll('.archive-folder-card')).toHaveLength(21)

    resolveOlderRequest?.(createArchiveResponse(20, 20))
    await flushPromises()

    expect(wrapper.findAll('.archive-folder-card')).toHaveLength(21)
    wrapper.unmount()
  })

  it('does not start observing after the page unmounts during its first request', async () => {
    let resolveInitialRequest: ((value: unknown) => void) | undefined
    api.getStatisticsList.mockImplementationOnce(() => new Promise((resolve) => {
      resolveInitialRequest = resolve
    }))

    const wrapper = mount(StatisticsDetailPage, { global: { stubs } })
    expect(api.getStatisticsList).toHaveBeenCalledOnce()

    wrapper.unmount()
    resolveInitialRequest?.(createArchiveResponse(18, 18))
    await flushPromises()

    expect(api.getStatisticsList).toHaveBeenCalledOnce()
    expect(observe).not.toHaveBeenCalled()
  })
})

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import ArchiveScanInspector from '../components/ArchiveScanInspector.vue'

const api = vi.hoisted(() => ({ getArchiveScanPage: vi.fn() }))
vi.mock('@/api/modules/data-relations', () => api)
const session = reactive({ token: 'synthetic', allowed: true })
vi.mock('@/utils/session', () => ({ hasPermission: () => session.allowed }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => session }))

const row = (id: number, archiveId = 1) => ({ id, archiveId, pages: 1, uploadFlag: 1, btype: 0 })
function card() {
  return mount(ArchiveScanInspector, {
    props: { archiveId: 1 },
    global: { stubs: {
      'MrrSectionCard': { template: '<section><slot /></section>' },
      'MrrScanContentDialog': { props: ['scanId'], template: '<div data-test="image">{{ scanId }}</div>' },
      'OcrPanel': { props: { scanId: Number, inline: Boolean }, template: '<div data-test="ocr">{{ scanId }} {{ inline }}</div>' },
      'AppEmpty': true,
      'AppError': { name: 'AppError', props: ['message'], emits: ['retry'], template: '<p>{{ message }}<button @click="$emit(\'retry\')">重试</button></p>' },
      'el-skeleton': true,
      'el-button': { props: ['disabled'], template: '<button :disabled="disabled"><slot /></button>' },
    } },
  })
}
describe('病案影像核对', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    session.allowed = true
    session.token = 'synthetic'
  })
  it('按游标切换页面并清除原图；内嵌OCR只绑定已选影像', async () => {
    api.getArchiveScanPage.mockResolvedValueOnce({ data: { archiveId: 1, rows: [row(11)], nextCursor: { pages: 1, id: 11 } } })
      .mockResolvedValueOnce({ data: { archiveId: 1, rows: [row(12)], nextCursor: null } })
      .mockResolvedValueOnce({ data: { archiveId: 1, rows: [row(11)], nextCursor: { pages: 1, id: 11 } } })
    const wrapper = card()
    await flushPromises()
    expect(wrapper.find('[data-test="image"]').exists()).toBe(false)
    await wrapper.get('.scan-row').trigger('click')
    expect(wrapper.get('[data-test="image"]').text()).toBe('11')
    expect(wrapper.get('[data-test="ocr"]').text()).toBe('11 true')
    await wrapper.findAll('button').find(b => b.text() === '下一页')!.trigger('click')
    await flushPromises()
    expect(api.getArchiveScanPage.mock.calls[1].slice(0, 2)).toEqual([1, { pages: 1, id: 11 }])
    expect(wrapper.find('[data-test="image"]').exists()).toBe(false)
    await wrapper.get('.scan-row').trigger('click')
    expect(wrapper.get('[data-test="image"]').text()).toBe('12')
    await wrapper.findAll('button').find(b => b.text() === '上一页')!.trigger('click')
    await flushPromises()
    expect(wrapper.get('.scan-row').text()).toContain('11')
    wrapper.unmount()
  })
  it('当前组导航与原图专注模式保持选中身份，不触发新分页', async () => {
    api.getArchiveScanPage.mockResolvedValue({ data: { archiveId: 1, rows: [row(11), row(12), { ...row(13), pages: null }], nextCursor: null } })
    const wrapper = card()
    await flushPromises()
    const button = (label: string) => wrapper.findAll('button').find(b => b.text() === label)!
    const scans = wrapper.findAll('.scan-row')
    await scans[0].trigger('click')
    expect(button('上一张').attributes('disabled')).toBeDefined()
    await button('下一张').trigger('click')
    expect(wrapper.get('[data-test="image"]').text()).toBe('12')
    await scans[1].trigger('keydown', { key: 'End' })
    expect(wrapper.get('[data-test="image"]').text()).toBe('13')
    expect(button('下一张').attributes('disabled')).toBeDefined()
    await button('专注原图').trigger('click')
    expect(wrapper.get('[data-test="image"]').text()).toBe('13')
    expect(wrapper.find('[data-test="ocr"]').exists()).toBe(false)
    await button('恢复布局').trigger('click')
    expect(wrapper.get('[data-test="ocr"]').text()).toBe('13 true')
    expect(api.getArchiveScanPage).toHaveBeenCalledOnce()
    wrapper.unmount()
  })
  it('显式逐张跨组导航保持专注模式，选择下一组首张和上一组末张', async () => {
    const first = { data: { archiveId: 1, rows: [row(11), row(12)], nextCursor: { pages: 1, id: 12 } } }
    const second = { data: { archiveId: 1, rows: [row(13), row(14)], nextCursor: null } }
    api.getArchiveScanPage.mockResolvedValueOnce(first).mockResolvedValueOnce(second).mockResolvedValueOnce(first)
    const wrapper = card()
    await flushPromises()
    const button = (label: string) => wrapper.findAll('button').find(b => b.text() === label)!
    await wrapper.findAll('.scan-row')[1].trigger('click')
    await button('专注原图').trigger('click')
    expect(button('下一张').attributes('disabled')).toBeUndefined()
    await button('下一张').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-test="image"]').text()).toBe('13')
    expect(wrapper.find('[data-test="ocr"]').exists()).toBe(false)
    expect(api.getArchiveScanPage.mock.calls[1].slice(0, 2)).toEqual([1, { pages: 1, id: 12 }])
    await button('上一张').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-test="image"]').text()).toBe('12')
    expect(wrapper.find('[data-test="ocr"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('跨组失败不显示旧原图，重试保留显式导航意图，普通翻组仍不选图', async () => {
    const first = { data: { archiveId: 1, rows: [row(11)], nextCursor: { pages: 1, id: 11 } } }
    const second = { data: { archiveId: 1, rows: [row(12)], nextCursor: null } }
    api.getArchiveScanPage.mockResolvedValueOnce(first).mockRejectedValueOnce(new Error('synthetic failure')).mockResolvedValueOnce(second).mockResolvedValueOnce(first)
    const wrapper = card()
    await flushPromises()
    const button = (label: string) => wrapper.findAll('button').find(b => b.text() === label)!
    await wrapper.get('.scan-row').trigger('click')
    await button('下一张').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-test="image"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('影像列表读取失败')
    await button('重试').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-test="image"]').text()).toBe('12')
    await button('上一页').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-test="image"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('跨组途中换病案，旧导航响应不能自动打开新病案影像', async () => {
    let resolve!: (value: unknown) => void
    api.getArchiveScanPage.mockResolvedValueOnce({ data: { archiveId: 1, rows: [row(11)], nextCursor: { pages: 1, id: 11 } } })
      .mockImplementationOnce(() => new Promise((r) => { resolve = r }))
      .mockResolvedValueOnce({ data: { archiveId: 2, rows: [row(21, 2)], nextCursor: null } })
    const wrapper = card()
    await flushPromises()
    await wrapper.get('.scan-row').trigger('click')
    await wrapper.findAll('button').find(b => b.text() === '下一张')!.trigger('click')
    expect(wrapper.find('[data-test="image"]').exists()).toBe(false)
    await wrapper.setProps({ archiveId: 2 })
    await flushPromises()
    resolve({ data: { archiveId: 1, rows: [row(12)], nextCursor: null } })
    await flushPromises()
    expect(wrapper.get('.scan-row').text()).toContain('21')
    expect(wrapper.find('[data-test="image"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('实时数据变化导致相邻组为空时不选择旧影像或猜测下一游标', async () => {
    api.getArchiveScanPage.mockResolvedValueOnce({ data: { archiveId: 1, rows: [row(11)], nextCursor: { pages: 1, id: 11 } } })
      .mockResolvedValueOnce({ data: { archiveId: 1, rows: [], nextCursor: null } })
    const wrapper = card()
    await flushPromises()
    await wrapper.get('.scan-row').trigger('click')
    await wrapper.findAll('button').find(b => b.text() === '下一张')!.trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-test="image"]').exists()).toBe(false)
    expect(wrapper.findAll('button').find(b => b.text() === '下一页')!.attributes('disabled')).toBeDefined()
    expect(wrapper.findAll('button').find(b => b.text() === '上一页')!.attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })

  it('跨组途中会话改变后取消导航，不自动选择影像', async () => {
    let resolve!: (value: unknown) => void
    api.getArchiveScanPage.mockResolvedValueOnce({ data: { archiveId: 1, rows: [row(11)], nextCursor: { pages: 1, id: 11 } } })
      .mockImplementationOnce(() => new Promise((r) => { resolve = r }))
    const wrapper = card()
    await flushPromises()
    await wrapper.get('.scan-row').trigger('click')
    await wrapper.findAll('button').find(b => b.text() === '下一张')!.trigger('click')
    const signal = api.getArchiveScanPage.mock.calls[1][2] as AbortSignal
    session.token = 'different-synthetic-session'
    resolve({ data: { archiveId: 1, rows: [row(12)], nextCursor: null } })
    await flushPromises()
    expect(signal.aborted).toBe(true)
    expect(wrapper.find('.scan-row').exists()).toBe(false)
    expect(wrapper.find('[data-test="image"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it.each([
    { rows: [row(12), row(11)], nextCursor: null },
    { rows: [row(11)], nextCursor: { pages: 2, id: 11 } },
    { rows: [{ ...row(11), pages: null }, row(12)], nextCursor: null },
  ])('拒绝非递增位置或与末行不一致的游标', async (invalid) => {
    api.getArchiveScanPage.mockResolvedValue({ data: { archiveId: 1, ...invalid } })
    const wrapper = card()
    await flushPromises()
    expect(wrapper.find('.scan-row').exists()).toBe(false)
    expect(wrapper.text()).toContain('影像列表读取失败')
    wrapper.unmount()
  })
  it('拒绝跨病案数据与迟到响应；权限撤销清空列表', async () => {
    let resolve!: (value: unknown) => void
    api.getArchiveScanPage.mockImplementationOnce(() => new Promise(r => resolve = r))
    const wrapper = card()
    const signal = api.getArchiveScanPage.mock.calls[0][2] as AbortSignal
    api.getArchiveScanPage.mockResolvedValueOnce({ data: { archiveId: 2, rows: [row(11)], nextCursor: null } })
    await wrapper.setProps({ archiveId: 2 })
    await flushPromises()
    expect(signal.aborted).toBe(true)
    resolve({ data: { archiveId: 1, rows: [row(11)], nextCursor: null } })
    await flushPromises()
    expect(wrapper.find('.scan-row').exists()).toBe(false)
    expect(wrapper.text()).toContain('影像列表读取失败')
    session.allowed = false
    await flushPromises()
    expect(wrapper.text()).toBe('')
    wrapper.unmount()
  })
})

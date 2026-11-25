import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ScanContent from '../index.vue'

const api = vi.hoisted(() => ({ getReviewImage: vi.fn() }))
vi.mock('@/api/modules/classification', () => api)
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ token: 'synthetic' }) }))
const image = () => new Blob(['image'], { type: 'image/png' })
const stubs = { 'el-button': { props: ['disabled'], template: '<button :disabled="disabled"><slot /></button>' } }
function viewer(scanId: number) {
  return mount(ScanContent, { props: { scanId, inline: true }, global: { stubs } })
}

describe('受控影像原图读取', () => {
  const create = vi.fn(() => 'blob:synthetic-image')
  const revoke = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('URL', class extends URL {
      static createObjectURL = create
      static revokeObjectURL = revoke
    })
  })
  afterEach(() => vi.unstubAllGlobals())

  it('读取解包后的 Blob，换图时释放旧资源并拒绝非影像响应', async () => {
    api.getReviewImage.mockResolvedValueOnce(image()).mockResolvedValueOnce(new Blob(['error'], { type: 'application/json' }))
    const wrapper = viewer(1)
    await flushPromises()
    expect(wrapper.get('img').attributes('src')).toBe('blob:synthetic-image')
    await wrapper.setProps({ scanId: 2 })
    await flushPromises()
    expect(revoke).toHaveBeenCalledWith('blob:synthetic-image')
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain('影像不可读或已变化')
    wrapper.unmount()
  })

  it('缩放有界，旋转改变布局尺寸，换图恢复默认视图且不重复请求', async () => {
    api.getReviewImage.mockResolvedValue(image())
    const wrapper = viewer(1)
    await flushPromises()
    const viewport = wrapper.get('.scan-viewport').element
    Object.defineProperties(viewport, { clientWidth: { value: 600 }, clientHeight: { value: 400 } })
    const img = wrapper.get('img')
    Object.defineProperties(img.element, { naturalWidth: { value: 1000 }, naturalHeight: { value: 2000 } })
    await img.trigger('load')
    await flushPromises()
    const button = (label: string) => wrapper.findAll('button').find(b => b.text() === label)!
    expect(img.attributes('style')).toContain('rotate(0deg)')
    for (let i = 0; i < 12; i++) { await button('放大').trigger('click') }
    expect(wrapper.get('.scan-viewer-percent').text()).toBe('300%')
    expect(button('放大').attributes('disabled')).toBeDefined()
    await button('旋转 90°').trigger('click')
    expect(img.attributes('style')).toContain('rotate(90deg)')
    expect(api.getReviewImage).toHaveBeenCalledTimes(1)
    await button('适应窗口').trigger('click')
    expect(wrapper.get('.scan-viewer-percent').text()).toBe('100%')
    expect(img.attributes('style')).toContain('rotate(0deg)')
    await button('放大').trigger('click')
    await wrapper.setProps({ scanId: 2 })
    await flushPromises()
    expect(wrapper.get('.scan-viewer-percent').text()).toBe('100%')
    expect(wrapper.get('.scan-viewer-angle').text()).toBe('0°')
    expect(api.getReviewImage).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('键盘缩放与复位不重新读取原图，普通滚轮不改变倍率', async () => {
    api.getReviewImage.mockResolvedValue(image())
    const wrapper = viewer(1)
    await flushPromises()
    const viewport = wrapper.get('.scan-viewport')
    Object.defineProperties(viewport.element, { clientWidth: { value: 600 }, clientHeight: { value: 400 } })
    const img = wrapper.get('img')
    Object.defineProperties(img.element, { naturalWidth: { value: 1000 }, naturalHeight: { value: 2000 } })
    await img.trigger('load')
    await flushPromises()
    await viewport.trigger('wheel', { deltaY: -100 })
    expect(wrapper.get('.scan-viewer-percent').text()).toBe('100%')
    await viewport.trigger('keydown', { key: '+' })
    expect(wrapper.get('.scan-viewer-percent').text()).toBe('125%')
    await viewport.trigger('wheel', { deltaY: -100, ctrlKey: true })
    expect(wrapper.get('.scan-viewer-percent').text()).toBe('150%')
    await viewport.trigger('keydown', { key: '-' })
    expect(wrapper.get('.scan-viewer-percent').text()).toBe('125%')
    await viewport.trigger('keydown', { key: '0' })
    expect(wrapper.get('.scan-viewer-percent').text()).toBe('100%')
    expect(api.getReviewImage).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('解码失败立即释放对象URL，重试重新读取且不留下旧影像', async () => {
    api.getReviewImage.mockResolvedValue(image())
    const wrapper = viewer(1)
    await flushPromises()
    await wrapper.get('img').trigger('error')
    expect(revoke).toHaveBeenCalledWith('blob:synthetic-image')
    expect(wrapper.find('img').exists()).toBe(false)
    await wrapper.findAll('button').find(b => b.text() === '重试')!.trigger('click')
    await flushPromises()
    expect(api.getReviewImage).toHaveBeenCalledTimes(2)
    expect(wrapper.get('img').attributes('src')).toBe('blob:synthetic-image')
    wrapper.unmount()
  })

  it('迟到的旧请求不得生成 Blob URL，卸载释放当前图像', async () => {
    let resolve!: (value: Blob) => void
    api.getReviewImage.mockImplementationOnce(() => new Promise(r => resolve = r)).mockResolvedValueOnce(image())
    const wrapper = viewer(1)
    const firstSignal = api.getReviewImage.mock.calls[0][1] as AbortSignal
    await wrapper.setProps({ scanId: 2 })
    expect(firstSignal.aborted).toBe(true)
    await flushPromises()
    resolve(image())
    await flushPromises()
    expect(create).toHaveBeenCalledTimes(1)
    expect(wrapper.get('img').attributes('src')).toBe('blob:synthetic-image')
    wrapper.unmount()
    expect(revoke).toHaveBeenCalledWith('blob:synthetic-image')
  })
})

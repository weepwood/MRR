import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ScanContent from '../index.vue'

const api = vi.hoisted(() => ({ getReviewImage: vi.fn() }))
vi.mock('@/api/modules/classification', () => api)
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ token: 'synthetic' }) }))
const image = () => new Blob(['image'], { type: 'image/png' })

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
    const wrapper = mount(ScanContent, { props: { scanId: 1, inline: true } })
    await flushPromises()
    expect(wrapper.get('img').attributes('src')).toBe('blob:synthetic-image')
    await wrapper.setProps({ scanId: 2 })
    await flushPromises()
    expect(revoke).toHaveBeenCalledWith('blob:synthetic-image')
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain('影像不可读或已变化')
    wrapper.unmount()
  })

  it('迟到的旧请求不得生成 Blob URL，卸载释放当前图像', async () => {
    let resolve!: (value: Blob) => void
    api.getReviewImage.mockImplementationOnce(() => new Promise(r => resolve = r)).mockResolvedValueOnce(image())
    const wrapper = mount(ScanContent, { props: { scanId: 1, inline: true } })
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

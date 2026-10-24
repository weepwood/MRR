import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import ScanImgPage from '../index.vue'

const { loadByToken } = vi.hoisted(() => ({ loadByToken: vi.fn() }))

vi.mock('vue-router', () => ({ useRoute: () => ({ query: { id: 'opaque-test-token' }, params: {} }) }))
vi.mock('@/api/modules/search', () => ({ getArchiveCasesByToken: loadByToken }))
vi.mock('@/api/modules/image', () => ({ getImgApiByBah: vi.fn(), updateImageType: vi.fn() }))

describe('图库身份证查询', () => {
  it('只使用受控令牌恢复档案结果', async () => {
    loadByToken.mockResolvedValue({
      data: { token: 'opaque-test-token', maskedIdCard: '110***********1234', cases: [] },
    })
    const wrapper = mount(ScanImgPage, {
      global: {
        stubs: {
          'el-tooltip': { template: '<div><slot /></div>' },
          'el-icon': true,
          'el-image': true,
        },
      },
    })
    await flushPromises()

    expect(loadByToken).toHaveBeenCalledWith('opaque-test-token')
    expect(wrapper.text()).toContain('110***********1234')
    wrapper.unmount()
  })
})

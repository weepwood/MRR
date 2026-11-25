import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import ArchiveScanInspector from '../components/ArchiveScanInspector.vue'

vi.mock('@/utils/session', () => ({ hasPermission: (permission: string) => ['record:read', 'ocr:read'].includes(permission) }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ token: 'synthetic' }) }))

describe('病案影像核对', () => {
  it('只显式读取本病案有效样本，病案切换清除选择', async () => {
    const wrapper = mount(ArchiveScanInspector, {
      props: {
        archiveId: 1,
        samples: [
          { id: 11, archiveId: 1, pages: 1, uploadFlag: 1, btype: 0 },
          { id: 12, archiveId: 1, pages: 2, uploadFlag: 0 },
          { id: 21, archiveId: 2, pages: 1, uploadFlag: 1 },
        ],
      },
      global: {
        stubs: {
          'MrrSectionCard': { template: '<section><slot /></section>' },
          'MrrScanContentDialog': { props: ['scanId'], template: '<div data-test="image">{{ scanId }}</div>' },
          'OcrPanel': { props: ['scanId'], template: '<div data-test="ocr">{{ scanId }}</div>' },
          'AppEmpty': true,
          'el-button': true,
        },
      },
    })
    expect(wrapper.find('[data-test="image"]').exists()).toBe(false)
    expect(wrapper.findAll('button')).toHaveLength(1)
    await wrapper.get('button').trigger('click')
    expect(wrapper.get('[data-test="image"]').text()).toBe('11')
    expect(wrapper.get('[data-test="ocr"]').text()).toBe('11')
    await wrapper.setProps({ archiveId: 2, samples: [{ id: 21, archiveId: 2, uploadFlag: 1 }] })
    expect(wrapper.find('[data-test="image"]').exists()).toBe(false)
    await wrapper.get('button').trigger('click')
    expect(wrapper.get('[data-test="image"]').text()).toBe('21')
    wrapper.unmount()
  })
})

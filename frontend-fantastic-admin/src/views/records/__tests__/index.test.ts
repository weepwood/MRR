import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/modules/records', () => ({
  getScanList: () => Promise.resolve({
    code: 200,
    data: { list: [{ id: 1, bah: '00789508', brxh: '001', filename: 'scan1.jpg', btype: 1 }], total: 1, page: 1, size: 20 },
  }),
  getScanByCondition: () => Promise.resolve({ code: 200, data: { list: [], total: 0, page: 1, size: 20 } }),
  batchDownloadRecords: () => Promise.resolve(new Blob()),
  getScanByBah: () => Promise.resolve([]),
  getScanByBrxh: () => Promise.resolve([]),
  getScanById: () => Promise.resolve(null),
  createScan: () => Promise.resolve(null),
  updateScan: () => Promise.resolve(null),
  deleteScan: () => Promise.resolve(null),
  findByCondition: () => Promise.resolve([]),
}))

vi.mock('@/store/modules/user', () => ({
  useUserStore: () => ({ token: 'dev-token', isLogin: true, permissions: ['record:read'] }),
}))

vi.mock('@/store/modules/settings', () => ({
  useSettingsStore: () => ({ settings: { home: { title: 'MRR' }, app: { enablePermission: true } } }),
}))

import RecordsPage from '../index.vue'

describe('RecordsPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders the unified MRR page structure', () => {
    const wrapper = mount(RecordsPage, {
      global: {
        stubs: {
          'el-card': { template: '<div class="el-card"><slot /></div>' },
          'el-table': { template: '<div class="el-table"><slot /></div>' },
          'el-button': { template: '<button><slot /></button>' },
          'el-input': true,
          'el-select': true,
          'el-pagination': true,
          'el-tag': true,
          'el-dialog': true,
          'el-icon': true,
          'el-table-column': true,
          'el-descriptions': true,
          'el-descriptions-item': true,
          'el-option': true,
          'FaIcon': true,
          'AppLoading': { template: '<div class="app-loading" />' },
          'AppEmpty': { template: '<div class="app-empty" />' },
          'AppError': { template: '<div class="app-error" />' },
        },
      },
    })

    expect(wrapper.find('.mrr-page-shell').exists()).toBe(true)
    expect(wrapper.find('.mrr-page-header').exists()).toBe(true)
    expect(wrapper.find('.mrr-data-table-panel__filters').exists()).toBe(true)
    expect(wrapper.find('.page-shell').exists()).toBe(false)
  })
})

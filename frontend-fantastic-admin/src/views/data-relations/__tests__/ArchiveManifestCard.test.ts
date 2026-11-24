import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ArchiveManifestCard from '../ArchiveManifestCard.vue'

const api = vi.hoisted(() => ({
  getLatestManifest: vi.fn(),
  getManifestIssues: vi.fn(),
  startManifest: vi.fn(),
  cancelManifest: vi.fn(),
}))
vi.mock('@/api/modules/archive-manifest', () => api)
vi.mock('@/utils/session', () => ({ hasPermission: () => true }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ token: 'synthetic-session' }) }))

function observation(id: number) {
  return { version: id + 100, archiveId: id, status: 'COMPLETED', processed: 3, verified: 2, anomalies: 1, completeDigestAvailable: false, createdAt: '2026-07-21T00:00:00Z', updatedAt: '2026-07-21T00:00:00Z' }
}

function card() {
  return mount(ArchiveManifestCard, {
    props: { archiveId: 1 },
    global: {
      stubs: {
        'MrrSectionCard': { template: '<section><slot name="actions" /><slot /></section>' },
        'AppEmpty': true,
        'AppError': { props: ['message'], template: '<div>{{ message }}</div>' },
        'el-button': { template: '<button><slot /></button>' },
        'el-table': { props: ['data'], template: '<div>{{ data.map(row => row.scanId).join(",") }}</div>' },
        'el-table-column': true,
        'el-alert': { props: ['title'], template: '<div>{{ title }}</div>' },
      },
    },
  })
}

describe('archiveManifestCard', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    api.getManifestIssues.mockResolvedValue({ data: [{ cursor: 1, scanId: 123 }] })
  })

  it('shows persisted observations while disabled and never offers start', async () => {
    api.getLatestManifest.mockResolvedValue({ data: { enabled: false, latest: observation(1) } })
    const wrapper = card()
    await flushPromises()
    expect(wrapper.text()).toContain('版本 #101')
    expect(wrapper.text()).toContain('123')
    expect(wrapper.text()).toContain('后台校验功能尚未启用')
    expect(wrapper.text()).not.toContain('开始校验')
    expect(api.startManifest).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('ignores delayed results from the previous archive', async () => {
    let resolve!: (data: unknown) => void
    api.getLatestManifest.mockImplementationOnce(() => new Promise(r => resolve = r))
    api.getLatestManifest.mockResolvedValueOnce({ data: { enabled: false, latest: observation(2) } })
    const wrapper = card()
    await wrapper.setProps({ archiveId: 2 })
    await flushPromises()
    resolve({ data: { enabled: true, latest: observation(1) } })
    await flushPromises()
    expect(wrapper.text()).toContain('版本 #102')
    expect(wrapper.text()).not.toContain('版本 #101')
    expect(wrapper.text()).not.toContain('开始校验')
    wrapper.unmount()
  })
})

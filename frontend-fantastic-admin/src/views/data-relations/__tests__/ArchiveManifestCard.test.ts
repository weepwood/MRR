import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ArchiveManifestCard from '../ArchiveManifestCard.vue'

const api = vi.hoisted(() => ({
  getLatestManifest: vi.fn(),
  getManifestHistory: vi.fn(),
  getManifestComparison: vi.fn(),
  getManifestVersion: vi.fn(),
  getManifestIssues: vi.fn(),
  getManifestReport: vi.fn(),
  startManifest: vi.fn(),
  cancelManifest: vi.fn(),
}))
vi.mock('@/api/modules/archive-manifest', () => api)
vi.mock('@/api/modules/classification', () => ({ getReviewImage: vi.fn() }))
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
        'el-select': { props: ['modelValue', 'ariaLabel'], emits: ['update:modelValue', 'change'], template: '<select :aria-label="ariaLabel" :value="modelValue" @change="$emit(\'update:modelValue\', Number($event.target.value)); $emit(\'change\')"><slot /></select>' },
        'el-option': { props: ['value', 'label'], template: '<option :value="value">{{ label }}</option>' },
        'el-progress': true,
        'MrrScanContentDialog': true,
        'el-alert': { props: ['title'], template: '<div>{{ title }}</div>' },
      },
    },
  })
}

describe('archiveManifestCard', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    api.getManifestHistory.mockResolvedValue({ data: [] })
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

  it('compares fixed snapshots and rejects late results after an archive change', async () => {
    const latest = { ...observation(1), snapshotCount: 3 }
    api.getLatestManifest.mockResolvedValue({ data: { enabled: false, latest } })
    api.getManifestHistory.mockResolvedValue({ data: [latest, { ...latest, version: 100, snapshotCount: 2 }] })
    api.getManifestComparison.mockResolvedValue({ data: { archiveId: 1, baseVersion: 100, targetVersion: 101, baseCount: 2, targetCount: 3, added: 1, removed: 0, changed: 0, unchanged: 2, digestComparison: 'UNAVAILABLE' } })
    const wrapper = card()
    await flushPromises()
    await wrapper.get('select[aria-label="完整性比较基准版本"]').setValue('100')
    await wrapper.findAll('button').find(button => button.text() === '比较版本')!.trigger('click')
    await flushPromises()
    expect(api.getManifestComparison).toHaveBeenCalledWith(1, 100, 101)
    expect(wrapper.text()).toContain('新增成员')
    expect(wrapper.text()).toContain('完整摘要不可比较')
    let resolve!: (value: unknown) => void
    api.getManifestComparison.mockImplementationOnce(() => new Promise(r => resolve = r))
    await wrapper.findAll('button').find(button => button.text() === '比较版本')!.trigger('click')
    api.getLatestManifest.mockResolvedValue({ data: { enabled: false, latest: { ...observation(2), snapshotCount: 3 } } })
    api.getManifestHistory.mockResolvedValue({ data: [] })
    await wrapper.setProps({ archiveId: 2 })
    await flushPromises()
    resolve({ data: { archiveId: 1, baseVersion: 100, targetVersion: 101 } })
    await flushPromises()
    expect(wrapper.text()).not.toContain('新增成员')
    wrapper.unmount()
  })

  it('loads an explicit fixed-membership report and rejects late results from a previous archive', async () => {
    api.getLatestManifest.mockResolvedValue({ data: { enabled: false, latest: { ...observation(1), snapshotCount: 4 } } })
    api.getManifestReport.mockResolvedValue({ data: { ...observation(1), snapshotCount: 4, segments: [{ number: 1, firstOrdinal: 1, lastOrdinal: 4, planned: 4, processed: 3, verified: 2, anomalies: 1 }], anomalyCounts: { MISSING_OR_UNREADABLE: 1 } } })
    const wrapper = card()
    await flushPromises()
    const button = () => wrapper.findAll('button').find(b => b.text() === '查看完整性报告')!
    await button().trigger('click')
    await flushPromises()
    expect(api.getManifestReport).toHaveBeenCalledWith(1, 101)
    expect(wrapper.text()).toContain('未处理 1')
    expect(wrapper.text()).toContain('MISSING_OR_UNREADABLE 1')
    expect(wrapper.text()).toContain('下载完整性报告 JSON')
    let resolve!: (value: unknown) => void
    api.getManifestReport.mockImplementationOnce(() => new Promise(r => resolve = r))
    await button().trigger('click')
    api.getLatestManifest.mockResolvedValue({ data: { enabled: false, latest: { ...observation(2), snapshotCount: 4 } } })
    await wrapper.setProps({ archiveId: 2 })
    await flushPromises()
    resolve({ data: { ...observation(1), snapshotCount: 4, segments: [], anomalyCounts: {} } })
    await flushPromises()
    expect(wrapper.text()).not.toContain('下载完整性报告 JSON')
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

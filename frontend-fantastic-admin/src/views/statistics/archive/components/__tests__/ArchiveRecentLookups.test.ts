import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import { AUTH_STORAGE_KEYS } from '@/utils/auth-storage'
import ArchiveRecentLookups from '../ArchiveRecentLookups.vue'

const history = vi.hoisted(() => ({
  ARCHIVE_SEARCH_HISTORY_STORAGE_KEY: 'legacy-test-history',
  ARCHIVE_SEARCH_HISTORY_UPDATED_EVENT: 'mrr:archive-search-history-updated',
  loadArchiveSearchHistory: vi.fn(),
  readArchiveSearchHistory: vi.fn(),
}))
vi.mock('@/views/statistics/archive/composables/useArchiveSearchHistory', () => history)

const user = reactive({ token: 'current-test-session', isSessionVerified: true, profile: { id: 1 } })
vi.mock('@/store/modules/user', () => ({ useUserStore: () => user }))

const stubs = { FaIcon: true }

function item(overrides: Record<string, unknown>) {
  return {
    key: `${String(overrides.bah ?? '')}:${String(overrides.sjh ?? '')}:${String(overrides.status ?? 'success')}`,
    bah: '',
    sjh: '',
    imageCount: 0,
    queryCount: 1,
    searchedAt: 1_800_000_000_000,
    status: 'success',
    failureReason: '',
    favorite: false,
    ...overrides,
  }
}

describe('archive recent lookups', () => {
  beforeEach(() => {
    user.token = 'current-test-session'
    user.isSessionVerified = true
    user.profile.id = 1
    history.loadArchiveSearchHistory.mockReset().mockResolvedValue([])
    history.readArchiveSearchHistory.mockReset().mockReturnValue([])
  })

  it('reads the current session without requesting the server by default', async () => {
    history.readArchiveSearchHistory.mockReturnValue([item({ bah: '00000417', imageCount: 86 })])
    const wrapper = mount(ArchiveRecentLookups, { global: { stubs } })
    await flushPromises()

    expect(history.loadArchiveSearchHistory).not.toHaveBeenCalled()
    expect(wrapper.find('[aria-busy="true"]').exists()).toBe(false)
    expect(wrapper.get('.recent-lookups__item').text()).toContain('00000417')
    expect(wrapper.get('.recent-lookups__item').text()).toContain('86 张')
  })

  it('loads from the server once when asked to', async () => {
    history.loadArchiveSearchHistory.mockResolvedValue([item({ bah: '00000001' })])
    const wrapper = mount(ArchiveRecentLookups, { props: { load: true }, global: { stubs } })
    expect(wrapper.find('[aria-busy="true"]').exists()).toBe(true)
    await flushPromises()

    expect(history.loadArchiveSearchHistory).toHaveBeenCalledOnce()
    expect(wrapper.findAll('.recent-lookups__item')).toHaveLength(1)
  })

  it('keeps favorites first, drops failures and respects the limit', async () => {
    history.readArchiveSearchHistory.mockReturnValue([
      item({ bah: '00000001', searchedAt: 1_800_000_003_000 }),
      item({ bah: '00000002', searchedAt: 1_800_000_001_000, favorite: true }),
      item({ bah: '00000003', searchedAt: 1_800_000_004_000, status: 'failure' }),
      item({ bah: '00000004', searchedAt: 1_800_000_002_000 }),
    ])
    const wrapper = mount(ArchiveRecentLookups, { props: { limit: 2 }, global: { stubs } })
    await flushPromises()

    const rows = wrapper.findAll('.recent-lookups__item').map(row => row.text())
    expect(rows).toHaveLength(2)
    expect(rows[0]).toContain('00000002')
    expect(rows[1]).toContain('00000001')
  })

  it('shows the shelf number alone when there is no record number', async () => {
    history.readArchiveSearchHistory.mockReturnValue([item({ sjh: '20001234' })])
    const wrapper = mount(ArchiveRecentLookups, { global: { stubs } })
    await flushPromises()

    const row = wrapper.get('.recent-lookups__item')
    expect(row.findAll('.recent-lookups__code')).toHaveLength(1)
    expect(row.text()).toContain('20001234')
    expect(row.attributes('aria-label')).toContain('上架号 20001234')
  })

  it('emits the chosen lookup and follows session updates', async () => {
    const wrapper = mount(ArchiveRecentLookups, { props: { headingTag: 'h3' }, global: { stubs } })
    await flushPromises()
    expect(wrapper.get('h3').text()).toBe('最近查询')
    expect(wrapper.get('.recent-lookups__empty').text()).toContain('还没有查询记录')

    const next = item({ bah: '00000009', sjh: '20000009' })
    history.readArchiveSearchHistory.mockReturnValue([next])
    window.dispatchEvent(new Event(history.ARCHIVE_SEARCH_HISTORY_UPDATED_EVENT))
    await flushPromises()

    await wrapper.get('.recent-lookups__item').trigger('click')
    expect(wrapper.emitted('select')?.[0]).toEqual([next])
    wrapper.unmount()
  })

  it('clears visible history when a different tab ends the session', async () => {
    history.readArchiveSearchHistory.mockReturnValue([item({ bah: '00000417' })])
    const wrapper = mount(ArchiveRecentLookups, { global: { stubs } })
    await flushPromises()
    expect(wrapper.find('.recent-lookups__item').exists()).toBe(true)

    history.readArchiveSearchHistory.mockReturnValue([])
    window.dispatchEvent(new StorageEvent('storage', { key: AUTH_STORAGE_KEYS.token, newValue: null }))
    await flushPromises()

    expect(wrapper.find('.recent-lookups__item').exists()).toBe(false)
    wrapper.unmount()
  })

  it('discards an old server result after the local session changes', async () => {
    let finish!: (value: ReturnType<typeof item>[]) => void
    history.loadArchiveSearchHistory.mockReturnValueOnce(new Promise((resolve) => { finish = resolve }))
    const wrapper = mount(ArchiveRecentLookups, { props: { load: true }, global: { stubs } })
    await flushPromises()
    expect(history.loadArchiveSearchHistory).toHaveBeenCalledOnce()

    user.token = ''
    user.isSessionVerified = false
    finish([item({ bah: '00000417' })])
    await flushPromises()

    expect(wrapper.find('.recent-lookups__item').exists()).toBe(false)
    wrapper.unmount()
  })
})

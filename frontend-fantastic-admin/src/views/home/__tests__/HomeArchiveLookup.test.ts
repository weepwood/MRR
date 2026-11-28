import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import HomeArchiveHistory from '../HomeArchiveHistory.vue'
import HomeArchiveLookup from '../HomeArchiveLookup.vue'

enableAutoUnmount(afterEach)
const push = vi.fn()
const search = vi.hoisted(() => ({ getArchiveCasesByIdCard: vi.fn() }))
vi.mock('@/api/modules/search', () => search)
const session = reactive({ token: 'test-session', sessionEpoch: 1, isSessionVerified: true, profile: { id: 1 }, permitted: true })

vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
  useRoute: () => ({ path: '/' }),
  RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
}))

vi.mock('@/store/modules/user', () => ({
  useUserStore: () => session,
}))

vi.mock('@/utils/session', () => ({ hasPermission: () => session.permitted }))

const history = vi.hoisted(() => ({
  ARCHIVE_SEARCH_HISTORY_STORAGE_KEY: 'test-history',
  ARCHIVE_SEARCH_HISTORY_UPDATED_EVENT: 'mrr:archive-search-history-updated',
  loadArchiveSearchHistory: vi.fn(),
  readArchiveSearchHistory: vi.fn(),
}))
vi.mock('@/views/statistics/archive/composables/useArchiveSearchHistory', () => history)

const stubs = {
  'FaIcon': true,
  'RouterLink': { props: ['to'], template: '<a :href="to"><slot /></a>' },
  'el-input': {
    props: ['modelValue'],
    emits: ['update:modelValue', 'input'],
    template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value); $emit(\'input\', $event.target.value)">',
  },
  'el-popover': {
    props: ['visible'],
    emits: ['update:visible', 'before-enter'],
    template: '<div><div @click="$emit(\'before-enter\'); $emit(\'update:visible\', true)"><slot name="reference" /></div><slot v-if="visible" /></div>',
  },
  'el-button': { props: ['nativeType'], template: '<button :type="nativeType || \'button\'"><slot /></button>' },
}

function item(overrides: Record<string, unknown>) {
  return {
    key: `${String(overrides.bah)}:${String(overrides.sjh ?? 'none')}`,
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

describe('home archive lookup', () => {
  beforeEach(() => {
    Object.assign(session, { token: 'test-session', sessionEpoch: 1, isSessionVerified: true, profile: { id: 1 }, permitted: true })
    search.getArchiveCasesByIdCard.mockReset()
    push.mockReset().mockResolvedValue(undefined)
    history.loadArchiveSearchHistory.mockReset().mockResolvedValue([])
    history.readArchiveSearchHistory.mockReset().mockReturnValue([])
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('opens the archive with the normalized record number', async () => {
    const wrapper = mount(HomeArchiveLookup, { global: { stubs } })
    await flushPromises()

    await wrapper.findAll('input')[0].setValue(' 1234 ')
    await wrapper.findAll('input')[0].trigger('keyup.enter')

    expect(push).toHaveBeenCalledWith({ path: '/archive/embed', query: { bah: '1234' } })
    expect(wrapper.get('[role="alert"]').text()).toBe('')
  })

  it('requires the shelf number for large record numbers and does not navigate', async () => {
    const wrapper = mount(HomeArchiveLookup, { global: { stubs } })
    await flushPromises()

    await wrapper.findAll('input')[0].setValue('10000001')
    expect(wrapper.text()).toContain('（必填）')
    await wrapper.findAll('input')[0].trigger('keyup.enter')

    expect(push).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toContain('必须输入上架号')
  })

  it('rejects an empty query', async () => {
    const wrapper = mount(HomeArchiveLookup, { global: { stubs } })
    await flushPromises()

    await wrapper.findAll('input')[0].trigger('keyup.enter')

    expect(push).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toBe('请输入病案号或上架号')
  })

  it('requires a verified permitted session before navigation', async () => {
    const wrapper = mount(HomeArchiveLookup, { global: { stubs } })
    const input = wrapper.get('input[aria-label="病案号"]')
    await input.setValue('1234')
    session.isSessionVerified = false
    await input.trigger('keyup.enter')
    expect(push).not.toHaveBeenCalled()
    session.isSessionVerified = true
    session.permitted = false
    await input.trigger('keyup.enter')
    expect(push).not.toHaveBeenCalled()
    expect(search.getArchiveCasesByIdCard).not.toHaveBeenCalled()
  })

  it('uses only the server-issued token in the route and never the raw identifier', async () => {
    search.getArchiveCasesByIdCard.mockResolvedValue({ data: { token: 'synthetic-token', cases: [{ bah: '00000001', sjh: '' }] } })
    const wrapper = mount(HomeArchiveLookup, { global: { stubs } })
    const input = wrapper.get('input[aria-label="身份证号"]')
    // Synthetic invalid birth-date digits, not a real patient identifier.
    await input.setValue('000000000000000')
    await input.trigger('keyup.enter')
    await flushPromises()
    expect(search.getArchiveCasesByIdCard).toHaveBeenCalledWith('000000000000000', expect.any(AbortSignal))
    expect(push).toHaveBeenCalledWith({ path: '/archive/embed', query: { id: 'synthetic-token', bah: '00000001' } })
    expect(input.element.value).toBe('')
  })

  it('cancels identifier lookup and discards late results after session replacement', async () => {
    let resolve!: (value: unknown) => void
    search.getArchiveCasesByIdCard.mockReturnValue(new Promise((value) => { resolve = value }))
    const wrapper = mount(HomeArchiveLookup, { global: { stubs } })
    const input = wrapper.get('input[aria-label="身份证号"]')
    await input.setValue('000000000000000')
    await input.trigger('keyup.enter')
    expect(search.getArchiveCasesByIdCard).toHaveBeenCalledTimes(1)
    const signal = search.getArchiveCasesByIdCard.mock.calls[0][1] as AbortSignal
    session.sessionEpoch++
    expect(signal.aborted).toBe(true)
    resolve({ data: { token: 'stale-token', cases: [{ bah: '00000001' }] } })
    await flushPromises()
    expect(push).not.toHaveBeenCalled()
    expect(input.element.value).toBe('')
  })

  it('lists successful lookups with favorites first and reopens them', async () => {
    history.loadArchiveSearchHistory.mockResolvedValue([
      item({ bah: '00000001', searchedAt: 1_800_000_003_000, imageCount: 12 }),
      item({ bah: '00000002', searchedAt: 1_800_000_001_000, favorite: true }),
      item({ bah: '00000003', searchedAt: 1_800_000_004_000, status: 'failure' }),
      ...Array.from({ length: 6 }, (_, index) => item({ bah: `0000010${index}`, searchedAt: 1_800_000_000_000 - index })),
    ])
    const wrapper = mount(HomeArchiveHistory, { global: { stubs } })
    await wrapper.get('button[aria-label="最近查询"]').trigger('click')
    await flushPromises()

    const rows = wrapper.findAll('.recent-lookups__item')
    expect(rows).toHaveLength(6)
    expect(rows[0].text()).toContain('00000002')
    expect(rows[1].text()).toContain('00000001')
    expect(rows[1].text()).toContain('12 张')
    expect(wrapper.text()).not.toContain('00000003')

    await rows[1].trigger('click')
    expect(push).toHaveBeenCalledWith({ path: '/archive/embed', query: { bah: '00000001' } })
  })

  it('shows an explanation instead of an empty list', async () => {
    const wrapper = mount(HomeArchiveHistory, { global: { stubs } })
    expect(history.loadArchiveSearchHistory).not.toHaveBeenCalled()
    await wrapper.get('button[aria-label="最近查询"]').trigger('click')
    expect(wrapper.find('[aria-busy="true"]').exists()).toBe(true)
    await flushPromises()

    expect(wrapper.find('[aria-busy="true"]').exists()).toBe(false)
    expect(wrapper.get('.recent-lookups__empty').text()).toContain('暂无查询记录')
  })

  it('refreshes when the archive page records a new lookup', async () => {
    const wrapper = mount(HomeArchiveHistory, { global: { stubs } })
    await flushPromises()

    await wrapper.get('button[aria-label="最近查询"]').trigger('click')
    await flushPromises()

    history.readArchiveSearchHistory.mockReturnValue([item({ bah: '00000009' })])
    window.dispatchEvent(new Event(history.ARCHIVE_SEARCH_HISTORY_UPDATED_EVENT))
    await flushPromises()

    expect(wrapper.text()).toContain('00000009')
  })
})

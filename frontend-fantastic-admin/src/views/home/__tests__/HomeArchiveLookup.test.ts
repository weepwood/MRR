import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import HomeArchiveLookup from '../HomeArchiveLookup.vue'

const push = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
  RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
}))

const history = vi.hoisted(() => ({
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
    await wrapper.get('form').trigger('submit')

    expect(push).toHaveBeenCalledWith({ path: '/archive/embed', query: { bah: '1234' } })
    expect(wrapper.get('[role="alert"]').text()).toBe('')
  })

  it('requires the shelf number for large record numbers and does not navigate', async () => {
    const wrapper = mount(HomeArchiveLookup, { global: { stubs } })
    await flushPromises()

    await wrapper.findAll('input')[0].setValue('10000001')
    expect(wrapper.text()).toContain('（必填）')
    await wrapper.get('form').trigger('submit')

    expect(push).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toContain('必须输入上架号')
  })

  it('rejects an empty query', async () => {
    const wrapper = mount(HomeArchiveLookup, { global: { stubs } })
    await flushPromises()

    await wrapper.get('form').trigger('submit')

    expect(push).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toBe('请输入病案号或上架号')
  })

  it('lists successful lookups with favorites first and reopens them', async () => {
    history.loadArchiveSearchHistory.mockResolvedValue([
      item({ bah: '00000001', searchedAt: 1_800_000_003_000, imageCount: 12 }),
      item({ bah: '00000002', searchedAt: 1_800_000_001_000, favorite: true }),
      item({ bah: '00000003', searchedAt: 1_800_000_004_000, status: 'failure' }),
      ...Array.from({ length: 6 }, (_, index) => item({ bah: `0000010${index}`, searchedAt: 1_800_000_000_000 - index })),
    ])
    const wrapper = mount(HomeArchiveLookup, { global: { stubs } })
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
    const wrapper = mount(HomeArchiveLookup, { global: { stubs } })
    expect(wrapper.find('[aria-busy="true"]').exists()).toBe(true)
    await flushPromises()

    expect(wrapper.find('[aria-busy="true"]').exists()).toBe(false)
    expect(wrapper.get('.recent-lookups__empty').text()).toContain('还没有查询记录')
  })

  it('refreshes when the archive page records a new lookup', async () => {
    const wrapper = mount(HomeArchiveLookup, { global: { stubs } })
    await flushPromises()

    history.readArchiveSearchHistory.mockReturnValue([item({ bah: '00000009' })])
    window.dispatchEvent(new Event(history.ARCHIVE_SEARCH_HISTORY_UPDATED_EVENT))
    await flushPromises()

    expect(wrapper.text()).toContain('00000009')
    wrapper.unmount()
  })
})

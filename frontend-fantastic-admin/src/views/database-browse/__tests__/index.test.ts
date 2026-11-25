import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import DatabaseBrowsePage from '../index.vue'

const api = vi.hoisted(() => ({ getBrowseTables: vi.fn(), getBrowseSchema: vi.fn(), getBrowseRows: vi.fn() }))
const session = vi.hoisted(() => ({ store: null as null | { token: string, allowed: boolean } }))
vi.mock('@/store/modules/user', async () => {
  const { reactive } = await import('vue')
  session.store = reactive({ token: 'synthetic', allowed: true })
  return { useUserStore: () => session.store }
})
vi.mock('@/utils/session', () => ({ hasPermission: () => session.store!.allowed }))
vi.mock('@/api/modules/database-browse', () => ({ ...api, describeBrowseError: (error: unknown) => ({ message: String(error), featureDisabled: false }) }))

const summary = (key: string) => ({ tableKey: key, label: key, estimatedRows: 2, estimated: false })
const schema = (key: string) => ({ tableKey: key, columns: [], maxPageSize: 50, requireFilter: false })
const result = (key: string, page = 1) => ({ tableKey: key, page, columns: [], rows: [{ id: key }], hasMore: true, nextCursor: 'next', tookMs: 1 })
const wrappers: ReturnType<typeof mount>[] = []
function open() {
  const wrapper = mount(DatabaseBrowsePage, { global: { stubs: {
    'MrrPageShell': { template: '<main><slot /></main>' },
    'MrrPageHeader': true,
    'MrrSectionCard': { template: '<section><slot /></section>' },
    'FaIcon': true,
    'el-select': true,
    'el-option': true,
    'el-tag': true,
    'el-alert': true,
    'el-input': true,
    'el-button': true,
    'el-table': true,
    'el-table-column': true,
  }, directives: { loading: {} } } })
  wrappers.push(wrapper)
  return (wrapper.vm as any).$.setupState
}
beforeEach(() => {
  vi.resetAllMocks()
  session.store!.allowed = true
  session.store!.token = 'synthetic'
  api.getBrowseTables.mockResolvedValue([summary('scan'), summary('patients')])
  api.getBrowseSchema.mockImplementation(async key => schema(key))
  api.getBrowseRows.mockImplementation(async (key, params) => result(key, params.page))
})
afterEach(() => { while (wrappers.length) { wrappers.pop()?.unmount() } })
describe('数据表浏览请求生命周期', () => {
  it('迟到的表结构不能替换新选择', async () => {
    const state = open()
    await flushPromises()
    let finish!: (value: unknown) => void
    api.getBrowseSchema.mockReturnValueOnce(new Promise((resolve) => { finish = resolve }))
    const old = state.selectTable('scan')
    const signal = api.getBrowseSchema.mock.lastCall?.[1]
    await state.selectTable('patients')
    expect(signal.aborted).toBe(true)
    finish(schema('scan'))
    await old
    expect(state.tableKey).toBe('patients')
    expect(state.schema.tableKey).toBe('patients')
    expect(state.rows).toEqual([{ id: 'patients' }])
  })
  it('分页失败保留上次成功结果，重试成功后才提交游标', async () => {
    const state = open()
    await flushPromises()
    await state.selectTable('scan')
    api.getBrowseRows.mockRejectedValueOnce(new Error('synthetic failure'))
    await state.nextPage()
    expect(state.pageIndex).toBe(1)
    expect(state.cursorStack).toEqual([])
    expect(state.rows).toEqual([{ id: 'scan' }])
    expect(state.errorMessage).toContain('synthetic failure')
    await state.retryAction()
    expect(state.pageIndex).toBe(2)
    expect(state.cursorStack).toEqual(['next'])
    expect(state.errorMessage).toBe('')
  })
  it('权限撤销清除行与过滤条件，拒绝迟到结果', async () => {
    const state = open()
    await flushPromises()
    await state.selectTable('scan')
    let finish!: (value: unknown) => void
    api.getBrowseRows.mockReturnValueOnce(new Promise((resolve) => { finish = resolve }))
    const pending = state.nextPage()
    const signal = api.getBrowseRows.mock.lastCall?.[2]
    session.store!.allowed = false
    expect(signal.aborted).toBe(true)
    finish(result('scan', 2))
    await pending
    expect(state.tables).toEqual([])
    expect(state.rows).toEqual([])
    expect(state.tableKey).toBe('')
    expect(state.loading).toBe(false)
  })
  it.each([{ response: { status: 403 } }, { response: { status: 404, data: { code: 3001 } } }])('错表响应不显示为当前结果，禁止时清空旧数据（%j）', async (denied) => {
    const state = open()
    await flushPromises()
    await state.selectTable('scan')
    api.getBrowseRows.mockResolvedValueOnce(result('patients', 2))
    await state.nextPage()
    expect(state.pageIndex).toBe(1)
    expect(state.errorMessage).toContain('不匹配')
    api.getBrowseRows.mockRejectedValueOnce(denied)
    await state.nextPage()
    expect(state.rows).toEqual([])
    expect(state.tables).toEqual([])
  })
})

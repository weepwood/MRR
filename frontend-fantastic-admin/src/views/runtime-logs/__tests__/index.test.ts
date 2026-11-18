import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import RuntimeLogsPage from '../index.vue'

const api = vi.hoisted(() => ({ getRuntimeLogs: vi.fn(), getRuntimeLogHealth: vi.fn(), getRuntimeLogFiles: vi.fn(), importRuntimeLog: vi.fn() }))
vi.mock('@/api/modules/runtime-logs', () => api)
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }))
vi.mock('@/utils/composables/useAuth', () => ({ default: () => ({ auth: () => true }) }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), error: vi.fn() } }))

const firstRow = { occurred_at: '2026-11-01T00:00:00Z', event_id: '00000000-0000-4000-8000-000000000001', message_summary: 'synthetic first' }
const secondRow = { ...firstRow, event_id: '00000000-0000-4000-8000-000000000002', message_summary: 'synthetic second' }
const names = ['img-api.log.2026-11-01.0.gz', 'img-api.log.2026-11-02.0.gz', 'img-api.log.2026-11-03.0.gz']
const stubs = {
  'AppEmpty': { props: ['description'], template: '<p class="empty">{{ description }}</p>' },
  'AppError': { props: ['message'], template: '<div class="query-error">{{ message }}<button @click="$emit(\'retry\')">重试查询</button></div>' },
  'MrrMetricCard': { props: ['label', 'value'], template: '<p class="metric">{{ label }} {{ value }}</p>' },
  'MrrFilterActionButton': { template: '<button><slot /></button>' },
  'el-alert': { props: ['title'], template: '<p role="alert">{{ title }}</p>' },
  'el-button': { template: '<button><slot /></button>' },
  'el-card': { template: '<section><slot name="header"/><slot/></section>' },
  'el-form': { template: '<form><slot/></form>' },
  'el-form-item': { template: '<div><slot/></div>' },
  'el-input': { props: ['modelValue', 'placeholder'], emits: ['update:modelValue'], template: '<input :placeholder="placeholder" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' },
  'el-select': { name: 'ElSelect', props: ['modelValue'], emits: ['update:modelValue'], template: '<div><slot/></div>' },
  'el-option': { props: ['label'], template: '<span>{{ label }}</span>' },
  'el-date-picker': { name: 'ElDatePicker', props: ['modelValue'], emits: ['update:modelValue'], template: '<div />' },
  'el-table': { props: ['data'], template: '<div class="rows">{{ (data || []).map(row => row.message_summary || row.file_name).join(",") }}</div>' },
  'el-table-column': true,
  'el-tag': true,
  'el-drawer': true,
  'el-collapse': true,
  'el-collapse-item': true,
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: Error) => void
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej })
  return { promise, resolve, reject }
}
const wrappers: ReturnType<typeof mount>[] = []
function openPage() {
  const wrapper = mount(RuntimeLogsPage, { global: { stubs, directives: { loading: {} } } })
  wrappers.push(wrapper)
  return wrapper
}
function button(wrapper: ReturnType<typeof mount>, label: string) {
  return wrapper.findAll('button').find(item => item.text() === label)!
}
function fileSelect(wrapper: ReturnType<typeof mount>) {
  return wrapper.findAllComponents({ name: 'ElSelect' }).at(-1)!
}

beforeEach(() => {
  vi.resetAllMocks()
  api.getRuntimeLogs.mockResolvedValue({ data: { list: [firstRow], hasMore: true } })
  api.getRuntimeLogHealth.mockResolvedValue({ data: { pollAgeSeconds: 2, sources: [] } })
  api.getRuntimeLogFiles.mockResolvedValue({ data: names.map(fileName => ({ fileName, bytes: 120 })) })
  api.importRuntimeLog.mockResolvedValue({ data: null })
})
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))

describe('运行日志失败恢复', () => {
  it('健康读取失败不隐藏成功日志，也不丢弃成功文件清单', async () => {
    api.getRuntimeLogHealth.mockRejectedValue(new Error('synthetic health failure'))
    const wrapper = openPage()
    await flushPromises()
    expect(wrapper.find('.query-error').exists()).toBe(false)
    expect(wrapper.find('.rows').text()).toContain('synthetic first')
    expect(wrapper.text()).toContain(names[0])
    expect(wrapper.text()).toContain('采集状态读取失败')
  })

  it('文件清单失败不丢弃健康状态或覆盖查询错误', async () => {
    api.getRuntimeLogs.mockRejectedValue(new Error('synthetic query failure'))
    api.getRuntimeLogFiles.mockRejectedValue(new Error('synthetic inventory failure'))
    api.getRuntimeLogHealth.mockResolvedValue({ data: { pollAgeSeconds: 11, unknownGap: true } })
    const wrapper = openPage()
    await flushPromises()
    expect(wrapper.find('.query-error').text()).toContain('synthetic query failure')
    expect(wrapper.findAll('.metric').map(item => item.text()).join()).toContain('11 秒前')
    expect(wrapper.text()).toContain('日志存在缺口')
    expect(wrapper.text()).toContain('日志文件清单读取失败')
  })

  it('旧健康响应与旧失败不能覆盖最新刷新', async () => {
    const oldHealth = deferred<{ data: { pollAgeSeconds: number } }>()
    const oldFiles = deferred<{ data: { fileName: string }[] }>()
    api.getRuntimeLogHealth.mockReturnValueOnce(oldHealth.promise)
    api.getRuntimeLogFiles.mockReturnValueOnce(oldFiles.promise)
    const wrapper = openPage()
    await button(wrapper, '刷新状态').trigger('click')
    await flushPromises()
    oldHealth.resolve({ data: { pollAgeSeconds: 999 } })
    oldFiles.reject(new Error('synthetic stale failure'))
    await flushPromises()
    expect(wrapper.findAll('.metric').map(item => item.text()).join()).toContain('2 秒前')
    expect(wrapper.text()).not.toContain('synthetic stale failure')
    expect(wrapper.text()).toContain(names[0])
  })

  it('翻批失败重试同一游标与已提交条件，回首批也不偷用编辑中的筛选', async () => {
    const wrapper = openPage()
    await flushPromises()
    const original = api.getRuntimeLogs.mock.calls[0][0]
    await wrapper.find('input[placeholder="Request ID"]').setValue('synthetic unsubmitted')
    api.getRuntimeLogs.mockRejectedValueOnce(new Error('synthetic next failure'))
    await button(wrapper, '下一批').trigger('click')
    await flushPromises()
    api.getRuntimeLogs.mockResolvedValue({ data: { list: [secondRow], hasMore: false } })
    await button(wrapper, '重试查询').trigger('click')
    await flushPromises()
    expect(api.getRuntimeLogs).toHaveBeenLastCalledWith({ ...original, cursorTime: firstRow.occurred_at, cursorId: firstRow.event_id })
    expect(wrapper.find('.rows').text()).toContain('synthetic second')
    await button(wrapper, '回到首批').trigger('click')
    await flushPromises()
    expect(api.getRuntimeLogs).toHaveBeenLastCalledWith(original)
    await button(wrapper, '刷新日志').trigger('click')
    await flushPromises()
    expect(api.getRuntimeLogs).toHaveBeenLastCalledWith(original)
    expect(wrapper.text()).toContain('筛选已修改')
  })

  it('清空时间后显式查询固定最近24小时范围，翻批不使用漂移的服务端默认窗口', async () => {
    const wrapper = openPage()
    await flushPromises()
    const picker = wrapper.findAllComponents({ name: 'ElDatePicker' })[0]!
    picker.vm.$emit('update:modelValue', null)
    await wrapper.find('.filter-form').trigger('submit')
    await flushPromises()
    const params = api.getRuntimeLogs.mock.calls.at(-1)![0]
    expect(params).toMatchObject({ from: expect.any(String), to: expect.any(String) })
    expect(Date.parse(params.to) - Date.parse(params.from)).toBe(86400000)
    expect(picker.props('modelValue')).toEqual([params.from, params.to])
    await button(wrapper, '下一批').trigger('click')
    await flushPromises()
    expect(api.getRuntimeLogs).toHaveBeenLastCalledWith({ ...params, cursorTime: firstRow.occurred_at, cursorId: firstRow.event_id })
  })

  it('仅移除成功登记项，部分失败后重试不会重复成功项', async () => {
    const wrapper = openPage()
    await flushPromises()
    fileSelect(wrapper).vm.$emit('update:modelValue', [...names])
    api.importRuntimeLog.mockResolvedValueOnce({ data: null }).mockRejectedValueOnce(new Error('synthetic registration failure'))
    await wrapper.find('.import-form').trigger('submit')
    await flushPromises()
    expect(api.importRuntimeLog.mock.calls.map(call => call[0])).toEqual(names.slice(0, 2))
    expect(fileSelect(wrapper).props('modelValue')).toEqual(names.slice(1))
    expect(wrapper.text()).toContain('已登记 1 个文件，剩余 2 个未确认登记')
    await wrapper.find('.import-form').trigger('submit')
    await flushPromises()
    expect(api.importRuntimeLog.mock.calls.map(call => call[0])).toEqual([names[0], names[1], names[1], names[2]])
    expect(fileSelect(wrapper).props('modelValue')).toEqual([])
    expect(wrapper.text()).toContain('已登记 2 个文件')
  })

  it('重复提交不会并行登记同一批文件', async () => {
    const pending = deferred<{ data: null }>()
    api.importRuntimeLog.mockReturnValueOnce(pending.promise)
    const wrapper = openPage()
    await flushPromises()
    fileSelect(wrapper).vm.$emit('update:modelValue', names.slice(0, 2))
    await wrapper.find('.import-form').trigger('submit')
    await wrapper.find('.import-form').trigger('submit')
    expect(api.importRuntimeLog).toHaveBeenCalledTimes(1)
    pending.resolve({ data: null })
    await flushPromises()
    expect(api.importRuntimeLog.mock.calls.map(call => call[0])).toEqual(names.slice(0, 2))
  })

  it('文件清单失败时禁止登记，刷新恢复后可继续', async () => {
    const wrapper = openPage()
    await flushPromises()
    fileSelect(wrapper).vm.$emit('update:modelValue', names.slice(0, 1))
    api.getRuntimeLogFiles.mockRejectedValueOnce(new Error('synthetic inventory unavailable'))
    await button(wrapper, '刷新状态').trigger('click')
    await flushPromises()
    await wrapper.find('.import-form').trigger('submit')
    await flushPromises()
    expect(api.importRuntimeLog).not.toHaveBeenCalled()
    await button(wrapper, '刷新状态').trigger('click')
    await flushPromises()
    await wrapper.find('.import-form').trigger('submit')
    await flushPromises()
    expect(api.importRuntimeLog).toHaveBeenCalledExactlyOnceWith(names[0])
  })

  it('卸载后不继续登记下一文件或发起状态刷新', async () => {
    const pending = deferred<{ data: null }>()
    api.importRuntimeLog.mockReturnValueOnce(pending.promise)
    const wrapper = openPage()
    await flushPromises()
    fileSelect(wrapper).vm.$emit('update:modelValue', names.slice(0, 2))
    await wrapper.find('.import-form').trigger('submit')
    wrapper.unmount()
    pending.resolve({ data: null })
    await flushPromises()
    expect(api.importRuntimeLog).toHaveBeenCalledTimes(1)
    expect(api.getRuntimeLogHealth).toHaveBeenCalledTimes(1)
  })
})

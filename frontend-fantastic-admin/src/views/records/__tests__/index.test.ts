import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import RecordsPage from '../index.vue'

const { getScanCursor } = vi.hoisted(() => ({ getScanCursor: vi.fn() }))
vi.mock('@/api/modules/records', () => ({
  getScanCursor,
  batchDownloadRecords: vi.fn(),
}))

const row = { id: 1, bah: '00789508', brxh: '001', filename: 'scan1.jpg', btype: 1 }
const response = (list = [row], hasMore = false, nextCursorId = 0) => ({ data: { list, hasMore, nextCursorId } })

function mountPage() {
  return mount(RecordsPage, {
    global: {
      directives: { loading: () => {} },
      stubs: {
        'FaIcon': true,
        'el-card': { template: '<div><slot name="header" /><slot /></div>' },
        'el-table': { template: '<div class="test-table"><slot /></div>' },
        'el-button': { emits: ['click'], template: '<button @click="$emit(\'click\', $event)"><slot /></button>' },
        'el-input': { props: ['modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" @keyup.enter="$emit(\'keyup.enter\')">' },
        'el-select': { props: ['modelValue'], template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', Number($event.target.value)); $emit(\'change\')"><slot /></select>' },
        'el-option': { props: ['value', 'label'], template: '<option :value="value">{{ label }}</option>' },
        'el-table-column': true,
        'el-dialog': true,
        'el-icon': true,
        'el-tag': true,
        'el-descriptions': true,
        'el-descriptions-item': true,
        'MrrMetricCard': true,
        'MrrTableActions': true,
        'MrrFilterActionButton': { emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
        'AppLoading': { template: '<div class="app-loading" />' },
        'AppEmpty': { props: ['description', 'actionText'], template: '<div class="app-empty">{{ description }}<button @click="$emit(\'action\')">{{ actionText }}</button></div>' },
        'AppError': { props: ['message'], template: '<div class="app-error">{{ message }}<button @click="$emit(\'retry\')">重试</button></div>' },
      },
    },
  })
}

function button(wrapper: ReturnType<typeof mountPage>, text: string) {
  return wrapper.findAll('button').find(item => item.text().trim() === text)!
}

describe('扫描记录查询', () => {
  beforeEach(() => {
    getScanCursor.mockReset().mockResolvedValue(response([row], true, 42))
  })

  it('显示关联的字段标签，并保留已提交条件和前导零', async () => {
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.text()).toContain('扫描记录')
    expect(wrapper.find('.mrr-page-header').exists()).toBe(true)
    expect(wrapper.get('label[for="records-bah"]').text()).toBe('病案号')
    expect(wrapper.get('label[for="records-brxh"]').text()).toBe('病人序号')
    expect(wrapper.get('label[for="records-sjh"]').text()).toBe('上架号')
    expect(wrapper.get('label[for="records-opener"]').text()).toBe('扫描人员')
    expect(wrapper.get('label[for="records-btype"]').text()).toBe('类型')
    await wrapper.get('#records-bah').setValue('00789508')
    expect(wrapper.text()).toContain('已提交条件：全部记录')
    expect(wrapper.text()).toContain('筛选条件已修改')
    await button(wrapper, '查询').trigger('click')
    await flushPromises()
    expect(getScanCursor).toHaveBeenLastCalledWith(expect.objectContaining({ bah: '00789508' }), 0, 20)
    expect(wrapper.text()).toContain('已提交条件：病案号：00789508')
    expect(wrapper.text()).not.toContain('筛选条件已修改')
  })

  it('翻批和更改每批条数时不提交未应用的条件', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('#records-bah').setValue('00123')
    await button(wrapper, '下一批').trigger('click')
    await flushPromises()
    expect(getScanCursor).toHaveBeenLastCalledWith(expect.objectContaining({ bah: undefined }), 42, 20)
    expect(wrapper.text()).toContain('筛选条件已修改')
    await wrapper.get('.records-page-size-select').setValue('50')
    await flushPromises()
    expect(getScanCursor).toHaveBeenLastCalledWith(expect.objectContaining({ bah: undefined }), 0, 50)
    expect(wrapper.text()).toContain('已提交条件：全部记录')
  })

  it('新查询加载期间不把旧表格显示为新结果，空态可重置', async () => {
    const wrapper = mountPage()
    await flushPromises()
    let resolve!: (value: ReturnType<typeof response>) => void
    getScanCursor.mockReturnValueOnce(new Promise((r) => { resolve = r }))
    await wrapper.get('#records-bah').setValue('00123')
    await button(wrapper, '查询').trigger('click')
    expect(wrapper.find('.test-table').exists()).toBe(false)
    expect(wrapper.find('.app-loading').exists()).toBe(true)
    resolve(response([]))
    await flushPromises()
    expect(wrapper.get('.app-empty').text()).toContain('当前条件未找到扫描记录')
    await button(wrapper, '重置条件').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('已提交条件：全部记录')
    expect(getScanCursor).toHaveBeenLastCalledWith(expect.objectContaining({ bah: undefined }), 0, 20)
  })

  it('较早请求的响应不会覆盖较新的查询结果', async () => {
    const wrapper = mountPage()
    await flushPromises()
    let resolve!: (value: ReturnType<typeof response>) => void
    getScanCursor.mockReturnValueOnce(new Promise((r) => { resolve = r }))
    await wrapper.get('#records-bah').setValue('00123')
    await button(wrapper, '查询').trigger('click')
    await wrapper.get('#records-bah').setValue('00456')
    await wrapper.get('#records-bah').trigger('keyup.enter')
    await flushPromises()
    expect(wrapper.text()).toContain('已提交条件：病案号：00456')
    resolve(response([]))
    await flushPromises()
    expect(wrapper.find('.test-table').exists()).toBe(true)
    expect(wrapper.find('.app-empty').exists()).toBe(false)
  })

  it('请求失败展示错误而不是空结果，并支持重试', async () => {
    getScanCursor.mockRejectedValueOnce(new Error('读取失败'))
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.get('.app-error').text()).toContain('读取失败')
    expect(wrapper.find('.app-empty').exists()).toBe(false)
    await button(wrapper, '重试').trigger('click')
    await flushPromises()
    expect(wrapper.find('.test-table').exists()).toBe(true)
  })
})

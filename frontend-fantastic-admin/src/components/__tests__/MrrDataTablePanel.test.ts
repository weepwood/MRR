import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MrrDataTablePanel from '../MrrDataTablePanel/index.vue'

const stubs = {
  'FaIcon': true,
  'el-tag': { template: '<span class="tag-stub"><slot /></span>' },
  'el-card': { template: '<section><slot name="header" /><slot /></section>' },
}

function mountPanel(props: Record<string, unknown> = {}) {
  return mount(MrrDataTablePanel, {
    props: { title: '扫描记录', ...props },
    slots: { default: '<div class="table-stub" />' },
    global: { stubs },
  })
}

const content = (wrapper: ReturnType<typeof mountPanel>) => wrapper.get('.mrr-data-table-panel__content')

describe('mrrDataTablePanel 刷新反馈', () => {
  it('默认不显示进度条，也不标记忙碌', () => {
    const wrapper = mountPanel()
    expect(wrapper.find('.mrr-data-table-panel__progress').exists()).toBe(false)
    expect(content(wrapper).attributes('aria-busy')).toBeUndefined()
    expect(content(wrapper).classes()).not.toContain('is-refreshing')
  })

  it('loading 时保留结果，显示进度条并标记 aria-busy', async () => {
    const wrapper = mountPanel()
    await wrapper.setProps({ loading: true })
    expect(wrapper.find('.table-stub').exists()).toBe(true)
    expect(wrapper.find('.mrr-data-table-panel__progress').attributes('aria-hidden')).toBe('true')
    expect(content(wrapper).attributes('aria-busy')).toBe('true')
    expect(content(wrapper).classes()).toContain('is-refreshing')

    await wrapper.setProps({ loading: false })
    expect(wrapper.find('.mrr-data-table-panel__progress').exists()).toBe(false)
    expect(content(wrapper).attributes('aria-busy')).toBeUndefined()
  })

  it('resultKey 每次变化轮换结果批次，值不变时不重播', async () => {
    const wrapper = mountPanel({ resultKey: 0 })
    expect(content(wrapper).attributes('data-result-pass')).toBeUndefined()

    await wrapper.setProps({ resultKey: 1 })
    expect(content(wrapper).attributes('data-result-pass')).toBe('a')

    await wrapper.setProps({ resultKey: 2 })
    expect(content(wrapper).attributes('data-result-pass')).toBe('b')

    await wrapper.setProps({ resultKey: 2, loading: true })
    expect(content(wrapper).attributes('data-result-pass')).toBe('b')
  })
})

import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import Button from '../index.vue'

const stubs = { 'el-button': { template: '<button><slot /></button>' }, 'FaIcon': true }

afterEach(() => vi.useRealTimers())

describe('查询反馈时间契约', () => {
  it('request 模式不添加人为忙态下限且阻止重复操作', async () => {
    vi.useFakeTimers()
    const wrapper = mount(Button, { props: { action: 'search', feedback: 'request' }, global: { stubs } })
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('click')).toHaveLength(1)
    await wrapper.setProps({ pending: true, busy: true })
    expect(wrapper.get('button').attributes('aria-busy')).toBe('true')
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('click')).toHaveLength(1)
    await wrapper.setProps({ pending: false, busy: false })
    expect(wrapper.get('button').attributes('aria-busy')).toBe('false')
    expect(vi.getTimerCount()).toBe(0)
    wrapper.unmount()
  })

  it('既有默认反馈策略仍保留原最低动画时间', async () => {
    vi.useFakeTimers()
    const wrapper = mount(Button, { props: { action: 'reset' }, global: { stubs } })
    await wrapper.get('button').trigger('click')
    expect(wrapper.get('button').attributes('aria-busy')).toBe('true')
    await vi.advanceTimersByTimeAsync(319)
    expect(wrapper.get('button').attributes('aria-busy')).toBe('true')
    await vi.advanceTimersByTimeAsync(1)
    expect(wrapper.get('button').attributes('aria-busy')).toBe('false')
    wrapper.unmount()
  })
})

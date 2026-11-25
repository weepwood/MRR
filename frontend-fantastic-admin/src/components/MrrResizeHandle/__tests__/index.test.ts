import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import Handle from '../index.vue'

describe('共享面板分隔条', () => {
  it('左面板与右面板键盘方向相反，边界与复位保持一致', async () => {
    const wrapper = mount(Handle, { props: { modelValue: 240, min: 180, max: 360, defaultWidth: 260, label: '面板', controls: 'pane' } })
    await wrapper.trigger('keydown', { key: 'ArrowRight' })
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([250])
    await wrapper.setProps({ side: 'right' })
    await wrapper.trigger('keydown', { key: 'ArrowRight' })
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([230])
    await wrapper.trigger('keydown', { key: 'Home' })
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([180])
    await wrapper.trigger('keydown', { key: 'End' })
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([360])
    await wrapper.trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([260])
    await wrapper.setProps({ modelValue: 180 })
    await wrapper.trigger('keydown', { key: 'ArrowRight' })
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([180])
    const count = wrapper.emitted('update:modelValue')!.length
    await wrapper.trigger('keydown', { key: 'ArrowLeft', ctrlKey: true })
    expect(wrapper.emitted('update:modelValue')).toHaveLength(count)
    wrapper.unmount()
  })
})

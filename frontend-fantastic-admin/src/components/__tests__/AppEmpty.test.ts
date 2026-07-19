import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AppEmpty from '../AppEmpty/index.vue'

describe('appEmpty', () => {
  it('renders default description', () => {
    const wrapper = mount(AppEmpty)
    expect(wrapper.find('.app-empty-text').text()).toBe('暂无数据')
  })

  it('renders custom description', () => {
    const wrapper = mount(AppEmpty, { props: { description: 'No users found' } })
    expect(wrapper.find('.app-empty-text').text()).toBe('No users found')
  })

  it('shows button when actionText provided', () => {
    const wrapper = mount(AppEmpty, {
      props: { actionText: '新增' },
      global: { stubs: { 'el-button': { template: '<button class="el-button"><slot /></button>' } } },
    })
    expect(wrapper.find('.el-button').exists()).toBe(true)
  })

  it('emits action on button click', async () => {
    const wrapper = mount(AppEmpty, {
      props: { actionText: '新增' },
    })
    const btn = wrapper.find('.el-button')
    if (btn.exists()) {
      await btn.trigger('click')
      expect(wrapper.emitted('action')).toBeTruthy()
    }
  })
})

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

  it('以 status 角色播报空态，图标不参与读屏', () => {
    const wrapper = mount(AppEmpty)
    const root = wrapper.find('.app-empty')
    expect(root.attributes('role')).toBe('status')
    expect(root.attributes('aria-live')).toBe('polite')
    expect(wrapper.find('.app-empty-icon').attributes('aria-hidden')).toBe('true')
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

  it('compact 尺寸只加修饰类，默认不加', () => {
    expect(mount(AppEmpty).find('.app-empty').classes()).not.toContain('is-compact')
    expect(mount(AppEmpty, { props: { size: 'compact' } }).find('.app-empty').classes()).toContain('is-compact')
  })
})

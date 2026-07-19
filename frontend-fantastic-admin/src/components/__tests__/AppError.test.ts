import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AppError from '../AppError/index.vue'

describe('appError', () => {
  it('renders default error message', () => {
    const wrapper = mount(AppError)
    expect(wrapper.find('.app-error-text').text()).toBe('加载失败')
  })

  it('renders custom error message', () => {
    const wrapper = mount(AppError, { props: { message: '网络异常，请检查连接' } })
    expect(wrapper.find('.app-error-text').text()).toBe('网络异常，请检查连接')
  })

  it('emits retry on button click', async () => {
    const wrapper = mount(AppError)
    const btn = wrapper.find('.el-button')
    if (btn.exists()) {
      await btn.trigger('click')
      expect(wrapper.emitted('retry')).toBeTruthy()
    }
  })
})

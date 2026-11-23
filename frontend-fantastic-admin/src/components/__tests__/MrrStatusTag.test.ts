import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MrrStatusTag from '../MrrStatusTag/index.vue'

describe('mrrStatusTag', () => {
  it('运行中类状态自动显示进行中光圈', () => {
    for (const status of ['running', 'processing', '运行中', '处理中']) {
      const wrapper = mount(MrrStatusTag, { props: { status } })
      expect(wrapper.classes()).toContain('mrr-status-tag--info')
      expect(wrapper.classes()).toContain('mrr-status-tag--live')
    }
  })

  it('终态与待处理不显示光圈', () => {
    for (const status of ['success', 'failed', 'pending', 'disabled', 'future_status']) {
      const wrapper = mount(MrrStatusTag, { props: { status } })
      expect(wrapper.classes()).not.toContain('mrr-status-tag--live')
    }
  })

  it('显式 tone 默认不显示光圈，live 可单独打开', () => {
    expect(mount(MrrStatusTag, { props: { tone: 'info', label: '同步中' } }).classes()).not.toContain('mrr-status-tag--live')
    expect(mount(MrrStatusTag, { props: { tone: 'info', label: '同步中', live: true } }).classes()).toContain('mrr-status-tag--live')
  })

  it('live=false 关闭自动识别，状态文字不变', () => {
    const wrapper = mount(MrrStatusTag, { props: { status: 'running', live: false } })
    expect(wrapper.classes()).not.toContain('mrr-status-tag--live')
    expect(wrapper.text()).toBe('running')
  })
})

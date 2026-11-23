import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MrrPageHeader from '../MrrPageHeader/index.vue'

const stubs = { FaIcon: true }

describe('mrrPageHeader', () => {
  it('默认尺寸不追加紧凑类', () => {
    const wrapper = mount(MrrPageHeader, { props: { title: '用户管理' }, global: { stubs } })
    expect(wrapper.get('h1').text()).toBe('用户管理')
    expect(wrapper.classes()).not.toContain('mrr-page-header--compact')
  })

  it('size="compact" 追加紧凑类，标题、说明与插槽结构不变', () => {
    const wrapper = mount(MrrPageHeader, {
      props: { title: '记录管理', description: '按完整编号检索扫描记录', size: 'compact' },
      slots: { meta: '<span class="meta-stub">自动刷新</span>', actions: '<button>导出</button>' },
      global: { stubs },
    })
    expect(wrapper.classes()).toContain('mrr-page-header--compact')
    expect(wrapper.get('h1').text()).toBe('记录管理')
    expect(wrapper.get('.mrr-page-header__description').text()).toBe('按完整编号检索扫描记录')
    expect(wrapper.find('.mrr-page-header__meta .meta-stub').exists()).toBe(true)
    expect(wrapper.find('.mrr-page-header__aside button').exists()).toBe(true)
  })
})

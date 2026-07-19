import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AppLoading from '../AppLoading/index.vue'

describe('appLoading', () => {
  it('renders table skeleton with default 5 rows', () => {
    const wrapper = mount(AppLoading, { props: { type: 'table' } })
    expect(wrapper.findAll('.skeleton-row')).toHaveLength(5)
  })

  it('renders card skeleton with specified cols', () => {
    const wrapper = mount(AppLoading, { props: { type: 'card', cols: 3 } })
    expect(wrapper.findAll('.skeleton-card')).toHaveLength(3)
  })

  it('renders stats skeleton', () => {
    const wrapper = mount(AppLoading, { props: { type: 'stats', cols: 4 } })
    expect(wrapper.findAll('.skeleton-stat')).toHaveLength(4)
  })

  it('respects custom row count', () => {
    const wrapper = mount(AppLoading, { props: { type: 'table', rows: 3 } })
    expect(wrapper.findAll('.skeleton-row')).toHaveLength(3)
  })
})

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import Breadcrumb from '../index.vue'
import BreadcrumbItem from '../item.vue'

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/system/users', component: { template: '<div />' } },
    ],
  })
}

describe('breadcrumb item', () => {
  it('renders navigable levels as real links reachable by keyboard', async () => {
    const router = makeRouter()
    await router.push('/')
    const wrapper = mount(BreadcrumbItem, {
      props: { to: '/system/users' },
      slots: { default: '系统管理' },
      global: { plugins: [router] },
    })

    const link = wrapper.get('a.text')
    expect(link.attributes('href')).toBe('/system/users')
    expect(link.text()).toBe('系统管理')
    expect(wrapper.find('[aria-current="page"]').exists()).toBe(false)

    await link.trigger('click')
    await router.isReady()
    await new Promise(resolve => setTimeout(resolve))
    expect(router.currentRoute.value.path).toBe('/system/users')
  })

  it('marks the current level without making it interactive', async () => {
    const router = makeRouter()
    await router.push('/')
    const wrapper = mount(BreadcrumbItem, {
      props: { current: true },
      slots: { default: '用户管理' },
      global: { plugins: [router] },
    })

    expect(wrapper.find('a').exists()).toBe(false)
    expect(wrapper.get('span.text').attributes('aria-current')).toBe('page')
    expect(wrapper.get('.separator').attributes('aria-hidden')).toBe('true')
  })

  it('wraps the trail in a labelled navigation landmark', () => {
    const wrapper = mount(Breadcrumb, { props: { navigationLabel: '面包屑导航' }, slots: { default: '<span>x</span>' } })

    expect(wrapper.get('nav').attributes('aria-label')).toBe('面包屑导航')
  })

  it('keeps descriptive search result trails free of navigation and current page semantics', () => {
    const wrapper = mount(Breadcrumb, { slots: { default: () => h(BreadcrumbItem, null, '系统管理') } })

    expect(wrapper.find('nav').exists()).toBe(false)
    expect(wrapper.find('[aria-current]').exists()).toBe(false)
  })
})

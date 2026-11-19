import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import MainMenu from '../index.vue'

vi.mock('@/store/modules/runtimeErrorsBadge', () => ({ useRuntimeErrorsBadgeStore: () => ({ recentErrorGroups: 0 }) }))

const menu = [
  { meta: { title: '系统' }, children: [{ path: '/users', meta: { title: '用户管理' } }] },
  { meta: { title: '业务' }, children: [{ path: '/records', meta: { title: '记录管理' } }] },
  { meta: { title: '数据治理' }, children: [{ path: '/relations', meta: { title: '数据关系' } }] },
]

function mountMenu(accordion: boolean, initialMenu = menu) {
  return mount(MainMenu, {
    props: { menu: initialMenu, value: '/users', accordion },
    global: {
      stubs: {
        RouterLink: { props: ['to', 'custom'], template: '<slot :href="\'\'" :navigate="() => {}" />' },
        FaTooltip: { template: '<div><slot /></div>' },
        FaScrollArea: { template: '<div><slot /></div>' },
        FaIcon: true,
      },
    },
  })
}

describe('侧栏无路径目录', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.stubGlobal('CSS', { supports: () => false })
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => { callback(0); return 1 })
  })

  afterEach(() => vi.unstubAllGlobals())

  it('菜单异步加载后各目录拥有独立展开状态', async () => {
    const wrapper = mountMenu(false, [])
    await wrapper.setProps({ menu })
    const group = (title: string) => wrapper.get(`.menu-item-container[title="${title}"]`)
    expect(wrapper.find('[title="用户管理"]').exists()).toBe(true)
    expect(wrapper.find('[title="记录管理"]').exists()).toBe(false)
    await group('业务').trigger('click')
    expect(wrapper.find('[title="用户管理"]').exists()).toBe(true)
    expect(wrapper.find('[title="记录管理"]').exists()).toBe(true)
    expect(wrapper.find('[title="数据关系"]').exists()).toBe(false)
    await group('系统').trigger('click')
    expect(wrapper.find('[title="用户管理"]').exists()).toBe(false)
    expect(wrapper.find('[title="记录管理"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('异步嵌套目录在路由变化后展开正确的路径', async () => {
    const wrapper = mountMenu(false, [])
    await wrapper.setProps({ menu: [
      { meta: { title: '业务' }, children: [
        { meta: { title: '子目录一' }, children: [{ path: '/one', meta: { title: '页面一' } }] },
        { meta: { title: '子目录二' }, children: [{ path: '/two', meta: { title: '页面二' } }] },
      ] },
      { meta: { title: '系统' }, children: [{ path: '/users', meta: { title: '用户管理' } }] },
    ] })
    expect(wrapper.find('[title="用户管理"]').exists()).toBe(true)
    await wrapper.setProps({ value: '/two' })
    expect(wrapper.find('[title="页面二"]').exists()).toBe(true)
    expect(wrapper.find('[title="页面一"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('同步菜单和异步菜单都保留手风琴行为', async () => {
    for (const delayed of [false, true]) {
      const wrapper = mountMenu(true, delayed ? [] : menu)
      if (delayed) { await wrapper.setProps({ menu }) }
      await wrapper.get('.menu-item-container[title="业务"]').trigger('click')
      expect(wrapper.find('[title="用户管理"]').exists()).toBe(false)
      expect(wrapper.find('[title="记录管理"]').exists()).toBe(true)
      expect(wrapper.find('[title="数据关系"]').exists()).toBe(false)
      wrapper.unmount()
    }
  })
})

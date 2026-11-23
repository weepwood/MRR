import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { computed, defineComponent, h, inject } from 'vue'
import { mrrDataTablePanelLoadingKey } from '../MrrDataTablePanel/context'
import MrrDataTablePanel from '../MrrDataTablePanel/index.vue'

const stubs = {
  'FaIcon': true,
  'el-tag': { template: '<span class="tag-stub"><slot /></span>' },
  'el-card': { template: '<section><slot name="header" /><slot /></section>' },
}

function mountPanel(props: Record<string, unknown> = {}, defaultSlot = '<div class="table-stub" />') {
  return mount(MrrDataTablePanel, {
    props: { title: '扫描记录', ...props },
    slots: { default: defaultSlot },
    global: { stubs },
  })
}

const content = (wrapper: ReturnType<typeof mountPanel>) => wrapper.get('.mrr-data-table-panel__content')

const LoadingProbe = defineComponent({
  setup() {
    const loading = inject(mrrDataTablePanelLoadingKey, computed(() => false))
    return { loading }
  },
  template: '<button class="loading-probe" :disabled="loading">行操作</button>',
})

describe('mrrDataTablePanel 刷新反馈', () => {
  it('默认不显示进度条，也不标记忙碌', () => {
    const wrapper = mountPanel()
    expect(wrapper.find('.mrr-data-table-panel__progress').exists()).toBe(false)
    expect(content(wrapper).attributes('aria-busy')).toBeUndefined()
    expect(content(wrapper).classes()).not.toContain('is-refreshing')
  })

  it('loading 时保留结果，显示进度条并标记 aria-busy', async () => {
    const wrapper = mountPanel()
    await wrapper.setProps({ loading: true })
    expect(wrapper.find('.table-stub').exists()).toBe(true)
    expect(wrapper.find('.mrr-data-table-panel__progress').attributes('aria-hidden')).toBe('true')
    expect(content(wrapper).attributes('aria-busy')).toBe('true')
    expect(content(wrapper).classes()).toContain('is-refreshing')

    await wrapper.setProps({ loading: false })
    expect(wrapper.find('.mrr-data-table-panel__progress').exists()).toBe(false)
    expect(content(wrapper).attributes('aria-busy')).toBeUndefined()
  })

  it('loading 时阻止旧行操作被键盘或合成点击触发', () => {
    const wrapper = mountPanel({ loading: true }, '<button class="row-action" @click="" />')
    const clickHandler = vi.fn()
    wrapper.get('.row-action').element.addEventListener('click', clickHandler)

    const keydown = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
    wrapper.get('.row-action').element.dispatchEvent(keydown)
    const click = new MouseEvent('click', { bubbles: true, cancelable: true })
    wrapper.get('.row-action').element.dispatchEvent(click)

    expect(keydown.defaultPrevented).toBe(true)
    expect(click.defaultPrevented).toBe(true)
    expect(clickHandler).not.toHaveBeenCalled()
    expect(content(wrapper).attributes('aria-disabled')).toBe('true')
  })

  it('把 loading 状态提供给表格插槽内的行操作组件', async () => {
    const wrapper = mount(MrrDataTablePanel, {
      props: { title: '扫描记录', loading: false },
      slots: { default: () => h(LoadingProbe) },
      global: { stubs },
    })

    expect(wrapper.get('.loading-probe').attributes('disabled')).toBeUndefined()
    await wrapper.setProps({ loading: true })
    expect(wrapper.get('.loading-probe').attributes('disabled')).toBeDefined()
  })

  it('resultKey 每次变化轮换结果批次，值不变时不重播', async () => {
    const wrapper = mountPanel({ resultKey: 0 })
    expect(content(wrapper).attributes('data-result-pass')).toBeUndefined()

    await wrapper.setProps({ resultKey: 1 })
    expect(content(wrapper).attributes('data-result-pass')).toBe('a')

    await wrapper.setProps({ resultKey: 2 })
    expect(content(wrapper).attributes('data-result-pass')).toBe('b')

    await wrapper.setProps({ resultKey: 2, loading: true })
    expect(content(wrapper).attributes('data-result-pass')).toBe('b')
  })
})

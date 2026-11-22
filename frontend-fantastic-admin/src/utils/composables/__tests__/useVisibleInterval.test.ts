import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, KeepAlive, nextTick, ref } from 'vue'
import useVisibleInterval from '../useVisibleInterval'

function setHidden(hidden: boolean) {
  Object.defineProperty(document, 'hidden', { value: hidden, configurable: true })
  document.dispatchEvent(new Event('visibilitychange'))
}

function createHost(task: () => void, interval = 1000) {
  return defineComponent({
    name: 'IntervalHost',
    setup() {
      useVisibleInterval(task, interval)
      return () => h('div')
    },
  })
}

describe('useVisibleInterval', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    setHidden(false)
  })

  afterEach(() => {
    setHidden(false)
    vi.useRealTimers()
  })

  it('按周期执行，卸载后不再执行', async () => {
    const task = vi.fn()
    const wrapper = mount(createHost(task))
    await vi.advanceTimersByTimeAsync(3000)
    expect(task).toHaveBeenCalledTimes(3)
    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(3000)
    expect(task).toHaveBeenCalledTimes(3)
  })

  it('页面隐藏时跳过，重新可见时补一次', async () => {
    const task = vi.fn()
    mount(createHost(task))
    setHidden(true)
    await vi.advanceTimersByTimeAsync(3000)
    expect(task).not.toHaveBeenCalled()
    setHidden(false)
    expect(task).toHaveBeenCalledTimes(1)
  })

  it('keepAlive 失活时暂停，激活时恢复', async () => {
    const task = vi.fn()
    const Host = createHost(task)
    const Other = defineComponent({ name: 'Other', setup: () => () => h('span') })
    const show = ref(true)
    mount(defineComponent({
      setup: () => () => h(KeepAlive, null, [show.value ? h(Host) : h(Other)]),
    }))
    show.value = false
    await nextTick()
    await vi.advanceTimersByTimeAsync(3000)
    expect(task).not.toHaveBeenCalled()
    show.value = true
    await nextTick()
    expect(task).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1000)
    expect(task).toHaveBeenCalledTimes(2)
  })
})

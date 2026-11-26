import type { GalleryImage } from '../../types'
import { shallowMount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import PreviewPanel from '../PreviewPanel.vue'

vi.mock('element-plus/es/components/image-viewer/style/css', () => ({}))
vi.mock('../PreviewActionBar.vue', () => ({ default: { template: '<div />' } }))
vi.mock('../PreviewViewerToolbar.vue', () => ({ default: { template: '<div />' } }))

afterEach(() => vi.unstubAllGlobals())

describe('连续影像观察器生命周期', () => {
  it('切换列表重新观察当前节点，旧回调与卸载后的回调不得改变选中页', async () => {
    const observers: Array<{ callback: IntersectionObserverCallback, observe: ReturnType<typeof vi.fn>, disconnect: ReturnType<typeof vi.fn> }> = []
    class Observer {
      observe = vi.fn()
      disconnect = vi.fn()
      constructor(callback: IntersectionObserverCallback) {
        observers.push({ callback, observe: this.observe, disconnect: this.disconnect })
      }
    }
    vi.stubGlobal('IntersectionObserver', Observer)
    // jsdom lacks scrollIntoView; only the mounted page nodes need a harmless stand-in.
    const original = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollIntoView')
    Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', { configurable: true, value: vi.fn() })
    const onSelect = vi.fn()
    const wrapper = shallowMount(PreviewPanel, {
      props: { image: { imageUrl: '/a.png' } as GalleryImage, previewList: ['/a.png', '/b.png'], index: 0, total: 2, isSelected: false, displayMode: 'scroll', onSelect },
      global: { directives: { loading: () => {} } },
    })
    try {
      await nextTick()
      await nextTick()
      const first = observers.at(-1)!
      expect(first.observe).toHaveBeenCalledTimes(2)
      const oldTarget = wrapper.findAll('.continuous-page')[1].element
      await wrapper.setProps({ previewList: ['/c.png', '/d.png', '/e.png'], total: 3 })
      await nextTick()
      await nextTick()
      const current = observers.at(-1)!
      expect(current).not.toBe(first)
      expect(first.disconnect).toHaveBeenCalled()
      expect(current.observe).toHaveBeenCalledTimes(3)
      first.callback([{ isIntersecting: true, intersectionRatio: 1, target: oldTarget } as IntersectionObserverEntry], {} as IntersectionObserver)
      expect(onSelect).not.toHaveBeenCalled()
      const target = wrapper.findAll('.continuous-page')[2].element
      current.callback([{ isIntersecting: true, intersectionRatio: 1, target } as IntersectionObserverEntry], {} as IntersectionObserver)
      expect(onSelect.mock.calls).toEqual([[2]])
      wrapper.unmount()
      expect(current.disconnect).toHaveBeenCalled()
      current.callback([{ isIntersecting: true, intersectionRatio: 1, target } as IntersectionObserverEntry], {} as IntersectionObserver)
      expect(onSelect.mock.calls).toEqual([[2]])
    }
    finally {
      if (wrapper.exists()) { wrapper.unmount() }
      if (original) { Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', original) }
      else { Reflect.deleteProperty(HTMLElement.prototype, 'scrollIntoView') }
    }
  })
})

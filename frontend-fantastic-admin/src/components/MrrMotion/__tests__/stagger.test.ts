import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import MrrStagger from '../MrrStagger.vue'
import MrrStaggerItem from '../MrrStaggerItem.vue'

/** jsdom 不实现 Web Animations API，给一个 no-op 以免 motion-v 在挂载时崩溃 */
beforeEach(() => {
  if (!Element.prototype.animate) {
    // @ts-expect-error test polyfill
    Element.prototype.animate = vi.fn(() => ({
      finished: Promise.resolve(),
      cancel: vi.fn(),
      play: vi.fn(),
      pause: vi.fn(),
    }))
  }
})

const originalMatchMedia = window.matchMedia
afterEach(() => {
  window.matchMedia = originalMatchMedia
})

function mockReducedMotion(reduce: boolean) {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: reduce,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })) as unknown as typeof window.matchMedia
}

describe('MrrStagger / MrrStaggerItem (motion-v 局部增强)', () => {
  it('renders slotted children inside the container', () => {
    const wrapper = mount(MrrStagger, {
      slots: { default: '<div class="cell">a</div><div class="cell">b</div>' },
    })
    expect(wrapper.findAll('.cell').length).toBe(2)
  })

  it('renders MrrStaggerItem with custom tag and slot content', () => {
    const wrapper = mount(MrrStaggerItem, {
      props: { as: 'button' },
      slots: { default: '<span class="label">x</span>' },
    })
    expect(wrapper.find('button').exists()).toBe(true)
    expect(wrapper.find('.label').exists()).toBe(true)
  })

  it('keeps static render under prefers-reduced-motion', () => {
    mockReducedMotion(true)
    const wrapper = mount(MrrStagger, {
      slots: { default: '<div class="cell">a</div>' },
    })
    expect(wrapper.find('.cell').exists()).toBe(true)
    expect(() => mount(MrrStaggerItem, { slots: { default: '<span>z</span>' } })).not.toThrow()
  })

  it('mounts without throwing under normal motion preference', () => {
    mockReducedMotion(false)
    expect(() => mount(MrrStagger, {
      slots: { default: '<div class="cell">a</div><div class="cell">b</div>' },
    })).not.toThrow()
  })

  it('forwards fallthrough attributes to the motion root element', () => {
    const wrapper = mount(MrrStaggerItem, {
      props: { as: 'button' },
      attrs: { 'data-test': 'cell-1', class: 'my-cell' },
      slots: { default: '<span>z</span>' },
    })
    const root = wrapper.find('button')
    expect(root.classes()).toContain('my-cell')
    expect(root.attributes('data-test')).toBe('cell-1')
  })
})

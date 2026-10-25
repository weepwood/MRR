import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import MrrMotionCard from '../index.vue'

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

describe('MrrMotionCard (motion-v 试点)', () => {
  it('renders slot content', () => {
    const wrapper = mount(MrrMotionCard, {
      slots: { default: '<span class="content">hello</span>' },
    })
    expect(wrapper.find('.content').exists()).toBe(true)
    expect(wrapper.find('.mrr-motion-card').exists()).toBe(true)
  })

  it('keeps static render under prefers-reduced-motion', () => {
    mockReducedMotion(true)
    const wrapper = mount(MrrMotionCard)
    expect(wrapper.find('.mrr-motion-card').exists()).toBe(true)
  })

  it('mounts without throwing under normal motion preference', () => {
    mockReducedMotion(false)
    expect(() => mount(MrrMotionCard)).not.toThrow()
  })
})

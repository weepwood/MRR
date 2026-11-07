import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'
import { useArchivePrint } from '../useArchivePrint'

const message = vi.hoisted(() => ({ error: vi.fn(), warning: vi.fn() }))
vi.mock('element-plus', () => ({ ElMessage: message }))
vi.mock('@/api/modules/archive-export', () => ({ downloadSelectedImagesPdf: vi.fn(), getSelectedImagesPdfExportPlan: vi.fn() }))
vi.mock('@/utils/composables/useAuth', () => ({ default: () => ({ auth: () => true }) }))
vi.mock('../useArchiveExportJob', () => ({ useArchiveExportJob: () => ({}) }))
const scopes: ReturnType<typeof effectScope>[] = []
function printer(ready: Array<boolean | 'pending'>) {
  const create = document.createElement.bind(document)
  const pages = ready.map((state) => {
    const image = create('img')
    Object.defineProperty(image, 'complete', { configurable: true, value: state !== 'pending' })
    Object.defineProperty(image, 'naturalWidth', { configurable: true, value: state === true ? 100 : 0 })
    return image
  })
  const doc = { open: vi.fn(), write: vi.fn(), close: vi.fn(), images: pages }
  const win = { document: doc, focus: vi.fn(), print: vi.fn(), addEventListener: vi.fn(), removeEventListener: vi.fn() }
  vi.spyOn(document, 'createElement').mockImplementation((tag) => {
    const element = create(tag)
    if (tag === 'iframe') { Object.defineProperty(element, 'contentWindow', { value: win }) }
    return element
  })
  const scope = effectScope()
  scopes.push(scope)
  const task = scope.run(() => useArchivePrint())!
  const images = ready.map((_, index) => ({ id: index + 1, imageUrl: `/synthetic-${index}.jpg` }))
  return { task, scope, win, pages, images, doc }
}
beforeEach(() => { vi.useFakeTimers(); vi.clearAllMocks() })
afterEach(() => {
  scopes.splice(0).forEach(scope => scope.stop())
  document.querySelectorAll('iframe').forEach(frame => frame.remove())
  vi.clearAllTimers(); vi.useRealTimers(); vi.restoreAllMocks()
})

describe('print readiness and cleanup', () => {
  it('cleans a frame if document generation fails before waiting begins', async () => {
    const p = printer([true])
    p.doc.write.mockImplementation(() => { throw new Error('synthetic document failure') })
    await p.task.printSelected(p.images)
    expect(p.win.print).not.toHaveBeenCalled()
    expect(document.querySelector('iframe')).toBeNull()
    expect(vi.getTimerCount()).toBe(0)
  })
  it('cleans immediately on afterprint without leaving a fallback timer', async () => {
    const p = printer([true])
    p.win.print.mockImplementation(() => {
      const listener = p.win.addEventListener.mock.calls[0][1] as () => void
      listener()
    })
    await p.task.printSelected(p.images)
    expect(p.win.print).toHaveBeenCalledOnce()
    expect(document.querySelector('iframe')).toBeNull()
    expect(vi.getTimerCount()).toBe(0)
  })
  it('does not print a cached broken page or silently omit it', async () => {
    const p = printer([true, false])
    await p.task.printSelected(p.images)
    expect(p.win.print).not.toHaveBeenCalled()
    expect(message.error).toHaveBeenCalledOnce()
    expect(document.querySelector('iframe')).toBeNull()
  })
  it('rejects a page load error and releases the waiting frame', async () => {
    const p = printer(['pending'])
    const remove = vi.spyOn(p.pages[0], 'removeEventListener')
    const pending = p.task.printSelected(p.images)
    p.pages[0].dispatchEvent(new Event('error'))
    await pending
    expect(p.win.print).not.toHaveBeenCalled()
    expect(document.querySelector('iframe')).toBeNull()
    expect(vi.getTimerCount()).toBe(0)
    expect(remove).toHaveBeenCalledWith('load', expect.any(Function))
    expect(remove).toHaveBeenCalledWith('error', expect.any(Function))
  })
  it('holds the frame lock until afterprint cleanup, then allows another print', async () => {
    const p = printer([true])
    await p.task.printSelected(p.images)
    await p.task.printSelected(p.images)
    expect(p.win.print).toHaveBeenCalledOnce()
    expect(document.querySelectorAll('iframe')).toHaveLength(1)
    const afterPrint = p.win.addEventListener.mock.calls[0][1] as () => void
    afterPrint()
    await p.task.printSelected(p.images)
    expect(p.win.print).toHaveBeenCalledTimes(2)
    p.scope.stop()
    expect(document.querySelector('iframe')).toBeNull()
    expect(vi.getTimerCount()).toBe(0)
  })
  it('does not print incomplete pages after the readiness deadline', async () => {
    const p = printer(['pending'])
    const pending = p.task.printSelected(p.images)
    await vi.advanceTimersByTimeAsync(5000)
    await pending
    expect(p.win.print).not.toHaveBeenCalled()
    expect(message.error).toHaveBeenCalledOnce()
    expect(document.querySelector('iframe')).toBeNull()
  })
  it('does not print after leaving the page while images are loading', async () => {
    const p = printer(['pending'])
    const pending = p.task.printSelected(p.images)
    p.scope.stop()
    Object.defineProperty(p.pages[0], 'naturalWidth', { value: 100 })
    p.pages[0].dispatchEvent(new Event('load'))
    await vi.advanceTimersByTimeAsync(5000)
    await pending
    expect(p.win.print).not.toHaveBeenCalled()
    expect(document.querySelector('iframe')).toBeNull()
    expect(message.error).not.toHaveBeenCalled()
  })
  it('cleans the frame immediately when native print throws', async () => {
    const p = printer([true])
    p.win.print.mockImplementation(() => { throw new Error('synthetic print failure') })
    await p.task.printSelected(p.images)
    expect(document.querySelector('iframe')).toBeNull()
    expect(p.task.printing.value).toBe(false)
  })
  it('preserves selected order and prints once only after every page succeeds', async () => {
    const p = printer([true, 'pending'])
    const pending = p.task.printSelected(p.images)
    expect(p.win.print).not.toHaveBeenCalled()
    Object.defineProperty(p.pages[1], 'naturalWidth', { value: 100 })
    p.pages[1].dispatchEvent(new Event('load'))
    await pending
    expect(p.win.print).toHaveBeenCalledOnce()
    const html = p.doc.write.mock.calls[0][0] as string
    expect(html.indexOf('/synthetic-0.jpg')).toBeLessThan(html.indexOf('/synthetic-1.jpg'))
    await vi.advanceTimersByTimeAsync(2000)
    expect(document.querySelector('iframe')).toBeNull()
    expect(vi.getTimerCount()).toBe(0)
  })
  it('does not allocate a second frame for overlapping print requests', async () => {
    const p = printer(['pending'])
    const first = p.task.printSelected(p.images)
    const second = p.task.printSelected(p.images)
    expect(document.querySelectorAll('iframe')).toHaveLength(1)
    p.pages[0].dispatchEvent(new Event('error'))
    await vi.advanceTimersByTimeAsync(5000)
    await Promise.all([first, second])
  })
})

import type { GalleryImage } from '../../types'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, ref } from 'vue'
import { setArchiveAccessMode } from '../../access-mode'
import { useArchiveSelectedZip } from '../useArchiveSelectedZip'

const mocks = vi.hoisted(() => ({ start: vi.fn(), discard: vi.fn(), auth: vi.fn(() => true), warning: vi.fn(), error: vi.fn() }))
vi.mock('../useArchiveExportJob', () => ({ useArchiveExportJob: () => ({ start: mocks.start, discard: mocks.discard }) }))
vi.mock('@/utils/composables/useAuth', () => ({ default: () => ({ auth: mocks.auth }) }))
vi.mock('element-plus', () => ({ ElMessage: { warning: mocks.warning, error: mocks.error } }))
const scopes: ReturnType<typeof effectScope>[] = []
function setup(images: GalleryImage[] = [{ id: 1, bah: '123', sjh: '456' }]) {
  const scope = effectScope()
  scopes.push(scope)
  const selection = ref(images)
  const task = scope.run(() => useArchiveSelectedZip(selection))!
  return { selection, task }
}
beforeEach(() => { vi.resetAllMocks(); mocks.auth.mockReturnValue(true); setArchiveAccessMode('internal') })
afterEach(() => { scopes.splice(0).forEach(scope => scope.stop()); setArchiveAccessMode('internal') })

describe('selected ZIP', () => {
  it('always creates a fixed-ID ZIP task, even when all current images were selected', async () => {
    await setup([{ id: 2, bah: '123', sjh: '456' }, { id: 1, bah: '123', sjh: '456' }]).task.start()
    expect(mocks.start).toHaveBeenCalledExactlyOnceWith({ format: 'ZIP', bah: '123', sjh: '456', ids: [2, 1] })
  })
  it.each([[], [{ bah: '123' }], [{ id: -1, bah: '123' }], [{ id: 1, bah: '123' }, { id: 1, bah: '123' }], [{ id: 1, bah: '123' }, { id: 2, bah: '456' }]].map(images => ({ images })))('rejects invalid selection without starting any export', async ({ images }) => {
    await setup(images).task.start()
    expect(mocks.start).not.toHaveBeenCalled()
    expect(mocks.warning).toHaveBeenCalledOnce()
  })
  it('denies missing permission', async () => {
    mocks.auth.mockReturnValue(false)
    await setup().task.start()
    expect(mocks.start).not.toHaveBeenCalled()
  })
  it('cannot turn an external session into an internal export task', async () => {
    setArchiveAccessMode('external-ticket')
    await setup().task.start()
    expect(mocks.start).not.toHaveBeenCalled()
  })
  it('invalidates synchronously when the selection changes, including while create is pending', async () => {
    let resolve!: () => void
    mocks.start.mockReturnValue(new Promise<void>((done) => { resolve = done }))
    const { selection, task } = setup()
    const pending = task.start()
    selection.value = [{ id: 2, bah: '123' }]
    expect(mocks.discard).toHaveBeenCalledOnce()
    resolve()
    await pending
  })
  it('does not fall back to another export when the server rejects creation', async () => {
    mocks.start.mockRejectedValue(new Error('synthetic quota rejection'))
    await setup().task.start()
    expect(mocks.start).toHaveBeenCalledOnce()
    expect(mocks.error).toHaveBeenCalledWith('synthetic quota rejection')
  })
  it('stops the selection watcher on disposal', () => {
    const { selection } = setup()
    scopes.pop()!.stop()
    selection.value = []
    expect(mocks.discard).not.toHaveBeenCalled()
  })
})

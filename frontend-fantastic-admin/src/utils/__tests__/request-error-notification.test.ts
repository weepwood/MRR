import { describe, expect, it, vi } from 'vitest'
import { registerUnhandledRequestError } from '../request-error-notification'

function dispatchUnhandledRejection(reason: unknown) {
  const event = new Event('unhandledrejection') as PromiseRejectionEvent
  Object.defineProperty(event, 'reason', { value: reason })
  window.dispatchEvent(event)
}

describe('request error notification fallback', () => {
  it('does not notify while the request error is handled normally', () => {
    const error = new Error('handled')
    const notify = vi.fn()

    registerUnhandledRequestError(error, notify)

    expect(notify).not.toHaveBeenCalled()
  })

  it('notifies when the registered request error becomes unhandled', () => {
    const error = new Error('unhandled')
    const notify = vi.fn()

    registerUnhandledRequestError(error, notify)
    dispatchUnhandledRejection(error)

    expect(notify).toHaveBeenCalledOnce()
  })

  it('ignores unhandled rejections that were not registered by the request layer', () => {
    const registeredError = new Error('registered')
    const unrelatedError = new Error('unrelated')
    const notify = vi.fn()

    registerUnhandledRequestError(registeredError, notify)
    dispatchUnhandledRejection(unrelatedError)

    expect(notify).not.toHaveBeenCalled()
  })

  it('notifies only once for the same request error', () => {
    const error = new Error('duplicate event')
    const notify = vi.fn()

    registerUnhandledRequestError(error, notify)
    dispatchUnhandledRejection(error)
    dispatchUnhandledRejection(error)

    expect(notify).toHaveBeenCalledOnce()
  })

  it('ignores primitive rejection reasons that cannot be tracked safely', () => {
    const notify = vi.fn()

    registerUnhandledRequestError('request failed', notify)
    dispatchUnhandledRejection('request failed')

    expect(notify).not.toHaveBeenCalled()
  })
})

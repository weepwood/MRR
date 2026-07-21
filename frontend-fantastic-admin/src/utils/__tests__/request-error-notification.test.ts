import { createApp } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import {
  installRequestErrorFallback,
  registerRequestErrorFallback,
} from '../request-error-notification'

function dispatchUnhandledRejection(reason: unknown) {
  const event = new Event('unhandledrejection') as PromiseRejectionEvent
  Object.defineProperty(event, 'reason', { value: reason })
  window.dispatchEvent(event)
}

describe('request error notification fallback', () => {
  it('does not notify while the page handles the request error normally', () => {
    const error = new Error('handled')
    const notify = vi.fn()

    registerRequestErrorFallback(error, notify)

    expect(notify).not.toHaveBeenCalled()
  })

  it('notifies when a registered request error becomes unhandled in the browser', () => {
    const error = new Error('unhandled')
    const notify = vi.fn()

    registerRequestErrorFallback(error, notify)
    dispatchUnhandledRejection(error)

    expect(notify).toHaveBeenCalledOnce()
  })

  it('consumes a registered request error caught by the Vue error handler', () => {
    const app = createApp({})
    const previousErrorHandler = vi.fn()
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    const error = new Error('vue request error')
    const notify = vi.fn()
    app.config.errorHandler = previousErrorHandler

    installRequestErrorFallback(app)
    registerRequestErrorFallback(error, notify)
    app.config.errorHandler?.(error, null, 'component event handler')

    expect(notify).toHaveBeenCalledOnce()
    expect(previousErrorHandler).not.toHaveBeenCalled()
    expect(consoleError).toHaveBeenCalledOnce()
  })

  it('delegates unrelated Vue errors to the existing framework handler', () => {
    const app = createApp({})
    const previousErrorHandler = vi.fn()
    const error = new Error('component error')
    app.config.errorHandler = previousErrorHandler

    installRequestErrorFallback(app)
    app.config.errorHandler?.(error, null, 'render function')

    expect(previousErrorHandler).toHaveBeenCalledWith(error, null, 'render function')
  })

  it('notifies only once for the same request error', () => {
    const error = new Error('duplicate event')
    const notify = vi.fn()

    registerRequestErrorFallback(error, notify)
    dispatchUnhandledRejection(error)
    dispatchUnhandledRejection(error)

    expect(notify).toHaveBeenCalledOnce()
  })

  it('ignores primitive rejection reasons that cannot be tracked safely', () => {
    const notify = vi.fn()

    registerRequestErrorFallback('request failed', notify)
    dispatchUnhandledRejection('request failed')

    expect(notify).not.toHaveBeenCalled()
  })
})

import { afterEach, describe, expect, it, vi } from 'vitest'
import { scheduleUnhandledRequestError } from '../request-error-notification'

afterEach(() => {
  vi.useRealTimers()
  document.body.innerHTML = ''
})

describe('request error notification fallback', () => {
  it('shows the global fallback when the page does not display a message', async () => {
    vi.useFakeTimers()
    const notify = vi.fn()

    scheduleUnhandledRequestError(notify, 80)
    await vi.advanceTimersByTimeAsync(80)

    expect(notify).toHaveBeenCalledOnce()
  })

  it('suppresses the global fallback when the page displays an Element Plus message', async () => {
    vi.useFakeTimers()
    const notify = vi.fn()

    scheduleUnhandledRequestError(notify, 80)
    const message = document.createElement('div')
    message.className = 'el-message el-message--error'
    document.body.appendChild(message)
    await Promise.resolve()
    await vi.advanceTimersByTimeAsync(80)

    expect(notify).not.toHaveBeenCalled()
  })

  it('does not treat an existing message as handling the new request error', async () => {
    vi.useFakeTimers()
    const notify = vi.fn()
    const existingMessage = document.createElement('div')
    existingMessage.className = 'el-message el-message--warning'
    document.body.appendChild(existingMessage)

    scheduleUnhandledRequestError(notify, 80)
    await vi.advanceTimersByTimeAsync(80)

    expect(notify).toHaveBeenCalledOnce()
  })

  it('can cancel a pending fallback', async () => {
    vi.useFakeTimers()
    const notify = vi.fn()

    const cancel = scheduleUnhandledRequestError(notify, 80)
    cancel()
    await vi.advanceTimersByTimeAsync(80)

    expect(notify).not.toHaveBeenCalled()
  })
})

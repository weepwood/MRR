import { getRequestErrorMessage, getRequestErrorMessageAsync } from '../request-error-message'

describe('getRequestErrorMessage', () => {
  it('translates gateway errors to a user-friendly Chinese message', () => {
    expect(getRequestErrorMessage({ response: { status: 502 } })).toBe('服务暂时不可用，请稍后重试')
  })

  it('translates network errors to Chinese', () => {
    expect(getRequestErrorMessage({ message: 'Network Error' })).toBe('网络连接异常，请检查网络后重试')
  })

  it('keeps a Chinese server message', () => {
    expect(getRequestErrorMessage({ response: { data: { message: '病案不存在' } } })).toBe('病案不存在')
  })

  it('keeps the login credential error instead of replacing it with a generic server message', () => {
    expect(getRequestErrorMessage({
      response: {
        status: 400,
        data: { code: 400, message: '用户名或密码错误' },
      },
    })).toBe('用户名或密码错误')
  })
})

describe('getRequestErrorMessageAsync', () => {
  const serverMessage = '统计导出超过单次 100000 条上限，请缩小筛选范围'

  it('reads a JSON error payload received as a Blob', async () => {
    const response = {
      status: 413,
      data: new Blob([JSON.stringify({ code: 413, message: serverMessage })], { type: 'application/json' }),
    }
    expect(await getRequestErrorMessageAsync({ response })).toBe(serverMessage)
  })

  it('falls back to HTTP status if the Blob is not JSON', async () => {
    const response = { status: 413, data: new Blob(['<html>error</html>'], { type: 'text/html' }) }
    expect(await getRequestErrorMessageAsync({ response })).toBe('提交内容过大，请调整后重试')
  })

  it('does not parse malformed JSON or oversized responses', async () => {
    const invalid = { response: { status: 413, data: new Blob(['{'], { type: 'application/json' }) } }
    const oversized = {
      response: { status: 413, data: new Blob([' '.repeat(65 * 1024)], { type: 'application/json' }) },
    }
    expect(await getRequestErrorMessageAsync(invalid)).toBe('提交内容过大，请调整后重试')
    expect(await getRequestErrorMessageAsync(oversized)).toBe('提交内容过大，请调整后重试')
  })

  it('does not change the message of ordinary JSON errors', async () => {
    expect(await getRequestErrorMessageAsync({ response: { status: 409, data: { message: '记录数量发生变化' } } }))
      .toBe('记录数量发生变化')
  })
})

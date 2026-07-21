import { getRequestErrorMessage } from '../request-error-message'

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

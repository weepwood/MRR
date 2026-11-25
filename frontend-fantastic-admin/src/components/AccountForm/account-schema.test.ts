import { describe, expect, it } from 'vitest'
import { z } from 'zod'
import { toAccountSchema } from './account-schema'

const schema = toAccountSchema(z.object({
  account: z.string().trim().min(1, '请输入用户名'),
  password: z.string().min(6, '密码太短'),
  checkPassword: z.string().min(1, '请确认密码'),
  contact: z.string().optional(),
}).refine(v => v.password === v.checkPassword, { path: ['checkPassword'], message: '密码不一致' }))

describe('zod 4 account form adapter', () => {
  it('validates and returns trimmed typed values', async () => {
    expect(await schema.parse({ account: ' synthetic ', password: 'secret1', checkPassword: 'secret1' })).toEqual({
      value: { account: 'synthetic', password: 'secret1', checkPassword: 'secret1' },
      errors: [],
    })
  })
  it('retains invalid initial fields and maps validation errors to their fields', async () => {
    const initial = { account: '', password: '', checkPassword: '' }
    expect(schema.cast!(initial)).toEqual(initial)
    const result = await schema.parse(initial)
    expect(result.value).toBeUndefined()
    expect(result.errors).toEqual(expect.arrayContaining([
      { path: 'account', errors: ['请输入用户名'] },
      { path: 'password', errors: ['密码太短'] },
      { path: 'checkPassword', errors: ['请确认密码'] },
    ]))
  })
  it('preserves cross-field refinement and optional field metadata', async () => {
    expect((await schema.parse({ account: 'synthetic', password: 'secret1', checkPassword: 'secret2' })).errors).toEqual([{ path: 'checkPassword', errors: ['密码不一致'] }])
    expect(schema.describe!('account')).toEqual({ exists: true, required: true })
    expect(schema.describe!('contact')).toEqual({ exists: true, required: false })
  })
})

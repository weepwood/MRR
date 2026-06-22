import { describe, expect, it } from 'vitest'
import { cn, resolveRoutePath } from '../index'

describe('cn', () => {
  it('合并 CSS 类名（含 Tailwind 冲突消除）', () => {
    const result = cn('px-4', 'px-2')
    // twMerge 会消除 px-4（被 px-2 覆盖）
    expect(result).toBe('px-2')
  })

  it('处理条件类名', () => {
    const result = cn('text-sm', false && 'hidden', 'font-bold')
    expect(result).toBe('text-sm font-bold')
  })

  it('空参数返回空串', () => {
    expect(cn()).toBe('')
  })
})

describe('resolveRoutePath', () => {
  it('拼接 basePath 和 routePath', () => {
    expect(resolveRoutePath('/admin', 'users')).toBe('/admin/users')
  })

  it('basePath 为 undefined 时直接返回 routePath', () => {
    expect(resolveRoutePath(undefined, '/home')).toBe('/home')
  })

  it('routePath 为 undefined 时返回 basePath', () => {
    expect(resolveRoutePath('/admin', undefined)).toBe('/admin')
  })

  it('两参数都为空时返回空串', () => {
    expect(resolveRoutePath()).toBe('')
  })
})

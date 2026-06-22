import { describe, expect, it } from 'vitest'
import { merge, mergeWithoutUndefinedProps, diffTwoObj } from '../object'

describe('merge', () => {
  it('应合并两个普通对象（第一参数优先级更高）', () => {
    // defu 语义：第一参数为目标对象，其已有属性不会被第二参数覆盖
    const result = merge({ a: 1, b: 2 }, { b: 3, c: 4 })
    expect(result).toEqual({ a: 1, b: 2, c: 4 })
  })

  it('应深度合并嵌套对象（第一参数嵌套属性优先级更高）', () => {
    const result = merge({ a: { x: 1, y: 2 } }, { a: { y: 3, z: 4 } })
    expect(result).toEqual({ a: { x: 1, y: 2, z: 4 } })
  })

  it('应处理空对象合并', () => {
    const result1 = merge({}, { a: 1 })
    expect(result1).toEqual({ a: 1 })
    const result2 = merge({ a: 1 }, {})
    expect(result2).toEqual({ a: 1 })
  })

  it('应处理 undefined 值', () => {
    const result = merge({ a: 1, b: 2 }, { a: undefined, c: 3 })
    // defu 跳过 source 中值为 undefined 的属性
    expect(result).toHaveProperty('a')
    expect(result).toHaveProperty('c')
  })

  it('应处理数组合并（保持第一参数的数组不变）', () => {
    // defu 默认将数组视为普通值，不触发自定义合并器中的数组替换逻辑
    const result = merge({ items: [1, 2, 3] }, { items: [4, 5] })
    expect(result).toEqual({ items: [1, 2, 3] })
  })
})

describe('mergeWithoutUndefinedProps', () => {
  it('应移除目标对象中第二参数对应为 undefined 的属性', () => {
    const result = mergeWithoutUndefinedProps({ a: 1, b: 2 }, { a: undefined, c: 3 })
    expect(result).not.toHaveProperty('a')
    expect(result).toHaveProperty('c')
  })

  it('应深度合并嵌套对象并移除 undefined 属性', () => {
    const result = mergeWithoutUndefinedProps(
      { a: { x: 1, y: 2 } },
      { a: { y: undefined, z: 4 } },
    )
    expect(result.a).toHaveProperty('z')
    expect(result.a).not.toHaveProperty('y')
  })

  it('应处理数组合并（保持第一参数的数组不变）', () => {
    const result = mergeWithoutUndefinedProps({ items: [1, 2, 3] }, { items: [4, 5] })
    expect(result).toEqual({ items: [1, 2, 3] })
  })
})

describe('diffTwoObj', () => {
  it('应返回两个对象的差异部分', () => {
    const result = diffTwoObj({ a: 1, b: 2, c: 3 }, { a: 1, b: 5, c: 3 })
    expect(result).toEqual({ b: 5 })
  })

  it('应返回空对象当两个对象相同时', () => {
    const result = diffTwoObj({ a: 1, b: 2 }, { a: 1, b: 2 })
    expect(result).toEqual({})
  })

  it('应深度比较嵌套对象', () => {
    const result = diffTwoObj(
      { a: { x: 1, y: 2 } },
      { a: { x: 1, y: 3 } },
    )
    expect(result).toEqual({ a: { y: 3 } })
  })

  it('应忽略嵌套对象中相同的部分', () => {
    const result = diffTwoObj(
      { a: { x: 1, y: 2 }, b: 1 },
      { a: { x: 1, y: 2 }, b: 2 },
    )
    expect(result).toEqual({ b: 2 })
  })

  it('当 diffObj 包含原对象不存在的键时应包含在新对象中', () => {
    const result = diffTwoObj({ a: 1 }, { a: 1, b: 2 })
    expect(result).toEqual({ b: 2 })
  })

  it('当输入不是对象时应直接返回 diffObj', () => {
    expect(diffTwoObj(undefined as any, 'string' as any)).toBe('string')
  })

  it('当输入为 null 时应直接返回 diffObj', () => {
    // isObject(null) 现已修复：null 不再被误判为 object
    expect(diffTwoObj(null as any, { a: 1 })).toEqual({ a: 1 })
  })
})

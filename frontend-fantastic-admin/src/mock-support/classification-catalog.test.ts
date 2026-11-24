import { describe, expect, it } from 'vitest'
import { createDemoClassificationCatalog } from './classification-catalog'

describe('synthetic classification catalog', () => {
  it('keeps published versions immutable and restores an independent draft', () => {
    const catalog = createDemoClassificationCatalog()
    const original = catalog.get('demo-rules-1')
    expect(original.code).toBe(200)
    if (!('data' in original)) {
      throw new Error('Missing catalog')
    }
    expect(catalog.save('demo-rules-1', original.data).code).toBe(409)
    const result = catalog.restore('demo-rules-1')
    expect(result.code).toBe(200)
    if (!('data' in result)) {
      throw new Error('Missing draft')
    }
    const version = result.data
    expect(catalog.save(version, { ...original.data, name: '合成草稿' }).code).toBe(200)
    expect(catalog.publish(version).code).toBe(200)
    expect(catalog.list().data.map(row => row.status)).toEqual(['PUBLISHED', 'ARCHIVED'])
    expect(catalog.test(version, null).code).toBe(409)
    expect(catalog.test(version, '合成影像').code).toBe(200)
  })
})

import { beforeEach, describe, expect, it } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useKeepAliveStore } from '../keepAlive'

describe('keepAlive store', () => {
  beforeEach(() => {
    // 每个测试前创建全新 pinia 实例，避免状态污染
    setActivePinia(createPinia())
  })

  describe('add', () => {
    it('添加单个名称去重', () => {
      const store = useKeepAliveStore()
      store.add('home')
      store.add('home')
      expect(store.list).toEqual(['home'])
    })

    it('添加数组批量去重', () => {
      const store = useKeepAliveStore()
      store.add(['a', 'b', 'a', 'c'])
      expect(store.list).toEqual(['a', 'b', 'c'])
    })

    it('数组中跳过空串', () => {
      const store = useKeepAliveStore()
      store.add(['a', '', 'b'])
      expect(store.list).toEqual(['a', 'b'])
    })
  })

  describe('remove', () => {
    it('移除单个名称', () => {
      const store = useKeepAliveStore()
      store.add(['a', 'b', 'c'])
      store.remove('b')
      expect(store.list).toEqual(['a', 'c'])
    })

    it('移除数组中所有匹配项', () => {
      const store = useKeepAliveStore()
      store.add(['a', 'b', 'c', 'd'])
      store.remove(['a', 'c'])
      expect(store.list).toEqual(['b', 'd'])
    })

    it('移除不存在的名称不影响列表', () => {
      const store = useKeepAliveStore()
      store.add(['a', 'b'])
      store.remove('x')
      expect(store.list).toEqual(['a', 'b'])
    })
  })

  describe('clean', () => {
    it('清空列表', () => {
      const store = useKeepAliveStore()
      store.add(['a', 'b', 'c'])
      store.clean()
      expect(store.list).toEqual([])
    })
  })
})

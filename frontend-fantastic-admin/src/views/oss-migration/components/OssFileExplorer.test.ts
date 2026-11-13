import type { OssBrowserPage } from '@/api/modules/oss'
import { flushPromises, mount, shallowMount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { h, KeepAlive, nextTick, ref } from 'vue'
import { browseOssObjects, getOssBrowserUrl } from '@/api/modules/oss'
import OssFileExplorer from './OssFileExplorer.vue'

vi.mock('@/api/modules/oss', () => ({ browseOssObjects: vi.fn(), getOssBrowserUrl: vi.fn() }))
const { error } = vi.hoisted(() => ({ error: vi.fn() }))
vi.mock('element-plus', () => ({ ElMessage: { error } }))

const root = 'medical-records/'
function directory(prefix = root, token: string | null = 'next') {
  return { data: { configured: true, rootPrefix: root, prefix, entries: [], truncated: !!token, nextContinuationToken: token, maxKeys: 200, loadedDirectories: 0, loadedFiles: 0, loadedBytes: 0 } as OssBrowserPage }
}
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: Error) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const image = (name: string) => ({ name, key: `${root}${name}`, directory: false, size: 10 })
interface Explorer {
  loadDirectory: (prefix: string, token?: string, options?: { rememberFolder?: boolean }) => Promise<void>
  nextPage: () => void
  previousPage: () => void
  goBack: () => void
  previewFile: (entry: ReturnType<typeof image>) => Promise<void>
  currentPrefix: string
  currentContinuationToken?: string
  canPreviousPage: boolean
  canGoBack: boolean
  loading: boolean
  previewVisible: boolean
  previewUrl: string
  previewLoading: boolean
}
const globalOptions = { stubs: Object.fromEntries(['el-card', 'el-icon', 'el-tooltip', 'el-tag', 'el-button', 'el-input', 'el-button-group', 'el-breadcrumb', 'el-breadcrumb-item', 'el-empty', 'el-table', 'el-table-column', 'el-dialog', 'el-image', 'el-alert'].map(name => [name, true])), directives: { loading: () => {} } }
const wrappers: ReturnType<typeof shallowMount>[] = []
async function setup() {
  const wrapper = shallowMount(OssFileExplorer, { global: globalOptions })
  wrappers.push(wrapper)
  await flushPromises()
  return { wrapper, vm: wrapper.vm as unknown as Explorer }
}
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(browseOssObjects).mockResolvedValue(directory() as Awaited<ReturnType<typeof browseOssObjects>>)
})
afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()) })

describe('oSS只读浏览器恢复', () => {
  it('下一页失败不增加游标历史，成功重试后仍能返回第一页', async () => {
    const { vm } = await setup()
    vi.mocked(browseOssObjects).mockRejectedValueOnce(new Error('synthetic failure'))
    vm.nextPage()
    await flushPromises()
    expect(vm.canPreviousPage).toBe(false)
    vm.nextPage()
    await flushPromises()
    vm.previousPage()
    await flushPromises()
    expect(browseOssObjects).toHaveBeenLastCalledWith(expect.objectContaining({ continuationToken: undefined }))
    expect(vm.canPreviousPage).toBe(false)
  })

  it('上一页失败保留可重试的游标', async () => {
    const { vm } = await setup()
    vm.nextPage()
    await flushPromises()
    vi.mocked(browseOssObjects).mockRejectedValueOnce(new Error('synthetic failure'))
    vm.previousPage()
    await flushPromises()
    expect(vm.canPreviousPage).toBe(true)
    expect(vm.currentContinuationToken).toBe('next')
    vm.previousPage()
    await flushPromises()
    expect(vm.canPreviousPage).toBe(false)
  })

  it('返回目录失败不丢历史', async () => {
    const { vm } = await setup()
    vi.mocked(browseOssObjects).mockResolvedValueOnce(directory(`${root}a/`) as Awaited<ReturnType<typeof browseOssObjects>>)
    await vm.loadDirectory(`${root}a/`, undefined, { rememberFolder: true })
    vi.mocked(browseOssObjects).mockRejectedValueOnce(new Error('synthetic failure'))
    vm.goBack()
    await flushPromises()
    expect(vm.currentPrefix).toBe(`${root}a/`)
    expect(vm.canGoBack).toBe(true)
    vm.goBack()
    await flushPromises()
    expect(vm.currentPrefix).toBe(root)
  })

  it('旧目录成功不覆盖新目录，也不提前结束新请求loading', async () => {
    const { vm } = await setup()
    const first = deferred<ReturnType<typeof directory>>()
    const second = deferred<ReturnType<typeof directory>>()
    vi.mocked(browseOssObjects).mockReturnValueOnce(first.promise as ReturnType<typeof browseOssObjects>).mockReturnValueOnce(second.promise as ReturnType<typeof browseOssObjects>)
    const a = vm.loadDirectory(`${root}a/`)
    const b = vm.loadDirectory(`${root}b/`)
    first.resolve(directory(`${root}a/`))
    await a
    expect(vm.loading).toBe(true)
    second.resolve(directory(`${root}b/`))
    await b
    expect(vm.currentPrefix).toBe(`${root}b/`)
  })

  it('旧预览失败不关闭新预览', async () => {
    const { vm } = await setup()
    const first = deferred<{ data: { ossUrl: string } }>()
    vi.mocked(getOssBrowserUrl).mockReturnValueOnce(first.promise as ReturnType<typeof getOssBrowserUrl>).mockResolvedValueOnce({ data: { ossUrl: '/synthetic-b.png' } } as Awaited<ReturnType<typeof getOssBrowserUrl>>)
    const a = vm.previewFile(image('a.png'))
    await vm.previewFile(image('b.png'))
    first.reject(new Error('old synthetic failure'))
    await a
    expect(vm.previewVisible).toBe(true)
    expect(vm.previewUrl).toBe('/synthetic-b.png')
    expect(error).not.toHaveBeenCalled()
  })

  it('关闭预览清理URL且忽略仍未完成的签名响应', async () => {
    const { vm } = await setup()
    const pending = deferred<{ data: { ossUrl: string } }>()
    vi.mocked(getOssBrowserUrl).mockReturnValueOnce(pending.promise as ReturnType<typeof getOssBrowserUrl>)
    const task = vm.previewFile(image('a.png'))
    vm.previewVisible = false
    await nextTick()
    pending.resolve({ data: { ossUrl: '/synthetic-a.png' } })
    await task
    expect(vm.previewUrl).toBe('')
    expect(vm.previewLoading).toBe(false)
  })

  it('卸载后不写回目录或弹出迟到错误', async () => {
    const { vm, wrapper } = await setup()
    const pending = deferred<ReturnType<typeof directory>>()
    vi.mocked(browseOssObjects).mockReturnValueOnce(pending.promise as ReturnType<typeof browseOssObjects>)
    const task = vm.loadDirectory(`${root}a/`)
    wrapper.unmount()
    pending.reject(new Error('late synthetic failure'))
    await task
    expect(error).not.toHaveBeenCalled()
  })

  it('最新目录先完成后，旧成功和旧失败均不能覆盖它', async () => {
    const { vm } = await setup()
    for (const reject of [false, true]) {
      const old = deferred<ReturnType<typeof directory>>()
      vi.mocked(browseOssObjects).mockReturnValueOnce(old.promise as ReturnType<typeof browseOssObjects>).mockResolvedValueOnce(directory(`${root}new/`) as Awaited<ReturnType<typeof browseOssObjects>>)
      const task = vm.loadDirectory(`${root}old/`)
      await vm.loadDirectory(`${root}new/`)
      if (reject) { old.reject(new Error('old failure')) }
      else { old.resolve(directory(`${root}old/`)) }
      await task
      expect(vm.currentPrefix).toBe(`${root}new/`)
      expect(vm.loading).toBe(false)
      expect(error).not.toHaveBeenCalled()
    }
  })

  it('迟到的旧预览成功不替换当前图片', async () => {
    const { vm } = await setup()
    const old = deferred<{ data: { ossUrl: string } }>()
    vi.mocked(getOssBrowserUrl).mockReturnValueOnce(old.promise as ReturnType<typeof getOssBrowserUrl>).mockResolvedValueOnce({ data: { ossUrl: '/synthetic-b.png' } } as Awaited<ReturnType<typeof getOssBrowserUrl>>)
    const task = vm.previewFile(image('a.png'))
    await vm.previewFile(image('b.png'))
    old.resolve({ data: { ossUrl: '/synthetic-a.png' } })
    await task
    expect(vm.previewUrl).toBe('/synthetic-b.png')
  })
  it('重复翻页在loading期间只请求一次', async () => {
    const { vm } = await setup()
    const pending = deferred<ReturnType<typeof directory>>()
    vi.mocked(browseOssObjects).mockReturnValueOnce(pending.promise as ReturnType<typeof browseOssObjects>)
    vm.nextPage()
    vm.nextPage()
    vm.previousPage()
    expect(browseOssObjects).toHaveBeenCalledTimes(2)
    pending.resolve(directory(root, null))
    await flushPromises()
  })

  it('缓存离开使目录/签名响应失效，返回补读原成功游标', async () => {
    const visible = ref(true)
    const parent = mount({ setup: () => () => h(KeepAlive, null, { default: () => visible.value ? h(OssFileExplorer) : h('div') }) }, { global: globalOptions })
    wrappers.push(parent)
    await flushPromises()
    const vm = parent.findComponent(OssFileExplorer).vm as unknown as Explorer
    vm.nextPage()
    await flushPromises()
    expect(vm.currentContinuationToken).toBe('next')
    const pending = deferred<ReturnType<typeof directory>>()
    const signing = deferred<{ data: { ossUrl: string } }>()
    vi.mocked(browseOssObjects).mockReturnValueOnce(pending.promise as ReturnType<typeof browseOssObjects>)
    vi.mocked(getOssBrowserUrl).mockReturnValueOnce(signing.promise as ReturnType<typeof getOssBrowserUrl>)
    const request = vm.loadDirectory(`${root}other/`)
    const preview = vm.previewFile(image('a.png'))
    visible.value = false
    await nextTick()
    pending.resolve(directory(`${root}other/`))
    signing.resolve({ data: { ossUrl: '/late.png' } })
    await Promise.all([request, preview])
    expect(vm.currentPrefix).toBe(root)
    expect(vm.previewUrl).toBe('')
    expect(vm.previewVisible).toBe(false)
    visible.value = true
    await flushPromises()
    expect(browseOssObjects).toHaveBeenLastCalledWith(expect.objectContaining({ prefix: root, continuationToken: 'next' }))
    expect(vm.canPreviousPage).toBe(true)
  })

  it('最新预览失败可重试，卸载后签名失败不再提示', async () => {
    const { vm, wrapper } = await setup()
    vi.mocked(getOssBrowserUrl).mockRejectedValueOnce(new Error('current failure'))
    await vm.previewFile(image('a.png'))
    expect(vm.previewVisible).toBe(false)
    expect(vm.previewLoading).toBe(false)
    expect(vm.previewUrl).toBe('')
    vi.mocked(getOssBrowserUrl).mockResolvedValueOnce({ data: { ossUrl: '/retry.png' } } as Awaited<ReturnType<typeof getOssBrowserUrl>>)
    await vm.previewFile(image('a.png'))
    expect(vm.previewVisible).toBe(true)
    expect(vm.previewUrl).toBe('/retry.png')
    error.mockClear()
    const pending = deferred<{ data: { ossUrl: string } }>()
    vi.mocked(getOssBrowserUrl).mockReturnValueOnce(pending.promise as ReturnType<typeof getOssBrowserUrl>)
    const task = vm.previewFile(image('b.png'))
    wrapper.unmount()
    pending.reject(new Error('late signing failure'))
    await task
    expect(error).not.toHaveBeenCalled()
    expect(vm.previewUrl).toBe('')
  })
})

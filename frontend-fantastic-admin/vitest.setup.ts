/**
 * 让 jsdom 环境下的 localStorage / sessionStorage 在 Node 25+ 上恢复可用。
 *
 * 原因：Node 25 起在全局对象上内置了 Web Storage（localStorage / sessionStorage），
 * 未传 `--localstorage-file` 时它不可用（读写抛错或为空对象）。
 * Vitest 3 把 jsdom 的 window 属性挂到全局时，会跳过「全局上已存在且不在其白名单中」的键，
 * 于是测试里拿到的是 Node 的存储，而不是 jsdom 的。
 *
 * 处理：始终把这两个全局指回当前测试文件的 jsdom 存储。
 * - Node 24 下二者本来就是同一个对象，这里不产生变化；
 * - jsdom 存储按测试文件隔离（每个文件一个新 window），不会像共享的
 *   `--localstorage-file` 那样在文件之间串状态。
 */
interface JsdomHandle {
  window: Window & typeof globalThis
}

const jsdomWindow = (globalThis as { jsdom?: JsdomHandle }).jsdom?.window

if (jsdomWindow) {
  for (const key of ['localStorage', 'sessionStorage'] as const) {
    const storage = jsdomWindow[key]
    // 不读取 globalThis[key] 做比较：在 Node 25+ 上读取内置存储本身就会打印警告。
    // Node 24 下二者是同一个对象，直接覆盖也不改变行为。
    Object.defineProperty(globalThis, key, {
      configurable: true,
      enumerable: true,
      get: () => storage,
    })
  }
}

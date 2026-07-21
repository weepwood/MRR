import type { Router } from 'vue-router'

function isArchivePath(path: string): boolean {
  return path === '/archive' || path.startsWith('/archive/')
}

function normalizeQueryValue(value: unknown): string {
  const candidate = Array.isArray(value) ? value[0] : value
  return String(candidate ?? '').trim()
}

/**
 * 在档案页内部切换病案时保留入口 URL 的 userid。
 * 离开档案页后立即清除，避免后续普通访问沿用上一次用户 ID。
 */
export function installArchiveUserIdRouting(router: Router): void {
  let activeUserId = ''

  router.beforeEach((to, from) => {
    const incomingUserId = normalizeQueryValue(to.query.userid)
    if (incomingUserId) {
      activeUserId = incomingUserId
    }

    if (!isArchivePath(to.path)) {
      if (isArchivePath(from.path)) {
        activeUserId = ''
      }
      return true
    }

    if (!incomingUserId && activeUserId) {
      return {
        path: to.path,
        hash: to.hash,
        query: {
          ...to.query,
          userid: activeUserId,
        },
        replace: true,
      }
    }

    return true
  })
}

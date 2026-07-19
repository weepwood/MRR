const MENU_VISIBILITY_STORAGE_KEY = 'MRR-ADMIN:hidden-menu-paths'
const PROTECTED_MENU_PATHS = new Set(['/settings'])

function normalizeHiddenPaths(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return []
  }

  return [...new Set(value.filter((path): path is string => (
    typeof path === 'string'
    && path.startsWith('/')
    && !PROTECTED_MENU_PATHS.has(path)
  )))]
}

function getInitialHiddenPaths() {
  try {
    return normalizeHiddenPaths(JSON.parse(localStorage.getItem(MENU_VISIBILITY_STORAGE_KEY) ?? '[]'))
  }
  catch {
    return []
  }
}

export const useMenuVisibilityStore = defineStore(
  'menuVisibility',
  () => {
    const hiddenPaths = ref<string[]>(getInitialHiddenPaths())
    const hiddenPathSet = computed(() => new Set(hiddenPaths.value))

    function isProtected(path?: string) {
      return !!path && PROTECTED_MENU_PATHS.has(path)
    }

    function isVisible(path?: string) {
      return !path || isProtected(path) || !hiddenPathSet.value.has(path)
    }

    function setVisible(path: string, visible: boolean) {
      if (!path || isProtected(path)) {
        return
      }

      const nextPaths = new Set(hiddenPaths.value)
      if (visible) {
        nextPaths.delete(path)
      }
      else {
        nextPaths.add(path)
      }
      hiddenPaths.value = [...nextPaths]
    }

    function showAll() {
      hiddenPaths.value = []
    }

    watch(hiddenPaths, (paths) => {
      localStorage.setItem(MENU_VISIBILITY_STORAGE_KEY, JSON.stringify(normalizeHiddenPaths(paths)))
    }, { deep: true })

    return {
      hiddenPaths,
      isProtected,
      isVisible,
      setVisible,
      showAll,
    }
  },
)

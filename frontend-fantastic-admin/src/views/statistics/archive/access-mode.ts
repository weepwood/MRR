import { readonly, ref } from 'vue'

export type ArchiveAccessMode = 'internal' | 'external-ticket' | 'archive-legacy'

const currentArchiveAccessMode = ref<ArchiveAccessMode>('internal')

export const archiveAccessMode = readonly(currentArchiveAccessMode)

export function setArchiveAccessMode(mode: ArchiveAccessMode): void {
  currentArchiveAccessMode.value = mode
  if (typeof document === 'undefined') {
    return
  }
  if (mode === 'internal') {
    delete document.documentElement.dataset.mrrAccessMode
  }
  else {
    document.documentElement.dataset.mrrAccessMode = mode
  }
}

export function resolveArchiveAccessMode(
  explicitMode: 'internal' | 'external-ticket' = 'internal',
  runtimeMode = '',
): ArchiveAccessMode {
  if (explicitMode === 'external-ticket' || runtimeMode === 'external-ticket') {
    return 'external-ticket'
  }
  return runtimeMode === 'archive-legacy' ? 'archive-legacy' : 'internal'
}

export function isExternalArchiveAccessMode(mode: ArchiveAccessMode): boolean {
  return mode !== 'internal'
}

export function shouldShowArchiveSearchCard(mode: ArchiveAccessMode): boolean {
  return !isExternalArchiveAccessMode(mode)
}

export function canRenderArchiveRoute(routeName: unknown, mode: ArchiveAccessMode): boolean {
  return routeName === 'archive' && isExternalArchiveAccessMode(mode)
}

export type ArchiveAccessMode = 'internal' | 'external-ticket' | 'archive-legacy'

export function resolveArchiveAccessMode(runtimeMode = ''): ArchiveAccessMode {
  if (runtimeMode === 'external-ticket') {
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

export const PERMISSION_HIERARCHY: Record<string, string[]> = {
  'record:manage': [
    'record:manage',
    'record:edit',
    'record:read',
    'record:download',
    'record:pdf:export',
  ],
  'record:edit': ['record:edit', 'record:read'],
  'record:download': ['record:download', 'record:read'],
  'record:pdf:export': ['record:pdf:export', 'record:read'],
  'record:read': ['record:read'],
  'role:manage': ['role:manage', 'role:read'],
  'role:read': ['role:read'],
  'system:manage': ['system:manage', 'system:read'],
  'system:read': ['system:read'],
}

export function resolvePermissions(perms: string[]): string[] {
  const resolved = new Set<string>()
  for (const perm of perms) {
    if (PERMISSION_HIERARCHY[perm]) {
      PERMISSION_HIERARCHY[perm].forEach(p => resolved.add(p))
    }
    else {
      resolved.add(perm)
    }
  }
  return Array.from(resolved)
}

export function checkPermission(userPerms: string[], target: string): boolean {
  return resolvePermissions(userPerms).includes(target)
}

export function checkAnyPermission(userPerms: string[], targets: string[]): boolean {
  return targets.some(t => checkPermission(userPerms, t))
}

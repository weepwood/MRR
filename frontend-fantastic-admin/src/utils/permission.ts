export interface PermissionDefinition {
  value: string
  label: string
  shortLabel: string
  category: string
  children: string[]
}

/**
 * 权限管理页面、权限计数和继承计算共用同一目录，避免名称与层级分别维护。
 */
export const PERMISSION_DEFINITIONS: PermissionDefinition[] = [
  {
    value: 'record:manage',
    label: '病案管理（编辑、查看和输出）',
    shortLabel: '病案管理',
    category: '病案管理',
    children: ['record:edit', 'record:download', 'record:pdf:export'],
  },
  { value: 'record:edit', label: '新增、修改和删除病案数据', shortLabel: '病案编辑', category: '病案管理', children: ['record:read'] },
  { value: 'record:read', label: '查看病案及影像', shortLabel: '病案查看', category: '病案管理', children: [] },
  { value: 'record:download', label: '下载病案 ZIP 文件', shortLabel: '病案下载', category: '病案输出', children: ['record:read'] },
  { value: 'record:pdf:export', label: '导出病案 PDF 文件', shortLabel: 'PDF 导出', category: '病案输出', children: ['record:read'] },
  { value: 'search:read', label: '使用病案与患者搜索', shortLabel: '病案搜索', category: '病案检索', children: [] },
  { value: 'statistics:read', label: '查看统计与分析页面', shortLabel: '统计分析', category: '统计分析', children: [] },
  { value: 'user:manage', label: '创建、审核和维护用户', shortLabel: '用户管理', category: '账号权限', children: [] },
  { value: 'role:manage', label: '修改角色及角色权限', shortLabel: '角色管理', category: '账号权限', children: ['role:read'] },
  { value: 'role:read', label: '查看角色与权限配置', shortLabel: '角色查看', category: '账号权限', children: [] },
  { value: 'log:read', label: '查看日志与审计记录', shortLabel: '日志审计', category: '系统运维', children: [] },
  {
    value: 'system:error:manage',
    label: '确认、解决和重新打开运行错误',
    shortLabel: '错误管理',
    category: '系统运维',
    children: ['system:error:read'],
  },
  { value: 'system:error:read', label: '查看脱敏运行错误与堆栈', shortLabel: '错误查看', category: '系统运维', children: [] },
  {
    value: 'system:manage',
    label: '修改系统设置并执行维护任务',
    shortLabel: '系统管理',
    category: '系统运维',
    children: ['system:read', 'system:error:manage'],
  },
  { value: 'system:read', label: '查看系统设置、状态和监控', shortLabel: '系统查看', category: '系统运维', children: [] },
  { value: 'test:read', label: '访问测试中心', shortLabel: '测试中心', category: '系统运维', children: [] },
]

const definitionMap = new Map(PERMISSION_DEFINITIONS.map(item => [item.value, item]))

function collectPermissions(permission: string, collected = new Set<string>()): Set<string> {
  if (collected.has(permission)) {
    return collected
  }
  collected.add(permission)
  for (const child of definitionMap.get(permission)?.children ?? []) {
    collectPermissions(child, collected)
  }
  return collected
}

export const PERMISSION_HIERARCHY: Record<string, string[]> = Object.fromEntries(
  PERMISSION_DEFINITIONS.map(definition => [
    definition.value,
    [...collectPermissions(definition.value)],
  ]),
)

export function resolvePermissions(permissions: string[]): string[] {
  const resolved = new Set<string>()
  for (const permission of permissions) {
    for (const inherited of PERMISSION_HIERARCHY[permission] ?? [permission]) {
      resolved.add(inherited)
    }
  }
  return [...resolved]
}

export function checkPermission(userPermissions: string[], target: string): boolean {
  return resolvePermissions(userPermissions).includes(target)
}

export function checkAnyPermission(userPermissions: string[], targets: string[]): boolean {
  return targets.some(target => checkPermission(userPermissions, target))
}

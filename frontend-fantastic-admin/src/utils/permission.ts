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
  { value: 'ocr:read', label: '查看 OCR 状态与任务', shortLabel: 'OCR 状态', category: '文字识别', children: [] },
  { value: 'ocr:text-read', label: '查看完整 OCR 文字', shortLabel: 'OCR 正文', category: '文字识别', children: [] },
  { value: 'ocr:run', label: '运行和取消单病案 OCR', shortLabel: '运行 OCR', category: '文字识别', children: [] },
  { value: 'ocr:batch-run', label: '创建和管理有限历史 OCR 批次', shortLabel: '历史 OCR', category: '文字识别', children: [] },
  { value: 'ocr:config', label: '配置 OCR 词库与分类规则', shortLabel: 'OCR 配置', category: '文字识别', children: [] },
  { value: 'classification:read', label: '查看分类建议与依据', shortLabel: '分类查看', category: '影像分类', children: [] },
  { value: 'classification:review', label: '人工审核分类建议', shortLabel: '分类审核', category: '影像分类', children: [] },
  { value: 'classification:batch-review', label: '批量确认分类建议', shortLabel: '批量审核', category: '影像分类', children: [] },
  { value: 'classification:override', label: '人工覆盖建议与回退自动分类', shortLabel: '分类覆盖', category: '影像分类', children: [] },
  { value: 'classification:auto-apply', label: '授权已验收类别自动归类', shortLabel: '自动分类授权', category: '影像分类', children: [] },
  { value: 'search:read', label: '使用病案与患者搜索', shortLabel: '病案搜索', category: '病案检索', children: [] },
  { value: 'statistics:read', label: '查看统计与分析页面', shortLabel: '统计分析', category: '统计分析', children: [] },
  { value: 'user:manage', label: '创建、审核和维护用户', shortLabel: '用户管理', category: '账号权限', children: [] },
  { value: 'role:manage', label: '修改角色及角色权限', shortLabel: '角色管理', category: '账号权限', children: ['role:read'] },
  { value: 'role:read', label: '查看角色与权限配置', shortLabel: '角色查看', category: '账号权限', children: [] },
  { value: 'system:log:import', label: '登记固定目录历史日志补录', shortLabel: '日志补录', category: '系统运维', children: [] },
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

/**
 * 基于会话凭据判断是否具备目标权限，不依赖任何 store。
 *
 * <p>ADMIN 角色视为拥有全部权限，与后端
 * {@code AuthorizationInterceptor} 的 {@code session.isAdmin()} 放行保持一致。
 * 该函数供无法引入 user store 的场景使用（例如 settings store 需要判断是否有
 * 权限推送全站外观配置，引入 user store 会形成循环依赖）。</p>
 */
export function checkSessionPermission(
  roleCode: string | null | undefined,
  permissions: readonly string[] | null | undefined,
  target: string,
): boolean {
  if (String(roleCode ?? '').trim().toUpperCase() === 'ADMIN') {
    return true
  }
  return checkPermission([...(permissions ?? [])], target)
}

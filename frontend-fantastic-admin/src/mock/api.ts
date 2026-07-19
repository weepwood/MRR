import { defineFakeRoute } from 'vite-plugin-fake-server/client'
import { MEDICAL_RECORD_TYPE_CODES, MEDICAL_RECORD_TYPES } from '@/constants/medical-record-types'

type QueryValue = string | string[] | undefined

interface MockScanRecord {
  id: number
  brxh: string
  bah: string
  sjh: string
  filename: string
  btype: number
  pages: number
  openerNo: string
  uploadDate: string
  uploadFlag: number
  folder: string
  ossUrl?: string
  fileSize: number
  checksumMd5: string
  migrationStatus: string
  migratedAt?: string
}

interface MockUser {
  id: number
  username: string
  displayName: string
  roleCode: string
  roleName: string
  permissions: string[]
  status: string
  lastLoginAt: string
}

const typeNames: Record<number, string> = Object.fromEntries(
  MEDICAL_RECORD_TYPES.map(item => [item.value, item.label] as const),
)

const departments = ['心内科', '神经内科', '普外科', '骨科', '呼吸科', '消化内科']
const operators = ['scanner-01', 'scanner-02', 'scanner-03', 'scanner-04']
const mockTimestamp = '2026-07-13T08:00:00.000Z'

const scans: MockScanRecord[] = Array.from({ length: 64 }, (_, index) => {
  const id = index + 1
  const btypeValues = MEDICAL_RECORD_TYPE_CODES
  const btype = btypeValues[index % btypeValues.length]
  const day = String((index % 28) + 1).padStart(2, '0')
  const month = String(6 + Math.floor(index / 28)).padStart(2, '0')
  const migrated = index % 4 !== 0

  return {
    id,
    brxh: String(320000 + id),
    bah: String(26070000 + Math.floor(index / 2)).padStart(8, '0'),
    sjh: String(90000000 + id),
    filename: `${String(26070000 + Math.floor(index / 2)).padStart(8, '0')}_${btype}_${id}.jpg`,
    btype,
    pages: 8 + (index * 7) % 83,
    openerNo: operators[index % operators.length],
    uploadDate: `2026-${month}-${day} ${String(8 + index % 10).padStart(2, '0')}:30:00`,
    uploadFlag: migrated ? 1 : 0,
    folder: `/mock/archive/2026/${month}/${String(26070000 + Math.floor(index / 2)).padStart(8, '0')}`,
    ossUrl: migrated ? `https://example.invalid/mock/${id}.jpg` : undefined,
    fileSize: 512_000 + index * 31_337,
    checksumMd5: `mock-md5-${String(id).padStart(4, '0')}`,
    migrationStatus: migrated ? (index % 5 === 0 ? 'verified' : 'migrated') : 'not_migrated',
    migratedAt: migrated ? `2026-${month}-${day} 12:00:00` : undefined,
  }
})

const patients = Array.from({ length: 32 }, (_, index) => ({
  id: index + 1,
  idCard: `330106199${index % 10}${String(101 + index).padStart(4, '0')}12${String(index % 10).padStart(2, '0')}`,
  bah: String(26070000 + index).padStart(8, '0'),
  name: ['张敏', '李华', '王芳', '陈杰', '赵宁', '刘洋'][index % 6],
  admissiontime: `2026-${String(5 + index % 3).padStart(2, '0')}-${String(index % 28 + 1).padStart(2, '0')} 09:00:00`,
  department: departments[index % departments.length],
}))

let users: MockUser[] = [
  {
    id: 1,
    username: 'admin',
    displayName: '系统管理员',
    roleCode: 'ADMIN',
    roleName: '管理员',
    permissions: ['*'],
    status: 'ACTIVE',
    lastLoginAt: mockTimestamp,
  },
  {
    id: 2,
    username: 'auditor',
    displayName: '审计员',
    roleCode: 'AUDITOR',
    roleName: '审计员',
    permissions: ['audit:read', 'logs:read', 'statistics:read'],
    status: 'ACTIVE',
    lastLoginAt: '2026-07-12T08:30:00.000Z',
  },
  {
    id: 3,
    username: 'operator',
    displayName: '扫描操作员',
    roleCode: 'OPERATOR',
    roleName: '操作员',
    permissions: ['scan:read', 'scan:write', 'statistics:read'],
    status: 'ACTIVE',
    lastLoginAt: '2026-07-11T06:20:00.000Z',
  },
]

let roles = [
  {
    code: 'ADMIN',
    name: '管理员',
    description: '拥有全部系统权限',
    permissions: '*',
    sortOrder: 1,
  },
  {
    code: 'AUDITOR',
    name: '审计员',
    description: '查看日志、审计与统计数据',
    permissions: 'audit:read,logs:read,statistics:read',
    sortOrder: 2,
  },
  {
    code: 'OPERATOR',
    name: '操作员',
    description: '维护扫描记录与查看统计',
    permissions: 'scan:read,scan:write,statistics:read',
    sortOrder: 3,
  },
]

let settings: Record<string, string> = {
  'app.title': 'MRR-ADMIN Mock',
  'scan.defaultPageSize': '20',
  'audit.retentionDays': '180',
  'oss.enabled': 'true',
  'oss.bucket': 'mrr-mock',
}

const logs = Array.from({ length: 72 }, (_, index) => {
  const actions = ['LIST', 'VIEW_IMAGE', 'VIEW_OSS_IMAGE', 'DOWNLOAD']
  const action = actions[index % actions.length]
  const status = index % 17 === 0 ? 404 : index % 29 === 0 ? 500 : 200
  const target = scans[index % scans.length].bah

  return {
    id: index + 1,
    username: users[index % users.length].username,
    clientIp: `10.10.${Math.floor(index / 20)}.${20 + index % 20}`,
    requestUri: action === 'DOWNLOAD'
      ? `/api/v1/img/download/${target}`
      : action === 'VIEW_IMAGE'
        ? `/api/v1/img/image/${target}/${index + 1}`
        : action === 'VIEW_OSS_IMAGE'
          ? `/api/v1/img/oss-image/${index + 1}`
          : `/api/v1/img/${target}`,
    method: 'GET',
    userAgent: 'Mozilla/5.0 Mock Browser',
    accessTime: `2026-07-${String(13 - Math.floor(index / 12)).padStart(2, '0')} ${String(8 + index % 10).padStart(2, '0')}:${String(index % 60).padStart(2, '0')}:00`,
    queryString: '',
    requestBody: '',
    responseStatus: status,
    executeTime: 35 + index * 3,
    referer: '/mock',
    auditAction: action,
    auditTarget: target,
    auditDescription: {
      LIST: '查询病案图片列表',
      VIEW_IMAGE: '查看本地病案图片',
      VIEW_OSS_IMAGE: '查看 OSS 病案图片',
      DOWNLOAD: '下载病案图片压缩包',
    }[action],
  }
})

const migrationLogs = scans.slice(0, 36).map((item, index) => ({
  id: index + 1,
  scanId: item.id,
  localPath: `${item.folder}/${item.filename}`,
  ossUrl: item.ossUrl,
  migrationStatus: item.migrationStatus === 'not_migrated' ? 'pending' : 'success',
  errorMessage: '',
  fileSize: item.fileSize,
  checksumMd5: item.checksumMd5,
  migratedAt: item.migratedAt,
  verifiedAt: item.migrationStatus === 'verified' ? item.migratedAt : undefined,
  createdAt: item.uploadDate,
  updatedAt: item.migratedAt ?? item.uploadDate,
}))

function firstQuery(value: QueryValue) {
  return Array.isArray(value) ? value[0] : value
}

function toNumber(value: QueryValue, fallback: number) {
  const parsed = Number(firstQuery(value))
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
}

function normalize(value: unknown) {
  return String(value ?? '').trim().toLowerCase()
}

function ok<T>(data: T, message = '操作成功') {
  return {
    code: 200,
    message,
    data,
    timestamp: new Date().toISOString(),
  }
}

function paginate<T>(items: T[], pageValue: QueryValue, sizeValue: QueryValue) {
  const page = toNumber(pageValue, 1)
  const size = toNumber(sizeValue, 20)
  const start = (page - 1) * size

  return {
    list: items.slice(start, start + size),
    total: items.length,
    page,
    size,
    totalPages: Math.ceil(items.length / size),
  }
}

function filterScans(source: MockScanRecord[], filters: Record<string, unknown>) {
  return source.filter((item) => {
    const keyword = normalize(filters.keyword)
    const bah = normalize(filters.bah)
    const brxh = normalize(filters.brxh)
    const sjh = normalize(filters.sjh)
    const openerNo = normalize(filters.openerNo)
    const btype = Number(filters.btype || 0)
    const type = normalize(filters.type)
    const startDate = normalize(filters.startDate)
    const endDate = normalize(filters.endDate)
    const date = item.uploadDate.slice(0, 10)

    if (keyword && ![
      item.bah,
      item.brxh,
      item.sjh,
      item.openerNo,
      typeNames[item.btype],
    ].some(value => normalize(value).includes(keyword))) {
      return false
    }
    if (bah && !normalize(item.bah).includes(bah)) { return false }
    if (brxh && !normalize(item.brxh).includes(brxh)) { return false }
    if (sjh && !normalize(item.sjh).includes(sjh)) { return false }
    if (openerNo && !normalize(item.openerNo).includes(openerNo)) { return false }
    if (btype && item.btype !== btype) { return false }
    if (type && !normalize(typeNames[item.btype]).includes(type) && String(item.btype) !== type) { return false }
    if (startDate && date < startDate) { return false }
    if (endDate && date > endDate) { return false }
    return true
  })
}

function statisticsRows() {
  return scans.map((item, index) => ({
    bah: item.bah,
    cid: `CID-${String(index + 1).padStart(5, '0')}`,
    openerNo: item.openerNo,
    date: item.uploadDate.slice(0, 10),
    type: typeNames[item.btype],
    pages: item.pages,
    sjh: item.sjh,
  }))
}

function statisticsSummary() {
  const rows = statisticsRows()
  const grouped = new Map<string, { type: string, recordCount: number, totalPages: number }>()
  rows.forEach((row) => {
    const current = grouped.get(row.type) ?? { type: row.type, recordCount: 0, totalPages: 0 }
    current.recordCount += 1
    current.totalPages += row.pages
    grouped.set(row.type, current)
  })

  return {
    total: {
      totalRecords: rows.length,
      totalPages: rows.reduce((sum, row) => sum + row.pages, 0),
    },
    uniqueBAHCount: new Set(rows.map(row => row.bah)).size,
    byType: [...grouped.values()],
  }
}

function dateSummary() {
  const grouped = new Map<string, { date: string, recordCount: number, totalPages: number }>()
  statisticsRows().forEach((row) => {
    const current = grouped.get(row.date) ?? { date: row.date, recordCount: 0, totalPages: 0 }
    current.recordCount += 1
    current.totalPages += row.pages
    grouped.set(row.date, current)
  })
  return [...grouped.values()].sort((a, b) => a.date.localeCompare(b.date))
}

function buildResponseAnalysis(days: number) {
  const pointCount = Math.min(Math.max(days, 7), 30)
  const trend = Array.from({ length: pointCount }, (_, index) => {
    const day = String(Math.max(1, 13 - (pointCount - index - 1))).padStart(2, '0')
    return {
      bucket: `2026-07-${day}`,
      requestCount: 720 + index * 41,
      errorCount: index % 6 === 0 ? 9 : 3,
      avgServerDurationMs: 88 + (index * 11) % 54,
      avgClientDurationMs: 132 + (index * 13) % 81,
    }
  })

  return {
    overview: {
      totalRequests: trend.reduce((sum, item) => sum + item.requestCount, 0),
      frontendSampleCount: trend.reduce((sum, item) => sum + item.requestCount, 0),
      successRate: 99.24,
      avgServerDurationMs: 112,
      avgClientDurationMs: 168,
      p95ClientDurationMs: 326,
    },
    trend,
    slowEndpoints: [
      {
        routePattern: '/api/v1/statistics',
        method: 'GET',
        requestCount: 1240,
        errorCount: 4,
        avgServerDurationMs: 238,
        avgClientDurationMs: 331,
        p95ClientDurationMs: 612,
      },
      {
        routePattern: '/api/v1/logs/audit/images',
        method: 'GET',
        requestCount: 885,
        errorCount: 8,
        avgServerDurationMs: 194,
        avgClientDurationMs: 286,
        p95ClientDurationMs: 520,
      },
      {
        routePattern: '/api/v1/scan/page/condition',
        method: 'POST',
        requestCount: 672,
        errorCount: 2,
        avgServerDurationMs: 151,
        avgClientDurationMs: 226,
        p95ClientDurationMs: 418,
      },
      {
        routePattern: '/api/v1/oss/migration/logs',
        method: 'GET',
        requestCount: 311,
        errorCount: 1,
        avgServerDurationMs: 127,
        avgClientDurationMs: 205,
        p95ClientDurationMs: 376,
      },
    ],
  }
}

function filterLogs(query: Record<string, QueryValue>, onlyAudit = false) {
  return logs.filter((item) => {
    const keyword = normalize(firstQuery(query.keyword))
    const username = normalize(firstQuery(query.username))
    const clientIp = normalize(firstQuery(query.clientIp))
    const requestUri = normalize(firstQuery(query.requestUri))
    const method = normalize(firstQuery(query.method))
    const auditAction = normalize(firstQuery(query.auditAction))
    const responseStatus = normalize(firstQuery(query.responseStatus))

    if (onlyAudit && !item.auditAction) { return false }
    if (keyword && ![
      item.username,
      item.clientIp,
      item.requestUri,
      item.auditTarget,
      item.auditDescription,
    ].some(value => normalize(value).includes(keyword))) {
      return false
    }
    if (username && !normalize(item.username).includes(username)) { return false }
    if (clientIp && !normalize(item.clientIp).includes(clientIp)) { return false }
    if (requestUri && !normalize(item.requestUri).includes(requestUri)) { return false }
    if (method && normalize(item.method) !== method) { return false }
    if (auditAction && normalize(item.auditAction) !== auditAction) { return false }
    if (responseStatus && !String(item.responseStatus).startsWith(responseStatus)) { return false }
    return true
  })
}

function imageAuditAnalytics(filteredLogs = logs) {
  const auditLogs = filteredLogs.filter(item => item.auditAction)
  const trendMap = new Map<string, number>()
  const actionMap = new Map<string, number>()
  const userMap = new Map<string, number>()

  auditLogs.forEach((item) => {
    const date = item.accessTime.slice(0, 10)
    trendMap.set(date, (trendMap.get(date) ?? 0) + 1)
    actionMap.set(item.auditAction, (actionMap.get(item.auditAction) ?? 0) + 1)
    userMap.set(item.username, (userMap.get(item.username) ?? 0) + 1)
  })

  return {
    totalAccesses: auditLogs.length,
    uniqueUsers: new Set(auditLogs.map(item => item.username)).size,
    uniqueTargets: new Set(auditLogs.map(item => item.auditTarget)).size,
    abnormalAccesses: auditLogs.filter(item => item.responseStatus >= 400).length,
    averageDurationMs: Math.round(auditLogs.reduce((sum, item) => sum + item.executeTime, 0) / Math.max(1, auditLogs.length)),
    trend: Array.from(trendMap.entries(), ([date, count]) => ({ date, count }))
      .sort((a, b) => a.date.localeCompare(b.date)),
    actionDistribution: Array.from(actionMap.entries(), ([label, count]) => ({ label, count })),
    topUsers: Array.from(userMap.entries(), ([label, count]) => ({ label, count }))
      .sort((a, b) => b.count - a.count),
  }
}

const systemInfo = {
  application: {
    name: 'MRR Backend Mock',
    startTime: '2026-07-13 08:00:00',
    runTime: '5小时 42分钟',
  },
  jvm: {
    javaVersion: '21.0.7',
    javaVendor: 'Eclipse Adoptium',
    javaHome: '/mock/jvm',
    availableProcessors: 8,
    maxMemory: '4.00 GB',
    totalMemory: '1.50 GB',
    freeMemory: '620 MB',
    usedMemory: '916 MB',
  },
  operatingSystem: {
    name: 'Mock Linux',
    version: '6.8',
    arch: 'amd64',
    availableProcessors: '8',
    systemLoadAverage: '0.42',
  },
}

const memoryInfo = {
  heap: {
    init: '256 MB',
    used: '916 MB',
    committed: '1.50 GB',
    max: '4.00 GB',
  },
  nonHeap: {
    init: '7 MB',
    used: '148 MB',
    committed: '162 MB',
    max: '-1',
  },
  usagePercent: '22.4%',
}

const runtimeInfo = {
  name: 'MRR-MOCK@localhost',
  startTime: 1783939200000,
  uptimeMillis: 20_520_000,
  uptimeFormatted: '5小时 42分钟',
  classPath: '/mock/mrr-backend.jar',
  inputArguments: ['-Xms256m', '-Xmx4g', '-Dspring.profiles.active=mock'],
}

const healthInfo = {
  status: 'UP',
  timestamp: mockTimestamp,
  port: '18045',
  application: 'MRR Backend Mock',
  components: {
    database: { status: 'UP', usagePercent: '18%' },
    disk: { status: 'UP', usagePercent: '43%' },
    oss: { status: 'UP' },
  },
}

const systemProperties = {
  'spring.profiles.active': 'mock',
  'server.port': '18045',
  'database.vendor': 'PostgreSQL Mock',
  'storage.mode': 'local + OSS mock',
}

export default defineFakeRoute([
  {
    url: '/api/v1/auth/login',
    method: 'POST',
    response: ({ body }) => ok({
      accessToken: 'mock-access-token',
      token: 'mock-access-token',
      user: {
        ...users[0],
        username: String(body.username || 'admin'),
      },
    }, '登录成功'),
  },
  {
    url: '/api/v1/auth/register',
    method: 'POST',
    response: ({ body }) => {
      const user: MockUser = {
        id: Math.max(...users.map(item => item.id)) + 1,
        username: String(body.username || `user${users.length + 1}`),
        displayName: String(body.displayName || body.username || '模拟用户'),
        roleCode: 'OPERATOR',
        roleName: '操作员',
        permissions: ['scan:read', 'statistics:read'],
        status: 'ACTIVE',
        lastLoginAt: mockTimestamp,
      }
      users.push(user)
      return ok({ accessToken: 'mock-access-token', token: 'mock-access-token', user }, '注册成功')
    },
  },
  {
    url: '/api/v1/auth/me',
    response: () => ok(users[0]),
  },
  {
    url: '/api/v1/auth/password/edit',
    method: 'POST',
    response: () => ok(null, '密码修改成功'),
  },
  {
    url: '/api/v1/auth/logout',
    method: 'POST',
    response: () => ok(null, '已退出登录'),
  },
  {
    url: '/api/v1/auth/users',
    response: ({ query }) => {
      const keyword = normalize(firstQuery(query.keyword))
      const roleCode = normalize(firstQuery(query.roleCode))
      const status = normalize(firstQuery(query.status))
      const filtered = users.filter((item) => {
        if (keyword && !normalize(`${item.username} ${item.displayName}`).includes(keyword)) { return false }
        if (roleCode && normalize(item.roleCode) !== roleCode) { return false }
        if (status && normalize(item.status) !== status) { return false }
        return true
      })
      return ok(paginate(filtered, query.page, query.size))
    },
  },
  {
    url: '/api/v1/auth/roles',
    response: () => ok(roles),
  },
  {
    url: '/api/v1/auth/roles/:code',
    method: 'PUT',
    response: ({ params, body }) => {
      const code = String(params.code)
      roles = roles.map(role => role.code === code ? { ...role, ...body, code } : role)
      return ok(roles.find(role => role.code === code))
    },
  },
  {
    url: '/api/v1/auth/users/:id',
    method: 'PUT',
    response: ({ params, body }) => {
      const id = Number(params.id)
      users = users.map(user => user.id === id ? { ...user, ...body, id } : user)
      return ok(users.find(user => user.id === id))
    },
  },
  {
    url: '/api/v1/auth/users/:id',
    method: 'DELETE',
    response: ({ params }) => {
      users = users.filter(user => user.id !== Number(params.id))
      return ok(null, '用户已停用')
    },
  },
  {
    url: '/api/v1/scan/page',
    response: ({ query }) => ok(paginate(scans, query.page, query.size)),
  },
  {
    url: '/api/v1/scan/page/condition',
    method: 'POST',
    response: ({ query, body }) => ok(paginate(filterScans(scans, body), query.page, query.size)),
  },
  {
    url: '/api/v1/scan/batch-download',
    method: 'POST',
    headers: {
      'content-type': 'application/zip',
      'content-disposition': 'attachment; filename="mock-records.zip"',
    },
    response: () => 'MRR mock archive',
  },
  {
    url: '/api/v1/scan/condition',
    method: 'POST',
    response: ({ body }) => ok(filterScans(scans, body)),
  },
  {
    url: '/api/v1/scan/bah/:bah',
    response: ({ params }) => ok(scans.filter(item => item.bah === String(params.bah))),
  },
  {
    url: '/api/v1/scan/brxh/:brxh',
    response: ({ params }) => ok(scans.filter(item => item.brxh === String(params.brxh))),
  },
  {
    url: '/api/v1/scan/:id',
    response: ({ params }) => ok(scans.find(item => item.id === Number(params.id)) ?? null),
  },
  {
    url: '/api/v1/scan',
    method: 'POST',
    response: ({ body }) => {
      const item = {
        ...scans[0],
        ...body,
        id: Math.max(...scans.map(record => record.id)) + 1,
      } as MockScanRecord
      scans.unshift(item)
      return ok(item, '扫描记录创建成功')
    },
  },
  {
    url: '/api/v1/scan/:id',
    method: 'PUT',
    response: ({ params, body }) => {
      const id = Number(params.id)
      const index = scans.findIndex(item => item.id === id)
      if (index >= 0) { scans[index] = { ...scans[index], ...body, id } }
      return ok(scans[index] ?? null, '扫描记录更新成功')
    },
  },
  {
    url: '/api/v1/scan/:id',
    method: 'DELETE',
    response: ({ params }) => {
      const index = scans.findIndex(item => item.id === Number(params.id))
      if (index >= 0) { scans.splice(index, 1) }
      return ok(null, '扫描记录删除成功')
    },
  },
  {
    url: '/api/v1/statistics',
    response: ({ query }) => {
      const source = statisticsRows()
      const keyword = normalize(firstQuery(query.keyword))
      const bah = normalize(firstQuery(query.bah))
      const sjh = normalize(firstQuery(query.sjh))
      const type = normalize(firstQuery(query.type))
      const startDate = normalize(firstQuery(query.startDate))
      const endDate = normalize(firstQuery(query.endDate))
      const sortBy = firstQuery(query.sortBy) || 'date'
      const sortOrder = firstQuery(query.sortOrder) || 'desc'
      const filtered = source.filter((item) => {
        if (keyword && !normalize(`${item.bah} ${item.sjh} ${item.type} ${item.openerNo}`).includes(keyword)) { return false }
        if (bah && !normalize(item.bah).includes(bah)) { return false }
        if (sjh && !normalize(item.sjh).includes(sjh)) { return false }
        if (type && normalize(item.type) !== type) { return false }
        if (startDate && item.date < startDate) { return false }
        if (endDate && item.date > endDate) { return false }
        return true
      })
      filtered.sort((a, b) => {
        const left = String(a[sortBy as keyof typeof a] ?? '')
        const right = String(b[sortBy as keyof typeof b] ?? '')
        return sortOrder === 'asc' ? left.localeCompare(right) : right.localeCompare(left)
      })
      return ok(paginate(filtered, query.page, query.size))
    },
  },
  {
    url: '/api/v1/statistics/summary',
    response: () => ok(statisticsSummary()),
  },
  {
    url: '/api/v1/statistics/date-summary',
    response: () => ok(dateSummary()),
  },
  {
    url: '/api/v1/statistics/dashboard',
    response: () => {
      const summary = statisticsSummary()
      const byBah = new Map<string, { bah: string, recordCount: number, totalPages: number }>()
      statisticsRows().forEach((row) => {
        const current = byBah.get(row.bah) ?? { bah: row.bah, recordCount: 0, totalPages: 0 }
        current.recordCount += 1
        current.totalPages += row.pages
        byBah.set(row.bah, current)
      })
      return ok({
        overview: summary.total,
        uniqueBAHCount: summary.uniqueBAHCount,
        recentTrend: dateSummary().slice(-14),
        topBAH: [...byBah.values()].sort((a, b) => b.totalPages - a.totalPages).slice(0, 10),
      })
    },
  },
  {
    url: '/api/v1/statistics/bah-summary',
    response: () => {
      const grouped = new Map<string, { bah: string, recordCount: number, totalPages: number }>()
      statisticsRows().forEach((row) => {
        const current = grouped.get(row.bah) ?? { bah: row.bah, recordCount: 0, totalPages: 0 }
        current.recordCount += 1
        current.totalPages += row.pages
        grouped.set(row.bah, current)
      })
      return ok([...grouped.values()])
    },
  },
  {
    url: '/api/v1/statistics/type-summary',
    response: () => ok(statisticsSummary().byType),
  },
  {
    url: '/api/v1/statistics/date-summary/condition',
    response: ({ query }) => {
      const startDate = normalize(firstQuery(query.startDate))
      const endDate = normalize(firstQuery(query.endDate))
      const rows = dateSummary().filter((item) => {
        if (startDate && item.date < startDate) { return false }
        if (endDate && item.date > endDate) { return false }
        return true
      })
      return ok(rows)
    },
  },
  {
    url: '/api/v1/statistics/bah/:bah',
    response: ({ params }) => ok(statisticsRows().filter(item => item.bah === String(params.bah))),
  },
  {
    url: '/api/v1/statistics/date/:date',
    response: ({ params }) => ok(statisticsRows().filter(item => item.date === String(params.date))),
  },
  {
    url: '/api/v1/statistics/export/csv',
    headers: {
      'content-type': 'text/csv; charset=utf-8',
      'content-disposition': 'attachment; filename="mock-statistics.csv"',
    },
    response: () => {
      const header = 'bah,sjh,type,pages,date,openerNo'
      const rows = statisticsRows().slice(0, 20).map(item =>
        [item.bah, item.sjh, item.type, item.pages, item.date, item.openerNo].join(','),
      )
      return `\uFEFF${[header, ...rows].join('\n')}`
    },
  },
  {
    url: '/api/v1/patients',
    response: ({ query }) => {
      const keyword = normalize(firstQuery(query.keyword))
      const filtered = patients.filter(item =>
        !keyword || normalize(`${item.name} ${item.bah} ${item.idCard} ${item.department}`).includes(keyword),
      )
      return ok(paginate(filtered, query.page, query.size))
    },
  },
  {
    url: '/api/v1/patients/bah/:bah',
    response: ({ params }) => ok(patients.filter(item => item.bah === String(params.bah))),
  },
  {
    url: '/api/v1/patients/idcard/:idCard',
    response: ({ params }) => ok(patients.filter(item => item.idCard === String(params.idCard))),
  },
  {
    url: '/api/v1/logs/search',
    response: ({ query }) => ok(paginate(filterLogs(query), query.page, query.size)),
  },
  {
    url: '/api/v1/logs/audit/images',
    response: ({ query }) => ok(paginate(filterLogs(query, true), query.page, query.size)),
  },
  {
    url: '/api/v1/logs/audit/images/analytics',
    response: ({ query }) => ok(imageAuditAnalytics(filterLogs(query, true))),
  },
  {
    url: '/api/v1/logs/retention/cleanup',
    method: 'POST',
    response: () => ok(null, '模拟清理完成'),
  },
  {
    url: '/api/v1/logs/retention/export',
    headers: {
      'content-type': 'text/csv; charset=utf-8',
      'content-disposition': 'attachment; filename="mock-logs.csv"',
    },
    response: () => 'id,username,requestUri,responseStatus\n1,admin,/api/v1/mock,200',
  },
  {
    url: '/api/v1/logs/:id',
    response: ({ params }) => ok(logs.find(item => item.id === Number(params.id)) ?? null),
  },
  {
    url: '/api/v1/response-metrics/frontend/batch',
    method: 'POST',
    response: () => ok(null),
  },
  {
    url: '/api/v1/response-metrics/analysis',
    response: ({ query }) => ok(buildResponseAnalysis(toNumber(query.days, 7))),
  },
  {
    url: '/api/v1/system/health',
    response: () => ok(healthInfo),
  },
  {
    url: '/api/v1/system/overview',
    response: () => ok({
      info: systemInfo,
      memory: memoryInfo,
      runtime: runtimeInfo,
      health: healthInfo,
      properties: systemProperties,
      gc: {
        G1YoungGeneration: { name: 'G1 Young Generation', count: 328, timeMs: 1840 },
        G1OldGeneration: { name: 'G1 Old Generation', count: 4, timeMs: 611 },
        totalCollections: 332,
        totalTimeMs: 2451,
      },
      threads: {
        currentCount: 48,
        daemonCount: 42,
        peakCount: 67,
        totalStarted: 512,
      },
    }),
  },
  {
    url: '/api/v1/system/runtime',
    response: () => ok(runtimeInfo),
  },
  {
    url: '/api/v1/system/memory',
    response: () => ok(memoryInfo),
  },
  {
    url: '/api/v1/system/info',
    response: () => ok(systemInfo),
  },
  {
    url: '/api/v1/system/properties',
    response: () => ok(systemProperties),
  },
  {
    url: '/actuator/health',
    response: () => ({ status: 'UP', components: healthInfo.components }),
  },
  {
    url: '/actuator/metrics',
    response: () => ({
      names: [
        'hikaricp.connections.active',
        'hikaricp.connections.idle',
        'hikaricp.connections.pending',
      ],
    }),
  },
  {
    url: '/actuator/metrics/:name',
    response: ({ params }) => {
      const values: Record<string, number> = {
        'hikaricp.connections.active': 4,
        'hikaricp.connections.idle': 6,
        'hikaricp.connections.pending': 0,
      }
      const name = String(params.name)
      return {
        name,
        description: `Mock metric for ${name}`,
        baseUnit: 'connections',
        measurements: [{ statistic: 'VALUE', value: values[name] ?? 0 }],
      }
    },
  },
  {
    url: '/api/v1/settings',
    response: () => ok(settings),
  },
  {
    url: '/api/v1/settings',
    method: 'PUT',
    response: ({ body }) => {
      settings = { ...settings, ...body }
      return ok(null, '设置保存成功')
    },
  },
  {
    url: '/api/v1/settings/:key',
    response: ({ params }) => ok(settings[decodeURIComponent(String(params.key))] ?? ''),
  },
  {
    url: '/api/v1/settings/:key',
    method: 'PUT',
    response: ({ params, body }) => {
      settings[decodeURIComponent(String(params.key))] = String(body.value ?? '')
      return ok(null, '设置保存成功')
    },
  },
  {
    url: '/api/v1/oss/migration/statistics',
    response: () => {
      const migratedCount = scans.filter(item => item.migrationStatus !== 'not_migrated').length
      const failedCount = migrationLogs.filter(item => item.migrationStatus === 'failed').length
      return ok({
        totalCount: scans.length,
        migratedCount,
        pendingCount: scans.length - migratedCount,
        failedCount,
        percentage: Math.round(migratedCount / scans.length * 100),
      })
    },
  },
  {
    url: '/api/v1/oss/migration/pending',
    response: ({ query }) => {
      const folder = normalize(firstQuery(query.folder))
      const limit = toNumber(query.limit, 100)
      const pending = scans.filter(item =>
        item.migrationStatus === 'not_migrated' && (!folder || normalize(item.folder).includes(folder)),
      )
      return ok({ list: pending.slice(0, limit), total: pending.length })
    },
  },
  {
    url: '/api/v1/oss/migration/pending-folders',
    response: () => {
      const grouped = new Map<string, number>()
      scans.filter(item => item.migrationStatus === 'not_migrated').forEach((item) => {
        grouped.set(item.folder, (grouped.get(item.folder) ?? 0) + 1)
      })
      return ok(Array.from(grouped.entries(), ([folder, cnt]) => ({ folder, cnt })))
    },
  },
  {
    url: '/api/v1/oss/migration/logs',
    response: ({ query }) => {
      const status = normalize(firstQuery(query.status))
      const filtered = migrationLogs.filter(item => !status || normalize(item.migrationStatus) === status)
      return ok(paginate(filtered, query.page, query.size))
    },
  },
  {
    url: '/api/v1/oss/upload',
    method: 'POST',
    response: ({ body }) => ok((body.scanIds ?? []).map((scanId: number) => ({
      scanId,
      ossUrl: `https://example.invalid/mock/${scanId}.jpg`,
      status: 'success',
    })), '模拟上传完成'),
  },
  {
    url: '/api/v1/oss/upload/bah/:bah',
    method: 'POST',
    response: ({ params }) => ok(scans
      .filter(item => item.bah === String(params.bah))
      .map(item => ({ scanId: item.id, ossUrl: `https://example.invalid/mock/${item.id}.jpg`, status: 'success' }))),
  },
  {
    url: '/api/v1/oss/upload/folder/:folder',
    method: 'POST',
    response: ({ params }) => ok(scans
      .filter(item => item.folder === decodeURIComponent(String(params.folder)))
      .map(item => ({ scanId: item.id, ossUrl: `https://example.invalid/mock/${item.id}.jpg`, status: 'success' }))),
  },
  {
    url: '/api/v1/oss/url/:scanId',
    response: ({ params }) => ok(`https://example.invalid/mock/${params.scanId}.jpg`),
  },
  {
    url: '/api/v1/oss/migration/jobs',
    method: 'POST',
    response: () => ok({ id: 1, status: 'RUNNING', progress: 35 }),
  },
  {
    url: '/api/v1/oss/migration/jobs',
    response: ({ query }) => ok(paginate([
      { id: 1, status: 'RUNNING', progress: 35, createdAt: mockTimestamp },
      { id: 2, status: 'COMPLETED', progress: 100, createdAt: '2026-07-12T08:00:00.000Z' },
    ], query.page, query.size)),
  },
  {
    url: '/api/v1/oss/migration/jobs/:id',
    response: ({ params }) => ok({ id: Number(params.id), status: 'RUNNING', progress: 35 }),
  },
])

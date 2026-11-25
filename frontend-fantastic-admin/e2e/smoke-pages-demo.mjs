import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import process from 'node:process'
import { chromium } from '@playwright/test'
import JSZip from 'jszip'
import { preview } from 'vite'

async function smokePagesDemo() {
  const origin = 'http://127.0.0.1:4177'
  const appUrl = `${origin}/MRR/`
  const server = await preview({
    mode: 'pages',
    preview: { host: '127.0.0.1', port: 4177, strictPort: true },
  })
  const outboundApiRequests = []
  server.httpServer.prependListener('request', (request) => {
    if (request.url?.startsWith('/proxy/')) {
      outboundApiRequests.push(request.url)
    }
  })
  let browser

  try {
    const indexResponse = await fetch(appUrl)
    assert.equal(indexResponse.status, 200, 'Pages 子路径应返回入口 HTML')
    const html = await indexResponse.text()
    assert.match(html, /(?:src|href)="\/MRR\//, '打包资源必须使用 /MRR/ 作为 base')
    assert.doesNotMatch(html, /(?:src|href)="\/assets\//, '不能使用根路径资产链接')

    browser = await chromium.launch({ headless: true })
    const page = await browser.newPage()
    const pageErrors = []
    page.on('pageerror', error => pageErrors.push(error.message))

    const response = await page.goto(`${appUrl}#/records`, { waitUntil: 'domcontentloaded' })
    assert.equal(response?.status(), 200, 'hash 深链必须落在项目入口')
    await page.waitForFunction(() => Boolean(document.querySelector('#app')?.children.length), null, { timeout: 45_000 })

    const mockResponses = await page.evaluate(async () => {
      const [user, scans] = await Promise.all([
        fetch('/proxy/api/v1/auth/me').then(response => response.json()),
        fetch('/proxy/api/v1/scan/page?page=1&size=3').then(response => response.json()),
      ])
      return { user, scans }
    })
    assert.equal(mockResponses.user?.data?.username, 'admin', '用户信息必须来自浏览器 Mock')
    assert.equal(mockResponses.scans?.data?.list?.length, 3, '扫描记录必须由浏览器 Mock 返回')

    const publicConfig = await page.evaluate(async () => {
      const get = path => fetch(`/proxy/api/v1/public/config/${path}`).then(response => response.json())
      const [login, environment, docs, session] = await Promise.all([
        get('login-page'),
        get('client-environment'),
        get('documentation'),
        fetch('/proxy/api/v1/documentation/session', { method: 'POST' }).then(response => response.json()),
      ])
      return { login, environment, docs, session }
    })
    assert.equal(publicConfig.login.data.loginEnvironmentLabel, '静态演示环境')
    assert.equal(publicConfig.login.data.systemAdminContactVisible, false)
    assert.equal(publicConfig.environment.code, 503)
    assert.equal(publicConfig.environment.data, undefined)
    assert.deepEqual(publicConfig.docs.data, { documentationUserGuideUrl: '', documentationDeveloperUrl: '', documentationOperationsUrl: '' })
    assert.equal(publicConfig.session.code, 503)
    const uiSettings = await page.evaluate(() => fetch('/proxy/api/v1/settings/ui').then(response => response.json()))
    assert.equal(uiSettings.code, 200)
    assert.ok(Object.keys(uiSettings.data).every(key => key === 'adminAppSettings'), 'UI设置接口只暴露界面配置')
    const queryDiagnostics = await page.evaluate(() => fetch('/proxy/api/v1/operations/query-diagnostics', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ bah: 'synthetic' }) }).then(response => response.json()))
    assert.equal(queryDiagnostics.code, 503, '静态演示不得伪造数据库诊断结果')
    const demoBrowse = await page.evaluate(async () => {
      const get = path => fetch(`/proxy/api/v1/system/database/browse/tables${path}`).then(response => response.json())
      const tables = await get('')
      const schema = await get('/scan')
      const first = await get('/scan/rows?page=1&size=50')
      const second = await get(`/scan/rows?page=2&size=50&cursor=${encodeURIComponent(first.data.nextCursor)}`)
      const filtered = await get(`/scan/rows?filter=${encodeURIComponent('bah:EQ:26070000')}`)
      const unknown = await get('/unknown')
      return { tables, schema, first, second, filtered, unknown }
    })
    assert.equal(demoBrowse.tables.data.length, 2)
    assert.equal(demoBrowse.schema.data.maxPageSize, 50)
    assert.equal(demoBrowse.first.data.rows.length, 50)
    assert.equal(demoBrowse.second.data.rows.length, 14)
    assert.equal(new Set([...demoBrowse.first.data.rows, ...demoBrowse.second.data.rows].map(row => row.id)).size, 64)
    assert.equal(demoBrowse.filtered.data.rows.length, 2)
    assert.ok(demoBrowse.filtered.data.rows.every(row => row.bah === '26****00'))
    assert.equal(demoBrowse.unknown.code, 404)
    const denied = await page.evaluate(async () => {
      const call = (path, method = 'GET') => fetch(`/proxy/api/v1/${path}`, { method }).then(response => response.json())
      return Promise.all([call('external/archive/session', 'POST'), call('external/archive/context'), call('external/archive/images'), call('auth/users', 'POST'), call('auth/password/required-change', 'POST'), call('search/archive-cases', 'POST'), call('patients/1', 'PUT'), call('data-exchange/patients/import', 'POST')])
    })
    assert.ok(denied.every(result => result.code === 503 && result.data === undefined), '不得伪造真实认证或医疗写入成功')

    // Verify shared records through the actual static browser Mock.
    const relations = await page.evaluate(async () => {
      const get = path => fetch(`/proxy/api/v1/${path}`).then(response => response.json())
      const [scans, patients, summary, logs] = await Promise.all([
        get('scan/page?page=1&size=100'),
        get('patients?page=1&size=100'),
        get('statistics/summary'),
        get('logs/audit/images?page=1&size=100'),
      ])
      const patient = patients.data.list[0]
      const [byBah, byBrxh] = await Promise.all([
        get(`patients/bah/${patient.bah}`),
        get(`scan/brxh/${patient.brxh}`),
      ])
      return { scans, patients, summary, logs, byBah, byBrxh }
    })
    const allScans = relations.scans.data.list
    const allPatients = relations.patients.data.list
    assert.equal(allScans.length, 64)
    assert.equal(allPatients.length, 32)
    const patientsByBah = new Map(allPatients.map(patient => [patient.bah, patient]))
    const scansById = new Map(allScans.map(scan => [scan.id, scan]))
    for (const scan of allScans) {
      const patient = patientsByBah.get(scan.bah)
      assert.ok(patient, '每条扫描必须关联合成患者')
      assert.equal(scan.brxh, patient.brxh)
      assert.ok(scan.uploadDate >= patient.admissiontime)
    }
    assert.equal(relations.byBah.data[0].brxh, allPatients[0].brxh)
    assert.equal(relations.byBrxh.data.length, 2)
    assert.equal(relations.summary.data.total.totalRecords, allScans.length)
    assert.equal(relations.summary.data.total.totalPages, allScans.reduce((sum, scan) => sum + scan.pages, 0))
    assert.equal(relations.summary.data.uniqueBAHCount, allPatients.length)
    for (const log of relations.logs.data.list) {
      if (log.auditAction === 'VIEW_IMAGE' || log.auditAction === 'VIEW_OSS_IMAGE') {
        const target = scansById.get(Number(log.requestUri.split('/').pop()))
        assert.ok(target, '影像访问日志不得引用不存在的扫描 ID')
        assert.equal(log.auditTarget, target.bah)
        assert.ok(log.accessTime >= target.uploadDate)
      }
    }

    // Patient analytics uses the same synthetic records as the list.
    const patientAnalytics = await page.evaluate(async () => {
      const get = path => fetch(`/proxy/api/v1/${path}`).then(response => response.json())
      const [summary, missing, groups, confirmed, departments, invalidYear] = await Promise.all([
        get('patients/analytics/summary'),
        get('patients/analytics/missing-idcard?page=1&size=1'),
        get('patients/analytics/multi-record-groups?page=1&size=20&includeSuspected=true'),
        get('patients/analytics/multi-record-groups?page=1&size=20&includeSuspected=false'),
        get('statistics/departments'),
        get('patients/analytics/summary?year=1899'),
      ])
      return { summary, missing, groups, confirmed, departments, invalidYear }
    })
    assert.equal(patientAnalytics.summary.data.totalRecords, allPatients.length)
    assert.equal(patientAnalytics.summary.data.missingIdCardRecords, allPatients.filter(row => !row.idCard).length)
    assert.equal(patientAnalytics.missing.data.total, patientAnalytics.summary.data.missingIdCardRecords)
    assert.equal(patientAnalytics.missing.data.list.length, 1)
    assert.equal(patientAnalytics.groups.data.total, 2)
    assert.equal(patientAnalytics.confirmed.data.total, 1)
    assert.equal(patientAnalytics.confirmed.data.list[0].matchType, 'IDCARD')
    assert.match(patientAnalytics.confirmed.data.list[0].maskedIdCard, /^000\*+\d{4}$/)
    assert.deepEqual(patientAnalytics.departments.data, [...new Set(allPatients.map(row => row.department))].sort())
    assert.equal(patientAnalytics.invalidYear.code, 400)

    // A historical year and type must produce the same rows in details,
    // daily aggregates and the actual downloadable CSV.
    const historical = await page.evaluate(async () => {
      const req = path => fetch(`/proxy/api/v1/statistics${path}`)
      const get = path => req(path).then(response => response.json())
      const all = await get('?page=1&size=100&sortBy=pages&sortOrder=asc')
      const year = [...new Set(all.data.list.map(row => row.date.slice(0, 4)))].sort()[0]
      const type = all.data.list.find(row => row.date.startsWith(year)).type
      const query = new URLSearchParams({ startDate: `${year}-01-01`, endDate: `${year}-12-31`, type })
      const [list, days, csv, empty, allCsv] = await Promise.all([
        get(`?page=1&size=100&${query}`),
        get(`/date-summary/condition?${query}`),
        req(`/export/csv?${query}`).then(response => response.text()),
        get('?page=1&size=100&startDate=1900-01-01&endDate=1900-12-31'),
        req('/export/csv').then(response => response.text()),
      ])
      return { all, year, type, list, days, csv, empty, allCsv }
    })
    const historicalRows = historical.list.data.list
    assert.ok(new Set(historical.all.data.list.map(row => row.date.slice(0, 4))).size >= 2)
    assert.ok(historical.all.data.list.every((row, index, rows) => index === 0 || rows[index - 1].pages <= row.pages))
    assert.ok(historicalRows.length > 0)
    assert.ok(historicalRows.every(row => row.date.startsWith(historical.year) && row.type === historical.type))
    assert.equal(historical.days.data.reduce((sum, row) => sum + row.recordCount, 0), historicalRows.length)
    assert.equal(historical.days.data.reduce((sum, row) => sum + row.totalPages, 0), historicalRows.reduce((sum, row) => sum + row.pages, 0))
    const csvRows = historical.csv.trim().split('\n').slice(1)
    assert.deepEqual(csvRows.map(line => line.split(',')[1]).sort(), historicalRows.map(row => row.sjh).sort())
    assert.equal(historical.allCsv.trim().split('\n').length - 1, allScans.length)
    assert.equal(historical.empty.data.total, 0)

    const migration = await page.evaluate(async () => {
      const get = path => fetch(`/proxy/api/v1/oss/migration/${path}`).then(response => response.json())
      const [stats, pending, waiting, folders, readiness] = await Promise.all([
        get('statistics'),
        get('pending?limit=100'),
        get('management/waiting-sjh?limit=100'),
        get('pending-folders'),
        get('readiness?sampleSize=2'),
      ])
      return { stats, pending, waiting, folders, readiness }
    })
    assert.equal(migration.stats.data.totalCount, allScans.filter(scan => scan.uploadFlag !== 0).length)
    assert.equal(migration.stats.data.migratedCount, allScans.filter(scan => scan.uploadFlag !== 0 && scan.ossUrl).length)
    assert.equal(migration.stats.data.pendingCount, migration.pending.data.total)
    assert.equal(migration.stats.data.waitingSjhCount, migration.waiting.data.returned)
    assert.equal(migration.folders.data.reduce((sum, folder) => sum + folder.cnt, 0), migration.pending.data.total)
    assert.equal(migration.readiness.data.pendingCount, migration.pending.data.total)
    assert.equal(migration.readiness.data.sampleSize, Math.min(2, migration.pending.data.total))
    for (const item of [...migration.pending.data.list, ...migration.waiting.data.list]) {
      assert.equal(item.bah, scansById.get(item.id)?.bah)
      assert.equal(item.sjh, scansById.get(item.id)?.sjh)
    }

    const archiveAssets = await page.evaluate(async () => {
      const get = path => fetch(`/proxy/api/v1/${path}`).then(response => response.json())
      const [legacy, shared, patient, missing, noShelf] = await Promise.all([
        get('img/search?bah=00800001'),
        get('img/search?sjh=90000001'),
        get('search/patient/26070000'),
        get('img/search?sjh=99999999'),
        get('img/search?bah=26070000'),
      ])
      const decode = async (src) => {
        const image = new Image()
        image.src = src
        try {
          await image.decode()
          return image.naturalWidth > 0
        }
        catch {
          return false
        }
      }
      const loaded = await Promise.all([...legacy.data, ...shared.data].map(item => decode(item.img_url)))
      return { legacy, shared, patient, missing, noShelf, loaded }
    })
    assert.equal(archiveAssets.legacy.data.length, 14)
    assert.equal(archiveAssets.shared.data.length, 1)
    assert.equal(archiveAssets.shared.data[0].id, allScans[0].id)
    assert.equal(archiveAssets.patient.data[0].name, allPatients[0].name)
    assert.equal(archiveAssets.patient.data[0].admissionTime, allPatients[0].admissiontime)
    assert.deepEqual(archiveAssets.missing.data, [])
    assert.equal(archiveAssets.noShelf.meta?.reasonCode, 'NEEDS_SJH')
    assert.ok(archiveAssets.loaded.every(Boolean), '档案袋影像必须在真实 Pages 浏览器中成功解码')

    const writes = await page.evaluate(async () => {
      const req = (path, body) => fetch(`/proxy/api/v1/${path}`, body === undefined
        ? undefined
        : {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body),
          }).then(response => response.json())
      const before = await req('oss/migration/statistics')
      const upload = await req('oss/upload', { scanIds: [1, 1, 99999] })
      const scan = await req('scan/1')
      const failed = await req('scan/13')
      const retry = await req('oss/migration/retry', { scanIds: [13, 13] })
      const reset = await req('scan/13')
      const after = await req('oss/migration/statistics')
      const image = new Image()
      image.src = scan.data.ossUrl
      await image.decode()
      return { before, upload, scan, failed, retry, reset, after, width: image.naturalWidth }
    })
    assert.deepEqual(writes.upload.data.results.map(item => item.status), ['success', 'skipped', 'failed'])
    assert.equal(writes.scan.data.migrationStatus, 'migrated')
    assert.ok(writes.width > 0)
    assert.equal(writes.failed.data.migrationStatus, 'failed')
    assert.equal(writes.retry.data.updated, 1)
    assert.equal(writes.reset.data.migrationStatus, 'not_migrated')
    assert.equal(writes.after.data.migratedCount, writes.before.data.migratedCount + 1)
    assert.equal(writes.after.data.failedCount, writes.before.data.failedCount - 1)

    const journal = await page.evaluate(async () => {
      const get = path => fetch(`/proxy/api/v1/oss/migration/${path}`).then(response => response.json())
      const [canonical, legacy, failed] = await Promise.all([
        get('management/logs?scanId=1&status=success&page=1&size=20'),
        get('logs?scanId=1&status=success&page=1&size=20'),
        get('management/logs?scanId=13&status=failed&page=1&size=20'),
      ])
      return { canonical, legacy, failed }
    })
    assert.deepEqual(journal.canonical.data, journal.legacy.data)
    assert.equal(journal.canonical.data.total, 1)
    assert.equal(journal.canonical.data.list[0].ossUrl, writes.scan.data.ossUrl)
    assert.equal(journal.failed.data.total, 1)
    assert.equal(journal.failed.data.list[0].migrationStatus, 'failed')

    const objects = await page.evaluate(async () => {
      const scan = await fetch('/proxy/api/v1/scan/1').then(response => response.json())
      const { sjh, bah, filename, ossUrl } = scan.data
      const prefix = `medical-records/${sjh.slice(0, 4)}/${sjh}-${bah}/`
      const key = prefix + filename
      const get = (path, params) => fetch(`/proxy/api/v1/oss/browser${path}?${new URLSearchParams(params)}`).then(response => response.json())
      const [listing, signed, missing] = await Promise.all([
        get('', { prefix }),
        get('/url', { key }),
        get('/url', { key: `${prefix}missing.jpg` }),
      ])
      const image = new Image()
      image.src = signed.data.ossUrl
      await image.decode()
      return { key, ossUrl, listing, signed, missing, width: image.naturalWidth }
    })
    assert.ok(objects.listing.data.entries.some(entry => entry.key === objects.key && !entry.directory))
    assert.equal(objects.signed.data.ossUrl, objects.ossUrl)
    assert.equal(objects.missing.code, 404)
    assert.ok(objects.width > 0)

    // Drive an actual bounded job through the browser Mock and observe its effect.
    const execution = await page.evaluate(async () => {
      const get = path => fetch(`/proxy/api/v1/${path}`).then(response => response.json())
      const post = (path, body) => fetch(`/proxy/api/v1/${path}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      }).then(response => response.json())
      const before = await get('oss/migration/statistics')
      const scan = (await get('scan/13')).data
      const job = await post('oss/migration/jobs', { mode: 'pilot', folder: scan.folder, limit: 1 })
      const blockedUpload = await post('oss/upload', { scanIds: [13] })
      let detail = job
      for (let attempt = 0; attempt < 20 && detail.data?.status !== 'completed'; attempt++) {
        await new Promise(resolve => setTimeout(resolve, 250))
        detail = await get(`oss/migration/jobs/${job.data.id}`)
      }
      const after = await get('oss/migration/statistics')
      const updated = await get('scan/13')
      const root = await get('oss/browser?prefix=medical-records%2F9000%2F&maxKeys=1')
      const next = await get(`oss/browser?prefix=medical-records%2F9000%2F&maxKeys=1&continuationToken=${encodeURIComponent(root.data.nextContinuationToken)}`)
      return { before, job, blockedUpload, detail, after, updated, root, next }
    })
    assert.equal(execution.job.data.totalCount, 1)
    assert.equal(execution.blockedUpload.code, 409)
    assert.equal(execution.detail.data.status, 'completed')
    assert.equal(execution.detail.data.processedCount, 1)
    assert.equal(execution.updated.data.migrationStatus, 'migrated')
    assert.equal(execution.after.data.migratedCount, execution.before.data.migratedCount + 1)
    assert.equal(execution.root.data.entries.length, 1)
    assert.equal(execution.root.data.truncated, true)
    assert.equal(execution.next.data.entries.length, 1)
    assert.notEqual(execution.root.data.entries[0].key, execution.next.data.entries[0].key)

    const boxes = await page.evaluate(async () => {
      const request = (path, method = 'GET', body) => fetch(`/proxy/api/v1/archive-box-records${path}`, {
        method,
        ...(body ? { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) } : {}),
      }).then(response => response.json())
      const before = await request('/summary')
      const rows = await request('?page=1&size=100')
      const created = await request('', 'POST', { bah: '99999990', sjh: '99999991', status: 'MISSING' })
      const duplicate = await request('', 'POST', { sjh: '99999991', status: 'NORMAL', boxNo: 'DEMO-NEW' })
      const id = created.data.id
      const moved = await request(`/${id}`, 'PUT', { bah: '99999990', sjh: '99999991', status: 'NORMAL', boxNo: 'DEMO-NEW' })
      const lookup = await request('/record/99999991')
      const group = await request('/boxes?keyword=DEMO-NEW')
      const detail = await request('/box/DEMO-NEW')
      await request(`/${id}`, 'DELETE')
      const missing = await request(`/${id}`)
      const after = await request('/summary')
      return { before, rows, created, duplicate, moved, lookup, group, detail, missing, after }
    })
    assert.ok(boxes.rows.data.list.length > 0)
    assert.ok(boxes.rows.data.list.every(row => allScans.some(scan => scan.bah === row.bah && scan.sjh === row.sjh)))
    assert.equal(boxes.duplicate.code, 400)
    assert.equal(boxes.moved.data.status, 'NORMAL')
    assert.equal(boxes.lookup.data[0].id, boxes.created.data.id)
    assert.equal(boxes.group.data.list[0].recordCount, 1)
    assert.equal(boxes.detail.data[0].id, boxes.created.data.id)
    assert.equal(boxes.missing.code, 404)
    assert.deepEqual(boxes.before.data, boxes.after.data)

    const ocr = await page.evaluate(async () => {
      const request = (path, body) => fetch(`/proxy/api/v1/${path}`, body === undefined
        ? undefined
        : {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body),
          }).then(response => response.json())
      const before = await request('scan/3/ocr')
      const created = await request('archive-records/2/ocr/jobs', { idempotencyKey: 'pages-ocr' })
      const reused = await request('archive-records/2/ocr/jobs', { idempotencyKey: 'pages-ocr' })
      let detail = created
      for (let attempt = 0; attempt < 20 && detail.data?.status !== 'SUCCESS'; attempt++) {
        await new Promise(resolve => setTimeout(resolve, 250))
        detail = await request(`ocr/jobs/${created.data.id}`)
      }
      const [full, hidden, items, scan, list, suggestions] = await Promise.all([
        request('scan/3/ocr?full=true'),
        request('scan/3/ocr'),
        request(`ocr/jobs/${created.data.id}/items`),
        request('scan/3'),
        request('archive-records/2/ocr/jobs'),
        request(`classification/jobs/${created.data.id}/suggestions`),
      ])
      const another = await request('archive-records/6/ocr/jobs', { idempotencyKey: 'pages-cancel' })
      const cancelled = await request(`ocr/jobs/${another.data.id}/cancel`, {})
      return { before, created, reused, detail, full, hidden, items, scan, list, suggestions, cancelled }
    })
    assert.equal(ocr.before.data.status, 'PENDING')
    assert.equal(ocr.created.data.plannedCount, 2)
    assert.equal(ocr.reused.data.id, ocr.created.data.id)
    assert.equal(ocr.detail.data.status, 'SUCCESS')
    assert.equal(ocr.detail.data.processedCount, 2)
    assert.ok(ocr.full.data.text.includes('合成影像'))
    assert.equal(ocr.hidden.data.text, null)
    assert.deepEqual(ocr.items.data.map(item => item.status), ['SUGGESTED', 'LOW_CONFIDENCE'])
    assert.equal(ocr.suggestions.data.length, 1)
    assert.equal(ocr.suggestions.data[0].scanId, 3)
    assert.equal(ocr.suggestions.data[0].reviewStatus, 'PENDING')
    assert.equal(ocr.suggestions.data[0].autoEligible, false)
    assert.equal(ocr.items.data[0].suggestedType, ocr.suggestions.data[0].suggestedType)
    assert.equal(ocr.scan.data.btype, 0)
    assert.equal(ocr.list.data[0].id, ocr.created.data.id)
    assert.equal(ocr.cancelled.data.status, 'CANCELLED')

    const blocked = await page.evaluate(async () => {
      const path = '/proxy/api/v1/pages-smoke-unmocked'
      const fetchBlocked = await fetch(path, { method: 'POST', body: 'synthetic-demo-only' }).then(() => false, () => true)
      const xhrBlocked = await new Promise((resolve) => {
        const request = new XMLHttpRequest()
        request.open('POST', path)
        request.timeout = 5_000
        request.onerror = () => resolve(true)
        request.onload = () => resolve(false)
        request.ontimeout = () => resolve(false)
        request.send('synthetic-demo-only')
      })
      return { fetchBlocked, xhrBlocked }
    })
    assert.deepEqual(blocked, { fetchBlocked: true, xhrBlocked: true }, '未模拟的 API 请求必须由浏览器拦截')

    await page.reload({ waitUntil: 'domcontentloaded' })
    await page.waitForFunction(() => Boolean(document.querySelector('#app')?.children.length), null, { timeout: 45_000 })
    assert.match(page.url(), /\/MRR\/#\/records/, '刷新必须保留 hash 路由')

    // 演示模式退出登录：守卫不得用 Dev User 自动重新登录，登录页必须可以驻留。
    await page.getByRole('button', { name: '账号菜单：Dev User' }).click({ timeout: 15_000 })
    await page.getByRole('menuitem', { name: '退出登录' }).click({ timeout: 15_000 })
    await page.waitForURL(/#\/login/, { timeout: 15_000 })
    await page.waitForSelector('#login-account', { timeout: 15_000 })
    await page.waitForTimeout(1200)
    assert.match(page.url(), /#\/login/, '退出登录后必须停留在登录页，不能被自动重新登录')
    assert.equal(
      await page.evaluate(() => window.sessionStorage.getItem('mrr:demo:logged-out')),
      '1',
      '退出登录必须写入演示模式已退出标记',
    )

    // 重新登录后清除标记并回到工作台。
    await page.fill('#login-account', 'admin')
    await page.fill('#login-password', 'demo-password')
    await page.getByRole('button', { name: '登录系统' }).click()
    await page.waitForFunction(() => window.sessionStorage.getItem('mrr:demo:logged-out') === null, null, { timeout: 15_000 })
    await page.waitForFunction(() => Boolean(window.location.hash) && window.location.hash !== '#/login', null, { timeout: 20_000 })
    assert.doesNotMatch(page.url(), /#\/login/, '重新登录后必须离开登录页')

    const filePage = await browser.newPage()
    filePage.on('pageerror', error => pageErrors.push(error.message))
    await filePage.goto(`${appUrl}#/nginx-browser`)
    await filePage.getByText('样例目录', { exact: true }).first().click({ timeout: 15_000 })
    await filePage.getByRole('button', { name: '打开文件夹' }).click({ timeout: 15_000 })
    await filePage.getByText('存储说明.txt', { exact: true }).first().click({ timeout: 15_000 })
    const previewText = filePage.getByRole('region', { name: '内容预览' }).getByText('MRR 文件浏览演示数据', { exact: false })
    await previewText.waitFor({ timeout: 15_000 })

    const [download] = await Promise.all([
      filePage.waitForEvent('download', { timeout: 15_000 }),
      filePage.getByRole('button', { name: '下载', exact: true }).click(),
    ])
    assert.equal(download.suggestedFilename(), '存储说明.txt')
    assert.match(await readFile(await download.path(), 'utf8'), /MRR 文件浏览演示数据/)

    // Verify real downloads rather than treating a mock success envelope as a file.
    const exportsPage = await browser.newPage()
    exportsPage.on('pageerror', error => pageErrors.push(error.message))
    await exportsPage.goto(`${appUrl}#/archive?bah=00800001`)
    const exportActions = exportsPage.locator('.archive-bottom-actions')
    const wholeButton = exportActions.getByRole('button', { name: /下载病案/ })
    await wholeButton.waitFor({ timeout: 20000 })
    const [wholeZipDownload] = await Promise.all([exportsPage.waitForEvent('download', { timeout: 20000 }), wholeButton.click()])
    const wholeZip = await JSZip.loadAsync(await readFile(await wholeZipDownload.path()))
    assert.equal(Object.keys(wholeZip.files).length, 15)
    assert.match(await wholeZip.file('image-91001.svg').async('string'), /合成影像 · 非真实病案/)
    await exportsPage.locator('.thumb-check').first().click()
    const [selectedZipDownload] = await Promise.all([
      exportsPage.waitForEvent('download', { timeout: 20000 }),
      exportActions.getByRole('button', { name: /导出选中 ZIP（1）/ }).click(),
    ])
    assert.equal(Object.keys((await JSZip.loadAsync(await readFile(await selectedZipDownload.path()))).files).length, 2)
    const [pdfDownload] = await Promise.all([
      exportsPage.waitForEvent('download', { timeout: 20000 }),
      exportActions.getByRole('button', { name: /导出选中 PDF（1）/ }).click(),
    ])
    const pdfBytes = await readFile(await pdfDownload.path())
    assert.equal(pdfBytes.subarray(0, 8).toString(), '%PDF-1.4')
    assert.match(pdfBytes.toString('latin1'), /\/Type \/Pages \/Count 1/)
    assert.match(pdfBytes.toString('latin1'), /%%EOF\s*$/)
    await exportsPage.close()

    // Exercise the independent workbench and real bundled binary Mock end to end.
    const workbench = await browser.newPage()
    workbench.on('pageerror', error => pageErrors.push(error.message))
    await workbench.goto(`${appUrl}#/archive-workbench`)
    const archiveInput = workbench.getByRole('textbox', { name: '病案查询值' })
    await archiveInput.waitFor({ timeout: 20000 })
    await archiveInput.fill('26070000')
    await archiveInput.press('Enter')
    const samples = workbench.getByRole('group', { name: '有效影像列表' })
    await samples.getByRole('button').first().click({ timeout: 20000 })
    const scanImage = workbench.getByAltText('待核对影像')
    await scanImage.waitFor({ timeout: 20000 })
    await workbench.waitForFunction(() => {
      const img = document.querySelector('img[alt="待核对影像"]')
      return img?.complete && img.naturalWidth > 0
    })
    assert.match(await scanImage.getAttribute('src'), /^blob:/)
    await workbench.getByRole('button', { name: '识别文字', exact: true }).click()
    await workbench.getByRole('region', { name: '影像文字识别' }).waitFor({ timeout: 15000 })
    await workbench.close()

    // Use the actual bundled browser Mock, not Playwright page.route stubs.
    const opsPage = await browser.newPage()
    opsPage.on('pageerror', error => pageErrors.push(error.message))
    await opsPage.goto(`${appUrl}#/operations-center`)
    await opsPage.locator('.overview-dashboard').getByText('当前允许业务写入。').waitFor({ timeout: 20_000 })
    await opsPage.getByRole('heading', { name: '运行历史与容量趋势' }).waitFor({ timeout: 20000 })
    await opsPage.getByRole('img', { name: 'JVM 堆内存历史' }).waitFor({ timeout: 20000 })
    await opsPage.getByText('配置检查未启用', { exact: true }).waitFor({ timeout: 20000 })
    assert.equal(await opsPage.getByRole('button', { name: '校验配置语法' }).isDisabled(), true)
    const history = await opsPage.evaluate(() => fetch('/proxy/api/v1/operations/history?hours=168').then(response => response.json()))
    assert.equal(history.data.hours, 168)
    await opsPage.getByRole('heading', { name: '持久存储熔断事件' }).waitFor({ timeout: 20000 })
    const circuits = await opsPage.evaluate(() => fetch('/proxy/api/v1/operations/storage-circuit-history?hours=168').then(response => response.json()))
    assert.equal(circuits.data.hours, 168)
    assert.equal(circuits.data.events.length, 5)
    assert.equal(circuits.data.events[0].eventType, 'RECOVERED')

    assert.ok(history.data.samples.length > 0 && history.data.samples.length <= 2017)
    const disabledCheck = await opsPage.evaluate(() => fetch('/proxy/api/v1/operations/nginx/check', { method: 'POST' }).then(response => response.json()))
    assert.equal(disabledCheck.data.status, 'DISABLED')

    const simulated = await opsPage.evaluate(async () => {
      async function request(path, body) {
        const options = body === undefined
          ? undefined
          : {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify(body),
            }
        return fetch(`/proxy/api/v1/${path}`, options).then(response => response.json())
      }
      const database = await request('system/database/overview')
      const quality = await request('system/data-quality/summary')
      const invalidMaintenance = await request('operations/maintenance/enable', { reason: '' })
      await request('operations/maintenance/enable', { reason: '合成演练' })
      const during = await request('operations/overview')
      await request('operations/maintenance/disable', {})
      const after = await request('operations/overview')
      const errors = await request('system-errors?page=1&size=2&level=ERROR')
      const id = errors.data?.list?.[0]?.id
      await request(`system-errors/${id}/status`, { status: 'ACKNOWLEDGED' })
      const detail = await request(`system-errors/${id}`)
      return { database, quality, invalidMaintenance, during, after, errors, detail }
    })
    assert.ok(simulated.database.data?.tables?.length > 0, '数据库概览须有合成表统计')
    assert.ok(simulated.quality.data?.checks?.length > 0, '数据质量须有检查结果')
    assert.equal(simulated.invalidMaintenance.code, 400)
    assert.equal(simulated.during.data?.readiness?.mode, 'READ_ONLY_MAINTENANCE')
    assert.equal(simulated.after.data?.readiness?.mode, 'READ_WRITE')
    assert.equal(simulated.errors.data?.list?.length, 2)
    assert.ok(simulated.errors.data.list.every(item => item.level === 'ERROR'))
    assert.equal(simulated.detail.data?.status, 'ACKNOWLEDGED')

    const monitorPage = await browser.newPage()
    monitorPage.on('pageerror', error => pageErrors.push(error.message))
    await monitorPage.goto(`${appUrl}#/monitoring`)
    await monitorPage.getByText('mrr_demo', { exact: true }).first().waitFor({ timeout: 20_000 })
    await monitorPage.getByText('mr_scan', { exact: true }).first().waitFor({ timeout: 20_000 })

    const statusFrame = monitorPage.locator('iframe[title="MRR 服务可用性状态"]')
    const frameUrl = await statusFrame.evaluate(element => element.src)
    assert.equal(frameUrl, `${appUrl}#/status`, '状态 iframe 必须解析到 Pages 子路径')
    const status = monitorPage.frameLocator('iframe[title="MRR 服务可用性状态"]')
    await status.getByRole('heading', { name: '所有服务正常运行' }).waitFor({ timeout: 25_000 })
    assert.equal(await status.locator('.day-cell').count(), 90, '状态页须渲染90天合成记录')
    assert.equal(await status.locator('.day-cell.is-down').count(), 1, '历史合成故障须可见')
    await status.locator('.day-cell.is-down').click()
    await status.getByText('演示：计划维护期间短暂不可用', { exact: true }).waitFor({ timeout: 15_000 })

    const errorPage = await browser.newPage()
    errorPage.on('pageerror', error => pageErrors.push(error.message))
    await errorPage.goto(`${appUrl}#/runtime-errors`)
    await errorPage.locator('.runtime-errors-table').getByText('DEMO-ERR-001', { exact: true }).waitFor({ timeout: 20_000 })

    const scenarioPage = await browser.newPage()
    scenarioPage.on('pageerror', error => pageErrors.push(error.message))
    await scenarioPage.goto(`${appUrl}#/`)
    const control = scenarioPage.getByRole('region', { name: '演示场景控制' })
    await control.waitFor({ timeout: 20000 })
    // Element Plus overlays its readonly combobox input with the selected label.
    // Click the visible label, which is the actual pointer target.
    await control.getByText('默认综合场景', { exact: true }).click()
    await scenarioPage.getByRole('option', { name: 'OSS 迁移积压' }).click()
    await control.getByRole('button', { name: '应用场景' }).click()
    await Promise.all([
      scenarioPage.waitForEvent('load'),
      scenarioPage.getByRole('button', { name: '确认重新加载' }).click(),
    ])
    await control.waitFor({ timeout: 20000 })
    assert.equal(await scenarioPage.evaluate(() => localStorage.getItem('mrr:pages:scenario:v1')), 'migration-backlog')
    const scenarioStats = () => scenarioPage.evaluate(() => fetch('/proxy/api/v1/oss/migration/statistics').then(response => response.json()))
    assert.equal((await scenarioStats()).data.migratedCount, 0)
    await scenarioPage.reload()
    await control.waitFor({ timeout: 20000 })
    assert.equal((await scenarioStats()).data.migratedCount, 0)
    await control.getByRole('button', { name: '恢复默认' }).click()
    await Promise.all([
      scenarioPage.waitForEvent('load'),
      scenarioPage.getByRole('button', { name: '确认重新加载' }).click(),
    ])
    await control.waitFor({ timeout: 20000 })
    assert.equal(await scenarioPage.evaluate(() => localStorage.getItem('mrr:pages:scenario:v1')), null)
    assert.ok((await scenarioStats()).data.migratedCount > 0)

    assert.deepEqual(outboundApiRequests, [], 'API 请求不能到达 Pages 静态服务器')
    assert.deepEqual(pageErrors, [], '浏览器运行不应出现未处理异常')
    process.stdout.write('Pages 静态演示冒烟通过：子路径、hash 刷新、浏览器 Mock API 及文件预览下载\n')
  }
  finally {
    await browser?.close()
    await new Promise((resolve, reject) => server.httpServer.close(error => error ? reject(error) : resolve()))
  }
}

smokePagesDemo().catch((error) => {
  console.error(error)
  process.exitCode = 1
})

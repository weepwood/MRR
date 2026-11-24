import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import process from 'node:process'
import { chromium } from '@playwright/test'
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
        get('statistics'), get('pending?limit=100'), get('management/waiting-sjh?limit=100'),
        get('pending-folders'), get('readiness?sampleSize=2'),
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
      const req = (path, body) => fetch(`/proxy/api/v1/${path}`, body === undefined ? undefined : {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
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
        get('', { prefix }), get('/url', { key }), get('/url', { key: prefix + 'missing.jpg' }),
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
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
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
        method, ...(body ? { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) } : {}),
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

    // Use the actual bundled browser Mock, not Playwright page.route stubs.
    const opsPage = await browser.newPage()
    opsPage.on('pageerror', error => pageErrors.push(error.message))
    await opsPage.goto(`${appUrl}#/operations-center`)
    await opsPage.locator('.overview-dashboard').getByText('当前允许业务写入。').waitFor({ timeout: 20_000 })
    const simulated = await opsPage.evaluate(async () => {
      async function request(path, body) {
        const options = body === undefined ? undefined : {
          method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
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

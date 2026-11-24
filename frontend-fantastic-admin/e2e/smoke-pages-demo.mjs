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

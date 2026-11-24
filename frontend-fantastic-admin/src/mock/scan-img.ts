import { defineFakeRoute } from 'vite-plugin-fake-server/client'
import { MEDICAL_RECORD_TYPES } from '@/constants/medical-record-types'
import { requiresSjhForBah, toMedicalRecordCodeSearchTerm } from '@/utils/medical-record-code'
import { pageSvg } from '../mock-support/image'
import { demoRecords } from '../mock-support/records'

/**
 * 影像查询页（/idcard/:idCard，views/scan-img）与影像档案袋（/archive，views/statistics/archive）的合成数据。
 *
 * - 身份证号与病案号都是明显的占位值，不对应任何真实患者；
 * - 图片是内联 SVG（data URI），不依赖后端、NAS 或 OSS，Pages 模式的 CSP 下也能显示；
 * - 覆盖：可访问区间内 / 区间外病案、多个类型、计数为 0 的类型、一张损坏的图片。
 *
 * 可访问区间由页面写死为 [00787327, 00855320]（views/scan-img/index.vue）。
 *
 * 用法：pnpm dev:mock 后打开 /idcard/000000200001010001（pages 模式为 /#/idcard/...）；
 * 打开 /idcard/000000200001010002 可验证无病案空态。
 *
 * 档案袋：打开 /archive?bah=00800001（14 张、4 个类型，可全屏看图、改分类、收藏）；
 * 共享扫描记录（如 SJH 90000001）也可查询档案袋；未知编号返回空结果。
 * 档案袋使用自包含合成 SVG，刷新时不得向 data URI 追加 HTTP 缓存参数。
 */

function ok<T>(data: T, message = '操作成功') {
  return { code: 200, message, data, timestamp: new Date().toISOString() }
}

const MOCK_SCAN_IMG_ID_CARD = '000000200001010001'

const records = [
  { id: 9001, bah: '00800001', name: '测试患者甲', department: '心内科', admissionTime: '2019-03-12 09:00:00' },
  { id: 9002, bah: '00800002', name: '测试患者甲', department: '普外科', admissionTime: '2020-06-08 14:30:00' },
  // 区间外：页面应显示为禁用，不能用鼠标或键盘选中
  { id: 9003, bah: '00900001', name: '测试患者甲', department: '骨科', admissionTime: '2021-01-20 10:15:00' },
]

// 每份病案只使用前 4 个类型；其余类型计数为 0，用于验证禁用的类型按钮。
const usedTypes = MEDICAL_RECORD_TYPES.slice(0, 4)
const BROKEN_IMAGE_URL = 'data:image/png;base64,broken'

function buildImages(bah: string, recordIndex: number) {
  const count = recordIndex === 0 ? 14 : 6
  return Array.from({ length: count }, (_, index) => {
    const page = index + 1
    const type = usedTypes[index % usedTypes.length]
    const isBroken = recordIndex === 0 && page === 5
    return {
      id: 90000 + recordIndex * 100 + page,
      brxh: String(990000 + recordIndex),
      bah,
      sjh: `M${String(recordIndex + 1).padStart(3, '0')}`,
      filename: `${String(page).padStart(4, '0')}.jpg`,
      btype: type.value,
      pages: page,
      openerNo: 'scanner-mock',
      uploadDate: '2026-07-13 08:00:00',
      uploadFlag: 1,
      img_url: isBroken ? BROKEN_IMAGE_URL : pageSvg(bah, page, type.label),
    }
  })
}

const imagesByBah = new Map(records.map((record, index) => [record.bah, buildImages(record.bah, index)]))

// ==================== 影像档案袋 ====================
const ARCHIVE_BAH = '00800001'

const archiveImages = Array.from({ length: 14 }, (_, index) => {
  const page = index + 1
  const type = usedTypes[index % usedTypes.length]
  return {
    id: 91000 + page,
    brxh: '991000',
    bah: ARCHIVE_BAH,
    sjh: '',
    filename: `${String(page).padStart(4, '0')}.jpg`,
    btype: type.value,
    pages: page,
    openerNo: 'scanner-mock',
    uploadDate: '2026-07-13 08:00:00',
    uploadFlag: 1,
    img_url: pageSvg(ARCHIVE_BAH, page, type.label),
  }
})

let archiveHistoryId = 1
const archiveHistory: Array<{
  id: number
  bah?: string
  sjh?: string
  success: boolean
  imageCount: number
  queryCount: number
  failureReason?: string
  favorite: boolean
  searchedAt: string
}> = []

function svgMarkup(bah: string, page: number, typeLabel: string) {
  return decodeURIComponent(pageSvg(bah, page, typeLabel).replace('data:image/svg+xml;charset=utf-8,', ''))
}

function sharedArchiveImages(bah: string, sjh: string) {
  return demoRecords.scans.filter((scan) => {
    return (!bah || toMedicalRecordCodeSearchTerm(scan.bah) === bah)
      && (!sjh || toMedicalRecordCodeSearchTerm(scan.sjh) === sjh)
  }).map(scan => ({
    ...scan,
    img_url: pageSvg(scan.bah, scan.id, MEDICAL_RECORD_TYPES.find(item => item.value === scan.btype)?.label ?? '未分类'),
  }))
}

function lookupMeta(reasonCode?: string) {
  return { requestId: `mock${Date.now().toString(16)}`, lookupStrategy: 'LEGACY_CODE', ...(reasonCode ? { reasonCode } : {}) }
}

export default defineFakeRoute([
  // 必须排在 /api/v1/img/:bah 之前，否则 search 会被当成病案号
  {
    url: '/api/v1/img/search',
    response: ({ query }) => {
      const bah = toMedicalRecordCodeSearchTerm(query?.bah)
      const sjh = toMedicalRecordCodeSearchTerm(query?.sjh)
      if (!bah && !sjh) {
        return { ...ok([]), meta: lookupMeta('INVALID_INPUT') }
      }
      if (requiresSjhForBah(bah) && !sjh) {
        return { ...ok([]), meta: lookupMeta('NEEDS_SJH') }
      }
      if (!sjh && bah === toMedicalRecordCodeSearchTerm(ARCHIVE_BAH)) {
        return { ...ok(archiveImages), meta: lookupMeta() }
      }
      const images = sharedArchiveImages(bah, sjh)
      return { ...ok(images), meta: lookupMeta(images.length ? undefined : 'NO_MATCHING_IMAGES') }
    },
  },
  {
    url: '/mock-images/:bah/:page',
    headers: { 'content-type': 'image/svg+xml; charset=utf-8', 'cache-control': 'no-store' },
    response: ({ params }) => {
      const page = Number.parseInt(String(params.page), 10) || 1
      const image = archiveImages.find(item => item.pages === page)
      const label = MEDICAL_RECORD_TYPES.find(item => item.value === image?.btype)?.label ?? '未分类'
      return svgMarkup(String(params.bah), page, label)
    },
  },
  {
    url: '/api/v1/search/patient/:bah',
    response: ({ params }) => {
      const bah = toMedicalRecordCodeSearchTerm(params.bah)
      const patient = demoRecords.patients.find(item => toMedicalRecordCodeSearchTerm(item.bah) === bah)
      if (patient) {
        const { id, name, department, admissiontime } = patient
        return ok([{ id, bah: patient.bah, name, department, admissionTime: admissiontime }])
      }
      return ok(records.filter(item => toMedicalRecordCodeSearchTerm(item.bah) === bah))
    },
  },
  {
    url: '/api/v1/archive-search-history',
    response: () => ok([...archiveHistory].sort((a, b) => b.searchedAt.localeCompare(a.searchedAt))),
  },
  {
    url: '/api/v1/archive-search-history',
    method: 'POST',
    response: ({ body }) => {
      const existing = archiveHistory.find(item => item.bah === body?.bah && item.sjh === body?.sjh && item.success === body?.success)
      if (existing) {
        existing.queryCount += 1
        existing.searchedAt = new Date().toISOString()
        return ok(existing)
      }
      const item = { favorite: false, queryCount: 1, imageCount: 0, success: true, ...body, id: archiveHistoryId++, searchedAt: new Date().toISOString() }
      archiveHistory.push(item)
      return ok(item)
    },
  },
  {
    url: '/api/v1/archive-search-history/:id/favorite',
    method: 'PUT',
    response: ({ params, body }) => {
      const item = archiveHistory.find(entry => entry.id === Number(params.id))
      if (!item) {
        return { code: 404, message: '模拟历史记录不存在', data: null }
      }
      item.favorite = Boolean(body?.favorite)
      return ok('ok')
    },
  },
  {
    url: '/api/v1/search/getBAHByID/:idCard',
    response: ({ params }) => {
      const idCard = String(params.idCard)
      if (idCard === MOCK_SCAN_IMG_ID_CARD) {
        return ok(records)
      }
      // 其余身份证号返回空列表，用于验证"无病案"空态
      return ok([])
    },
  },
  {
    url: '/api/v1/img/:bah',
    response: ({ params }) => {
      const bah = String(params.bah)
      // 非纯数字（如未知子路径）按未定义接口处理
      if (!/^\d+$/.test(bah)) {
        return { code: 404, message: '模拟接口未定义', data: null }
      }
      return ok(imagesByBah.get(bah) ?? [])
    },
  },
  {
    url: '/api/v1/img/updateImageType/:id',
    method: 'PUT',
    response: ({ params, body }) => {
      const id = Number(params.id)
      const btype = Number(body?.btype)
      const scan = demoRecords.scans.find(item => item.id === id)
      if (scan && Number.isFinite(btype)) {
        scan.btype = btype
        return ok(null, '图片类型已更新（模拟）')
      }
      for (const list of [...imagesByBah.values(), archiveImages]) {
        const target = list.find(item => item.id === id)
        if (target && Number.isFinite(btype)) {
          target.btype = btype
          return ok(null, '图片类型已更新（模拟）')
        }
      }
      return { code: 404, message: '模拟图片不存在', data: null }
    },
  },
])

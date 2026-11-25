import JSZip from 'jszip'
import { getMedicalRecordTypeLabel } from '@/constants/medical-record-types'
import { createCsvText } from '@/utils/file-download'
import { demoArchiveBoxStore } from './archive-box-store'
import { demoRecords, matchesLegacyScan } from './records'
import { demoError, demoOk } from './response'

type Query = Record<string, unknown>
type Cell = string | number | null | undefined
const headers: Record<string, string[]> = {
  'patients': ['bah', 'name', 'idcard', 'ruyuan', 'admissiontime', 'department', 'bingqu', 'chuangwei'],
  'statistics': ['bah', 'cid', 'openerno', 'date', 'type', 'pages', 'sjh', 'patientname', 'inpatientdepartment', 'patientid', 'dischargedate'],
  'archives': ['id', 'sjh', 'bah', 'patient_id', 'patient_name', 'inpatient_department', 'device_id', 'operator_no', 'archive_date', 'discharge_date', 'archive_type', 'page_count', 'source_statistics_id'],
  'archive-boxes': ['bah', 'sjh', 'box_no', 'expected_box_no', 'status', 'remark'],
  'scan': ['sjh', 'bah', 'brxh', 'folder', 'filename', 'btype', 'filesize'],
}
const filterKeys: Record<string, string[]> = {
  'patients': ['keyword'],
  'statistics': ['keyword', 'bah', 'sjh', 'type', 'startDate', 'endDate'],
  'archives': ['keyword', 'bah', 'sjh', 'patientId', 'type', 'startDate', 'endDate'],
  'archive-boxes': ['keyword', 'bah', 'sjh', 'boxNo', 'status'],
  'scan': ['bah', 'sjh', 'brxh', 'folder', 'filename', 'btype', 'afterId'],
}
function own(key: string) { return Object.hasOwn(headers, key) }
const term = (value: unknown) => String(value ?? '').trim().toLowerCase()
const includes = (value: unknown, query: unknown) => term(value).includes(term(query))
function dates(date: string, query: Query) { return (!query.startDate || date >= String(query.startDate)) && (!query.endDate || date <= String(query.endDate)) }
function patientRows(query: Query): Cell[][] {
  return demoRecords.patients.filter(p => includes([p.bah, p.name, p.idCard, p.department].join(' '), query.keyword))
    .map(p => [p.bah, p.name, '', p.admissiontime.slice(0, 10), p.admissiontime, p.department, '', ''])
}
function rows(dataset: string, query: Query): Cell[][] {
  const { scans, patients } = demoRecords
  if (dataset === 'patients') { return patientRows(query) }
  if (dataset === 'scan') {
    return scans.filter(s => s.id > Number(query.afterId || 0) && matchesLegacyScan(s, query))
      .map(s => [s.sjh, s.bah, s.brxh, s.folder, s.filename, s.btype, s.fileSize])
  }
  if (dataset === 'archive-boxes') {
    return demoArchiveBoxStore.list(query).map(b => [b.bah, b.sjh, b.boxNo, b.expectedBoxNo, b.status, b.remark])
  }
  if (dataset === 'statistics') {
    return scans.filter(s => includes(s.bah, query.bah) && includes(s.sjh, query.sjh)
      && includes([s.bah, s.sjh, s.openerNo, getMedicalRecordTypeLabel(s.btype)].join(' '), query.keyword)
      && (!query.type || term(query.type) === term(getMedicalRecordTypeLabel(s.btype))) && dates(s.uploadDate.slice(0, 10), query))
      .map((s) => {
        const p = patients.find(p => p.id === s.archiveId)
        return [s.bah, `CID-${String(s.id).padStart(5, '0')}`, s.openerNo, s.uploadDate.slice(0, 10), getMedicalRecordTypeLabel(s.btype), s.pages, s.sjh, p?.name, p?.department, p?.id, '']
      })
  }
  return patients.flatMap((p) => {
    const active = scans.filter(s => s.archiveId === p.id && s.uploadFlag !== 0)
    const first = active[0]
    const date = first?.uploadDate.slice(0, 10) ?? ''
    const type = first ? getMedicalRecordTypeLabel(first.btype) : ''
    if (!includes(p.bah, query.bah) || !includes(first?.sjh, query.sjh) || !includes(p.id, query.patientId)
      || !includes([p.bah, first?.sjh, p.name, p.department].join(' '), query.keyword)
      || (query.type && term(query.type) !== term(type)) || !dates(date, query)) { return [] }
    return [[p.id, first?.sjh, p.bah, p.id, p.name, p.department, '', first?.openerNo, date, '', type, active.reduce((n, s) => n + s.pages, 0), '']]
  })
}
export function demoTemplate(dataset: string) {
  if (!own(dataset) || dataset === 'archives') { return demoError(404, '未登记的演示模板') }
  return demoOk({ text: createCsvText(headers[dataset], []), name: `mrr-demo-${dataset}-template.csv` })
}
export function demoCsv(dataset: string, query: Query = {}) {
  if (!own(dataset)) { return demoError(404, '未登记的演示数据集') }
  if (Object.entries(query).some(([key, value]) => !filterKeys[dataset].includes(key) || (typeof value !== 'string' && typeof value !== 'number') || String(value).length > 256)
    || (query.afterId != null && (!/^\d+$/.test(String(query.afterId)) || !Number.isSafeInteger(Number(query.afterId))))
    || (query.btype != null && !/^(?:\d|1[0-5])$/.test(String(query.btype)))) { return demoError(400, '无效演示导出条件') }
  return demoOk({ text: createCsvText(headers[dataset], rows(dataset, query)), name: `mrr-demo-${dataset}.csv` })
}

function xml(value: Cell) {
  const clean = Array.from(String(value ?? '')).filter((char) => {
    const code = char.codePointAt(0)!
    return code === 9 || code === 10 || code === 13 || (code >= 32 && code <= 0xD7FF) || (code >= 0xE000 && code <= 0xFFFD) || code >= 0x10000
  }).join('')
  return clean.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}
// Small, fixed demo-only OOXML workbook; no formulas, macros, external links or styles.
export async function demoPatientWorkbook(query: Query = {}) {
  if (Object.entries(query).some(([k, v]) => k !== 'keyword' || typeof v !== 'string' || v.length > 256)) { return demoError(400, '无效演示导出条件') }
  const zip = new JSZip()
  zip.file('[Content_Types].xml', '<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>')
  const rel = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships'
  const pkg = 'http://schemas.openxmlformats.org/package/2006/relationships'
  const main = 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'
  zip.file('_rels/.rels', `<Relationships xmlns="${pkg}"><Relationship Id="rId1" Type="${rel}/officeDocument" Target="xl/workbook.xml"/></Relationships>`)
  zip.file('xl/workbook.xml', `<workbook xmlns="${main}" xmlns:r="${rel}"><sheets><sheet name="合成演示患者" sheetId="1" r:id="rId1"/></sheets></workbook>`)
  zip.file('xl/_rels/workbook.xml.rels', `<Relationships xmlns="${pkg}"><Relationship Id="rId1" Type="${rel}/worksheet" Target="worksheets/sheet1.xml"/></Relationships>`)
  const sheet = [headers.patients, ...patientRows(query)].map((row, index) => `<row r="${index + 1}">${row.map((cell, col) => `<c r="${String.fromCharCode(65 + col)}${index + 1}" t="inlineStr"><is><t xml:space="preserve">${xml(cell)}</t></is></c>`).join('')}</row>`).join('')
  zip.file('xl/worksheets/sheet1.xml', `<worksheet xmlns="${main}"><sheetData>${sheet}</sheetData></worksheet>`)
  return demoOk({ base64: await zip.generateAsync({ type: 'base64', compression: 'DEFLATE' }), name: 'mrr-demo-patients.xlsx' })
}

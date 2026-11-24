import type { ClassificationBundle, ClassificationVersion } from '@/api/modules/classification'
import { MEDICAL_RECORD_TYPE_CODES } from '@/constants/medical-record-types'
import { demoError, demoOk } from './response'

const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T
export function createDemoClassificationCatalog() {
  const versions: Array<ClassificationVersion & { bundle: ClassificationBundle }> = [{
    version: 'demo-rules-1',
    name: '合成病案规则',
    status: 'PUBLISHED',
    bundle: { name: '合成病案规则', rules: [{ targetType: 1, keyword: '合成影像', matchScope: 'ANY', matchMode: 'CONTAINS', weight: 1, negative: false, required: false, priority: 100, enabled: true }], lexicon: [] },
  }]
  let sequence = 2
  function valid(bundle: ClassificationBundle) {
    return bundle && typeof bundle.name === 'string' && bundle.name.trim().length > 0 && bundle.name.length <= 128
      && Array.isArray(bundle.rules) && bundle.rules.length <= 200 && Array.isArray(bundle.lexicon) && bundle.lexicon.length <= 200
      && bundle.rules.every(r => r && MEDICAL_RECORD_TYPE_CODES.includes(r.targetType) && typeof r.keyword === 'string' && r.keyword.trim().length > 0 && r.keyword.length <= 500
        && ['TITLE', 'BODY', 'ANY'].includes(r.matchScope) && ['EXACT', 'CONTAINS'].includes(r.matchMode) && Number.isFinite(r.weight) && r.weight > 0 && r.weight <= 1000)
      && bundle.lexicon.every(l => l && typeof l.term === 'string' && l.term.trim().length > 0 && Array.isArray(l.aliases) && Array.isArray(l.commonOcrErrors))
  }
  function create(bundle: ClassificationBundle) {
    if (!valid(bundle)) {
      return demoError(400, '演示规则无效；仅支持完全匹配与包含，不执行正则')
    }
    if (versions.length >= 50) {
      return demoError(409, '演示版本数量达到上限')
    }
    const version = `demo-rules-${sequence++}`
    versions.unshift({ version, name: bundle.name, status: 'DRAFT', bundle: clone(bundle) })
    return demoOk(version)
  }
  return {
    list: () => demoOk(versions.map(({ version, name, status }) => ({ version, name, status }))),
    get(version: string) {
      const row = versions.find(v => v.version === version)
      return row ? demoOk(clone(row.bundle)) : demoError(404, '演示规则版本不存在')
    },
    create,
    save(version: string, bundle: ClassificationBundle) {
      const row = versions.find(v => v.version === version)
      if (!row || row.status !== 'DRAFT') {
        return demoError(409, '仅草稿允许修改')
      }
      if (!valid(bundle)) {
        return demoError(400, '演示规则无效或使用了不支持的正则')
      }
      row.bundle = clone(bundle)
      row.name = bundle.name
      return demoOk(null)
    },
    publish(version: string) {
      const row = versions.find(v => v.version === version)
      if (!row || row.status !== 'DRAFT' || !row.bundle.rules.length) {
        return demoError(409, '仅包含有效规则的草稿允许演示发布')
      }
      versions.forEach((v) => { if (v.status === 'PUBLISHED') { v.status = 'ARCHIVED' } })
      row.status = 'PUBLISHED'
      return demoOk(null)
    },
    restore(version: string) {
      const row = versions.find(v => v.version === version)
      return row ? create(row.bundle) : demoError(404, '演示规则版本不存在')
    },
    test(version: string, text: string | null) {
      const row = versions.find(v => v.version === version)
      if (!row || !text) {
        return demoError(409, '请先完成合成影像 OCR 并选择已有规则版本')
      }
      const matches = row.bundle.rules.filter(r => r.enabled && !r.negative && !r.required && r.matchScope === 'ANY'
        && (r.matchMode === 'EXACT' ? text === r.keyword : text.includes(r.keyword)))
      const types = [...new Set(matches.map(r => r.targetType))]
      return demoOk({ targetType: types.length === 1 ? types[0] : null, score: types.length === 1 ? matches.reduce((n, r) => n + r.weight, 0) : 0, reason: 'DEMO_ONLY：仅演示简单关键词匹配，不代表生产分类引擎结果' })
    },
  }
}

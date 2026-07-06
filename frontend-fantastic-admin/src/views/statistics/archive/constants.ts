import type { TypeOption, TypeStatItem } from './types'

export const TYPE_OPTIONS: TypeOption[] = [
  { value: 1, label: '01-病案首页' },
  { value: 2, label: '02-病程记录' },
  { value: 3, label: '03-手术记录' },
  { value: 4, label: '04-术后病程' },
  { value: 5, label: '05-护理记录' },
  { value: 6, label: '06-会诊单' },
  { value: 7, label: '07-特殊检查' },
  { value: 8, label: '08-检验单' },
  { value: 9, label: '09-医嘱' },
  { value: 10, label: '10-体温单' },
  { value: 12, label: '12-出院记录' },
  { value: 13, label: '13-大病历' },
  { value: 14, label: '14-其它' },
]

export const TYPE_OPTION_MAP = new Map<number, TypeOption>(
  TYPE_OPTIONS.map(item => [item.value, item]),
)

export const MIN_BAH_LENGTH = 8

export function getTypeLabel(type?: number | string | null): string {
  const numericType = Number(type)
  return TYPE_OPTION_MAP.get(numericType)?.label || (type ? `类型 ${type}` : '未分类')
}

export function normalizeText(value: unknown): string {
  const text = String(value ?? '').trim()
  return text && text.toUpperCase() !== 'NULL' ? text : '-'
}

export function formatDate(value: string | undefined): string {
  if (!value) {
    return '-'
  }
  return String(value).replace(/\//g, '-')
}

export function padCode(value: string): string {
  const trimmed = value.trim()
  if (trimmed.length > 0 && trimmed.length < MIN_BAH_LENGTH && /^\d+$/.test(trimmed)) {
    return trimmed.padStart(MIN_BAH_LENGTH, '0')
  }
  return trimmed
}

export function buildTypeStats(images: { btype?: number | null }[]): TypeStatItem[] {
  const counts = new Map<number, number>()
  for (const item of images) {
    const type = Number(item.btype || 0)
    counts.set(type, (counts.get(type) || 0) + 1)
  }
  return TYPE_OPTIONS.map(item => ({ ...item, count: counts.get(item.value) || 0 }))
}

export function resolveImageUrl(item: { ossUrl?: string, img_url?: string }): string {
  return item.ossUrl || item.img_url || ''
}

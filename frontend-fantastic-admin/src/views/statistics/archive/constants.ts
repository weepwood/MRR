import type { TypeOption, TypeStatItem } from './types'
import {
  getMedicalRecordTypeLabel,
  MEDICAL_RECORD_TYPES,
} from '@/constants/medical-record-types'
import { MEDICAL_RECORD_CODE_LENGTH, normalizeMedicalRecordCode } from '@/utils/medical-record-code'

export const TYPE_OPTIONS: TypeOption[] = MEDICAL_RECORD_TYPES.map(item => ({ ...item }))

export const TYPE_OPTION_MAP = new Map<number, TypeOption>(
  TYPE_OPTIONS.map(item => [item.value, item]),
)

export const MIN_BAH_LENGTH = MEDICAL_RECORD_CODE_LENGTH
const ARCHIVE_IMAGE_VERSION_STORAGE_KEY = 'MRR-ADMIN:archive-image-versions'

export function getTypeLabel(type?: number | string | null): string {
  return type ? getMedicalRecordTypeLabel(type) : '未分类'
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
  return normalizeMedicalRecordCode(value)
}

export function buildTypeStats(images: { btype?: number | null }[]): TypeStatItem[] {
  const counts = new Map<number, number>()
  for (const item of images) {
    const type = Number(item.btype || 0)
    counts.set(type, (counts.get(type) || 0) + 1)
  }
  return TYPE_OPTIONS.map(item => ({ ...item, count: counts.get(item.value) || 0 }))
}

/**
 * img_url 由后端按照系统 imageSource 设置生成；ossUrl 仅作为旧响应兼容兜底。
 */
export function resolveImageUrl(item: { ossUrl?: string, img_url?: string }, cacheBuster?: number): string {
  const url = item.img_url || item.ossUrl || ''
  if (!url || !cacheBuster) {
    return url
  }
  return `${url}${url.includes('?') ? '&' : '?'}_=${cacheBuster}`
}

function imageVersionKey(bah: string, sjh: string): string {
  return `${bah || 'none'}:${sjh || 'none'}`
}

function readImageVersions(): Record<string, number> {
  try {
    const raw = localStorage.getItem(ARCHIVE_IMAGE_VERSION_STORAGE_KEY)
    const parsed = raw ? JSON.parse(raw) : {}
    if (!parsed || typeof parsed !== 'object') {
      return {}
    }
    const versions: Record<string, number> = {}
    Object.entries(parsed).forEach(([key, value]) => {
      const version = Number(value)
      if (Number.isSafeInteger(version) && version > 0) {
        versions[key] = version
      }
    })
    return versions
  }
  catch {
    return {}
  }
}

export function readArchiveImageVersion(bah: string, sjh: string): number | undefined {
  return readImageVersions()[imageVersionKey(bah, sjh)]
}

export function writeArchiveImageVersion(bah: string, sjh: string, version: number): void {
  try {
    localStorage.setItem(ARCHIVE_IMAGE_VERSION_STORAGE_KEY, JSON.stringify({
      ...readImageVersions(),
      [imageVersionKey(bah, sjh)]: version,
    }))
  }
  catch {
    // 本地存储不可用时，当前页面仍会使用新图片版本。
  }
}

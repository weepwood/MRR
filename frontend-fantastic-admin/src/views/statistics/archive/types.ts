import type { BAHImageData, BAHRecord } from '@/api/types'

export interface GalleryImage extends BAHImageData {
  imageUrl?: string
  archiveId?: number
  predictedBtype?: number | null
  classificationConfidence?: number | null
  classificationState?: 'UNPROCESSED' | 'PROCESSING' | 'SUGGESTED' | 'NO_MATCH' | 'CONFIRMED' | 'REJECTED' | 'FAILED'
  classificationSource?: string
  classificationModelVersion?: string
  classificationOcrTitle?: string
}

export type ViewMode = 'thumb' | 'list'

export interface TypeOption {
  value: number
  label: string
}

export interface TypeStatItem extends TypeOption {
  count: number
}

export interface PatientInfo extends BAHRecord {}

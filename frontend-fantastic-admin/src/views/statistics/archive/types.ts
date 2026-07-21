import type { BAHImageData, BAHRecord } from '@/api/types'

export interface GalleryImage extends BAHImageData {
  imageUrl?: string
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

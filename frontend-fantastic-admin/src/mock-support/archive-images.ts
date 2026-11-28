import { MEDICAL_RECORD_TYPES } from '@/constants/medical-record-types'
import { pageSvg } from './image'

const usedTypes = MEDICAL_RECORD_TYPES.slice(0, 4)
export const ARCHIVE_BAH = '00800001'

export const archiveImages = Array.from({ length: 14 }, (_, index) => {
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

// Separate synthetic stress dossier. Build lazily so ordinary Mock startup stays cheap.
export const LARGE_ARCHIVE_BAH = '00800999'
let largeImages: typeof archiveImages | undefined
export function getLargeArchiveImages() {
  largeImages ??= Array.from({ length: 600 }, (_, index) => {
    const page = index + 1
    const type = usedTypes[index % usedTypes.length]
    return {
      ...archiveImages[0],
      id: 990600000 + page,
      bah: LARGE_ARCHIVE_BAH,
      brxh: 'synthetic-large',
      filename: `synthetic-${page}.jpg`,
      btype: type.value,
      pages: page,
      img_url: pageSvg(LARGE_ARCHIVE_BAH, page, type.label),
    }
  })
  return largeImages
}

export function findLargeArchiveImage(id: number) {
  return largeImages?.find(image => image.id === id)
}

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

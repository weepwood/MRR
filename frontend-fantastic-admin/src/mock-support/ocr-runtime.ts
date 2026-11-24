import { createOcrStore } from './ocr-jobs'
import { demoRecords } from './records'

export const demoOcrStore = createOcrStore(demoRecords.scans)

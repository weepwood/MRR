import { createArchiveBoxStore } from './archive-boxes'
import { demoRecords } from './records'

export const demoArchiveBoxStore = createArchiveBoxStore(demoRecords.scans)

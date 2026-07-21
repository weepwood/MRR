import type { ImageAuditCountItem } from '@/api/types'

declare module '@/api/types' {
  interface LogRecord {
    bah?: string
    sjh?: string
    patientId?: string
  }

  interface ImageAuditAnalytics {
    topTargets: ImageAuditCountItem[]
  }
}

export {}

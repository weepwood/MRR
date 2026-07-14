import { getRequest } from '../index'

export type SystemAvailabilityState = 'UP' | 'DOWN' | 'NO_DATA'

export interface SystemStatusSummary {
  currentStatus: SystemAvailabilityState
  currentStatusSince?: string | null
  lastCheckedAt?: string | null
  rangeStartedAt: string
  rangeEndedAt: string
  days: number
  uptimePercentage?: number | null
  monitoredSeconds: number
  downtimeSeconds: number
}

export interface DailySystemAvailability {
  date: string
  status: SystemAvailabilityState
  uptimePercentage?: number | null
  monitoredSeconds: number
  downtimeSeconds: number
}

export interface SystemStatusIncident {
  startedAt: string
  endedAt?: string | null
  durationSeconds: number
  ongoing: boolean
  reason: string
}

const publicRequestConfig = {
  skipGlobalError: true,
  skipResponseMetrics: true,
}

export function getSystemStatusSummary(days = 90) {
  return getRequest<SystemStatusSummary>('/api/v1/public/status/summary', {
    ...publicRequestConfig,
    params: { days },
  })
}

export function getDailySystemAvailability(days = 90) {
  return getRequest<DailySystemAvailability[]>('/api/v1/public/status/daily', {
    ...publicRequestConfig,
    params: { days },
  })
}

export function getSystemStatusIncidents(days = 90) {
  return getRequest<SystemStatusIncident[]>('/api/v1/public/status/incidents', {
    ...publicRequestConfig,
    params: { days },
  })
}

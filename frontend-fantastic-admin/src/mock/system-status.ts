import type { DailySystemAvailability, MinuteSystemAvailability, SystemStatusIncident, SystemStatusSummary } from '@/api/modules/system-status'
import dayjs from 'dayjs'
import { defineFakeRoute } from 'vite-plugin-fake-server/client'
import { boundedInt, demoError, demoOk } from '../mock-support/response'

// The same fixed session clock and synthetic outage drive all four endpoints.
// No real monitoring service, address or machine information is accessed.
const reference = dayjs().startOf('minute')
const outageStart = reference.subtract(3, 'day').startOf('day').add(10, 'hour')
const outageEnd = outageStart.add(15, 'minute')
const incident: SystemStatusIncident = {
  startedAt: outageStart.toISOString(),
  endedAt: outageEnd.toISOString(),
  durationSeconds: 900,
  ongoing: false,
  reason: '演示：计划维护期间短暂不可用',
}

function dailyAvailability(days: number): DailySystemAvailability[] {
  return Array.from({ length: days }, (_, index) => {
    const start = reference.startOf('day').subtract(days - index - 1, 'day')
    const end = start.add(1, 'day')
    const monitoredSeconds = Math.max(0, Math.min(end.valueOf(), reference.valueOf()) - start.valueOf()) / 1000
    const downtimeSeconds = Math.max(0, Math.min(end.valueOf(), outageEnd.valueOf(), reference.valueOf()) - Math.max(start.valueOf(), outageStart.valueOf())) / 1000
    return {
      date: start.format('YYYY-MM-DD'),
      status: monitoredSeconds === 0 ? 'NO_DATA' : downtimeSeconds ? 'DOWN' : 'UP',
      uptimePercentage: monitoredSeconds ? (monitoredSeconds - downtimeSeconds) * 100 / monitoredSeconds : null,
      monitoredSeconds,
      downtimeSeconds,
    }
  })
}

function summary(days: number): SystemStatusSummary {
  const daily = dailyAvailability(days)
  const monitoredSeconds = daily.reduce((sum, day) => sum + day.monitoredSeconds, 0)
  const downtimeSeconds = daily.reduce((sum, day) => sum + day.downtimeSeconds, 0)
  return {
    currentStatus: 'UP',
    currentStatusSince: outageEnd.toISOString(),
    lastCheckedAt: reference.toISOString(),
    rangeStartedAt: reference.startOf('day').subtract(days - 1, 'day').toISOString(),
    rangeEndedAt: reference.toISOString(),
    days,
    monitoredSeconds,
    downtimeSeconds,
    uptimePercentage: monitoredSeconds ? (monitoredSeconds - downtimeSeconds) * 100 / monitoredSeconds : null,
  }
}

function minutesFor(date: string): MinuteSystemAvailability[] | undefined {
  const start = dayjs(date).startOf('day')
  if (!start.isValid() || start.format('YYYY-MM-DD') !== date
    || start.isAfter(reference.startOf('day')) || start.isBefore(reference.startOf('day').subtract(89, 'day'))) {
    return undefined
  }
  return Array.from({ length: 1440 }, (_, index) => {
    const minute = start.add(index, 'minute')
    return {
      startedAt: minute.toISOString(),
      status: !minute.isBefore(reference) ? 'NO_DATA' : !minute.isBefore(outageStart) && minute.isBefore(outageEnd) ? 'DOWN' : 'UP',
    }
  })
}

export default defineFakeRoute([
  { url: '/api/v1/public/status/summary', response: ({ query }) => demoOk(summary(boundedInt(query?.days, 90, 90))) },
  { url: '/api/v1/public/status/daily', response: ({ query }) => demoOk(dailyAvailability(boundedInt(query?.days, 90, 90))) },
  {
    url: '/api/v1/public/status/minutes',
    response: ({ query }) => {
      const minutes = minutesFor(String(query?.date ?? reference.format('YYYY-MM-DD')))
      return minutes ? demoOk(minutes) : demoError(400, '日期不在演示记录范围内')
    },
  },
  { url: '/api/v1/public/status/incidents', response: ({ query }) => demoOk(boundedInt(query?.days, 90, 90) >= 4 ? [incident] : []) },
])

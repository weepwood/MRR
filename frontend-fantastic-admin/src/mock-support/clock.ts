import dayjs from 'dayjs'

// Capture one reference per demo session. Relative fixtures stay useful on
// future deployments and all related tables share the same calendar.
export function createDemoClock(reference: Date = new Date()) {
  const now = dayjs(reference).startOf('minute')
  return {
    date(daysAgo = 0) {
      return now.subtract(daysAgo, 'day').format('YYYY-MM-DD')
    },
    dateTime(daysAgo = 0, hoursAgo = 0, minutesAgo = 0) {
      return now.subtract(daysAgo, 'day').subtract(hoursAgo, 'hour').subtract(minutesAgo, 'minute').format('YYYY-MM-DD HH:mm:ss')
    },
    iso(daysAgo = 0, hoursAgo = 0) {
      return now.subtract(daysAgo, 'day').subtract(hoursAgo, 'hour').toISOString()
    },
  }
}

export const demoClock = createDemoClock()

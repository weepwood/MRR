import { defineFakeRoute } from 'vite-plugin-fake-server/client'
import { createDemoPatientAnalytics } from '../mock-support/patient-analytics'
import { demoRecords } from '../mock-support/records'
import { demoOk } from '../mock-support/response'

const analytics = createDemoPatientAnalytics(demoRecords.patients)

export default defineFakeRoute([
  { url: '/api/v1/patients/analytics/summary', response: ({ query }) => analytics.summary(query.year) },
  { url: '/api/v1/patients/analytics/missing-idcard', response: ({ query }) => analytics.missing(query.page, query.size) },
  { url: '/api/v1/patients/analytics/multi-record-groups', response: ({ query }) => analytics.groups(query.page, query.size, query.includeSuspected !== 'false') },
  { url: '/api/v1/statistics/departments', response: () => demoOk(analytics.departments()) },
])

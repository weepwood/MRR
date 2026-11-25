import { defineFakeRoute } from 'vite-plugin-fake-server/client'
import { demoCsv, demoPatientWorkbook, demoTemplate } from '../mock-support/data-exchange'

export default defineFakeRoute([
  { url: '/api/v1/data-exchange/:dataset/template', response: ({ params }) => demoTemplate(String(params.dataset)) },
  { url: '/api/v1/data-exchange/:dataset/export/csv', response: ({ params, query }) => demoCsv(String(params.dataset), query) },
  { url: '/api/v1/patients/export/excel', response: ({ query }) => demoPatientWorkbook(query) },
])

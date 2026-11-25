import { defineFakeRoute } from 'vite-plugin-fake-server/client'
import { demoError } from '../mock-support/response'

// These routes are deliberately unavailable: a static demo cannot issue a real
// authentication ticket, change credentials, or confirm a medical data import.
const disabled = () => demoError(503, '静态演示不执行真实认证、账号管理或医疗数据写入')
export default defineFakeRoute([
  { url: '/api/v1/auth/password/required-change', method: 'POST', response: disabled },
  { url: '/api/v1/auth/users', method: 'POST', response: disabled },
  { url: '/api/v1/auth/users/:id/password/reset', method: 'POST', response: disabled },
  { url: '/api/v1/auth/users/:id/registration/approve', method: 'POST', response: disabled },
  { url: '/api/v1/auth/users/:id/registration/reject', method: 'POST', response: disabled },
  { url: '/api/v1/patients/:id', method: 'PUT', response: disabled },
  { url: '/api/v1/patients/import', method: 'POST', response: disabled },
  { url: '/api/v1/data-exchange/:dataset/import', method: 'POST', response: disabled },
  { url: '/api/v1/search/getBAHByEncryptID', response: disabled },
  { url: '/api/v1/search/archive-cases', method: 'POST', response: disabled },
  { url: '/api/v1/search/archive-cases', response: disabled },
  { url: '/api/v1/external/archive/session', method: 'POST', response: disabled },
  { url: '/api/v1/external/archive/context', response: disabled },
  { url: '/api/v1/external/archive/images', response: disabled },
  { url: '/api/v1/external/archive/download', statusCode: 503, response: disabled },
])

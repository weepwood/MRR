import api from '@/api'
import { normalizeMedicalRecordCodeFields } from '@/utils/medical-record-code'

let installed = false

/**
 * 统一处理前端所有 API 请求与响应中的 bah / sjh 字段。
 *
 * 请求时补齐 8 位，保证新建、更新和查询参数格式一致；响应时再次兜底，
 * 使所有页面无需分别实现格式化逻辑即可统一显示 8 位编码。
 */
export function installMedicalRecordCodeInterceptors() {
  if (installed) {
    return
  }
  installed = true

  api.interceptors.request.use((request) => {
    if (request.params) {
      request.params = normalizeMedicalRecordCodeFields(request.params)
    }
    if (request.data) {
      request.data = normalizeMedicalRecordCodeFields(request.data)
    }
    return request
  })

  api.interceptors.response.use(payload => normalizeMedicalRecordCodeFields(payload))
}

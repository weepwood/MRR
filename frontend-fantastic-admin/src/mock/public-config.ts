import { defineFakeRoute } from 'vite-plugin-fake-server/client'
import { DEFAULT_LOGIN_PAGE_SETTINGS } from '@/api/modules/login-page-settings'
import { demoError, demoOk } from '../mock-support/response'

export default defineFakeRoute([
  {
    url: '/api/v1/public/config/login-page',
    response: () => demoOk({
      ...DEFAULT_LOGIN_PAGE_SETTINGS,
      loginEnvironmentLabel: '静态演示环境',
      loginFormDescription: '使用演示账号体验工作区，不连接医院数据库。',
      loginHelpText: '演示仅使用合成数据，不提交真实医疗信息。',
      loginFooterText: '静态演示 · 不连接医院内网',
      systemAdminContactVisible: false,
    }),
  },
  {
    url: '/api/v1/public/config/client-environment',
    // No actual server behind Pages. Do not return a fabricated client IP or online backend.
    response: () => demoError(503, '演示不提供真实后端或客户端地址探测'),
  },
  {
    url: '/api/v1/public/config/documentation',
    // Do not advertise internal documentation endpoints that are not packaged with this site.
    response: () => demoOk({
      documentationUserGuideUrl: '',
      documentationDeveloperUrl: '',
      documentationOperationsUrl: '',
    }),
  },
  {
    url: '/api/v1/documentation/session',
    method: 'POST',
    response: () => demoError(503, '演示不建立正式文档授权会话'),
  },
])

export const adminSettingsStorageKey = 'admin-dashboard-settings'

export const adminDefaultSettings = {
  systemName: '病案管理系统',
  maxFileSize: 10,
  sessionTimeout: 30,
  logLevel: 'info',
  swaggerUrl: '/swagger-ui/index.html'
}

export const adminSectionMetaMap = {
  users: {
    title: '用户管理',
    description: '管理账户权限、状态和最近登录信息。',
    pill: '管理模块'
  },
  records: {
    title: '病案管理',
    description: '查看、编辑与维护病案记录。',
    pill: '业务模块'
  },
  testing: {
    title: '系统测试',
    description: '执行接口请求和功能验证，检查系统可用性。',
    pill: '测试模块'
  },
  logs: {
    title: '系统日志',
    description: '追踪关键操作、告警与后台事件。',
    pill: '审计模块'
  },
  monitoring: {
    title: '系统监控',
    description: '查看 CPU、内存、磁盘和网络的运行状态。',
    pill: '监控模块'
  },
  settings: {
    title: '系统设置',
    description: '配置系统参数、安全策略和通知规则。',
    pill: '配置模块'
  }
}

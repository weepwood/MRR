import { defineConfig } from 'vitepress'
import { MermaidMarkdown, MermaidPlugin } from 'vitepress-plugin-mermaid'

export default defineConfig({
  title: 'MRR 文档',
  description: '医疗影像记录管理系统 — 基于 Spring Boot 4 + Vue 3 的现代化医疗影像管理解决方案',
  lang: 'zh-CN',

  head: [
    ['link', { rel: 'icon', href: '/logo.svg' }],
    ['meta', { name: 'theme-color', content: '#0a7c42' }],
    ['meta', { name: 'apple-mobile-web-app-capable', content: 'yes' }],
    ['meta', { property: 'og:title', content: 'MRR 医疗影像记录管理系统' }],
    ['meta', { property: 'og:description', content: '基于 Spring Boot 4 + Vue 3 的现代化医疗影像管理解决方案' }],
    ['meta', { property: 'og:type', content: 'website' }],
  ],

  themeConfig: {
    logo: '/logo.svg',
    siteTitle: 'MRR 文档',

    nav: [
      { text: '首页', link: '/' },
      { text: '快速开始', link: '/getting-started/installation' },
      { text: '项目概览', link: '/ai-generation/项目概览/项目概览' },
      { text: '系统架构', link: '/ai-generation/系统架构/系统架构' },
      { text: 'API 文档', link: '/ai-generation/后端API文档/后端API文档' },
      { text: '开发指南', link: '/ai-generation/开发指南/开发指南' },
      { text: '用户指南', link: '/user-guide/index' },
      { text: '运维指南', link: '/maintenance/index' },
    ],

    sidebar: {
      '/getting-started/': [
        {
          text: '快速开始', collapsed: false,
          items: [
            { text: '安装指南', link: '/getting-started/installation' },
            { text: '配置说明', link: '/getting-started/configuration' },
          ],
        },
      ],
      '/architecture/': [
        {
          text: '系统架构',
          items: [
            { text: '系统架构概览', link: '/architecture/overview' },
          ],
        },
      ],
      '/api/': [
        {
          text: 'API 文档',
          items: [
            { text: 'API 概览', link: '/api/overview' },
          ],
        },
      ],
      '/development/': [
        {
          text: '开发环境',
          items: [
            { text: '环境设置', link: '/development/setup' },
            { text: '应用配置', link: '/development/app-settings' },
          ],
        },
      ],
      '/user-guide/': [
        {
          text: '用户指南', collapsed: false,
          items: [
            { text: '用户指南', link: '/user-guide/index' },
            { text: '快速上手', link: '/user-guide/getting-started' },
            { text: '病案管理', link: '/user-guide/patients' },
            { text: '影像浏览', link: '/user-guide/images' },
            { text: '统计分析', link: '/user-guide/statistics' },
            { text: '系统管理', link: '/user-guide/admin' },
            { text: '日志查看', link: '/user-guide/logs' },
          ],
        },
      ],
      '/maintenance/': [
        {
          text: '运维指南', collapsed: false,
          items: [
            { text: '运维指南', link: '/maintenance/index' },
            { text: '日志管理', link: '/maintenance/logs' },
            { text: '数据备份与恢复', link: '/maintenance/backup' },
            { text: '性能监控', link: '/maintenance/monitoring' },
            { text: '故障处理', link: '/maintenance/troubleshooting' },
            { text: '定期维护', link: '/maintenance/scheduled' },
            { text: '安全维护', link: '/maintenance/security' },
          ],
        },
      ],
      '/ai-generation/项目概览/': [
        {
          text: '项目概览',
          items: [
            { text: '项目概览', link: '/ai-generation/项目概览/项目概览' },
            { text: '技术架构概览', link: '/ai-generation/项目概览/技术架构概览' },
            { text: '核心功能模块', link: '/ai-generation/项目概览/核心功能模块' },
            { text: '系统特性与优势', link: '/ai-generation/项目概览/系统特性与优势' },
            { text: '系统目标与愿景', link: '/ai-generation/项目概览/系统目标与愿景' },
          ],
        },
      ],
      '/ai-generation/系统架构/': [
        {
          text: '系统架构', collapsed: false,
          items: [
            { text: '系统架构', link: '/ai-generation/系统架构/系统架构' },
            { text: '运营模式', link: '/ai-generation/系统架构/运营模式' },
            { text: '部署架构', link: '/ai-generation/系统架构/部署架构' },
          ],
        },
        {
          text: '前端架构', collapsed: true,
          items: [
            { text: '前端架构', link: '/ai-generation/系统架构/前端架构/前端架构' },
            { text: 'Vue 应用结构', link: '/ai-generation/系统架构/前端架构/Vue应用结构' },
            { text: '构建配置', link: '/ai-generation/系统架构/前端架构/构建配置' },
            { text: '样式与主题', link: '/ai-generation/系统架构/前端架构/样式与主题' },
            { text: '状态管理', link: '/ai-generation/系统架构/前端架构/状态管理' },
            { text: '组件设计', link: '/ai-generation/系统架构/前端架构/组件设计' },
            { text: '路由系统', link: '/ai-generation/系统架构/前端架构/路由系统' },
          ],
        },
        {
          text: '后端架构', collapsed: true,
          items: [
            { text: '后端架构', link: '/ai-generation/系统架构/后端架构/后端架构' },
            { text: 'Spring Boot 配置', link: '/ai-generation/系统架构/后端架构/Spring Boot配置' },
            { text: '异常处理', link: '/ai-generation/系统架构/后端架构/异常处理与全局错误管理' },
          ],
        },
        {
          text: '控制器层', collapsed: true,
          items: [
            { text: '控制器层架构', link: '/ai-generation/系统架构/后端架构/控制器层架构/控制器层架构' },
            { text: '扫描功能', link: '/ai-generation/系统架构/后端架构/控制器层架构/扫描功能控制器' },
            { text: '图像管理', link: '/ai-generation/系统架构/后端架构/控制器层架构/图像管理控制器' },
            { text: '用户管理', link: '/ai-generation/系统架构/后端架构/控制器层架构/用户管理控制器' },
            { text: '统计分析', link: '/ai-generation/系统架构/后端架构/控制器层架构/统计分析控制器' },
            { text: '日志管理', link: '/ai-generation/系统架构/后端架构/控制器层架构/日志管理控制器' },
            { text: '搜索', link: '/ai-generation/系统架构/后端架构/控制器层架构/搜索控制器' },
            { text: '系统信息', link: '/ai-generation/系统架构/后端架构/控制器层架构/系统信息控制器' },
            { text: '压力测试', link: '/ai-generation/系统架构/后端架构/控制器层架构/压力测试控制器' },
          ],
        },
        {
          text: '服务层', collapsed: true,
          items: [
            { text: '服务层架构', link: '/ai-generation/系统架构/后端架构/服务层架构/服务层架构' },
            { text: '扫描服务', link: '/ai-generation/系统架构/后端架构/服务层架构/扫描服务' },
            { text: '认证服务', link: '/ai-generation/系统架构/后端架构/服务层架构/认证服务' },
            { text: '用户服务', link: '/ai-generation/系统架构/后端架构/服务层架构/用户服务' },
            { text: '统计服务', link: '/ai-generation/系统架构/后端架构/服务层架构/统计服务' },
            { text: '搜索服务', link: '/ai-generation/系统架构/后端架构/服务层架构/搜索服务' },
            { text: '日志服务', link: '/ai-generation/系统架构/后端架构/服务层架构/日志服务' },
            { text: 'PDF 处理', link: '/ai-generation/系统架构/后端架构/服务层架构/PDF处理服务' },
          ],
        },
        {
          text: '数据访问层', collapsed: true,
          items: [
            { text: '数据访问层', link: '/ai-generation/系统架构/后端架构/数据访问层/数据访问层' },
            { text: '扫描记录 Mapper', link: '/ai-generation/系统架构/后端架构/数据访问层/扫描记录Mapper' },
            { text: '用户认证 Mapper', link: '/ai-generation/系统架构/后端架构/数据访问层/用户认证Mapper' },
            { text: '用户信息 Mapper', link: '/ai-generation/系统架构/后端架构/数据访问层/用户信息Mapper' },
            { text: '统计数据 Mapper', link: '/ai-generation/系统架构/后端架构/数据访问层/统计数据Mapper' },
            { text: '搜索查询 Mapper', link: '/ai-generation/系统架构/后端架构/数据访问层/搜索查询Mapper' },
            { text: '日志记录 Mapper', link: '/ai-generation/系统架构/后端架构/数据访问层/日志记录Mapper' },
            { text: '角色权限 Mapper', link: '/ai-generation/系统架构/后端架构/数据访问层/角色权限Mapper' },
          ],
        },
        {
          text: '数据架构', collapsed: true,
          items: [
            { text: '数据架构', link: '/ai-generation/系统架构/数据架构/数据架构' },
            { text: '实体模型设计', link: '/ai-generation/系统架构/数据架构/实体模型设计' },
            { text: '数据库表结构', link: '/ai-generation/系统架构/数据架构/数据库表结构' },
            { text: '数据访问层', link: '/ai-generation/系统架构/数据架构/数据访问层' },
            { text: '索引与查询优化', link: '/ai-generation/系统架构/数据架构/索引与查询优化' },
          ],
        },
      ],
      '/ai-generation/前端组件/': [
        {
          text: '前端组件',
          items: [
            { text: '前端组件', link: '/ai-generation/前端组件/前端组件' },
            { text: '仪表板组件', link: '/ai-generation/前端组件/仪表板组件' },
            { text: '导航组件', link: '/ai-generation/前端组件/导航组件' },
            { text: '数据表格组件', link: '/ai-generation/前端组件/数据表格组件' },
            { text: '表单组件', link: '/ai-generation/前端组件/表单组件' },
            { text: '对话框与通知', link: '/ai-generation/前端组件/对话框与通知组件' },
            { text: '影像浏览组件', link: '/ai-generation/前端组件/影像浏览组件' },
            { text: '认证组件', link: '/ai-generation/前端组件/认证组件' },
            { text: '管理员管理', link: '/ai-generation/前端组件/管理员管理组件' },
            { text: '统计详情页', link: '/ai-generation/前端组件/统计详情页面响应式设计' },
          ],
        },
      ],
      '/ai-generation/后端API文档/': [
        {
          text: '后端 API 文档',
          items: [
            { text: '后端 API 文档', link: '/ai-generation/后端API文档/后端API文档' },
            { text: '认证授权 API', link: '/ai-generation/后端API文档/认证授权API' },
            { text: '扫描记录 API', link: '/ai-generation/后端API文档/扫描记录API' },
            { text: '影像管理 API', link: '/ai-generation/后端API文档/影像管理API' },
            { text: '统计分析 API', link: '/ai-generation/后端API文档/统计分析API' },
            { text: '搜索查询 API', link: '/ai-generation/后端API文档/搜索查询API' },
            { text: '日志管理 API', link: '/ai-generation/后端API文档/日志管理API' },
            { text: '系统信息 API', link: '/ai-generation/后端API文档/系统信息API' },
            { text: '压力测试 API', link: '/ai-generation/后端API文档/压力测试API' },
          ],
        },
      ],
      '/ai-generation/数据库设计/': [
        {
          text: '数据库设计',
          items: [
            { text: '数据库设计', link: '/ai-generation/数据库设计/数据库设计' },
            { text: '用户管理表', link: '/ai-generation/数据库设计/用户管理表' },
            { text: '审计日志表', link: '/ai-generation/数据库设计/审计日志表' },
            { text: '索引与约束', link: '/ai-generation/数据库设计/索引与约束' },
            { text: '数据迁移', link: '/ai-generation/数据库设计/数据迁移与版本管理' },
          ],
        },
        {
          text: '核心业务表', collapsed: true,
          items: [
            { text: '核心业务表', link: '/ai-generation/数据库设计/核心业务表/核心业务表' },
            { text: 'mr_patient', link: '/ai-generation/数据库设计/核心业务表/mr_patient（患者表）' },
            { text: 'mr_scan', link: '/ai-generation/数据库设计/核心业务表/mr_scan（扫描记录表）' },
            { text: 'mr_statistics', link: '/ai-generation/数据库设计/核心业务表/mr_statistics（统计表）' },
          ],
        },
        {
          text: '认证授权表', collapsed: true,
          items: [
            { text: '认证授权表', link: '/ai-generation/数据库设计/认证授权表/认证授权表' },
            { text: 'mr_auth_user', link: '/ai-generation/数据库设计/认证授权表/用户表 (mr_auth_user)' },
            { text: 'mr_auth_role', link: '/ai-generation/数据库设计/认证授权表/角色表 (mr_auth_role)' },
            { text: '数据完整性与约束', link: '/ai-generation/数据库设计/认证授权表/数据完整性与约束' },
            { text: '初始数据与示例', link: '/ai-generation/数据库设计/认证授权表/初始数据与示例' },
          ],
        },
      ],
      '/ai-generation/开发指南/': [
        {
          text: '开发指南',
          items: [
            { text: '开发指南', link: '/ai-generation/开发指南/开发指南' },
            { text: '代码规范', link: '/ai-generation/开发指南/代码规范' },
            { text: '开发流程', link: '/ai-generation/开发指南/开发流程' },
            { text: '测试策略', link: '/ai-generation/开发指南/测试策略' },
            { text: '性能优化', link: '/ai-generation/开发指南/性能优化' },
            { text: '调试与故障排除', link: '/ai-generation/开发指南/调试与故障排除' },
          ],
        },
      ],
      '/ai-generation/认证授权/': [
        {
          text: '认证授权',
          items: [
            { text: '认证授权', link: '/ai-generation/认证授权/认证授权' },
            { text: 'JWT 认证机制', link: '/ai-generation/认证授权/JWT认证机制' },
            { text: '权限控制系统', link: '/ai-generation/认证授权/权限控制系统' },
            { text: '拦截器与安全过滤', link: '/ai-generation/认证授权/拦截器与安全过滤' },
            { text: '认证上下文与会话管理', link: '/ai-generation/认证授权/认证上下文与会话管理' },
          ],
        },
      ],
      '/ai-generation/日志审计与监控/': [
        {
          text: '日志审计与监控',
          items: [
            { text: '日志审计与监控', link: '/ai-generation/日志审计与监控/日志审计与监控' },
            { text: '日志管理', link: '/ai-generation/日志审计与监控/日志管理' },
            { text: '审计追踪', link: '/ai-generation/日志审计与监控/审计追踪' },
            { text: '监控系统', link: '/ai-generation/日志审计与监控/监控系统' },
          ],
        },
      ],
      '/ai-generation/部署运维/': [
        {
          text: '部署运维',
          items: [
            { text: '部署运维', link: '/ai-generation/部署运维/部署运维' },
            { text: '容器化部署', link: '/ai-generation/部署运维/容器化部署' },
            { text: '监控告警', link: '/ai-generation/部署运维/监控告警' },
            { text: '备份恢复', link: '/ai-generation/部署运维/备份恢复' },
            { text: '性能调优', link: '/ai-generation/部署运维/性能调优' },
            { text: 'CI/CD 流水线', link: '/ai-generation/部署运维/CI_CD流水线' },
          ],
        },
      ],
      '/ai-generation/guide/': [
        {
          text: '使用指南',
          items: [
            { text: '快速开始', link: '/ai-generation/guide/index' },
            { text: '部署指南', link: '/ai-generation/guide/deploy' },
            { text: '项目说明文档', link: '/ai-generation/guide/项目说明文档' },
            { text: '当前版本说明', link: '/ai-generation/guide/当前版本使用说明' },
            { text: '功能演示说明', link: '/ai-generation/guide/功能演示说明' },
          ],
        },
      ],
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/weepwood/MRR' },
    ],

    footer: {
      message: '基于 MIT 许可发布',
      copyright: `Copyright © 2024-${new Date().getFullYear()} MRR Team`,
    },

    search: {
      provider: 'local',
      options: {
        translations: {
          button: { buttonText: '搜索文档', buttonAriaLabel: '搜索文档' },
          modal: {
            noResultsText: '无法找到相关结果',
            resetButtonTitle: '清除查询条件',
            footer: { selectText: '选择', navigateText: '切换' },
          },
        },
      },
    },

    outline: { level: [2, 3], label: '页面导航' },
    docFooter: { prev: '上一页', next: '下一页' },
    lastUpdated: { text: '最后更新于', formatOptions: { dateStyle: 'short', timeStyle: 'short' } },
    returnToTopLabel: '返回顶部',
    sidebarMenuLabel: '菜单',
    darkModeSwitchLabel: '主题',
    lightModeSwitchTitle: '切换到浅色模式',
    darkModeSwitchTitle: '切换到深色模式',
  },

  markdown: {
    lineNumbers: true,
    config: (md) => { MermaidMarkdown(md) },
  },

  vite: {
    plugins: [MermaidPlugin()],
    optimizeDeps: { include: ['mermaid'] },
  },

  lastUpdated: true,
  ignoreDeadLinks: true,
})

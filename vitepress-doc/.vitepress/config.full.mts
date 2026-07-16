import { defineConfig } from 'vitepress'
import { MermaidMarkdown, MermaidPlugin } from 'vitepress-plugin-mermaid'

export default defineConfig({
  title: 'MRR 内部文档',
  description: 'MRR 医疗病案文件记录管理系统开发、部署与运维文档',
  lang: 'zh-CN',
  srcExclude: [
    'ai-generation/**',
    'architecture/**',
    'api/**',
    'development/**',
    'maintenance/**',
  ],

  head: [
    ['link', { rel: 'icon', href: '/logo.svg' }],
    ['meta', { name: 'theme-color', content: '#0a7c42' }],
    ['meta', { name: 'apple-mobile-web-app-capable', content: 'yes' }],
    ['meta', { property: 'og:title', content: 'MRR 内部文档' }],
    ['meta', { property: 'og:description', content: 'MRR 开发、架构、部署与运维文档' }],
    ['meta', { property: 'og:type', content: 'website' }],
  ],

  themeConfig: {
    logo: '/logo.svg',
    siteTitle: 'MRR 内部文档',
    nav: [
      { text: '首页', link: '/' },
      { text: '内部文档', link: '/internal/' },
      { text: '系统架构', link: '/internal/architecture' },
      {
        text: '工程指南',
        items: [
          { text: '前端工程', link: '/internal/frontend' },
          { text: '后端工程', link: '/internal/backend' },
          { text: '数据库', link: '/internal/database' },
          { text: 'API 与权限', link: '/internal/api' },
          { text: '开发流程', link: '/internal/development' },
        ],
      },
      {
        text: '部署与运维',
        items: [
          { text: '部署', link: '/internal/deployment' },
          { text: 'Windows Server 部署', link: '/internal/windows-deployment' },
          { text: '运维与监控', link: '/internal/operations' },
          { text: '安全', link: '/internal/security' },
          { text: '故障排查', link: '/internal/troubleshooting' },
          { text: '发布流程', link: '/internal/release' },
        ],
      },
      { text: '安装配置', link: '/getting-started/installation' },
      { text: '用户手册', link: '/user-guide/' },
      { text: '更新记录', link: '/user-guide/changelog' },
    ],

    sidebar: {
      '/internal/': [
        {
          text: '内部文档',
          collapsed: false,
          items: [
            { text: '文档首页', link: '/internal/' },
            { text: '系统架构', link: '/internal/architecture' },
          ],
        },
        {
          text: '工程实现',
          collapsed: false,
          items: [
            { text: '前端工程', link: '/internal/frontend' },
            { text: '后端工程', link: '/internal/backend' },
            { text: '数据库', link: '/internal/database' },
            { text: 'API 与权限', link: '/internal/api' },
            { text: '开发流程', link: '/internal/development' },
          ],
        },
        {
          text: '交付与运行',
          collapsed: false,
          items: [
            { text: '部署', link: '/internal/deployment' },
            { text: 'Windows Server 部署', link: '/internal/windows-deployment' },
            { text: '运维与监控', link: '/internal/operations' },
            { text: '安全', link: '/internal/security' },
            { text: '故障排查', link: '/internal/troubleshooting' },
            { text: '发布流程', link: '/internal/release' },
          ],
        },
      ],
      '/getting-started/': [
        {
          text: '安装与配置',
          collapsed: false,
          items: [
            { text: '安装指南', link: '/getting-started/installation' },
            { text: '配置说明', link: '/getting-started/configuration' },
          ],
        },
      ],
      '/user-guide/': [
        {
          text: '用户手册',
          collapsed: false,
          items: [
            { text: '用户指南', link: '/user-guide/' },
            { text: '快速上手', link: '/user-guide/getting-started' },
            { text: '病案与记录', link: '/user-guide/patients' },
            { text: '影像档案袋', link: '/user-guide/images' },
            { text: '统计分析', link: '/user-guide/statistics' },
            { text: '系统管理', link: '/user-guide/admin' },
            { text: '日志、审计与监控', link: '/user-guide/logs' },
            { text: '更新说明', link: '/user-guide/release-notes' },
            { text: 'Git 更新记录', link: '/user-guide/changelog' },
          ],
        },
      ],
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/weepwood/MRR' },
    ],
    footer: {
      message: '内部文档以当前代码与 Flyway 迁移为准',
      copyright: `Copyright © 2024-${new Date().getFullYear()} MRR Team`,
    },
    search: {
      provider: 'local',
      options: {
        translations: {
          button: { buttonText: '搜索内部文档', buttonAriaLabel: '搜索内部文档' },
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
  ignoreDeadLinks: false,
})

import fs from 'node:fs'
import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vitepress'

const productVersion = fs.readFileSync(
  fileURLToPath(new URL('../../VERSION', import.meta.url)),
  'utf8',
).trim()

export default defineConfig({
  title: `MRR 用户手册 v${productVersion}`,
  description: 'MRR 医疗病案文件管理系统用户操作手册',
  lang: 'zh-CN',
  base: '/docs/',
  srcDir: 'user-guide',
  outDir: '.vitepress/dist-user',
  appearance: false,

  head: [
    ['link', { rel: 'icon', href: '/docs/logo.svg' }],
    ['meta', { name: 'theme-color', content: '#0a7c42' }],
    ['meta', { name: 'robots', content: 'noindex,nofollow' }],
    ['meta', { property: 'og:title', content: `MRR 用户手册 v${productVersion}` }],
    ['meta', { property: 'og:description', content: '病案查询、影像调阅、导出、迁移和系统管理指南' }],
  ],

  themeConfig: {
    logo: '/logo.svg',
    siteTitle: `MRR 用户手册 v${productVersion}`,
    nav: [
      { text: '用户手册', link: '/' },
      { text: '影像档案袋', link: '/images' },
      { text: '系统管理', link: '/admin' },
      { text: '运行错误中心', link: '/logs#运行错误中心' },
      { text: '常见问题', link: '/faq' },
      { text: '更新说明', link: '/release-notes' },
      { text: `v${productVersion}`, link: '/release-notes' },
    ],
    sidebar: [
      {
        text: '开始使用',
        collapsed: false,
        items: [
          { text: '用户指南', link: '/' },
          { text: '快速上手', link: '/getting-started' },
          { text: '账号、注册与登录', link: '/accounts' },
          { text: '常见问题', link: '/faq' },
        ],
      },
      {
        text: '业务操作',
        collapsed: false,
        items: [
          { text: '病案与记录', link: '/patients' },
          { text: '影像档案袋', link: '/images' },
          { text: '病案类型', link: '/medical-record-types' },
          { text: '统计分析', link: '/statistics' },
          { text: '界面示例', link: '/screenshots' },
        ],
      },
      {
        text: '存储与数据治理',
        collapsed: false,
        items: [
          { text: 'OSS 迁移管理', link: '/oss-migration' },
          { text: '存储与文件浏览', link: '/storage-browser' },
          { text: '数据关系工作台', link: '/data-relation-workbench' },
        ],
      },
      {
        text: '管理与运维',
        collapsed: false,
        items: [
          { text: '系统管理', link: '/admin' },
          { text: '日志、审计与监控', link: '/logs' },
          { text: '运行错误中心', link: '/logs#运行错误中心' },
        ],
      },
      {
        text: '版本信息',
        collapsed: false,
        items: [
          { text: `当前版本 v${productVersion}`, link: '/release-notes' },
          { text: '更新说明', link: '/release-notes' },
          { text: 'Git 更新记录', link: '/changelog' },
        ],
      },
    ],
    search: {
      provider: 'local',
      options: {
        translations: {
          button: { buttonText: '搜索用户手册', buttonAriaLabel: '搜索用户手册' },
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
    footer: {
      message: `本手册以 main 当前代码与权限配置为准（v${productVersion}）`,
      copyright: `Copyright © 2024-${new Date().getFullYear()} MRR Team`,
    },
  },

  markdown: {
    lineNumbers: true,
  },

  vite: {
    publicDir: '../public',
  },

  lastUpdated: true,
  ignoreDeadLinks: false,
})
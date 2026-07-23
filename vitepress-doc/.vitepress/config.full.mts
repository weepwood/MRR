import fs from 'node:fs'
import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vitepress'
import { MermaidMarkdown, MermaidPlugin } from 'vitepress-plugin-mermaid'

const productVersion = fs.readFileSync(
  fileURLToPath(new URL('../../VERSION', import.meta.url)),
  'utf8',
).trim()

export default defineConfig({
  title: `MRR 内部文档 v${productVersion}`,
  description: 'MRR 医疗病案文件管理系统开发、部署与运维文档',
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
    ['meta', { property: 'og:title', content: `MRR 内部文档 v${productVersion}` }],
    ['meta', { property: 'og:description', content: 'MRR 架构、配置、数据、部署、安全与运维文档' }],
    ['meta', { property: 'og:type', content: 'website' }],
  ],

  themeConfig: {
    logo: '/logo.svg',
    siteTitle: `MRR 内部文档 v${productVersion}`,
    nav: [
      { text: '首页', link: '/' },
      { text: '内部文档', link: '/internal/' },
      { text: '系统架构', link: '/internal/architecture' },
      {
        text: '工程与配置',
        items: [
          { text: '最新代码审查', link: '/internal/code-review' },
          { text: '前端工程', link: '/internal/frontend' },
          { text: '后端工程', link: '/internal/backend' },
          { text: '数据库', link: '/internal/database' },
          { text: 'API 与权限', link: '/internal/api' },
          { text: '配置参考', link: '/internal/configuration-reference' },
          { text: '开发流程', link: '/internal/development' },
          { text: '文档维护规范', link: '/internal/documentation' },
        ],
      },
      {
        text: '数据与接入',
        items: [
          { text: '数据导入与迁移', link: '/internal/data-migration' },
          { text: '逐表导入教程', link: '/internal/data-import/' },
          { text: '外部系统影像接入', link: '/internal/external-archive-integration' },
          { text: 'OSS 迁移管理', link: '/user-guide/oss-migration' },
          { text: '存储与文件浏览', link: '/user-guide/storage-browser' },
        ],
      },
      {
        text: '部署与运维',
        items: [
          { text: '部署总览', link: '/internal/deployment' },
          { text: '单体 JAR 部署', link: '/internal/standalone-jar' },
          { text: 'Windows Server 部署', link: '/internal/windows-deployment' },
          { text: '运行错误中心', link: '/internal/runtime-errors' },
          { text: '运维与监控', link: '/internal/operations' },
          { text: '生产运行手册', link: '/internal/runbook' },
          { text: '安全', link: '/internal/security' },
          { text: '故障排查', link: '/internal/troubleshooting' },
          { text: '发布流程', link: '/internal/release' },
        ],
      },
      { text: '用户手册', link: '/user-guide/' },
      { text: '更新记录', link: '/user-guide/changelog' },
      { text: `v${productVersion}`, link: '/internal/release' },
    ],

    sidebar: {
      '/internal/': [
        {
          text: '总览',
          collapsed: false,
          items: [
            { text: '内部文档首页', link: '/internal/' },
            { text: '系统架构', link: '/internal/architecture' },
            { text: '配置参考', link: '/internal/configuration-reference' },
            { text: '最新代码审查', link: '/internal/code-review' },
          ],
        },
        {
          text: '工程开发',
          collapsed: false,
          items: [
            { text: '前端工程', link: '/internal/frontend' },
            { text: '后端工程', link: '/internal/backend' },
            { text: '数据库', link: '/internal/database' },
            { text: 'API 与权限', link: '/internal/api' },
            { text: '开发流程', link: '/internal/development' },
            { text: '文档维护规范', link: '/internal/documentation' },
            { text: '更新日志工作流', link: '/internal/changelog-workflow' },
          ],
        },
        {
          text: '数据与集成',
          collapsed: false,
          items: [
            { text: '数据导入与迁移', link: '/internal/data-migration' },
            { text: '逐表导入教程', link: '/internal/data-import/' },
            { text: '患者数据导入', link: '/internal/data-import/mr-patient' },
            { text: '统计数据导入', link: '/internal/data-import/mr-statistics' },
            { text: '装箱数据导入', link: '/internal/data-import/mr-archive-box-record' },
            { text: '扫描影像导入', link: '/internal/data-import/mr-scan' },
            { text: '外部系统影像接入', link: '/internal/external-archive-integration' },
          ],
        },
        {
          text: '交付与运行',
          collapsed: false,
          items: [
            { text: '部署总览', link: '/internal/deployment' },
            { text: '单体 JAR 部署', link: '/internal/standalone-jar' },
            { text: 'Windows Server 部署', link: '/internal/windows-deployment' },
            { text: '运行错误中心', link: '/internal/runtime-errors' },
            { text: '运维与监控', link: '/internal/operations' },
            { text: '生产运行手册', link: '/internal/runbook' },
            { text: '安全', link: '/internal/security' },
            { text: '故障排查', link: '/internal/troubleshooting' },
            { text: `发布流程（v${productVersion}）`, link: '/internal/release' },
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
            { text: '完整配置参考', link: '/internal/configuration-reference' },
          ],
        },
      ],
      '/user-guide/': [
        {
          text: '开始使用',
          collapsed: false,
          items: [
            { text: '用户指南', link: '/user-guide/' },
            { text: '快速上手', link: '/user-guide/getting-started' },
            { text: '账号、注册与登录', link: '/user-guide/accounts' },
            { text: '常见问题', link: '/user-guide/faq' },
          ],
        },
        {
          text: '业务操作',
          collapsed: false,
          items: [
            { text: '病案与记录', link: '/user-guide/patients' },
            { text: '影像档案袋', link: '/user-guide/images' },
            { text: '病案类型', link: '/user-guide/medical-record-types' },
            { text: '统计分析', link: '/user-guide/statistics' },
            { text: '界面示例', link: '/user-guide/screenshots' },
          ],
        },
        {
          text: '存储与治理',
          collapsed: false,
          items: [
            { text: 'OSS 迁移管理', link: '/user-guide/oss-migration' },
            { text: '存储与文件浏览', link: '/user-guide/storage-browser' },
            { text: '数据关系工作台', link: '/user-guide/data-relation-workbench' },
          ],
        },
        {
          text: '系统管理',
          collapsed: false,
          items: [
            { text: '系统管理', link: '/user-guide/admin' },
            { text: '日志、审计与监控', link: '/user-guide/logs' },
            { text: '运行错误中心', link: '/user-guide/logs#运行错误中心' },
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
      message: `内部文档以 VERSION、release-baseline.json 与 main 当前代码为准（v${productVersion}）`,
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
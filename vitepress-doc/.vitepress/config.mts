import { defineConfig } from 'vitepress'
import { MermaidMarkdown, MermaidPlugin } from 'vitepress-plugin-mermaid'

export default defineConfig({
  title: "MRR 文档",
  description: "医疗影像记录管理系统文档",
  lang: 'zh-CN',
  
  head: [
    ['meta', { name: 'theme-color', content: '#3eaf7c' }],
    ['meta', { name: 'apple-mobile-web-app-capable', content: 'yes' }],
    ['meta', { name: 'apple-mobile-web-app-status-bar-style', content: 'black' }]
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
      { text: '运维指南', link: '/maintenance/index' }
    ],

    sidebar: {
      '/getting-started/': [
        { text: '快速开始', items: [
          { text: '安装指南', link: '/getting-started/installation' },
          { text: '配置说明', link: '/getting-started/configuration' }
        ]}
      ],
      '/user-guide/': [
        { text: '用户指南', items: [
          { text: '用户指南', link: '/user-guide/index' },
          { text: '快速上手', link: '/user-guide/getting-started' },
          { text: '病案管理', link: '/user-guide/patients' },
          { text: '影像浏览', link: '/user-guide/images' },
          { text: '统计分析', link: '/user-guide/statistics' },
          { text: '系统管理', link: '/user-guide/admin' },
          { text: '日志查看', link: '/user-guide/logs' }
        ]}
      ],
      '/maintenance/': [
        { text: '运维指南', items: [
          { text: '运维指南', link: '/maintenance/index' },
          { text: '日志管理', link: '/maintenance/logs' },
          { text: '数据备份与恢复', link: '/maintenance/backup' },
          { text: '性能监控', link: '/maintenance/monitoring' },
          { text: '故障处理', link: '/maintenance/troubleshooting' },
          { text: '定期维护', link: '/maintenance/scheduled' },
          { text: '安全维护', link: '/maintenance/security' }
        ]}
      ]
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/your-repo/mrr' }
    ],

    footer: {
      message: '基于 MIT 许可发布',
      copyright: 'Copyright © 2024-present MRR Team'
    },

    search: { provider: 'local' },

    lastUpdated: { text: '最后更新于', formatOptions: { dateStyle: 'short', timeStyle: 'short' } }
  },

  markdown: {
    lineNumbers: true,
    config: (md) => { MermaidMarkdown(md) }
  },

  vite: {
    plugins: [MermaidPlugin()],
    optimizeDeps: { include: ['mermaid'] }
  },

  lastUpdated: true,
  ignoreDeadLinks: true
})

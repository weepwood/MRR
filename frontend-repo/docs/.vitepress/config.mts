import { defineConfig } from 'vitepress'

export default defineConfig({
  base: '/docs/',
  srcDir: '.',
  outDir: '../public/docs',
  title: 'PMR 文档中心',
  description: '病案翻拍管理系统文档站点',
  lang: 'zh-CN',
  lastUpdated: true,
  cleanUrls: true,
  themeConfig: {
    nav: [
      { text: '首页', link: '/' },
      { text: '快速开始', link: '/guide/getting-started' },
      { text: '部署', link: '/guide/deploy' }
    ],
    sidebar: [
      {
        text: '指南',
        items: [
          { text: '快速开始', link: '/guide/getting-started' },
          { text: '构建与部署', link: '/guide/deploy' }
        ]
      },
      {
        text: '项目文档',
        items: [{ text: '现有说明文档索引', link: '/reference/project-docs' }]
      }
    ],
    outline: { level: [2, 3], label: '目录' },
    docFooter: { prev: '上一页', next: '下一页' },
    returnToTopLabel: '返回顶部',
    search: {
      provider: 'local'
    }
  }
})

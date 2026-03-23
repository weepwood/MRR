import { defineConfig } from 'vitepress'

export default defineConfig({
  base: '/docs/',
  srcDir: '.',
  outDir: '../public/docs',
  title: 'PMR 文档中心',
  description: '病案翻拍管理系统使用与维护文档',
  lang: 'zh-CN',
  lastUpdated: true,
  cleanUrls: true,
  themeConfig: {
    nav: [
      { text: '首页', link: '/' },
      { text: '指南', link: '/guide/' },
      { text: '快速开始', link: '/guide/getting-started' },
      { text: '部署', link: '/guide/deploy' },
      { text: '文档索引', link: '/reference/project-docs' }
    ],
    sidebar: {
      '/guide/': [
        {
          text: '入门',
          items: [
            { text: '指南首页', link: '/guide/' },
            { text: '快速开始', link: '/guide/getting-started' },
            { text: '构建与部署', link: '/guide/deploy' }
          ]
        },
        {
          text: '功能说明',
          items: [
            { text: '病案管理功能说明', link: '/guide/病案管理功能说明' },
            { text: '病案统计页面说明', link: '/guide/病案统计页面说明' },
            { text: '系统日志功能说明', link: '/guide/系统日志功能说明' },
            { text: '系统监控功能说明', link: '/guide/系统监控功能说明' },
            { text: '功能演示说明', link: '/guide/功能演示说明' },
            { text: '多选 Bug 修复说明', link: '/guide/多选Bug修复说明' },
            { text: '打印功能修复说明', link: '/guide/打印功能修复说明' }
          ]
        }
      ],
      '/reference/': [
        {
          text: '参考',
          items: [{ text: '项目文档索引', link: '/reference/project-docs' }]
        }
      ]
    },
    outline: { level: [2, 3], label: '目录' },
    docFooter: { prev: '上一页', next: '下一页' },
    lastUpdatedText: '最后更新',
    returnToTopLabel: '返回顶部',
    search: {
      provider: 'local',
      options: {
        translations: {
          button: {
            buttonText: '搜索文档',
            buttonAriaLabel: '搜索文档'
          },
          modal: {
            noResultsText: '没有找到相关结果',
            resetButtonTitle: '清空搜索条件',
            footer: {
              selectText: '选择',
              navigateText: '切换',
              closeText: '关闭'
            }
          }
        }
      }
    }
  }
})

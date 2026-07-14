import { defineConfig } from 'vitepress'
import fullConfig from './config.full.mts'

export default defineConfig({
  ...fullConfig,
  base: '/docs/internal/',
  outDir: '.vitepress/dist-internal',
  appearance: false,
  head: [
    ['link', { rel: 'icon', href: '/docs/internal/logo.svg' }],
    ['meta', { name: 'theme-color', content: '#0a7c42' }],
    ['meta', { name: 'robots', content: 'noindex,nofollow' }],
    ['meta', { property: 'og:title', content: 'MRR 内部文档' }],
    ['meta', { property: 'og:description', content: 'MRR 开发、架构与运维文档' }],
    ['meta', { property: 'og:type', content: 'website' }],
  ],
})

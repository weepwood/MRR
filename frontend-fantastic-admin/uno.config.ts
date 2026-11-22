import type { Theme } from 'unocss/preset-uno'
import { entriesToCss } from '@unocss/core'
import presetLegacyCompat from '@unocss/preset-legacy-compat'
import {
  defineConfig,
  presetAttributify,
  presetIcons,
  presetTypography,
  presetWind3,
  transformerCompileClass,
  transformerDirectives,
  transformerVariantGroup,
} from 'unocss'
import { presetAnimations } from 'unocss-preset-animations'
import { darkTheme, lightTheme } from './themes'

export default defineConfig<Theme>({
  content: {
    pipeline: {
      include: [
        /\.(vue|svelte|[jt]sx|mdx?|astro|elm|php|phtml|html)($|\?)/,
        'src/**/*.{js,ts}',
      ],
    },
  },
  shortcuts: [
    [/^flex-?(col)?-(start|end|center|baseline|stretch)-?(start|end|center|between|around|evenly|left|right)?$/, ([, col, items, justify]) => {
      const cls = ['flex']
      if (col === 'col') {
        cls.push('flex-col')
      }
      if (items === 'center' && !justify) {
        cls.push('items-center')
        cls.push('justify-center')
      }
      else {
        cls.push(`items-${items}`)
        if (justify) {
          cls.push(`justify-${justify}`)
        }
      }
      return cls.join(' ')
    }],
  ],
  presets: [
    presetWind3(),
    presetAnimations(),
    presetAttributify(),
    presetIcons({
      extraProperties: {
        'display': 'inline-block',
        'vertical-align': 'middle',
      },
    }),
    presetTypography(),
    presetLegacyCompat({
      legacyColorSpace: true,
    }),
    {
      name: 'unocss-preset-shadcn',
      preflights: [
        {
          getCSS: () => {
            const returnCss: any = []
            // 明亮主题
            const lightCss = entriesToCss(Object.entries(lightTheme))
            returnCss.push(`:root{${lightCss}}`)
            // 暗黑主题
            const darkCss = entriesToCss(Object.entries(darkTheme))
            returnCss.push(`html.dark{${darkCss}}`)
            return `
${returnCss.join('\n')}

* {
  border-color: hsl(var(--border));
}

body {
  color: hsl(var(--foreground));
  background: hsl(var(--background));
}
`
          },
        },
      ],
      theme: {
        colors: {
          border: 'hsl(var(--border))',
          input: 'hsl(var(--input))',
          ring: 'hsl(var(--ring))',
          background: 'hsl(var(--background))',
          foreground: 'hsl(var(--foreground))',
          primary: {
            DEFAULT: 'hsl(var(--primary))',
            foreground: 'hsl(var(--primary-foreground))',
          },
          secondary: {
            DEFAULT: 'hsl(var(--secondary))',
            foreground: 'hsl(var(--secondary-foreground))',
          },
          destructive: {
            DEFAULT: 'hsl(var(--destructive))',
            foreground: 'hsl(var(--destructive-foreground))',
          },
          muted: {
            DEFAULT: 'hsl(var(--muted))',
            foreground: 'hsl(var(--muted-foreground))',
          },
          accent: {
            DEFAULT: 'hsl(var(--accent))',
            foreground: 'hsl(var(--accent-foreground))',
          },
          popover: {
            DEFAULT: 'hsl(var(--popover))',
            foreground: 'hsl(var(--popover-foreground))',
          },
          card: {
            DEFAULT: 'hsl(var(--card))',
            foreground: 'hsl(var(--card-foreground))',
          },
        },
        // 动效令牌别名：duration-mrr-move / ease-mrr-out 等直接读 tokens.css，
        // DEFAULT 让 transition / transition-colors 等工具类默认走 feedback + ease-out（DESIGN.md §2.9）。
        duration: {
          'DEFAULT': 'var(--mrr-motion-feedback)',
          'mrr-press': 'var(--mrr-motion-press)',
          'mrr-feedback': 'var(--mrr-motion-feedback)',
          'mrr-move': 'var(--mrr-motion-move)',
          'mrr-overlay': 'var(--mrr-motion-overlay)',
        },
        easing: {
          'DEFAULT': 'var(--mrr-ease-out)',
          'mrr-out': 'var(--mrr-ease-out)',
          'mrr-in': 'var(--mrr-ease-in)',
        },
        borderRadius: {
          xl: 'calc(var(--radius) + 4px)',
          lg: 'var(--radius)',
          md: 'calc(var(--radius) - 2px)',
          sm: 'calc(var(--radius) - 4px)',
        },
      },
    },
  ],
  transformers: [
    transformerDirectives(),
    transformerVariantGroup(),
    transformerCompileClass(),
  ],
  configDeps: [
    'themes/index.ts',
  ],
})

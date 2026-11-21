import type { VariantProps } from 'class-variance-authority'
import { cva } from 'class-variance-authority'

export { default as Button } from './Button.vue'

/**
 * FaButton 的几何与配色。
 *
 * <p>项目并存两套按钮：Element Plus 的 `el-button` 与这里的 reka-ui 封装。
 * 两者在同一页面里承担同样的角色，所以**不允许各自维护一套数值**：
 * 高度、内边距、字号、圆角全部引用与 `el-button` 相同的令牌
 * （`--mrr-control-height*`、`--mrr-button-*`、`--mrr-radius-*`），
 * 主题换密度时两族一起变。</p>
 *
 * <p>`text-[length:...]` 的 `length:` 提示不能省：`text-[var(--x)]` 在 UnoCSS 里
 * 是「颜色还是字号」的歧义写法，会按颜色处理，字号不生效（实测退化成继承的 14px）。</p>
 *
 * <p>交互状态（hover / active / focus-visible / disabled / loading）不在这里定义：
 * cva 只产出工具类，而同名状态的伪类规则无法用工具类表达。
 * 这些状态由 `src/assets/styles/components/form-controls.css` 按 `[data-variant]`
 * 统一定义，取值与 `element-plus-overrides.css` 的 Buttons 段逐条对齐。</p>
 */
export const buttonVariants = cva(
  'fa-button-control inline-flex items-center justify-center gap-[6px] whitespace-nowrap border border-solid font-medium leading-none h-[var(--mrr-control-height)] px-[var(--mrr-button-padding-x)] text-[length:var(--mrr-button-font-size)] rounded-[var(--mrr-control-radius)] transition-colors [&_svg]:pointer-events-none [&_svg]:size-4 [&_svg]:shrink-0',
  {
    variants: {
      variant: {
        // 颜色的「静置态」也放在这里，调用方仍可用工具类覆盖（cn 会做 tailwind-merge）；
        // hover/active/禁用等状态见 form-controls.css。
        default: 'bg-[var(--mrr-primary)] text-[color:var(--mrr-primary-foreground)] border-[var(--mrr-primary)] shadow-[var(--mrr-shadow-xs)]',
        destructive: 'bg-[var(--mrr-destructive-muted)] text-[color:var(--mrr-destructive)] border-transparent',
        outline: 'bg-[var(--mrr-background)] text-[color:var(--mrr-foreground)] border-[var(--mrr-border)] shadow-[var(--mrr-shadow-xs)]',
        secondary: 'bg-[var(--mrr-secondary)] text-[color:var(--mrr-secondary-foreground)] border-[var(--mrr-border)]',
        // 文字类按钮没有底与描边，左右留白收窄一档（与 el-button 的 text 变体一致）。
        // 描边宽度见 form-controls.css：ghost 无边框、link 保留 1px 透明边框。
        ghost: 'bg-transparent text-[color:var(--mrr-foreground)] px-[var(--mrr-button-padding-x-text)] rounded-[var(--mrr-radius-sm)]',
        link: 'bg-transparent text-[color:var(--mrr-primary)] px-[var(--mrr-button-padding-x-text)] rounded-[var(--mrr-radius-sm)]',
      },
      size: {
        default: '',
        sm: 'h-[var(--mrr-control-height-compact)] px-[var(--mrr-button-padding-x-sm)] text-[length:var(--mrr-button-font-size-sm)] rounded-[var(--mrr-radius-sm)]',
        lg: 'h-[var(--mrr-control-height-large)] px-[var(--mrr-button-padding-x-lg)] text-[length:var(--mrr-button-font-size-lg)] rounded-[var(--mrr-radius-lg)]',
        icon: 'w-[var(--mrr-control-height)] shrink-0 px-0',
      },
    },
    defaultVariants: {
      variant: 'default',
      size: 'default',
    },
  },
)

export type ButtonVariants = VariantProps<typeof buttonVariants>

export default {
  extends: [
    'stylelint-config-standard-scss',
    'stylelint-config-standard-vue/scss',
    'stylelint-config-recess-order',
    '@stylistic/stylelint-config',
  ],
  plugins: [
    'stylelint-scss',
  ],
  rules: {
    'at-rule-no-unknown': null,
    'no-descending-specificity': null,
    'property-no-unknown': null,
    'font-family-no-missing-generic-family-keyword': null,
    'selector-class-pattern': null,
    'function-no-unknown': [
      true,
      {
        ignoreFunctions: [
          'v-bind',
          'map-get',
          'lighten',
          'darken',
        ],
      },
    ],
    'selector-pseudo-element-no-unknown': [
      true,
      {
        ignorePseudoElements: [
          '/^view-transition/',
        ],
      },
    ],
    /*
     * `:not(.a, .b)` 属于 Selectors Level 4，需要 Chrome 88+，而本项目基线是
     * Chrome 86——列表形态会让整条规则在目标浏览器上被丢弃（默认按钮的边框、
     * 背景色会整体失效）。这里固定为 simple，允许链式 `:not(.a):not(.b)`，
     * 与 scripts/verify-chrome86-build.mjs 的源码门禁保持一致。
     */
    'selector-not-notation': 'simple',
    /*
     * 媒体查询区间语法 `(width <= 760px)` 需要 Chrome 104+，本项目基线是 Chrome 86，
     * 在目标浏览器上该查询恒不匹配——整块响应式规则静默失效（曾一度有 77 处这种写法）。
     * 固定为 prefix，强制使用 `max-width` / `min-width`，与 verify-chrome86-build.mjs 门禁一致。
     */
    'media-feature-range-notation': 'prefix',
    /*
     * `mask-*` 的无前缀支持要到 Chrome 120，基线 Chrome 86 必须保留 `-webkit-` 前缀，
     * 且源码一律「前缀在前、标准在后」成对声明（现代浏览器取后者）。这不是过时写法。
     */
    'property-no-vendor-prefix': [
      true,
      {
        ignoreProperties: [
          '/^-webkit-mask/',
        ],
      },
    ],
    'scss/double-slash-comment-empty-line-before': null,
    'scss/no-global-function-names': null,
    '@stylistic/max-line-length': null,
    'declaration-block-single-line-max-declarations': null,
    '@stylistic/block-closing-brace-newline-after': [
      'always',
      {
        ignoreAtRules: ['if', 'else'],
      },
    ],
  },
  allowEmptyInput: true,
  ignoreFiles: [
    'node_modules/**/*',
    'dist*/**/*',
  ],
  overrides: [
    /*
     * public/ 是不经构建转译、原样拷贝进 dist 的静态层（浏览器升级拦截页受众是
     * Chromium <86）。lint-staged 的 `stylelint --cache --fix` 曾把
     * top/right/bottom/left 四向 longhand 收敛为 inset，全屏浮层失去定位
     * （见 scripts/verify-chrome86-build.mjs 门禁）。这里停用实测触发的改写
     * 规则——shorthand 收敛 / color 现代记法 / 属性重排；其余格式类 autofix
     * 无兼容风险，仍保持启用：
     * - declaration-block-no-redundant-longhand-properties / shorthand-property-no-redundant-values：
     *   shorthand 收敛（top/right/bottom/left → inset 等），直接破坏 Chrome 86 基线；
     * - color-function-notation / color-function-alias-notation / alpha-value-notation /
     *   hue-degree-notation / lightness-notation：强制现代颜色记法的 autofix
     *   （rgba(...) → rgb(r g b / a%)、deg/percentage 归一）。注：Chrome 86 实际
     *   支持 rgb() 空格斜杠记法，兼容性理由对当前基线并不成立；关停依据是
     *   「public/ 静态首帧壳层一律不动」的原则——这个零转译层的任何改动都应
     *   走人工审查，并落在 verify-chrome86-build 与 public-static-files.test
     *   双门禁可核查的范围内；
     * - order/properties-order（stylelint-config-recess-order）：
     *   属性顺序自动重排，对静态层同样构成非必要改写。
     */
    {
      files: ['public/**/*.css'],
      rules: {
        'declaration-block-no-redundant-longhand-properties': null,
        'shorthand-property-no-redundant-values': null,
        'color-function-alias-notation': null,
        'color-function-notation': null,
        'alpha-value-notation': null,
        'hue-degree-notation': null,
        'lightness-notation': null,
        'order/properties-order': null,
      },
    },
  ],
}

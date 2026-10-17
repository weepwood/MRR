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
}

/**
 * 主题主色的派生体系。
 *
 * 主色只允许一个输入（十六进制），其余色阶全部由这里推导，保证：
 * 1. **感知均匀**：混合与明暗调整都在 OKLab/OKLCH 中完成，避免 sRGB 直接混白/混黑造成的
 *    褪色、发灰与色相漂移（旧实现把蓝色混到 10% 会得到偏灰的浅蓝）。
 * 2. **明暗自适应**：浅色模式把 `light-3..9` 提亮（供浅底上的浅色填充），暗色模式改为压暗
 *    （Element Plus 暗色主题的约定是 light-x 越深越接近底色），`dark-2` 反向取更亮的一档。
 * 3. **可读性**：预设色块与白字对比度全部 ≥ 4.5:1，色块上的对勾按背景亮度自动取黑或白。
 */

/** 明亮 / 暗黑两种配色方向 */
export type ThemeColorScheme = 'light' | 'dark'

/** 主色派生出的完整 CSS 变量表 */
export interface ThemeColorScale {
  /** 主色本身 */
  primary: string
  /** 悬停：浅色略深、暗色略亮 */
  hover: string
  /** 按下 */
  active: string
  /** 更深的强调色（描边、图表深色档） */
  deep: string
  /** 供 shadcn 令牌使用的 `H S% L%` 形式 */
  hsl: string
  /** Element Plus 的浅色档，浅色模式向白、暗色模式向底色混合 */
  light3: string
  light5: string
  light7: string
  light8: string
  light9: string
  /** Element Plus 的深色强调档 */
  dark2: string
}

/**
 * 主题主色预设。
 *
 * 三条不变量（见 `theme-color.test.ts`）：感知亮度落在同一区间、白字对比度 ≥ 4.5、
 * 两两 OKLab 色差 ≥ 0.12。最后一条是刻意避开"橙色和红色看起来一样"的选色结果：
 * 暖色里取橙色与玫红，而不是橙 + 正红。
 */
export interface ThemeColorPreset {
  label: string
  color: string
}

export const themeColorPresets: readonly ThemeColorPreset[] = [
  { label: '蓝色', color: '#2563EB' },
  { label: '青色', color: '#0B7EA6' },
  { label: '绿色', color: '#0A8642' },
  { label: '橙色', color: '#C2410C' },
  { label: '紫色', color: '#7C3AED' },
  { label: '玫红', color: '#DB2777' },
]

/** 后端与设置默认值共用的兜底主色，同时也是预设里的蓝色。 */
export const DEFAULT_THEME_COLOR = themeColorPresets[0].color

/** Element Plus 暗色主题的基底色，`light-x` 向它混合才符合暗色约定。 */
const DARK_SURFACE_COLOR = '#141414'

/** 白字与主色的最低对比度（WCAG AA 正文） */
export const MIN_CONTRAST_ON_PRIMARY = 4.5

interface Rgb {
  /** 0–1 的 sRGB 通道 */
  r: number
  g: number
  b: number
}

interface Oklab {
  l: number
  a: number
  b: number
}

/** 解析 `#RGB` / `#RRGGBB`（忽略透明度），非法输入返回 undefined。 */
export function parseHexColor(value: unknown): Rgb | undefined {
  const text = String(value ?? '').trim().replace(/^#/, '')
  if (/^[\da-f]{3}$/i.test(text)) {
    const [r, g, b] = text.split('')
    return channelsToRgb(Number.parseInt(`${r}${r}`, 16), Number.parseInt(`${g}${g}`, 16), Number.parseInt(`${b}${b}`, 16))
  }
  if (/^[\da-f]{6}$/i.test(text)) {
    const number = Number.parseInt(text, 16)
    return channelsToRgb((number >> 16) & 0xFF, (number >> 8) & 0xFF, number & 0xFF)
  }
  return undefined
}

function channelsToRgb(red: number, green: number, blue: number): Rgb {
  return { r: red / 255, g: green / 255, b: blue / 255 }
}

function rgbToHex({ r, g, b }: Rgb): string {
  const toHex = (channel: number) => Math.round(clamp01(channel) * 255).toString(16).padStart(2, '0')
  return `#${toHex(r)}${toHex(g)}${toHex(b)}`.toUpperCase()
}

function clamp01(value: number): number {
  return Math.min(1, Math.max(0, value))
}

function srgbToLinear(channel: number): number {
  return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4
}

function linearToSrgb(channel: number): number {
  return channel <= 0.0031308 ? channel * 12.92 : 1.055 * channel ** (1 / 2.4) - 0.055
}

function rgbToOklab({ r, g, b }: Rgb): Oklab {
  const red = srgbToLinear(r)
  const green = srgbToLinear(g)
  const blue = srgbToLinear(b)
  const long = Math.cbrt(0.4122214708 * red + 0.5363325363 * green + 0.0514459929 * blue)
  const medium = Math.cbrt(0.2119034982 * red + 0.6806995451 * green + 0.1073969566 * blue)
  const short = Math.cbrt(0.0883024619 * red + 0.2817188376 * green + 0.6299787005 * blue)
  return {
    l: 0.2104542553 * long + 0.7936177850 * medium - 0.0040720468 * short,
    a: 1.9779984951 * long - 2.4285922050 * medium + 0.4505937099 * short,
    b: 0.0259040371 * long + 0.7827717662 * medium - 0.8086757660 * short,
  }
}

function oklabToRgb({ l, a, b }: Oklab): Rgb {
  const long = (l + 0.3963377774 * a + 0.2158037573 * b) ** 3
  const medium = (l - 0.1055613458 * a - 0.0638541728 * b) ** 3
  const short = (l - 0.0894841775 * a - 1.2914855480 * b) ** 3
  return {
    r: linearToSrgb(4.0767416621 * long - 3.3077115913 * medium + 0.2309699292 * short),
    g: linearToSrgb(-1.2684380046 * long + 2.6097574011 * medium - 0.3413193965 * short),
    b: linearToSrgb(-0.0041960863 * long - 0.7034186147 * medium + 1.7076147010 * short),
  }
}

function isInGamut({ r, g, b }: Rgb): boolean {
  const epsilon = 0.0005
  return r >= -epsilon && r <= 1 + epsilon && g >= -epsilon && g <= 1 + epsilon && b >= -epsilon && b <= 1 + epsilon
}

interface Oklch {
  l: number
  c: number
  h: number
}

function oklabToOklch({ l, a, b }: Oklab): Oklch {
  const chroma = Math.hypot(a, b)
  let hue = (Math.atan2(b, a) * 180) / Math.PI
  if (hue < 0) {
    hue += 360
  }
  return { l, c: chroma, h: hue }
}

function oklchToOklab({ l, c, h }: Oklch): Oklab {
  const radians = (h * Math.PI) / 180
  return { l, a: c * Math.cos(radians), b: c * Math.sin(radians) }
}

/** 把 OKLCH 转成十六进制；超出 sRGB 色域时逐步降低彩度，优先保住色相与亮度。 */
function oklchToHex(color: Oklch): string {
  let chroma = Math.max(0, color.c)
  for (let step = 0; step < 32; step++) {
    const rgb = oklabToRgb(oklchToOklab({ ...color, c: chroma }))
    if (isInGamut(rgb)) {
      return rgbToHex(rgb)
    }
    chroma = chroma <= 0.004 ? 0 : chroma * 0.9
  }
  return rgbToHex(oklabToRgb(oklchToOklab({ ...color, c: 0 })))
}

/** 在 OKLab 中按权重插值，`sourceWeight` 为目标色的占比，等价于 `mix(source, target)`。 */
function mixInOklab(source: string, target: string, sourceWeight: number): string {
  const from = rgbToOklab(parseHexColor(source) ?? parseHexColor(DEFAULT_THEME_COLOR)!)
  const to = rgbToOklab(parseHexColor(target) ?? parseHexColor('#FFFFFF')!)
  const weight = clamp01(sourceWeight)
  return oklchToHex(oklabToOklch({
    l: from.l * weight + to.l * (1 - weight),
    a: from.a * weight + to.a * (1 - weight),
    b: from.b * weight + to.b * (1 - weight),
  }))
}

/**
 * 沿亮度轴调整一档。
 * 色相与彩度保持不变，只在超出色域时降彩度，因此变亮不会发灰、变暗不会发浊。
 */
function shiftLightness(color: string, delta: number): string {
  const oklch = oklabToOklch(rgbToOklab(parseHexColor(color) ?? parseHexColor(DEFAULT_THEME_COLOR)!))
  return oklchToHex({ l: Math.min(1, Math.max(0, oklch.l + delta)), c: oklch.c, h: oklch.h })
}

function toHslToken(color: string): string {
  const { r, g, b } = parseHexColor(color) ?? parseHexColor(DEFAULT_THEME_COLOR)!
  const maximum = Math.max(r, g, b)
  const minimum = Math.min(r, g, b)
  const lightness = (maximum + minimum) / 2
  const delta = maximum - minimum
  const saturation = delta === 0 ? 0 : delta / (1 - Math.abs(2 * lightness - 1))
  let hue = 0
  if (delta !== 0) {
    if (maximum === r) {
      hue = 60 * (((g - b) / delta) % 6)
    }
    else if (maximum === g) {
      hue = 60 * ((b - r) / delta + 2)
    }
    else {
      hue = 60 * ((r - g) / delta + 4)
    }
  }
  if (hue < 0) {
    hue += 360
  }
  return `${Math.round(hue)} ${Math.round(saturation * 100)}% ${Math.round(lightness * 100)}%`
}

/** 规范化任意输入为主色十六进制，非法值回落到默认主色。 */
export function normalizeThemeColor(value: unknown): string {
  return parseHexColor(value) ? rgbToHex(parseHexColor(value)!) : DEFAULT_THEME_COLOR
}

/** WCAG 相对亮度，用于对比度与色块墨色判定。 */
export function relativeLuminance(color: string): number {
  const rgb = parseHexColor(color) ?? parseHexColor(DEFAULT_THEME_COLOR)!
  return 0.2126 * srgbToLinear(rgb.r) + 0.7152 * srgbToLinear(rgb.g) + 0.0722 * srgbToLinear(rgb.b)
}

/** 两色的 WCAG 对比度，范围 1–21。 */
export function contrastRatio(first: string, second: string): number {
  const [lighter, darker] = [relativeLuminance(first), relativeLuminance(second)].sort((a, b) => b - a)
  return (lighter + 0.05) / (darker + 0.05)
}

/**
 * 色块上对勾/文字的墨色。
 * 背景够深用白，够浅用近黑——自定义主色可能是亮黄或近白，写死白色会看不见。
 */
export function readableInkOn(color: string): string {
  return contrastRatio(color, '#FFFFFF') >= contrastRatio(color, '#111827') ? '#FFFFFF' : '#111827'
}

/** 主色的 OKLCH 描述，供测试断言感知亮度与彩度。 */
export function describeThemeColor(color: string): Oklch {
  return oklabToOklch(rgbToOklab(parseHexColor(color) ?? parseHexColor(DEFAULT_THEME_COLOR)!))
}

/** 两色的 OKLab 欧氏距离，用于判断两个预设是否"看起来像同一个颜色"。 */
export function themeColorDistance(first: string, second: string): number {
  const left = rgbToOklab(parseHexColor(first) ?? parseHexColor(DEFAULT_THEME_COLOR)!)
  const right = rgbToOklab(parseHexColor(second) ?? parseHexColor(DEFAULT_THEME_COLOR)!)
  return Math.hypot(left.l - right.l, left.a - right.a, left.b - right.b)
}

/**
 * 由单个主色推导全部色阶。
 *
 * 浅色模式：hover/active/deep 依次压暗，light-x 依次向白混合。
 * 暗色模式：hover 反而提亮（深底上更亮才像"被点亮"），light-x 向暗色基底混合，
 * dark-2 取更亮的一档，符合 Element Plus 暗色主题的语义。
 */
export function buildThemeColorScale(value: unknown, scheme: ThemeColorScheme = 'light'): ThemeColorScale {
  const primary = normalizeThemeColor(value)
  const isDark = scheme === 'dark'
  const blendTarget = isDark ? DARK_SURFACE_COLOR : '#FFFFFF'
  const tone = isDark
    ? {
        hover: shiftLightness(primary, 0.055),
        active: shiftLightness(primary, 0.02),
        deep: shiftLightness(primary, 0.1),
        dark2: mixInOklab(primary, '#FFFFFF', 0.7),
      }
    : {
        hover: mixInOklab(primary, '#000000', 0.9),
        active: mixInOklab(primary, '#000000', 0.8),
        deep: mixInOklab(primary, '#000000', 0.68),
        dark2: mixInOklab(primary, '#000000', 0.8),
      }

  return {
    primary,
    ...tone,
    hsl: toHslToken(primary),
    light3: mixInOklab(primary, blendTarget, 0.7),
    light5: mixInOklab(primary, blendTarget, 0.5),
    light7: mixInOklab(primary, blendTarget, 0.3),
    light8: mixInOklab(primary, blendTarget, 0.2),
    light9: mixInOklab(primary, blendTarget, 0.1),
  }
}

/** 把色阶展开成写进 `<html>` 的 CSS 变量表。 */
export function toCssVariables(scale: ThemeColorScale): Record<string, string> {
  return {
    '--color-primary': scale.primary,
    '--color-primary-hover': scale.hover,
    '--color-primary-active': scale.active,
    '--color-primary-deep': scale.deep,
    '--primary': scale.hsl,
    '--ring': scale.hsl,
    '--el-color-primary': scale.primary,
    '--el-color-primary-light-3': scale.light3,
    '--el-color-primary-light-5': scale.light5,
    '--el-color-primary-light-7': scale.light7,
    '--el-color-primary-light-8': scale.light8,
    '--el-color-primary-light-9': scale.light9,
    '--el-color-primary-dark-2': scale.dark2,
  }
}

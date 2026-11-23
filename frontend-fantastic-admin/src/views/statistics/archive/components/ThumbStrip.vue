<script setup lang="ts">
import type { GalleryImage, ViewMode } from '../types'
import { isMotionEnabled } from '@/utils/motion-preference'
import { useThumbLayout } from '../composables/useThumbLayout'
import { getTypeLabel } from '../constants'
import { isFocusSelectSuppressed } from '../utils/viewer-close-focus-guard'

defineOptions({ name: 'ThumbStrip' })

const props = withDefaults(defineProps<{
  images: GalleryImage[]
  selectedIndex: number
  isSelected: (img: GalleryImage) => boolean
  thumbnailSize?: number
  preloadCount?: number
}>(), {
  thumbnailSize: 200,
  preloadCount: 20,
})

const emit = defineEmits<{
  select: [index: number]
  toggle: [img: GalleryImage]
}>()

const thumbsContainer = ref<HTMLElement | null>(null)
const thumbRefs = ref<(HTMLElement | null)[]>([])
const failedImageKeys = ref<Set<string>>(new Set())
const viewMode = defineModel<ViewMode>('viewMode', { default: 'thumb' })
const thumbnailSize = toRef(props, 'thumbnailSize')
const preloadCount = toRef(props, 'preloadCount')

const { thumbItemWidth, pageSize, visibleCount, resetVisible } = useThumbLayout(
  thumbsContainer,
  viewMode,
  thumbnailSize,
  preloadCount,
)

const displayed = computed(() => props.images.slice(0, visibleCount.value))

function selectItem(index: number) {
  emit('select', index)
}

function onCardFocus(index: number) {
  // 全屏查看器关闭时 focus-trap 会还原焦点到打开前的卡片，
  // 还原引发的 focus 不切换预览（避免 P1→P5 关闭后回跳 P1）
  if (isFocusSelectSuppressed()) {
    return
  }
  selectItem(index)
}

function toggleItem(img: GalleryImage, event: Event) {
  event.stopPropagation()
  emit('toggle', img)
}

function onMetaClick(img: GalleryImage, event: MouseEvent) {
  // 缩略图模式下悬浮条整块可点：点页码/类型文字等同点勾选框；
  // 列表模式 meta 是行内文字，不拦截，保持「点整行 = 选中预览」的原行为。
  if (viewMode.value === 'thumb') {
    toggleItem(img, event)
  }
}

function imageKey(img: GalleryImage, index: number) {
  return String(img.imageUrl || img.id || img.filename || index)
}

function isImageFailed(img: GalleryImage, index: number) {
  return !img.imageUrl || failedImageKeys.value.has(imageKey(img, index))
}

function onImageError(img: GalleryImage, index: number) {
  const next = new Set(failedImageKeys.value)
  next.add(imageKey(img, index))
  failedImageKeys.value = next
}

/* 加载完成打标：写目标元素 class，不走响应式状态，缩略图条整批加载零重渲染。 */
function onImgLoad(event: Event) {
  const target = event.target as HTMLImageElement
  if (target.naturalWidth > 0) {
    target.classList.add('is-loaded')
  }
}

/* 卡片上的 Enter/Space 由页面级快捷键统一接管（回车=勾选当前预览图、空格=开关全屏），
 * 焦点跟随预览（@focus=selectItem），无需卡片本地处理；
 * 勾选框是独立控件，保留自己的 Enter/Space 语义。 */
function onToggleKeydown(img: GalleryImage, event: KeyboardEvent) {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    event.stopPropagation()
    emit('toggle', img)
  }
}

function scrollToIndex(index: number, smooth = true) {
  if (index >= visibleCount.value) {
    visibleCount.value = Math.max(visibleCount.value, index + pageSize.value)
  }
  nextTick(() => {
    const container = thumbsContainer.value
    const target = thumbRefs.value[index]
    if (!container || !target) {
      return
    }
    const top = target.offsetTop - (container.clientHeight - target.clientHeight) / 2
    const scrollTop = Math.max(0, top)
    if (smooth && isMotionEnabled() && 'scrollBehavior' in document.documentElement.style) {
      container.scrollTo({ top: scrollTop, behavior: 'smooth' })
    }
    else {
      container.scrollTop = scrollTop
    }
  })
}

defineExpose({ resetVisible, scrollToIndex })
</script>

<template>
  <section class="thumb-panel" :class="viewMode">
    <div ref="thumbsContainer" class="thumb-strip" :class="viewMode">
      <div
        v-for="(img, index) in displayed"
        :key="img.id || img.filename || index"
        :ref="(el: any) => { thumbRefs[index] = el }"
        class="thumb-item"
        :class="{ active: index === props.selectedIndex, checked: props.isSelected(img) }"
        :style="viewMode === 'thumb' ? { width: `${thumbItemWidth}px` } : {}"
        role="button"
        :aria-current="index === props.selectedIndex ? 'true' : undefined"
        :aria-label="`第 ${index + 1} 张影像，${getTypeLabel(img.btype)}`"
        tabindex="0"
        @focus="onCardFocus(index)"
        @click="selectItem(index)"
        @dblclick="toggleItem(img, $event)"
      >
        <div
          v-if="viewMode === 'thumb' && isImageFailed(img, index)"
          class="thumb-image-placeholder"
          role="img"
          :aria-label="`第 ${index + 1} 张影像加载失败`"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M4 5.5A1.5 1.5 0 0 1 5.5 4h13A1.5 1.5 0 0 1 20 5.5v9.88l-3.03-3.03a1 1 0 0 0-1.41 0l-1.2 1.2-3.34-3.34a1 1 0 0 0-1.41 0L4 15.82V5.5Zm0 13.14 6.32-6.32 3.34 3.34a1 1 0 0 0 1.41 0l1.2-1.2L20 18.2v.3a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5v.14ZM15.5 9A1.5 1.5 0 1 0 15.5 6a1.5 1.5 0 0 0 0 3Z" fill="currentColor" />
          </svg>
          <span>加载失败</span>
        </div>
        <img
          v-else-if="viewMode === 'thumb'"
          :key="img.imageUrl"
          :src="img.imageUrl"
          :alt="`第 ${index + 1} 张影像缩略图`"
          loading="lazy"
          @load="onImgLoad"
          @error="onImageError(img, index)"
        >
        <div class="thumb-meta" @click="onMetaClick(img, $event)">
          <!-- 勾选框放在页码前面：影像左上角不再压一块角标，meta 行就是「勾选 + 页码 + 类型」 -->
          <span
            class="thumb-check"
            :class="{ checked: props.isSelected(img) }"
            role="checkbox"
            :aria-checked="props.isSelected(img)"
            tabindex="0"
            @click="toggleItem(img, $event)"
            @keydown="onToggleKeydown(img, $event)"
          >
            <svg v-if="props.isSelected(img)" viewBox="0 0 16 16" width="10" height="10"><path d="M13.485 4.485a1 1 0 0 1 0 1.415l-6.5 6.5a1 1 0 0 1-1.414 0l-3-3a1 1 0 1 1 1.414-1.414L6.278 10.586l5.793-5.793a1 1 0 0 1 1.414 0z" fill="currentColor" /></svg>
          </span>
          <span class="thumb-page">P{{ img.pages ?? '-' }}</span>
          <small>{{ getTypeLabel(img.btype) }}</small>
        </div>
      </div>
      <div v-if="!props.images.length" class="empty-list">
        暂无影像
      </div>
      <div v-else-if="visibleCount < props.images.length" class="load-hint">
        滚动加载更多（{{ visibleCount }}/{{ props.images.length }}）
      </div>
    </div>
  </section>
</template>

<style scoped>
.thumb-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.thumb-strip {
  box-sizing: border-box;
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-content: flex-start;
  width: 100%;
  min-width: 0;
  padding: 8px;
  overflow-y: auto;
  scrollbar-gutter: stable;
  background: var(--surface-muted);
  border: 1px solid var(--divider);
  border-radius: var(--mrr-radius-card);
}

.thumb-strip.list {
  flex-flow: column nowrap;
}

.thumb-strip.list .thumb-item {
  grid-template-columns: minmax(0, 1fr);
  gap: 8px;
  align-items: center;
  width: 100% !important;
  min-height: 42px;
  padding: 8px var(--mrr-space-3);
}

.thumb-item {
  position: relative;
  box-sizing: border-box;
  display: grid;
  gap: 5px;
  min-width: 0;
  padding: var(--mrr-space-2);
  cursor: pointer;

  /* 双击卡片是「切换选中」手势，禁掉浏览器默认的文本选区（会把页码/类型名拖蓝） */
  user-select: none;
  background: var(--surface);
  border: 1px solid var(--divider);
  border-radius: var(--mrr-radius-md);
  transition: border-color var(--mrr-motion-feedback) var(--mrr-ease-out), box-shadow var(--mrr-motion-feedback) var(--mrr-ease-out);
}

/* 键盘焦点不再单独画圈：@focus → selectItem 让预览跟随焦点，active 圈即唯一指示；
 * 这里压掉浏览器 UA 的默认焦点圈（卡片基类没设 outline:none，必须显式关掉）。
 * 勾选框是独立控件，保留自己的焦点外圈。 */
.thumb-item:focus-visible {
  outline: none;
}

.thumb-check:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.thumb-item:hover {
  border-color: var(--mrr-navigation-active-border);
}

/*
 * 两种「选中」用形态区分：
 * - checked（勾选待复制）→ 面：淡蓝底填充，外圈不加强调描边
 * - active（正在预览）→ 线：底色不变，主题色实线双圈描边
 * 两者各设不同属性互不覆盖，叠加态自动 = 填充 + 实线圈。
 */
.thumb-item.checked {
  background: var(--mrr-navigation-active);
}

.thumb-item.active {
  border-color: var(--color-primary);
  box-shadow: inset 0 0 0 1px var(--color-primary);
}

/*
 * 勾选框默认不再带白色实底：选中卡片是浅蓝底，白色角标叠上去会像「两层底色」。
 * 常驻形态只有一层描边（白描边 + 轻投影，暗色影像和浅色卡片上都看得清），
 * 悬停时才填充白色给出可点的暗示；勾选后为主题色实底 + 白勾。
 */
.thumb-check {
  position: relative; /* 行内占位，::after 命中区相对自身扩展 */
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  color: var(--mrr-primary-foreground);
  cursor: pointer;
  background: transparent;
  border: 1.5px solid rgb(255 255 255 / 85%);
  border-radius: var(--mrr-radius-sm);
  transition: color var(--mrr-motion-feedback) var(--mrr-ease-out), background-color var(--mrr-motion-feedback) var(--mrr-ease-out), border-color var(--mrr-motion-feedback) var(--mrr-ease-out);
}

/* 命中区比视觉块大一圈，但只在上下与左侧外扩，不盖住右侧页码的点击范围。 */
.thumb-check::after {
  position: absolute;
  inset: -4px -2px -4px -4px;
  content: "";
  border-radius: inherit;
}

.thumb-check:hover {
  background: rgb(255 255 255 / 86%);
  border-color: rgb(255 255 255 / 95%);
}

.thumb-check.checked {
  color: var(--mrr-primary-foreground);
  background: var(--color-primary);
  border-color: var(--color-primary);
  box-shadow: none;
}

.thumb-item img,
.thumb-image-placeholder {
  box-sizing: border-box;
  width: 100%;
  border-radius: var(--mrr-radius-sm);
}

.thumb-item img {
  display: block;
  height: auto;
  object-fit: contain;
  background: var(--surface-alt);
}

/* 未加载完成时显示低频微光占位（关动效时为静态占位底色），加载后由图像内容覆盖。
 * 同时给未就绪缩略图保底高度：height:auto 的空 img 会塌陷成 0 高，卡片会随加载跳动。 */
.thumb-item img:not(.is-loaded) {
  min-height: 96px;
  background-image: linear-gradient(100deg, transparent 30%, rgb(255 255 255 / 45%) 50%, transparent 70%);
  background-size: 220% 100%;
  animation: var(--mrr-motion-decor, thumb-shimmer var(--mrr-motion-loop-soft, 1.5s) linear infinite);
}

@supports (aspect-ratio: 3 / 4) {
  .thumb-item img:not(.is-loaded) {
    min-height: 0;
    aspect-ratio: 3 / 4;
  }
}

@keyframes thumb-shimmer {
  from { background-position: 180% 0; }
  to { background-position: -80% 0; }
}

.thumb-image-placeholder {
  display: flex;
  flex-direction: column;
  gap: var(--mrr-space-2);
  align-items: center;
  justify-content: center;
  min-height: 200px;
  aspect-ratio: 3 / 4;
  padding: var(--mrr-space-3);
  font-size: 11px;
  color: var(--text-tertiary);
  text-align: center;
  background:
    linear-gradient(135deg, hsl(var(--primary) / 4%), transparent 60%),
    var(--surface-alt);
  border: 1px dashed var(--divider);
}

@supports (aspect-ratio: 3 / 4) {
  .thumb-image-placeholder {
    min-height: 0;
  }
}

.thumb-image-placeholder svg {
  width: 26px;
  height: 26px;
  opacity: 0.62;
}

/*
 * 绝对定位元素 width:auto 时按内容收缩（shrink-to-fit），
 * 只锚定左下角即可让深色背景宽度跟随文字，不再横向撑满整张卡片；
 * max-width 兜底防止类型名过长时溢出卡片，此时 small 走省略号。
 * 遮罩 72% 不透明度：55% 在白底文档影像上会被垫亮成中灰，
 * 白字对比度跌到 3.6/2.9（AA 要求 4.5），72% 后页码 6.3、类型名 5.4。
 */
.thumb-meta {
  position: absolute;
  bottom: 11px;
  left: 11px;
  display: flex;
  gap: var(--mrr-space-2);
  align-items: center;
  min-width: 0;
  max-width: calc(100% - 22px);
  padding: 4px 8px;
  background: rgb(15 23 42 / 72%);
  border-radius: var(--mrr-radius-sm);
}

/* 缩略图模式下整块悬浮条可点（见 onMetaClick），悬停微亮给出可点暗示 */
.thumb-strip.thumb .thumb-meta {
  cursor: pointer;
}

.thumb-strip.thumb .thumb-meta:hover {
  background: rgb(15 23 42 / 82%);
}

/* 悬浮条压在影像上，文字固定用浅色，不随主题反转（遮罩本身恒为深色）。 */
.thumb-page {
  flex: none;
  font-size: 12px;
  font-weight: 800;
  color: rgb(255 255 255 / 92%);
}

.thumb-item small {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;

  /* 白底文档上遮罩会被垫亮，82% 白字才能在遮罩最浅处也保持 5.4:1 对比度 */
  color: rgb(255 255 255 / 82%);
  white-space: nowrap;
}

/*
 * 列表模式没有影像可悬浮，meta 行回到行内样式，
 * 文字色也恢复主题色（列表行的底色是卡片底色而非深遮罩）。
 */
.thumb-strip.list .thumb-meta {
  position: static;
  padding: 0;
  background: none;
  border-radius: 0;
}

.thumb-strip.list .thumb-page {
  color: var(--text-primary);
}

.thumb-strip.list .thumb-item small {
  color: var(--text-secondary);
}

.empty-list,
.load-hint {
  grid-column: 1 / -1;
  padding: 16px 0;
  font-size: 12px;
  color: var(--text-tertiary);
  text-align: center;
}

@media (max-width: 1100px) {
  .thumb-panel {
    max-height: 240px;
  }

  .thumb-panel.list {
    max-height: 220px;
  }
}
</style>

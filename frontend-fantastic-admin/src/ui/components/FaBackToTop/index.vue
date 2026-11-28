<script setup lang="ts">
import { isMotionEnabled } from '@/utils/motion-preference'

defineOptions({
  name: 'BackTop',
})

const transitionClass = {
  enterActiveClass: 'ease-mrr-out duration-mrr-overlay',
  enterFromClass: 'opacity-0 translate-y-4 lg-translate-y-0 lg-scale-95',
  enterToClass: 'opacity-100 translate-y-0 lg-scale-100',
  leaveActiveClass: 'ease-mrr-in duration-mrr-move',
  leaveFromClass: 'opacity-100 translate-y-0 lg-scale-100',
  leaveToClass: 'opacity-0 translate-y-4 lg-translate-y-0 lg-scale-95',
}

onMounted(() => {
  window.addEventListener('scroll', handleScroll)
  handleScroll()
})
onBeforeUnmount(() => {
  window.removeEventListener('scroll', handleScroll)
  clearTimers()
})

let hideTimer: ReturnType<typeof setTimeout> | undefined
let revealTimer: ReturnType<typeof setTimeout> | undefined
const show = ref(false)
const scrollTop = ref(0)
const focused = ref(false)
function clearTimers() {
  clearTimeout(hideTimer)
  clearTimeout(revealTimer)
}
function handleScroll() {
  scrollTop.value = document.documentElement.scrollTop
}
watch(scrollTop, (val) => {
  if (val >= 200) {
    handleMouseenter()
    handleMouseleave()
  }
  else {
    clearTimers()
    show.value = false
    focused.value = false
  }
})
function handleMouseenter() {
  clearTimers()
  revealTimer = setTimeout(() => { show.value = true }, 0)
}
function handleMouseleave() {
  clearTimeout(hideTimer)
  if (!focused.value) {
    hideTimer = setTimeout(() => { show.value = false }, 2000)
  }
}
function handleFocus() {
  focused.value = true
  handleMouseenter()
}
function handleBlur() {
  focused.value = false
  handleMouseleave()
}

function handleBackToTop() {
  document.documentElement.scrollTo({
    top: 0,
    // 空间动效开关对 CSS 的 scroll-behavior 生效，但 JS 的 behavior 选项不受其约束。
    behavior: isMotionEnabled() ? 'smooth' : 'auto',
  })
}
</script>

<template>
  <Teleport to="body">
    <Transition v-bind="transitionClass">
      <el-button v-if="scrollTop >= 200" :auto-insert-space="false" aria-label="回到顶部" class="mrr-button-flow mrr-button-square mrr-button-round transition-[right,inset-inline-end,opacity,transform] fixed inset-b-4 z-1000 rounded-full -inset-e-9" :class="{ 'inset-e-3!': show }" @mouseenter="handleMouseenter" @mouseleave="handleMouseleave" @focus="handleFocus" @blur="handleBlur" @click="handleBackToTop">
        <FaIcon name="i-lucide:arrow-up-to-line" class="size-4" aria-hidden="true" />
      </el-button>
    </Transition>
  </Teleport>
</template>

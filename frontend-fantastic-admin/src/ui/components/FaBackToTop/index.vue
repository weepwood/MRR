<script setup lang="ts">
import { useTimeoutFn } from '@vueuse/core'
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
})

let timeout: (() => void) | undefined

const show = ref(false)
const scrollTop = ref(0)
function handleScroll() {
  scrollTop.value = document.documentElement.scrollTop
}
watch(scrollTop, (val) => {
  if (val >= 200) {
    handleMouseenter()
    handleMouseleave()
  }
})

function handleMouseenter() {
  timeout?.()
  // 此处setTimeout是为了避免移动端下和click事件同时触发
  setTimeout(() => {
    show.value = true
  }, 0)
}

function handleMouseleave() {
  ;({ stop: timeout } = useTimeoutFn(() => {
    show.value = false
  }, 2000))
}

function handleBackToTop() {
  show.value && document.documentElement.scrollTo({
    top: 0,
    // 空间动效开关对 CSS 的 scroll-behavior 生效，但 JS 的 behavior 选项不受其约束。
    behavior: isMotionEnabled() ? 'smooth' : 'auto',
  })
}
</script>

<template>
  <Teleport to="body">
    <Transition v-bind="transitionClass">
      <FaButton v-if="scrollTop >= 200" variant="outline" size="icon" aria-label="回到顶部" class="transition-[right,inset-inline-end,opacity,transform] fixed inset-b-4 z-1000 rounded-full -inset-e-9" :class="{ 'inset-e-3!': show }" @mouseenter="handleMouseenter" @mouseleave="handleMouseleave" @click="handleBackToTop">
        <FaIcon name="i-lucide:arrow-up-to-line" class="size-6" />
      </FaButton>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { useClipboard } from '@vueuse/core'
import { notify } from '@/utils/notification'

defineOptions({
  name: 'LinkView',
})

const route = useRoute()

const { copy, copied } = useClipboard()
watch(copied, (val) => {
  val && notify.success('复制成功')
})

function open() {
  window.open(route.meta.link, '_blank')
}
</script>

<template>
  <div class="absolute h-full w-full flex flex-col">
    <Transition name="slide-right" mode="out-in" appear>
      <FaPageMain :key="route.meta.link" class="flex flex-1 flex-col justify-center">
        <div class="flex flex-col items-center">
          <FaIcon name="i-lucide:globe" class="size-30 text-primary/80" />
          <div class="my-2 text-xl dark:text-white">
            是否访问此链接
          </div>
          <!-- 复制链接改为真实按钮：原 div 无法用键盘聚焦和触发。 -->
          <button type="button" class="link-copy my-2 max-w-[300px] cursor-pointer text-center text-[14px] text-secondary-foreground/50" aria-label="复制链接" @click="route.meta.link && copy(route.meta.link)">
            <FaTooltip text="复制链接">
              <div class="line-clamp-3">
                {{ route.meta.link }}
              </div>
            </FaTooltip>
          </button>
          <FaButton class="my-4" @click="open">
            <FaIcon name="i-lucide:external-link" />
            立即访问
          </FaButton>
        </div>
      </FaPageMain>
    </Transition>
  </div>
</template>

<style scoped>
/* 只过渡 transform / opacity：原写法 `transition: 0.2s` 等于 all，连 margin-left 也参与布局动画。 */
.slide-right-enter-active {
  transition: transform var(--mrr-motion-move) var(--mrr-ease-out), opacity var(--mrr-motion-move) var(--mrr-ease-out);
}

.slide-right-leave-active {
  transition: transform var(--mrr-motion-feedback) var(--mrr-ease-in), opacity var(--mrr-motion-feedback) var(--mrr-ease-in);
}

.slide-right-enter-from {
  opacity: 0;
  transform: var(--mrr-motion-decor, translateX(-8px));
}

.slide-right-leave-to {
  opacity: 0;
  transform: var(--mrr-motion-decor, translateX(8px));
}

.link-copy {
  padding: 0;
  background: transparent;
  border: 0;
}

.link-copy:focus-visible {
  outline: 2px solid var(--mrr-ring);
  outline-offset: 2px;
  border-radius: var(--mrr-radius-sm);
}
</style>

<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/ui/components/FaDropdown/dropdown-menu'
import { cn } from '@/utils'
import Profile from './profile.vue'

const props = withDefaults(defineProps<{
  onlyAvatar?: boolean
  dropdownAlign?: 'start' | 'center' | 'end'
  dropdownSide?: 'left' | 'right' | 'top' | 'bottom'
  buttonVariant?: 'secondary' | 'ghost'
  class?: HTMLAttributes['class']
}>(), {
  dropdownAlign: 'end',
  dropdownSide: 'right',
  buttonVariant: 'ghost',
})

const router = useRouter()

const settingsStore = useSettingsStore()
const userStore = useUserStore()

const isProfileShow = ref(false)
</script>

<template>
  <DropdownMenu :modal="false">
    <DropdownMenuTrigger as-child>
      <FaButton
        :variant="buttonVariant" :aria-label="`账号菜单：${userStore.account}`" :title="userStore.account" :class="cn('account-trigger min-w-0 flex-center gap-2 px-2 py-0 text-[13px]', {
          'size-[var(--mrr-control-height)] shrink-0 p-1': onlyAvatar,
        }, props.class)"
      >
        <FaAvatar :src="userStore.avatar" :class="cn('size-7 shrink-0', { 'size-full': onlyAvatar })">
          <FaIcon name="i-lucide:user-round" class="size-6 text-[var(--text-secondary)]" />
        </FaAvatar>
        <div v-if="!onlyAvatar" class="min-w-0 flex-center-between flex-1 gap-2">
          <div class="flex-1 truncate text-start">
            {{ userStore.account }}
          </div>
          <FaIcon name="i-lucide:chevrons-up-down" class="size-4 shrink-0 text-[var(--text-secondary)]" />
        </div>
      </FaButton>
    </DropdownMenuTrigger>
    <DropdownMenuContent
      :align="dropdownAlign" :side="dropdownSide" :side-offset="8" :collision-padding="12"
      class="account-menu max-w-[calc(100vw-24px)] w-[248px] rounded-[var(--mrr-radius-lg)] p-1.5"
    >
      <DropdownMenuLabel class="p-2">
        <div class="flex items-center gap-3">
          <FaAvatar :src="userStore.avatar" class="size-9 shrink-0">
            <FaIcon name="i-lucide:user-round" class="size-7 text-[var(--text-secondary)]" />
          </FaAvatar>
          <div class="min-w-0 flex-1">
            <div class="account-name text-sm text-[var(--text-primary)] font-semibold leading-5">
              {{ userStore.account }}
            </div>
            <div class="mt-0.5 text-xs text-[var(--text-secondary)] font-normal leading-4">
              当前登录账号
            </div>
          </div>
        </div>
      </DropdownMenuLabel>
      <DropdownMenuSeparator class="mx-2 my-1 bg-[var(--mrr-border)]" />
      <DropdownMenuGroup>
        <DropdownMenuItem
          v-if="settingsStore.settings.home.enable" class="min-h-9 cursor-pointer gap-2.5 px-2.5"
          @select="router.push({ path: settingsStore.settings.home.fullPath })"
        >
          <FaIcon name="i-lucide:house" class="size-4 shrink-0 text-[var(--text-secondary)]" />
          {{ settingsStore.settings.home.title }}
        </DropdownMenuItem>
        <DropdownMenuItem class="min-h-9 cursor-pointer gap-2.5 px-2.5" @select="isProfileShow = true">
          <FaIcon name="i-lucide:user-round" class="size-4 shrink-0 text-[var(--text-secondary)]" />
          个人设置
        </DropdownMenuItem>
      </DropdownMenuGroup>
      <DropdownMenuSeparator class="mx-2 my-1 bg-[var(--mrr-border)]" />
      <DropdownMenuItem
        class="account-logout min-h-9 cursor-pointer gap-2.5 px-2.5 text-[var(--color-danger)] focus:bg-[var(--mrr-navigation-hover)] focus:text-[var(--color-danger)]"
        @select="userStore.logout(settingsStore.settings.home.fullPath)"
      >
        <FaIcon name="i-lucide:log-out" class="size-4 shrink-0" />
        退出登录
      </DropdownMenuItem>
    </DropdownMenuContent>
  </DropdownMenu>
  <FaModal v-model="isProfileShow" align-center :header="false" :footer="false" :close-on-click-overlay="false" :close-on-press-escape="false" class="h-500px max-w-xl overflow-hidden" content-class="min-h-full p-0 flex">
    <Profile />
  </FaModal>
</template>

<style scoped>
.account-trigger[data-state="open"] {
  background: var(--mrr-navigation-active);
}

.account-name {
  overflow-wrap: break-word;
}
</style>

<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
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
const menuOpen = ref(false)
const trigger = ref<{ $el: HTMLElement }>()
const placement = computed(() => props.dropdownAlign === 'center' ? props.dropdownSide : `${props.dropdownSide}-${props.dropdownAlign}` as const)
const popperOptions = {
  modifiers: [
    { name: 'offset', options: { offset: [0, 8] } },
    { name: 'preventOverflow', options: { padding: 12, tether: false } },
  ],
}
function closeOnEscape(event: KeyboardEvent) {
  if (event.key === 'Escape' && menuOpen.value) {
    nextTick(() => trigger.value?.$el.focus({ preventScroll: true }))
  }
}
</script>

<template>
  <el-dropdown class="mrr-account-dropdown" trigger="click" :placement="placement" :show-arrow="false" :popper-options="popperOptions" popper-class="mrr-account-menu" @visible-change="menuOpen = $event">
    <el-button
      ref="trigger" :data-state="menuOpen ? 'open' : 'closed'" text :auto-insert-space="false" :data-variant="buttonVariant" :data-avatar-only="onlyAvatar" :aria-label="`账号菜单：${userStore.account}`" :title="userStore.account" :class="cn('mrr-migrated-toolbar account-trigger min-w-0 flex-center gap-2 px-2 py-0 text-[13px]', {
        'size-[var(--mrr-control-height)] shrink-0 p-1': onlyAvatar,
      }, props.class)"
    >
      <el-avatar :src="userStore.avatar" class="mrr-account-avatar" :class="{ 'mrr-account-avatar--fill': onlyAvatar }">
        <FaIcon name="i-lucide:user-round" class="size-6 text-[var(--text-secondary)]" />
      </el-avatar>
      <div v-if="!onlyAvatar" class="min-w-0 flex-center-between flex-1 gap-2">
        <div class="flex-1 truncate text-start">
          {{ userStore.account }}
        </div>
        <FaIcon name="i-lucide:chevrons-up-down" class="size-4 shrink-0 text-[var(--text-secondary)]" />
      </div>
    </el-button>
    <template #dropdown>
      <el-dropdown-menu @keydown="closeOnEscape">
        <li role="presentation" class="mrr-account-label p-2">
          <div class="flex items-center gap-3">
            <el-avatar :src="userStore.avatar" class="mrr-account-avatar mrr-account-avatar--menu">
              <FaIcon name="i-lucide:user-round" class="size-7 text-[var(--text-secondary)]" />
            </el-avatar>
            <div class="min-w-0 flex-1">
              <div class="account-name text-sm text-[var(--text-primary)] font-semibold leading-5">
                {{ userStore.account }}
              </div>
              <div class="mt-0.5 text-xs text-[var(--text-secondary)] font-normal leading-4">
                当前登录账号
              </div>
            </div>
          </div>
        </li>
        <li role="separator" class="mrr-account-separator" />
        <el-dropdown-item
          v-if="settingsStore.settings.home.enable" class="min-h-9 cursor-pointer gap-2.5 px-2.5"
          @click="router.push({ path: settingsStore.settings.home.fullPath })"
        >
          <FaIcon name="i-lucide:house" class="size-4 shrink-0 text-[var(--text-secondary)]" />
          {{ settingsStore.settings.home.title }}
        </el-dropdown-item>
        <el-dropdown-item class="min-h-9 cursor-pointer gap-2.5 px-2.5" @click="isProfileShow = true">
          <FaIcon name="i-lucide:user-round" class="size-4 shrink-0 text-[var(--text-secondary)]" />
          个人设置
        </el-dropdown-item>
        <li role="separator" class="mrr-account-separator" />
        <el-dropdown-item
          class="account-logout min-h-9 cursor-pointer gap-2.5 px-2.5 text-[var(--color-danger)] focus:bg-[var(--mrr-navigation-hover)] focus:text-[var(--color-danger)]"
          @click="userStore.logout(settingsStore.settings.home.fullPath)"
        >
          <FaIcon name="i-lucide:log-out" class="size-4 shrink-0" />
          退出登录
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
  <MrrDialog v-model="isProfileShow" title="个人设置" align-center :header="false" :footer="false" :close-on-click-overlay="false" :close-on-press-escape="false" class="mrr-profile-dialog overflow-hidden" content-class="min-h-full p-0 flex">
    <Profile />
  </MrrDialog>
</template>

<style scoped>
.account-trigger[data-state="open"] {
  background: var(--mrr-navigation-active);
}

.account-name {
  overflow-wrap: break-word;
}
</style>

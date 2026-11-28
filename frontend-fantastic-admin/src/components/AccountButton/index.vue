<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { useConfirmedLogout } from '@/composables/useConfirmedLogout'
import { cn } from '@/utils'
import Profile from './profile.vue'

const props = withDefaults(defineProps<{
  onlyAvatar?: boolean
  dropdownAlign?: 'start' | 'center' | 'end'
  dropdownSide?: 'left' | 'right' | 'top' | 'bottom'
  buttonVariant?: 'secondary' | 'ghost' | 'sidebar'
  class?: HTMLAttributes['class']
}>(), {
  dropdownAlign: 'end',
  dropdownSide: 'right',
  buttonVariant: 'ghost',
})

const router = useRouter()

const settingsStore = useSettingsStore()
const userStore = useUserStore()
const { confirmLogout, logoutPending } = useConfirmedLogout()

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
        <FaIcon name="i-lucide:user-round" class="size-6 text-[var(--text-secondary)]" :class="{ 'account-sidebar-glyph': buttonVariant === 'sidebar' }" />
      </el-avatar>
      <div v-if="!onlyAvatar" class="min-w-0 flex-center-between flex-1 gap-2">
        <div class="flex-1 truncate text-start">
          {{ userStore.account }}
        </div>
        <FaIcon name="i-lucide:chevrons-up-down" class="account-trigger-chevron size-4 shrink-0 text-[var(--text-secondary)]" />
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
          :disabled="logoutPending" @click="confirmLogout(settingsStore.settings.home.fullPath)"
        >
          <FaIcon name="i-lucide:log-out" class="size-4 shrink-0" />
          退出登录
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
  <MrrDialog v-model="isProfileShow" title="个人设置" width="460px" align-center :footer="false" :close-on-click-overlay="false" :close-on-press-escape="false" class="mrr-profile-dialog">
    <Profile />
  </MrrDialog>
</template>

<style scoped>
.account-trigger[data-state="open"] {
  background: var(--mrr-navigation-active);
}

/* Expanded single sidebar only: a quiet account row, not a toolbar button. */
.el-button.mrr-migrated-toolbar.account-trigger.is-text[data-variant="sidebar"] {
  width: 100%;
  height: calc(var(--g-toolbar-height) - 8px);
  min-height: calc(var(--g-toolbar-height) - 8px);
  padding: 0 var(--mrr-space-2);
  font-size: var(--mrr-font-size-sm);
  font-weight: 500;
  color: var(--text-primary);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--mrr-radius-lg);
  box-shadow: none;
}

.el-button.mrr-migrated-toolbar.account-trigger.is-text[data-variant="sidebar"]:not(.is-disabled):hover,
.el-button.mrr-migrated-toolbar.account-trigger.is-text[data-variant="sidebar"][data-state="open"] {
  background: var(--mrr-navigation-hover);
  border-color: transparent;
}

.account-trigger[data-variant="sidebar"] :deep(.mrr-account-avatar) {
  --el-avatar-size: 28px;

  background: var(--mrr-muted);
  border-radius: var(--mrr-radius-lg);
}

.account-trigger[data-variant="sidebar"] :deep(.account-sidebar-glyph) {
  width: 20px;
  height: 20px;
  font-size: 20px;
}

.account-trigger[data-variant="sidebar"] :deep(.account-trigger-chevron) {
  width: 14px;
  height: 14px;
  font-size: 14px;
}

.account-name {
  overflow-wrap: break-word;
}
</style>

<script setup lang="ts">
import type { Menu } from '#/global'

defineOptions({ name: 'MenuVisibilitySettings' })

type MenuTitle = string | (() => string) | undefined

interface MenuVisibilityItem {
  path: string
  title: string
  icon?: string
  depth: number
  protected: boolean
}

interface MenuVisibilityGroup {
  title: string
  icon?: string
  items: MenuVisibilityItem[]
}

const menuStore = useMenuStore()
const menuVisibilityStore = useMenuVisibilityStore()

function resolveTitle(title: MenuTitle, fallback: string): string {
  if (typeof title === 'function') {
    return title()
  }
  return title || fallback
}

function collectMenuItems(menus: Menu.recordRaw[], depth = 0): MenuVisibilityItem[] {
  const items: MenuVisibilityItem[] = []

  menus.forEach((menu) => {
    if (menu.meta?.menu === false) {
      return
    }

    if (menu.path) {
      items.push({
        path: menu.path,
        title: resolveTitle(menu.meta?.title, menu.path),
        icon: menu.meta?.icon,
        depth,
        protected: menuVisibilityStore.isProtected(menu.path),
      })
    }

    if (menu.children?.length) {
      items.push(...collectMenuItems(menu.children, depth + 1))
    }
  })

  return items
}

const menuGroups = computed<MenuVisibilityGroup[]>(() => {
  return menuStore.menuVisibilityMenus
    .map(group => ({
      title: resolveTitle(group.meta?.title, '其他菜单'),
      icon: group.meta?.icon,
      items: collectMenuItems(group.children),
    }))
    .filter(group => group.items.length > 0)
})

const hiddenCount = computed(() => {
  return menuGroups.value.reduce((count, group) => (
    count + group.items.filter(item => !menuVisibilityStore.isVisible(item.path)).length
  ), 0)
})

function handleVisibilityChange(path: string, visible: boolean) {
  menuVisibilityStore.setVisible(path, visible)
}
</script>

<template>
  <section class="menu-visibility-settings">
    <div class="menu-visibility-heading">
      <div>
        <strong>菜单显示管理</strong>
        <small>按需隐藏侧边栏和顶部导航中的页面入口，不影响通过地址直接访问页面。</small>
      </div>
      <div class="menu-visibility-actions">
        <el-tag v-if="hiddenCount > 0" type="info" effect="plain" round>
          已隐藏 {{ hiddenCount }} 项
        </el-tag>
        <el-button size="small" :disabled="hiddenCount === 0" @click="menuVisibilityStore.showAll()">
          <FaIcon name="i-ri:eye-line" />
          全部显示
        </el-button>
      </div>
    </div>

    <div v-if="menuGroups.length" class="menu-group-list">
      <section v-for="group in menuGroups" :key="group.title" class="menu-group-card">
        <header class="menu-group-header">
          <span class="menu-group-icon">
            <FaIcon :name="group.icon || 'i-ri:folder-3-line'" />
          </span>
          <div>
            <strong>{{ group.title }}</strong>
            <small>{{ group.items.length }} 个菜单入口</small>
          </div>
        </header>

        <div class="menu-item-list">
          <div v-for="item in group.items" :key="item.path" class="menu-visibility-item">
            <div class="menu-item-copy" :style="{ '--menu-depth': item.depth }">
              <span class="menu-item-icon">
                <FaIcon :name="item.icon || 'i-ri:file-list-3-line'" />
              </span>
              <div>
                <strong>
                  {{ item.title }}
                  <el-tag v-if="item.protected" size="small" type="success" effect="plain" round>
                    固定显示
                  </el-tag>
                </strong>
                <small>{{ item.path }}</small>
              </div>
            </div>
            <FaSwitch
              :model-value="menuVisibilityStore.isVisible(item.path)"
              :disabled="item.protected"
              @update:model-value="handleVisibilityChange(item.path, $event)"
            />
          </div>
        </div>
      </section>
    </div>

    <el-empty v-else :image-size="64" description="暂无可配置的菜单" />
  </section>
</template>

<style scoped>
.menu-visibility-settings {
  margin-top: 18px;
}

.menu-visibility-heading {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.menu-visibility-heading strong,
.menu-visibility-heading small {
  display: block;
}

.menu-visibility-heading strong {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.menu-visibility-heading small {
  margin-top: 3px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.menu-visibility-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
  align-items: center;
}

.menu-visibility-actions :deep(.el-button) {
  gap: 5px;
}

.menu-group-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.menu-group-card {
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}

.menu-group-header {
  display: flex;
  gap: 10px;
  align-items: center;
  min-height: 58px;
  padding: 10px 12px;
  background: var(--el-fill-color-extra-light);
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.menu-group-header strong,
.menu-group-header small {
  display: block;
}

.menu-group-header strong {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.menu-group-header small {
  margin-top: 2px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.menu-group-icon,
.menu-item-icon {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
}

.menu-group-icon {
  width: 32px;
  height: 32px;
  color: var(--el-color-primary);
  background: color-mix(in srgb, var(--el-color-primary) 10%, var(--el-bg-color));
  border-radius: 8px;
}

.menu-item-list {
  padding: 2px 0;
}

.menu-visibility-item {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  min-height: 58px;
  padding: 9px 12px;
}

.menu-visibility-item + .menu-visibility-item {
  border-top: 1px solid var(--el-border-color-extra-light);
}

.menu-item-copy {
  display: flex;
  min-width: 0;
  gap: 10px;
  align-items: center;
  padding-left: calc(var(--menu-depth, 0) * 14px);
}

.menu-item-copy > div {
  min-width: 0;
}

.menu-item-copy strong,
.menu-item-copy small {
  display: block;
}

.menu-item-copy strong {
  overflow: hidden;
  font-size: 13px;
  color: var(--el-text-color-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.menu-item-copy strong :deep(.el-tag) {
  margin-left: 5px;
  vertical-align: 1px;
}

.menu-item-copy small {
  margin-top: 3px;
  overflow: hidden;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 10px;
  color: var(--el-text-color-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.menu-item-icon {
  width: 28px;
  height: 28px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border-radius: 7px;
}

@media (max-width: 900px) {
  .menu-group-list {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .menu-visibility-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .menu-visibility-actions {
    flex-wrap: wrap;
  }
}
</style>

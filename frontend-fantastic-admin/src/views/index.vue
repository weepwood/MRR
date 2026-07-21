<route lang="yaml">
meta:
  title: 管理概览
  icon: ant-design:home-twotone
</route>

<script setup lang="ts">
import { computed } from 'vue'
import MrrPageHeader from '@/components/MrrPageHeader/index.vue'
import MrrPageShell from '@/components/MrrPageShell/index.vue'
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'
import { useUserStore } from '@/store/modules/user'
import { hasPermission } from '@/utils/session'

defineOptions({ name: 'HomePage' })

interface ModuleEntry {
  title: string
  description: string
  icon: string
  path: string
  permission?: string
}

interface ModuleGroup {
  key: string
  title: string
  description: string
  icon: string
  modules: ModuleEntry[]
}

const userStore = useUserStore()

const moduleGroups: ModuleGroup[] = [
  {
    key: 'business',
    title: '业务处理',
    description: '病案记录、患者信息、影像档案与实体档案管理。',
    icon: 'i-ant-design:appstore-outlined',
    modules: [
      {
        title: '影像档案袋',
        description: '查询并浏览病案影像、缩略图和患者信息。',
        icon: 'i-ant-design:folder-open-twotone',
        path: '/archive/embed',
        permission: 'record:read',
      },
      {
        title: '记录管理',
        description: '维护病案扫描记录及其基础数据。',
        icon: 'i-ant-design:database-twotone',
        path: '/records',
        permission: 'record:read',
      },
      {
        title: '患者管理',
        description: '查看患者与住院病案的关联信息。',
        icon: 'i-ant-design:team-outlined',
        path: '/patients',
        permission: 'record:read',
      },
      {
        title: '档案装箱',
        description: '管理病案装箱、箱号和档案状态。',
        icon: 'i-ant-design:inbox-outlined',
        path: '/archive-boxes',
        permission: 'record:read',
      },
      {
        title: 'OSS 迁移管理',
        description: '查看与管理病案影像迁移任务。',
        icon: 'i-ant-design:cloud-upload-outlined',
        path: '/oss-migration',
        permission: 'record:read',
      },
    ],
  },
  {
    key: 'data',
    title: '数据与统计',
    description: '查看扫描统计、明细数据和数据库关系。',
    icon: 'i-ant-design:area-chart-outlined',
    modules: [
      {
        title: '病案扫描统计',
        description: '按年份、科室和扫描状态查看统计结果。',
        icon: 'i-ant-design:area-chart-outlined',
        path: '/statistics',
        permission: 'statistics:read',
      },
      {
        title: '统计明细',
        description: '查看病案扫描与档案记录的详细数据。',
        icon: 'i-ant-design:profile-twotone',
        path: '/statistics-detail',
        permission: 'statistics:read',
      },
      {
        title: '数据关系工作台',
        description: '检查数据库表之间的关联和数据完整性。',
        icon: 'i-ant-design:apartment-outlined',
        path: '/data-relations',
        permission: 'system:read',
      },
    ],
  },
  {
    key: 'system',
    title: '系统管理',
    description: '管理用户、权限和系统运行配置。',
    icon: 'i-ant-design:setting-outlined',
    modules: [
      {
        title: '用户管理',
        description: '创建用户、审核账号和重置密码。',
        icon: 'i-ant-design:user-outlined',
        path: '/users',
        permission: 'user:manage',
      },
      {
        title: '权限管理',
        description: '维护角色权限和功能访问范围。',
        icon: 'i-ant-design:lock-twotone',
        path: '/permissions',
        permission: 'role:read',
      },
      {
        title: '系统设置',
        description: '配置系统信息、档案浏览、安全和界面外观。',
        icon: 'i-ant-design:tool-twotone',
        path: '/settings',
        permission: 'system:read',
      },
    ],
  },
  {
    key: 'operations',
    title: '运维与审计',
    description: '查看系统运行状态、访问审计和接口性能。',
    icon: 'i-ant-design:control-outlined',
    modules: [
      {
        title: '病案图片访问审计',
        description: '查询病案影像查看、下载和访问记录。',
        icon: 'i-ant-design:security-scan-outlined',
        path: '/audit-images',
        permission: 'log:read',
      },
      {
        title: '日志管理',
        description: '检索系统日志、操作记录和异常信息。',
        icon: 'i-ant-design:file-search-outlined',
        path: '/logs',
        permission: 'log:read',
      },
      {
        title: '系统监控',
        description: '查看服务、数据库、内存和运行指标。',
        icon: 'i-ant-design:dashboard-twotone',
        path: '/monitoring',
        permission: 'system:read',
      },
      {
        title: '服务状态',
        description: '快速确认系统服务是否正常运行。',
        icon: 'i-ant-design:check-circle-twotone',
        path: '/system-status',
        permission: 'system:read',
      },
      {
        title: '接口响应分析',
        description: '分析慢接口、响应时间和近期性能趋势。',
        icon: 'i-ant-design:fund-projection-screen-outlined',
        path: '/response-analysis',
        permission: 'system:read',
      },
      {
        title: '认证接口测试',
        description: '验证登录、鉴权和外部系统接入流程。',
        icon: 'i-ant-design:api-twotone',
        path: '/auth-test',
        permission: 'user:manage',
      },
    ],
  },
  {
    key: 'help',
    title: '帮助',
    description: '查看系统使用说明和相关文档。',
    icon: 'i-ant-design:question-circle-outlined',
    modules: [
      {
        title: '帮助与文档',
        description: '查看功能说明、使用流程和常见问题。',
        icon: 'i-ant-design:read-outlined',
        path: '/help',
      },
    ],
  },
]

const featuredPaths = ['/archive/embed', '/records', '/statistics', '/monitoring']

function canAccess(module: ModuleEntry) {
  return !module.permission || hasPermission(module.permission)
}

const visibleModuleGroups = computed(() => moduleGroups
  .map(group => ({
    ...group,
    modules: group.modules.filter(canAccess),
  }))
  .filter(group => group.modules.length > 0))

const accessibleModules = computed(() => visibleModuleGroups.value.flatMap(group => group.modules))

const featuredModules = computed(() => featuredPaths.flatMap((path) => {
  const module = accessibleModules.value.find(item => item.path === path)
  return module ? [module] : []
}))

const accessibleModuleCount = computed(() => accessibleModules.value.length)
const welcomeDescription = computed(() => `欢迎回来${userStore.profile?.displayName ? `，${userStore.profile.displayName}` : ''}。从这里快速进入当前账号可访问的业务模块。`)
</script>

<template>
  <MrrPageShell width="fluid">
    <MrrPageHeader
      eyebrow="Medical Record Repository"
      title="管理概览"
      :description="welcomeDescription"
      icon="i-ant-design:home-twotone"
    >
      <template #badge>
        <span class="overview-count">
          <span class="overview-count__dot" />
          {{ accessibleModuleCount }} 个可访问模块
        </span>
      </template>
    </MrrPageHeader>

    <MrrSectionCard
      v-if="featuredModules.length"
      title="快捷访问"
      description="优先展示日常使用频率较高的功能。"
      icon="i-ant-design:thunderbolt-outlined"
    >
      <template #actions>
        <span class="section-count">{{ featuredModules.length }} 个入口</span>
      </template>

      <div class="module-grid module-grid--featured">
        <router-link
          v-for="module in featuredModules"
          :key="module.path"
          :to="module.path"
          class="module-entry"
        >
          <span class="module-entry__icon" aria-hidden="true">
            <FaIcon :name="module.icon" />
          </span>
          <span class="module-entry__copy">
            <strong>{{ module.title }}</strong>
            <small>{{ module.description }}</small>
          </span>
          <FaIcon name="i-ri:arrow-right-s-line" class="module-entry__arrow" />
        </router-link>
      </div>
    </MrrSectionCard>

    <MrrSectionCard
      v-for="group in visibleModuleGroups"
      :key="group.key"
      :title="group.title"
      :description="group.description"
      :icon="group.icon"
    >
      <template #actions>
        <span class="section-count">{{ group.modules.length }} 个模块</span>
      </template>

      <div class="module-grid">
        <router-link
          v-for="module in group.modules"
          :key="module.path"
          :to="module.path"
          class="module-entry"
        >
          <span class="module-entry__icon" aria-hidden="true">
            <FaIcon :name="module.icon" />
          </span>
          <span class="module-entry__copy">
            <strong>{{ module.title }}</strong>
            <small>{{ module.description }}</small>
          </span>
          <FaIcon name="i-ri:arrow-right-s-line" class="module-entry__arrow" />
        </router-link>
      </div>
    </MrrSectionCard>
  </MrrPageShell>
</template>

<style scoped>
.overview-count,
.section-count {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  min-height: 25px;
  padding: 3px 9px;
  font-size: 12px;
  font-weight: 550;
  color: var(--mrr-secondary-foreground);
  background: var(--mrr-secondary);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-pill);
}

.overview-count__dot {
  width: 6px;
  height: 6px;
  background: var(--mrr-primary);
  border-radius: 50%;
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--mrr-primary) 10%, transparent);
}

.module-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--mrr-space-3);
}

.module-grid--featured {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.module-entry {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) 16px;
  gap: 10px;
  align-items: center;
  min-width: 0;
  min-height: 82px;
  padding: 14px;
  color: inherit;
  text-decoration: none;
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-lg);
  box-shadow: var(--mrr-shadow-xs);
  transition:
    color var(--mrr-motion-fast) ease,
    background-color var(--mrr-motion-fast) ease,
    border-color var(--mrr-motion-fast) ease,
    box-shadow var(--mrr-motion-fast) ease,
    transform var(--mrr-motion-fast) var(--mrr-ease-out);
}

.module-entry:hover {
  background: color-mix(in srgb, var(--mrr-primary) 2%, var(--mrr-card));
  border-color: color-mix(in srgb, var(--mrr-primary) 28%, var(--mrr-border));
  box-shadow: var(--mrr-shadow-sm);
}

.module-entry:active {
  transform: translateY(1px);
}

.module-entry:focus-visible {
  outline: 2px solid var(--mrr-ring);
  outline-offset: 2px;
}

.module-entry__icon {
  display: grid;
  width: 32px;
  height: 32px;
  font-size: 15px;
  color: var(--mrr-primary);
  background: color-mix(in srgb, var(--mrr-primary) 8%, var(--mrr-card));
  border: 1px solid color-mix(in srgb, var(--mrr-primary) 16%, var(--mrr-border));
  border-radius: var(--mrr-radius-md);
  place-items: center;
}

.module-entry__copy {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.module-entry__copy strong {
  overflow: hidden;
  font-size: 14px;
  font-weight: 650;
  line-height: 1.4;
  color: var(--mrr-card-foreground);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.module-entry__copy small {
  display: -webkit-box;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.45;
  color: var(--mrr-muted-foreground);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.module-entry__arrow {
  color: var(--mrr-muted-foreground);
  transition:
    color var(--mrr-motion-fast) ease,
    transform var(--mrr-motion-fast) var(--mrr-ease-out);
}

.module-entry:hover .module-entry__arrow {
  color: var(--mrr-primary);
  transform: translateX(1px);
}

@media (width <= 1180px) {
  .module-grid,
  .module-grid--featured {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width <= 700px) {
  .module-grid,
  .module-grid--featured {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .module-entry,
  .module-entry__arrow {
    transition: none;
  }

  .module-entry:active,
  .module-entry:hover .module-entry__arrow {
    transform: none;
  }
}
</style>

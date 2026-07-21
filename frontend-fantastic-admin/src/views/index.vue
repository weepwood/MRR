<route lang="yaml">
meta:
  title: 管理概览
  icon: ant-design:home-twotone
</route>

<script setup lang="ts">
import { computed } from 'vue'
import { useUserStore } from '@/store/modules/user'
import { hasPermission } from '@/utils/session'

defineOptions({ name: 'HomePage' })

type ModuleTone = 'primary' | 'success' | 'warning' | 'danger' | 'info'

interface ModuleEntry {
  title: string
  description: string
  icon: string
  path: string
  permission?: string
  tone: ModuleTone
}

interface ModuleGroup {
  key: string
  title: string
  description: string
  modules: ModuleEntry[]
}

const userStore = useUserStore()

const moduleGroups: ModuleGroup[] = [
  {
    key: 'business',
    title: '业务处理',
    description: '病案记录、患者信息、影像档案与实体档案管理。',
    modules: [
      {
        title: '影像档案袋',
        description: '查询并浏览病案影像、缩略图和患者信息。',
        icon: 'i-ant-design:folder-open-twotone',
        path: '/archive/embed',
        permission: 'record:read',
        tone: 'primary',
      },
      {
        title: '记录管理',
        description: '维护病案扫描记录及其基础数据。',
        icon: 'i-ant-design:database-twotone',
        path: '/records',
        permission: 'record:read',
        tone: 'success',
      },
      {
        title: '患者管理',
        description: '查看患者与住院病案的关联信息。',
        icon: 'i-ant-design:team-outlined',
        path: '/patients',
        permission: 'record:read',
        tone: 'info',
      },
      {
        title: '档案装箱',
        description: '管理病案装箱、箱号和档案状态。',
        icon: 'i-ant-design:inbox-outlined',
        path: '/archive-boxes',
        permission: 'record:read',
        tone: 'warning',
      },
      {
        title: 'OSS 迁移管理',
        description: '查看与管理病案影像迁移任务。',
        icon: 'i-ant-design:cloud-upload-outlined',
        path: '/oss-migration',
        permission: 'record:read',
        tone: 'primary',
      },
    ],
  },
  {
    key: 'data',
    title: '数据与统计',
    description: '查看扫描统计、明细数据和数据库关系。',
    modules: [
      {
        title: '病案扫描统计',
        description: '按年份、科室和扫描状态查看统计结果。',
        icon: 'i-ant-design:area-chart-outlined',
        path: '/statistics',
        permission: 'statistics:read',
        tone: 'success',
      },
      {
        title: '统计明细',
        description: '查看病案扫描与档案记录的详细数据。',
        icon: 'i-ant-design:profile-twotone',
        path: '/statistics-detail',
        permission: 'statistics:read',
        tone: 'primary',
      },
      {
        title: '数据关系工作台',
        description: '检查数据库表之间的关联和数据完整性。',
        icon: 'i-ant-design:apartment-outlined',
        path: '/data-relations',
        permission: 'system:read',
        tone: 'warning',
      },
    ],
  },
  {
    key: 'system',
    title: '系统管理',
    description: '管理用户、权限和系统运行配置。',
    modules: [
      {
        title: '用户管理',
        description: '创建用户、审核账号和重置密码。',
        icon: 'i-ant-design:user-outlined',
        path: '/users',
        permission: 'user:manage',
        tone: 'primary',
      },
      {
        title: '权限管理',
        description: '维护角色权限和功能访问范围。',
        icon: 'i-ant-design:lock-twotone',
        path: '/permissions',
        permission: 'role:read',
        tone: 'danger',
      },
      {
        title: '系统设置',
        description: '配置系统信息、档案浏览、安全和界面外观。',
        icon: 'i-ant-design:tool-twotone',
        path: '/settings',
        permission: 'system:read',
        tone: 'info',
      },
    ],
  },
  {
    key: 'operations',
    title: '运维与审计',
    description: '查看系统运行状态、访问审计和接口性能。',
    modules: [
      {
        title: '病案图片访问审计',
        description: '查询病案影像查看、下载和访问记录。',
        icon: 'i-ant-design:security-scan-outlined',
        path: '/audit-images',
        permission: 'log:read',
        tone: 'warning',
      },
      {
        title: '日志管理',
        description: '检索系统日志、操作记录和异常信息。',
        icon: 'i-ant-design:file-search-outlined',
        path: '/logs',
        permission: 'log:read',
        tone: 'info',
      },
      {
        title: '系统监控',
        description: '查看服务、数据库、内存和运行指标。',
        icon: 'i-ant-design:dashboard-twotone',
        path: '/monitoring',
        permission: 'system:read',
        tone: 'success',
      },
      {
        title: '服务状态',
        description: '快速确认系统服务是否正常运行。',
        icon: 'i-ant-design:check-circle-twotone',
        path: '/system-status',
        permission: 'system:read',
        tone: 'success',
      },
      {
        title: '接口响应分析',
        description: '分析慢接口、响应时间和近期性能趋势。',
        icon: 'i-ant-design:fund-projection-screen-outlined',
        path: '/response-analysis',
        permission: 'system:read',
        tone: 'primary',
      },
      {
        title: '认证接口测试',
        description: '验证登录、鉴权和外部系统接入流程。',
        icon: 'i-ant-design:api-twotone',
        path: '/auth-test',
        permission: 'user:manage',
        tone: 'danger',
      },
    ],
  },
  {
    key: 'help',
    title: '帮助',
    description: '查看系统使用说明和相关文档。',
    modules: [
      {
        title: '帮助与文档',
        description: '查看功能说明、使用流程和常见问题。',
        icon: 'i-ant-design:read-outlined',
        path: '/help',
        tone: 'info',
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
</script>

<template>
  <div class="overview-page">
    <header class="overview-hero">
      <div class="hero-copy">
        <span class="hero-eyebrow">MRR 工作台</span>
        <h1>管理概览</h1>
        <p>
          欢迎回来{{ userStore.profile?.displayName ? `，${userStore.profile.displayName}` : '' }}。从这里快速进入当前账号可访问的业务模块。
        </p>
      </div>
      <div class="hero-summary" aria-label="可访问模块数量">
        <strong>{{ accessibleModuleCount }}</strong>
        <span>个可访问模块</span>
      </div>
    </header>

    <section v-if="featuredModules.length" class="featured-section" aria-labelledby="featured-title">
      <div class="section-heading">
        <div>
          <span class="section-kicker">常用功能</span>
          <h2 id="featured-title">快捷访问</h2>
        </div>
        <p>优先进入日常使用频率较高的功能。</p>
      </div>

      <div class="featured-grid">
        <router-link
          v-for="module in featuredModules"
          :key="module.path"
          :to="module.path"
          class="featured-card"
          :data-tone="module.tone"
        >
          <span class="featured-icon">
            <FaIcon :name="module.icon" />
          </span>
          <span class="featured-copy">
            <strong>{{ module.title }}</strong>
            <small>{{ module.description }}</small>
          </span>
          <FaIcon name="i-ri:arrow-right-line" class="card-arrow" />
        </router-link>
      </div>
    </section>

    <div class="module-groups">
      <section
        v-for="group in visibleModuleGroups"
        :key="group.key"
        class="module-group"
        :aria-labelledby="`${group.key}-title`"
      >
        <div class="section-heading">
          <div>
            <span class="section-kicker">{{ group.modules.length }} 个模块</span>
            <h2 :id="`${group.key}-title`">{{ group.title }}</h2>
          </div>
          <p>{{ group.description }}</p>
        </div>

        <div class="module-grid">
          <router-link
            v-for="module in group.modules"
            :key="module.path"
            :to="module.path"
            class="module-card"
            :data-tone="module.tone"
          >
            <span class="module-icon">
              <FaIcon :name="module.icon" />
            </span>
            <span class="module-copy">
              <strong>{{ module.title }}</strong>
              <small>{{ module.description }}</small>
            </span>
            <FaIcon name="i-ri:arrow-right-s-line" class="card-arrow" />
          </router-link>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.overview-page {
  display: grid;
  gap: 22px;
  min-width: 0;
}

.overview-hero {
  display: flex;
  gap: 24px;
  align-items: center;
  justify-content: space-between;
  min-height: 152px;
  padding: 26px 28px;
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-xl);
  box-shadow: var(--mrr-shadow-sm);
}

.hero-copy {
  min-width: 0;
}

.hero-eyebrow,
.section-kicker {
  display: block;
  font-size: 11px;
  font-weight: 700;
  color: var(--mrr-primary);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-copy h1 {
  margin: 7px 0 8px;
  font-size: clamp(24px, 3vw, 34px);
  line-height: 1.15;
  color: var(--mrr-foreground);
  letter-spacing: -0.03em;
}

.hero-copy p,
.section-heading p {
  margin: 0;
  line-height: 1.7;
  color: var(--mrr-muted-foreground);
}

.hero-copy p {
  max-width: 680px;
  font-size: 13px;
}

.hero-summary {
  display: grid;
  flex: 0 0 auto;
  min-width: 132px;
  padding: 18px;
  text-align: center;
  background: var(--mrr-muted);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-lg);
}

.hero-summary strong {
  font-size: 28px;
  line-height: 1;
  color: var(--mrr-primary);
}

.hero-summary span {
  margin-top: 7px;
  font-size: 11px;
  color: var(--mrr-muted-foreground);
}

.featured-section,
.module-group {
  display: grid;
  gap: 14px;
}

.section-heading {
  display: flex;
  gap: 20px;
  align-items: end;
  justify-content: space-between;
}

.section-heading h2 {
  margin: 4px 0 0;
  font-size: 17px;
  color: var(--mrr-foreground);
}

.section-heading p {
  max-width: 520px;
  font-size: 12px;
  text-align: right;
}

.featured-grid,
.module-grid {
  display: grid;
  gap: 12px;
}

.featured-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.module-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.featured-card,
.module-card {
  display: grid;
  min-width: 0;
  color: inherit;
  text-decoration: none;
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  transition:
    border-color var(--mrr-motion-fast) ease,
    box-shadow var(--mrr-motion-fast) ease,
    transform var(--mrr-motion-fast) var(--mrr-ease-out);
}

.featured-card {
  grid-template-columns: 44px minmax(0, 1fr) 18px;
  gap: 12px;
  align-items: center;
  min-height: 112px;
  padding: 18px;
  border-radius: var(--mrr-radius-xl);
}

.module-card {
  grid-template-columns: 40px minmax(0, 1fr) 16px;
  gap: 12px;
  align-items: start;
  min-height: 98px;
  padding: 16px;
  border-radius: var(--mrr-radius-lg);
}

.featured-card:hover,
.module-card:hover {
  border-color: var(--mrr-primary);
  box-shadow: var(--mrr-shadow-sm);
  transform: translateY(-2px);
}

.featured-card:active,
.module-card:active {
  transform: translateY(0) scale(0.99);
}

.featured-card:focus-visible,
.module-card:focus-visible {
  outline: 2px solid var(--mrr-primary);
  outline-offset: 2px;
}

.featured-icon,
.module-icon {
  display: grid;
  color: var(--module-color);
  background: var(--module-background);
  border: 1px solid var(--module-border);
  place-items: center;
}

.featured-icon {
  width: 44px;
  height: 44px;
  font-size: 21px;
  border-radius: 13px;
}

.module-icon {
  width: 40px;
  height: 40px;
  font-size: 18px;
  border-radius: 11px;
}

.featured-copy,
.module-copy {
  display: grid;
  min-width: 0;
}

.featured-copy {
  gap: 5px;
}

.module-copy {
  gap: 4px;
}

.featured-copy strong,
.module-copy strong {
  color: var(--mrr-foreground);
}

.featured-copy strong {
  font-size: 14px;
}

.module-copy strong {
  font-size: 13px;
}

.featured-copy small,
.module-copy small {
  display: -webkit-box;
  overflow: hidden;
  line-height: 1.55;
  color: var(--mrr-muted-foreground);
  -webkit-box-orient: vertical;
}

.featured-copy small {
  font-size: 11px;
  -webkit-line-clamp: 2;
}

.module-copy small {
  font-size: 10px;
  -webkit-line-clamp: 2;
}

.card-arrow {
  align-self: center;
  color: var(--mrr-muted-foreground);
  transition:
    color var(--mrr-motion-fast) ease,
    transform var(--mrr-motion-fast) var(--mrr-ease-out);
}

.featured-card:hover .card-arrow,
.module-card:hover .card-arrow {
  color: var(--mrr-primary);
  transform: translateX(2px);
}

.module-groups {
  display: grid;
  gap: 24px;
}

[data-tone="primary"] {
  --module-color: var(--el-color-primary);
  --module-background: var(--el-color-primary-light-9);
  --module-border: var(--el-color-primary-light-7);
}

[data-tone="success"] {
  --module-color: var(--el-color-success);
  --module-background: var(--el-color-success-light-9);
  --module-border: var(--el-color-success-light-7);
}

[data-tone="warning"] {
  --module-color: var(--el-color-warning);
  --module-background: var(--el-color-warning-light-9);
  --module-border: var(--el-color-warning-light-7);
}

[data-tone="danger"] {
  --module-color: var(--el-color-danger);
  --module-background: var(--el-color-danger-light-9);
  --module-border: var(--el-color-danger-light-7);
}

[data-tone="info"] {
  --module-color: var(--el-color-info);
  --module-background: var(--el-color-info-light-9);
  --module-border: var(--el-color-info-light-7);
}

@media (max-width: 1180px) {
  .featured-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .module-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 700px) {
  .overview-hero,
  .section-heading {
    align-items: flex-start;
  }

  .overview-hero {
    flex-direction: column;
    padding: 20px;
  }

  .hero-summary {
    grid-template-columns: auto 1fr;
    gap: 8px;
    align-items: baseline;
    min-width: 0;
    text-align: left;
  }

  .hero-summary span {
    margin-top: 0;
  }

  .section-heading {
    flex-direction: column;
    gap: 6px;
  }

  .section-heading p {
    text-align: left;
  }

  .featured-grid,
  .module-grid {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .featured-card,
  .module-card,
  .card-arrow {
    transition: none;
  }

  .featured-card:hover,
  .module-card:hover,
  .featured-card:active,
  .module-card:active,
  .featured-card:hover .card-arrow,
  .module-card:hover .card-arrow {
    transform: none;
  }
}
</style>

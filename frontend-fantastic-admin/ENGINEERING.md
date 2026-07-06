# 前端工程规范

> 本文档定义 MRR 前端项目的编码规范、架构约定和最佳实践。
> 与 `DESIGN.md`（视觉设计语言）和 `.trae/rules/`（通用编码风格）配合使用。

---

## 1. 技术栈总览

| 层次 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 框架 | Vue 3 | ^3.5 | Composition API + `<script setup>` |
| 语言 | TypeScript | ^5.9 | 类型安全 |
| 构建 | Vite | ^8.0 | HMR + 构建 |
| UI 组件 | Element Plus | ^2.13 | 表单/表格/弹窗/布局 |
| UI 框架 | Fantastic Admin (Fa*) | - | 内置 41 个业务组件 |
| CSS | UnoCSS (Wind3) | ^66.6 | 原子化 CSS |
| 状态管理 | Pinia | ^3.0 | 全局状态 |
| 路由 | Vue Router | ^5.0 | SPA 路由 |
| HTTP | Axios | ^1.14 | API 请求 |
| 表单验证 | VeeValidate + Zod | ^4.15 / ^4.3 | 声明式校验 |
| 图标 | Lucide Vue + Iconify | - | 统一图标 |
| 包管理 | pnpm | ^10.33 | 依赖管理 |
| 代码生成 | Plop | ^4.0 | 模板化创建 |
| Lint | ESLint (Antfu) | ^10.1 | 代码检查 |
| Style | Stylelint | ^17.6 | 样式检查 |

---

## 2. 项目结构

```
src/
├── api/                      # API 层
│   ├── index.ts              # Axios 实例 + 拦截器
│   ├── types.ts              # 所有 API 类型定义
│   └── modules/              # 按领域划分的 API 模块
├── assets/                   # 静态资源
│   ├── icons/                # SVG 图标
│   ├── images/               # 图片
│   └── styles/               # 全局样式
│       ├── globals.css       # CSS 变量 / 全局样式
│       ├── nprogress.css
│       └── resources/        # SCSS 变量与工具函数
├── components/               # 业务组件
│   ├── AccountButton/        # 用户头像/下拉组件
│   │   ├── index.vue
│   │   └── profile.vue
│   ├── AccountForm/          # 登录/注册/密码表单
│   └── shared/               # 共享业务组件
├── layouts/                  # 布局组件
│   ├── index.vue             # 主布局
│   └── components/
│       ├── Header/           # 顶部导航
│       ├── MainSidebar/      # 一级侧栏
│       ├── SubSidebar/       # 二级侧栏
│       ├── Topbar/           # 工具栏 + 面包屑 + 标签栏
│       └── ...
├── menu/                     # 菜单配置
├── mock/                     # Mock 数据 (开发环境)
├── router/
│   ├── index.ts              # 路由实例
│   ├── routes.ts             # 路由定义
│   ├── guards.ts             # 导航守卫
│   └── extensions.ts         # 路由扩展 (标签栏集成)
├── settings/                 # 应用配置
├── store/
│   └── modules/              # Pinia 状态模块
├── types/                    # TypeScript 类型声明
├── ui/
│   ├── components/           # Fa* 组件库 (41 个)
│   ├── provider/             # UI 提供者 (ElementPlus 安装)
│   └── shadcn/               # shadcn-vue 组件
├── utils/
│   ├── composables/          # 组合式函数
│   └── ...
└── views/                    # 页面组件 (文件系统路由)
    ├── index.vue             # 首页/仪表盘
    ├── login.vue             # 登录页
    ├── records/index.vue     # 扫描记录管理
    ├── statistics/index.vue  # 统计分析
    ├── users/index.vue       # 用户管理
    └── ...
```

### 2.1 目录约定

| 目录 | 职责 | 约束 |
|------|------|------|
| `api/modules/` | 每个后端领域一个文件 | 文件名与后端 API 对应，如 `records.ts` → `/api/v1/scan/*` |
| `views/` | 每个路由一个目录 | 由 `vite-plugin-pages` 自动注册，目录名 kebab-case |
| `components/` | 业务复用组件 | 每个组件一个目录，`index.vue` 为入口 |
| `ui/components/` | Fa* 通用 UI 组件 | 遵循框架组件设计规范 |
| `store/modules/` | 每个领域一个 Store | 组合式 API (`defineStore('name', () => {})`) |

---

## 3. Vue 3 编码约定

### 3.1 SFC 结构

每个 `.vue` 文件严格遵循以下区块顺序：

```vue
<route lang="yaml">
meta:
  title: 页面标题
  icon: icon-name
  constant: false
  auth: ['permission:code']
</route>

<script setup lang="ts">
defineOptions({ name: 'PageName' })

// 1. 类型导入 (from @/api/types 或 @/types)
import type { ScanRecord } from '@/api/types'

// 2. 组件导入 (局部组件)
import DetailDialog from './components/DetailDialog.vue'

// 3. Vue/Router/Pinia 组合式 API
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/modules/user'

// 4. API 模块导入
import { getRecords, deleteRecord } from '@/api/modules/records'

// 5. 响应式状态
const loading = ref(false)
const tableData = ref<ScanRecord[]>([])

// 6. 计算属性
const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

// 7. 函数定义 (按生命周期顺序)
async function loadData() { /* ... */ }
function handleSearch() { /* ... */ }
function handleCreate() { /* ... */ }
function handleEdit(row: ScanRecord) { /* ... */ }
function handleDelete(id: number) { /* ... */ }

// 8. 生命周期
onMounted(() => { loadData() })

// 9. 侦听器
watch(searchText, () => { loadData() })
</script>

<template>
  <!-- 模板 -->
</template>

<style scoped>
/* 作用域样式 */
</style>
```

### 3.2 命名规范

| 类别 | 规范 | 示例 |
|------|------|------|
| 组件名 | PascalCase | `RecordsPage`, `DetailDialog` |
| 目录名 | kebab-case | `records-statistics/`, `oss-migration/` |
| 变量 | camelCase | `tableData`, `searchText` |
| 常量 | UPPER_SNAKE | `BATCH_SIZE`, `MAX_FILE_SIZE` |
| 函数 | camelCase 动词前缀 | `loadData`, `handleSearch`, `formatDate` |
| 事件 | kebab-case | `@row-click`, `@update:model-value` |
| Props | camelCase | `pageSize`, `showDetail` |
| 类型 | PascalCase | `ScanRecord`, `LoginResponse` |
| Pinia Store | useXxxStore | `useUserStore`, `useSettingsStore` |
| API 函数 | 动词 + 资源 | `getRecords`, `deleteRecord`, `updateUser` |

### 3.3 `<script setup>` 约定

```vue
<script setup lang="ts">
// ✅ 正确: defineOptions 放在最前
defineOptions({ name: 'RecordsPage' })

// ✅ 正确: 使用 type-only 导入
import type { ScanRecord } from '@/api/types'

// ✅ 正确: defineProps / defineEmits 紧随导入之后
const props = defineProps<{ recordId: number }>()
const emit = defineEmits<{ close: []; saved: [id: number] }>()

// ❌ 避免: 在 setup 中使用选项式 API
// export default { methods: { ... } }

// ❌ 避免: 非必要的 this 引用
</script>
```

---

## 4. API 层规范

### 4.1 模块文件结构

每个 `api/modules/*.ts` 文件遵循统一模式——使用类型安全封装函数：

```typescript
import type { PaginatedResult, ScanRecord, ScanRequest } from '../types'
import { deleteRequest, getRequest, postRequest, putRequest } from '../index'

/** GET /api/v1/scan/page — 分页获取扫描记录 */
export function getScanList(params: { page: number, size: number }) {
  return getRequest<PaginatedResult<ScanRecord>>('/api/v1/scan/page', { params })
}

/** POST /api/v1/scan — 创建新的扫描记录 */
export function createScan(data: ScanRequest) {
  return postRequest<ScanRecord>('/api/v1/scan', data)
}

/** PUT /api/v1/scan/{id} — 更新扫描记录 */
export function updateScan(id: string | number, data: Partial<ScanRequest>) {
  return putRequest<ScanRecord>(`/api/v1/scan/${id}`, data)
}

/** DELETE /api/v1/scan/{id} — 删除扫描记录 */
export function deleteScan(id: string | number) {
  return deleteRequest(`/api/v1/scan/${id}`)
}

/** POST /api/v1/scan/batch-download — blob 下载（不走 JSON 解包） */
export function batchDownloadRecords(ids: (string | number)[]) {
  return api.post('/api/v1/scan/batch-download', { ids }, { responseType: 'blob' })
}
```

### 4.2 响应解包约定

Axios 拦截器已统一处理 `Result<T>` 的 `code/msg/data` 结构：

- **`getRequest<T>`/`postRequest<T>`/`putRequest<T>`/`deleteRequest<T>`**: 自动解包 `Result.data`，返回 `Promise<ApiResult<T>>`
- **`api.get`/`api.post` (raw)**: 仅用于 blob 二进制下载（设置 `responseType: 'blob'`）或需要 `skipGlobalError` 的特殊场景（如 auth 端点）

```typescript
// ✅ 标准 JSON API：用类型安全封装 (自动解包 Result.data)
const res = await getRequest<MyData>('/api/v1/resource')
console.log(res.data)  // 这里已经是 MyData

// ✅ Blob 下载：用原始 api.get (避免 JSON 解包损坏二进制)
const res = await api.get<Blob>('/api/v1/export', { responseType: 'blob' })

// ✅ Auth 端点：用 postRequest + skipGlobalError
postRequest<LoginResponse>('/api/v1/auth/login', payload, { skipGlobalError: true })
```

### 4.3 错误处理

```typescript
// API 层: 不捕获异常，让调用方处理
export function getScanList(params: { page: number, size: number }) {
  return getRequest<PaginatedResult<ScanRecord>>('/api/v1/scan/page', { params })
}

// 视图层: try/catch/finally
async function loadData() {
  loading.value = true
  try {
    const res = await getScanList({ page: page.value, size: pageSize.value })
    tableData.value = res.data?.list ?? []
    total.value = res.data?.total ?? 0
  }
  catch (err: unknown) {
    const msg = err instanceof Error ? err.message : '加载失败'
    ElMessage.error(msg)
  }
  finally {
    loading.value = false
  }
}
```

### 4.4 skipGlobalError 配置

Axios 实例请求配置支持 `skipGlobalError: true`，跳过全局 toast 错误提示：

```typescript
interface AxiosRequestConfig {
  retry?: boolean         // 启用重试 (最大 3 次)
  retryCount?: number     // 当前重试次数 (内部使用)
  skipGlobalError?: boolean  // 跳过全局错误提示 (auth 端点使用)
}
```

### 4.5 401 防抖

并发 401 响应时通过 `isLoggingOut` 标志防止多次登出重定向。

---

## 5. Pinia Store 规范

### 5.1 Store 定义模式

```typescript
// store/modules/user.ts
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin } from '@/api/modules/auth'

export const useUserStore = defineStore('user', () => {
  // 1. State (使用 ref)
  const token = ref(localStorage.getItem('token') || '')
  const profile = ref<AuthUser | null>(null)

  // 2. Getters (使用 computed)
  const isLoggedIn = computed(() => !!token.value)
  const roleCode = computed(() => profile.value?.roleCode ?? '')

  // 3. Actions (普通函数)
  async function login(username: string, password: string) {
    const res = await apiLogin({ username, password })
    token.value = res.token
    profile.value = res.user
    localStorage.setItem('token', res.token)
  }

  function logout() {
    token.value = ''
    profile.value = null
    localStorage.removeItem('token')
  }

  return { token, profile, isLoggedIn, roleCode, login, logout }
})
```

### 5.2 Store 职责划分

| Store | 职责 | 持久化 |
|-------|------|--------|
| `user` | 认证状态、用户信息、权限 | localStorage |
| `settings` | 主题/布局/偏好设置 | localStorage |
| `menu` | 菜单树生成与过滤 | 无 |
| `route` | 路由表生成 | 无 |
| `tabbar` | 标签栏状态 | 无 |
| `keepAlive` | 页面缓存管理 | 无 |

### 5.3 跨 Store 访问

```typescript
// store/modules/xxx.ts
import { useUserStore } from './user'

export const useSomeStore = defineStore('some', () => {
  const userStore = useUserStore()

  function canAccess(permission: string) {
    return userStore.permissions.includes(permission)
  }

  return { canAccess }
})
```

---

## 6. 路由规范

### 6.1 路由定义 (`router/routes.ts`)

```typescript
// 常量路由 (无需登录)
export const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login.vue'),
    meta: { title: '登录', constant: true },
  },
]

// 系统路由 (始终存在)
export const systemRoutes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'index',
    component: () => import('@/views/index.vue'),
    meta: { title: '首页', icon: 'dashboard', auth: true },
  },
]

// 异步路由 (权限过滤)
export const asyncRoutes: RouteRecordRaw[] = [
  {
    path: '/records',
    name: 'records',
    component: () => import('@/views/records/index.vue'),
    meta: { title: '扫描记录', icon: 'document', auth: ['record:read'] },
  },
]
```

### 6.2 路由元信息约定

```typescript
meta: {
  title: string        // 页面标题 (必填)
  icon?: string         // 菜单图标
  constant?: boolean   // 是否常量路由 (无需权限)
  auth?: string[]      // 所需权限 (满足任一即可访问)
  layout?: false       // false 表示不使用主布局 (如登录页)
  activeMenu?: string  // 高亮菜单项
  sort?: number        // 菜单排序
}
```

### 6.3 导航守卫

`router/guards.ts` 中守卫执行顺序：

1. **`setupRoutes`** — 检查登录态 → 获取权限 → 生成路由表
2. **`setupProgress`** — NProgress 加载条
3. **`setupTitle`** — 动态文档标题
4. **`setupKeepAlive`** — 页面缓存管理
5. **`setupOther`** — 滚动到顶部

---

## 7. 组件设计规范

### 7.1 组件分类

| 层级 | 目录 | 说明 | 示例 |
|------|------|------|------|
| **Ui** | `ui/components/` | 通用 UI 原子组件 (Fa*) | `FaButton`, `FaModal`, `FaCard` |
| **Shadcn** | `ui/shadcn/` | shadcn-vue 组件 | `Button`, `Dialog`, `Table` |
| **业务** | `components/` | 领域相关复用组件 | `AccountForm/LoginForm.vue` |
| **页面** | `views/` | 路由页面组件 | `records/index.vue` |
| **布局** | `layouts/` | 页面布局组件 | `index.vue`, `Header/` |

### 7.2 组件 Props 规范

```vue
<script setup lang="ts">
// ✅ 正确: 使用 interface 定义 props, 带 JSDoc
interface Props {
  /** 加载状态 */
  loading?: boolean
  /** 数据列表 */
  data: ScanRecord[]
  /** 分页信息 */
  pagination: { page: number; size: number; total: number }
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
})

// ✅ 正确: 复杂类型使用 type-only import
import type { ScanRecord } from '@/api/types'
</script>
```

### 7.3 Emits 规范

```vue
<script setup lang="ts">
// ✅ 正确: 使用类型化 emits
const emit = defineEmits<{
  close: []
  saved: [id: number]
  'update:model-value': [value: string]
}>()

// ❌ 避免: 字符串 emits (无类型安全)
// defineEmits(['close', 'saved'])
</script>
```

### 7.4 自定义指令

权限控制使用 `v-auth` 指令：

```vue
<template>
  <!-- 有 record:delete 权限时渲染 -->
  <el-button v-auth="'record:delete'" @click="handleDelete">
    删除
  </el-button>

  <!-- 有任一权限时渲染 -->
  <el-button v-auth="['record:manage', 'admin:all']">
    管理
  </el-button>
</template>
```

---

## 8. 样式规范

### 8.1 CSS 方案优先级

```
UnoCSS 原子类 (首选) → scoped CSS → 全局 CSS
```

### 8.2 UnoCSS 约定

```vue
<template>
  <!-- ✅ 正确: 使用 UnoCSS 原子类 -->
  <div class="flex items-center gap-2 p-4 bg-white rounded-lg shadow-sm">
    <span class="text-sm text-gray-500">总数:</span>
    <span class="text-lg font-semibold text-primary">{{ total }}</span>
  </div>

  <!-- ✅ 正确: 使用 UnoCSS 变体 -->
  <div class="hover:bg-gray-50 dark:bg-gray-800">
    <!-- 内容 -->
  </div>
</template>
```

### 8.3 scoped CSS 约定

```vue
<style scoped>
/* ✅ 正确: 使用 .page-shell 作为根容器类名, BEM 命名 */
.page-shell {
  .page-header {
    margin-bottom: 16px;
  }
  .search-form {
    display: flex;
    gap: 12px;
  }
}

/* ✅ 正确: CSS 变量通过 v-bind 连接响应式状态 */
.text-highlight {
  color: v-bind('themeColor');
}
</style>
```

### 8.4 页面布局模式

```vue
<template>
  <FaPageMain>
    <!-- 1. 摘要卡片 -->
    <div class="summary-grid grid grid-cols-4 gap-4 mb-4">
      <FaCard v-for="item in summary" :key="item.label">
        <div class="text-2xl font-bold">{{ item.value }}</div>
        <div class="text-sm text-gray-500">{{ item.label }}</div>
      </FaCard>
    </div>

    <!-- 2. 搜索表单 -->
    <el-form :model="filters" inline class="mb-4">
      <el-form-item label="关键字">
        <el-input v-model="filters.keyword" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
      </el-form-item>
    </el-form>

    <!-- 3. 数据表格 -->
    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="bah" label="病案号" />
      <el-table-column prop="filename" label="文件名" />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button text @click="handleEdit(row)">编辑</el-button>
          <el-button text type="danger" @click="handleDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 4. 分页 -->
    <el-pagination
      v-model:current-page="page"
      v-model:page-size="pageSize"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      class="mt-4 justify-center"
    />
  </FaPageMain>
</template>
```

---

## 9. 状态管理数据流

```
用户操作 → View (Vue SFC)
              │
              ▼
          API Module (api/modules/*.ts)
              │
              ▼
          Axios Instance (api/index.ts)
              │ 请求拦截器 → 注入 Token
              │ 响应拦截器 → 解包 Result<T>
              ▼
          后端 API
              │
              ▼
          API Module ← 返回 data.data
              │
              ▼
          View / Store
              │
              ▼
          更新响应式状态 → UI 更新
```

### 9.1 本地 vs 全局状态决策

| 状态类型 | 存放位置 | 示例 |
|----------|----------|------|
| 组件内部 UI 状态 | 组件的 `ref` | 弹窗显隐、表单加载中 |
| 跨组件/路由共享 | Pinia Store | 用户信息、菜单 |
| 服务端数据 | API 模块 | 扫描记录、统计 |
| URL 参数 | `useRoute().query` | 分页页码、搜索条件 |

```typescript
// ✅ 正确: 列表页数据使用局部 ref, 不放入 Store
const tableData = ref<ScanRecord[]>([])

// ✅ 正确: 全局认证信息放入 Store
const userStore = useUserStore()
const token = computed(() => userStore.token)
```

---

## 10. 表单验证规范

### 10.1 使用 VeeValidate + Zod

```vue
<script setup lang="ts">
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { z } from 'zod'

const validationSchema = toTypedSchema(z.object({
  username: z.string().min(1, '用户名不能为空'),
  password: z.string().min(6, '密码至少 6 位'),
}))

const { defineField, errors, handleSubmit } = useForm({
  validationSchema,
})

const [username, usernameAttrs] = defineField('username')
const [password, passwordAttrs] = defineField('password')

const onSubmit = handleSubmit(async (values) => {
  await login(values.username, values.password)
})
</script>

<template>
  <form @submit="onSubmit">
    <el-form-item label="用户名" :error="errors.username">
      <el-input v-model="username" v-bind="usernameAttrs" />
    </el-form-item>
    <el-form-item label="密码" :error="errors.password">
      <el-input v-model="password" type="password" v-bind="passwordAttrs" />
    </el-form-item>
    <el-button type="primary" native-type="submit">登录</el-button>
  </form>
</template>
```

---

## 11. 错误处理规范

```typescript
// API 层: 不吞异常, 向上抛出
export async function getRecords() {
  const { data } = await api.get('/api/v1/scan/page')
  return data.data
}

// 视图层: 统一 try/catch 模式
async function loadData() {
  loading.value = true
  try {
    const res = await getRecords(page.value, pageSize.value)
    tableData.value = res.list
    total.value = res.total
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

// 视图层: 写入操作需用户确认
async function handleDelete(id: number) {
  try {
    await ElMessageBox.confirm('确认删除?', '提示', { type: 'warning' })
    await deleteRecord(id)
    ElMessage.success('删除成功')
    await loadData()
  } catch {
    // 用户取消不做处理
  }
}
```

---

## 12. 性能规范

### 12.1 代码分割

```typescript
// ✅ 正确: 路由懒加载 (已由 vite-plugin-pages 自动处理)

// ✅ 正确: 组件异步加载
const DetailDialog = defineAsyncComponent(() =>
  import('./components/DetailDialog.vue')
)

// ❌ 避免: 非必要的大组件同步导入
```

### 12.2 响应式性能

```vue
<script setup lang="ts">
// ✅ 正确: 大列表使用 shallowRef
import { shallowRef } from 'vue'
const tableData = shallowRef<ScanRecord[]>([])

// ✅ 正确: 计算属性缓存
const displayData = computed(() =>
  tableData.value.filter(item => item.status === 'active')
)

// ❌ 避免: 模板中复杂计算
// <div>{{ list.filter(x => x.active).map(x => x.name).join(',') }}</div>
</script>
```

### 12.3 缓存策略

```typescript
// ✅ 正确: 请求缓存 (antd/缓存工具)
// ✅ 正确: keep-alive 页面缓存 (已由路由守卫自动处理)
// ✅ 正确: 计算属性而非方法调用
```

---

## 13. 图标使用规范

```vue
<template>
  <!-- ✅ 正确: 使用 Lucide 图标 (按需导入, Tree-shaking) -->
  <FaIcon name="i-lucide:search" />

  <!-- ✅ 正确: 使用 Element Plus 图标 -->
  <el-icon><Search /></el-icon>

  <!-- ❌ 避免: 直接引入整个 Iconify 集合 -->
</template>

<script setup lang="ts">
// ✅ 正确: 按需导入 Lucide 图标
import { Search, Edit, Delete } from 'lucide-vue-next'
</script>
```

---

## 14. 国际化约定

虽然当前版本仅支持中文，但编码时遵循 i18n 友好原则：

```vue
<template>
  <!-- ✅ 正确: UI 文案使用 Element Plus 内置国际化 -->
  <el-table :empty-text="'暂无数据'" />

  <!-- ✅ 正确: 业务文案后续接入 i18n 时易迁移 -->
  <span>{{ '扫描记录' }}</span>
</template>
```

---

## 15. 环境变量规范

| 变量 | 环境 | 默认值 | 说明 |
|------|------|--------|------|
| `VITE_APP_TITLE` | 全部 | MRR-ADMIN | 应用标题 |
| `VITE_APP_API_BASEURL` | dev | `http://localhost:18045` | 后端 API 地址 |
| `VITE_APP_API_BASEURL` | prod | `/` | 生产环境同域或反向代理 |
| `VITE_BUILD_MOCK` | test | true | 测试环境启用 Mock |
| `VITE_BUILD_COMPRESS` | prod | gzip,brotli | 构建压缩 |
| `VITE_BUILD_SOURCEMAP` | test/prod | false | Source Map |

---

## 16. 提交规范

遵循 Conventional Commits，由 `.commitlintrc.js` 和 `cz-git` 强制执行：

```
<type>(<scope>): <description>

类型: feat | fix | docs | style | refactor | perf | test | chore | ci
范围:   component | api | store | router | style | build | deps
```

示例：
```
feat(records): 添加批量导出功能
fix(api): 修复 Token 刷新时 401 循环
style(theme): 调整表格行高亮颜色
```

---

## 17. 测试策略

| 测试类型 | 工具 | 覆盖范围 |
|----------|------|----------|
| 单元测试 | Vitest | Store、Utils、API 模块 |
| 组件测试 | Vitest + Vue Test Utils | Fa* 组件、业务组件 |
| E2E | Playwright | 核心用户流程 |

```typescript
// store 单元测试示例
describe('useUserStore', () => {
  it('login 后应保存 token', async () => {
    const store = useUserStore()
    await store.login('admin', '123456')
    expect(store.token).toBeTruthy()
    expect(store.isLoggedIn).toBe(true)
  })
})
```

---

## 18. 开发工作流

```
1. 创建特性分支: git checkout -b feat/my-feature
2. 本地开发:    pnpm dev
3. 代码检查:    pnpm lint
4. 提交:         pnpm commit (cz-git 交互式提交)
5. 推送到远程:  git push origin feat/my-feature
6. 创建 PR:     发起 Pull Request → CI 检查 → Code Review → 合并
```

每日开发之前：
```bash
git checkout dev
git pull
pnpm install     # 更新依赖
pnpm dev         # 启动开发服务器
```

---

## 19. 参考资源

| 文档 | 位置 |
|------|------|
| 设计语言 | `DESIGN.md` |
| 工程规范 (本文) | `ENGINEERING.md` |
| 组件库文档 | `src/ui/components/` 各组件 README |
| shadcn-vue | `https://www.shadcn-vue.com/` |
| Element Plus | `https://element-plus.org/` |
| UnoCSS | `https://unocss.dev/` |
| Vite | `https://vite.dev/` |
| 通用编码风格 | `.trae/rules/zh/coding-style.md` |

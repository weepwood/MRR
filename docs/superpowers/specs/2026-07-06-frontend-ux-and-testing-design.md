# 前端用户体验 + 软件测试 — 设计规格

| 项目 | 版本 | 日期 |
|------|------|------|
| MRR 医疗影像管理系统 | v1.0 | 2026-07-06 |

---

## Phase 1: 前端用户体验组件

### 目标

为 18 个前端视图提供统一的加载、空状态、错误状态 UI 组件，替代各视图分散的临时实现。

### 范围

- 新增 3 个通用组件：`AppLoading`、`AppEmpty`、`AppError`
- 改造 7 个 CRUD 核心视图，接入新组件
- 视图范围：records、logs、audit-images、patients、users、statistics-detail、oss-migration

### 不在此范围

- 不修改视图的业务逻辑
- 不修改 Element Plus 主题
- 不动 `monitoring`、`settings`、`statistics`、`scan-img` 等非 CRUD 视图（它们的状态处理已稳定）
- 不动 auth 表单组件（login/register）

### 组件规格

#### AppLoading

骨架屏组件，提供表格和卡片两种变体。

**Props:**
```ts
interface AppLoadingProps {
  type?: 'table' | 'card' | 'stats'  // 骨架屏变体
  rows?: number                         // 表格行数 (默认 5)
  cols?: number                         // 卡片列数 (默认 4)
}
```

**行为：** 渲染灰色占位块（animate-pulse），模拟页面加载时的视觉结构。

#### AppEmpty

空状态统一展示。

**Props:**
```ts
interface AppEmptyProps {
  description?: string   // 描述文案 (默认 "暂无数据")
  icon?: string          // 自定义图标名
  actionText?: string    // 操作按钮文案（无则不显示按钮）
  actionIcon?: string    // 操作按钮图标
}
```

**Emits:** `action` — 点击操作按钮时触发。

#### AppError

错误状态统一展示。

**Props:**
```ts
interface AppErrorProps {
  message?: string       // 错误描述
  retryText?: string     // 重试按钮文案 (默认 "重试")
}
```

**Emits:** `retry` — 点击重试按钮时触发。

### 视图接入模式

每个视图的 template 中按以下三元模式：

```vue
<AppLoading v-if="loading" type="table" />
<AppError v-else-if="error" :message="error" @retry="loadData" />
<AppEmpty v-else-if="!list.length" description="暂无记录" />
<el-table v-else :data="list">...</el-table>
```

### 验收标准

1. 页面首次加载显示骨架屏而非空白
2. 搜索无结果时显示统一空状态（含引导操作）
3. API 失败时显示错误组件 + 可点击重试
4. 现有功能无回归

---

## Phase 2: 软件测试

### Phase 2a — 后端测试

#### SystemSettingControllerTest

| 用例 | 说明 |
|------|------|
| `getAllSettings` | Mock Service 返回 Map，验证 200 + 数据 |
| `getSetting_found` | 返回已有 key 的值 |
| `getSetting_notFound` | 不存在的 key 返回 404 |
| `saveSettings` | PUT 批量保存，验证调用成功 |
| `deleteSetting` | 删除已有 key |
| `saveSettings_emptyBody` | 空 body 返回 400 |

使用 `@WebMvcTest` + `@MockBean` 轻量集成。

#### SystemInfoControllerTest

| 用例 | 说明 |
|------|------|
| `getSystemInfo` | 返回应用+JVM+OS 信息 |
| `getMemoryInfo` | 返回堆/非堆内存 |
| `getRuntimeInfo` | 返回运行时长信息 |
| `healthCheck` | 返回 UP 状态 |

Mock `SystemInfoService`。无需 mock JMX。

#### PatientControllerTest

| 用例 | 说明 |
|------|------|
| `listPatients` | 分页查询返回患者列表 |
| `exportPatientsExcel` | 返回 Excel 流 |

Mock `SearchMapper` 和 `ScanService`。

#### OssServiceImplTest

| 用例 | 说明 |
|------|------|
| `uploadToOss_single` | 上传单条记录 |
| `uploadToOss_emptyList` | 空列表直接返回 |
| `generatePresignedUrl` | 生成预签名 URL |
| `uploadByFolder` | 按文件夹批量上传 |

Mock OSS SDK 的 `AmazonS3` 客户端。

#### SystemInfoServiceImplTest

| 用例 | 说明 |
|------|------|
| `getSystemInfo` | 包含应用 + JVM + OS 段 |
| `getMemoryInfo` | 包含 heap/nonHeap/百分比 |
| `getHealth_up` | 健康检查返回 UP |
| `formatBytes` | 私有方法通过 getMemoryInfo 间接验证 |

因为 `ManagementFactory` 是静态方法，mock 受限。测试侧重验证返回结构而非精确数值。

### Phase 2b — 前端测试基础设施

#### 测试配置

不新增依赖。Vitest 3.2 已配置。

新增 `src/test/setup.ts`:
- Mock `vue-router` (useRouter, useRoute)
- Mock Pinia stores (user, settings)
- Mock Element Plus 全局组件 (el-table, el-card 等)
- Stub 全局 API (axios)

#### 组件测试 (Vitest + @vue/test-utils)

| 组件 | 用例 |
|------|------|
| AppLoading | 渲染表格骨架（5 行） |
| AppLoading | 渲染卡片骨架 |
| AppLoading | 渲染统计面板骨架 |
| AppEmpty | 默认描述 "暂无数据" |
| AppEmpty | 自定义描述 + 操作按钮 |
| AppEmpty | 点击按钮触发 action 事件 |
| AppError | 显示错误消息 |
| AppError | 点击重试触发 retry 事件 |
| AppError | 无消息时显示默认文案 |

#### 视图测试 (1 个代表性视图)

选择 `records/index.vue` 作为第一个视图测试：

| 用例 | 说明 |
|------|------|
| `首次加载 — 骨架屏可见` | `loading=true` → 显示 AppLoading |
| `列表渲染 — 数据展示` | Mock API 返回 3 条记录 |
| `空状态 — 无记录提示` | API 返回空列表 |
| `搜索 — 触发 handleSearch` | 输入 BAH → 调用 API |
| `分页 — 切换页码` | 点击第 2 页 → pageNum 变化 |
| `错误 — 显示重试按钮` | API reject → 显示 AppError |

### 验收标准

1. 后端新增测试 `mvn test` 全部通过
2. 前端组件测试 `pnpm test:run` 全部通过
3. 测试覆盖率达到:
   - 控制器: 10/10 (新增 3 个)
   - 服务层: 12/12 (新增 2 个)
   - 前端组件: 3/3 (新增 3 个)
   - 前端视图: 1/18 (新增记录管理视图)

---

## 实施依赖

- Phase 1 和 Phase 2 相互独立，可并行
- Phase 1 的内部顺序: 组件 → 视图改造
- Phase 2a 和 Phase 2b 可并行
- 无需数据库变更
- 无需新增依赖

## 文件清单 (预估)

**Phase 1 (前端 UX):**
- 新增: `src/components/AppLoading/index.vue`
- 新增: `src/components/AppEmpty/index.vue`
- 新增: `src/components/AppError/index.vue`
- 修改: 7 个 CRUD 视图 (records/logs/audit-images/patients/users/statistics-detail/oss-migration)

**Phase 2a (后端测试):**
- 新增: `SystemSettingControllerTest.java`
- 新增: `SystemInfoControllerTest.java`
- 新增: `PatientControllerTest.java`
- 新增: `OssServiceImplTest.java`
- 新增: `SystemInfoServiceImplTest.java`

**Phase 2b (前端测试):**
- 新增: `src/test/setup.ts`
- 新增: `src/components/__tests__/AppLoading.test.ts`
- 新增: `src/components/__tests__/AppEmpty.test.ts`
- 新增: `src/components/__tests__/AppError.test.ts`
- 新增: `src/views/records/__tests__/index.test.ts`

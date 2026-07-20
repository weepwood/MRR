# 影像档案袋用户名与 ID 水印实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在 URL 未提供 `userid` 时，将影像档案袋水印用户标识改为 `用户名-ID`，同时保留既有回退和 URL 覆盖行为。

**架构：** 继续由纯函数 `resolveArchiveWatermarkUserId` 负责生成登录用户标识，由安装器保留 `routeUserId(router) || resolveArchiveWatermarkUserId(...)` 的覆盖顺序。变更限定在纯函数、单元测试和用户文档，不新增状态或接口。

**技术栈：** Vue 3、TypeScript、Vitest、Canvas 水印

---

## 文件结构

- 修改：`frontend-fantastic-admin/src/utils/__tests__/archive-watermark.test.ts`，定义 `用户名-ID` 和缺失字段回退行为。
- 修改：`frontend-fantastic-admin/src/utils/archive-watermark.ts`，实现纯函数拼接规则。
- 修改：`vitepress-doc/user-guide/images.md`，记录水印用户标识格式与 URL 覆盖规则。

### 任务 1：按用户名与 ID 生成水印标识

**文件：**

- 测试：`frontend-fantastic-admin/src/utils/__tests__/archive-watermark.test.ts`
- 修改：`frontend-fantastic-admin/src/utils/archive-watermark.ts`

- [ ] **步骤 1：编写失败的测试**

将现有用户标识测试改为以下行为：

```typescript
it('combines username and user id and falls back without extra separators', () => {
  expect(resolveArchiveWatermarkUserId({ id: 42, username: 'doctor' }, 'operator')).toBe('doctor-42')
  expect(resolveArchiveWatermarkUserId({ id: ' 007 ', username: ' nurse ' }, 'operator')).toBe('nurse-007')
  expect(resolveArchiveWatermarkUserId({ username: 'doctor' }, 'operator')).toBe('doctor')
  expect(resolveArchiveWatermarkUserId({ id: 42 }, 'operator')).toBe('42')
  expect(resolveArchiveWatermarkUserId({}, 'operator')).toBe('operator')
  expect(resolveArchiveWatermarkUserId({}, '')).toBe('未登录')
})
```

- [ ] **步骤 2：运行测试验证失败**

运行：

```bash
cd frontend-fantastic-admin
./node_modules/.bin/vitest run src/utils/__tests__/archive-watermark.test.ts
```

预期：FAIL；当前实现返回 `42`，断言期望 `doctor-42`。

- [ ] **步骤 3：编写最少实现代码**

在 `resolveArchiveWatermarkUserId` 中分别标准化用户名与 ID；两者都有值时使用连字符拼接，否则依次回退到单个用户名、单个 ID、账号显示值和 `未登录`：

```typescript
const username = String(profile?.username ?? '').trim()
const id = String(profile?.id ?? '').trim()

if (username && id) return `${username}-${id}`
if (username) return username
if (id) return id

const accountValue = String(account ?? '').trim()
return accountValue || '未登录'
```

不得修改 `archive-watermark-installer.ts` 中 URL `userid` 的优先级。

- [ ] **步骤 4：运行测试验证通过**

运行：

```bash
cd frontend-fantastic-admin
./node_modules/.bin/vitest run src/utils/__tests__/archive-watermark.test.ts
```

预期：测试文件全部通过，0 个失败。

- [ ] **步骤 5：提交任务 1**

```bash
git add frontend-fantastic-admin/src/utils/__tests__/archive-watermark.test.ts frontend-fantastic-admin/src/utils/archive-watermark.ts
git commit -m "feat(档案袋): 使用用户名与 ID 生成水印"
```

### 任务 2：更新用户文档并完成前端验证

**文件：**

- 修改：`vitepress-doc/user-guide/images.md`

- [ ] **步骤 1：补充水印格式说明**

在“访问水印”章节明确说明：登录用户默认显示 `用户名-ID`；URL 中非空 `userid` 参数仍覆盖该标识；缺失字段时不显示多余连字符。

- [ ] **步骤 2：运行完整单元测试**

运行：

```bash
cd frontend-fantastic-admin
./node_modules/.bin/vitest run
```

预期：所有 Vitest 测试通过，0 个失败。

- [ ] **步骤 3：运行 TypeScript 类型检查**

运行：

```bash
cd frontend-fantastic-admin
./node_modules/.bin/vue-tsc --noEmit
```

预期：退出码为 0，无 TypeScript 错误。

- [ ] **步骤 4：运行生产构建**

运行：

```bash
cd frontend-fantastic-admin
./node_modules/.bin/vite build
```

预期：生产构建完成且退出码为 0。

- [ ] **步骤 5：提交任务 2**

```bash
git add vitepress-doc/user-guide/images.md
git commit -m "docs(档案袋): 说明用户名与 ID 水印规则"
```

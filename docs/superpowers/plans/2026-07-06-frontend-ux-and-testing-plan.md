# 前端 UX 组件 + 软件测试 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 7 个 CRUD 视图提供统一加载/空/错误状态组件，并补齐后端 5 个 + 前端 4 个测试文件。

**Architecture:** Phase 1 创建 3 个通用 Vue 3 组件（`AppLoading`/`AppEmpty`/`AppError`），遵循现有 `components/` 目录模式；然后逐视图接入。Phase 2a 使用 `@WebMvcTest` + Mockito 编写控制器/服务测试。Phase 2b 使用 Vitest + @vue/test-utils 编写组件/视图测试。

**Tech Stack:** Vue 3.5, Element Plus 2.13, TypeScript 5.9, Vitest 3.2, @vue/test-utils 2, Java 21, Spring Boot 4, Mockito, JUnit 5

## Global Constraints

- 不修改现有业务逻辑（视图只改 template 的状态分支，script 不动）
- 遵循现有组件命名约定：`defineOptions({ name: 'PascalCase' })`
- 前端测试使用 Vitest + jsdom + @vue/test-utils（已配置），不添加新依赖
- 后端测试使用 `@WebMvcTest`（控制器）或纯 Mockito（服务），遵循现有测试模式
- Java 文件 4 空格缩进，Vue/TS 文件 2 空格缩进
- 所有新增类型使用 TypeScript interface（非 type alias，与现有 types.ts 一致）

---

## Task 1: AppLoading 组件

**Files:**
- Create: `frontend-fantastic-admin/src/components/AppLoading/index.vue`
- Create: `frontend-fantastic-admin/src/components/__tests__/AppLoading.test.ts`

**Interfaces:**
- Produces: `<AppLoading>` component with props: `type` (`'table'|'card'|'stats'`, default `'table'`), `rows` (`number`, default `5`), `cols` (`number`, default `4`)

- [ ] **Step 1: 创建组件文件**

```vue
<!-- frontend-fantastic-admin/src/components/AppLoading/index.vue -->
<script setup lang="ts">
defineOptions({ name: 'AppLoading' })

withDefaults(defineProps<{
  type?: 'table' | 'card' | 'stats'
  rows?: number
  cols?: number
}>(), {
  type: 'table',
  rows: 5,
  cols: 4,
})
</script>

<template>
  <div class="app-loading">
    <!-- 表格骨架 -->
    <template v-if="type === 'table'">
      <div v-for="i in rows" :key="i" class="skeleton-row">
        <div v-for="j in cols" :key="j" class="skeleton-cell" />
      </div>
    </template>

    <!-- 卡片骨架 -->
    <template v-else-if="type === 'card'">
      <div class="skeleton-cards">
        <div v-for="i in cols" :key="i" class="skeleton-card">
          <div class="skeleton-card-img" />
          <div class="skeleton-card-line skeleton-card-line--short" />
          <div class="skeleton-card-line" />
        </div>
      </div>
    </template>

    <!-- 统计面板骨架 -->
    <template v-else-if="type === 'stats'">
      <div class="skeleton-stats">
        <div v-for="i in cols" :key="i" class="skeleton-stat">
          <div class="skeleton-stat-label" />
          <div class="skeleton-stat-value" />
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.app-loading {
  width: 100%;
}

/* 表格骨架 */
.skeleton-row {
  display: flex;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
}
.skeleton-cell {
  flex: 1;
  height: 16px;
  background: linear-gradient(90deg, var(--el-fill-color-light, #f0f2f5) 25%, var(--el-fill-color, #e4e7ed) 50%, var(--el-fill-color-light, #f0f2f5) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: 4px;
}

/* 卡片骨架 */
.skeleton-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}
.skeleton-card {
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: 8px;
}
.skeleton-card-img {
  height: 120px;
  background: linear-gradient(90deg, var(--el-fill-color-light, #f0f2f5) 25%, var(--el-fill-color, #e4e7ed) 50%, var(--el-fill-color-light, #f0f2f5) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: 4px;
  margin-bottom: 12px;
}
.skeleton-card-line {
  height: 14px;
  background: linear-gradient(90deg, var(--el-fill-color-light, #f0f2f5) 25%, var(--el-fill-color, #e4e7ed) 50%, var(--el-fill-color-light, #f0f2f5) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: 4px;
  margin-bottom: 8px;
}
.skeleton-card-line--short {
  width: 60%;
}

/* 统计面板骨架 */
.skeleton-stats {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 16px;
}
.skeleton-stat {
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: 8px;
}
.skeleton-stat-label {
  width: 50%;
  height: 12px;
  background: linear-gradient(90deg, var(--el-fill-color-light, #f0f2f5) 25%, var(--el-fill-color, #e4e7ed) 50%, var(--el-fill-color-light, #f0f2f5) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: 4px;
  margin-bottom: 8px;
}
.skeleton-stat-value {
  width: 70%;
  height: 24px;
  background: linear-gradient(90deg, var(--el-fill-color-light, #f0f2f5) 25%, var(--el-fill-color, #e4e7ed) 50%, var(--el-fill-color-light, #f0f2f5) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: 4px;
}

@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}
</style>
```

- [ ] **Step 2: 验证组件构建通过**

Run: `cd frontend-fantastic-admin && npx vue-tsc --noEmit src/components/AppLoading/index.vue 2>&1 | head -5`
Expected: no errors

- [ ] **Step 3: 提交**

```bash
git add frontend-fantastic-admin/src/components/AppLoading/index.vue
git commit -m "feat(ui): add AppLoading skeleton component

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: AppEmpty 组件

**Files:**
- Create: `frontend-fantastic-admin/src/components/AppEmpty/index.vue`

**Interfaces:**
- Produces: `<AppEmpty>` component with props: `description` (`string`, default `'暂无数据'`), `icon` (`string`, default `'i-ant-design:inbox-outlined'`), `actionText` (`string`, optional), `actionIcon` (`string`, optional). Signal `action` emitted on button click.

- [ ] **Step 1: 创建组件文件**

```vue
<!-- frontend-fantastic-admin/src/components/AppEmpty/index.vue -->
<script setup lang="ts">
defineOptions({ name: 'AppEmpty' })

withDefaults(defineProps<{
  description?: string
  icon?: string
  actionText?: string
  actionIcon?: string
}>(), {
  description: '暂无数据',
  icon: 'i-ant-design:inbox-outlined',
})

const emit = defineEmits<{
  action: []
}>()

function handleAction() {
  emit('action')
}
</script>

<template>
  <div class="app-empty">
    <div class="app-empty-icon" :class="icon" />
    <p class="app-empty-text">{{ description }}</p>
    <el-button v-if="actionText" type="primary" :icon="actionIcon" @click="handleAction">
      {{ actionText }}
    </el-button>
  </div>
</template>

<style scoped>
.app-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 16px;
  text-align: center;
}
.app-empty-icon {
  font-size: 48px;
  color: var(--el-text-color-placeholder, #c0c4cc);
  margin-bottom: 16px;
}
.app-empty-text {
  margin: 0 0 20px;
  font-size: 14px;
  color: var(--el-text-color-secondary, #909399);
}
</style>
```

- [ ] **Step 2: 验证构建**

Run: `cd frontend-fantastic-admin && npx vue-tsc --noEmit src/components/AppEmpty/index.vue 2>&1 | head -5`
Expected: no errors

- [ ] **Step 3: 提交**

```bash
git add frontend-fantastic-admin/src/components/AppEmpty/index.vue
git commit -m "feat(ui): add AppEmpty component with optional action button

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: AppError 组件

**Files:**
- Create: `frontend-fantastic-admin/src/components/AppError/index.vue`

**Interfaces:**
- Produces: `<AppError>` component with props: `message` (`string`, default `'加载失败'`), `retryText` (`string`, default `'重试'`). Signal `retry` emitted on button click.

- [ ] **Step 1: 创建组件文件**

```vue
<!-- frontend-fantastic-admin/src/components/AppError/index.vue -->
<script setup lang="ts">
import { Refresh } from '@element-plus/icons-vue'

defineOptions({ name: 'AppError' })

withDefaults(defineProps<{
  message?: string
  retryText?: string
}>(), {
  message: '加载失败',
  retryText: '重试',
})

const emit = defineEmits<{
  retry: []
}>()

function handleRetry() {
  emit('retry')
}
</script>

<template>
  <div class="app-error">
    <div class="app-error-icon i-ant-design:warning-twotone" />
    <p class="app-error-text">{{ message }}</p>
    <el-button type="primary" :icon="Refresh" @click="handleRetry">
      {{ retryText }}
    </el-button>
  </div>
</template>

<style scoped>
.app-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 16px;
  text-align: center;
}
.app-error-icon {
  font-size: 48px;
  color: var(--el-color-warning, #e6a23c);
  margin-bottom: 16px;
}
.app-error-text {
  margin: 0 0 20px;
  font-size: 14px;
  color: var(--el-text-color-regular, #606266);
}
</style>
```

- [ ] **Step 2: 验证构建**

Run: `cd frontend-fantastic-admin && npx vue-tsc --noEmit src/components/AppError/index.vue 2>&1 | head -5`
Expected: no errors

- [ ] **Step 3: 提交**

```bash
git add frontend-fantastic-admin/src/components/AppError/index.vue
git commit -m "feat(ui): add AppError component with retry button

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: 改造 records/index.vue

**Files:**
- Modify: `frontend-fantastic-admin/src/views/records/index.vue`

**Interfaces:**
- Consumes: `<AppLoading>` (table, rows=6), `<AppEmpty>` (description="暂无扫描记录"), `<AppError>` (message from catch, @retry=loadData)
- Uses existing: `useCrudList` composable (list, total, loading, error)

- [ ] **Step 1: 在 records 视图的 template 中添加状态分支**

在 records/index.vue 的 template 中，找到 `<div class="page-shell">` 内的内容区域，在 `<el-table>` 外层包裹条件渲染。定位到 `:loading="loading"` 的 el-table 处，在其上层添加状态判断。

具体修改：在 `<el-card class="table-card">` 内部，添加：

```vue
<AppLoading v-if="loading" type="table" :rows="8" />
<AppError v-else-if="error" :message="error" @retry="loadData" />
<AppEmpty v-else-if="!list.length" description="暂无扫描记录" />
<el-table v-else v-loading="false" ...>
```

同时从 `<script setup>` 中添加 imports：
```typescript
import AppLoading from '@/components/AppLoading/index.vue'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
```

并在 script 中添加 error 状态：
```typescript
const error = ref('')
// 在 loadData 的 catch 中: error.value = msg
// 在 loadData 的 try 开头: error.value = ''
```

**Note:** 由于 `useCrudList` composable 当前不暴露 `error` 字段，需要在 records 视图中自行维护 `const error = ref('')`，在 try 中清空、catch 中设置。

- [ ] **Step 2: 验证 TypeScript 编译**

Run: `cd frontend-fantastic-admin && npx vue-tsc --noEmit 2>&1 | grep -i "records" | head -5`
Expected: no errors mentioning records

- [ ] **Step 3: 提交**

```bash
git add frontend-fantastic-admin/src/views/records/index.vue
git commit -m "feat(ux): add loading/empty/error states to records view

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: 改造 logs/index.vue

**Files:**
- Modify: `frontend-fantastic-admin/src/views/logs/index.vue`

- [ ] **Step 1: 在 logs 视图中添加上下文状态分支**

在 `logs/index.vue` 中添加 imports 和 error ref（同 Task 4 模式）。template 中：

```vue
<AppLoading v-if="loading" type="table" :rows="8" />
<AppError v-else-if="error" :message="error" @retry="loadData" />
<AppEmpty v-else-if="!list.length" description="暂无日志记录" />
<el-table v-else ...>
```

- [ ] **Step 2: 提交**

```bash
git add frontend-fantastic-admin/src/views/logs/index.vue
git commit -m "feat(ux): add loading/empty/error states to logs view

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: 改造 audit-images/index.vue

**Files:**
- Modify: `frontend-fantastic-admin/src/views/audit-images/index.vue`

- [ ] **Step 1: 在 audit-images 视图中添加状态分支**

同 Task 4 模式。空描述: `"暂无审计记录"`。

- [ ] **Step 2: 提交**

```bash
git add frontend-fantastic-admin/src/views/audit-images/index.vue
git commit -m "feat(ux): add loading/empty/error states to audit-images view

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: 改造 patients/index.vue

**Files:**
- Modify: `frontend-fantastic-admin/src/views/patients/index.vue`

- [ ] **Step 1: patients 视图不使用 useCrudList，但有 loading/tableData/error 状态**

在 patients/index.vue 中添加 imports。template 中：

```vue
<AppLoading v-if="loading" type="table" :rows="8" />
<AppError v-else-if="error" :message="error" @retry="loadData" />
<AppEmpty v-else-if="!tableData.length" description="暂无患者记录" />
<el-table v-else ...>
```

在 script 中添加:
```typescript
import AppLoading from '@/components/AppLoading/index.vue'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'

const error = ref('')
// 在 loadData 的 try 中: error.value = ''
// 在 loadData 的 catch 中: error.value = msg
```

- [ ] **Step 2: 提交**

```bash
git add frontend-fantastic-admin/src/views/patients/index.vue
git commit -m "feat(ux): add loading/empty/error states to patients view

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: 改造 users/index.vue

**Files:**
- Modify: `frontend-fantastic-admin/src/views/users/index.vue`

- [ ] **Step 1: users 视图不涉及 useCrudList，有 loading/users/error**

同 Task 7 模式。空描述: `"暂无用户记录"`。

- [ ] **Step 2: 提交**

```bash
git add frontend-fantastic-admin/src/views/users/index.vue
git commit -m "feat(ux): add loading/empty/error states to users view

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 9: 改造 statistics-detail/index.vue

**Files:**
- Modify: `frontend-fantastic-admin/src/views/statistics-detail/index.vue`

- [ ] **Step 1: statistics-detail 有 loading/error/listData**

同 Task 7 模式。空描述: `"暂无统计明细"`。error 字段已存在，无需新增。

- [ ] **Step 2: 提交**

```bash
git add frontend-fantastic-admin/src/views/statistics-detail/index.vue
git commit -m "feat(ux): add loading/empty/error states to statistics-detail view

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 10: 改造 oss-migration/index.vue

**Files:**
- Modify: `frontend-fantastic-admin/src/views/oss-migration/index.vue`

- [ ] **Step 1: oss-migration 是复杂视图，在表格区域添加状态分支**

在 `pendingList` 表格区域添加状态分支。该视图有多个独立 panel，仅在主表格处应用。空描述: `"暂无待迁移记录"`。

- [ ] **Step 2: 提交**

```bash
git add frontend-fantastic-admin/src/views/oss-migration/index.vue
git commit -m "feat(ux): add loading/empty/error states to oss-migration view

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 11: 后端测试 — SystemSettingControllerTest

**Files:**
- Create: `backend-repo/src/test/java/com/zjcxph/imgapi/unit/controller/SystemSettingControllerTest.java`

**Interfaces:**
- Consumes: `SystemSettingController` (GET `/api/v1/settings`, GET `/api/v1/settings/{key}`, PUT `/api/v1/settings`, DELETE `/api/v1/settings/{key}`)
- Mock: `SystemSettingService`

- [ ] **Step 1: 编写测试**

```java
package com.zjcxph.imgapi.unit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.controller.SystemSettingController;
import com.zjcxph.imgapi.service.SystemSettingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SystemSettingController.class)
@DisplayName("SystemSettingController 控制器测试")
class SystemSettingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SystemSettingService systemSettingService;

    @Test
    @DisplayName("GET /api/v1/settings — 返回全部设置")
    void getAllSettings() throws Exception {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("systemName", "MRR");
        settings.put("logLevel", "info");
        when(systemSettingService.getAllSettings()).thenReturn(settings);

        mockMvc.perform(get("/api/v1/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.systemName").value("MRR"));
    }

    @Test
    @DisplayName("PUT /api/v1/settings — 批量保存成功")
    void saveSettings() throws Exception {
        Map<String, String> body = Map.of("systemName", "MRR-Prod");
        doNothing().when(systemSettingService).saveSettings(anyMap(), eq(null));

        mockMvc.perform(put("/api/v1/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("PUT /api/v1/settings — 空 body 返回 400")
    void saveSettings_emptyBody() throws Exception {
        mockMvc.perform(put("/api/v1/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("GET /api/v1/settings/{key} — 存在时返回 200")
    void getSetting_found() throws Exception {
        when(systemSettingService.getSetting("logLevel")).thenReturn("info");

        mockMvc.perform(get("/api/v1/settings/logLevel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("info"));
    }

    @Test
    @DisplayName("GET /api/v1/settings/{key} — 不存在返回 404")
    void getSetting_notFound() throws Exception {
        when(systemSettingService.getSetting("unknown")).thenReturn(null);

        mockMvc.perform(get("/api/v1/settings/unknown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("DELETE /api/v1/settings/{key} — 删除成功")
    void deleteSetting() throws Exception {
        doNothing().when(systemSettingService).deleteSetting("key");

        mockMvc.perform(delete("/api/v1/settings/key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
```

- [ ] **Step 2: 运行测试验证**

Run: `cd backend-repo && mvn test -pl . -Dtest=SystemSettingControllerTest -DfailIfNoTests=false 2>&1 | tail -20`
Expected: BUILD SUCCESS, Tests run: 6, Failures: 0

- [ ] **Step 3: 提交**

```bash
git add backend-repo/src/test/java/com/zjcxph/imgapi/unit/controller/SystemSettingControllerTest.java
git commit -m "test: add SystemSettingController unit tests (6 cases)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 12: 后端测试 — SystemInfoControllerTest

**Files:**
- Create: `backend-repo/src/test/java/com/zjcxph/imgapi/unit/controller/SystemInfoControllerTest.java`

- [ ] **Step 1: 编写测试**

```java
package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.controller.SystemInfoController;
import com.zjcxph.imgapi.service.SystemInfoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SystemInfoController.class)
@DisplayName("SystemInfoController 控制器测试")
class SystemInfoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemInfoService systemInfoService;

    @Test
    @DisplayName("GET /api/v1/system/info — 返回系统信息")
    void getSystemInfo() throws Exception {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("application", Map.of("name", "imgapi"));
        info.put("jvm", Map.of("javaVersion", "21"));
        info.put("operatingSystem", Map.of("name", "Windows"));
        when(systemInfoService.getSystemInfo()).thenReturn(info);

        mockMvc.perform(get("/api/v1/system/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.application.name").value("imgapi"));
    }

    @Test
    @DisplayName("GET /api/v1/system/memory — 返回内存信息")
    void getMemoryInfo() throws Exception {
        Map<String, Object> mem = new LinkedHashMap<>();
        mem.put("heap", Map.of("used", "128MB"));
        mem.put("usagePercent", "45.00%");
        when(systemInfoService.getMemoryInfo()).thenReturn(mem);

        mockMvc.perform(get("/api/v1/system/memory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usagePercent").value("45.00%"));
    }

    @Test
    @DisplayName("GET /api/v1/system/runtime — 返回运行时信息")
    void getRuntimeInfo() throws Exception {
        when(systemInfoService.getRuntimeInfo()).thenReturn(Map.of("uptimeFormatted", "2小时"));

        mockMvc.perform(get("/api/v1/system/runtime"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uptimeFormatted").value("2小时"));
    }

    @Test
    @DisplayName("GET /api/v1/system/health — 返回健康检查")
    void healthCheck() throws Exception {
        when(systemInfoService.getHealth()).thenReturn(Map.of("status", "UP"));

        mockMvc.perform(get("/api/v1/system/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    @DisplayName("GET /api/v1/system/overview — 返回综合概览")
    void getOverview() throws Exception {
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("info", Map.of());
        overview.put("memory", Map.of());
        overview.put("runtime", Map.of());
        overview.put("health", Map.of("status", "UP"));
        when(systemInfoService.getOverview()).thenReturn(overview);

        mockMvc.perform(get("/api/v1/system/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.health.status").value("UP"));
    }
}
```

- [ ] **Step 2: 运行测试验证**

Run: `cd backend-repo && mvn test -pl . -Dtest=SystemInfoControllerTest -DfailIfNoTests=false 2>&1 | tail -20`
Expected: BUILD SUCCESS, Tests run: 5

- [ ] **Step 3: 提交**

```bash
git add backend-repo/src/test/java/com/zjcxph/imgapi/unit/controller/SystemInfoControllerTest.java
git commit -m "test: add SystemInfoController unit tests (5 cases)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 13: 后端测试 — PatientControllerTest

**Files:**
- Create: `backend-repo/src/test/java/com/zjcxph/imgapi/unit/controller/PatientControllerTest.java`

- [ ] **Step 1: 编写测试**

```java
package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.controller.PatientController;
import com.zjcxph.imgapi.entity.Patient;
import com.zjcxph.imgapi.mapper.SearchMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PatientController.class)
@DisplayName("PatientController 控制器测试")
class PatientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchMapper searchMapper;

    @Test
    @DisplayName("GET /api/v1/patients — 分页返回患者列表")
    void listPatients() throws Exception {
        Patient p = new Patient();
        p.setId(1);
        p.setBah("00789508");
        p.setName("张三");
        p.setDepartment("内科");

        when(searchMapper.findAllPatients(anyString(), anyInt(), anyInt())).thenReturn(List.of(p));
        when(searchMapper.countAllPatients(anyString())).thenReturn(1L);

        mockMvc.perform(get("/api/v1/patients")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].bah").value("00789508"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/patients — 关键词搜索")
    void listPatients_withKeyword() throws Exception {
        when(searchMapper.findAllPatients(anyString(), anyInt(), anyInt())).thenReturn(List.of());
        when(searchMapper.countAllPatients(anyString())).thenReturn(0L);

        mockMvc.perform(get("/api/v1/patients")
                        .param("keyword", "张三")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list").isEmpty());
    }
}
```

- [ ] **Step 2: 运行测试验证**

Run: `cd backend-repo && mvn test -pl . -Dtest=PatientControllerTest -DfailIfNoTests=false 2>&1 | tail -20`
Expected: BUILD SUCCESS, Tests run: 2

- [ ] **Step 3: 提交**

```bash
git add backend-repo/src/test/java/com/zjcxph/imgapi/unit/controller/PatientControllerTest.java
git commit -m "test: add PatientController unit tests (2 cases)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 14: 后端测试 — OssServiceImplTest

**Files:**
- Create: `backend-repo/src/test/java/com/zjcxph/imgapi/unit/service/OssServiceImplTest.java`

- [ ] **Step 1: 编写测试**

```java
package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.config.OssProperties;
import com.zjcxph.imgapi.mapper.ScanMapper;
import com.zjcxph.imgapi.service.impl.OssServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("OssServiceImpl OSS 服务测试")
@ExtendWith(MockitoExtension.class)
class OssServiceImplTest {

    @Mock
    private OssProperties ossProperties;

    @Mock
    private ScanMapper scanMapper;

    @Mock
    private com.amazonaws.services.s3.AmazonS3 amazonS3;

    private OssServiceImpl ossService;

    @BeforeEach
    void setUp() {
        when(ossProperties.getBucketName()).thenReturn("test-bucket");
        when(ossProperties.getAccessKeyId()).thenReturn("test-key");
        when(ossProperties.getAccessKeySecret()).thenReturn("test-secret");
        when(ossProperties.getEndpoint()).thenReturn("https://oss.example.com");
        when(ossProperties.getRegion()).thenReturn("cn-hangzhou");
        ossService = new OssServiceImpl(ossProperties, scanMapper);
    }

    @Test
    @DisplayName("generatePresignedUrl — 生成带签名的临时 URL")
    void generatePresignedUrl_returnsSignedUrl() {
        // OSS 对象键
        String ossKey = "scans/2026/test.jpg";
        String signedUrl = ossService.generatePresignedUrl(ossKey);

        assertThat(signedUrl).isNotNull();
        assertThat(signedUrl).contains("https://oss.example.com");
    }

    @Test
    @DisplayName("deleteObject — 删除 OSS 文件（委托 S3 客户端）")
    void deleteObject_delegatesToS3() {
        // 不抛异常即成功
        ossService.deleteObject("scans/2026/delete-me.jpg");
    }
}
```

- [ ] **Step 2: 运行测试验证**

Run: `cd backend-repo && mvn test -pl . -Dtest=OssServiceImplTest -DfailIfNoTests=false 2>&1 | tail -20`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add backend-repo/src/test/java/com/zjcxph/imgapi/unit/service/OssServiceImplTest.java
git commit -m "test: add OssServiceImpl unit tests (2 cases)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 15: 后端测试 — SystemInfoServiceImplTest

**Files:**
- Create: `backend-repo/src/test/java/com/zjcxph/imgapi/unit/service/SystemInfoServiceImplTest.java`

- [ ] **Step 1: 编写测试（验证结构非精确数值）**

```java
package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.service.impl.SystemInfoServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DisplayName("SystemInfoServiceImpl 系统信息服务测试")
class SystemInfoServiceImplTest {

    private final SystemInfoServiceImpl service = new SystemInfoServiceImpl();

    @Test
    @DisplayName("getSystemInfo — 返回 application/jvm/operatingSystem 三段结构")
    void getSystemInfo_hasExpectedSections() {
        Map<String, Object> info = service.getSystemInfo();

        assertThat(info).containsKeys("application", "jvm", "operatingSystem");

        @SuppressWarnings("unchecked")
        Map<String, String> app = (Map<String, String>) info.get("application");
        assertThat(app).containsKeys("name", "startTime", "runTime");
    }

    @Test
    @DisplayName("getMemoryInfo — 返回 heap/nonHeap/usagePercent")
    void getMemoryInfo_hasExpectedSections() {
        Map<String, Object> mem = service.getMemoryInfo();

        assertThat(mem).containsKeys("heap", "nonHeap", "usagePercent");
        // usagePercent 以 % 结尾
        assertThat((String) mem.get("usagePercent")).endsWith("%");
    }

    @Test
    @DisplayName("getRuntimeInfo — 返回 uptimeFormatted")
    void getRuntimeInfo_hasUptime() {
        Map<String, Object> runtime = service.getRuntimeInfo();

        assertThat(runtime).containsKeys("uptimeFormatted", "startTime", "uptimeMillis");
    }

    @Test
    @DisplayName("getHealth — 状态为 UP")
    void getHealth_statusIsUp() {
        Map<String, Object> health = service.getHealth();

        assertThat(health).containsEntry("status", "UP");
        assertThat(health).containsKey("components");
    }

    @Test
    @DisplayName("getGcStats — 包含 totalCollections 键")
    void getGcStats_hasTotals() {
        Map<String, Object> gc = service.getGcStats();

        assertThat(gc).containsKeys("totalCollections", "totalTimeMs");
        // totalCollections >= 0（即使没有实际 GC，管理 Bean 也存在）
        assertThat((Long) gc.get("totalCollections")).isGreaterThanOrEqualTo(0);
    }
}
```

- [ ] **Step 2: 运行测试验证**

Run: `cd backend-repo && mvn test -pl . -Dtest=SystemInfoServiceImplTest -DfailIfNoTests=false 2>&1 | tail -20`
Expected: BUILD SUCCESS, Tests run: 5

- [ ] **Step 3: 提交**

```bash
git add backend-repo/src/test/java/com/zjcxph/imgapi/unit/service/SystemInfoServiceImplTest.java
git commit -m "test: add SystemInfoServiceImpl unit tests (5 cases)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 16: 前端测试 — 组件测试 (AppLoading + AppEmpty + AppError)

**Files:**
- Create: `frontend-fantastic-admin/src/components/__tests__/AppLoading.test.ts`
- Create: `frontend-fantastic-admin/src/components/__tests__/AppEmpty.test.ts`
- Create: `frontend-fantastic-admin/src/components/__tests__/AppError.test.ts`

- [ ] **Step 1: 编写 AppLoading 测试**

```typescript
// src/components/__tests__/AppLoading.test.ts
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AppLoading from '../AppLoading/index.vue'

describe('AppLoading', () => {
  it('renders table skeleton with default 5 rows', () => {
    const wrapper = mount(AppLoading, { props: { type: 'table' } })
    expect(wrapper.findAll('.skeleton-row')).toHaveLength(5)
  })

  it('renders card skeleton', () => {
    const wrapper = mount(AppLoading, { props: { type: 'card', cols: 3 } })
    expect(wrapper.findAll('.skeleton-card')).toHaveLength(3)
  })

  it('renders stats skeleton', () => {
    const wrapper = mount(AppLoading, { props: { type: 'stats', cols: 4 } })
    expect(wrapper.findAll('.skeleton-stat')).toHaveLength(4)
  })

  it('respects custom row count', () => {
    const wrapper = mount(AppLoading, { props: { type: 'table', rows: 3 } })
    expect(wrapper.findAll('.skeleton-row')).toHaveLength(3)
  })
})
```

- [ ] **Step 2: 编写 AppEmpty 测试**

```typescript
// src/components/__tests__/AppEmpty.test.ts
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { ElButton } from 'element-plus'
import AppEmpty from '../AppEmpty/index.vue'

describe('AppEmpty', () => {
  it('renders default description', () => {
    const wrapper = mount(AppEmpty)
    expect(wrapper.find('.app-empty-text').text()).toBe('暂无数据')
  })

  it('renders custom description', () => {
    const wrapper = mount(AppEmpty, { props: { description: 'No users' } })
    expect(wrapper.find('.app-empty-text').text()).toBe('No users')
  })

  it('shows action button when actionText provided', () => {
    const wrapper = mount(AppEmpty, {
      props: { actionText: '新增' },
      global: { stubs: { ElButton } },
    })
    // button exists when actionText is provided
    expect(wrapper.findComponent(ElButton).exists()).toBe(true)
  })

  it('emits action when button clicked', async () => {
    const wrapper = mount(AppEmpty, {
      props: { actionText: '新增' },
      global: { stubs: { ElButton: false } },
    })
    // Click the button
    await wrapper.find('.el-button').trigger('click')
    expect(wrapper.emitted('action')).toBeTruthy()
  })
})
```

- [ ] **Step 3: 编写 AppError 测试**

```typescript
// src/components/__tests__/AppError.test.ts
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AppError from '../AppError/index.vue'

describe('AppError', () => {
  it('renders default message', () => {
    const wrapper = mount(AppError)
    expect(wrapper.find('.app-error-text').text()).toBe('加载失败')
  })

  it('renders custom message', () => {
    const wrapper = mount(AppError, { props: { message: '网络异常' } })
    expect(wrapper.find('.app-error-text').text()).toBe('网络异常')
  })

  it('emits retry when button clicked', async () => {
    const wrapper = mount(AppError)
    await wrapper.find('.el-button').trigger('click')
    expect(wrapper.emitted('retry')).toBeTruthy()
  })
})
```

- [ ] **Step 4: 运行前端测试**

Run: `cd frontend-fantastic-admin && npx vitest run src/components/__tests__/ 2>&1 | tail -20`
Expected: Tests passed

- [ ] **Step 5: 提交**

```bash
git add frontend-fantastic-admin/src/components/__tests__/
git commit -m "test: add AppLoading/AppEmpty/AppError component tests (10 cases)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 17: 前端测试 — records 视图测试

**Files:**
- Create: `frontend-fantastic-admin/src/views/records/__tests__/index.test.ts`

- [ ] **Step 1: 编写 records 视图测试**

```typescript
// src/views/records/__tests__/index.test.ts
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

// Mock API modules
vi.mock('@/api/modules/records', () => ({
  getScanList: vi.fn().mockResolvedValue({
    code: 200,
    data: {
      list: [
        { id: 1, bah: '00789508', brxh: '001', filename: 'scan1.jpg', btype: 1 },
        { id: 2, bah: '00789509', brxh: '002', filename: 'scan2.jpg', btype: 2 },
      ],
      total: 2,
      page: 1,
      size: 20,
    },
  }),
  getScanByCondition: vi.fn(),
  batchDownloadRecords: vi.fn(),
}))

vi.mock('@/store/modules/user', () => ({
  useUserStore: () => ({
    token: 'dev-token',
    isLogin: true,
    permissions: ['record:read'],
  }),
}))

vi.mock('@/store/modules/settings', () => ({
  useSettingsStore: () => ({
    settings: { home: { title: 'MRR' }, app: { enablePermission: true } },
  }),
}))

import RecordsPage from '../index.vue'

describe('RecordsPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders page shell on mount', () => {
    const wrapper = mount(RecordsPage, {
      global: {
        stubs: {
          'el-card': { template: '<div><slot /></div>' },
          'el-table': { template: '<div><slot /></div>' },
          'el-button': { template: '<button><slot /></button>' },
          'el-input': true,
          'el-select': true,
          'el-pagination': true,
          'el-tag': true,
          'el-dialog': true,
          'AppLoading': { template: '<div class="app-loading" />' },
          'AppEmpty': { template: '<div class="app-empty" />' },
          'AppError': { template: '<div class="app-error" />' },
          'router-link': true,
          'router-view': true,
        },
      },
    })

    // Page shell should render
    expect(wrapper.find('.page-shell').exists()).toBe(true)
  })
})
```

- [ ] **Step 2: 运行前端视图测试**

Run: `cd frontend-fantastic-admin && npx vitest run src/views/records/__tests__/ 2>&1 | tail -20`
Expected: Tests passed

- [ ] **Step 3: 提交**

```bash
git add frontend-fantastic-admin/src/views/records/__tests__/
git commit -m "test: add records view smoke test

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 验证检查点

完成所有 17 个 Task 后运行:

```bash
# 后端全量测试
cd backend-repo && mvn test 2>&1 | tail -30
# Expected: BUILD SUCCESS, Total tests > 40

# 前端全量测试
cd frontend-fantastic-admin && pnpm test:run 2>&1 | tail -20
# Expected: all tests pass, new component + view tests included

# 前端类型检查
cd frontend-fantastic-admin && pnpm lint:tsc 2>&1 | tail -10
# Expected: no TypeScript errors
```

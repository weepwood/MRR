## 变更说明

<!-- 说明为什么要改、改了什么。不要只罗列文件名。 -->

## 关联事项

<!-- Closes #123 / Related to #123；没有关联 Issue 时说明原因。 -->

## 变更类型

- [ ] 缺陷修复
- [ ] 新功能
- [ ] 重构或性能优化
- [ ] 测试或工程管理
- [ ] 文档或部署
- [ ] 数据库迁移
- [ ] 安全或权限

## 影响与风险

- 影响模块：
- 数据库兼容性：
- 权限与审计：
- Chrome 109 / Windows Server 兼容性：
- 回滚方式：

## 验证记录

- [ ] 后端单元测试：`cd backend-repo && mvn -B -ntp test`
- [ ] 后端集成测试：`cd backend-repo && mvn -B -ntp verify`
- [ ] 前端类型、构建与单元测试：`cd frontend-fantastic-admin && pnpm lint:tsc && pnpm build && pnpm test:run`
- [ ] 前端核心流程 E2E：`cd frontend-fantastic-admin && pnpm test:e2e`
- [ ] Windows PowerShell 5.1 脚本检查
- [ ] 已进行必要的手工验证
- [ ] 本次变更不适用的检查已在下方说明

### 未执行或不适用的检查

<!-- 不要留空。说明原因、替代证据和剩余风险。 -->

## 数据与隐私检查

- [ ] 测试数据均为虚构或已脱敏
- [ ] 日志、截图、配置和测试夹具中不包含患者信息、密码、令牌或内网敏感信息
- [ ] 导入、导出、下载或删除变更包含边界条件与失败路径测试

## Review 重点

<!-- 指出最希望审查者重点确认的设计、并发、SQL、权限、性能或兼容性问题。 -->

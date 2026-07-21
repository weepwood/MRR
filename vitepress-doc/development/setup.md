# 开发环境设置

> 本文档介绍如何搭建 MRR 系统的开发环境

## 开发工具

### 必需工具

**Java 开发**
- JDK 21+
- Maven 3.9+
- IDE: IntelliJ IDEA (推荐) 或 Eclipse

**前端开发**
- Node.js 22
- pnpm 10.33.0（项目锁定版本）
- IDE: VS Code (推荐) 或 WebStorm

**数据库**
- PostgreSQL 15+
- 数据库管理工具: DBeaver, pgAdmin, 或 DataGrip

### 推荐工具

- **Git**: 版本控制
- **Postman**: API 测试
- **Docker**: 容器化部署
- **Draw.io**: 架构图绘制

## IDE 配置

### IntelliJ IDEA

**安装插件**
- Lombok
- MyBatisX
- Spring Boot Assistant
- Rainbow Brackets
- CodeGlance

**配置 Maven**
```
Settings -> Build, Execution, Deployment -> Build Tools -> Maven
Maven home path: /path/to/maven
User settings file: /path/to/settings.xml
```

**配置 JDK**
```
File -> Project Structure -> Project
SDK: JDK 21
Language level: 21 - Record patterns, pattern matching for switch
```

**代码风格**
```
Settings -> Editor -> Code Style -> Java
导入项目代码风格配置文件
```

### VS Code

**安装扩展**
- Vue - Official
- TypeScript Vue Plugin (Volar)
- ESLint
- Prettier
- Vite

**配置 settings.json**
```json
{
  "editor.formatOnSave": true,
  "editor.defaultFormatter": "esbenp.prettier-vscode",
  "editor.codeActionsOnSave": {
    "source.fixAll.eslint": "explicit"
  },
  "typescript.tsdk": "node_modules/typescript/lib"
}
```

## 项目导入

### 后端项目

**IntelliJ IDEA**
1. File -> Open
2. 选择 `backend-repo` 目录
3. 等待 Maven 导入依赖
4. 配置运行配置

**运行配置**
```
Main class: com.zjcxph.imgapi.ImageApiApplication
VM options: -Dspring.profiles.active=local
Environment variables: SPRING_DATASOURCE_PASSWORD=your_password; AES_SECRET_KEY=your-32-byte-key; JWT_SECRET_KEY=your-jwt-signing-key
```

### 前端项目

**VS Code**
1. File -> Open Folder
2. 选择 `frontend-fantastic-admin` 目录
3. 打开终端，安装依赖

```bash
corepack pnpm@10.33.0 install --frozen-lockfile
```

## 数据库设置

### 创建开发数据库

```sql
-- 连接 PostgreSQL
psql -U postgres

-- 创建数据库
CREATE DATABASE imgapi_dev;

-- 创建 schema
\c imgapi_dev
CREATE SCHEMA app;

-- 授权 (可选)
CREATE USER mrr_dev WITH PASSWORD 'dev_password';
GRANT ALL PRIVILEGES ON DATABASE imgapi_dev TO mrr_dev;
GRANT ALL PRIVILEGES ON SCHEMA app TO mrr_dev;
```

### 初始化数据

项目启动时会自动执行初始化脚本:
- `backend-repo/src/main/resources/schema-postgresql.sql`

手动执行:
```bash
psql -U postgres -d imgapi_dev -f backend-repo/src/main/resources/schema-postgresql.sql
```

### 测试数据

创建测试数据脚本 `test-data.sql`:

```sql
-- 插入测试患者
INSERT INTO app.mr_patient (patient_id, name, gender, birth_date) VALUES
('P001', '张三', 'M', '1980-01-01'),
('P002', '李四', 'F', '1990-05-15');

-- 插入测试扫描记录
INSERT INTO app.mr_scan (scan_id, patient_id, scan_date, modality) VALUES
('S001', 'P001', '2024-01-01 10:00:00', 'CT'),
('S002', 'P002', '2024-01-02 14:30:00', 'MRI');
```

## 环境变量配置

### 后端环境变量

创建 `.env` 文件 (不要提交到 Git):

```powershell
# Copy the tracked template first, then set the actual database password and AES key.
Copy-Item backend-repo\src\main\resources\application-local.template.properties backend-repo\src\main\resources\application-local.properties

$env:SPRING_DATASOURCE_PASSWORD = 'your-postgresql-password'
$env:AES_SECRET_KEY = 'your-32-byte-aes-key'
$env:JWT_SECRET_KEY = 'your-jwt-signing-key'
```

在 IntelliJ IDEA 中配置环境变量:
```
Run -> Edit Configurations -> Environment variables
```

### 前端环境变量

创建 `.env.local`:

开发环境默认使用 `frontend-fantastic-admin/.env.development`，通过 Vite 代理访问 `http://localhost:18045`。`VITE_APP_DEMO_MODE=true` 时可浏览界面而不调用认证接口，但业务数据仍需后端运行。

## 开发流程

### 启动后端服务

**方式 1: Maven**
```bash
cd backend-repo
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**方式 2: IDE**
在 IntelliJ IDEA 中运行 `ImageApiApplication`

**方式 3: JAR**
```bash
mvn clean package -DskipTests
java -jar target/imgapi-*.jar --spring.profiles.active=local
```

### 启动前端服务

```bash
cd frontend-fantastic-admin

pnpm dev
```

访问 http://localhost:9000

### 热重载

**后端**
- 使用 Spring Boot DevTools
- 修改代码后自动重启

**前端**
- Vite 自动热重载
- 修改代码后自动刷新

## 调试配置

### 后端调试

**IntelliJ IDEA**
1. 设置断点
2. 右键 -> Debug 'ImageApiApplication'
3. 使用 Debug 工具窗口

**远程调试**
```bash
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 \
  -jar target/imgapi-*.jar
```

### 前端调试

**VS Code**
1. 安装 "Debugger for Chrome" 扩展
2. 创建 `.vscode/launch.json`:

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "chrome",
      "request": "launch",
      "name": "Launch Chrome",
  "url": "http://localhost:9000",
      "webRoot": "${workspaceFolder}/src"
    }
  ]
}
```

**浏览器 DevTools**
- Vue DevTools 扩展
- Network 标签查看 API 请求
- Console 查看日志

## 日志配置

### 后端日志

`application-local.properties`:
```properties
# 日志级别
logging.level.root=INFO
logging.level.com.zjcxph.imgapi=DEBUG

# SQL 日志
logging.level.com.zjcxph.imgapi.mapper=DEBUG

# 请求日志
logging.level.org.springframework.web=DEBUG
```

### 前端日志

在代码中使用:
```javascript
console.log('Debug info:', data)
console.error('Error:', error)
```

生产环境移除 console.log:
```javascript
// vite.config.ts
export default defineConfig({
  esbuild: {
    drop: process.env.NODE_ENV === 'production' ? ['console'] : []
  }
})
```

## 测试配置

### 后端测试

**运行所有测试**
```bash
mvn test
```

**运行单个测试**
```bash
mvn test -Dtest=ScanTest
```

**测试覆盖率**
```bash
mvn jacoco:report
```

### 前端测试

**运行测试**
```bash
pnpm test:run
```

**测试覆盖率**
```bash
pnpm test:coverage
```

## Git 配置

### .gitignore

确保以下文件不被提交:

```gitignore
# 后端
backend-repo/target/
backend-repo/.mvn/
backend-repo/.env
backend-repo/application-local.properties

# 前端
frontend-fantastic-admin/node_modules/
frontend-fantastic-admin/dist/
frontend-fantastic-admin/.env.local

# IDE
.idea/
.vscode/
*.iml
*.swp
*.swo

# 日志
*.log
logs/
```

### Git Hooks

使用 pre-commit hook 进行代码检查:

```bash
#!/bin/sh
# .git/hooks/pre-commit

# 后端代码检查
cd backend-repo
mvn checkstyle:check

# 前端代码检查
cd ../frontend-fantastic-admin
pnpm lint:tsc
```

## 常见问题

### 端口冲突

**后端端口被占用**
```bash
# Windows
netstat -ano | findstr :18045
taskkill /PID <pid> /F

# Linux/macOS
lsof -i :18045
kill -9 <pid>
```

**前端端口被占用**
```bash
# 修改端口
pnpm dev -- --port 9001
```

### 依赖问题

**Maven 依赖下载失败**
```bash
# 清理本地仓库
mvn dependency:purge-local-repository

# 强制更新
mvn clean install -U
```

**npm 依赖问题**
```bash
# 清理缓存
npm cache clean --force

# 删除 node_modules
rm -rf node_modules package-lock.json
corepack pnpm@10.33.0 install --frozen-lockfile
```

### 数据库连接失败

1. 确认 PostgreSQL 服务已启动
2. 检查连接参数
3. 检查防火墙设置
4. 检查用户权限

## 相关链接

- [编码规范](/development/coding-standards)
- [测试指南](/development/testing)
- [部署指南](/development/deployment)

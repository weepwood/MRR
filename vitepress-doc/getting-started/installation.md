# 安装指南

> 本文档介绍如何安装和部署 MRR 系统

## 系统要求

### 硬件要求

- **CPU**: 4 核或以上
- **内存**: 8GB 或以上 (推荐 16GB)
- **存储**: 50GB 可用空间 (用于数据库和影像存储)

### 软件要求

**后端服务**
- JDK 21 或更高版本
- Maven 3.9+
- PostgreSQL 16+

**前端应用**
- Node.js 22
- pnpm 10.33.0

**可选组件**
- Docker & Docker Compose (容器化部署)
- Nginx (生产环境反向代理)

## 安装步骤

### 1. 获取源代码

```bash
# 克隆项目仓库
git clone <repository-url>
cd MRR
```

### 2. 数据库准备

#### 安装 PostgreSQL

**Windows:**
下载并安装 [PostgreSQL 官方安装包](https://www.postgresql.org/download/windows/)

**Linux (Ubuntu/Debian):**
```bash
sudo apt update
sudo apt install postgresql postgresql-contrib
sudo systemctl start postgresql
sudo systemctl enable postgresql
```

**macOS:**
```bash
brew install postgresql@15
brew services start postgresql@15
```

#### 创建数据库

```bash
# 登录 PostgreSQL
psql -U postgres

# 创建数据库
CREATE DATABASE imgapi;

# 创建用户 (可选)
CREATE USER mrr_user WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE imgapi TO mrr_user;

# 退出
\q
```

### 3. 后端服务安装

#### 配置数据库连接

创建配置文件 `backend-repo/src/main/resources/application-local.properties`:

```properties
# 数据库配置
spring.datasource.url=jdbc:postgresql://localhost:5432/imgapi?currentSchema=app
spring.datasource.username=postgres
spring.datasource.password=your_password

# 服务端口
server.port=18045

# AES 加密密钥 (32 字节)
aes.secret.key=your-32-byte-aes-key
```

#### 构建项目

```bash
cd backend-repo

# 安装依赖
mvn clean install

# 跳过测试构建 (快速构建)
mvn -DskipTests package
```

#### 运行服务

```bash
# 方式 1: 使用 Maven
mvn spring-boot:run

# 方式 2: 直接运行 JAR
java -jar target/imgapi-*.jar
```

服务启动后访问:
- API 文档: http://localhost:18045/v1/swagger-ui/index.html
- 健康检查: http://localhost:18045/actuator/health

### 4. 前端应用安装

#### 安装依赖

```bash
cd frontend-fantastic-admin

corepack pnpm@10.33.0 install --frozen-lockfile
```

#### 配置环境变量

创建 `.env.local` 文件:

```env
VITE_APP_API_BASEURL=http://localhost:18045
```

#### 运行开发服务器

```bash
pnpm dev
```

访问 http://localhost:9000

#### 构建生产版本

```bash
pnpm build
```

构建产物位于 `dist/` 目录。

## Docker 部署 (可选)

### 使用 Docker Compose

创建 `docker-compose.yml`:

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: imgapi
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    volumes:
      - postgres-data:/var/lib/postgresql/data
    ports:
      - "5432:5432"

  backend:
    build: ./backend-repo
    ports:
      - "18045:18045"
    depends_on:
      - postgres
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/imgapi?currentSchema=app
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres

  frontend:
    build: ./frontend-fantastic-admin
    ports:
      - "80:80"
    depends_on:
      - backend

volumes:
  postgres-data:
```

启动服务:

```bash
docker-compose up -d
```

## 验证安装

### 1. 检查后端服务

```bash
# 健康检查
curl http://localhost:18045/actuator/health

# 预期返回
{
  "status": "UP"
}
```

### 2. 检查前端应用

访问 http://localhost:9000。开发环境默认启用展示模式，不会请求认证接口；业务数据仍需要后端服务。

### 3. 测试登录

使用默认管理员账号:
- 用户名: `br_admin`
- 密码: `br_password`

::: warning 注意
生产环境请立即修改默认密码!
:::

## 常见问题

### 端口被占用

修改后端端口 (默认 18045):
```properties
server.port=18046
```

修改前端端口 (默认 9000):
```bash
pnpm dev -- --port 9001
```

### 数据库连接失败

1. 确认 PostgreSQL 服务已启动
2. 检查连接参数是否正确
3. 确认防火墙允许 5432 端口

### 前端无法连接后端

1. 检查后端服务是否正常运行
2. 确认 `.env.local` 中的 API 地址正确
3. 检查 CORS 配置

## 下一步

- [配置说明](/getting-started/configuration) - 了解详细配置参数
- [首次运行](/getting-started/first-run) - 系统初始化和基本使用
- [系统架构](/architecture/overview) - 了解系统设计

## 相关链接

- [PostgreSQL 官方文档](https://www.postgresql.org/docs/)
- [Spring Boot 文档](https://spring.io/projects/spring-boot)
- [Vue 3 文档](https://vuejs.org/)

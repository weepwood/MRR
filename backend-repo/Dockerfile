FROM node:22-alpine AS frontend-build
WORKDIR /workspace

RUN corepack enable
COPY VERSION release-baseline.json ./
COPY frontend-fantastic-admin/package.json \
     frontend-fantastic-admin/pnpm-lock.yaml \
     frontend-fantastic-admin/.npmrc ./frontend-fantastic-admin/
WORKDIR /workspace/frontend-fantastic-admin
RUN pnpm install --frozen-lockfile
COPY frontend-fantastic-admin/ ./
RUN pnpm build

FROM maven:3.9.9-eclipse-temurin-21 AS backend-build
WORKDIR /workspace/backend-repo

COPY backend-repo/pom.xml ./
RUN mvn -q -B -ntp dependency:go-offline
COPY backend-repo/src ./src
RUN mvn -q -B -ntp -DskipTests package

COPY --from=frontend-build /workspace/frontend-fantastic-admin/dist /workspace/frontend-dist
RUN set -eux; \
    JAR="$(find target -maxdepth 1 -type f -name '*.jar' ! -name '*.original' | head -n 1)"; \
    test -n "${JAR}"; \
    mkdir -p /tmp/mrr-frontend/BOOT-INF/classes/static; \
    cp -a /workspace/frontend-dist/. /tmp/mrr-frontend/BOOT-INF/classes/static/; \
    jar uf "${JAR}" -C /tmp/mrr-frontend BOOT-INF/classes/static; \
    jar tf "${JAR}" | grep -q '^BOOT-INF/classes/static/index.html$'; \
    jar tf "${JAR}" | grep -q '^BOOT-INF/classes/static/assets/'

FROM eclipse-temurin:21-jre
WORKDIR /app

# 创建非 root 用户运行应用
RUN groupadd -r app && useradd -r -g app appuser

COPY --from=backend-build /workspace/backend-repo/target/*.jar /app/app.jar

USER appuser

EXPOSE 18045

# 健康检查：调用 Spring Actuator health 端点
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:18045/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "/app/app.jar"]

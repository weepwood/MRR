ARG NODE_VERSION=24.15.0
FROM node:${NODE_VERSION}-alpine AS frontend-build
WORKDIR /workspace
ARG SOURCE_REVISION
RUN case "$SOURCE_REVISION" in *[!0-9a-f]*|'') echo 'SOURCE_REVISION must be a full commit SHA' >&2; exit 1;; esac; \
    test "${#SOURCE_REVISION}" -eq 40
ENV MRR_GIT_COMMIT=${SOURCE_REVISION}
RUN corepack enable
COPY VERSION release-baseline.json ./
COPY frontend-fantastic-admin/package.json \
     frontend-fantastic-admin/pnpm-lock.yaml \
     frontend-fantastic-admin/pnpm-workspace.yaml ./frontend-fantastic-admin/
WORKDIR /workspace/frontend-fantastic-admin
COPY frontend-fantastic-admin/scripts/require-package-manager.mjs ./scripts/
RUN pnpm install --frozen-lockfile
COPY frontend-fantastic-admin/ ./
RUN pnpm build

FROM eclipse-temurin:21-jdk AS backend-build
WORKDIR /workspace/backend-repo
ARG SOURCE_REVISION
RUN apt-get update && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*
COPY backend-repo/mvnw ./mvnw
COPY backend-repo/.mvn ./.mvn
COPY backend-repo/pom.xml ./
RUN bash mvnw -q -B -ntp dependency:go-offline
COPY backend-repo/src ./src
COPY --from=frontend-build /workspace/frontend-fantastic-admin/dist /workspace/frontend-dist
RUN set -eux; \
    rm -rf src/main/resources/static; \
    mkdir -p src/main/resources/static; \
    cp -a /workspace/frontend-dist/. src/main/resources/static/; \
    printf 'git.commit.id=%s\ngit.commit.id.abbrev=%.7s\ngit.branch=source-archive\n' "$SOURCE_REVISION" "$SOURCE_REVISION" > src/main/resources/git.properties; \
    bash mvnw -q -B -ntp -Dmaven.gitcommitid.skip=true -Dlocal.config.exclude=true -DskipTests package; \
    JAR="$(find target -maxdepth 1 -type f -name '*.jar' ! -name '*.original' | head -n 1)"; \
    test -n "${JAR}"; \
    test "$(jar tf "${JAR}" | grep -c '^BOOT-INF/classes/static/index.html$')" -eq 1; \
    jar tf "${JAR}" | grep -q '^BOOT-INF/classes/static/assets/'

FROM eclipse-temurin:21-jre
WORKDIR /app

# 创建非 root 用户运行应用
RUN groupadd -r app && useradd -r -g app appuser \
    && mkdir -p /app/logs /app/exports && chown -R appuser:app /app
ENV LOG_FILE_NAME=/app/logs/img-api.log
ENV ARCHIVE_EXPORT_TEMP_DIRECTORY=/app/exports

COPY --from=backend-build /workspace/backend-repo/target/*.jar /app/app.jar

USER appuser

EXPOSE 18045

# 健康检查：调用 Spring Actuator health 端点
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://127.0.0.1:18046/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "/app/app.jar"]

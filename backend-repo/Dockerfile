FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -q -B -ntp dependency:go-offline

COPY src ./src

RUN mvn -q -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

# 创建非 root 用户运行应用
RUN groupadd -r app && useradd -r -g app appuser

COPY --from=build /workspace/target/*.jar /app/app.jar

USER appuser

EXPOSE 18045

# 健康检查：调用 Spring Actuator health 端点
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:18045/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "/app/app.jar"]

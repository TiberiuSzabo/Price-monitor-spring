# syntax=docker/dockerfile:1

# ---- 1. build: compile and package with the full JDK + Maven ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# Dependencies first: this layer is reused until pom.xml changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
# Tests run in CI before the image is built, so they are neither compiled nor run here.
RUN mvn -B -q -Dmaven.test.skip=true package

# ---- 2. extract: split the fat jar into Spring Boot layers ----
FROM eclipse-temurin:21-jre-alpine AS extract
WORKDIR /workspace
COPY --from=build /workspace/target/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ---- 3. runtime: JRE only, non-root ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
WORKDIR /app

# Least-changing layers first, so a code change only replaces the last (small) one.
COPY --from=extract --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=extract --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=extract --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=extract --chown=app:app /workspace/extracted/application/ ./

USER app

# Heap is sized from the container memory limit instead of a fixed -Xmx,
# and the JVM exits on OOM so the orchestrator restarts it.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0 -XX:+ExitOnOutOfMemoryError"

# 8081 = public API, 9001 = management (health, metrics)
EXPOSE 8081 9001

# exec keeps the JVM as PID 1 so SIGTERM triggers Spring's graceful shutdown.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

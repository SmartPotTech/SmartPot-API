FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:24-jre-alpine

LABEL org.opencontainers.image.title="SmartPot API" \
      org.opencontainers.image.description="API REST y MQTT de SmartPot" \
      org.opencontainers.image.source="https://github.com/SmartPotTech/SmartPot-API" \
      org.opencontainers.image.licenses="MIT"

WORKDIR /app

COPY --from=build /workspace/target/smartpot-api.jar /app/smartpot-api.jar

ENV PORT=8091 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp -Duser.timezone=UTC"

USER 1000:1000

EXPOSE 8091

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O /dev/null "http://127.0.0.1:${PORT}/health" || exit 1

ENTRYPOINT ["java", "-jar", "/app/smartpot-api.jar"]

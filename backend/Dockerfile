# ── Etapa 1: compilar ──────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cachear dependencias por separado para builds más rápidos
COPY pom.xml .
RUN mvn -q dependency:go-offline -B

COPY src ./src
RUN mvn -q -DskipTests package -B

# ── Etapa 2: runtime (imagen mínima) ───────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl && addgroup -S app && adduser -S app -G app

WORKDIR /app
COPY --from=build /build/target/trancones-*.jar app.jar

# Ejecutar como usuario sin privilegios
USER app
EXPOSE 8080

# SPRING_R2DBC_URL se construye desde DB_HOST/DB_PORT/DB_NAME en application.yml.
# SPRING_DATA_REDIS_HOST: default para docker-compose; en Render usar REDIS_HOST.
ENV SPRING_DATA_REDIS_HOST=redis

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]

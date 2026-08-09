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
RUN addgroup -S app && adduser -S app -G app

WORKDIR /app
COPY --from=build /build/target/trancones-*.jar app.jar

# Ejecutar como usuario sin privilegios
USER app
EXPOSE 8080

# Conexiones por defecto pensadas para docker-compose (ver docker-compose.yml)
ENV SPRING_R2DBC_URL=r2dbc:postgresql://postgres:5432/trancones \
    SPRING_DATA_REDIS_HOST=redis

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]

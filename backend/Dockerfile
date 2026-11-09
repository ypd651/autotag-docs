# Multi-stage Dockerfile para Spring Boot Backend con Gradle
FROM gradle:8.10-jdk17-alpine AS build
WORKDIR /app

# Copiar archivos de Gradle para cachear dependencias
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew ./
RUN ./gradlew dependencies --no-daemon || true

# Copiar código fuente y empaquetar la aplicación
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# Runtime Stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Crear usuario sin privilegios para seguridad en producción
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Crear directorio de subida persistente
RUN mkdir -p /shared-storage/uploads && chown -R appuser:appgroup /shared-storage

COPY --from=build /app/build/libs/*.jar app.jar
RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8081

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
